' 网络请求工具类（.NET CF 2.0 兼容）。
' 统一处理：UA/Accept/Referer 请求头、Cookie、TLS 信任、超时、重试。
' 参考 Android 版 NetWorkUtil 的能力，但适配 CF 2.0 的 HttpWebRequest API。
'
' 用法：
'   NetWorkUtil.TrustAllCert()                     ' 全局信任所有证书（首次调用）
'   NetWorkUtil.SetCookies(cookie)                 ' 设置登录 Cookie
'   Dim text As String = NetWorkUtil.GetText(url)  ' GET，返回响应文本
'   Dim text As String = NetWorkUtil.PostText(url, formData)  ' POST form
'   Dim bmp As Bitmap = NetWorkUtil.GetImage(url, maxW, maxH) ' 下载图片（可选缩放）

Public NotInheritable Class NetWorkUtil

    ' 浏览器 UA（与 Android 版一致）。
    Public Const USER_AGENT As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
    Public Const REFERER As String = "https://www.bilibili.com/"

    ' 请求超时（毫秒）。
    Private Const CONNECT_TIMEOUT As Integer = 15000
    Private Const READ_TIMEOUT As Integer = 15000

    ' 全局登录 Cookie（MainForm 登录成功后设置）。
    Private Shared sCookies As String = ""

    ' 信任所有证书策略（CF 2.0 用 ICertificatePolicy，参考 Android 的 TRUST_ALL）。
    Private Class TrustAllPolicy
        Implements System.Net.ICertificatePolicy
        Public Function CheckValidationResult(ByVal sp As System.Net.ServicePoint, ByVal cert As System.Security.Cryptography.X509Certificates.X509Certificate, ByVal request As System.Net.WebRequest, ByVal problem As Integer) As Boolean Implements System.Net.ICertificatePolicy.CheckValidationResult
            Return True
        End Function
    End Class

    Private Sub New()
    End Sub

    ' 全局信任所有证书（WM 上 B 站证书链可能不完整，须信任）。
    Public Shared Sub TrustAllCert()
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch ex As Exception
        End Try
    End Sub

    ' 设置登录 Cookie（后续所有请求自动携带）。
    Public Shared Sub SetCookies(ByVal cookie As String)
        sCookies = cookie
    End Sub

    Public Shared Function GetCookies() As String
        Return sCookies
    End Function

    ' GET 请求，返回响应文本（失败返回空串）。
    Public Shared Function GetText(ByVal url As String) As String
        Return GetText(url, sCookies)
    End Function

    ' GET 请求，指定 Cookie。
    Public Shared Function GetText(ByVal url As String, ByVal cookie As String) As String
        Return GetTextEx(url, cookie, REFERER)
    End Function

    ' GET 请求，指定 Cookie 与 Referer。
    Public Shared Function GetTextEx(ByVal url As String, ByVal cookie As String, ByVal referer As String) As String
        TrustAllCert()
        ' 协议相对地址（//host/...）补 https 前缀。
        If url IsNot Nothing AndAlso url.StartsWith("//") Then
            url = "https:" & url
        End If
        ' WM 网络不稳，最多尝试 2 次。
        Dim attempt As Integer = 0
        While attempt < 2
            attempt += 1
            Try
                Return DoGetText(url, cookie, referer)
            Catch ex As Exception
                If attempt >= 2 Then
                    WriteNetLog2("GetText failed: " & url & " | " & ex.GetType().Name & " | " & ex.Message)
                End If
            End Try
        End While
        Return ""
    End Function

    Private Shared Function DoGetText(ByVal url As String, ByVal cookie As String, ByVal referer As String) As String
        Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
        req.Method = "GET"
        req.Timeout = CONNECT_TIMEOUT
        req.Accept = "application/json, text/plain, */*"
        req.UserAgent = USER_AGENT
        req.Referer = referer
        ApplyCookie(req, cookie)
        Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
        Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
        Dim body As String = reader.ReadToEnd()
        reader.Close()
        resp.Close()
        Return body
    End Function

    ' POST 请求（form 编码），返回响应文本。
    Public Shared Function PostText(ByVal url As String, ByVal data As String) As String
        Return PostText(url, data, sCookies)
    End Function

    Public Shared Function PostText(ByVal url As String, ByVal data As String, ByVal cookie As String) As String
        TrustAllCert()
        Dim attempt As Integer = 0
        While attempt < 2
            attempt += 1
            Try
                Return DoPostText(url, data, cookie)
            Catch ex As Exception
                If attempt >= 2 Then
                    WriteNetLog2("PostText failed: " & url & " | " & ex.GetType().Name & " | " & ex.Message)
                End If
            End Try
        End While
        Return ""
    End Function

    Private Shared Function DoPostText(ByVal url As String, ByVal data As String, ByVal cookie As String) As String
        Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
        req.Method = "POST"
        req.Timeout = CONNECT_TIMEOUT
        req.Accept = "application/json, text/plain, */*"
        req.UserAgent = USER_AGENT
        req.Referer = REFERER
        req.ContentType = "application/x-www-form-urlencoded; charset=utf-8"
        ApplyCookie(req, cookie)
        If data IsNot Nothing AndAlso data.Length > 0 Then
            Dim bytes As Byte() = System.Text.Encoding.UTF8.GetBytes(data)
            req.ContentLength = bytes.Length
            Dim os As System.IO.Stream = req.GetRequestStream()
            os.Write(bytes, 0, bytes.Length)
            os.Close()
        End If
        Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
        Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
        Dim body As String = reader.ReadToEnd()
        reader.Close()
        resp.Close()
        Return body
    End Function

    ' 下载图片为 Bitmap。maxW/maxH>0 时按比例缩小到该尺寸内（封面用小图，避免 WM 内存不足）。
    Public Shared Function GetImage(ByVal url As String, ByVal maxW As Integer, ByVal maxH As Integer) As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        TrustAllCert()
        ' 协议相对地址（//host/...）补 https 前缀。
        If url IsNot Nothing AndAlso url.StartsWith("//") Then
            url = "https:" & url
        End If
        ' B 站 CDN 支持缩略图参数：请求小图，避免 WM 解码大图 OOM。
        Dim thumbUrl As String = BuildThumbUrl(url, maxW, maxH)
        Dim attempt As Integer = 0
        ' WM 网络不稳，最多尝试 3 次；https 失败时回退 http 再试。
        While attempt < 3 AndAlso bmp Is Nothing
            attempt += 1
            Dim tryUrl As String = thumbUrl
            ' 第 2、3 次尝试：若当前是 https，回退 http（WM 上部分 CDN 域名 https 握手失败）。
            If attempt >= 2 AndAlso tryUrl.StartsWith("https:") Then
                tryUrl = "http:" & tryUrl.Substring(6)
            End If
            Try
                Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(tryUrl), System.Net.HttpWebRequest)
                req.Method = "GET"
                req.Timeout = 20000
                req.Accept = "image/jpeg, image/*"
                req.UserAgent = USER_AGENT
                req.Referer = REFERER
                ApplyCookie(req, sCookies)
                Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
                Dim stream As System.IO.Stream = resp.GetResponseStream()
                Dim ms As New System.IO.MemoryStream()
                Dim buf(8191) As Byte
                Dim n As Integer = stream.Read(buf, 0, buf.Length)
                While n > 0
                    ms.Write(buf, 0, n)
                    n = stream.Read(buf, 0, buf.Length)
                End While
                stream.Close()
                resp.Close()
                If ms.Length > 0 Then
                    ms.Position = 0
                    Dim full As System.Drawing.Bitmap = New System.Drawing.Bitmap(ms)
                    ' CF 的 Bitmap(Stream) 延迟解码：立即触发解码（流尚有效），避免绘制时才解码导致失败。
                    Try
                        Dim px As System.Drawing.Color = full.GetPixel(0, 0)
                    Catch exD As Exception
                    End Try
                    If maxW > 0 AndAlso maxH > 0 AndAlso (full.Width > maxW OrElse full.Height > maxH) Then
                        Dim scale As Single = 1.0F
                        If full.Width / maxW > full.Height / maxH Then
                            scale = maxW / full.Width
                        Else
                            scale = maxH / full.Height
                        End If
                        Dim tw As Integer = CInt(full.Width * scale)
                        Dim th As Integer = CInt(full.Height * scale)
                        If tw < 1 Then
                            tw = 1
                        End If
                        If th < 1 Then
                            th = 1
                        End If
                        Try
                            Dim small As New System.Drawing.Bitmap(tw, th)
                            Dim sg As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(small)
                            sg.Clear(System.Drawing.Color.White)
                            Dim srcR As New System.Drawing.Rectangle(0, 0, full.Width, full.Height)
                            Dim dstR As New System.Drawing.Rectangle(0, 0, tw, th)
                            sg.DrawImage(full, dstR, srcR, System.Drawing.GraphicsUnit.Pixel)
                            sg.Dispose()
                            full.Dispose()
                            bmp = small
                        Catch exS As Exception
                            bmp = full
                        End Try
                    Else
                        bmp = full
                    End If
                End If
            Catch ex As Exception
                bmp = Nothing
                WriteNetLog2("GetImage failed: " & tryUrl & " | " & ex.GetType().Name & " | " & ex.Message)
            End Try
        End While
        Return bmp
    End Function

    ' B 站 CDN 缩略图参数：对 hdslb 图片 URL 追加 @Ww_Hh_1c.jpg，服务端返回小图。
    ' 仅对形如 "https://i*.hdslb.com/bfs/xxx.jpg" 的图片追加，避免大图解码 OOM。
    Private Shared Function BuildThumbUrl(ByVal url As String, ByVal maxW As Integer, ByVal maxH As Integer) As String
        Try
            If maxW <= 0 OrElse maxH <= 0 Then
                Return url
            End If
            If url Is Nothing Then
                Return url
            End If
            ' 仅处理 B 站 CDN 图片路径（/bfs/ 目录下，.jpg/.jpeg 结尾）。
            If url.IndexOf("hdslb.com") < 0 Then
                Return url
            End If
            Dim lower As String = url.ToLower()
            If (Not lower.EndsWith(".jpg")) AndAlso (Not lower.EndsWith(".jpeg")) Then
                Return url
            End If
            ' 带查询参数的不处理（避免破坏已有参数）。
            If url.IndexOf("?") >= 0 Then
                Return url
            End If
            Return url & "@" & maxW.ToString() & "w_" & maxH.ToString() & "h_1c.jpg"
        Catch ex As Exception
            Return url
        End Try
    End Function

    ' 应用 Cookie。
    Private Shared Sub ApplyCookie(ByVal req As System.Net.HttpWebRequest, ByVal cookie As String)
        If cookie Is Nothing OrElse cookie.Length = 0 Then
            Return
        End If
        Try
            req.Headers("Cookie") = cookie
        Catch ex As Exception
        End Try
    End Sub

    ' 日志委托（外部可挂接 WriteLog）。
    Public Delegate Sub LogHandler(ByVal msg As String)
    Public Shared WriteNetLog As LogHandler = Nothing

    Private Shared Sub WriteNetLog2(ByVal msg As String)
        If WriteNetLog IsNot Nothing Then
            Try
                WriteNetLog(msg)
            Catch ex As Exception
            End Try
        End If
    End Sub

End Class
