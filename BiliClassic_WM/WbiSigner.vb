Public Class WbiSigner

    Private Shared ReadOnly MIXIN_KEY_ENC_TAB() As Integer = New Integer() { _
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, _
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, _
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, _
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52}

    Private _mixinKey As String = ""

    Public ReadOnly Property IsReady() As Boolean
        Get
            Return (_mixinKey IsNot Nothing AndAlso _mixinKey.Length = 32)
        End Get
    End Property

    Public ReadOnly Property MixinKey() As String
        Get
            Return _mixinKey
        End Get
    End Property

    Public Function FetchKeys(ByVal userAgent As String, ByVal referer As String) As Boolean
        Try
            Dim body As String = HttpGet("https://api.bilibili.com/x/web-interface/nav", userAgent, referer)
            If String.IsNullOrEmpty(body) Then
                Return False
            End If
            Dim imgUrl As String = ExtractJsonString(body, "img_url")
            Dim subUrl As String = ExtractJsonString(body, "sub_url")
            If String.IsNullOrEmpty(imgUrl) OrElse String.IsNullOrEmpty(subUrl) Then
                Return False
            End If
            Dim imgKey As String = KeyFromUrl(imgUrl)
            Dim subKey As String = KeyFromUrl(subUrl)
            If imgKey.Length = 0 OrElse subKey.Length = 0 Then
                Return False
            End If
            _mixinKey = ComputeMixinKey(imgKey, subKey)
            Return IsReady
        Catch ex As Exception
            Return False
        End Try
    End Function

    Public Function SignUrl(ByVal baseUrl As String, ByVal params As System.Collections.Generic.Dictionary(Of String, String)) As String
        Dim query As String = SignQuery(params)
        If String.IsNullOrEmpty(query) Then
            Return baseUrl
        End If
        If baseUrl.IndexOf("?") >= 0 Then
            Return baseUrl & "&" & query
        End If
        Return baseUrl & "?" & query
    End Function

    Public Function SignQuery(ByVal params As System.Collections.Generic.Dictionary(Of String, String)) As String
        If Not IsReady Then
            Return ""
        End If
        Dim wts As Long = CurrentUnixSeconds()

        Dim values As New System.Collections.Generic.Dictionary(Of String, String)()
        Dim keys As New System.Collections.ArrayList()
        Dim en As System.Collections.Generic.Dictionary(Of String, String).Enumerator = params.GetEnumerator()
        While en.MoveNext()
            Dim k As String = en.Current.Key
            values(k) = en.Current.Value
            If Not keys.Contains(k) Then
                keys.Add(k)
            End If
        End While
        values("wts") = wts.ToString()
        If Not keys.Contains("wts") Then
            keys.Add("wts")
        End If

        Dim arr(keys.Count - 1) As String
        keys.CopyTo(arr, 0)
        System.Array.Sort(arr, StringComparer.Ordinal)

        Dim sb As New System.Text.StringBuilder()
        For i As Integer = 0 To arr.Length - 1
            Dim k As String = arr(i)
            Dim v As String = values(k)
            v = v.Replace("!", "").Replace("'", "").Replace("(", "").Replace(")", "").Replace("*", "")
            If sb.Length > 0 Then
                sb.Append("&")
            End If
            sb.Append(Encode(k)).Append("=").Append(Encode(v))
        Next

        Dim query As String = sb.ToString()
        Dim wRid As String = Md5Hex(query & _mixinKey)
        Return query & "&w_rid=" & wRid
    End Function

    Private Function CurrentUnixSeconds() As Long
        Dim epoch As New DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)
        Dim span As TimeSpan = DateTime.UtcNow.Subtract(epoch)
        Return CType(span.TotalSeconds, Long)
    End Function

    Private Function ComputeMixinKey(ByVal imgKey As String, ByVal subKey As String) As String
        Dim raw As String = imgKey & subKey
        Dim sb As New System.Text.StringBuilder(32)
        For i As Integer = 0 To 31
            sb.Append(raw(MIXIN_KEY_ENC_TAB(i)))
        Next
        Return sb.ToString()
    End Function

    Private Function KeyFromUrl(ByVal url As String) As String
        Dim idx As Integer = url.LastIndexOf("/")
        Dim fname As String = url.Substring(idx + 1)
        idx = fname.LastIndexOf(".")
        If idx >= 0 Then
            fname = fname.Substring(0, idx)
        End If
        Return fname
    End Function

    Private Function ExtractJsonString(ByVal json As String, ByVal field As String) As String
        Dim pat As String = """" & field & """:\s*""([^""]*)"""
        Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(json, pat)
        If m.Success Then
            Return m.Groups(1).Value
        End If
        Return ""
    End Function

    Private Function Encode(ByVal value As String) As String
        Dim sb As New System.Text.StringBuilder()
        Dim bytes As Byte() = System.Text.Encoding.UTF8.GetBytes(value)
        For i As Integer = 0 To bytes.Length - 1
            Dim c As Char = ChrW(bytes(i))
            If (c >= "a"c AndAlso c <= "z"c) OrElse (c >= "A"c AndAlso c <= "Z"c) OrElse (c >= "0"c AndAlso c <= "9"c) OrElse c = "-"c OrElse c = "_"c OrElse c = "."c OrElse c = "~"c Then
                sb.Append(c)
            Else
                sb.Append("%")
                sb.Append(Hex(bytes(i)))
            End If
        Next
        Return sb.ToString()
    End Function

    Private Function Md5Hex(ByVal input As String) As String
        Dim md5 As New System.Security.Cryptography.MD5CryptoServiceProvider()
        Dim data As Byte() = System.Text.Encoding.UTF8.GetBytes(input)
        Dim hash As Byte() = md5.ComputeHash(data)
        Dim sb As New System.Text.StringBuilder(32)
        For i As Integer = 0 To hash.Length - 1
            sb.Append(hash(i).ToString("x2"))
        Next
        Return sb.ToString()
    End Function

    Private Function HttpGet(ByVal url As String, ByVal userAgent As String, ByVal referer As String) As String
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch ex As Exception
        End Try
        Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
        req.Method = "GET"
        req.Timeout = 15000
        req.Accept = "application/json"
        req.UserAgent = userAgent
        If Not String.IsNullOrEmpty(referer) Then
            req.Referer = referer
        End If
        Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
        Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
        Dim body As String = reader.ReadToEnd()
        reader.Close()
        resp.Close()
        Return body
    End Function

    Private Class TrustAllPolicy
        Implements System.Net.ICertificatePolicy
        Public Function CheckValidationResult(ByVal sp As System.Net.ServicePoint, ByVal cert As System.Security.Cryptography.X509Certificates.X509Certificate, ByVal request As System.Net.WebRequest, ByVal problem As Integer) As Boolean Implements System.Net.ICertificatePolicy.CheckValidationResult
            Return True
        End Function
    End Class

End Class