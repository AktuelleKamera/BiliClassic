' 内置 DirectShow 播放器界面（全屏，内嵌 PlayerControl）。
' .NET CF 2.0 兼容。
Public Class PlayerForm
    Inherits System.Windows.Forms.Form

    Private mPlayer As PlayerControl = Nothing
    Private mBackBtn As System.Windows.Forms.Button = Nothing
    Private mStatusLbl As System.Windows.Forms.Label = Nothing
    Private mIsChinese As Boolean = False
    Private mStarted As Boolean = False

    Public Event PlaybackEnded As EventHandler

    Public Sub New(ByVal isChinese As Boolean)
        MyBase.New()
        mIsChinese = isChinese
        Me.FormBorderStyle = System.Windows.Forms.FormBorderStyle.None
        Me.WindowState = System.Windows.Forms.FormWindowState.Maximized
        Me.Location = New System.Drawing.Point(0, 0)
        Me.BackColor = System.Drawing.Color.Black

        ' 返回按钮（顶部左侧）。
        mBackBtn = New System.Windows.Forms.Button()
        mBackBtn.Text = If(mIsChinese, "返回", "Back")
        mBackBtn.Location = New System.Drawing.Point(4, 4)
        mBackBtn.Size = New System.Drawing.Size(64, 28)
        AddHandler mBackBtn.Click, AddressOf OnBackClick
        Me.Controls.Add(mBackBtn)

        ' 状态标签（加载中/错误）。
        mStatusLbl = New System.Windows.Forms.Label()
        mStatusLbl.Text = If(mIsChinese, "正在加载视频...", "Loading video...")
        mStatusLbl.Location = New System.Drawing.Point(76, 8)
        mStatusLbl.Size = New System.Drawing.Size(200, 24)
        mStatusLbl.ForeColor = System.Drawing.Color.White
        mStatusLbl.BackColor = System.Drawing.Color.Transparent
        Me.Controls.Add(mStatusLbl)

        ' 播放器控件（视频区在返回按钮下方铺满）。
        mPlayer = New PlayerControl()
        mPlayer.Location = New System.Drawing.Point(0, 36)
        AddHandler mPlayer.MediaFailed, AddressOf OnMediaFailed
        AddHandler mPlayer.MediaEnded, AddressOf OnMediaEnded
        Me.Controls.Add(mPlayer)
    End Sub

    Protected Overrides Sub OnResize(ByVal e As System.EventArgs)
        MyBase.OnResize(e)
        Try
            If mPlayer IsNot Nothing Then
                mPlayer.Width = Me.ClientSize.Width
                mPlayer.Height = Me.ClientSize.Height - 36
                If mPlayer.Height < 10 Then
                    mPlayer.Height = 10
                End If
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 打开并播放（URL 可为 http 流）。
    ' RenderFile 建图可能阻塞（尤其 HTTP 流），放后台线程执行，避免冻结 UI。
    Public Sub OpenAndPlay(ByVal url As String)
        Try
            mStatusLbl.Text = If(mIsChinese, "正在加载视频...", "Loading video...")
            mStatusLbl.Visible = True
            ' UI 线程缓存视频宿主窗口句柄（后台线程不能直接访问 Control.Handle）。
            If mPlayer IsNot Nothing Then
                Try
                    mPlayer.VideoHostHandle = mPlayer.Handle
                Catch exH As Exception
                    mPlayer.VideoHostHandle = IntPtr.Zero
                End Try
            End If
            Dim t As New System.Threading.Thread(AddressOf OpenAndPlayWorker)
            mPendingUrl = url
            t.Start()
        Catch ex As Exception
            ShowError(ex.Message)
        End Try
    End Sub

    Private mPendingUrl As String = ""

    ' 诊断日志（与 PlayerModel/PlayerControl 写同一文件 playerlog.txt）。
    Private Sub Log(ByVal msg As String)
        Try
            Dim dir As String = System.IO.Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().GetName().CodeBase.Replace("file:///", ""))
            If String.IsNullOrEmpty(dir) Then
                dir = "\Program Files\BiliClassic"
            End If
            Try
                System.IO.Directory.CreateDirectory(dir)
            Catch exD As Exception
            End Try
            Dim fs As New System.IO.FileStream(dir & "\playerlog.txt", System.IO.FileMode.Append)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.UTF8)
            w.WriteLine(DateTime.Now.ToString("HH:mm:ss") & "  " & msg)
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub OpenAndPlayWorker()
        Log("OpenAndPlayWorker start")
        Try
            If mPlayer Is Nothing Then
                Log("OpenAndPlayWorker: mPlayer is Nothing")
                Return
            End If

            ' WM 的 DirectShow 不支持 HTTP 流（RenderFile/FileSource 均失败），
            ' 先把视频下载到本地临时文件，再播放本地路径。
            Dim playPath As String = mPendingUrl
            If mPendingUrl.StartsWith("http://") OrElse mPendingUrl.StartsWith("https://") Then
                Log("OpenAndPlayWorker: downloading to local")
                playPath = DownloadToTemp(mPendingUrl)
                Log("OpenAndPlayWorker: download done, local=" & playPath)
                If playPath = "" Then
                    Try
                        Me.Invoke(New SimpleCallback(AddressOf OnOpenFailed))
                    Catch exI As Exception
                    End Try
                    Return
                End If
            End If

            mPlayer.OpenFile(playPath)
            Log("OpenAndPlayWorker: OpenFile done")
            mPlayer.Play()
            Log("OpenAndPlayWorker: Play done")
            mStarted = True
            Try
                Me.Invoke(New SimpleCallback(AddressOf OnOpenSuccess))
            Catch ex As Exception
            End Try
        Catch ex As Exception
            Log("OpenAndPlayWorker EXCEPTION: " & ex.GetType().Name & " | " & ex.Message & " | " & ex.StackTrace)
            Try
                Me.Invoke(New SimpleCallback(AddressOf OnOpenFailed))
            Catch ex2 As Exception
            End Try
        End Try
    End Sub

    ' 下载 URL 到临时文件，返回本地路径（失败返回空串）。
    Private Function DownloadToTemp(ByVal url As String) As String
        Dim localPath As String = ""
        Dim tmpCount As Integer = 0
        Try
            Try
                tmpCount = System.Convert.ToInt32(System.IO.Path.GetFileNameWithoutExtension(url).Replace("video", ""))
            Catch exC As Exception
                tmpCount = 0
            End Try
            localPath = "\Temp\biliclassic_av" & tmpCount.ToString() & ".mp4"
            Try
                System.IO.Directory.CreateDirectory("\Temp")
            Catch exD As Exception
                Log("DownloadToTemp: mkdir \Temp FAIL: " & exD.Message)
            End Try

            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Dim req As System.Net.HttpWebRequest = CType(System.Net.WebRequest.Create(url), System.Net.HttpWebRequest)
            req.Method = "GET"
            req.Timeout = 60000
            req.Accept = "*/*"
            req.UserAgent = ua
            req.Referer = "https://www.bilibili.com/"
            Log("DownloadToTemp: requesting " & url)
            Dim resp As System.Net.HttpWebResponse = CType(req.GetResponse(), System.Net.HttpWebResponse)
            Log("DownloadToTemp: status=" & resp.StatusCode.ToString() & " len=" & resp.ContentLength.ToString())
            Dim inStream As System.IO.Stream = resp.GetResponseStream()
            Dim outFs As New System.IO.FileStream(localPath, System.IO.FileMode.Create)
            Dim buf(32767) As Byte
            Dim total As Long = 0
            Dim n As Integer = inStream.Read(buf, 0, buf.Length)
            While n > 0
                outFs.Write(buf, 0, n)
                total += n
                n = inStream.Read(buf, 0, buf.Length)
            End While
            outFs.Close()
            inStream.Close()
            resp.Close()
            Log("DownloadToTemp: total=" & total.ToString())
            If total < 1000 Then
                Return ""
            End If
        Catch ex As Exception
            Log("DownloadToTemp EXCEPTION: " & ex.GetType().Name & " | " & ex.Message)
            Return ""
        End Try
        Return localPath
    End Function

    Private Delegate Sub SimpleCallback()

    Private Sub OnOpenSuccess()
        mStatusLbl.Visible = False
    End Sub

    Private Sub OnOpenFailed()
        mStatusLbl.Text = If(mIsChinese, "播放失败，请重试", "Playback failed")
        mStatusLbl.Visible = True
    End Sub

    Private Sub ShowError(ByVal msg As String)
        Try
            mStatusLbl.Text = If(mIsChinese, "播放失败: " & msg, "Play failed: " & msg)
            mStatusLbl.Visible = True
        Catch ex As Exception
        End Try
    End Sub

    Private Sub OnBackClick(ByVal sender As Object, ByVal e As System.EventArgs)
        StopPlayback()
        Me.Close()
    End Sub

    Public Sub StopPlayback()
        Try
            If mPlayer IsNot Nothing Then
                mPlayer.Stop()
            End If
        Catch ex As Exception
        End Try
        mStarted = False
    End Sub

    Private Sub OnMediaFailed(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            mStatusLbl.Text = If(mIsChinese, "播放失败，请重试", "Playback failed")
            mStatusLbl.Visible = True
        Catch ex As Exception
        End Try
    End Sub

    Private Sub OnMediaEnded(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            RaiseEvent PlaybackEnded(Me, EventArgs.Empty)
        Catch ex As Exception
        End Try
    End Sub

    Protected Overrides Sub Dispose(ByVal disposing As Boolean)
        StopPlayback()
        If mPlayer IsNot Nothing Then
            Try
                mPlayer.Dispose()
            Catch ex As Exception
            End Try
            mPlayer = Nothing
        End If
        MyBase.Dispose(disposing)
    End Sub

End Class
