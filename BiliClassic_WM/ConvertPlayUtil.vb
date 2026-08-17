' ConvertPlayUtil.vb
' 转码播放取地址工具（移植自安卓版 ConvertPlayUtil.java）：
' 把 B 站原始播放地址交给 SCF 后端转成低码率 H.264 Baseline / MPEG-4，
' 供无法硬解高画质的老 WM 设备播放。转码是同步长耗时（可能几百秒），
' 必须在后台线程调用。
'
' 协议（与 SCF 后端对话，全部 POST JSON）：
'   1) POST {apiBase}/register
'      body: { bucket, region, install_id, ts, sig }
'      sig = hex(HmacSHA1(AUTH_SECRET, ts + "|" + install_id))
'      resp: { ok, token }   同一 install_id 服务器返回同一 token
'   2) POST {apiBase}/transcode
'      body: { bucket, region, src_url, token [, format] }
'      resp: { ok, cached?, bucket, region, out_key, url }
'   token / install_id 缓存在本地文件（GetAppDir()）。

Public Class ConvertPlayUtil

    ' 服务端默认地址（与安卓版一致）。WM 上直接存明文密钥（无需 XOR 掩码）。
    Private Shared ReadOnly API_BASE As String = "http://1303002254-dja6s2xtn7.ap-hongkong.tencentscf.com"
    Private Shared ReadOnly BUCKET As String = "video-storage-1303002254"
    Private Shared ReadOnly REGION As String = "ap-hongkong"
    Private Shared ReadOnly AUTH_SECRET As String = "da2e0efd51c3d601f629e9e878aa5c3218419d5aeb459c6355c4f1674b3bb2de"

    Private Shared sToken As String = ""
    Private Shared sTokenLoaded As Boolean = False

    ' ---- 设置读写（由 MainForm 调用）----

    Private Shared mConvertEnabled As Boolean = False
    Private Shared mConvertFormat As String = "h264" ' "h264" | "mpeg4"

    Public Shared Function IsConvertEnabled() As Boolean
        Return mConvertEnabled
    End Function

    Public Shared Sub SetConvertEnabled(ByVal enabled As Boolean)
        mConvertEnabled = enabled
    End Sub

    Public Shared Function GetConvertFormat() As String
        Return mConvertFormat
    End Function

    Public Shared Sub SetConvertFormat(ByVal fmt As String)
        mConvertFormat = fmt
    End Sub

    ' ---- 入口 ----

    ' 转码播放：开启时把地址交给 SCF 转成低码率；失败则回退原地址。
    Public Shared Function ConvertPlayUrl(ByVal rawUrl As String) As String
        If String.IsNullOrEmpty(rawUrl) Then
            Return rawUrl
        End If
        If Not IsConvertEnabled() Then
            Return rawUrl
        End If
        If Not (rawUrl.StartsWith("http://") OrElse rawUrl.StartsWith("https://")) Then
            Return rawUrl
        End If
        Dim conv As String = FetchTranscodedUrl(rawUrl)
        If conv Is Nothing OrElse conv.Length = 0 Then
            Return rawUrl
        End If
        Return conv
    End Function

    ' 调 SCF /transcode 转码，返回可播放地址；失败返回 null。
    Public Shared Function FetchTranscodedUrl(ByVal srcUrl As String) As String
        Dim token As String = GetCachedToken()
        If token Is Nothing Then
            token = RegisterToken()
        End If
        If token Is Nothing Then
            Return Nothing
        End If

        Dim url As String = DoTranscode(srcUrl, token)
        If url IsNot Nothing Then
            Return url
        End If

        ' token 可能已失效：清掉重新注册再试一次
        sToken = ""
        sTokenLoaded = False
        Try
            System.IO.File.Delete(GetTokenPath())
        Catch ex As Exception
        End Try
        Dim token2 As String = RegisterToken()
        If token2 Is Nothing Then
            Return Nothing
        End If
        Return DoTranscode(srcUrl, token2)
    End Function

    ' ---- token / install_id ----

    Private Shared Function GetCachedToken() As String
        If Not sTokenLoaded Then
            sTokenLoaded = True
            Try
                If System.IO.File.Exists(GetTokenPath()) Then
                    Dim sr As New System.IO.StreamReader(GetTokenPath())
                    sToken = sr.ReadToEnd().Trim()
                    sr.Close()
                End If
            Catch ex As Exception
            End Try
        End If
        If sToken IsNot Nothing AndAlso sToken.Length > 0 Then
            Return sToken
        End If
        Return Nothing
    End Function

    Private Shared Function RegisterToken() As String
        Try
            Dim installId As String = GetInstallId()
            ' 老设备系统时钟可能严重错误，先取服务器时钟算 ts（失败则用本机时间）
            Dim ts As String = GetServerTs()
            If ts Is Nothing Then
                Dim fallbackTicks As Long = DateTime.UtcNow.Ticks \ 10000000L
                ts = fallbackTicks.ToString()
            End If
            Dim sig As String = HmacSha1Hex(ts & "|" & installId)

            Dim sb As New System.Text.StringBuilder()
            sb.Append("{""bucket"":""" & BUCKET & """,""region"":""" & REGION & """,""install_id"":""" & installId & """,""ts"":""" & ts & """")
            If sig IsNot Nothing Then
                sb.Append(",""sig"":""" & sig & """")
            End If
            sb.Append("}")

            Dim resp As String() = HttpPostJson(API_BASE & "/register", sb.ToString())
            Dim code As Integer = Integer.Parse(resp(0))
            If code >= 200 AndAlso code < 300 Then
                Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(resp(1), """token"":\s*""([^""]*)""")
                If m.Success AndAlso m.Groups(1).Value.Length > 0 Then
                    sToken = m.Groups(1).Value
                    sTokenLoaded = True
                    Try
                        System.IO.Directory.CreateDirectory(GetAppDir())
                        Dim fs As New System.IO.FileStream(GetTokenPath(), System.IO.FileMode.Create)
                        Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.ASCII)
                        w.Write(sToken)
                        w.Close()
                        fs.Close()
                    Catch ex As Exception
                    End Try
                    Return sToken
                End If
            End If
        Catch ex As Exception
        End Try
        Return Nothing
    End Function

    Private Shared Function DoTranscode(ByVal srcUrl As String, ByVal token As String) As String
        Try
            Dim json As String = "{""bucket"":""" & BUCKET & """,""region"":""" & REGION & """,""src_url"":""" & JsonEscape(srcUrl) & """,""token"":""" & JsonEscape(token) & """,""format"":""" & mConvertFormat & """}"

            Dim resp As String() = HttpPostJson(API_BASE & "/transcode", json)
            Dim code As Integer = Integer.Parse(resp(0))
            If code >= 200 AndAlso code < 300 Then
                Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(resp(1), """url"":\s*""([^""]*)""")
                If m.Success AndAlso m.Groups(1).Value.Length > 0 Then
                    Dim url As String = m.Groups(1).Value
                    ' 老设备无法校验 HTTPS 证书，COS 输出地址强制转成 http
                    If url.StartsWith("https://") Then
                        url = "http://" & url.Substring("https://".Length)
                    End If
                    Return url
                End If
            End If
        Catch ex As Exception
        End Try
        Return Nothing
    End Function

    Private Shared Function JsonEscape(ByVal s As String) As String
        If s Is Nothing Then
            Return ""
        End If
        s = s.Replace("\", "\\")
        s = s.Replace("""", "\""")
        Return s
    End Function

    Private Shared Function GetServerTs() As String
        Try
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(API_BASE & "/health"), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 5000
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()
            Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """ts"":\s*(\d+)")
            If m.Success Then
                Return m.Groups(1).Value
            End If
        Catch ex As Exception
        End Try
        Return Nothing
    End Function

    ' POST JSON，返回 [状态码, 响应体]。转码读超时按长耗时放宽。
    Private Shared Function HttpPostJson(ByVal url As String, ByVal jsonBody As String) As String()
        Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
        req.Method = "POST"
        req.ContentType = "application/json; charset=utf-8"
        req.Timeout = 10000
        req.ReadWriteTimeout = 900000
        Dim data As Byte() = System.Text.Encoding.UTF8.GetBytes(jsonBody)
        req.ContentLength = data.Length
        Dim os As System.IO.Stream = req.GetRequestStream()
        os.Write(data, 0, data.Length)
        os.Close()
        Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
        Dim statusCode As Integer = CInt(resp.StatusCode)
        Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
        Dim body As String = reader.ReadToEnd()
        reader.Close()
        resp.Close()
        Dim result(1) As String
        result(0) = statusCode.ToString()
        result(1) = body
        Return result
    End Function

    Private Shared Function HmacSha1Hex(ByVal message As String) As String
        Return HmacUtil.HmacSha1Hex(AUTH_SECRET, message)
    End Function

    Private Shared Function GetInstallId() As String
        Dim id As String = ""
        Try
            If System.IO.File.Exists(GetInstallPath()) Then
                Dim sr As New System.IO.StreamReader(GetInstallPath())
                id = sr.ReadToEnd().Trim()
                sr.Close()
            End If
        Catch ex As Exception
        End Try
        If id.Length = 0 Then
            id = RandomHex(16)
            Try
                System.IO.Directory.CreateDirectory(GetAppDir())
                Dim fs As New System.IO.FileStream(GetInstallPath(), System.IO.FileMode.Create)
                Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.ASCII)
                w.Write(id)
                w.Close()
                fs.Close()
            Catch ex As Exception
            End Try
        End If
        Return id
    End Function

    Private Shared Function RandomHex(ByVal byteCount As Integer) As String
        Return HmacUtil.RandomHex(byteCount)
    End Function

    Private Shared Function GetAppDir() As String
        Try
            Dim p As String = System.IO.Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().GetName().CodeBase.Replace("file:///", ""))
            If String.IsNullOrEmpty(p) Then
                p = "\Program Files\BiliClassic"
            End If
            Return p
        Catch ex As Exception
            Return "\Program Files\BiliClassic"
        End Try
    End Function

    Private Shared Function GetTokenPath() As String
        Return GetAppDir() & "\convert_token.txt"
    End Function

    Private Shared Function GetInstallPath() As String
        Return GetAppDir() & "\convert_install.txt"
    End Function

End Class