Public Class LocalStreamProxy

    Private _remoteUrl As String
    Private _ua As String
    Private _referer As String
    Private _cookie As String
    Private _listener As System.Net.Sockets.TcpListener
    Private _running As Boolean
    Private _localUrl As String
    Private _lastError As String = ""
    Private _preferPartial As Boolean = False

    Private _cacheMode As Boolean = False
    Private _cachePath As String = ""
    Private _cacheDownloaded As Long = 0
    Private _cacheTotal As Long = 0
    Private _cacheDone As Boolean = False
    Private _cacheError As String = ""

    Public Sub New(ByVal remoteUrl As String, ByVal userAgent As String, ByVal referer As String, ByVal cookie As String, ByVal preferPartial As Boolean)
        _remoteUrl = remoteUrl
        _ua = userAgent
        _referer = referer
        _cookie = cookie
        _preferPartial = preferPartial
    End Sub

    Public Sub EnableCacheMode(ByVal filePath As String)
        _cacheMode = True
        _cachePath = filePath
        Dim t As New System.Threading.Thread(AddressOf DownloadToCache)
        t.IsBackground = True
        t.Start()
        LogLine("cache: mode enabled, target=" & filePath)
    End Sub

    Private Sub DownloadToCache()
        Try
            Try
                If System.IO.File.Exists(_cachePath) Then
                    System.IO.File.Delete(_cachePath)
                End If
            Catch exD As Exception
            End Try

            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(_remoteUrl), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 60000
            req.Accept = "*/*"
            req.UserAgent = _ua
            req.Referer = _referer
            If Not String.IsNullOrEmpty(_cookie) Then
                req.Headers.Set("Cookie", _cookie)
            End If
            Try
                System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
            Catch exC As Exception
            End Try

            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            _cacheTotal = resp.ContentLength
            LogLine("cache: total=" & _cacheTotal.ToString())
            Dim inS As System.IO.Stream = resp.GetResponseStream()
            Dim fs As New System.IO.FileStream(_cachePath, System.IO.FileMode.Create, System.IO.FileAccess.Write, System.IO.FileShare.ReadWrite)
            Dim buf(65535) As Byte
            Dim n As Integer = inS.Read(buf, 0, buf.Length)
            While n > 0
                fs.Write(buf, 0, n)
                _cacheDownloaded += n
                n = inS.Read(buf, 0, buf.Length)
            End While
            fs.Close()
            inS.Close()
            resp.Close()
            _cacheDone = True
            LogLine("cache: done " & _cacheDownloaded.ToString())
        Catch ex As Exception
            _cacheError = ex.GetType().FullName & " | " & ex.Message
            _cacheDone = True
            LogLine("cache: error " & _cacheError)
        End Try
    End Sub

    Public Function Start() As String
        Dim localAddr As System.Net.IPAddress = System.Net.IPAddress.Any
        _listener = New System.Net.Sockets.TcpListener(localAddr, 0)
        _listener.Start()
        Dim port As Integer = CType(_listener.LocalEndpoint, System.Net.IPEndPoint).Port
        Dim host As String = GetLocalIp()
        _localUrl = "http://" & host & ":" & port.ToString() & "/video"
        _running = True
        Dim t As New System.Threading.Thread(AddressOf AcceptLoop)
        t.IsBackground = True
        t.Start()
        Return _localUrl
    End Function

    Private Function GetLocalIp() As String
        Try
            Dim he As System.Net.IPHostEntry = System.Net.Dns.GetHostEntry(System.Net.Dns.GetHostName())
            For Each a As System.Net.IPAddress In he.AddressList
                If a.AddressFamily = System.Net.Sockets.AddressFamily.InterNetwork AndAlso Not a.Equals(System.Net.IPAddress.Parse("127.0.0.1")) Then
                    Return a.ToString()
                End If
            Next
        Catch ex As Exception
        End Try
        Return "127.0.0.1"
    End Function

    Private Sub LogLine(ByVal msg As String)
        Try
            Dim logPath As String = "\Program Files\BiliClassic"
            Try
                System.IO.Directory.CreateDirectory(logPath)
            Catch exD As Exception
            End Try
            Dim fs As New System.IO.FileStream(logPath & "\biliclassic.txt", System.IO.FileMode.Append)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.UTF8)
            w.WriteLine(DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") & "  " & msg)
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub AcceptLoop()
        While _running
            Try
                Dim client As System.Net.Sockets.TcpClient = _listener.AcceptTcpClient()
                If client Is Nothing Then
                    Continue While
                End If
                Try
                    Dim h As New System.Threading.Thread(AddressOf New ClientHandler(client, AddressOf HandleClient).Run)
                    h.IsBackground = True
                    h.Start()
                Catch ex As Exception
                    Try
                        client.Close()
                    Catch ex2 As Exception
                    End Try
                End Try
            Catch ex As Exception
                If Not _running Then
                    Exit While
                End If
            End Try
        End While
    End Sub

    Private Delegate Sub HandleClientDelegate(ByVal client As System.Net.Sockets.TcpClient)

    Private Class ClientHandler
        Private _client As System.Net.Sockets.TcpClient
        Private _cb As HandleClientDelegate
        Public Sub New(ByVal client As System.Net.Sockets.TcpClient, ByVal cb As HandleClientDelegate)
            _client = client
            _cb = cb
        End Sub
        Public Sub Run()
            _cb(_client)
        End Sub
    End Class

    Private Sub HandleClient(ByVal client As System.Net.Sockets.TcpClient)
        Dim errored As Boolean = False
        Dim errMsg As String = ""
        Try
            Try
                client.ReceiveTimeout = 30000
                client.SendTimeout = 60000
            Catch exT As Exception
            End Try

            Dim stream As System.Net.Sockets.NetworkStream = client.GetStream()

            ' WMP keeps the same TCP connection alive and issues multiple
            ' Range requests (probe -> seek moov -> stream). Loop until client closes.
            While True
                Dim requestLine As String = ReadLine(stream)
                If requestLine Is Nothing Then
                    LogLine("conn closed (client done)")
                    Exit While
                End If

                Dim rangeHeader As String = Nothing
                Dim allHeaders As New System.Text.StringBuilder()
                Dim line As String = ReadLine(stream)
                While line IsNot Nothing AndAlso line.Length > 0
                    allHeaders.Append(line).Append("; ")
                    If line.ToLower().StartsWith("range:") Then
                        rangeHeader = line.Substring(6).Trim()
                    End If
                    line = ReadLine(stream)
                End While

                LogLine("conn req=" & requestLine & " headers=[" & allHeaders.ToString() & "]")

                errored = Not ServeRaw(stream, rangeHeader, errMsg)

                Try
                    stream.Flush()
                Catch ex As Exception
                End Try

                If errored Then
                    Exit While
                End If
            End While
        Catch ex As Exception
            errored = True
            errMsg = ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace
        End Try
        If errored AndAlso errMsg.Length > 0 Then
            _lastError = errMsg
            LogLine("proxy: " & errMsg)
        End If
        Try
            client.Close()
        Catch ex As Exception
        End Try
    End Sub

    Private Function ServeRaw(ByVal stream As System.Net.Sockets.NetworkStream, ByVal rangeHeader As String, ByRef errMsg As String) As Boolean
        If _cacheMode Then
            Return ServeFromCache(stream, rangeHeader, errMsg)
        End If
        Try
            Dim clientRange As Boolean = (rangeHeader IsNot Nothing) AndAlso rangeHeader.StartsWith("bytes=")
            If (Not clientRange) AndAlso _preferPartial Then
                clientRange = True
                rangeHeader = "bytes=0-"
            End If

            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(_remoteUrl), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 20000
            req.Accept = "*/*"
            req.UserAgent = _ua
            req.Referer = _referer
            If Not String.IsNullOrEmpty(_cookie) Then
                req.Headers.Set("Cookie", _cookie)
            End If
            Dim rangeSet As Boolean = False
            If clientRange Then
                Dim rng As String = rangeHeader.Substring(6)
                If rng.Length > 0 Then
                    Dim parts As String() = rng.Split("-"c)
                    Try
                        If parts.Length = 1 AndAlso rng.EndsWith("-") Then
                            req.AddRange(Integer.Parse(parts(0)))
                            rangeSet = True
                        ElseIf parts.Length = 2 Then
                            Dim s As Integer = Integer.Parse(parts(0))
                            Dim e As Integer = 0
                            If parts(1).Length > 0 Then
                                e = Integer.Parse(parts(1))
                            End If
                            If e > 0 Then
                                req.AddRange(s, e)
                            Else
                                req.AddRange(s)
                            End If
                            rangeSet = True
                        End If
                    Catch exRng As Exception
                    End Try
                End If
            End If
            Try
                System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
            Catch ex As Exception
            End Try

            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim respCode As Integer = CInt(resp.StatusCode)
            Dim contentType As String = resp.ContentType
            If String.IsNullOrEmpty(contentType) Then
                contentType = "video/mp4"
            End If
            Dim contentLength As Long = resp.ContentLength
            Dim contentRange As String = resp.Headers("Content-Range")
            LogLine("remote code=" & respCode.ToString() & " type=" & contentType & " len=" & contentLength.ToString() & " cr=" & (If(contentRange Is Nothing, "(none)", contentRange)))

            Dim sb As New System.Text.StringBuilder()
            If respCode = 206 Then
                sb.Append("HTTP/1.1 206 Partial Content" & Chr(13) & Chr(10))
                sb.Append("Accept-Ranges: bytes" & Chr(13) & Chr(10))
                If contentRange IsNot Nothing Then
                    sb.Append("Content-Range: " & contentRange & Chr(13) & Chr(10))
                End If
            Else
                sb.Append("HTTP/1.1 200 OK" & Chr(13) & Chr(10))
                sb.Append("Accept-Ranges: bytes" & Chr(13) & Chr(10))
            End If
            sb.Append("Content-Type: " & contentType & Chr(13) & Chr(10))
            If contentLength >= 0 Then
                sb.Append("Content-Length: " & contentLength.ToString() & Chr(13) & Chr(10))
            End If
            sb.Append("Connection: keep-alive" & Chr(13) & Chr(10))
            sb.Append(Chr(13) & Chr(10))

            Dim headerBytes As Byte() = System.Text.Encoding.ASCII.GetBytes(sb.ToString())
            stream.Write(headerBytes, 0, headerBytes.Length)
            stream.Flush()

            Dim remoteIn As System.IO.Stream = Nothing
            If respCode >= 200 AndAlso respCode < 300 Then
                remoteIn = resp.GetResponseStream()
            End If
            If remoteIn IsNot Nothing Then
                Dim buf(8191) As Byte
                Dim total As Long = 0
                Dim stopReason As String = "eof"
                Dim n As Integer = remoteIn.Read(buf, 0, buf.Length)
                While n > 0
                    If Not _running Then
                        stopReason = "stopped"
                        Exit While
                    End If
                    Try
                        stream.Write(buf, 0, n)
                        total += n
                    Catch ex As Exception
                        stopReason = "client closed (" & ex.GetType().FullName & ")"
                        Exit While
                    End Try
                    n = remoteIn.Read(buf, 0, buf.Length)
                End While
                LogLine("streamed " & total.ToString() & " bytes to client, stop=" & stopReason)
                Try
                    remoteIn.Close()
                Catch ex As Exception
                End Try
            End If
            Try
                resp.Close()
            Catch ex As Exception
            End Try
            Return True
        Catch ex As Exception
            errMsg = "proxy error: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace
            Return False
        End Try
    End Function

    Public ReadOnly Property LastError() As String
        Get
            Return _lastError
        End Get
    End Property

    Private Function ServeFromCache(ByVal stream As System.Net.Sockets.NetworkStream, ByVal rangeHeader As String, ByRef errMsg As String) As Boolean
        Try
            ' Wait for the background downloader to reach the requested offset.
            Dim start As Long = 0
            Dim endPos As Long = -1
            Dim hasRange As Boolean = (rangeHeader IsNot Nothing) AndAlso rangeHeader.StartsWith("bytes=")

            If hasRange Then
                Dim rng As String = rangeHeader.Substring(6)
                Dim parts As String() = rng.Split("-"c)
                Try
                    If parts.Length >= 1 AndAlso parts(0).Length > 0 Then
                        start = Long.Parse(parts(0))
                    End If
                    If parts.Length >= 2 AndAlso parts(1).Length > 0 Then
                        endPos = Long.Parse(parts(1))
                    End If
                Catch exR As Exception
                End Try
            End If

            ' Wait until the downloader has at least 'start' bytes buffered.
            Dim waited As Integer = 0
            While _cacheDownloaded <= start AndAlso Not _cacheDone AndAlso _running
                System.Threading.Thread.Sleep(50)
                waited += 50
                If waited > 120000 Then
                    errMsg = "cache wait timeout at " & start.ToString()
                    Return False
                End If
            End While
            If _cacheTotal <= 0 Then
                errMsg = "cache total unknown: " & _cacheError
                Return False
            End If
            If _cacheDownloaded <= start Then
                If _cacheError.Length > 0 Then
                    errMsg = "cache error: " & _cacheError
                Else
                    errMsg = "cache stopped at " & start.ToString()
                End If
                Return False
            End If

            If endPos < 0 OrElse endPos >= _cacheTotal Then
                endPos = _cacheTotal - 1
            End If
            Dim len As Long = endPos - start + 1

            Dim sb As New System.Text.StringBuilder()
            If hasRange OrElse _preferPartial Then
                sb.Append("HTTP/1.1 206 Partial Content" & Chr(13) & Chr(10))
                sb.Append("Accept-Ranges: bytes" & Chr(13) & Chr(10))
                sb.Append("Content-Range: bytes " & start.ToString() & "-" & endPos.ToString() & "/" & _cacheTotal.ToString() & Chr(13) & Chr(10))
            Else
                ' No Range requested -> return the full file (RFC-correct 200).
                ' This is what makes progressive-download players (desktop WMP,
                ' TCPMP) behave properly instead of confusing their demuxer.
                sb.Append("HTTP/1.1 200 OK" & Chr(13) & Chr(10))
                sb.Append("Accept-Ranges: bytes" & Chr(13) & Chr(10))
                start = 0
                endPos = _cacheTotal - 1
                len = _cacheTotal
            End If
            sb.Append("Content-Type: video/mp4" & Chr(13) & Chr(10))
            sb.Append("Content-Length: " & len.ToString() & Chr(13) & Chr(10))
            sb.Append("Connection: keep-alive" & Chr(13) & Chr(10))
            sb.Append(Chr(13) & Chr(10))

            Dim headerBytes As Byte() = System.Text.Encoding.ASCII.GetBytes(sb.ToString())
            stream.Write(headerBytes, 0, headerBytes.Length)
            stream.Flush()

            Dim fs As New System.IO.FileStream(_cachePath, System.IO.FileMode.Open, System.IO.FileAccess.Read, System.IO.FileShare.ReadWrite)
            fs.Seek(start, System.IO.SeekOrigin.Begin)
            Dim buf(65535) As Byte
            Dim total As Long = 0
            Dim stopReason As String = "eof"
            While total < len AndAlso _running
                ' If the next chunk isn't downloaded yet, wait briefly.
                If (start + total) >= _cacheDownloaded AndAlso Not _cacheDone Then
                    System.Threading.Thread.Sleep(50)
                    Continue While
                End If
                Dim toRead As Integer = CInt(Math.Min(len - total, buf.Length))
                Dim n As Integer = fs.Read(buf, 0, toRead)
                If n <= 0 Then
                    stopReason = "cache eof"
                    Exit While
                End If
                Try
                    stream.Write(buf, 0, n)
                    total += n
                Catch ex As Exception
                    stopReason = "client closed (" & ex.GetType().FullName & ")"
                    Exit While
                End Try
            End While
            LogLine("cache streamed " & total.ToString() & " bytes to client, stop=" & stopReason)
            Try
                fs.Close()
            Catch ex As Exception
            End Try
            Return True
        Catch ex As Exception
            errMsg = "cache proxy error: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace
            Return False
        End Try
    End Function

    Private Function ReadLine(ByVal stream As System.Net.Sockets.NetworkStream) As String
        Dim sb As New System.Text.StringBuilder()
        Dim prev As Integer = -1
        Dim b As Integer = stream.ReadByte()
        While b >= 0
            If prev = 13 AndAlso b = 10 Then
                If sb.Length > 0 Then
                    Return sb.ToString().Substring(0, sb.Length - 1)
                End If
                Return ""
            End If
            sb.Append(Chr(b))
            prev = b
            b = stream.ReadByte()
        End While
        If sb.Length = 0 Then
            Return Nothing
        End If
        Return sb.ToString()
    End Function

    Public Sub Shutdown()
        _running = False
        Try
            If _listener IsNot Nothing Then
                _listener.Stop()
                _listener = Nothing
            End If
        Catch ex As Exception
        End Try
    End Sub

    Public ReadOnly Property LocalUrl() As String
        Get
            Return _localUrl
        End Get
    End Property

    Private Class TrustAllPolicy
        Implements System.Net.ICertificatePolicy
        Public Function CheckValidationResult(ByVal sp As System.Net.ServicePoint, ByVal cert As System.Security.Cryptography.X509Certificates.X509Certificate, ByVal request As System.Net.WebRequest, ByVal problem As Integer) As Boolean Implements System.Net.ICertificatePolicy.CheckValidationResult
            Return True
        End Function
    End Class

End Class
