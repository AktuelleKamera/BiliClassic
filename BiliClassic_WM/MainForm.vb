Public Class MainForm

    Private Delegate Sub SimpleCallback()

    Private Declare Function SHGetSpecialFolderPath Lib "coredll.dll" (ByVal hwndOwner As IntPtr, ByVal lpszPath As System.Text.StringBuilder, ByVal nFolder As Integer, ByVal fCreate As Boolean) As Boolean

    Private Const CSIDL_PROGRAMS As Integer = 2
    Private Const CSIDL_STARTMENU As Integer = 11

    Private isChinese As Boolean = False
    Private isDesktopRuntime As Boolean = False
    Private av706Cid As String = ""
    Private av706Fetched As Boolean = False
    Private av706VideoUrl As String = ""
    Private av706Converted As Boolean = False
    Private convertSrcUrl As String = ""
    Private convertResultUrl As String = ""
    Private convertFromPlay As Boolean = False
    Private av706Proxy As LocalStreamProxy = Nothing
    Private currentAv As String = "706"
    Private playMode As String = "stream"

    Private pseudoRun As Integer = 0
    Private pseudoPath As String = ""
    Private pseudoTotal As Long = 0
    Private pseudoDownloaded As Long = 0
    Private pseudoLaunched As Boolean = False
    Private pseudoDone As Boolean = False
    Private pseudoError As String = ""

    Private loadingOverlay As LoadingOverlay = Nothing
    Private loadingCancelRequested As Boolean = False
    Private fetchSb As System.Text.StringBuilder = Nothing

    Private loginTimer As System.Windows.Forms.Timer = Nothing
    Private loginQrKey As String = ""
    Private loginPolling As Boolean = False
    Private loginExpireTick As Integer = 0
    Private savedCookies As String = ""
    Private loginUserName As String = ""
    Private searchHint As String = ""

    ' History pagination cursor (bilibili cursor API). max=0 means first page.
    Private histMax As String = "0"
    Private histViewAt As String = "0"
    Private histBusiness As String = "archive"
    Private histHasMore As Boolean = False
    Private histLoading As Boolean = False
    Private Const HIST_PAGE_MARKER As String = ">>>"

    Private favMid As String = ""
    Private favView As String = "" ' "folders" or "videos"
    Private favFolderId As String = ""
    Private favFolderName As String = ""
    Private favHasMore As Boolean = False
    Private favPage As Integer = 1
    Private favLoading As Boolean = False
    Private favLastBody As String = ""
    Private favLastErr As String = ""
    Private Const FAV_PAGE_MARKER As String = "==="

    Public Sub New()
        InitializeComponent()
    End Sub

    Private Class TrustAllPolicy
        Implements System.Net.ICertificatePolicy
        Public Function CheckValidationResult(ByVal sp As System.Net.ServicePoint, ByVal cert As System.Security.Cryptography.X509Certificates.X509Certificate, ByVal request As System.Net.WebRequest, ByVal problem As Integer) As Boolean Implements System.Net.ICertificatePolicy.CheckValidationResult
            Return True
        End Function
    End Class

    Private Sub Form1_Load(ByVal sender As Object, ByVal e As System.EventArgs) Handles MyBase.Load
        Try
            AddHandler AppDomain.CurrentDomain.UnhandledException, AddressOf OnUnhandledException

            Dim lang As String = System.Globalization.CultureInfo.CurrentUICulture.Name.ToLower()
            isChinese = lang.StartsWith("zh")

            Dim isDesktop As Boolean = (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)
            isDesktopRuntime = isDesktop
            If isDesktop Then
                Me.FormBorderStyle = System.Windows.Forms.FormBorderStyle.Sizable
                Me.WindowState = System.Windows.Forms.FormWindowState.Normal
                Me.Size = New System.Drawing.Size(800, 480)
                Me.Location = New System.Drawing.Point(0, 0)
                Try
                    Dim icoStream As System.IO.Stream = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("BiliClassic_WM.icon.ico")
                    If icoStream IsNot Nothing Then
                        Me.Icon = New System.Drawing.Icon(icoStream)
                    End If
                Catch exIcon As Exception
                End Try
            Else
                Me.WindowState = System.Windows.Forms.FormWindowState.Maximized
                Me.FormBorderStyle = System.Windows.Forms.FormBorderStyle.None
                Me.Size = Screen.PrimaryScreen.Bounds.Size
                Me.Location = Screen.PrimaryScreen.Bounds.Location
            End If
            Panel1.Dock = System.Windows.Forms.DockStyle.Top
            Panel1.BringToFront()

            If isChinese Then
                btnTestNet.Text = "测试网络连接B站"
                btnPlay.Text = "播放视频"
                btnPlayStream.Text = "流式播放"
                btnPlayOffline.Text = "离线播放"
                btnLogin.Text = "扫码登录"
                btnSearch.Text = "搜索"
                mnuSettings.Text = "设置"
                mnuExit.Text = "退出"
                mnuLogin.Text = "扫码登录"
                searchHint = "输入关键词或AV/BV号"
            Else
                btnTestNet.Text = "Test Bilibili Connection"
                btnPlay.Text = "Play Video"
                btnPlayStream.Text = "Stream Play"
                btnPlayOffline.Text = "Offline Play"
                btnLogin.Text = "QR Login"
                btnSearch.Text = "Search"
                mnuSettings.Text = "Settings"
                mnuExit.Text = "Exit"
                mnuLogin.Text = "QR Login"
                searchHint = "Enter keyword or av/BV"
            End If

            txtSearch.Text = searchHint
            txtSearch.ForeColor = System.Drawing.Color.Gray
            AddHandler txtSearch.GotFocus, AddressOf TxtSearch_GotFocus
            AddHandler txtSearch.LostFocus, AddressOf TxtSearch_LostFocus

            UpdateShortcut()

            LoadCookies()
            LoadPlayMode()
            LoadConvertConfig()

            loadingOverlay = New LoadingOverlay()
            AddHandler loadingOverlay.ReturnPressed, AddressOf LoadingOverlay_ReturnPressed
            Me.Controls.Add(loadingOverlay)
            loadingOverlay.Visible = False

            LayoutControls()
            Me.Refresh()

            ' 启动时不再自动获取播放地址，改为点击「播放」时才获取，
            ' 这样加载层小电视能正常展示获取过程。
            ShowResultView()
            If isChinese Then
                txtResult.Text = "点击「播放」开始播放。"
            Else
                txtResult.Text = "Tap Play to start."
            End If
        Catch ex As Exception
            MessageBox.Show(ex.ToString(), "Error")
        End Try
    End Sub

    ' On entry, automatically fetch the 360P source so the video is ready to play.
    Private Sub AutoFetchVideo()
        Try
            ShowResultView()
            If isChinese Then
                txtResult.Text = "正在获取 360P 视频源..."
            Else
                txtResult.Text = "Fetching 360P video source..."
            End If

            av706Fetched = False
            av706VideoUrl = ""
            av706Converted = False
            currentAv = "706"
            If av706Proxy IsNot Nothing Then
                av706Proxy.Shutdown()
                av706Proxy = Nothing
            End If

            Dim sb As New System.Text.StringBuilder()
            DoPlayVideoFlow("706", "", sb)
            If av706Fetched AndAlso String.IsNullOrEmpty(av706VideoUrl) Then
                SBLine(sb, "")
                SBLine(sb, If(isChinese, "自动获取播放地址...", "Auto fetching stream..."))
                DoPlayVideoFlow("", "", sb)
            End If
            txtResult.Text = sb.ToString()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub UpdateShortcut()
        Try
            Dim progDir As String = GetSpecialFolder(CSIDL_PROGRAMS)
            Dim startDir As String = GetSpecialFolder(CSIDL_STARTMENU)

            If String.IsNullOrEmpty(progDir) Then
                progDir = startDir
            End If

            Dim dirs As String() = New String() {progDir, startDir}
            Dim seen As New System.Collections.ArrayList()
            For i As Integer = 0 To dirs.Length - 1
                Dim d As String = dirs(i)
                If String.IsNullOrEmpty(d) OrElse Not System.IO.Directory.Exists(d) Then
                    Continue For
                End If
                If seen.Contains(d) Then
                    Continue For
                End If
                seen.Add(d)
                Dim enLnk As String = System.IO.Path.Combine(d, "BiliClassic.lnk")
                Dim zhLnk As String = System.IO.Path.Combine(d, "哔哩经典.lnk")

                If i = 0 Then
                    If isChinese Then
                        CreateShortcut(zhLnk)
                    Else
                        CreateShortcut(enLnk)
                    End If
                End If

                If isChinese Then
                    If System.IO.File.Exists(enLnk) Then
                        System.IO.File.Delete(enLnk)
                    End If
                    If i <> 0 AndAlso System.IO.File.Exists(zhLnk) Then
                        System.IO.File.Delete(zhLnk)
                    End If
                Else
                    If System.IO.File.Exists(zhLnk) Then
                        System.IO.File.Delete(zhLnk)
                    End If
                    If i <> 0 AndAlso System.IO.File.Exists(enLnk) Then
                        System.IO.File.Delete(enLnk)
                    End If
                End If
            Next
        Catch ex As Exception
        End Try
    End Sub

    Private Function GetSpecialFolder(ByVal csidl As Integer) As String
        Try
            Dim sb As New System.Text.StringBuilder(260)
            If SHGetSpecialFolderPath(System.IntPtr.Zero, sb, csidl, True) Then
                Return sb.ToString()
            End If
        Catch ex As Exception
        End Try
        Return ""
    End Function

    Private Sub SBLine(ByVal sb As System.Text.StringBuilder, ByVal text As String)
        sb.Append(text)
        sb.Append(Chr(13))
        sb.Append(Chr(10))
    End Sub

    ' The search result list overlays the txtResult area; any other action
    ' should switch back to showing the text result box.
    Private Sub ShowResultView()
        lstSearch.Visible = False
        txtResult.Visible = True
    End Sub

    Private Sub CreateShortcut(ByVal lnkPath As String)
        Dim exePath As String = System.Reflection.Assembly.GetExecutingAssembly().GetName().CodeBase
        If String.IsNullOrEmpty(exePath) Then
            exePath = "\Program Files\BiliClassic\BiliClassic.exe"
        End If
        exePath = exePath.Replace("file:///", "")

        Dim target As String = """" & exePath & """"
        Dim lnkContent As String = target.Length.ToString() & "#" & target

        Dim fs As New System.IO.FileStream(lnkPath, System.IO.FileMode.Create)
        Dim writer As New System.IO.StreamWriter(fs, System.Text.Encoding.ASCII)
        writer.Write(lnkContent)
        writer.Close()
        fs.Close()
    End Sub

    Private Sub MainForm_Resize(ByVal sender As Object, ByVal e As System.EventArgs) Handles Me.Resize
        Try
            LayoutControls()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub LayoutControls()
        Dim w As Integer = Me.ClientSize.Width
        Dim h As Integer = Me.ClientSize.Height
        Dim titleH As Integer = 38

        ' Title bar now hosts the search: title left, search box middle, button right
        Panel1.Height = titleH

        ' Measure the title text at full size so the search box shifts right
        ' to fit it instead of shrinking the title. MeasureString under-reports
        ' CJK width on CF, so compute a fixed per-char width instead.
        Dim appName As String = "BiliClassic"
        If isChinese Then
            appName = "哔哩经典"
        End If
        Dim titleLeft As Integer = 8
        Dim titleW As Integer = 62
        ' CJK glyphs at 9pt bold are ~18-19px wide; give each char a generous
        ' 20px so the white search box never overlaps the title.
        titleW = appName.Length * 20 + 12
        If titleW < 62 Then
            titleW = 62
        End If

        txtSearch.Height = 24
        txtSearch.Top = CInt((titleH - txtSearch.Height) / 2)
        txtSearch.Left = titleLeft + titleW
        txtSearch.Width = w - txtSearch.Left - 68
        If txtSearch.Width < 80 Then
            txtSearch.Width = 80
        End If

        btnSearch.Height = 28
        btnSearch.Top = CInt((titleH - btnSearch.Height) / 2)
        btnSearch.Left = txtSearch.Right + 4
        btnSearch.Width = 64

        Dim rowTop As Integer = titleH + 8

        btnTestNet.Width = CInt(w * 0.6)
        btnTestNet.Height = 40
        btnTestNet.Left = CInt((w - btnTestNet.Width) / 2)
        btnTestNet.Top = rowTop

        btnPlay.Width = CInt(w * 0.6)
        btnPlay.Height = 40
        btnPlay.Left = CInt((w - btnPlay.Width) / 2)
        btnPlay.Top = btnTestNet.Bottom + 10

        btnLogin.Width = CInt(w * 0.6)
        btnLogin.Height = 40
        btnLogin.Left = CInt((w - btnLogin.Width) / 2)
        btnLogin.Top = btnPlay.Bottom + 10

        btnMine.Width = CInt(w * 0.6)
        btnMine.Height = 40
        btnMine.Left = CInt((w - btnMine.Width) / 2)
        btnMine.Top = btnLogin.Bottom + 10

        picQr.Width = CInt(w * 0.5)
        picQr.Height = picQr.Width
        picQr.Left = CInt((w - picQr.Width) / 2)
        picQr.Top = btnMine.Bottom + 12
        If picQr.Top + picQr.Height > h - 100 Then
            picQr.Height = h - 100 - picQr.Top
            If picQr.Height > 0 Then
                picQr.Width = picQr.Height
            End If
        End If

        lstSearch.Left = 4
        lstSearch.Width = w - 8
        lstSearch.Top = titleH + 6
        lstSearch.Height = h - lstSearch.Top - 10

        txtResult.Left = 8
        txtResult.Width = w - 16
        txtResult.Top = picQr.Bottom + 12
        txtResult.Height = h - txtResult.Top - 15
        If txtResult.Height < 50 Then
            txtResult.Height = 50
        End If

        If loadingOverlay IsNot Nothing Then
            loadingOverlay.LayoutForSize()
        End If
    End Sub

    Private Sub Panel1_Paint(ByVal sender As Object, ByVal e As System.Windows.Forms.PaintEventArgs) Handles Panel1.Paint
        Dim g As System.Drawing.Graphics = e.Graphics

        Dim titleBrush As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HD8, &H6D, &HA5))
        g.FillRectangle(titleBrush, 0, 0, Panel1.Width, Panel1.Height)
        titleBrush.Dispose()

        Dim appName As String = "BiliClassic"
        If isChinese Then
            appName = "哔哩经典"
        End If

        Dim textBrush As New System.Drawing.SolidBrush(System.Drawing.Color.White)

        ' The search box sits right of the title; draw the title at full size.
        Dim availW As Single = CSng(txtSearch.Left - 4)
        Dim font As System.Drawing.Font = New System.Drawing.Font("Tahoma", 9.0!, System.Drawing.FontStyle.Bold)
        Dim textRect As New System.Drawing.RectangleF(3, 0, availW, Panel1.Height)
        Dim sf As New System.Drawing.StringFormat()
        sf.Alignment = System.Drawing.StringAlignment.Near
        sf.LineAlignment = System.Drawing.StringAlignment.Center
        sf.FormatFlags = System.Drawing.StringFormatFlags.NoWrap
        g.DrawString(appName, font, textBrush, textRect, sf)

        font.Dispose()
        textBrush.Dispose()
        sf.Dispose()
    End Sub

    Private Sub mnuWebsite_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuWebsite.Click
        Try
            Dim psi As New System.Diagnostics.ProcessStartInfo()
            psi.FileName = "http://www.biliclassic.cn"
            psi.UseShellExecute = True
            System.Diagnostics.Process.Start(psi)
        Catch ex As Exception
            Try
                MessageBox.Show(ex.Message, "Website")
            Catch ex2 As Exception
            End Try
        End Try
    End Sub

    Private Sub mnuStream_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuStream.Click
        SetPlayMode("stream")
    End Sub

    Private Sub mnuOffline_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuOffline.Click
        SetPlayMode("offline")
    End Sub

    Private Sub mnuConvert_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuConvert.Click
        Dim enabled As Boolean = Not ConvertPlayUtil.IsConvertEnabled()
        ConvertPlayUtil.SetConvertEnabled(enabled)
        SaveConvertConfig()
        UpdateConvertMenu()
        If isChinese Then
            txtResult.Text = "转码播放：" & If(enabled, "已开启", "已关闭")
        Else
            txtResult.Text = "Convert play: " & If(enabled, "On", "Off")
        End If
    End Sub

    Private Sub mnuConvertH264_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuConvertH264.Click
        ConvertPlayUtil.SetConvertFormat("h264")
        SaveConvertConfig()
        UpdateConvertMenu()
        If isChinese Then
            txtResult.Text = "转码格式：H.264 Baseline"
        Else
            txtResult.Text = "Convert format: H.264 Baseline"
        End If
    End Sub

    Private Sub mnuConvertMpeg4_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuConvertMpeg4.Click
        ConvertPlayUtil.SetConvertFormat("mpeg4")
        SaveConvertConfig()
        UpdateConvertMenu()
        If isChinese Then
            txtResult.Text = "转码格式：MPEG-4"
        Else
            txtResult.Text = "Convert format: MPEG-4"
        End If
    End Sub

    Private Sub UpdateConvertMenu()
        mnuConvert.Checked = ConvertPlayUtil.IsConvertEnabled()
        mnuConvertH264.Checked = (ConvertPlayUtil.GetConvertFormat() = "h264")
        mnuConvertMpeg4.Checked = (ConvertPlayUtil.GetConvertFormat() = "mpeg4")
    End Sub

    Private Function GetConvertPath() As String
        Return GetAppDir() & "\convert.txt"
    End Function

    Private Sub SaveConvertConfig()
        Try
            System.IO.Directory.CreateDirectory(GetAppDir())
            Dim fs As New System.IO.FileStream(GetConvertPath(), System.IO.FileMode.Create)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.ASCII)
            w.Write(If(ConvertPlayUtil.IsConvertEnabled(), "1", "0") & "," & ConvertPlayUtil.GetConvertFormat())
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub LoadConvertConfig()
        ConvertPlayUtil.SetConvertEnabled(False)
        ConvertPlayUtil.SetConvertFormat("h264")
        Try
            If System.IO.File.Exists(GetConvertPath()) Then
                Dim sr As New System.IO.StreamReader(GetConvertPath())
                Dim s As String = sr.ReadToEnd().Trim()
                sr.Close()
                Dim parts As String() = s.Split(","c)
                If parts.Length >= 2 Then
                    ConvertPlayUtil.SetConvertEnabled(parts(0).Trim() = "1")
                    ConvertPlayUtil.SetConvertFormat(parts(1).Trim())
                End If
            End If
        Catch ex As Exception
        End Try
        UpdateConvertMenu()
    End Sub

    Private Sub mnuHistory_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuHistory.Click
        StopLoginPolling()
        LoadHistory()
    End Sub

    Private Sub SetPlayMode(ByVal mode As String)
        playMode = mode
        UpdatePlayModeMenu()
        SavePlayMode()
        If isChinese Then
            If mode = "stream" Then
                txtResult.Text = "播放方式已设为：流式播放"
            Else
                txtResult.Text = "播放方式已设为：离线播放"
            End If
        Else
            If mode = "stream" Then
                txtResult.Text = "Play mode: Streaming"
            Else
                txtResult.Text = "Play mode: Offline"
            End If
        End If
    End Sub

    Private Sub UpdatePlayModeMenu()
        mnuStream.Checked = (playMode = "stream")
        mnuOffline.Checked = (playMode = "offline")
    End Sub

    Private Function GetPlayModePath() As String
        Return GetAppDir() & "\playmode.txt"
    End Function

    Private Sub SavePlayMode()
        Try
            System.IO.Directory.CreateDirectory(GetAppDir())
            Dim fs As New System.IO.FileStream(GetPlayModePath(), System.IO.FileMode.Create)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.ASCII)
            w.Write(playMode)
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub LoadPlayMode()
        playMode = "stream"
        Try
            If System.IO.File.Exists(GetPlayModePath()) Then
                Dim sr As New System.IO.StreamReader(GetPlayModePath())
                Dim s As String = sr.ReadToEnd().Trim()
                sr.Close()
                If s = "offline" Then
                    playMode = "offline"
                End If
            End If
        Catch ex As Exception
        End Try
        UpdatePlayModeMenu()
    End Sub

    Private Sub mnuCheckUpdate_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuCheckUpdate.Click
        StopLoginPolling()
        CheckForUpdate()
    End Sub

    Private Sub CheckForUpdate()
        Try
            ShowResultView()
            If isChinese Then
                txtResult.Text = "正在检查更新..."
            Else
                txtResult.Text = "Checking for updates..."
            End If
        Catch ex As Exception
        End Try

        Dim url As String = "http://www.biliclassic.cn/wm/api/version.json"
        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        Dim body As String = ""
        Try
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 15000
            req.Accept = "application/json"
            req.UserAgent = ua
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            body = reader.ReadToEnd()
            reader.Close()
            resp.Close()
        Catch ex As Exception
            If isChinese Then
                txtResult.Text = "检查更新失败: " & ex.Message
            Else
                txtResult.Text = "Update check failed: " & ex.Message
            End If
            Return
        End Try

        If String.IsNullOrEmpty(body) Then
            If isChinese Then
                txtResult.Text = "检查更新失败: 服务器无响应"
            Else
                txtResult.Text = "Update check failed: no response"
            End If
            Return
        End If

        Try
            Dim mVer As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """version"":\s*""([^""]*)""")
            Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """version_code"":\s*(\d+)")
            Dim mUrl As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """download_url"":\s*""([^""]*)""")
            Dim mLog As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """changelog"":\s*\[([^\]]*)\]")

            Dim serverName As String = ""
            If mVer.Success Then
                serverName = mVer.Groups(1).Value
            End If
            Dim serverCode As Integer = 0
            If mCode.Success Then
                serverCode = Integer.Parse(mCode.Groups(1).Value)
            End If
            Dim downloadUrl As String = ""
            If mUrl.Success Then
                downloadUrl = mUrl.Groups(1).Value
            End If

            Dim currentCode As Integer = 0
            Dim currentName As String = ""
            Try
                Dim v As System.Version = System.Reflection.Assembly.GetExecutingAssembly().GetName().Version
                currentName = v.ToString(3)
                currentCode = (v.Major * 10000) + (v.Minor * 100) + (v.Build * 10) + v.Revision
            Catch ex As Exception
            End Try

            If serverCode <= currentCode Then
                If isChinese Then
                    txtResult.Text = "已是最新版本 (" & currentName & ")"
                    MessageBox.Show("已是最新版本 (" & currentName & ")", "检查更新", MessageBoxButtons.OK, MessageBoxIcon.Asterisk, MessageBoxDefaultButton.Button1)
                Else
                    txtResult.Text = "You are up to date (" & currentName & ")"
                    MessageBox.Show("You are up to date (" & currentName & ")", "Update Check", MessageBoxButtons.OK, MessageBoxIcon.Asterisk, MessageBoxDefaultButton.Button1)
                End If
                Return
            End If

            Dim logText As String = ""
            If mLog.Success Then
                Dim mc As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(mLog.Groups(1).Value, """([^""]*)""")
                Dim sb As New System.Text.StringBuilder()
                For Each m As System.Text.RegularExpressions.Match In mc
                    If sb.Length > 0 Then
                        sb.Append(Chr(13))
                        sb.Append(Chr(10))
                    End If
                    sb.Append("• " & m.Groups(1).Value)
                Next
                logText = sb.ToString()
            End If

            Dim msg As String = ""
            If isChinese Then
                msg = "发现新版本: " & serverName & Chr(13) & Chr(10) & Chr(13) & Chr(10) & "更新内容:" & Chr(13) & Chr(10) & logText
            Else
                msg = "New version available: " & serverName & Chr(13) & Chr(10) & Chr(13) & Chr(10) & "Changelog:" & Chr(13) & Chr(10) & logText
            End If

            Dim res As System.Windows.Forms.DialogResult
            If isChinese Then
                res = MessageBox.Show(msg, "检查更新", MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button1)
            Else
                res = MessageBox.Show(msg, "Update Check", MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button1)
            End If
            If res = System.Windows.Forms.DialogResult.Yes Then
                If Not String.IsNullOrEmpty(downloadUrl) Then
                    Try
                        Dim psi As New System.Diagnostics.ProcessStartInfo()
                        psi.FileName = downloadUrl
                        psi.UseShellExecute = True
                        System.Diagnostics.Process.Start(psi)
                    Catch ex As Exception
                        If isChinese Then
                            MessageBox.Show("无法打开下载地址: " & ex.Message, "更新")
                        Else
                            MessageBox.Show("Cannot open download link: " & ex.Message, "Update")
                        End If
                    End Try
                End If
            End If
        Catch ex As Exception
            If isChinese Then
                txtResult.Text = "解析更新信息失败: " & ex.Message
            Else
                txtResult.Text = "Failed to parse update info: " & ex.Message
            End If
        End Try
    End Sub

    Private Sub mnuExit_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuExit.Click
        Try
            Me.Close()
        Catch ex As Exception
        End Try
        Try
            System.Windows.Forms.Application.Exit()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub btnTestNet_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnTestNet.Click
        StopLoginPolling()
        ShowResultView()
        btnTestNet.Enabled = False
        If isChinese Then
            txtResult.Text = "正在测试网络..."
        Else
            txtResult.Text = "Testing network..."
        End If
        Try
            ' Skip SSL certificate validation (WM6 root certs are outdated)
            Try
                System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
            Catch exCert As Exception
            End Try

            Dim sb As New System.Text.StringBuilder()

            ' Step 1: DNS resolution
            If isChinese Then
                SBLine(sb, "1. DNS 解析...")
            Else
                SBLine(sb, "1. DNS resolution...")
            End If
            Dim host As String = "api.bilibili.com"
            Try
                Dim he As System.Net.IPHostEntry = System.Net.Dns.GetHostEntry(host)
                SBLine(sb,"   " & host & " -> " & he.AddressList.Length.ToString() & " IP(s)")
                For i As Integer = 0 To he.AddressList.Length - 1
                    SBLine(sb,"   " & he.AddressList(i).ToString())
                Next
            Catch ex As Exception
                SBLine(sb,"   DNS failed: " & ex.Message)
            End Try

            ' Step 2: TCP connect to port 443
            If isChinese Then
                SBLine(sb,"2. TCP 连接 :443...")
            Else
                SBLine(sb,"2. TCP connect :443...")
            End If
            Try
                Dim client As New System.Net.Sockets.TcpClient()
                client.Connect(host, 443)
                SBLine(sb,"   TCP OK (port 443 open)")
                client.Close()
            Catch ex As Exception
                SBLine(sb,"   TCP failed: " & ex.Message)
            End Try

            ' Step 3: WBI 签名测试
            If isChinese Then
                SBLine(sb, "3. WBI 签名测试...")
            Else
                SBLine(sb, "3. WBI sign test...")
            End If
            Try
                Dim signer As New WbiSigner()
                Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                If signer.FetchKeys(ua, "https://www.bilibili.com/") Then
                    SBLine(sb, "   mixin_key: " & signer.MixinKey)
                    Dim params As New System.Collections.Generic.Dictionary(Of String, String)()
                    params("bvid") = "BV1xx411c7mD"
                    Dim signedUrl As String = signer.SignUrl("https://api.bilibili.com/x/web-interface/wbi/view", params)
                    SBLine(sb, "   signed URL: " & Microsoft.VisualBasic.Left(signedUrl, 200))

                    Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(signedUrl), System.Net.HttpWebRequest)
                    req.Method = "GET"
                    req.Timeout = 15000
                    req.Accept = "*/*"
                    req.UserAgent = ua
                    req.Referer = "https://www.bilibili.com/"

                    Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
                    Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
                    Dim body As String = reader.ReadToEnd()
                    reader.Close()
                    resp.Close()

                    SBLine(sb, "   response: " & Microsoft.VisualBasic.Left(body, 300))
                Else
                    SBLine(sb, "   FetchKeys failed (mixin_key not ready)")
                End If
            Catch ex As Exception
                If isChinese Then
                    SBLine(sb, "   WBI 失败: " & ex.Message)
                Else
                    SBLine(sb, "   WBI failed: " & ex.Message)
                End If
            End Try

            ' Step 4: HTTP plain test
            If isChinese Then
                SBLine(sb,"4. HTTP 明文请求...")
            Else
                SBLine(sb,"4. HTTP plain request...")
            End If
            Try
                Dim url2 As String = "http://api.bilibili.com/x/web-interface/nav"
                Dim req2 As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url2), System.Net.HttpWebRequest)
                req2.Method = "GET"
                req2.Timeout = 15000
                req2.Accept = "*/*"
                req2.UserAgent = "Mozilla/5.0"
                If String.IsNullOrEmpty(savedCookies) Then
                    SBLine(sb, "   未登录（无已保存 Cookie）")
                Else
                    SBLine(sb, "   已附加登录 Cookie")
                End If
                ApplyCookies(req2)

                Dim resp2 As System.Net.HttpWebResponse = CType(req2.GetResponse(), System.Net.HttpWebResponse)
                Dim reader2 As New System.IO.StreamReader(resp2.GetResponseStream(), System.Text.Encoding.UTF8)
                Dim body2 As String = reader2.ReadToEnd()
                reader2.Close()
                resp2.Close()

                SBLine(sb,"   HTTP status=" & CInt(resp2.StatusCode).ToString())
                SBLine(sb,"   " & Microsoft.VisualBasic.Left(body2, 200))
                Dim mIsLogin As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body2, """isLogin"":\s*(true|false)")
                Dim mUname As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body2, """uname"":\s*""([^""]*)""")
                If mIsLogin.Success AndAlso mIsLogin.Groups(1).Value = "true" Then
                    SBLine(sb,"   登录状态: 已登录" & IIf(mUname.Success, "（" & mUname.Groups(1).Value & "）", ""))
                Else
                    SBLine(sb,"   登录状态: 未登录（Cookie 无效或已过期）")
                End If
            Catch ex As Exception
                If isChinese Then
                    SBLine(sb,"   HTTP 失败: " & ex.Message)
                Else
                    SBLine(sb,"   HTTP failed: " & ex.Message)
                End If
            End Try

            txtResult.Text = sb.ToString()
        Catch ex As Exception
            If isChinese Then
                txtResult.Text = "测试失败：" & ex.Message
            Else
                txtResult.Text = "Test failed: " & ex.Message
            End If
        End Try
        btnTestNet.Enabled = True
    End Sub

    Private Sub btnPlay_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnPlay.Click
        StopLoginPolling()
        ShowResultView()
        btnPlay.Enabled = False

        ' 全屏加载层：获取播放地址期间显示动画，UI 线程只跑动画，
        ' 网络获取/转码/播放启动全部放后台线程，避免阻塞消息泵。
        Dim loadingMsg As String = If(isChinese, "正在获取播放地址...", "Fetching stream URL...")
        ShowLoadingOverlay(loadingMsg)
        loadingCancelRequested = False

        ' Ensure we have a fetched 360P stream for the current video.
        ' 同步获取会饿死 UI 消息泵导致加载层动画不显示，改到后台线程执行。
        If (Not av706Fetched) OrElse String.IsNullOrEmpty(av706VideoUrl) Then
            av706Fetched = False
            av706VideoUrl = ""
            av706Converted = False
            If av706Proxy IsNot Nothing Then
                av706Proxy.Shutdown()
                av706Proxy = Nothing
            End If
            fetchSb = New System.Text.StringBuilder()
            Dim tf As New System.Threading.Thread(AddressOf FetchAndPlayWorker)
            tf.Start()
            Return
        End If

        ' 地址已存在（本会话曾获取过）。
        If ConvertPlayUtil.IsConvertEnabled() AndAlso (Not av706Converted) AndAlso _
           Not String.IsNullOrEmpty(av706VideoUrl) Then
            Dim srcUrl As String = av706VideoUrl
            Dim convertingMsg As String = ""
            If isChinese Then
                convertingMsg = "正在转码视频（H.264/MPEG-4），可能需要几分钟..." & Chr(13) & Chr(10) & srcUrl
            Else
                convertingMsg = "Converting video, may take a few minutes..." & Chr(13) & Chr(10) & srcUrl
            End If
            loadingCancelRequested = False
            ShowLoadingOverlay(convertingMsg)
            Dim t As New System.Threading.Thread(AddressOf ConvertAndPlayWorker)
            convertSrcUrl = srcUrl
            convertFromPlay = True
            t.Start()
            Return
        End If

        ' 直连：后台线程启动播放，加载层保持动画。
        If (Not ConvertPlayUtil.IsConvertEnabled()) OrElse av706Converted Then
            loadingCancelRequested = False
            If isChinese Then
                ShowLoadingOverlay("正在加载视频..." & Chr(13) & Chr(10) & "播放地址已就绪")
            Else
                ShowLoadingOverlay("Loading video..." & Chr(13) & Chr(10) & "Stream URL ready")
            End If
            Dim t As New System.Threading.Thread(AddressOf PlayDirectWorker)
            t.Start()
            Return
        End If

        ' Play using the current preference (stream or offline).
        If playMode = "offline" Then
            btnPlayOffline_Click(sender, e)
        Else
            btnPlayStream_Click(sender, e)
        End If
        btnPlay.Enabled = True
    End Sub

    ' 后台线程：获取 360P 播放地址（避免同步阻塞 UI 消息泵），完成后回 UI 线程继续。
    Private Sub FetchAndPlayWorker()
        Dim sbLocal As System.Text.StringBuilder = fetchSb
        Try
            DoPlayVideoFlow(currentAv, "", sbLocal)
            If av706Fetched AndAlso String.IsNullOrEmpty(av706VideoUrl) Then
                SBLine(sbLocal, "")
                SBLine(sbLocal, If(isChinese, "自动获取播放地址...", "Auto fetching stream..."))
                DoPlayVideoFlow("", "", sbLocal)
            End If
        Catch ex As Exception
            WriteLog("fetch worker: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
        Try
            Me.Invoke(New SimpleCallback(AddressOf ContinueAfterFetch))
        Catch ex As Exception
            WriteLog("fetch invoke: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    ' UI 线程：获取完成后继续直连/转码/播放流程。
    Private Sub ContinueAfterFetch()
        If loadingCancelRequested Then
            HideLoadingOverlay()
            btnPlay.Enabled = True
            Return
        End If
        Dim sb As System.Text.StringBuilder = fetchSb
        txtResult.Text = sb.ToString()

        ' Transcode the source URL on a background thread when enabled.
        If ConvertPlayUtil.IsConvertEnabled() AndAlso (Not av706Converted) AndAlso _
           Not String.IsNullOrEmpty(av706VideoUrl) Then
            Dim srcUrl As String = av706VideoUrl
            Dim convertingMsg As String = ""
            If isChinese Then
                convertingMsg = "正在转码视频（H.264/MPEG-4），可能需要几分钟..." & Chr(13) & Chr(10) & srcUrl
            Else
                convertingMsg = "Converting video, may take a few minutes..." & Chr(13) & Chr(10) & srcUrl
            End If
            txtResult.Text = convertingMsg
            Me.Refresh()

            ' 全屏加载层：转码期间显示小电视动画，返回键可取消。
            loadingCancelRequested = False
            ShowLoadingOverlay(convertingMsg)
            Dim t As New System.Threading.Thread(AddressOf ConvertAndPlayWorker)
            convertSrcUrl = srcUrl
            convertFromPlay = True
            t.Start()
            Return
        End If

        ' 获取地址完成。直连（不转码）时也在后台线程启动播放，
        ' 加载层保持显示到播放器真正启动。
        If (Not ConvertPlayUtil.IsConvertEnabled()) OrElse av706Converted Then
            loadingCancelRequested = False
            If isChinese Then
                ShowLoadingOverlay("正在加载视频..." & Chr(13) & Chr(10) & "正在获取播放地址...【完成】")
            Else
                ShowLoadingOverlay("Loading video..." & Chr(13) & Chr(10) & "Fetching stream URL... [done]")
            End If
            Dim t As New System.Threading.Thread(AddressOf PlayDirectWorker)
            t.Start()
            Return
        End If

        ' Play using the current preference (stream or offline).
        If playMode = "offline" Then
            btnPlayOffline_Click(Me, New System.EventArgs())
        Else
            btnPlayStream_Click(Me, New System.EventArgs())
        End If
        btnPlay.Enabled = True
    End Sub

' 直连播放：后台线程启动代理+播放器，完成后回 UI 线程隐藏加载层。
    Private Sub PlayDirectWorker()
        Try
            If playMode = "offline" Then
                Me.Invoke(New SimpleCallback(AddressOf PlayOfflineOnUi))
            Else
                Me.Invoke(New SimpleCallback(AddressOf PlayStreamOnUi))
            End If
        Catch ex As Exception
            WriteLog("direct play invoke: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub PlayStreamOnUi()
        If loadingCancelRequested Then
            HideLoadingOverlay()
            btnPlay.Enabled = True
            Return
        End If
        Try
            btnPlayStream_Click(Me, New System.EventArgs())
        Catch ex As Exception
            WriteLog("stream on ui: " & ex.Message)
        End Try
        If Not loadingCancelRequested Then
            HideLoadingOverlay()
        End If
        btnPlay.Enabled = True
    End Sub

    Private Sub PlayOfflineOnUi()
        If loadingCancelRequested Then
            HideLoadingOverlay()
            btnPlay.Enabled = True
            Return
        End If
        Try
            btnPlayOffline_Click(Me, New System.EventArgs())
        Catch ex As Exception
            WriteLog("offline on ui: " & ex.Message)
        End Try
        If Not loadingCancelRequested Then
            HideLoadingOverlay()
        End If
        btnPlay.Enabled = True
    End Sub

    ' 全屏加载层：显示并带状态文字。
    Private Sub ShowLoadingOverlay(ByVal status As String)
        If loadingOverlay Is Nothing Then
            Return
        End If
        loadingOverlay.ShowOverlay(status)
    End Sub

    Private Sub HideLoadingOverlay()
        If loadingOverlay IsNot Nothing Then
            loadingOverlay.HideOverlay()
        End If
    End Sub

    ' 返回键：请求取消转码/加载，回到主界面。
    Private Sub LoadingOverlay_ReturnPressed(ByVal sender As Object, ByVal e As System.EventArgs)
        loadingCancelRequested = True
        HideLoadingOverlay()
        btnPlay.Enabled = True
        If isChinese Then
            txtResult.Text = "已取消加载，返回主界面。"
        Else
            txtResult.Text = "Loading cancelled."
        End If
    End Sub

    ' Background worker: run the SCF transcode, then marshal back to the UI
    ' thread to update the URL and start playback.
    Private Sub ConvertAndPlayWorker()
        convertResultUrl = ConvertPlayUtil.ConvertPlayUrl(convertSrcUrl)
        If String.IsNullOrEmpty(convertResultUrl) Then
            convertResultUrl = convertSrcUrl
        End If
        Try
            Me.Invoke(New SimpleCallback(AddressOf FinishConvertAndPlay))
        Catch ex As Exception
            av706VideoUrl = convertResultUrl
            av706Converted = True
            ResumePlayAfterConvert()
        End Try
    End Sub

    Private Sub FinishConvertAndPlay()
        av706VideoUrl = convertResultUrl
        av706Converted = True
        If convertFromPlay AndAlso (Not loadingCancelRequested) Then
            ResumePlayAfterConvert()
        Else
            HideLoadingOverlay()
            btnPlay.Enabled = True
        End If
    End Sub

    Private Sub ResumePlayAfterConvert()
        Try
            HideLoadingOverlay()
            Dim sb2 As New System.Text.StringBuilder()
            SBLine(sb2, If(isChinese, "转码完成，开始播放：", "Converted, playing:"))
            SBLine(sb2, av706VideoUrl)
            txtResult.Text = sb2.ToString()

            If playMode = "offline" Then
                btnPlayOffline_Click(Me, New System.EventArgs())
            Else
                btnPlayStream_Click(Me, New System.EventArgs())
            End If
        Catch ex As Exception
            WriteLog("convert resume: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
        btnPlay.Enabled = True
    End Sub

    Private Sub TxtSearch_GotFocus(ByVal sender As Object, ByVal e As System.EventArgs)
        If txtSearch.Text = searchHint Then
            txtSearch.Text = ""
            txtSearch.ForeColor = System.Drawing.Color.Black
        End If
        SipHelper.Show()
    End Sub

    Private Sub TxtSearch_LostFocus(ByVal sender As Object, ByVal e As System.EventArgs)
        If txtSearch.Text.Trim() = "" Then
            txtSearch.Text = searchHint
            txtSearch.ForeColor = System.Drawing.Color.Gray
        End If
        SipHelper.Hide()
    End Sub

    Private Sub btnSearch_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnSearch.Click
        StopLoginPolling()
        btnSearch.Enabled = False
        Dim kw As String = txtSearch.Text.Trim()
        If kw = searchHint Then
            kw = ""
        End If
        WriteLog("search clicked, kw='" & kw & "'")
        If String.IsNullOrEmpty(kw) Then
            txtResult.Text = If(isChinese, "请输入关键词或AV/BV号", "Enter a keyword or av/BV number")
            btnSearch.Enabled = True
            Return
        End If

        Dim sb As New System.Text.StringBuilder()

        ' av/BV number input -> direct playback
        Dim lower As String = kw.ToLower()
        Dim aid As String = ""
        Dim bvid As String = ""
        If lower.StartsWith("av") Then
            aid = kw.Substring(2)
        ElseIf lower.StartsWith("bv") Then
            bvid = kw
        End If

        If aid <> "" OrElse bvid <> "" Then
            av706Fetched = False
            av706VideoUrl = ""
            av706Converted = False
            If av706Proxy IsNot Nothing Then
                av706Proxy.Shutdown()
                av706Proxy = Nothing
            End If
            DoPlayVideoFlow(aid, bvid, sb)
            If av706Fetched AndAlso String.IsNullOrEmpty(av706VideoUrl) Then
                SBLine(sb, "")
                SBLine(sb, If(isChinese, "自动获取播放地址...", "Auto fetching stream..."))
                DoPlayVideoFlow("", "", sb)
            End If
            txtResult.Text = sb.ToString()
            lstSearch.Visible = False
            txtResult.Visible = True
            btnSearch.Enabled = True
            Return
        End If

        ' Keyword -> video search
        DoSearchVideo(kw, sb)
        btnSearch.Enabled = True
    End Sub

    Private Sub lstSearch_ItemActivate(ByVal sender As Object, ByVal e As System.EventArgs) Handles lstSearch.ItemActivate
        If lstSearch.SelectedIndices.Count > 0 Then
            Dim idx As Integer = lstSearch.SelectedIndices(0)
            If idx >= 0 AndAlso idx < lstSearch.Items.Count Then
                Dim sel As String = lstSearch.Items(idx).Text
                ' "Load more history" row triggers the next page of history.
                If sel.EndsWith(HIST_PAGE_MARKER) Then
                    LoadMoreHistory()
                    Return
                End If
                ' Favorites: folder row -> video list; video row -> play.
                If favView = "folders" Then
                    Dim mFid As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(sel, "#fid=(\d+)")
                    If mFid.Success Then
                        Dim name As String = System.Text.RegularExpressions.Regex.Replace(sel, "\s*#fid=\d+.*$", "")
                        OpenFavoriteFolder(mFid.Groups(1).Value, name)
                        Return
                    End If
                ElseIf favView = "videos" Then
                    If sel.EndsWith(FAV_PAGE_MARKER) Then
                        favPage += 1
                        LoadFolderVideosPage(True)
                        Return
                    End If
                    Dim mBv2 As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(sel, "#bv=(BV\w+)")
                    If mBv2.Success Then
                        PlayBvid(mBv2.Groups(1).Value)
                        Return
                    End If
                End If
                ' Extract the trailing BV number from the list line
                Dim mBv As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(sel, "(BV\w+)")
                If mBv.Success Then
                    PlayBvid(mBv.Groups(1).Value)
                End If
            End If
        End If
    End Sub

    Private Sub PlayBvid(ByVal bvid As String)
        btnSearch.Enabled = False
        Dim sb As New System.Text.StringBuilder()
        av706Fetched = False
        av706VideoUrl = ""
        av706Converted = False
        If av706Proxy IsNot Nothing Then
            av706Proxy.Shutdown()
            av706Proxy = Nothing
        End If
        DoPlayVideoFlow("", bvid, sb)
        If av706Fetched AndAlso String.IsNullOrEmpty(av706VideoUrl) Then
            SBLine(sb, "")
            SBLine(sb, If(isChinese, "自动获取播放地址...", "Auto fetching stream..."))
            DoPlayVideoFlow("", "", sb)
        End If
        txtResult.Text = sb.ToString()
        lstSearch.Visible = False
        txtResult.Visible = True
        btnSearch.Enabled = True
    End Sub

    ' Parse the view (video info) response and print a readable detail page.
    Private Sub ShowVideoInfo(ByVal body As String, ByVal bvid As String, ByVal sb As System.Text.StringBuilder)
        Dim title As String = ""
        Dim mT As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """title"":\s*""((?:[^""\\]|\\.)*)""")
        If mT.Success Then
            title = StripHtml(UnescapeJson(mT.Groups(1).Value))
        End If

        Dim author As String = ""
        Dim mU As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """name"":\s*""([^""]*)""")
        If mU.Success Then
            author = mU.Groups(1).Value
        End If

        Dim mView As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """view"":\s*(\d+)")
        Dim mLike As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """like"":\s*(\d+)")
        Dim mDan As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """danmaku"":\s*(\d+)")
        Dim mPub As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """pubdate"":\s*(\d+)")

        Dim desc As String = ""
        Dim mD As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """desc"":\s*""((?:[^""\\]|\\.)*)""")
        If mD.Success Then
            desc = StripHtml(UnescapeJson(mD.Groups(1).Value))
        End If

        SBLine(sb, "======================")
        SBLine(sb, If(title <> "", title, If(isChinese, "（无标题）", "(no title)")))
        SBLine(sb, "======================")
        SBLine(sb, If(isChinese, "UP主: ", "UP: ") & author)
        SBLine(sb, "BV: " & bvid)
        If mPub.Success Then
            Dim dt As Date = DateAdd(DateInterval.Second, CLng(mPub.Groups(1).Value), #1/1/1970#)
            SBLine(sb, If(isChinese, "发布时间: ", "Published: ") & dt.ToString("yyyy-MM-dd HH:mm"))
        End If
        SBLine(sb, "")
        SBLine(sb, If(isChinese, "播放: ", "Views: ") & mView.Groups(1).Value & "    " & If(isChinese, "弹幕: ", "Danmaku: ") & mDan.Groups(1).Value)
        SBLine(sb, If(isChinese, "点赞: ", "Likes: ") & mLike.Groups(1).Value)
        SBLine(sb, "")
        If desc <> "" Then
            SBLine(sb, If(isChinese, "简介: ", "Desc: "))
            SBLine(sb, desc)
            SBLine(sb, "")
        End If
        SBLine(sb, If(isChinese, "自动获取播放地址...", "Fetching stream..."))
    End Sub

    Private Sub DoSearchVideo(ByVal kw As String, ByVal sb As System.Text.StringBuilder)
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        Try
            Dim signer As New WbiSigner()
            If Not signer.FetchKeys(ua, "https://www.bilibili.com/") Then
                txtResult.Text = If(isChinese, "WBI 密钥获取失败", "WBI key fetch failed")
                Return
            End If

            Dim vp As New System.Collections.Generic.Dictionary(Of String, String)()
            vp("search_type") = "video"
            vp("keyword") = kw
            vp("page") = "1"
            Dim searchUrl As String = signer.SignUrl("https://api.bilibili.com/x/web-interface/wbi/search/type", vp)

            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(searchUrl), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 15000
            req.Accept = "application/json"
            req.UserAgent = ua
            req.Referer = "https://search.bilibili.com/"
            ApplyCookies(req)
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()

            ' Anchor on each bvid; take title forward and author backward so the
            ' fields stay correctly paired even when some result types lack bvid.
            Dim mB As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, """bvid"":\s*""([^""]+)""")
            Dim patT As String = """title"":\s*""((?:[^""\\]|\\.)*)"""
            Dim patA As String = """author"":\s*""([^""]*)"""
            Dim results As New System.Collections.ArrayList()
            For Each m As System.Text.RegularExpressions.Match In mB
                If results.Count >= 20 Then
                    Exit For
                End If
                Dim bv As String = m.Groups(1).Value
                Dim tail As Integer = System.Math.Min(400, body.Length - m.Index)
                Dim after As String = body.Substring(m.Index, tail)
                Dim mt As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patT)
                Dim title As String = ""
                If mt.Success Then
                    title = StripHtml(UnescapeJson(mt.Groups(1).Value))
                End If
                Dim startIdx As Integer = m.Index - 200
                If startIdx < 0 Then
                    startIdx = 0
                End If
                Dim before As String = body.Substring(startIdx, m.Index - startIdx)
                Dim ma As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(before, patA)
                Dim author As String = ""
                If ma.Count > 0 Then
                    author = ma(ma.Count - 1).Groups(1).Value
                End If
                Dim line As String = title & " ｜ " & author & "  " & bv
                results.Add(line)
            Next

            If results.Count = 0 Then
                lstSearch.BringToFront()
                lstSearch.Visible = True
                txtResult.Visible = False
                lstSearch.Items.Clear()
                If isChinese Then
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("未找到相关视频"))
                Else
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("No videos found"))
                End If
                Return
            End If

            ' Show result list on the main page (white background with separators)
            lstSearch.BringToFront()
            lstSearch.Visible = True
            txtResult.Visible = False
            lstSearch.Items.Clear()
            For i As Integer = 0 To results.Count - 1
                lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(CStr(results(i))))
            Next
            WriteLog("search OK: results=" & results.Count.ToString() & " items=" & lstSearch.Items.Count.ToString() & " listTop=" & lstSearch.Top.ToString() & " listH=" & lstSearch.Height.ToString() & " visible=" & lstSearch.Visible.ToString())
            lstSearch.Refresh()
        Catch ex As Exception
            WriteLog("search: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            txtResult.Text = If(isChinese, "搜索失败: " & ex.Message, "search failed: " & ex.Message)
            lstSearch.Visible = False
            txtResult.Visible = True
        End Try
    End Sub

    ' Load the logged-in user's watch history (mirrors Android HistoryApi).
    ' bilibili returns a cursor (max/view_at/business) used to request the next page.
    Private Sub LoadHistory()
        ShowResultView()
        If String.IsNullOrEmpty(savedCookies) Then
            If isChinese Then
                txtResult.Text = "请先登录后再查看历史记录"
            Else
                txtResult.Text = "Please log in first to view history"
            End If
            Return
        End If

        histMax = "0"
        histViewAt = "0"
        histBusiness = "archive"
        histHasMore = False
        histLoading = False
        LoadHistoryPage(False)
    End Sub

    Private Sub LoadMoreHistory()
        If histLoading Then
            Return
        End If
        If Not histHasMore Then
            Return
        End If
        LoadHistoryPage(True)
    End Sub

    Private Sub LoadHistoryPage(ByVal loadMore As Boolean)
        If histLoading Then
            Return
        End If
        histLoading = True
        ShowResultView()

        lstSearch.BringToFront()
        lstSearch.Visible = True
        txtResult.Visible = False

        If loadMore Then
            ' Replace the trailing "load more" row with a loading indicator.
            If lstSearch.Items.Count > 0 Then
                lstSearch.Items(lstSearch.Items.Count - 1).Text = If(isChinese, "正在加载更多...", "Loading more...")
            Else
                lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "正在加载更多...", "Loading more...")))
            End If
            lstSearch.Refresh()
        Else
            lstSearch.Items.Clear()
            If isChinese Then
                lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("正在加载历史记录..."))
            Else
                lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("Loading history..."))
            End If
            lstSearch.Refresh()
        End If

        Dim results As New System.Collections.ArrayList()
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        Try
            Dim url As String = "https://api.bilibili.com/x/web-interface/history/cursor?type=archive&ps=20&max=" & histMax
            If histMax <> "0" Then
                url &= "&view_at=" & histViewAt & "&business=" & histBusiness
            End If
            WriteLog("history: url=" & url)
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 15000
            req.Accept = "application/json, text/plain, */*"
            req.UserAgent = ua
            req.Referer = "https://www.bilibili.com/"
            ApplyCookies(req)
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()

            WriteLog("history: " & Microsoft.VisualBasic.Left(body, 200))

            Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
            If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
                lstSearch.Items.Clear()
                If isChinese Then
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("历史记录获取失败 (code=" & mCode.Groups(1).Value & ")"))
                Else
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("History failed (code=" & mCode.Groups(1).Value & ")"))
                End If
                lstSearch.Refresh()
                histLoading = False
                Return
            End If

            ' Update the pagination cursor from this page's response.
            Dim mCur As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """cursor"":\s*\{([^}]*)\}")
            If mCur.Success Then
                Dim cBlock As String = mCur.Groups(1).Value
                Dim mMax As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(cBlock, """max"":\s*(\d+)")
                If mMax.Success Then
                    histMax = mMax.Groups(1).Value
                End If
                Dim mVA As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(cBlock, """view_at"":\s*(\d+)")
                If mVA.Success Then
                    histViewAt = mVA.Groups(1).Value
                End If
                Dim mBiz As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(cBlock, """business"":\s*""([^""]*)""")
                If mBiz.Success Then
                    histBusiness = mBiz.Groups(1).Value
                End If
            End If

            ' Each history entry: title before history.bvid; author_name and
            ' progress after it. Anchor on bvid, look backward for title and
            ' forward for author/progress.
            Dim mB As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, """bvid"":\s*""([^""]+)""")
            Dim seen As New System.Collections.ArrayList()
            For Each m As System.Text.RegularExpressions.Match In mB
                Dim bv As String = m.Groups(1).Value
                If seen.Contains(bv) Then
                    Continue For
                End If
                seen.Add(bv)
                If results.Count >= 20 Then
                    Exit For
                End If

                Dim startIdx As Integer = m.Index - 300
                If startIdx < 0 Then
                    startIdx = 0
                End If
                Dim before As String = body.Substring(startIdx, m.Index - startIdx)
                Dim mt As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(before, """title"":\s*""((?:[^""\\]|\\.)*)""")
                Dim title As String = ""
                If mt.Success Then
                    title = StripHtml(UnescapeJson(mt.Groups(1).Value))
                End If

                Dim tail As Integer = System.Math.Min(600, body.Length - m.Index)
                Dim after As String = body.Substring(m.Index, tail)
                Dim ma As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(after, """author_name"":\s*""([^""]*)""")
                Dim author As String = ""
                If ma.Count > 0 Then
                    author = ma(0).Groups(1).Value
                End If
                Dim mProg As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, """progress"":\s*(\d+)")
                Dim prog As Long = 0
                If mProg.Success Then
                    prog = CLng(mProg.Groups(1).Value)
                End If
                Dim progStr As String = ""
                If prog > 0 Then
                    progStr = "看到 " & FormatTime(prog)
                Else
                    progStr = "还没看过"
                End If

                Dim line As String = title & " ｜ " & author & " ｜ " & progStr & "  " & bv
                results.Add(line)
            Next

            ' Keep paging as long as this page returned records; only stop when
            ' the server sends an empty page (list exhausted).
            histHasMore = (results.Count > 0)

            If loadMore Then
                ' Drop the trailing loading-indicator row, then append the new page.
                Dim idx As Integer = lstSearch.Items.Count - 1
                If idx >= 0 AndAlso (lstSearch.Items(idx).Text = "正在加载更多..." OrElse lstSearch.Items(idx).Text = "Loading more...") Then
                    lstSearch.Items.RemoveAt(idx)
                End If
            Else
                lstSearch.Items.Clear()
            End If

            If results.Count = 0 Then
                If loadMore Then
                    If isChinese Then
                        lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("没有更多历史记录了"))
                    Else
                        lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("No more history"))
                    End If
                ElseIf isChinese Then
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("暂无历史记录"))
                Else
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("No history yet"))
                End If
            Else
                For i As Integer = 0 To results.Count - 1
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(CStr(results(i))))
                Next
                If histHasMore Then
                    Dim loadTxt As String = If(isChinese, "加载更多历史记录 " & HIST_PAGE_MARKER, "Load more history " & HIST_PAGE_MARKER)
                    lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(loadTxt))
                End If
            End If
            lstSearch.Refresh()
            WriteLog("history OK: page=" & results.Count.ToString() & " hasMore=" & histHasMore.ToString() & " max=" & histMax)
        Catch ex As Exception
            WriteLog("history: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            lstSearch.Items.Clear()
            If isChinese Then
                lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("历史记录加载失败: " & ex.Message))
            Else
                lstSearch.Items.Add(New System.Windows.Forms.ListViewItem("History load failed: " & ex.Message))
            End If
            lstSearch.Refresh()
        Finally
            histLoading = False
        End Try
    End Sub

    Private Function FormatTime(ByVal secs As Long) As String
        Try
            Dim h As Long = secs \ 3600
            Dim m As Long = (secs Mod 3600) \ 60
            Dim s As Long = secs Mod 60
            Dim t As String = ""
            If h > 0 Then
                t = h.ToString() & ":" & m.ToString("00") & ":" & s.ToString("00")
            Else
                t = m.ToString() & ":" & s.ToString("00")
            End If
            Return t
        Catch ex As Exception
            Return secs.ToString()
        End Try
    End Function

    Private Function UnescapeJson(ByVal s As String) As String
        Try
            Dim sb As New System.Text.StringBuilder()
            Dim i As Integer = 0
            While i < s.Length
                If s(i) = "\"c AndAlso i + 1 < s.Length Then
                    If s(i + 1) = "u"c AndAlso i + 5 < s.Length Then
                        Dim hexs As String = s.Substring(i + 2, 4)
                        Try
                            Dim cp As Integer = Integer.Parse(hexs, System.Globalization.NumberStyles.HexNumber)
                            sb.Append(ChrW(cp))
                        Catch exU As Exception
                        End Try
                        i += 6
                        Continue While
                    ElseIf s(i + 1) = "n"c Then
                        sb.Append(Chr(10)) : i += 2 : Continue While
                    ElseIf s(i + 1) = "r"c Then
                        sb.Append(Chr(13)) : i += 2 : Continue While
                    ElseIf s(i + 1) = "t"c Then
                        sb.Append(Chr(9)) : i += 2 : Continue While
                    ElseIf s(i + 1) = """"c Then
                        sb.Append(""""c) : i += 2 : Continue While
                    ElseIf s(i + 1) = "\"c Then
                        sb.Append("\"c) : i += 2 : Continue While
                    End If
                End If
                sb.Append(s(i))
                i += 1
            End While
            Return sb.ToString()
        Catch ex As Exception
            Return s
        End Try
    End Function

    Private Function StripHtml(ByVal s As String) As String
        If s Is Nothing Then
            Return ""
        End If
        ' Remove HTML tags (e.g. <em class="keyword">...</em>) from search titles
        Try
            s = System.Text.RegularExpressions.Regex.Replace(s, "<[^>]*>", "")
        Catch ex As Exception
        End Try
        s = s.Replace("\u0026", "&")
        s = s.Replace("\/", "/")
        s = s.Replace("\\\""", """")
        Return s
    End Function

    Private Function IsNumeric(ByVal s As String) As Boolean
        If String.IsNullOrEmpty(s) Then
            Return False
        End If
        For i As Integer = 0 To s.Length - 1
            If s(i) < "0"c OrElse s(i) > "9"c Then
                Return False
            End If
        Next
        Return True
    End Function

    ' CF 2.0 has no InputBox, so build a small modal dialog at runtime.
    ' On WM the soft keyboard (SIP) is shown via Microsoft.WindowsCE.Forms.InputPanel.
    ' When the SIP pops up the shell shrinks the focused window, so we relocate
    ' the dialog to the top of the screen so it is not vertically compressed.
    Private Function ShowInputDialog(ByVal owner As System.Windows.Forms.Form, ByVal prompt As String) As String
        Try
            Dim dlg As New System.Windows.Forms.Form()
            dlg.Text = If(isChinese, "输入AV/BV号", "Enter AV/BV")
            dlg.FormBorderStyle = System.Windows.Forms.FormBorderStyle.FixedDialog
            dlg.ControlBox = False
            dlg.MinimizeBox = False
            dlg.MaximizeBox = False
            dlg.AutoScaleMode = System.Windows.Forms.AutoScaleMode.None
            dlg.Size = New System.Drawing.Size(210, 155)
            ' Put dialog at top center of the screen so the SIP does not push it off
            Dim scrW As Integer = Screen.PrimaryScreen.Bounds.Width
            dlg.Location = New System.Drawing.Point(CInt((scrW - dlg.Width) / 2), 4)

            Dim lbl As New System.Windows.Forms.Label()
            lbl.Text = prompt
            lbl.Location = New System.Drawing.Point(8, 6)
            lbl.Size = New System.Drawing.Size(194, 18)

            Dim txt As New System.Windows.Forms.TextBox()
            txt.Location = New System.Drawing.Point(8, 28)
            txt.Size = New System.Drawing.Size(194, 24)
            txt.Text = ""

            Dim btnOk As New System.Windows.Forms.Button()
            btnOk.Text = If(isChinese, "确定", "OK")
            btnOk.Location = New System.Drawing.Point(20, 62)
            btnOk.Size = New System.Drawing.Size(80, 40)
            AddHandler btnOk.Click, AddressOf InputDlgOk

            Dim btnCancel As New System.Windows.Forms.Button()
            btnCancel.Text = If(isChinese, "取消", "Cancel")
            btnCancel.Location = New System.Drawing.Point(110, 62)
            btnCancel.Size = New System.Drawing.Size(80, 40)
            AddHandler btnCancel.Click, AddressOf InputDlgCancel

            AddHandler txt.GotFocus, AddressOf InputDlgGotFocus
            AddHandler dlg.Activated, AddressOf InputDlgActivated

            inputDlgForm = dlg
            inputDlgText = txt
            dlg.Controls.Add(lbl)
            dlg.Controls.Add(txt)
            dlg.Controls.Add(btnOk)
            dlg.Controls.Add(btnCancel)

            inputDlgResult = Nothing
            dlg.ShowDialog()
            If inputDlgResult Is Nothing Then
                Return ""
            End If
            Return inputDlgResult
        Catch ex As Exception
            WriteLog("input dialog: " & ex.GetType().FullName & " | " & ex.Message)
            Return ""
        End Try
    End Function

    Private inputDlgForm As System.Windows.Forms.Form = Nothing
    Private inputDlgText As System.Windows.Forms.TextBox = Nothing
    Private inputDlgResult As String = Nothing

    ' When the soft keyboard appears the shell shrinks the focused window.
    ' Move the dialog up to the top of the screen so it is not compressed.
    Private Sub InputDlgActivated(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            If inputDlgText IsNot Nothing Then
                inputDlgText.Focus()
            End If
            If Not IsDesktopRuntime Then
                SipHelper.Show()
            End If
        Catch ex As Exception
        End Try
    End Sub

    Private Sub InputDlgGotFocus(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            If Not IsDesktopRuntime Then
                SipHelper.Show()
            End If
        Catch ex As Exception
        End Try
    End Sub

    Private Sub InputDlgOk(ByVal sender As Object, ByVal e As System.EventArgs)
        If inputDlgText IsNot Nothing Then
            inputDlgResult = inputDlgText.Text
        End If
        Try
            If Not IsDesktopRuntime Then
                SipHelper.Hide()
            End If
        Catch ex As Exception
        End Try
        If inputDlgForm IsNot Nothing Then
            inputDlgForm.Close()
        End If
    End Sub

    Private Sub InputDlgCancel(ByVal sender As Object, ByVal e As System.EventArgs)
        inputDlgResult = Nothing
        Try
            If Not IsDesktopRuntime Then
                SipHelper.Hide()
            End If
        Catch ex As Exception
        End Try
        If inputDlgForm IsNot Nothing Then
            inputDlgForm.Close()
        End If
    End Sub

    ' Runs the view-then-playurl flow for a given av (aid) or BV (bvid).
    Private Sub DoPlayVideoFlow(ByVal aid As String, ByVal bvid As String, ByVal sb As System.Text.StringBuilder)
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        Try
            Dim signer As New WbiSigner()
            If Not signer.FetchKeys(ua, "https://www.bilibili.com/") Then
                If isChinese Then
                    txtResult.Text = "WBI 密钥获取失败"
                Else
                    txtResult.Text = "WBI key fetch failed"
                End If
                Return
            End If

            If Not av706Fetched Then
                ' First step: fetch raw video info (view) to get cid
                Dim vp As New System.Collections.Generic.Dictionary(Of String, String)()
                If bvid <> "" Then
                    vp("bvid") = bvid
                Else
                    vp("aid") = aid
                End If
                Dim viewUrl As String = signer.SignUrl("https://api.bilibili.com/x/web-interface/wbi/view", vp)
                SBLine(sb, "view: " & Microsoft.VisualBasic.Left(viewUrl, 120))
                Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(viewUrl), System.Net.HttpWebRequest)
                req.Method = "GET"
                req.Timeout = 15000
                req.Accept = "application/json"
                req.UserAgent = ua
                req.Referer = "https://www.bilibili.com/"
                ApplyCookies(req)
                Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
                Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
                Dim body As String = reader.ReadToEnd()
                reader.Close()
                resp.Close()

                Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """cid"":\s*(\d+)")
                If m.Success Then
                    av706Cid = m.Groups(1).Value
                    av706Fetched = True
                    If bvid <> "" Then
                        ' BV input: view response also returns the numeric aid we need for playurl
                        Dim mA As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """aid"":\s*(\d+)")
                        If mA.Success Then
                            currentAv = mA.Groups(1).Value
                        End If
                    Else
                        currentAv = aid
                    End If

                    ' Pretty-print the video info so tapping a search result
                    ' shows a readable detail page before playback.
                    ShowVideoInfo(body, bvid, sb)

                    SBLine(sb, "")
                    SBLine(sb, "cid = " & av706Cid & "  (再次点击按钮获取 360P 播放)")
                Else
                    If isChinese Then
                        SBLine(sb, "未找到 cid")
                    Else
                        SBLine(sb, "cid not found")
                    End If
                End If
            Else
                ' Second step: fetch playurl (360P MP4) and store it
                If av706Proxy IsNot Nothing Then
                    av706Proxy.Shutdown()
                    av706Proxy = Nothing
                End If

                Dim pp As New System.Collections.Generic.Dictionary(Of String, String)()
                pp("avid") = currentAv
                pp("cid") = av706Cid
                pp("qn") = "16"
                pp("fnval") = "1"
                pp("fnver") = "0"
                pp("platform") = "pc"
                pp("voice_balance") = "1"
                pp("gaia_source") = "pre-load"
                pp("isGaiaAvoided") = "true"
                Dim playUrl As String = signer.SignUrl("https://api.bilibili.com/x/player/wbi/playurl", pp)

                Dim req2 As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(playUrl), System.Net.HttpWebRequest)
                req2.Method = "GET"
                req2.Timeout = 15000
                req2.Accept = "application/json"
                req2.UserAgent = ua
                req2.Referer = "https://www.bilibili.com/"
                ApplyCookies(req2)
                Dim resp2 As System.Net.HttpWebResponse = CType(req2.GetResponse(), System.Net.HttpWebResponse)
                Dim reader2 As New System.IO.StreamReader(resp2.GetResponseStream(), System.Text.Encoding.UTF8)
                Dim playResp As String = reader2.ReadToEnd()
                reader2.Close()
                resp2.Close()

                SBLine(sb, "playurl raw:")
                SBLine(sb, playResp)

                Dim md As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(playResp, """url"":\s*""([^""]+)""")
                If md.Success Then
                    av706VideoUrl = md.Groups(1).Value
                    av706VideoUrl = av706VideoUrl.Replace("\u0026", "&")
                    SBLine(sb, "")
                    SBLine(sb, "360P video url:")
                    SBLine(sb, av706VideoUrl)
                    SBLine(sb, "")
                    SBLine(sb, "已就绪：点击 播放视频")
                Else
                    If isChinese Then
                        SBLine(sb, "未找到播放地址")
                    Else
                        SBLine(sb, "video url not found")
                    End If
                End If
            End If
        Catch ex As Exception
            WriteLog("playvideo: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            If isChinese Then
                SBLine(sb, "获取失败: " & ex.Message)
            Else
                SBLine(sb, "fetch failed: " & ex.Message)
            End If
        End Try
    End Sub

    Private Sub btnPlayStream_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnPlayStream.Click
        ShowResultView()
        btnPlayStream.Enabled = False
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        Dim sb As New System.Text.StringBuilder()

        If String.IsNullOrEmpty(av706VideoUrl) Then
            SBLine(sb, "请先点击「播放视频」获取 360P 地址")
            txtResult.Text = sb.ToString()
            btnPlayStream.Enabled = True
            Return
        End If

        Try
            If av706Proxy IsNot Nothing Then
                av706Proxy.Shutdown()
                av706Proxy = Nothing
            End If

            Dim isDesktop As Boolean = (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)

            If (Not isDesktop) Then
                ' WM device: prefer CorePlayer/TCPMP (they have proper HTTP streaming).
                Dim playerExe As String = FindThirdPartyPlayer()
                If String.IsNullOrEmpty(playerExe) Then
                    ' WMP CE detected: its HTTP stack reads only one ~16KB buffer then
                    ' closes (0x8000FFFF), so it cannot stream over HTTP at all.
                    ' Pseudo-stream instead: download the MP4 to a temp file in the
                    ' background, then launch WMP on the LOCAL path once enough is
                    ' buffered (download >> playback speed, so it stays ahead).
                    SBLine(sb, "")
                    SBLine(sb, "WMP CE detected -> pseudo-stream (download to temp, play local)")
                    StartPseudoStream(sb)
                    txtResult.Text = sb.ToString()
                    btnPlayStream.Enabled = True
                    Return
                Else
                    SBLine(sb, "player: " & playerExe)
                    av706Proxy = New LocalStreamProxy(av706VideoUrl, ua, "https://www.bilibili.com/", "", False)
                    Dim localUrl As String = av706Proxy.Start()
                    SBLine(sb, "")
                    SBLine(sb, "proxy: " & localUrl)
                    SBLine(sb, "proxy-> 360P MP4 (true stream from CDN)")
                    Try
                        System.Diagnostics.Process.Start(playerExe, localUrl)
                    Catch exP As Exception
                        SBLine(sb, "player launch failed: " & exP.Message)
                    End Try
                End If
            Else
                ' Desktop: WMP streams HTTP fine.
                av706Proxy = New LocalStreamProxy(av706VideoUrl, ua, "https://www.bilibili.com/", "", False)
                Dim localUrl As String = av706Proxy.Start()
                SBLine(sb, "")
                SBLine(sb, "proxy: " & localUrl)
                SBLine(sb, "proxy-> 360P MP4 (true stream from CDN)")
                Try
                    System.Diagnostics.Process.Start("wmplayer.exe", "/play " & localUrl)
                Catch exP As Exception
                    SBLine(sb, "player launch failed: " & exP.Message)
                End Try
            End If

            ' Wait briefly, then surface any proxy-side error
            Try
                System.Threading.Thread.Sleep(3000)
            Catch exS As Exception
            End Try
            If av706Proxy IsNot Nothing AndAlso Not String.IsNullOrEmpty(av706Proxy.LastError) Then
                SBLine(sb, "proxy error: " & av706Proxy.LastError)
            ElseIf av706Proxy IsNot Nothing Then
                SBLine(sb, "proxy: no error after 3s (client may have streamed)")
            End If
        Catch ex As Exception
            WriteLog("stream: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            SBLine(sb, "流式播放失败: " & ex.Message)
        End Try

        txtResult.Text = sb.ToString()
        btnPlayStream.Enabled = True
    End Sub

    ' ---- QR scan login (mirrors Android BiliClassic QRLoginFragment) ----
    ' API: generate -> { qrcode_key, url }; poll -> code: 0=success, 86090=scanned,
    ' 86101=not scanned, 86038=expired. Poll every ~1s like the Android client.

    Private Sub btnMine_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnMine.Click
        ShowProfile()
    End Sub

    Private Sub mnuProfile_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuProfile.Click
        StopLoginPolling()
        ShowProfile()
    End Sub

    Private Sub mnuFavs_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuFavs.Click
        StopLoginPolling()
        LoadFavoriteFolders()
    End Sub

    ' 收藏夹：先取 mid，再显示收藏夹列表。列表显示在 lstSearch。
    Private Sub LoadFavoriteFolders()
        If String.IsNullOrEmpty(savedCookies) Then
            ShowResultView()
            If isChinese Then
                txtResult.Text = "请先登录后再查看收藏夹"
            Else
                txtResult.Text = "Please log in first to view favorites"
            End If
            Return
        End If

        ShowResultView()
        lstSearch.BringToFront()
        lstSearch.Visible = True
        txtResult.Visible = False
        lstSearch.Items.Clear()
        lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "正在加载收藏夹...", "Loading favorites...")))
        favView = "folders"
        favLoading = True

        Dim t As New System.Threading.Thread(AddressOf FetchFoldersWorker)
        t.Start()
    End Sub

    Private Sub FetchFoldersWorker()
        Dim body As String = ""
        Dim errMsg As String = ""
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        Try
            If String.IsNullOrEmpty(favMid) Then
                favMid = FetchMidFromNav()
            End If
            If String.IsNullOrEmpty(favMid) Then
                errMsg = If(isChinese, "无法获取账号 UID，请重新登录", "Cannot get UID, please re-login")
            Else
                Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                Dim url As String = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" & favMid & "&type=0"
                Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
                req.Method = "GET"
                req.Timeout = 15000
                req.Accept = "application/json"
                req.UserAgent = ua
                req.Referer = "https://space.bilibili.com/"
                ApplyCookies(req)
                Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
                Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
                body = reader.ReadToEnd()
                reader.Close()
                resp.Close()
                WriteLog("fav folders: " & Microsoft.VisualBasic.Left(body, 200))
            End If
        Catch ex As Exception
            errMsg = ex.GetType().FullName & " | " & ex.Message
            WriteLog("fav folders: " & errMsg & " | " & ex.StackTrace)
        End Try

        favLastBody = body
        favLastErr = errMsg
        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowFoldersOnUi))
        Catch ex As Exception
            WriteLog("fav folders invoke: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub ShowFoldersOnUi()
        favLoading = False
        lstSearch.Items.Clear()
        If favLastErr <> "" Then
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(favLastErr))
            lstSearch.Enabled = True
            Return
        End If

        Dim body As String = favLastBody
        Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
        If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "获取收藏夹失败 (code=" & mCode.Groups(1).Value & ")", "Favorites failed (code=" & mCode.Groups(1).Value & ")")))
            lstSearch.Enabled = True
            Return
        End If

        Dim folderIdx As Integer = 0
        For Each fm As System.Text.RegularExpressions.Match In System.Text.RegularExpressions.Regex.Matches(body, """fid"":\s*(\d+)")
            If folderIdx >= 50 Then
                Exit For
            End If
            Dim fid As String = fm.Groups(1).Value
            ' 收藏夹对象结构: {"id":...,"fid":<fid>,"mid":...,"attr":...,"title":"...",...,"media_count":...}
            ' title 在 fid 之后。取 fid 到本对象结束(})之间的文本，避免跨对象误匹配。
            Dim objEnd As Integer = body.IndexOf("}", fm.Index)
            If objEnd < 0 Then
                objEnd = body.Length
            End If
            Dim after As String = body.Substring(fm.Index, objEnd - fm.Index)
            Dim mt As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, """title"":\s*""((?:[^""\\]|\\.)*)""")
            Dim title As String = ""
            If mt.Success Then
                title = StripHtml(UnescapeJson(mt.Groups(1).Value))
            End If
            If title = "" Then
                title = If(isChinese, "未命名收藏夹", "Untitled folder")
            End If
            Dim mCnt As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, """media_count"":\s*(\d+)")
            Dim cnt As String = ""
            If mCnt.Success Then
                cnt = mCnt.Groups(1).Value
            End If
            Dim line As String = title
            If cnt <> "" Then
                line &= "  [" & cnt & If(isChinese, "个视频", " videos") & "]"
            End If
            line &= "  #fid=" & fid
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(line))
            folderIdx += 1
        Next

        If lstSearch.Items.Count = 0 Then
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "没有收藏夹", "No favorite folders")))
        End If
        lstSearch.Enabled = True
    End Sub

    ' 进入收藏夹：显示其中的视频列表。
    Private Sub OpenFavoriteFolder(ByVal fid As String, ByVal name As String)
        favFolderId = fid
        favFolderName = name
        favView = "videos"
        favPage = 1
        favHasMore = True
        lstSearch.BringToFront()
        lstSearch.Visible = True
        txtResult.Visible = False
        lstSearch.Items.Clear()
        LoadFolderVideosPage(False)
    End Sub

    Private Sub LoadFolderVideosPage(ByVal loadMore As Boolean)
        If favLoading Then
            Return
        End If
        favLoading = True
        If loadMore Then
            Dim idx As Integer = lstSearch.Items.Count - 1
            If idx >= 0 Then
                lstSearch.Items.RemoveAt(idx)
            End If
        End If
        lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "正在加载更多...", "Loading more...")))
        Dim t As New System.Threading.Thread(AddressOf FetchVideosWorker)
        t.Start()
    End Sub

    Private Sub FetchVideosWorker()
        Dim errMsg As String = ""
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try
        Try
            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Dim url As String = "https://api.bilibili.com/x/space/fav/arc?vmid=" & favMid _
                & "&ps=30&fid=" & favFolderId & "&tid=0&keyword=&pn=" & favPage.ToString() & "&order=fav_time"
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 15000
            req.Accept = "application/json"
            req.UserAgent = ua
            req.Referer = "https://space.bilibili.com/"
            ApplyCookies(req)
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            favLastBody = reader.ReadToEnd()
            reader.Close()
            resp.Close()
            WriteLog("fav videos: " & Microsoft.VisualBasic.Left(favLastBody, 200))
        Catch ex As Exception
            errMsg = ex.GetType().FullName & " | " & ex.Message
            WriteLog("fav videos: " & errMsg & " | " & ex.StackTrace)
        End Try
        favLastErr = errMsg
        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowVideosOnUi))
        Catch ex As Exception
            WriteLog("fav videos invoke: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub ShowVideosOnUi()
        favLoading = False
        Dim idx As Integer = lstSearch.Items.Count - 1
        If idx >= 0 Then
            lstSearch.Items.RemoveAt(idx)
        End If
        If favLastErr <> "" Then
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(favLastErr))
            lstSearch.Enabled = True
            Return
        End If

        Dim body As String = favLastBody
        Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
        If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "获取视频失败 (code=" & mCode.Groups(1).Value & ")", "Videos failed (code=" & mCode.Groups(1).Value & ")")))
            lstSearch.Enabled = True
            Return
        End If

        Dim added As Integer = 0
        Dim seenBv As New System.Collections.ArrayList()
        ' fav/arc 接口不返回独立 "bvid" 字段，BV 号出现在 short_link_v2 等位置。
        ' 每个 archive 对象以 {"aid" 开头，title 在对象开头，BV 在末尾的 short_link_v2。
        ' 用非贪婪正则按 {"aid" ... "short_link_v2":"https://b23.tv/BV..." 切出对象块。
        For Each objM As System.Text.RegularExpressions.Match In _
            System.Text.RegularExpressions.Regex.Matches(body, "\{""aid"":.*?""short_link_v2"":""https://b23.tv/(BV1[0-9A-Za-z]{9})")
            Dim bv As String = objM.Groups(1).Value
            If seenBv.Contains(bv) Then
                Continue For
            End If
            seenBv.Add(bv)
            Dim title As String = ""
            Dim mt As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(objM.Value, """title"":\s*""((?:[^""\\]|\\.)*)""")
            If mt.Success Then
                title = StripHtml(UnescapeJson(mt.Groups(1).Value))
            End If
            If title = "" Then
                title = If(isChinese, "无标题", "(no title)")
            End If
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(title & "  #bv=" & bv))
            added += 1
        Next

        ' 用接口的 pagecount 判断是否还有更多页（不能只看本页视频数，
        ' 偶有视频缺 short_link_v2 导致本页 < ps=30 但后面仍有更多）。
        Dim mPc As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """pagecount"":\s*(\d+)")
        Dim pageCount As Integer = 0
        If mPc.Success Then
            pageCount = CInt(mPc.Groups(1).Value)
        End If
        favHasMore = (pageCount > favPage)
        If added > 0 Then
            If favHasMore Then
                lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "加载更多视频 " & FAV_PAGE_MARKER, "Load more videos " & FAV_PAGE_MARKER)))
            End If
        Else
            lstSearch.Items.Add(New System.Windows.Forms.ListViewItem(If(isChinese, "这个收藏夹还没有视频", "This folder has no videos")))
        End If
        lstSearch.Enabled = True
    End Sub

    ' 从 nav API 获取当前登录账号的 mid。
    Private Function FetchMidFromNav() As String
        Try
            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create("https://api.bilibili.com/x/web-interface/nav"), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 10000
            req.Accept = "application/json"
            req.UserAgent = ua
            req.Referer = "https://www.bilibili.com/"
            ApplyCookies(req)
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()
            Dim mM As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """mid"":\s*(\d+)")
            If mM.Success Then
                Return mM.Groups(1).Value
            End If
        Catch ex As Exception
            WriteLog("fav mid: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
        Return ""
    End Function

    Private Sub ShowProfile()
        ShowResultView()
        btnMine.Enabled = False
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        Dim sb As New System.Text.StringBuilder()
        Try
            If String.IsNullOrEmpty(savedCookies) Then
                If isChinese Then
                    SBLine(sb, "未登录。请点击「扫码登录」后再试。")
                Else
                    SBLine(sb, "Not logged in. Please tap QR Login first.")
                End If
                txtResult.Text = sb.ToString()
                btnMine.Enabled = True
                Return
            End If

            SBLine(sb, If(isChinese, "正在加载个人中心...", "Loading profile..."))
            txtResult.Text = sb.ToString()

            ' Nav API returns uname / mid / money / vip for the logged-in account.
            Dim navUrl As String = "https://api.bilibili.com/x/web-interface/nav"
            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(navUrl), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 15000
            req.Accept = "application/json"
            req.UserAgent = ua
            req.Referer = "https://www.bilibili.com/"
            ApplyCookies(req)
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()

            WriteLog("mine: " & Microsoft.VisualBasic.Left(body, 200))

            Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
            If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
                If isChinese Then
                    SBLine(sb, "获取个人信息失败 (code=" & mCode.Groups(1).Value & ")，请重新扫码登录")
                Else
                    SBLine(sb, "Profile failed (code=" & mCode.Groups(1).Value & "), please re-login")
                End If
                txtResult.Text = sb.ToString()
                btnMine.Enabled = True
                Return
            End If

            Dim uname As String = ""
            Dim mU As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """uname"":\s*""([^""]*)""")
            If mU.Success Then
                uname = UnescapeJson(mU.Groups(1).Value)
            End If
            Dim mid As String = ""
            Dim mM As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """mid"":\s*(\d+)")
            If mM.Success Then
                mid = mM.Groups(1).Value
            End If
            Dim money As String = "0"
            Dim mCo As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """money"":\s*(\d+)")
            If mCo.Success Then
                money = mCo.Groups(1).Value
            End If
            Dim isVip As Boolean = False
            Dim mVipType As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """type"":\s*(\d+)")
            Dim mVipStatus As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """status"":\s*(\d+)")
            If mVipType.Success AndAlso mVipStatus.Success Then
                If CLng(mVipType.Groups(1).Value) > 0 AndAlso CLng(mVipStatus.Groups(1).Value) = 1 Then
                    isVip = True
                End If
            End If

            sb = New System.Text.StringBuilder()
            SBLine(sb, "==============")
            SBLine(sb, If(isChinese, "我的", "Mine"))
            SBLine(sb, "==============")
            If uname <> "" Then
                SBLine(sb, If(isChinese, "用户名: ", "Name: ") & uname)
            Else
                SBLine(sb, If(isChinese, "用户名: （未知）", "Name: (unknown)"))
            End If
            If mid <> "" Then
                SBLine(sb, "UID: " & mid)
            End If
            SBLine(sb, If(isChinese, "硬币: ", "Coins: ") & money)
            If isVip Then
                SBLine(sb, If(isChinese, "VIP: 大会员", "VIP: Big Member"))
            Else
                SBLine(sb, If(isChinese, "VIP: 普通用户", "VIP: Regular"))
            End If
            SBLine(sb, "")
            SBLine(sb, If(isChinese, "点上方菜单「设置」可查看播放历史/检查更新。", "Use the Settings menu above for History / Update check."))
            txtResult.Text = sb.ToString()
            WriteLog("mine OK: uname=" & uname & " mid=" & mid)
        Catch ex As Exception
            WriteLog("mine: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            sb = New System.Text.StringBuilder()
            If isChinese Then
                SBLine(sb, "个人中心加载失败: " & ex.Message)
            Else
                SBLine(sb, "Profile load failed: " & ex.Message)
            End If
            txtResult.Text = sb.ToString()
        Finally
            btnMine.Enabled = True
        End Try
    End Sub

    Private Sub btnLogin_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnLogin.Click
        ShowLogin()
    End Sub

    Private Sub mnuLogin_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuLogin.Click
        If loginPolling Then
            StopLoginPolling()
            ShowResultView()
            If isChinese Then
                txtResult.Text = "已取消扫码登录"
            Else
                txtResult.Text = "QR login cancelled"
            End If
            Return
        End If
        ShowLogin()
    End Sub

    Private Sub StopLoginPolling()
        loginPolling = False
        If loginTimer IsNot Nothing Then
            loginTimer.Enabled = False
        End If
        Try
            picQr.Image = Nothing
        Catch ex As Exception
        End Try
        UpdateLoginMenuText()
    End Sub

    Private Sub UpdateLoginMenuText()
        Try
            If loginPolling Then
                mnuLogin.Text = If(isChinese, "取消扫码", "Cancel Login")
            Else
                mnuLogin.Text = If(isChinese, "扫码登录", "QR Login")
            End If
        Catch ex As Exception
        End Try
    End Sub

    Private Sub ShowLogin()
        ShowResultView()
        btnLogin.Enabled = False
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        If isChinese Then
            txtResult.Text = "正在获取二维码..."
        Else
            txtResult.Text = "Fetching QR code..."
        End If

        Try
            ' 1. GET generate -> parse qrcode_key + url
            Dim genUrl As String = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F"
            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(genUrl), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 20000
            req.Accept = "application/json"
            req.UserAgent = ua
            req.Referer = "https://www.bilibili.com/"
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()

            Dim mKey As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """qrcode_key"":\s*""([^""]+)""")
            Dim mUrl As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """url"":\s*""([^""]+)""")
            If (Not mKey.Success) OrElse (Not mUrl.Success) Then
                If isChinese Then
                    txtResult.Text = "二维码获取失败（无 qrcode_key）"
                Else
                    txtResult.Text = "QR fetch failed (no qrcode_key)"
                End If
                btnLogin.Enabled = True
                Return
            End If

            loginQrKey = mKey.Groups(1).Value
            Dim qrContent As String = mUrl.Groups(1).Value
            qrContent = qrContent.Replace("\u0026", "&")

            ' 2. Generate the QR bitmap locally (Nayuki algorithm port, byte mode)
            Dim bmp As System.Drawing.Bitmap = QrCode.MakeQrBitmap(qrContent, QrEcc.ECC_MEDIUM, 3, 2)
            picQr.Image = bmp

            ' 3. Start polling every 1s, expire after 180s like the web login
            loginExpireTick = System.Environment.TickCount
            loginPolling = True
            If loginTimer Is Nothing Then
                loginTimer = New System.Windows.Forms.Timer()
                loginTimer.Interval = 1000
                AddHandler loginTimer.Tick, AddressOf LoginTimer_Tick
            End If
            loginTimer.Enabled = True
            UpdateLoginMenuText()

            If isChinese Then
                txtResult.Text = "请用 B 站手机 App 扫码登录（180 秒有效）"
            Else
                txtResult.Text = "Scan with the Bilibili app (valid 180s)"
            End If
        Catch ex As Exception
            WriteLog("login: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            If isChinese Then
                txtResult.Text = "扫码登录失败: " & ex.Message
            Else
                txtResult.Text = "QR login failed: " & ex.Message
            End If
        End Try

        btnLogin.Enabled = True
    End Sub

    Private Sub LoginTimer_Tick(ByVal sender As Object, ByVal e As System.EventArgs)
        If (Not loginPolling) Then
            Return
        End If

        ' Expire after 180s
        If (System.Environment.TickCount - loginExpireTick) >= 180000 Then
            loginPolling = False
            loginTimer.Enabled = False
            If isChinese Then
                txtResult.Text = "二维码已过期，请重新获取"
            Else
                txtResult.Text = "QR expired, please refresh"
            End If
            Return
        End If

        Try
            Dim pollUrl As String = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?source=main-fe-header&qrcode_key=" & loginQrKey
            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(pollUrl), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 10000
            req.Accept = "application/json"
            req.UserAgent = ua
            req.Referer = "https://www.bilibili.com/"
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()

            Dim mCode As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, """code"":\s*(-?\d+)")
            If mCode.Count = 0 Then
                Return
            End If
            ' The response has an outer code (HTTP-level, always 0) and the real
            ' login state in data.code. Take the LAST match (data.code).
            Dim code As Integer = Integer.Parse(mCode(mCode.Count - 1).Groups(1).Value)

            Select Case code
                Case 0 ' Success
                    loginPolling = False
                    loginTimer.Enabled = False
                    picQr.Image = Nothing
                    SaveLoginCookies(body, resp)
                    UpdateLoginMenuText()
                    If isChinese Then
                        txtResult.Text = "登录成功！" & Chr(13) & Chr(10) & body
                    Else
                        txtResult.Text = "Login OK!" & Chr(13) & Chr(10) & body
                    End If
                Case 86090 ' Scanned, waiting for confirmation on phone
                    If isChinese Then
                        txtResult.Text = "已扫码，请在手机上确认登录..."
                    Else
                        txtResult.Text = "Scanned, confirm on your phone..."
                    End If
                Case 86038 ' Expired
                    loginPolling = False
                    loginTimer.Enabled = False
                    UpdateLoginMenuText()
                    If isChinese Then
                        txtResult.Text = "二维码已过期，请重新获取"
                    Else
                        txtResult.Text = "QR expired, please refresh"
                    End If
                Case Else ' 86101 = not scanned yet, keep waiting
                    If isChinese Then
                        txtResult.Text = "等待扫码..."
                    Else
                        txtResult.Text = "Waiting for scan..."
                    End If
            End Select
        Catch ex As Exception
            WriteLog("login poll: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    ' ---- Cookie persistence ----
    ' On login success, Bilibili returns a redirect URL in data.url whose query
    ' string carries the auth cookies (SESSDATA, bili_jct, DedeUserID, ...).
    ' We extract those and persist them so later API calls are authenticated.

    Private Function GetAppDir() As String
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

    Private Function GetCookiePath() As String
        Return GetAppDir() & "\cookie.txt"
    End Function

    Private Sub SaveLoginCookies(ByVal body As String, ByVal resp As System.Net.HttpWebResponse)
        Try
            ' Bilibili now delivers the auth cookies in the Set-Cookie response
            ' headers and/or a data.cookies JSON array, NOT in data.url.
            Dim sb As New System.Text.StringBuilder()
            Dim names As String() = New String() {"SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid"}

            ' Source 1: data.url query params (legacy, still seen on some accounts)
            Dim mUrl As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """url"":\s*""([^""]+)""")
            If mUrl.Success Then
                Dim redirectUrl As String = mUrl.Groups(1).Value.Replace("\u0026", "&")
                For Each n As String In names
                    Dim v As String = GetQueryParam(redirectUrl, n)
                    If Not String.IsNullOrEmpty(v) Then
                        AppendCookie(sb, n, v)
                    End If
                Next
            End If

            ' Source 2: data.cookies JSON array. Bilibili sends it either as
            '   [{"name":"SESSDATA","value":"xxx",...}, ...]   (object form)
            '   [["SESSDATA","xxx"], ...]                      (pair form)
            If sb.Length = 0 Then
                Dim mc As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, """name""\s*:\s*""(SESSDATA|bili_jct|DedeUserID|DedeUserID__ckMd5|sid)""\s*,\s*""value""\s*:\s*""([^""]*)""")
                For Each m As System.Text.RegularExpressions.Match In mc
                    AppendCookie(sb, m.Groups(1).Value, m.Groups(2).Value)
                Next
            End If
            If sb.Length = 0 Then
                Dim mc2 As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, "\[""(SESSDATA|bili_jct|DedeUserID|DedeUserID__ckMd5|sid)""\s*,\s*""([^""]*)""\]")
                For Each m As System.Text.RegularExpressions.Match In mc2
                    AppendCookie(sb, m.Groups(1).Value, m.Groups(2).Value)
                Next
            End If

            ' Source 3: Set-Cookie response headers
            If sb.Length = 0 Then
                Try
                    For i As Integer = 0 To resp.Headers.Count - 1
                        Dim k As String = resp.Headers.Keys(i)
                        If String.Compare(k, "Set-Cookie", True) = 0 Then
                            Dim hdr As String = resp.Headers(i)
                            For Each n As String In names
                                Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(hdr, n & "=([^;,\s]+)")
                                If m.Success Then
                                    AppendCookie(sb, n, m.Groups(1).Value)
                                End If
                            Next
                        End If
                    Next
                Catch exH As Exception
                    WriteLog("login cookie headers: " & exH.GetType().FullName & " | " & exH.Message)
                End Try
            End If

            savedCookies = sb.ToString()
            If savedCookies.Length = 0 Then
                WriteLog("login: no auth cookies found (url/cookies/headers)")
                Return
            End If

            Try
                System.IO.Directory.CreateDirectory(GetAppDir())
            Catch exD As Exception
            End Try
            Dim fs As New System.IO.FileStream(GetCookiePath(), System.IO.FileMode.Create)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.UTF8)
            w.Write(savedCookies)
            w.Close()
            fs.Close()
            WriteLog("login: cookies saved (" & savedCookies.Length.ToString() & " chars)")

            ' Verify the session works: fetch the nav API (returns user name)
            VerifyLogin()
        Catch ex As Exception
            WriteLog("login save: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub AppendCookie(ByVal sb As System.Text.StringBuilder, ByVal n As String, ByVal v As String)
        If String.IsNullOrEmpty(v) Then
            Return
        End If
        If sb.Length > 0 Then
            sb.Append("; ")
        End If
        sb.Append(n)
        sb.Append("=")
        sb.Append(v)
    End Sub

    Private Function GetQueryParam(ByVal url As String, ByVal name As String) As String
        Try
            Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(url, "[?&]" & name & "=([^&]+)")
            If m.Success Then
                Return m.Groups(1).Value
            End If
        Catch ex As Exception
        End Try
        Return ""
    End Function

    Private Sub LoadCookies()
        savedCookies = ""
        Try
            If System.IO.File.Exists(GetCookiePath()) Then
                Dim sr As New System.IO.StreamReader(GetCookiePath())
                savedCookies = sr.ReadToEnd()
                sr.Close()
                If Not String.IsNullOrEmpty(savedCookies) Then
                    WriteLog("login: loaded saved cookies")
                End If
            End If
        Catch ex As Exception
            WriteLog("login load: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub VerifyLogin()
        Try
            Dim navUrl As String = "http://api.bilibili.com/x/web-interface/nav"
            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(navUrl), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 10000
            req.Accept = "application/json"
            req.UserAgent = ua
            req.Referer = "https://www.bilibili.com/"
            ApplyCookies(req)
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Dim reader As New System.IO.StreamReader(resp.GetResponseStream(), System.Text.Encoding.UTF8)
            Dim body As String = reader.ReadToEnd()
            reader.Close()
            resp.Close()

            Dim mU As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """uname"":\s*""([^""]*)""")
            If mU.Success Then
                loginUserName = mU.Groups(1).Value
                WriteLog("login: verified as " & loginUserName)
                If isChinese Then
                    txtResult.Text = "登录成功！欢迎 " & loginUserName
                Else
                    txtResult.Text = "Login OK! Welcome " & loginUserName
                End If
            Else
                WriteLog("login: nav response had no uname")
            End If
        Catch ex As Exception
            WriteLog("login verify: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub ApplyCookies(ByVal req As System.Net.HttpWebRequest)
        If String.IsNullOrEmpty(savedCookies) Then
            Return
        End If
        Try
            req.Headers("Cookie") = savedCookies
        Catch ex As Exception
            WriteLog("cookie apply: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub btnPlayOffline_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnPlayOffline.Click
        ShowResultView()
        btnPlayOffline.Enabled = False
        Try
            System.Net.ServicePointManager.CertificatePolicy = New TrustAllPolicy()
        Catch exCert As Exception
        End Try

        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        Dim sb As New System.Text.StringBuilder()

        If String.IsNullOrEmpty(av706VideoUrl) Then
            SBLine(sb, "请先点击「播放视频」获取 360P 地址")
            txtResult.Text = sb.ToString()
            btnPlayOffline.Enabled = True
            Return
        End If

        Try
            ' Download the 360P MP4 to a local temp file, then play it.
            SBLine(sb, "")
            SBLine(sb, "downloading 360P MP4...")
            Dim dlPath As String = "\Temp\biliclassic_av706.mp4"
            Dim reqD As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(av706VideoUrl), System.Net.HttpWebRequest)
            reqD.Method = "GET"
            reqD.Timeout = 60000
            reqD.Accept = "*/*"
            reqD.UserAgent = ua
            reqD.Referer = "https://www.bilibili.com/"
            Dim respD As System.Net.HttpWebResponse = CType(reqD.GetResponse(), System.Net.HttpWebResponse)
            Dim totalLen As Long = respD.ContentLength
            Dim dlIn As System.IO.Stream = respD.GetResponseStream()
            Dim dlOut As System.IO.FileStream = New System.IO.FileStream(dlPath, System.IO.FileMode.Create)
            Dim dlBuf(16383) As Byte
            Dim dlN As Integer = dlIn.Read(dlBuf, 0, dlBuf.Length)
            Dim dlTotal As Long = 0
            While dlN > 0
                dlOut.Write(dlBuf, 0, dlN)
                dlTotal += dlN
                If dlTotal Mod 1000000 < 16384 Then
                    SBLine(sb, "downloaded " & dlTotal.ToString() & " / " & totalLen.ToString())
                End If
                dlN = dlIn.Read(dlBuf, 0, dlBuf.Length)
            End While
            dlOut.Close()
            dlIn.Close()
            respD.Close()
            SBLine(sb, "done: " & dlTotal.ToString() & " bytes saved to " & dlPath)
            Try
                System.Diagnostics.Process.Start("\Windows\wmplayer.exe", dlPath)
            Catch exP As Exception
                SBLine(sb, "wmplayer launch failed: " & exP.Message)
            End Try
        Catch ex As Exception
            WriteLog("offline: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            SBLine(sb, "离线播放失败: " & ex.Message)
        End Try

        txtResult.Text = sb.ToString()
        btnPlayOffline.Enabled = True
    End Sub

    ' Pseudo-streaming for WMP CE: download the MP4 to a temp file in the
    ' background; once enough bytes are buffered (moov + a few MB of media)
    ' launch WMP on the LOCAL file so it avoids its 16KB HTTP-read bug.
    Private Sub StartPseudoStream(ByVal sb As System.Text.StringBuilder)
        Try
            If av706Proxy IsNot Nothing Then
                av706Proxy.Shutdown()
                av706Proxy = Nothing
            End If

            pseudoRun += 1
            pseudoPath = "\Temp\biliclassic_av706_" & pseudoRun.ToString() & ".mp4"
            pseudoTotal = 0
            pseudoDownloaded = 0
            pseudoLaunched = False
            pseudoDone = False
            pseudoError = ""

            SBLine(sb, "buffering to " & pseudoPath & " ...")

            Dim t As New System.Threading.Thread(AddressOf PseudoStreamWorker)
            t.IsBackground = True
            t.Start()

            ' Wait until WMP has been launched (buffered enough) or an error.
            Dim waited As Integer = 0
            While (Not pseudoLaunched) AndAlso (Not pseudoDone) AndAlso (pseudoError.Length = 0) AndAlso waited < 60000
                System.Threading.Thread.Sleep(250)
                waited += 250
            End While

            If pseudoError.Length > 0 Then
                SBLine(sb, "pseudo-stream error: " & pseudoError)
            ElseIf pseudoLaunched Then
                SBLine(sb, "buffered " & pseudoDownloaded.ToString() & " bytes, WMP playing local file")
            ElseIf pseudoDone Then
                SBLine(sb, "download finished, launching WMP on " & pseudoPath)
                Try
                    System.Diagnostics.Process.Start("\Windows\wmplayer.exe", pseudoPath)
                Catch exP As Exception
                    SBLine(sb, "wmplayer launch failed: " & exP.Message)
                End Try
            Else
                SBLine(sb, "timed out waiting for buffer")
            End If
        Catch ex As Exception
            WriteLog("pseudo: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            SBLine(sb, "pseudo-stream failed: " & ex.Message)
        End Try
    End Sub

    Private Sub PseudoStreamWorker()
        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        Try
            Dim reqD As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(av706VideoUrl), System.Net.HttpWebRequest)
            reqD.Method = "GET"
            reqD.Timeout = 60000
            reqD.Accept = "*/*"
            reqD.UserAgent = ua
            reqD.Referer = "https://www.bilibili.com/"
            Dim respD As System.Net.HttpWebResponse = CType(reqD.GetResponse(), System.Net.HttpWebResponse)
            pseudoTotal = respD.ContentLength
            Dim dlIn As System.IO.Stream = respD.GetResponseStream()
            Dim dlOut As System.IO.FileStream = New System.IO.FileStream(pseudoPath, System.IO.FileMode.Create, System.IO.FileAccess.Write, System.IO.FileShare.Read)
            Dim dlBuf(16383) As Byte
            Dim dlN As Integer = dlIn.Read(dlBuf, 0, dlBuf.Length)
            Dim launchAfter As Long = 2097152 ' launch once 2MB buffered
            If pseudoTotal > 0 AndAlso pseudoTotal < launchAfter Then
                launchAfter = pseudoTotal
            End If
            While dlN > 0
                dlOut.Write(dlBuf, 0, dlN)
                pseudoDownloaded += dlN
                If (Not pseudoLaunched) AndAlso pseudoDownloaded >= launchAfter Then
                    pseudoLaunched = True
                    WriteLog("pseudo: launching WMP after " & pseudoDownloaded.ToString() & " bytes")
                    Try
                        System.Diagnostics.Process.Start("\Windows\wmplayer.exe", pseudoPath)
                    Catch exP As Exception
                        pseudoError = "wmplayer launch failed: " & exP.Message
                    End Try
                End If
                dlN = dlIn.Read(dlBuf, 0, dlBuf.Length)
            End While
            dlOut.Close()
            dlIn.Close()
            respD.Close()
            pseudoDone = True
            If (Not pseudoLaunched) AndAlso pseudoError.Length = 0 Then
                pseudoError = "download ended before launch threshold (got " & pseudoDownloaded.ToString() & " bytes)"
            End If
        Catch ex As Exception
            pseudoError = ex.GetType().FullName & " | " & ex.Message
            pseudoDone = True
            WriteLog("pseudo worker: " & pseudoError & " | " & ex.StackTrace)
        End Try
    End Sub

    Private Sub OnUnhandledException(ByVal sender As Object, ByVal e As System.UnhandledExceptionEventArgs)
        Try
            Dim ex As Exception = TryCast(e.ExceptionObject, Exception)
            If ex IsNot Nothing Then
                WriteLog("unhandled: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            Else
                WriteLog("unhandled (non-exception): " & e.ExceptionObject.ToString())
            End If
        Catch exLog As Exception
        End Try
    End Sub

    Private Sub WriteLog(ByVal msg As String)
        Try
            Dim logPath As String = ""
            Try
                logPath = System.IO.Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().GetName().CodeBase.Replace("file:///", ""))
            Catch exP As Exception
                logPath = "\Program Files\BiliClassic"
            End Try
            If String.IsNullOrEmpty(logPath) Then
                logPath = "\Program Files\BiliClassic"
            End If
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

    Private Function FindThirdPartyPlayer() As String
        Try
            ' 1. Known common paths
            Dim rootDirs As String() = New String() {"\Program Files", "\Storage Card\Program Files", "\Storage Card", "\Windows"}
            Dim candidates As String() = New String() {"CorePlayer\CorePlayer.exe", "TCPMP\tcpmp.exe", "CorePlayer\tcpmp.exe", "tcpmp\tcpmp.exe", "CorePlayer.exe", "tcpmp.exe", "TCPMP\TCPMP.exe"}
            For Each d As String In rootDirs
                If String.IsNullOrEmpty(d) OrElse Not System.IO.Directory.Exists(d) Then
                    Continue For
                End If
                For Each c As String In candidates
                    Try
                        Dim p As String = System.IO.Path.Combine(d, c)
                        If System.IO.File.Exists(p) Then
                            WriteLog("findplayer: hit " & p)
                            Return p
                        End If
                    Catch exF As Exception
                    End Try
                Next c
            Next d

            ' 2. Recursive scan of Program Files roots (depth 3) for exe names
            Dim scanRoots As String() = New String() {"\Program Files", "\Storage Card\Program Files"}
            Dim names As String() = New String() {"tcpmp", "coreplayer", "core player", "coreplayer1"}
            For Each d As String In scanRoots
                If String.IsNullOrEmpty(d) OrElse Not System.IO.Directory.Exists(d) Then
                    Continue For
                End If
                Dim found As String = ScanForPlayerExe(d, names, 0)
                If Not String.IsNullOrEmpty(found) Then
                    WriteLog("findplayer: scan hit " & found)
                    Return found
                End If
            Next d

            ' 3. Parse Start Menu / Programs shortcuts
            Dim sdirs As String() = New String() {GetSpecialFolder(CSIDL_PROGRAMS), GetSpecialFolder(CSIDL_STARTMENU)}
            For Each sd As String In sdirs
                If String.IsNullOrEmpty(sd) OrElse Not System.IO.Directory.Exists(sd) Then
                    Continue For
                End If
                Dim hit As String = FindPlayerViaShortcuts(sd)
                If Not String.IsNullOrEmpty(hit) Then
                    WriteLog("findplayer: shortcut hit " & hit)
                    Return hit
                End If
            Next sd

            WriteLog("findplayer: not found anywhere")
        Catch ex As Exception
            WriteLog("findplayer: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
        Return ""
    End Function

    Private Function ScanForPlayerExe(ByVal dir As String, ByVal names As String(), ByVal depth As Integer) As String
        If depth > 3 Then
            Return ""
        End If
        Try
            Dim files As String() = System.IO.Directory.GetFiles(dir)
            For Each f As String In files
                Try
                    Dim low As String = f.ToLower()
                    If Not low.EndsWith(".exe") Then
                        Continue For
                    End If
                    For Each n As String In names
                        If low.IndexOf(n) >= 0 Then
                            Return f
                        End If
                    Next n
                Catch exF As Exception
                End Try
            Next f
            Dim dirs As String() = System.IO.Directory.GetDirectories(dir)
            For Each sd As String In dirs
                Try
                    Dim r As String = ScanForPlayerExe(sd, names, depth + 1)
                    If Not String.IsNullOrEmpty(r) Then
                        Return r
                    End If
                Catch exD As Exception
                End Try
            Next sd
        Catch ex As Exception
        End Try
        Return ""
    End Function

    Private Function FindPlayerViaShortcuts(ByVal dir As String) As String
        Try
            Dim lnks As String() = System.IO.Directory.GetFiles(dir, "*.lnk")
            For Each l As String In lnks
                Try
                    Dim sr As New System.IO.StreamReader(l)
                    Dim content As String = sr.ReadToEnd()
                    sr.Close()
                    Dim low As String = content.ToLower()
                    If low.IndexOf("tcpmp") >= 0 OrElse low.IndexOf("coreplayer") >= 0 Then
                        WriteLog("findplayer: lnk=" & l & " -> " & content)
                        Dim m As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(content, """([^""]+\.exe)""", System.Text.RegularExpressions.RegexOptions.IgnoreCase)
                        If m.Success Then
                            Return m.Groups(1).Value
                        End If
                    End If
                Catch exL As Exception
                End Try
            Next l
        Catch ex As Exception
        End Try
        Return ""
    End Function

End Class