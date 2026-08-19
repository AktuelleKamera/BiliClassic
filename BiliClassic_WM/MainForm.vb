Public Class MainForm

    Private Delegate Sub SimpleCallback()

    Private Declare Function SHGetSpecialFolderPath Lib "coredll.dll" (ByVal hwndOwner As IntPtr, ByVal lpszPath As System.Text.StringBuilder, ByVal nFolder As Integer, ByVal fCreate As Boolean) As Boolean

    ' 桌面模拟用 user32/gdi32；WM6 设备用 coredll.dll（两套声明，运行时按平台选择）。
    Private Declare Function User32_GetDC Lib "user32.dll" Alias "GetDC" (ByVal hwnd As IntPtr) As IntPtr
    Private Declare Function User32_ReleaseDC Lib "user32.dll" Alias "ReleaseDC" (ByVal hwnd As IntPtr, ByVal hdc As IntPtr) As Integer
    Private Declare Function Gdi32_BitBlt Lib "gdi32.dll" Alias "BitBlt" (ByVal hdcDest As IntPtr, ByVal xDest As Integer, ByVal yDest As Integer, ByVal wDest As Integer, ByVal hDest As Integer, ByVal hdcSrc As IntPtr, ByVal xSrc As Integer, ByVal ySrc As Integer, ByVal dwRop As Integer) As Boolean
    Private Declare Function User32_PrintWindow Lib "user32.dll" Alias "PrintWindow" (ByVal hwnd As IntPtr, ByVal hdcBlt As IntPtr, ByVal nFlags As Integer) As Boolean

    Private Declare Function Core_GetDC Lib "coredll.dll" Alias "GetDC" (ByVal hwnd As IntPtr) As IntPtr
    Private Declare Function Core_ReleaseDC Lib "coredll.dll" Alias "ReleaseDC" (ByVal hwnd As IntPtr, ByVal hdc As IntPtr) As Integer
    Private Declare Function Core_BitBlt Lib "coredll.dll" Alias "BitBlt" (ByVal hdcDest As IntPtr, ByVal xDest As Integer, ByVal yDest As Integer, ByVal wDest As Integer, ByVal hDest As Integer, ByVal hdcSrc As IntPtr, ByVal xSrc As Integer, ByVal ySrc As Integer, ByVal dwRop As Integer) As Boolean
    ' Windows CE 5.0+ 的 coredll.dll 提供 PrintWindow（可绘制子控件、无视遮挡）。
    Private Declare Function Core_PrintWindow Lib "coredll.dll" Alias "PrintWindow" (ByVal hwnd As IntPtr, ByVal hdcBlt As IntPtr, ByVal nFlags As Integer) As Boolean

    ' WM_PRINT 消息：直接让控件树绘制到目标 DC（桌面与 CE 均支持，无需窗口可见/置前）。
    Private Declare Function User32_SendMessage Lib "user32.dll" Alias "SendMessage" (ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As IntPtr, ByVal lParam As IntPtr) As IntPtr
    Private Declare Function Core_SendMessage Lib "coredll.dll" Alias "SendMessage" (ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As IntPtr, ByVal lParam As IntPtr) As IntPtr
    Private Const WM_PRINT As Integer = &H317
    Private Const PRF_CLIENT As Integer = &H4
    Private Const PRF_CHILDREN As Integer = &H10
    Private Const PRF_ERASEBKGND As Integer = &H8

    Private Const SRCCOPY As Integer = &HCC0020

    ' AnimateWindow：CE/桌面系统级窗口动画（当年 WM 开发者做推入过渡的标准方式）。
    ' 详情页改为独立 Form，用 AnimateWindow 从右滑入/滑出，可靠不闪烁。
    Private Declare Function User32_AnimateWindow Lib "user32.dll" Alias "AnimateWindow" (ByVal hwnd As IntPtr, ByVal dwTime As Integer, ByVal dwFlags As Integer) As Boolean
    Private Declare Function Core_AnimateWindow Lib "coredll.dll" Alias "AnimateWindow" (ByVal hwnd As IntPtr, ByVal dwTime As Integer, ByVal dwFlags As Integer) As Boolean
    Private Const AW_SLIDE As Integer = &H40000
    Private Const AW_HOR_POSITIVE As Integer = &H1
    Private Const AW_HOR_NEGATIVE As Integer = &H2
    Private Const AW_HIDE As Integer = &H10000

    Private Function ApiAnimateWindow(ByVal hwnd As IntPtr, ByVal dwTime As Integer, ByVal dwFlags As Integer) As Boolean
        Try
            If isDesktopRuntime Then
                Return User32_AnimateWindow(hwnd, dwTime, dwFlags)
            Else
                Return Core_AnimateWindow(hwnd, dwTime, dwFlags)
            End If
        Catch ex As Exception
            Return False
        End Try
    End Function

    Private Function ApiGetDC(ByVal hwnd As IntPtr) As IntPtr
        If isDesktopRuntime Then
            Return User32_GetDC(hwnd)
        Else
            Return Core_GetDC(hwnd)
        End If
    End Function

    Private Sub ApiReleaseDC(ByVal hwnd As IntPtr, ByVal hdc As IntPtr)
        If isDesktopRuntime Then
            User32_ReleaseDC(hwnd, hdc)
        Else
            Core_ReleaseDC(hwnd, hdc)
        End If
    End Sub

    Private Function ApiBitBlt(ByVal hdcDest As IntPtr, ByVal xDest As Integer, ByVal yDest As Integer, ByVal wDest As Integer, ByVal hDest As Integer, ByVal hdcSrc As IntPtr, ByVal xSrc As Integer, ByVal ySrc As Integer, ByVal dwRop As Integer) As Boolean
        If isDesktopRuntime Then
            Return Gdi32_BitBlt(hdcDest, xDest, yDest, wDest, hDest, hdcSrc, xSrc, ySrc, dwRop)
        Else
            Return Core_BitBlt(hdcDest, xDest, yDest, wDest, hDest, hdcSrc, xSrc, ySrc, dwRop)
        End If
    End Function

    Private Function ApiPrintWindow(ByVal hwnd As IntPtr, ByVal hdcBlt As IntPtr, ByVal nFlags As Integer) As Boolean
        If isDesktopRuntime Then
            Return User32_PrintWindow(hwnd, hdcBlt, nFlags)
        Else
            Try
                Return Core_PrintWindow(hwnd, hdcBlt, nFlags)
            Catch ex As Exception
                Return False
            End Try
        End If
    End Function

    Private Function ApiSendMessage(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As IntPtr, ByVal lParam As IntPtr) As IntPtr
        If isDesktopRuntime Then
            Return User32_SendMessage(hwnd, msg, wParam, lParam)
        Else
            Return Core_SendMessage(hwnd, msg, wParam, lParam)
        End If
    End Function

    Private Const CSIDL_PROGRAMS As Integer = 2
    Private Const CSIDL_STARTMENU As Integer = 11

    Private isChinese As Boolean = False
    Private isDesktopRuntime As Boolean = False

    ' DPI 缩放因子：以桌面 96 DPI 为基准（1.0），高分辨率设备（如 HD2 WVGA）>1。
    Private dpiScale As Single = 1.0F
    ' 标题栏高度：WM 实机字体/触控偏大，单独加大到 54；桌面保持 44。
    Private ReadOnly Property TITLE_BAR_H() As Integer
        Get
            Try
                If System.Environment.OSVersion.Platform = System.PlatformID.Win32NT Then
                    Return 44
                Else
                    Return 54
                End If
            Catch ex As Exception
                Return 44
            End Try
        End Get
    End Property
    ' 基于实际字体的测量行高：设备 DPI 高时字体自动变大，用测量值决定行高/文字区，避免固定像素裁剪。
    Private fontTitleH As Integer = 16
    Private fontSubH As Integer = 14
    Private fontLineH As Integer = 18

    Private transitionTimer As System.Windows.Forms.Timer = Nothing
    Private transitionFrom As System.Windows.Forms.Control = Nothing
    Private transitionTo As System.Windows.Forms.Control = Nothing
    Private transitionFromPic As System.Windows.Forms.PictureBox = Nothing
    Private transitionToPic As System.Windows.Forms.PictureBox = Nothing
    Private transitionStartTick As Integer = 0
    Private transitionDurationMs As Integer = 400
    Private transitionFromStartX As Integer = 0
    Private transitionToStartX As Integer = 0
    Private transitionDir As Integer = 1
    Private transitionActive As Boolean = False
    Private transitionDoneCalled As Boolean = False
    Private transitionOnDone As SimpleCallback = Nothing

    ' 全屏推入过渡（整屏含标题栏一起滑动，单一覆盖控件合成绘制，无撕裂）。
    Private contentTimer As System.Windows.Forms.Timer = Nothing
    Private contentOv As PageTransitionOverlay = Nothing
    Private contentStartTick As Integer = 0
    Private contentDurationMs As Integer = 400
    Private contentDir As Integer = 1
    Private contentActive As Boolean = False
    Private contentDoneCalled As Boolean = False
    Private contentOnDone As SimpleCallback = Nothing

    ' 自绘虚拟列表（嵌入 recPanel，负责绘制与触摸惯性滚动）。
    Private recListControl As RecListControl = Nothing

    ' 主页（PMC 风格：蓝色背景，竖排选择栏）。
    Private homePanel As HomePanelControl = Nothing
    Private homeButtons As System.Collections.ArrayList = Nothing
    Private homePanelCreated As Boolean = False
    Private homeSelected As Integer = -1
    Private homeHover As Integer = -1
    ' 键盘/方向键在主菜单上的选择项（与触摸高亮分离，方向键上下移动、确定键进入）。
    Private homeKeySel As Integer = 0
    Private homeKeyActive As Boolean = False
    ' 触摸按下状态：按下只显示高亮，抬起才导航（WM 无悬停，必须让高亮有机会渲染）。
    Private homePressDown As Boolean = False
    Private homePressIdx As Integer = -1
    Private homePressY As Integer = 0
    ' 主页右下角装饰大图（bilibili_tv.png，透明，绘制在文字与高亮之下）。
    Private homeTv As System.Drawing.Bitmap = Nothing
    Private currentPage As String = "home" ' home / rec / search / history / fav / profile / detail
    ' 顶部栏页面标题（白条上的文字，主页不显示）。
    Private pageTitle As String = ""
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
    Private imageThreads As Integer = 1
    ' 播放器选择：tcpmp / coreplayer / ostwind（内置）。
    Private selectedPlayer As String = "tcpmp"

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
    ' 加载层显示前 Panel1（顶部标题栏）的可见状态，隐藏加载层时恢复。
    Private loadingPanel1Visible As Boolean = True

    Private loginTimer As System.Windows.Forms.Timer = Nothing
    Private loginQrKey As String = ""
    Private loginQrContent As String = ""
    Private loginErrMsg As String = ""
    Private loginPolling As Boolean = False
    Private loginExpireTick As Integer = 0
    Private savedCookies As String = ""
    Private loginUserName As String = ""
    Private searchHint As String = ""

    ' 异步搜索/更新检查的结果缓冲（工作线程填充，UI 线程读取）。
    Private searchResults As System.Collections.ArrayList = Nothing
    Private searchErrText As String = ""
    Private searchKeyword As String = ""
    Private updateBody As String = ""
    Private updateErr As String = ""

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

    ' Homepage recommendations.
    Private recView As Boolean = False ' True while the recommend list is shown
    Private recLoading As Boolean = False
    Private recItems As System.Collections.ArrayList = Nothing
    Private recPage As Integer = 1
    Private recHasMore As Boolean = False

    ' 搜索结果（自绘卡片列表，与推荐共用 recListControl）。
    Private searchView As Boolean = False ' True 时 recListControl 正显示搜索结果
    Private searchPage As Integer = 1
    Private searchHasMore As Boolean = False
    Private searchLoading As Boolean = False
    ' 搜索历史（关键词，最近在前，上限 20）。
    Private searchHistory As System.Collections.ArrayList = Nothing

    ' 历史记录列表（与推荐同用 recListControl 卡片样式）。
    Private histItems As System.Collections.ArrayList = Nothing
    Private histView As Boolean = False ' True 时推荐列表控件正显示历史记录
    Private histPendingResults As System.Collections.ArrayList = Nothing
    Private histPendingErr As String = ""
    Private histPendingLoadMore As Boolean = False
    ' 封面加载管线当前作用的数据列表（推荐或历史）。
    Private coverItems As System.Collections.ArrayList = Nothing
    ' 详情页来源：是否来自历史记录（决定关闭详情后回到历史样式）。
    Private detailFromHist As Boolean = False

    ' 收藏列表（与历史同用 recListControl 卡片样式）。
    Private favFolderItems As System.Collections.ArrayList = Nothing
    Private favVideoItems As System.Collections.ArrayList = Nothing
    Private favListActive As Boolean = False ' True 时 recListControl 正显示收藏列表
    Private favPendingLoadMore As Boolean = False
    Private detailFromFav As Boolean = False
    Private recCoverLoading As Boolean = False
    Private recCoverIdx As Integer = -1
    Private recCoverActive As Integer = 0
    Private recCoverMaxActive As Integer = 1
    Private recLoadingPreload As Boolean = False
    Private recLastBody As String = ""
    Private recLastErr As String = ""
    Private recRowsBuilt As Integer = 0
    Private Const REC_PAGE_MARKER As String = "==="

    ' Video detail view (bilibili x/web-interface/view).
    Private detailView As Boolean = False
    Private detailLoading As Boolean = False
    Private detailFromRec As Boolean = True ' 详情来源：True=推荐列表，False=搜索/历史/收藏列表
    Private detailBvid As String = ""
    Private detailAid As String = ""
    Private detailLastBody As String = ""
    Private detailLastErr As String = ""
    Private detailCoverLoading As Boolean = False
    Private detailCoverIdx As Integer = -1
    Private detailCoverBox As System.Windows.Forms.PictureBox = Nothing
    Private detailCoverBitmap As System.Drawing.Bitmap = Nothing
    Private detailCoverUrl As String = ""
    Private detailCoverGeneration As Integer = 0
    Private detailCoverWorkerGen As Integer = 0
    Private detailVideoControl As VideoDetailControl = Nothing
    Private pendingPlayBvid As String = ""
    Private playFromDetail As Boolean = False

    ' 个人中心（自绘双缓冲控件：头像/文字/退出登录；未登录显示二维码）。
    Private profilePanel As System.Windows.Forms.Panel = Nothing
    Private profileControl As ProfileControl = Nothing
    Private profileAvatar As System.Drawing.Bitmap = Nothing
    Private profileFetchName As String = ""
    Private profileFetchUid As String = ""
    Private profileFetchCoins As String = ""
    Private profileFetchIsVip As Boolean = False
    Private profileFetchLevel As String = ""
    Private profileFetchSign As String = ""
    Private profileFetchErr As String = ""

    Public Sub New()
        InitializeComponent()
    End Sub

    Private Sub Form1_Load(ByVal sender As Object, ByVal e As System.EventArgs) Handles MyBase.Load
        Try
            AddHandler AppDomain.CurrentDomain.UnhandledException, AddressOf OnUnhandledException

            Dim lang As String = System.Globalization.CultureInfo.CurrentUICulture.Name.ToLower()
            isChinese = lang.StartsWith("zh")
            If isChinese Then
                Me.Text = "哔哩经典"
            End If

            Dim isDesktop As Boolean = (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)
            isDesktopRuntime = isDesktop
            Try
                Dim g As System.Drawing.Graphics = Me.CreateGraphics()
                Dim dpi As Single = g.DpiX
                If dpi <= 0 Then
                    dpi = 96
                End If
                dpiScale = dpi / 96.0F
                ' 用测量值决定行高：字体按设备 DPI 自动缩放，测量结果天然适配设备。
                Dim ft As System.Drawing.Font = GetUiFont(9.0!, True)
                fontTitleH = CInt(Math.Ceiling(g.MeasureString("BiliClassic", ft).Height))
                Dim fr As System.Drawing.Font = GetUiFont(9.0!, False)
                fontSubH = CInt(Math.Ceiling(g.MeasureString("BiliClassic", fr).Height))
                fontLineH = fontTitleH + 2
                ft.Dispose()
                fr.Dispose()
                g.Dispose()
            Catch exDpi As Exception
                dpiScale = 1.0F
            End Try
            If isDesktop Then
                Me.FormBorderStyle = System.Windows.Forms.FormBorderStyle.Sizable
                Me.WindowState = System.Windows.Forms.FormWindowState.Normal
                Me.Size = New System.Drawing.Size(800, 480)
                Try
                    Dim wa As System.Drawing.Rectangle = Screen.PrimaryScreen.WorkingArea
                    Me.Location = New System.Drawing.Point((wa.Width - Me.Width) \ 2, (wa.Height - Me.Height) \ 2)
                Catch exLoc As Exception
                    Me.Location = New System.Drawing.Point(0, 0)
                End Try
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
                btnPlayStream.Text = "流式播放"
                btnPlayOffline.Text = "离线播放"
                mnuSettings.Text = "设置"
                mnuPlayMode.Text = "播放方式"
                mnuExit.Text = "退出"
                mnuLogin.Text = "扫码登录"
                mnuImageThreads.Text = "图片加载线程"
                mnuImageThreadsSingle.Text = "单线程"
                mnuImageThreadsDual.Text = "双线程"
                mnuPlayer.Text = "播放器"
                mnuPlayerTcpmp.Text = "TCPMP"
                mnuPlayerCorePlayer.Text = "CorePlayer"
                mnuPlayerOstwind.Text = "OstwindPlayer（内置）"
                searchHint = "输入关键词或AV/BV号"
            Else
                btnPlayStream.Text = "Stream Play"
                btnPlayOffline.Text = "Offline Play"
                mnuSettings.Text = "Settings"
                mnuPlayMode.Text = "Play Mode"
                mnuExit.Text = "Exit"
                mnuLogin.Text = "QR Login"
                mnuImageThreads.Text = "Image Threads"
                mnuImageThreadsSingle.Text = "Single"
                mnuImageThreadsDual.Text = "Dual"
                mnuPlayer.Text = "Player"
                mnuPlayerTcpmp.Text = "TCPMP"
                mnuPlayerCorePlayer.Text = "CorePlayer"
                mnuPlayerOstwind.Text = "OstwindPlayer (built-in)"
                searchHint = "Enter keyword or av/BV"
            End If

            ' 隐藏内置播放器（Ostwind）选项：CF 2.0 的 MenuItem 无 Visible，
            ' 直接从"播放器"子菜单移除。
            Try
                mnuPlayer.MenuItems.Remove(mnuPlayerOstwind)
            Catch exO As Exception
            End Try

            txtSearch.Text = searchHint
            txtSearch.ForeColor = System.Drawing.Color.Gray
            AddHandler txtSearch.GotFocus, AddressOf TxtSearch_GotFocus
            AddHandler txtSearch.LostFocus, AddressOf TxtSearch_LostFocus

            UpdateShortcut()

            LoadCookies()
            ' 统一网络层：信任所有证书 + 注入登录 Cookie + 日志挂接。
            NetWorkUtil.TrustAllCert()
            NetWorkUtil.SetCookies(savedCookies)
            NetWorkUtil.WriteNetLog = New NetWorkUtil.LogHandler(AddressOf WriteLog)
            LoadPlayMode()
            LoadConvertConfig()
            LoadImageThreads()
            LoadPlayerSelection()
            LoadSearchHistory()

            loadingOverlay = New LoadingOverlay()
            AddHandler loadingOverlay.ReturnPressed, AddressOf LoadingOverlay_ReturnPressed
            Me.Controls.Add(loadingOverlay)
            loadingOverlay.Visible = False

            ApplyUiFonts()
            LayoutControls()
            Me.Refresh()
            transitionTimer = New System.Windows.Forms.Timer()
            AddHandler transitionTimer.Tick, AddressOf TransitionTimerTick
            transitionTimer.Interval = 16
            transitionTimer.Enabled = False

            recListControl = New RecListControl()
            recListControl.Dock = System.Windows.Forms.DockStyle.Fill
            recListControl.OnRowTap = AddressOf RecRowTap
            recListControl.OnMoreTap = AddressOf RecMoreTap
            recListControl.OnScrollChanged = AddressOf RecListScrollChanged
            recPanel.AutoScroll = False
            recPanel.Controls.Add(recListControl)

            detailVideoControl = New VideoDetailControl()
            detailVideoControl.Dock = System.Windows.Forms.DockStyle.Fill
            AddHandler detailVideoControl.PlayClick, AddressOf DetailPlay_Click
            detailPanel.Controls.Add(detailVideoControl)

            profilePanel = New System.Windows.Forms.Panel()
            profilePanel.BackColor = System.Drawing.Color.White
            profileControl = New ProfileControl()
            profileControl.Dock = System.Windows.Forms.DockStyle.Fill
            AddHandler profileControl.LogoutClick, AddressOf ProfileLogout_Click
            profilePanel.Controls.Add(profileControl)
            Me.Controls.Add(profilePanel)
            profilePanel.Visible = False
            ' profilePanel 是运行时创建（第一次 LayoutControls 时尚未存在），补一次布局定位。
            LayoutControls()

            ' 挂钩窗体 wndproc：桌面接收鼠标滚轮（CF 无 OnMouseWheel 事件）；设备拦截硬件返回键（路由到返回上一页）。
            Try
                WndProcHooker.HookWndProc(Me, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
                WriteLog("wheel hook: " & exHook.GetType().FullName & " | " & exHook.Message)
            End Try

            ' 同时挂钩各自绘页面控件：确保方向键/确定键无论焦点在哪个控件上都能被捕获（按键选择）。
            Try
                WndProcHooker.HookWndProc(recListControl, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(detailVideoControl, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(profileControl, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            ' 其余可聚焦控件也挂钩：桌面焦点可能停在任意控件上，全部挂钩才能收到按键。
            Try
                WndProcHooker.HookWndProc(txtSearch, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(txtResult, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(lstSearch, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(Panel1, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(recPanel, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(detailPanel, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try
            Try
                WndProcHooker.HookWndProc(profilePanel, AddressOf MainFormWheelProc, &HFFFFFF)
            Catch exHook As Exception
            End Try

            ' 启动时显示主页（PMC 风格选择栏）。推荐在点击“推荐视频”时加载。
            ShowHome()
        Catch ex As Exception
            MessageBox.Show(ex.ToString(), "Error")
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
        Try
            lstSearch.Visible = False
            recPanel.Visible = False
            detailPanel.Visible = False
            If profilePanel IsNot Nothing Then
                profilePanel.Visible = False
            End If
            txtResult.Visible = True
            recView = False
            histView = False
            favView = ""
            favListActive = False
            detailView = False
            searchView = False
        Catch ex As Exception
            WriteLog("ShowResultView: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    ' ===== 主页（PMC 风格：蓝色背景 + 竖排选择栏）=====

    ' 选择 UI 字体：桌面优先微软雅黑（无则回退 Tahoma），WM 用 Tahoma。
    Private Function GetUiFont(ByVal size As Single, ByVal bold As Boolean) As System.Drawing.Font
        Try
            Dim style As System.Drawing.FontStyle = If(bold, System.Drawing.FontStyle.Bold, System.Drawing.FontStyle.Regular)
            If isDesktopRuntime Then
                ' CF 桌面运行时优先微软雅黑（Light 名 CF 不识别，用 Regular 逼近 Light 观感）。
                Try
                    Dim fYH As System.Drawing.Font = New System.Drawing.Font("Microsoft YaHei", size, System.Drawing.FontStyle.Regular)
                    WriteLog("GetUiFont: using " & fYH.Name & " (desktop)")
                    Return fYH
                Catch exF As Exception
                End Try
                Return New System.Drawing.Font("Tahoma", size, style)
            Else
                Return New System.Drawing.Font("Tahoma", size, style)
            End If
        Catch ex As Exception
            Return New System.Drawing.Font("Tahoma", size, If(bold, System.Drawing.FontStyle.Bold, System.Drawing.FontStyle.Regular))
        End Try
    End Function

    ' 创建主页（仅一次，后续复用）。
    Private Sub EnsureHomePanel()
        If homePanelCreated Then
            Return
        End If
        homePanelCreated = True
        ' 加载主页右下角装饰大图（bilibili_tv.png，透明 PNG）。
        Try
            Dim sTv As System.IO.Stream = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("BiliClassic_WM.bilibili_tv.png")
            If sTv IsNot Nothing Then
                homeTv = New System.Drawing.Bitmap(sTv)
            End If
        Catch ex As Exception
            homeTv = Nothing
        End Try
        homePanel = New HomePanelControl()
        homePanel.Dock = System.Windows.Forms.DockStyle.Fill
        homePanel.OnDraw = New HomePanelControl.PaintCallback(AddressOf HomePanelDraw)
        AddHandler homePanel.MouseDown, AddressOf HomePanel_MouseDown
        AddHandler homePanel.MouseMove, AddressOf HomePanel_MouseMove
        AddHandler homePanel.MouseUp, AddressOf HomePanel_MouseUp
        AddHandler homePanel.Resize, AddressOf HomePanel_Resize
        Me.Controls.Add(homePanel)
        homePanel.BringToFront()
        ' 挂钩主页控件：硬件方向键/确定键可操作主菜单（按键选择）。
        Try
            WndProcHooker.HookWndProc(homePanel, AddressOf MainFormWheelProc, &HFFFFFF)
        Catch exHook As Exception
        End Try

        ' 顶部返回按钮（动态创建，放标题栏左侧；主页时隐藏）。用 ic_back.png（AlphaImage 平铺到白底，CF 上渲染可靠）。
        btnBack = New System.Windows.Forms.PictureBox()
        btnBack.BackColor = System.Drawing.Color.White
        btnBack.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage
        btnBack.Left = 4
        btnBack.Top = (TITLE_BAR_H - 32) \ 2
        btnBack.Width = 32
        btnBack.Height = 32
        Try
            Dim iconBmp As System.Drawing.Bitmap = CreateBackButtonImage()
            If iconBmp IsNot Nothing Then
                btnBack.Image = iconBmp
            End If
        Catch ex As Exception
        End Try
        AddHandler btnBack.Click, AddressOf OnBackClick
        Panel1.Controls.Add(btnBack)
        ' 顶部按钮同样挂钩：焦点落到这些控件时方向键/确定键仍可用。
        Try
            WndProcHooker.HookWndProc(btnBack, AddressOf MainFormWheelProc, &HFFFFFF)
        Catch exHook As Exception
        End Try

        ' 顶部搜索按钮（标题栏右侧）。用 ic_search.png（AlphaImage 平铺到白底，CF 上渲染可靠）。
        btnSearch.BackColor = System.Drawing.Color.White
        btnSearch.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage
        Try
            Dim searchIcon As System.Drawing.Bitmap = CreateSearchButtonImage()
            If searchIcon IsNot Nothing Then
                btnSearch.Image = searchIcon
            End If
        Catch ex As Exception
        End Try
        Try
            WndProcHooker.HookWndProc(btnSearch, AddressOf MainFormWheelProc, &HFFFFFF)
        Catch exHook As Exception
        End Try

        homeButtons = New System.Collections.ArrayList()
        homeButtons.Add(If(isChinese, "个人中心", "My Profile"))
        homeButtons.Add(If(isChinese, "搜索视频", "Search Videos"))
        homeButtons.Add(If(isChinese, "推荐视频", "Recommendations"))
        homeButtons.Add(If(isChinese, "历史记录", "History"))
        homeButtons.Add(If(isChinese, "收藏视频", "Favorites"))
    End Sub

    ' 生成返回按钮图标：ic_back.png 平铺到白底生成不透明位图（CF 透明 PNG 直接渲染不可靠），失败回退画箭头。
    Private Function CreateBackButtonImage() As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            bmp = New System.Drawing.Bitmap(32, 32)
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            g.Clear(System.Drawing.Color.White)
            Try
                Dim sBack As System.IO.Stream = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("BiliClassic_WM.ic_back.png")
                If sBack IsNot Nothing Then
                    Dim msBack As New System.IO.MemoryStream()
                    Dim buf(4095) As Byte
                    Dim n As Integer = sBack.Read(buf, 0, buf.Length)
                    While n > 0
                        msBack.Write(buf, 0, n)
                        n = sBack.Read(buf, 0, buf.Length)
                    End While
                    sBack.Close()
                    msBack.Position = 0
                    Dim aBack As AlphaMobileControls.AlphaImage = AlphaMobileControls.AlphaImage.CreateFromStream(msBack)
                    If aBack.DrawStretched(g, New System.Drawing.Rectangle(0, 0, 32, 32)) Then
                        aBack.Dispose()
                        g.Dispose()
                        Return bmp
                    End If
                    aBack.Dispose()
                End If
            Catch exA As Exception
            End Try
            ' 回退：GDI+ 画深灰左箭头（‹）。
            Dim pen As New System.Drawing.Pen(System.Drawing.Color.FromArgb(&H34, &H34, &H34), 3)
            Dim pts As System.Drawing.Point() = New System.Drawing.Point() {New System.Drawing.Point(12, 7), New System.Drawing.Point(5, 16), New System.Drawing.Point(12, 25)}
            g.DrawLines(pen, pts)
            pen.Dispose()
            g.Dispose()
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function

    ' 生成搜索按钮图标：ic_search.png 平铺到白底生成不透明位图（CF 透明 PNG 直接渲染不可靠），失败回退画放大镜。
    Private Function CreateSearchButtonImage() As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            bmp = New System.Drawing.Bitmap(32, 32)
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            g.Clear(System.Drawing.Color.White)
            Try
                Dim sSearch As System.IO.Stream = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("BiliClassic_WM.ic_search.png")
                If sSearch IsNot Nothing Then
                    Dim msSearch As New System.IO.MemoryStream()
                    Dim buf(4095) As Byte
                    Dim n As Integer = sSearch.Read(buf, 0, buf.Length)
                    While n > 0
                        msSearch.Write(buf, 0, n)
                        n = sSearch.Read(buf, 0, buf.Length)
                    End While
                    sSearch.Close()
                    msSearch.Position = 0
                    Dim aSearch As AlphaMobileControls.AlphaImage = AlphaMobileControls.AlphaImage.CreateFromStream(msSearch)
                    If aSearch.DrawStretched(g, New System.Drawing.Rectangle(0, 0, 32, 32)) Then
                        aSearch.Dispose()
                        g.Dispose()
                        Return bmp
                    End If
                    aSearch.Dispose()
                End If
            Catch exA As Exception
            End Try
            ' 回退：GDI+ 画深灰放大镜（圆 + 手柄）。
            Dim pen As New System.Drawing.Pen(System.Drawing.Color.FromArgb(&H34, &H34, &H34), 3)
            g.DrawEllipse(pen, 6, 6, 14, 14)
            g.DrawLine(pen, 18, 18, 26, 26)
            pen.Dispose()
            g.Dispose()
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function
    Private Sub OnBackClick(ByVal sender As Object, ByVal e As System.EventArgs)
        If detailView Then
            CloseVideoDetail()
            Return
        End If
        If favView = "videos" Then
            BackFromFolderVideos()
            Return
        End If
        SetPageContext("home")
        GoToHome()
    End Sub

    Private Sub HomePanel_Resize(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            If homePanel IsNot Nothing Then
                homePanel.Invalidate()
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 主页鼠标按下：仅记录按下项并显示高亮；导航移到 MouseUp 触发（WM 触摸无悬停，必须让高亮有机会渲染）。
    Private Sub HomePanel_MouseDown(ByVal sender As Object, ByVal e As System.Windows.Forms.MouseEventArgs)
        Try
            ' 触摸/鼠标操作：键盘高亮立即消失，等下一次按按键才重新出现。
            homeKeyActive = False
            Dim idx As Integer = HomeHitTest(e.Y)
            homePressDown = True
            homePressIdx = idx
            homePressY = e.Y
            homeSelected = idx
            homePanel.Invalidate()
            ' 立即同步重绘，确保慢速设备上按下高亮也能看到。
            homePanel.Update()
        Catch ex As Exception
        End Try
    End Sub

    ' 主页鼠标移动：桌面悬停高亮；手指拖离按点超过阈值则取消按下。
    Private Sub HomePanel_MouseMove(ByVal sender As Object, ByVal e As System.Windows.Forms.MouseEventArgs)
        Try
            ' 鼠标悬停也属鼠标操作：键盘高亮消失（桌面悬停会持续触发）。
            If homeKeyActive Then
                homeKeyActive = False
                homePanel.Invalidate()
            End If
            Dim idx As Integer = HomeHitTest(e.Y)
            If idx <> homeHover Then
                homeHover = idx
                homePanel.Invalidate()
            End If
            If homePressDown AndAlso Math.Abs(e.Y - homePressY) > 8 Then
                homePressDown = False
                homeSelected = -1
                homePanel.Invalidate()
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 主页鼠标抬起：仍落在原按点上则触发导航。
    Private Sub HomePanel_MouseUp(ByVal sender As Object, ByVal e As System.Windows.Forms.MouseEventArgs)
        Try
            If Not homePressDown Then
                Return
            End If
            homePressDown = False
            Dim idx As Integer = HomeHitTest(e.Y)
            Dim wasIdx As Integer = homePressIdx
            homeSelected = -1
            homePanel.Invalidate()
            ' 同步重绘清除按下高亮，避免随后触发导航时截图把高亮烧进过渡快照。
            homePanel.Update()
            If idx = wasIdx AndAlso idx >= 0 Then
                HomeNavigate(idx)
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 主页导航：按索引分发到各页面。
    Private Sub HomeNavigate(ByVal idx As Integer)
        Select Case idx
            Case 0
                OnHomeProfile(Nothing, Nothing)
            Case 1
                OnHomeSearch(Nothing, Nothing)
            Case 2
                OnHomeRecommend(Nothing, Nothing)
            Case 3
                OnHomeHistory(Nothing, Nothing)
            Case 4
                OnHomeFavs(Nothing, Nothing)
        End Select
    End Sub

    Private Sub HomePanel_MouseLeave(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            If homeHover <> -1 Then
                homeHover = -1
                homePanel.Invalidate()
            End If
            homePressDown = False
            homeSelected = -1
        Catch ex As Exception
        End Try
    End Sub

    ' 命中测试：返回 y 对应的选项索引（-1=无）。
    Private Function HomeHitTest(ByVal y As Integer) As Integer
        Try
            If homePanel Is Nothing Then
                Return -1
            End If
            Dim w As Integer = homePanel.ClientSize.Width
            Dim h As Integer = homePanel.ClientSize.Height
            Dim leftW As Integer = 60
            If w < 10 OrElse h < 10 Then
                Return -1
            End If
            ' 选项区：标题栏左侧往右。
            Dim optLeft As Integer = leftW + 10
            Dim optW As Integer = w - optLeft - 10
            If optW < 40 Then
                Return -1
            End If
            Dim itemH As Integer = 50
            Dim gap As Integer = 12
            ' WM 字体行高较大，选项需要更大间距，避免相邻项重叠。
            If Not isDesktopRuntime Then
                itemH = 62
                gap = 18
            End If
            Dim totalH As Integer = homeButtons.Count * itemH + (homeButtons.Count - 1) * gap
            Dim startY As Integer = CInt((h - totalH) / 2)
            ' 与 HomePanelDraw 同步：选项起点至少低于标题底部一段间距。
            Dim th As Integer = 30
            Try
                Dim fT As System.Drawing.Font = GetUiFont(24.0!, True)
                th = CInt(homePanel.CreateGraphics().MeasureString("BiliClassic", fT).Height)
                fT.Dispose()
            Catch exT As Exception
            End Try
            Dim titleBottom As Integer = 16 + th
            Dim minStart As Integer = titleBottom + 30
            If startY < minStart Then
                startY = minStart
            End If
            If startY < 10 Then
                startY = 10
            End If
            Dim i As Integer
            For i = 0 To homeButtons.Count - 1
                Dim top As Integer = startY + i * (itemH + gap)
                If y >= top AndAlso y < top + itemH Then
                    Return i
                End If
            Next
        Catch ex As Exception
        End Try
        Return -1
    End Function

    Private Sub HomePanelDraw(ByVal g As System.Drawing.Graphics, ByVal w As Integer, ByVal h As Integer)
        Try
            If w < 10 OrElse h < 10 Then
                Return
            End If

            ' 背景：纯白。
            g.Clear(System.Drawing.Color.White)

            ' 右下角装饰大图（bilibili_tv.png，透明）：画在文字与高亮之下。
            ' 大尺寸贴右下角，右下角约 20% 伸出屏幕边缘（被裁剪），观感更满。
            If homeTv IsNot Nothing Then
                Try
                    Dim tvH As Integer = CInt(h * 1.0)
                    If tvH > homeTv.Height Then
                        tvH = homeTv.Height
                    End If
                    Dim tvW As Integer = CInt(tvH * homeTv.Width / homeTv.Height)
                    If tvW > CInt(w * 1.0) Then
                        tvW = CInt(w * 1.0)
                        tvH = CInt(tvW * homeTv.Height / homeTv.Width)
                    End If
                    Dim tvX As Integer = w - CInt(tvW * 0.8)
                    Dim tvY As Integer = h - CInt(tvH * 0.8)
                    Dim tvSrc As New System.Drawing.Rectangle(0, 0, homeTv.Width, homeTv.Height)
                    Dim tvDst As New System.Drawing.Rectangle(tvX, tvY, tvW, tvH)
                    g.DrawImage(homeTv, tvDst, tvSrc, System.Drawing.GraphicsUnit.Pixel)
                Catch exTv As Exception
                End Try
            End If

            ' 顶部横排大字标题（正的，粉色 D86DA5）。
            Dim titleBrush As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HD8, &H6D, &HA5))
            Dim title As String = If(isChinese, "哔哩经典", "BiliClassic")
            Dim titleFont As System.Drawing.Font = GetUiFont(24.0!, True)
            Dim tw As Integer = CInt(g.MeasureString(title, titleFont).Width)
            Dim th As Integer = CInt(g.MeasureString(title, titleFont).Height)
            Dim titleX As Integer = 20
            If tw > w - 40 Then
                titleFont.Dispose()
                titleFont = GetUiFont(18.0!, True)
                tw = CInt(g.MeasureString(title, titleFont).Width)
                th = CInt(g.MeasureString(title, titleFont).Height)
            End If
            g.DrawString(title, titleFont, titleBrush, titleX, 16)
            titleFont.Dispose()
            titleBrush.Dispose()

            ' 右侧选项（默认粉色文字，选中/悬停用粉色高亮条 + 白色文字）。
            Dim leftW As Integer = 60
            Dim optLeft As Integer = leftW + 10
            Dim optW As Integer = w - optLeft - 10
            If optW < 40 Then
                Return
            End If
            Dim itemH As Integer = 50
            Dim gap As Integer = 12
            ' WM 字体行高较大，选项需要更大间距，避免相邻项重叠。
            If Not isDesktopRuntime Then
                itemH = 62
                gap = 18
            End If
            Dim totalH As Integer = homeButtons.Count * itemH + (homeButtons.Count - 1) * gap
            Dim startY As Integer = CInt((h - totalH) / 2)
            ' 标题在顶部，选项起点至少低于标题底部一段间距，拉开标题与下方文字的距离。
            Dim titleBottom As Integer = 16 + th
            Dim minStart As Integer = titleBottom + 30
            If startY < minStart Then
                startY = minStart
            End If
            If startY < 12 Then
                startY = 12
            End If

            Dim itemFont As System.Drawing.Font = GetUiFont(15.0!, True)
            Dim selBrush As New System.Drawing.SolidBrush(System.Drawing.Color.White)
            Dim normBrush As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HD8, &H6D, &HA5))
            Dim i As Integer
            For i = 0 To homeButtons.Count - 1
                Dim top As Integer = startY + i * (itemH + gap)
                Dim text As String = CStr(homeButtons(i))
                Dim th2 As Integer = CInt(g.MeasureString(text, itemFont).Height)
                If (i = homeSelected OrElse i = homeHover OrElse (homeKeyActive AndAlso i = homeKeySel)) Then
                    ' 选中/悬停：画粉色高亮条（白底上 select.png 白色条不可见，用粉色渐变），再覆盖白色文字。
                    DrawHomeSelect(g, optLeft, top, optW, itemH)
                    g.DrawString(text, itemFont, selBrush, optLeft + 12, top + (itemH - th2) \ 2)
                Else
                    g.DrawString(text, itemFont, normBrush, optLeft + 12, top + (itemH - th2) \ 2)
                End If
            Next
            itemFont.Dispose()
            selBrush.Dispose()
            normBrush.Dispose()
        Catch ex As Exception
            WriteLog("HomeDraw EXCEPTION: " & ex.GetType().Name & " | " & ex.Message)
        End Try
    End Sub

    ' 选择高亮：背景已改纯白，select.png（白色渐变条）在白底上不可见，直接画粉色渐变。
    Private Sub DrawHomeSelect(ByVal g As System.Drawing.Graphics, ByVal x As Integer, ByVal y As Integer, ByVal w As Integer, ByVal h As Integer)
        DrawHomeGradient(g, x, y, w, h)
    End Sub

    ' 水平渐变条：从左到右渐渐变亮（CF 无 LinearGradientBrush，逐列绘制）。
    ' 白底主页：用粉色系（浅粉 D8D8A5 系错，实为从深粉 D86DA5 到浅粉 F6C7DC）。
    Private Sub DrawHomeGradient(ByVal g As System.Drawing.Graphics, ByVal x As Integer, ByVal y As Integer, ByVal w As Integer, ByVal h As Integer)
        Try
            If w <= 0 OrElse h <= 0 Then
                Return
            End If
            Dim stepX As Integer = 2
            If stepX > w Then
                stepX = w
            End If
            Dim cols As Integer = w \ stepX
            If cols < 1 Then
                cols = 1
            End If
            Dim j As Integer
            For j = 0 To cols - 1
                Dim ratio As Single = 1.0F - (j / CSng(cols))
                ' 从左（浅粉 #F2C6DE）到右（深粉 #D86DA5）的渐变，白字在粉色条上必清晰。
                Dim rr As Integer = CInt(216 + ratio * (242 - 216))
                Dim gg As Integer = CInt(109 + ratio * (198 - 109))
                Dim bb As Integer = CInt(165 + ratio * (222 - 165))
                Dim cb As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HFF000000 Or (rr << 16) Or (gg << 8) Or bb))
                Dim cx As Integer = x + j * stepX
                Dim cw As Integer = stepX
                If j = cols - 1 Then
                    cw = w - j * stepX
                End If
                If cw > 0 Then
                    g.FillRectangle(cb, cx, y, cw, h)
                End If
                cb.Dispose()
            Next
        Catch ex As Exception
        End Try
    End Sub

    ' 显示主页。
    Private Sub ShowHome()
        Try
            If Not homePanelCreated Then
                EnsureHomePanel()
            End If
            SetPageContext("home")
            recView = False
            histView = False
            favView = ""
            favListActive = False
            detailView = False
            searchView = False
            ' 返回主页：清空搜索状态（输入框恢复占位提示、结果清空），下次进搜索页是全新状态。
            Try
                If searchResults IsNot Nothing Then
                    searchResults.Clear()
                End If
                If txtSearch IsNot Nothing Then
                    txtSearch.Text = searchHint
                    txtSearch.ForeColor = System.Drawing.Color.Gray
                End If
            Catch exS As Exception
            End Try
            ' 返回主页：清除选中高亮（只保留悬停高亮）。
            homeSelected = -1
            homeHover = -1
            txtResult.Visible = False
            lstSearch.Visible = False
            recPanel.Visible = False
            detailPanel.Visible = False
            If profilePanel IsNot Nothing Then
                profilePanel.Visible = False
            End If
            ' 确保 homePanel 尺寸铺满客户区（Dock=Fill 在首次布局可能滞后）。
            Try
                homePanel.Bounds = Me.ClientRectangle
            Catch exB As Exception
            End Try
            homePanel.Visible = True
            homePanel.BringToFront()
            ' 确保主窗体持有键盘焦点：桌面方向键才能到达 form 的 wndproc hook。
            Try
                Me.Focus()
            Catch exF As Exception
            End Try
            ' 主页顶部：隐藏整个标题栏（无粉条/标题），只显示自绘主页。
            Try
                Panel1.Visible = False
                btnBack.Visible = False
                txtSearch.Visible = False
                btnSearch.Visible = False
            Catch exT As Exception
            End Try
            homePanel.Invalidate()
            homePanel.Update()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub OnHomeProfile(ByVal sender As Object, ByVal e As System.EventArgs)
        SetPageContext("profile")
        GoFromHome(-1, If(isChinese, "个人中心", "Profile"), "", New SimpleCallback(AddressOf ShowProfile))
    End Sub

    Private Sub OnHomeSearch(ByVal sender As Object, ByVal e As System.EventArgs)
        SetPageContext("search")
        GoFromHome(-1, "", "", New SimpleCallback(AddressOf ShowSearchPage))
    End Sub

    Private Sub OnHomeRecommend(ByVal sender As Object, ByVal e As System.EventArgs)
        SetPageContext("rec")
        If Not recLoading Then
            StartRecEntryTransition()
        Else
            ' 已有加载/已加载：过渡回推荐页。
            Try
                recView = True
                histView = False
                favView = ""
                favListActive = False
                recListControl.HistoryMode = False
                coverItems = recItems
                Dim fromBmp As System.Drawing.Bitmap = Nothing
                If homePanel IsNot Nothing Then
                    Try
                        homePanel.Bounds = Me.ClientRectangle
                    Catch ex As Exception
                    End Try
                    fromBmp = homePanel.RenderToBitmap()
                End If
                Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(If(isChinese, "推荐视频", "Recommendations"), recListControl.RenderToBitmap())
                StartFullTransition(fromBmp, toBmp, -1, New SimpleCallback(AddressOf FinishRecResume))
            Catch ex As Exception
                recPanel.BringToFront()
                recPanel.Visible = True
            End Try
        End If
    End Sub

    ' 恢复推荐页过渡结束。
    Private Sub FinishRecResume()
        Try
            SetPageContext("rec")
            histView = False
            favView = ""
            favListActive = False
            recListControl.HistoryMode = False
            coverItems = recItems
            ShowSubPageTop()
            recPanel.BringToFront()
            recPanel.Visible = True
            ResumeCoverLoad()
        Catch ex As Exception
        End Try
    End Sub

    ' 主页 → 推荐：先就绪推荐面板（loading 态），再播放整屏推入过渡，结束后启动抓取线程。
    Private Sub StartRecEntryTransition()
        Try
            recLoading = True
            recView = True
            histView = False
            favView = ""
            favListActive = False
            recListControl.HistoryMode = False
            coverItems = recItems
            recPanel.Controls.Clear()
            recPanel.AutoScroll = False
            recPanel.Controls.Add(recListControl)
            recListControl.Dock = System.Windows.Forms.DockStyle.Fill
            recListControl.BringToFront()
            recListControl.ShowStateText(If(isChinese, "正在加载推荐...", "Loading recommendations..."))
            recLoadingPreload = True
            txtResult.Visible = False
            lstSearch.Visible = False
            detailPanel.Visible = False
            detailView = False

            ' direction=-1：to 从右侧滑入、from 向左滑出（前进观感，整屏含标题栏一起滑）。
            Dim fromBmp As System.Drawing.Bitmap = Nothing
            If homePanel IsNot Nothing Then
                Try
                    homePanel.Bounds = Me.ClientRectangle
                Catch ex As Exception
                End Try
                fromBmp = homePanel.RenderToBitmap()
            End If
            Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(If(isChinese, "推荐视频", "Recommendations"), recListControl.RenderToBitmap())
            StartFullTransition(fromBmp, toBmp, -1, New SimpleCallback(AddressOf FinishRecEntry))
        Catch ex As Exception
            ' 过渡失败则直接切换。
            FinishRecEntry()
        End Try
    End Sub

    ' 过渡结束：显示标题栏、隐藏主页、显示推荐面板并启动抓取。
    Private Sub FinishRecEntry()
        Try
            SetPageContext("rec")
            histView = False
            favView = ""
            favListActive = False
            recListControl.HistoryMode = False
            coverItems = recItems
            ShowSubPageTop()
            recPanel.BringToFront()
            recPanel.Visible = True
            Dim t As New System.Threading.Thread(AddressOf FetchRecommendWorker)
            t.Start()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub OnHomeHistory(ByVal sender As Object, ByVal e As System.EventArgs)
        SetPageContext("history")
        GoFromHome(-1, If(isChinese, "播放历史", "History"), "", New SimpleCallback(AddressOf LoadHistory))
    End Sub

    Private Sub OnHomeFavs(ByVal sender As Object, ByVal e As System.EventArgs)
        SetPageContext("fav")
        GoFromHome(-1, If(isChinese, "我的收藏", "Favorites"), "", New SimpleCallback(AddressOf LoadFavoriteFolders))
    End Sub

    ' ===== 搜索独立页 =====

    Private Sub ShowSearchPage()
        ' 搜索页：显示搜索输入框 + 自绘结果卡片列表（不用 ListView，规避桌面 .NET 2.0 溢出崩溃）。
        Try
            txtResult.Visible = False
            lstSearch.Visible = False
            detailPanel.Visible = False
            homePanel.Visible = False
            searchView = True
            recListControl.HistoryMode = False
            recPanel.BringToFront()
            recPanel.Visible = True
            If searchResults Is Nothing OrElse searchResults.Count = 0 Then
                If searchHistory IsNot Nothing AndAlso searchHistory.Count > 0 Then
                    ' 有搜索历史：显示历史列表，回车/点击可再次搜索。
                    BuildSearchHistoryRows()
                    Try
                        recListControl.Focus()
                    Catch exF As Exception
                    End Try
                Else
                    ' 空页面提示：搜索框占位提示已说明如何搜索，这里不再重复同一句话。
                    recListControl.ShowStateTextPlain(If(isChinese, "来搜索些东西吧喵~", "Search for something, meow~"))
                    ' 无历史可操作：焦点留在搜索框，方便直接输入。
                    txtSearch.Focus()
                End If
            Else
                BuildSearchRows()
                ResumeCoverLoad()
                ' 有结果：焦点移到结果列表，方向键选择、回车打开。
                Try
                    recListControl.Focus()
                Catch exF As Exception
                End Try
            End If
            ' 顶部：返回 + 搜索框。
            Try
                Panel1.Visible = True
                btnBack.Visible = True
                txtSearch.Visible = True
                btnSearch.Visible = True
            Catch exT As Exception
            End Try
        Catch ex As Exception
        End Try
    End Sub

    ' 构建搜索结果行（自绘卡片列表）。
    Private Sub BuildSearchRows()
        Try
            If recListControl Is Nothing Then
                Return
            End If
            If searchResults Is Nothing OrElse searchResults.Count = 0 Then
                recListControl.ShowStateTextPlain(If(isChinese, "未找到相关视频", "No videos found"))
                Return
            End If
            histView = False
            favView = ""
            favListActive = False
            recListControl.HistoryMode = False
            coverItems = searchResults
            Dim w As Integer = recPanel.ClientSize.Width
            If w < 10 Then
                w = 460
            End If
            Dim rowH As Integer = 94 + 12
            Dim minTitleH As Integer = fontLineH * 2 + 8
            If rowH < minTitleH Then
                rowH = minTitleH
            End If
            recListControl.SetData(searchResults, rowH, fontLineH, fontSubH, isChinese)
            ' 有更多页时显示"加载更多"行。
            recListControl.SetShowMore(searchHasMore)
        Catch ex As Exception
        End Try
    End Sub

    ' 返回主页。
    Private Sub GoHome()
        Try
            ' 若在详情页，先关闭详情。
            If detailView Then
                CloseVideoDetail()
            End If
            ShowHome()
        Catch ex As Exception
            ShowHome()
        End Try
    End Sub

    ' 子页面顶部：显示标题栏+返回按钮，隐藏搜索框（搜索页除外），并隐藏主页。
    Private Sub ShowSubPageTop()
        Try
            If homePanelCreated Then
                Panel1.Visible = True
                btnBack.Visible = True
                txtSearch.Visible = False
                btnSearch.Visible = False
                homePanel.Visible = False
                Panel1.Refresh()
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 设置页面上下文：currentPage + 顶部栏标题，标题变化时重绘白条。
    Private Sub SetPageContext(ByVal page As String)
        currentPage = page
        Dim t As String = ""
        Select Case page
            Case "rec"
                t = If(isChinese, "推荐视频", "Recommendations")
            Case "history"
                t = If(isChinese, "播放历史", "History")
            Case "fav"
                t = If(isChinese, "我的收藏", "Favorites")
            Case "favvideos"
                t = favFolderName
                If t = "" Then
                    t = If(isChinese, "我的收藏", "Favorites")
                End If
            Case "profile"
                t = If(isChinese, "个人中心", "Profile")
            Case "detail"
                t = If(isChinese, "视频详情", "Video Detail")
            Case Else
                t = ""
        End Select
        If t <> pageTitle Then
            pageTitle = t
            Try
                Panel1.Invalidate()
            Catch ex As Exception
            End Try
        End If
    End Sub

    ' 首页推荐：加载一页推荐视频，显示为卡片列表（recPanel 内每行封面+文字）。
    Private Sub LoadRecommendPage(ByVal loadMore As Boolean)
        If recLoading Then
            Return
        End If
        recLoading = True
        recView = True
        ShowSubPageTop()
        SetPageContext("rec")

        recPanel.BringToFront()
        recPanel.Visible = True
        txtResult.Visible = False
        lstSearch.Visible = False
        detailPanel.Visible = False
        detailView = False

        If loadMore Then
            ' 分页：底部“加载更多”行已在位；等待新数据到达后重建列表。
            recPanel.Refresh()
        Else
            recPanel.Controls.Clear()
            recPanel.AutoScroll = False
            recPanel.Controls.Add(recListControl)
            recListControl.Dock = System.Windows.Forms.DockStyle.Fill
            recListControl.BringToFront()
            recListControl.ShowStateText(If(isChinese, "正在加载推荐...", "Loading recommendations..."))
            recLoadingPreload = True
        End If

        Dim t As New System.Threading.Thread(AddressOf FetchRecommendWorker)
        t.Start()
    End Sub

    Private Sub FetchRecommendWorker()
        Dim body As String = ""
        Dim errMsg As String = ""


        Try
            Dim signer As New WbiSigner()
            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            If Not signer.FetchKeys(ua, "https://www.bilibili.com/") Then
                errMsg = If(isChinese, "WBI 密钥获取失败", "WBI key fetch failed")
            Else
                Dim vp As New System.Collections.Generic.Dictionary(Of String, String)()
                vp("web_location") = "1430650"
                vp("feed_version") = "V8"
                vp("homepage_ver") = "1"
                vp("screen") = "800-480"
                vp("fresh_idx") = recPage.ToString()
                vp("fresh_idx_1h") = recPage.ToString()
                vp("brush") = recPage.ToString()
                vp("fetch_row") = ((recPage - 1) * 20).ToString()
                Dim url As String = signer.SignUrl("https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd", vp)

                body = NetWorkUtil.GetText(url, savedCookies)
                WriteLog("recommend: " & Microsoft.VisualBasic.Left(body, 200))
            End If
        Catch ex As Exception
            errMsg = ex.GetType().FullName & " | " & ex.Message
            WriteLog("recommend fetch: " & errMsg & " | " & ex.StackTrace)
        End Try

        recLastBody = body
        recLastErr = errMsg
        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowRecommendOnUi))
        Catch ex As Exception
            WriteLog("recommend invoke: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    Private Sub ShowRecommendOnUi()
        recLoading = False
        If recLastErr <> "" Then
            ShowRecommendMessage(recLastErr)
            Return
        End If

        ' 只解析 item 数组区域（bvid 可能出现在嵌套字段中，需限定范围）。
        Dim body As String = recLastBody
        Dim itemStart As Integer = body.IndexOf("""item"":[")
        If itemStart >= 0 Then
            Dim itemEnd As Integer = body.IndexOf(""", ""business_card""", itemStart)
            If itemEnd < 0 Then
                itemEnd = body.LastIndexOf("]")
            End If
            If itemEnd > itemStart Then
                body = body.Substring(itemStart, itemEnd - itemStart + 1)
            End If
        End If

        Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
        If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
            ShowRecommendMessage(If(isChinese, "获取推荐失败 (code=" & mCode.Groups(1).Value & ")", "Recommend failed (code=" & mCode.Groups(1).Value & ")"))
            Return
        End If

        ' 解析 item 数组：每项 bvid/pic/title/owner.name/stat.view
        If recItems Is Nothing Then
            recItems = New System.Collections.ArrayList()
        End If
        Dim newCount As Integer = 0
        Dim mBvCol As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, """bvid"":\s*""([^""]+)""")
        Dim patPic As String = """pic"":\s*""([^""]+)"""
        Dim patTitle As String = """title"":\s*""((?:[^""\\]|\\.)*)"""
        Dim patAuthor As String = """name"":\s*""([^""]*)"""
        Dim patView As String = """view"":\s*(\d+)"
        Dim patDanmaku As String = """danmaku"":\s*(\d+)"
        For Each mb As System.Text.RegularExpressions.Match In mBvCol
            Dim bv As String = mb.Groups(1).Value
            Dim tail As Integer = System.Math.Min(600, body.Length - mb.Index)
            Dim after As String = body.Substring(mb.Index, tail)

            Dim mPic As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patPic)
            Dim pic As String = ""
            If mPic.Success Then
                pic = mPic.Groups(1).Value
            End If

            Dim mTitle As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patTitle)
            Dim title As String = ""
            If mTitle.Success Then
                title = StripHtml(UnescapeJson(mTitle.Groups(1).Value))
            End If

            ' owner 对象在 bvid 之后：{"owner":{"mid":...,"name":"..."}}。
            ' 在 bvid 之后 600 字符内取最后一个 name 字段。
            Dim mAuth As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(after, patAuthor)
            Dim author As String = ""
            If mAuth.Count > 0 Then
                author = mAuth(mAuth.Count - 1).Groups(1).Value
            End If

            Dim mView As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patView)
            Dim view As String = ""
            If mView.Success Then
                view = mView.Groups(1).Value
            End If

            Dim mDanmaku As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patDanmaku)
            Dim danmaku As String = ""
            If mDanmaku.Success Then
                danmaku = mDanmaku.Groups(1).Value
            End If

            ' 只保留有 bvid 且有封面的正常视频条目（广告/直播等无封面的不显示）。
            If bv <> "" AndAlso pic <> "" Then
                Dim ri As New RecItem(bv, pic, title, author, view)
                ri.Danmaku = danmaku
                recItems.Add(ri)
                newCount += 1
            End If
        Next

        ' 分页标记：只要本页有数据就允许继续翻页（与历史一致）。
        recHasMore = (newCount > 0)

        ' 构建卡片式列表：每行 = 左侧封面缩略图 + 右侧标题/UP主/播放量。
        BuildRecommendRows()
        WriteLog("recommend OK: new=" & newCount.ToString() & " total=" & recItems.Count.ToString() & " hasMore=" & recHasMore.ToString())

        ' 封面加载：首次从第 0 张开始；加载更多时从头扫描（StartCoverLoad 会跳过已有封面/失败的条目）。
        If newCount > 0 Then
            If recLoadingPreload Then
                recLoadingPreload = False
                StartCoverLoad(0)
            ElseIf recCoverLoading Then
                ' 有线程正在下载，交给 PumpCoverWorkers 推进。
                If recView Then
                    PumpCoverWorkers()
                End If
            Else
                StartCoverLoad(0)
            End If
        End If
    End Sub

    ' 推荐视图内显示错误消息（请求失败：小电视静止）。
    Private Sub ShowRecommendMessage(ByVal msg As String)
        If recListControl Is Nothing Then
            Return
        End If
        recListControl.ShowStateTextStatic(msg)
    End Sub

    ' 构建卡片行数据：行高 = 左侧封面(168x94) + 上下边距；标题在右侧占整行。
    ' 自绘虚拟列表只保存数据，绘制与触摸滚动由 recListControl 内部完成。
    Private Sub BuildRecommendRows()
        If recListControl Is Nothing Then
            Return
        End If
        Dim w As Integer = recPanel.ClientSize.Width
        If w < 10 Then
            w = 460
        End If
        Dim rowH As Integer = 94 + 12
        Dim minTitleH As Integer = fontLineH * 2 + 8
        If rowH < minTitleH Then
            rowH = minTitleH
        End If

        If recRowsBuilt = 0 AndAlso recItems.Count = 0 Then
            recListControl.ShowStateTextStatic(If(isChinese, "暂无推荐视频", "No recommendations"))
            Return
        End If

        recRowsBuilt = recItems.Count
        ' 推荐样式：关闭历史模式，封面管线切回推荐列表。
        histView = False
        favView = ""
        favListActive = False
        recListControl.HistoryMode = False
        coverItems = recItems
        recListControl.SetData(recItems, rowH, fontLineH, fontSubH, isChinese)
    End Sub

    ' 按下高亮色（安卓 item_click_effect #40D86DA5 半透明粉叠加到白底 ≈ 245,218,232）。
    Private Shared RecPressedColor As System.Drawing.Color = System.Drawing.Color.FromArgb(&HF5, &HDA, &HE8)

    ' 自绘列表点击某行：打开视频详情（推荐/搜索/历史/收藏共用，按视图状态路由）。
    Private Sub RecRowTap(ByVal idx As Integer)
        Try
            If searchView Then
                If searchResults IsNot Nothing AndAlso searchResults.Count > 0 Then
                    ' 搜索结果。
                    If idx >= 0 AndAlso idx < searchResults.Count Then
                        Dim si As RecItem = CType(searchResults(idx), RecItem)
                        If Not String.IsNullOrEmpty(si.Bvid) Then
                            OpenVideoDetail(si.Bvid)
                        End If
                    End If
                Else
                    ' 搜索历史（searchResults 为空时列表显示的是历史关键词）。
                    If searchHistory IsNot Nothing AndAlso idx >= 0 AndAlso idx < searchHistory.Count Then
                        Dim hkw As String = CStr(searchHistory(idx))
                        Try
                            txtSearch.Text = hkw
                            txtSearch.ForeColor = System.Drawing.Color.Black
                        Catch exT As Exception
                        End Try
                        btnSearch_Click(btnSearch, System.EventArgs.Empty)
                    End If
                End If
                Return
            End If
            If favView <> "" Then
                If favView = "folders" Then
                    If favFolderItems IsNot Nothing AndAlso idx >= 0 AndAlso idx < favFolderItems.Count Then
                        Dim fi As RecItem = CType(favFolderItems(idx), RecItem)
                        If Not String.IsNullOrEmpty(fi.Bvid) Then
                            OpenFavoriteFolder(fi.Bvid, fi.Title)
                        End If
                    End If
                Else
                    If favVideoItems IsNot Nothing AndAlso idx >= 0 AndAlso idx < favVideoItems.Count Then
                        Dim vi As RecItem = CType(favVideoItems(idx), RecItem)
                        If Not String.IsNullOrEmpty(vi.Bvid) Then
                            OpenVideoDetail(vi.Bvid)
                        End If
                    End If
                End If
                Return
            End If
            If histView Then
                If histItems IsNot Nothing AndAlso idx >= 0 AndAlso idx < histItems.Count Then
                    Dim hi As RecItem = CType(histItems(idx), RecItem)
                    If Not String.IsNullOrEmpty(hi.Bvid) Then
                        OpenVideoDetail(hi.Bvid)
                    End If
                End If
                Return
            End If
            WriteLog("rec row tap idx=" & idx.ToString())
            If idx >= 0 AndAlso idx < recItems.Count Then
                Dim ri As RecItem = CType(recItems(idx), RecItem)
                If Not String.IsNullOrEmpty(ri.Bvid) Then
                    OpenVideoDetail(ri.Bvid)
                End If
            End If
        Catch ex As Exception
            WriteLog("rec row tap: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    ' 自绘列表点击“加载更多”（推荐/搜索/历史/收藏共用）。
    Private Sub RecMoreTap(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            If searchView Then
                LoadMoreSearch()
                Return
            End If
            If favView <> "" Then
                If favView = "videos" Then
                    LoadFolderVideosPage(True)
                End If
                Return
            End If
            If histView Then
                LoadMoreHistory()
                Return
            End If
            If Not recLoading Then
                recPage += 1
                LoadRecommendPage(True)
            End If
        Catch ex As Exception
            WriteLog("rec more tap: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    ' ===== 视频详情页（类似安卓版 VideoDetailFragment，暂只展示视频信息）=====

    ' 截取指定控件的画面到 Bitmap。
    ' 桌面：PrintWindow（user32，支持递归子控件）→ 回退屏幕 DC。
    ' WM：CF 2.0 不支持 WM_PRINT 子控件绘制，直接回退屏幕 DC（面板需可见，见调用处）。
    Private Function CaptureControlImage(ByVal target As System.Windows.Forms.Control) As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            bmp = New System.Drawing.Bitmap(target.Width, target.Height)
            Dim srcDc As IntPtr = IntPtr.Zero
            Dim destDc As IntPtr = IntPtr.Zero
            Dim g As System.Drawing.Graphics = Nothing
            Dim done As Boolean = False
            Try
                g = System.Drawing.Graphics.FromImage(bmp)
                destDc = g.GetHdc()
                If isDesktopRuntime Then
                    ' 桌面 PrintWindow 支持递归子控件。
                    done = ApiPrintWindow(target.Handle, destDc, 0)
                End If
                If Not done Then
                    ' 回退：BitBlt 从屏幕 DC 截取目标面板区域（要求面板可见不被遮挡）。
                    Dim pt As System.Drawing.Point = target.PointToScreen(System.Drawing.Point.Empty)
                    srcDc = ApiGetDC(System.IntPtr.Zero)
                    ApiBitBlt(destDc, 0, 0, target.Width, target.Height, srcDc, pt.X, pt.Y, SRCCOPY)
                End If
            Catch exB As Exception
            Finally
                If destDc <> IntPtr.Zero Then
                    g.ReleaseHdc(destDc)
                End If
                If srcDc <> IntPtr.Zero Then
                    ApiReleaseDC(System.IntPtr.Zero, srcDc)
                End If
                If g IsNot Nothing Then
                    g.Dispose()
                End If
            End Try
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function

    ' 获取面板快照：recPanel 用自绘控件的 RenderToBitmap（可靠）；
    ' detailPanel 用屏幕 DC（标准控件），若不可见则临时显示置前截取后恢复。
    Private Function CapturePanelBitmap(ByVal panel As System.Windows.Forms.Control) As System.Drawing.Bitmap
        ' 找到 recPanel 内的 recListControl。
        Dim rl As RecListControl = Nothing
        Try
            Dim i As Integer
            For i = 0 To panel.Controls.Count - 1
                If TypeOf panel.Controls(i) Is RecListControl Then
                    rl = CType(panel.Controls(i), RecListControl)
                    Exit For
                End If
            Next
        Catch ex As Exception
        End Try
        If rl IsNot Nothing Then
            Dim b As System.Drawing.Bitmap = Nothing
            Try
                b = rl.RenderToBitmap()
            Catch ex As Exception
                b = Nothing
            End Try
            If b IsNot Nothing Then
                Return b
            End If
        End If

        ' 普通面板：detailPanel 若不可见（进入详情），不置前截图（会闪烁），
        ' 改为程序化绘制 loading 界面（动画期间真实 detailPanel 就是 loading 状态）。
        If panel Is detailPanel Then
            If Not panel.Visible Then
                Return CreateDetailLoadingBitmap(panel.Width, panel.Height)
            End If
            ' detailPanel 内是自绘双缓冲控件：用 RenderToBitmap 可靠截取。
            If detailVideoControl IsNot Nothing Then
                Dim vb As System.Drawing.Bitmap = Nothing
                Try
                    vb = detailVideoControl.RenderToBitmap()
                Catch ex As Exception
                    vb = Nothing
                End Try
                If vb IsNot Nothing Then
                    Return vb
                End If
            End If
        End If

        ' 普通面板：屏幕 DC 截图，必要时临时显示置前。
        Dim wasVisible As Boolean = panel.Visible
        Dim restored As Boolean = False
        Try
            If Not panel.Visible Then
                panel.Visible = True
            End If
            panel.BringToFront()
            panel.Update()
            restored = True
            Return CaptureControlImage(panel)
        Finally
            If restored AndAlso Not wasVisible Then
                panel.Visible = False
            End If
        End Try
    End Function

    ' 程序化绘制详情页 loading 界面（白底 + 顶部灰字），供进入动画占位，避免截图闪烁。
    Private Function CreateDetailLoadingBitmap(ByVal w As Integer, ByVal h As Integer) As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            If w < 10 Then
                w = 460
            End If
            If h < 10 Then
                h = 240
            End If
            bmp = New System.Drawing.Bitmap(w, h)
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            g.Clear(System.Drawing.Color.White)
            Dim br As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H88, &H88, &H88))
            Dim fnt As System.Drawing.Font = GetUiFont(9.0!, False)
            Dim msg As String = If(isChinese, "正在加载视频信息...", "Loading video info...")
            g.DrawString(msg, fnt, br, 12, 8)
            fnt.Dispose()
            br.Dispose()
            g.Dispose()
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function

    ' 面板推入过渡动画：fromPanel 滑出，toPanel 滑入。
    ' direction: 1 = 从右侧推入（to 从右边滑入，from 向左滑出），
    '           -1 = 从左侧推入（to 从左边滑入，from 向右滑出）。
    ' 用快照位图移动（不移动真实面板），CF 上避免整面板重绘卡顿。
    Private Sub StartPanelTransition(ByVal fromPanel As System.Windows.Forms.Control, ByVal toPanel As System.Windows.Forms.Control, ByVal direction As Integer, ByVal onDone As SimpleCallback)
        transitionTimer.Enabled = False
        If transitionActive AndAlso transitionFrom IsNot Nothing AndAlso transitionTo IsNot Nothing Then
            ' 前一次动画未结束：清理快照，恢复真实面板状态。
            If transitionFromPic IsNot Nothing Then
                Me.Controls.Remove(transitionFromPic)
                If transitionFromPic.Image IsNot Nothing Then
                    transitionFromPic.Image.Dispose()
                End If
                transitionFromPic.Dispose()
                transitionFromPic = Nothing
            End If
            If transitionToPic IsNot Nothing Then
                Me.Controls.Remove(transitionToPic)
                If transitionToPic.Image IsNot Nothing Then
                    transitionToPic.Image.Dispose()
                End If
                transitionToPic.Dispose()
                transitionToPic = Nothing
            End If
            transitionFrom.Left = transitionFromStartX
            transitionTo.Left = transitionToStartX
            transitionFrom.Visible = False
            transitionTo.Visible = True
            transitionTo.BringToFront()
            transitionActive = False
        End If

        Dim fromX As Integer = fromPanel.Left
        Dim toX As Integer = toPanel.Left
        Dim fromY As Integer = fromPanel.Top
        Dim toY As Integer = toPanel.Top
        Dim width As Integer = fromPanel.Width
        If width <= 0 Then
            width = toPanel.Width
        End If
        Dim height As Integer = fromPanel.Height
        If height <= 0 Then
            height = toPanel.Height
        End If

        ' 渲染两张快照位图。
        ' recPanel 内容来自 recListControl.RenderToBitmap（可靠，无需可见性）；
        ' detailPanel 用屏幕 DC（标准控件，需可见时截，见 CapturePanelBitmap）。
        Dim fromBmp As System.Drawing.Bitmap = CapturePanelBitmap(fromPanel)
        Dim toBmp As System.Drawing.Bitmap = CapturePanelBitmap(toPanel)

        ' 快照 PictureBox（加在 Form 顶层）。先加快照盖住，再隐藏真实面板，避免闪白。
        Dim fromPic As New System.Windows.Forms.PictureBox()
        fromPic.Width = width
        fromPic.Height = height
        fromPic.Location = New System.Drawing.Point(fromX, fromY)
        fromPic.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage
        If fromBmp IsNot Nothing Then
            fromPic.Image = fromBmp
        Else
            fromPic.BackColor = fromPanel.BackColor
        End If
        Dim toPic As New System.Windows.Forms.PictureBox()
        toPic.Width = width
        toPic.Height = height
        toPic.Location = New System.Drawing.Point(toX, toY)
        toPic.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage
        If toBmp IsNot Nothing Then
            toPic.Image = toBmp
        Else
            toPic.BackColor = toPanel.BackColor
        End If

        ' 关键顺序：先 Add 快照并置顶（盖住真实面板），再隐藏 fromPanel。
        Me.Controls.Add(fromPic)
        Me.Controls.Add(toPic)
        fromPic.BringToFront()
        toPic.BringToFront()
        fromPanel.Visible = False
        ' toPanel 保持可见（在快照下层，内容与 toPic 一致，结束移除快照后无缝衔接）。

        transitionFrom = fromPanel
        transitionTo = toPanel
        transitionOnDone = onDone
        transitionFromStartX = fromX
        transitionToStartX = toX
        transitionStartTick = System.Environment.TickCount
        transitionDir = direction
        transitionFromPic = fromPic
        transitionToPic = toPic
        transitionDoneCalled = False

        ' 起始位置：to 快照在屏幕外侧（对侧），from 快照在原位。
        toPic.Left = toX - (direction * width)
        fromPic.Left = fromX

        transitionActive = True
        transitionTimer.Enabled = True
    End Sub

    Private Sub TransitionTimerTick(ByVal sender As Object, ByVal e As System.EventArgs)
        If transitionFromPic Is Nothing OrElse transitionToPic Is Nothing Then
            transitionTimer.Enabled = False
            transitionActive = False
            Return
        End If
        ' 时间驱动：按实际经过时间计算进度，避免固定步数抖动。
        Dim elapsed As Integer = System.Environment.TickCount - transitionStartTick
        If elapsed < 0 Then
            elapsed = 0
        End If
        Dim frac As Double = elapsed / CDbl(transitionDurationMs)
        If frac > 1 Then
            frac = 1
        End If
        ' 缓动：三次 ease-in-out（起止慢、中间快），观感更平滑。
        Dim eased As Double = 0
        If frac < 0.5 Then
            eased = 4 * frac * frac * frac
        Else
            Dim f2 As Double = frac - 1
            eased = 1 + f2 * f2 * f2 * 4
        End If
        ' 整体位移 = direction * 缓动 * 宽度。
        ' from 从原位向 direction 方向滑出；to 从对侧滑入归位。
        Dim move As Integer = transitionDir * CInt(Math.Floor(eased * transitionFromPic.Width))
        transitionFromPic.Left = transitionFromStartX + move
        transitionToPic.Left = (transitionToStartX - transitionDir * transitionFromPic.Width) + move
        transitionFromPic.Refresh()
        transitionToPic.Refresh()

        If frac >= 1 Then
            transitionTimer.Enabled = False
            transitionActive = False
            ' 真实 toPanel 一直保持可见，移除快照后无缝衔接。
            transitionFrom.Left = transitionFromStartX
            transitionTo.Left = transitionToStartX
            transitionTo.BringToFront()
            ' 移除快照，显示真实面板。
            Me.Controls.Remove(transitionFromPic)
            Me.Controls.Remove(transitionToPic)
            If transitionFromPic.Image IsNot Nothing Then
                transitionFromPic.Image.Dispose()
            End If
            If transitionToPic.Image IsNot Nothing Then
                transitionToPic.Image.Dispose()
            End If
            transitionFromPic.Dispose()
            transitionToPic.Dispose()
            transitionFromPic = Nothing
            transitionToPic = Nothing
            transitionFrom.Visible = False
            transitionTo.Visible = True
            If Not transitionDoneCalled Then
                transitionDoneCalled = True
                Dim cb As SimpleCallback = transitionOnDone
                transitionOnDone = Nothing
                If cb IsNot Nothing Then
                    cb()
                End If
            End If
        End If
    End Sub

    ' ===== 全屏推入过渡（整屏含标题栏一起滑动，单一覆盖控件单次重绘，无撕裂/不同步）=====

    ' 内容区几何：与 lstSearch/recPanel/detailPanel 一致（标题栏下方）。
    Private Sub GetContentRegion(ByRef rx As Integer, ByRef ry As Integer, ByRef rw As Integer, ByRef rh As Integer)
        Dim w As Integer = Me.ClientSize.Width
        Dim h As Integer = Me.ClientSize.Height
        rx = 4
        ry = TITLE_BAR_H + 6
        rw = w - 8
        rh = h - ry - 10
        If rw < 10 Then
            rw = 10
        End If
        If rh < 10 Then
            rh = 10
        End If
    End Sub

    ' 全屏过渡覆盖控件：双缓冲合成 from/to 快照，OnPaint 单次 DrawImage，杜绝闪烁/撕裂。
    Private Class PageTransitionOverlay
        Inherits System.Windows.Forms.Control
        Public FromImage As System.Drawing.Bitmap = Nothing
        Public ToImage As System.Drawing.Bitmap = Nothing
        Public FromX As Integer = 0
        Public ToX As Integer = 0
        Private mBuf As System.Drawing.Bitmap = Nothing
        Private mG As System.Drawing.Graphics = Nothing

        Public Sub New()
            MyBase.New()
            Me.BackColor = System.Drawing.Color.Black
        End Sub

        ' 合成当前帧到内存缓冲（双缓冲）。
        Public Sub Compose()
            Dim w As Integer = Me.ClientSize.Width
            Dim h As Integer = Me.ClientSize.Height
            If w <= 0 OrElse h <= 0 Then
                w = Me.Width
                h = Me.Height
            End If
            If w <= 0 OrElse h <= 0 Then
                Return
            End If
            If mBuf Is Nothing OrElse mBuf.Width <> w OrElse mBuf.Height <> h Then
                If mG IsNot Nothing Then
                    mG.Dispose()
                End If
                If mBuf IsNot Nothing Then
                    mBuf.Dispose()
                End If
                mBuf = New System.Drawing.Bitmap(w, h)
                mG = System.Drawing.Graphics.FromImage(mBuf)
            End If
            mG.Clear(Me.BackColor)
            Try
                If FromImage IsNot Nothing Then
                    Dim srcR As New System.Drawing.Rectangle(0, 0, FromImage.Width, FromImage.Height)
                    Dim dstR As New System.Drawing.Rectangle(FromX, 0, w, h)
                    mG.DrawImage(FromImage, dstR, srcR, System.Drawing.GraphicsUnit.Pixel)
                End If
                If ToImage IsNot Nothing Then
                    Dim srcR As New System.Drawing.Rectangle(0, 0, ToImage.Width, ToImage.Height)
                    Dim dstR As New System.Drawing.Rectangle(ToX, 0, w, h)
                    mG.DrawImage(ToImage, dstR, srcR, System.Drawing.GraphicsUnit.Pixel)
                End If
            Catch ex As Exception
            End Try
        End Sub

        Protected Overrides Sub OnPaintBackground(ByVal e As System.Windows.Forms.PaintEventArgs)
        End Sub

        Protected Overrides Sub OnPaint(ByVal e As System.Windows.Forms.PaintEventArgs)
            If mBuf Is Nothing Then
                Compose()
            End If
            If mBuf IsNot Nothing Then
                Try
                    Dim srcR As New System.Drawing.Rectangle(0, 0, mBuf.Width, mBuf.Height)
                    Dim dstR As New System.Drawing.Rectangle(0, 0, mBuf.Width, mBuf.Height)
                    e.Graphics.DrawImage(mBuf, dstR, srcR, System.Drawing.GraphicsUnit.Pixel)
                Catch ex As Exception
                End Try
            End If
        End Sub

        Protected Overrides Sub Dispose(ByVal disposing As Boolean)
            If disposing Then
                If mG IsNot Nothing Then
                    mG.Dispose()
                End If
                If mBuf IsNot Nothing Then
                    mBuf.Dispose()
                End If
                mG = Nothing
                mBuf = Nothing
            End If
            MyBase.Dispose(disposing)
        End Sub
    End Class

    ' 清理全屏过渡覆盖。
    Private Sub CleanupFullTransition()
        If contentOv IsNot Nothing Then
            Try
                Me.Controls.Remove(contentOv)
            Catch ex As Exception
            End Try
            If contentOv.FromImage IsNot Nothing Then
                Try
                    contentOv.FromImage.Dispose()
                Catch ex As Exception
                End Try
            End If
            If contentOv.ToImage IsNot Nothing Then
                Try
                    contentOv.ToImage.Dispose()
                Catch ex As Exception
                End Try
            End If
            Try
                contentOv.Dispose()
            Catch ex As Exception
            End Try
            contentOv = Nothing
        End If
    End Sub

    ' 全屏推入过渡：from/to 两张全屏快照（含标题栏）作为整体一起滑动。
    ' direction: -1 = 前进（to 从右侧滑入，from 向左滑出）；1 = 后退（to 从左侧滑入，from 向右滑出）。
    Private Sub StartFullTransition(ByVal fromBmp As System.Drawing.Bitmap, ByVal toBmp As System.Drawing.Bitmap, ByVal direction As Integer, ByVal onDone As SimpleCallback)
        Try
            If contentTimer Is Nothing Then
                contentTimer = New System.Windows.Forms.Timer()
                contentTimer.Interval = 16
                AddHandler contentTimer.Tick, AddressOf ContentTransitionTick
            End If
            contentTimer.Enabled = False
            CleanupFullTransition()

            Dim ov As New PageTransitionOverlay()
            ov.FromImage = fromBmp
            ov.ToImage = toBmp
            ov.BackColor = Me.BackColor
            ov.Left = 0
            ov.Top = 0
            ov.Width = Me.ClientSize.Width
            ov.Height = Me.ClientSize.Height

            contentOv = ov
            contentStartTick = System.Environment.TickCount
            contentDir = direction
            contentDoneCalled = False
            contentOnDone = onDone
            contentActive = True

            ' 起始位置 + 首帧合成（覆盖层全屏不透明盖住所有真实面板，无需隐藏，避免闪烁）。
            Dim w As Integer = ov.Width
            If direction = -1 Then
                ov.FromX = 0
                ov.ToX = w
            Else
                ov.FromX = 0
                ov.ToX = -w
            End If
            ov.Compose()
            Me.Controls.Add(ov)
            ov.BringToFront()
            ov.Refresh()
            contentTimer.Enabled = True
        Catch ex As Exception
            CleanupFullTransition()
            If onDone IsNot Nothing Then
                onDone()
            End If
        End Try
    End Sub

    Private Sub ContentTransitionTick(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            If contentOv Is Nothing Then
                contentTimer.Enabled = False
                contentActive = False
                Return
            End If
            Dim elapsed As Integer = System.Environment.TickCount - contentStartTick
            If elapsed < 0 Then
                elapsed = 0
            End If
            Dim frac As Double = elapsed / CDbl(contentDurationMs)
            If frac > 1 Then
                frac = 1
            End If
            Dim eased As Double = 0
            If frac < 0.5 Then
                eased = 4 * frac * frac * frac
            Else
                Dim f2 As Double = frac - 1
                eased = 1 + f2 * f2 * f2 * 4
            End If
            Dim w As Integer = 0
            If contentOv.FromImage IsNot Nothing Then
                w = contentOv.FromImage.Width
            End If
            If w <= 0 Then
                w = Me.ClientSize.Width
            End If
            Dim move As Integer = CInt(Math.Floor(eased * w))
            If contentDir = -1 Then
                contentOv.FromX = -move
                contentOv.ToX = w - move
            Else
                contentOv.FromX = move
                contentOv.ToX = -w + move
            End If
            ' 合成到缓冲 + 强制重绘（Refresh = Invalidate + Update，双缓冲无闪烁）。
            contentOv.Compose()
            contentOv.Refresh()
            If frac >= 1 Then
                contentTimer.Enabled = False
                contentActive = False
                ' 先切换真实页面（在覆盖层下方），强制重绘后再移除覆盖层，避免闪旧页面/闪白。
                If Not contentDoneCalled Then
                    contentDoneCalled = True
                    Dim cb As SimpleCallback = contentOnDone
                    contentOnDone = Nothing
                    If cb IsNot Nothing Then
                        Try
                            cb()
                        Catch exCb As Exception
                            WriteLog("transition onDone: " & exCb.GetType().FullName & " | " & exCb.Message & " | " & exCb.StackTrace)
                        End Try
                    End If
                End If
                Try
                    Me.Update()
                Catch exU As Exception
                End Try
                CleanupFullTransition()
            End If
        Catch ex As Exception
            WriteLog("transition tick: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            contentTimer.Enabled = False
            contentActive = False
            Try
                CleanupFullTransition()
            Catch ex2 As Exception
            End Try
        End Try
    End Sub

    ' 空白占位页（捕获失败时的兜底快照）。
    Private Function CreateBlankPageBitmap(ByVal w As Integer, ByVal h As Integer) As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            If w < 10 Then
                w = 460
            End If
            If h < 10 Then
                h = 240
            End If
            bmp = New System.Drawing.Bitmap(w, h)
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            g.Clear(System.Drawing.Color.White)
            g.Dispose()
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function

    ' 捕获当前窗体整屏（含标题栏）。桌面：PrintWindow；WM：屏幕 DC。
    Private Function CaptureFormBitmap() As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            Dim w As Integer = Me.ClientSize.Width
            Dim h As Integer = Me.ClientSize.Height
            If w < 10 OrElse h < 10 Then
                Return Nothing
            End If
            bmp = New System.Drawing.Bitmap(w, h)
            Dim srcDc As IntPtr = IntPtr.Zero
            Dim destDc As IntPtr = IntPtr.Zero
            Dim g As System.Drawing.Graphics = Nothing
            Dim done As Boolean = False
            Try
                g = System.Drawing.Graphics.FromImage(bmp)
                destDc = g.GetHdc()
                If isDesktopRuntime Then
                    Try
                        done = ApiPrintWindow(Me.Handle, destDc, PRF_CLIENT Or PRF_CHILDREN Or PRF_ERASEBKGND)
                    Catch exP As Exception
                        done = False
                    End Try
                End If
                If Not done Then
                    Dim pt As System.Drawing.Point = Me.PointToScreen(System.Drawing.Point.Empty)
                    srcDc = ApiGetDC(System.IntPtr.Zero)
                    ApiBitBlt(destDc, 0, 0, w, h, srcDc, pt.X, pt.Y, SRCCOPY)
                End If
            Catch exB As Exception
            Finally
                If destDc <> IntPtr.Zero Then
                    g.ReleaseHdc(destDc)
                End If
                If srcDc <> IntPtr.Zero Then
                    ApiReleaseDC(System.IntPtr.Zero, srcDc)
                End If
                If g IsNot Nothing Then
                    g.Dispose()
                End If
            End Try
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function

    ' 程序化构建目标页整屏快照：白底 + 标题栏（深色标题文字）+ 内容区（占位快照）。
    Private Function CreateToPageBitmap(ByVal title As String, ByVal contentBmp As System.Drawing.Bitmap) As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            Dim w As Integer = Me.ClientSize.Width
            Dim h As Integer = Me.ClientSize.Height
            If w < 10 Then
                w = 460
            End If
            If h < 10 Then
                h = 240
            End If
            bmp = New System.Drawing.Bitmap(w, h)
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            g.Clear(System.Drawing.Color.White)
            ' 标题栏：白底 + 底部浅灰分隔线。
            Dim linePen As New System.Drawing.Pen(System.Drawing.Color.FromArgb(&HE0, &HE0, &HE0))
            g.DrawLine(linePen, 0, TITLE_BAR_H - 1, w, TITLE_BAR_H - 1)
            linePen.Dispose()
            If title.Length > 0 Then
                Dim font As System.Drawing.Font = GetUiFont(9.0!, True)
                Dim br As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H22, &H22, &H22))
                Dim sf As New System.Drawing.StringFormat()
                sf.LineAlignment = System.Drawing.StringAlignment.Center
                g.DrawString(title, font, br, New System.Drawing.RectangleF(42, 0, w - 42, TITLE_BAR_H), sf)
                font.Dispose()
                br.Dispose()
                sf.Dispose()
            End If
            ' 内容区。
            If contentBmp IsNot Nothing Then
                Dim srcR As New System.Drawing.Rectangle(0, 0, contentBmp.Width, contentBmp.Height)
                Dim dstR As New System.Drawing.Rectangle(4, TITLE_BAR_H + 6, w - 8, h - (TITLE_BAR_H + 6) - 10)
                If dstR.Width > 0 AndAlso dstR.Height > 0 Then
                    g.DrawImage(contentBmp, dstR, srcR, System.Drawing.GraphicsUnit.Pixel)
                End If
            End If
            g.Dispose()
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function

    ' 在占位页内容区顶部画提示文字（如 loading）。
    Private Sub DrawHintOnBitmap(ByVal bmp As System.Drawing.Bitmap, ByVal hint As String)
        Try
            If bmp Is Nothing OrElse hint.Length = 0 Then
                Return
            End If
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            Dim font As System.Drawing.Font = GetUiFont(9.0!, False)
            Dim br As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H88, &H88, &H88))
            g.DrawString(hint, font, br, 12, 52)
            font.Dispose()
            br.Dispose()
            g.Dispose()
        Catch ex As Exception
        End Try
    End Sub

    ' 主页 → 子页：主页整屏快照 + 目标页整屏快照，前进过渡，结束后 onDone 切换页面。
    Private Sub GoFromHome(ByVal direction As Integer, ByVal titleText As String, ByVal hint As String, ByVal onDone As SimpleCallback)
        Try
            Dim fromBmp As System.Drawing.Bitmap = Nothing
            If homePanel IsNot Nothing Then
                Try
                    homePanel.Bounds = Me.ClientRectangle
                Catch ex As Exception
                End Try
                fromBmp = homePanel.RenderToBitmap()
            End If
            Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(titleText, Nothing)
            DrawHintOnBitmap(toBmp, hint)
            StartFullTransition(fromBmp, toBmp, direction, onDone)
        Catch ex As Exception
            If onDone IsNot Nothing Then
                onDone()
            End If
        End Try
    End Sub

    ' 子页 → 主页：捕获当前子页整屏与主页，播放后退过渡，结束后 ShowHome。
    Private Sub GoToHome()
        Try
            ' 离开子页时停止扫码轮询（个人中心扫码登录）。
            Try
                StopLoginPolling()
            Catch exP As Exception
            End Try
            Dim fromBmp As System.Drawing.Bitmap = CaptureFormBitmap()
            ' 返回主页：先清空主页高亮并同步重绘，避免旧 hover/选中 select.png 烧进返回快照。
            If homePanel IsNot Nothing Then
                Try
                    homeSelected = -1
                    homeHover = -1
                    homePanel.Invalidate()
                    homePanel.Update()
                Catch exH As Exception
                End Try
            End If
            Dim toBmp As System.Drawing.Bitmap = Nothing
            If homePanel IsNot Nothing Then
                Try
                    homePanel.Bounds = Me.ClientRectangle
                Catch ex As Exception
                End Try
                toBmp = homePanel.RenderToBitmap()
            End If
            StartFullTransition(fromBmp, toBmp, 1, New SimpleCallback(AddressOf ShowHome))
        Catch ex As Exception
            ShowHome()
        End Try
    End Sub

    ' 打开详情页：切换到 detailPanel 并后台拉取视频信息。
    Private Sub OpenVideoDetail(ByVal bvid As String)
        If String.IsNullOrEmpty(bvid) Then
            Return
        End If
        detailBvid = bvid
        detailAid = ""
        OpenVideoDetailCore()
    End Sub

    Private Sub OpenVideoDetailAid(ByVal aid As String)
        If String.IsNullOrEmpty(aid) Then
            Return
        End If
        detailBvid = ""
        detailAid = aid
        OpenVideoDetailCore()
    End Sub

    Private Sub OpenVideoDetailCore()
        detailView = True
        ' 记录来源：收藏列表用 recPanel 卡片（favView 非空）；历史用 recPanel 卡片（histView）。
        ' 其余用搜索卡片列表（searchView）。优先级：收藏 > 历史 > 搜索。
        detailFromFav = (favView <> "")
        detailFromHist = histView
        detailFromRec = (Not detailFromFav) AndAlso (Not searchView)
        SetPageContext("detail")
        ShowSubPageTop()

        ' 先捕获来源页整屏（含标题栏，在隐藏之前）。
        Dim fromBmp As System.Drawing.Bitmap = CaptureFormBitmap()

        txtResult.Visible = False
        lstSearch.Visible = False
        recView = False
        searchView = False

        If detailVideoControl IsNot Nothing Then
            detailVideoControl.ShowStateText(If(isChinese, "正在加载视频信息...", "Loading video info..."))
            detailVideoControl.Refresh()
        Else
            detailPanel.SuspendLayout()
            detailPanel.Controls.Clear()
            Dim loadingLbl As New System.Windows.Forms.Label()
            loadingLbl.Text = If(isChinese, "正在加载视频信息...", "Loading video info...")
            loadingLbl.Location = New System.Drawing.Point(12, 8)
            loadingLbl.Size = New System.Drawing.Size(detailPanel.ClientSize.Width - 24, 24)
            detailPanel.Controls.Add(loadingLbl)
            detailPanel.ResumeLayout()
            detailPanel.Refresh()
        End If

        ' 整屏推入过渡：来源页整屏（含标题栏）向左滑出，详情页整屏从右侧滑入。
        Dim rx As Integer = 0
        Dim ry As Integer = 0
        Dim rw As Integer = 0
        Dim rh As Integer = 0
        GetContentRegion(rx, ry, rw, rh)
        Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(If(isChinese, "视频详情", "Video Detail"), CreateDetailLoadingBitmap(rw, rh))
        StartFullTransition(fromBmp, toBmp, -1, New SimpleCallback(AddressOf FinishDetailEntry))

        If detailLoading Then
            Return
        End If
        detailLoading = True
        Dim t As New System.Threading.Thread(AddressOf FetchVideoDetailWorker)
        t.Start()
    End Sub

    ' 过渡结束：显示详情面板。
    Private Sub FinishDetailEntry()
        Try
            detailPanel.BringToFront()
            detailPanel.Visible = True
        Catch ex As Exception
        End Try
    End Sub

    Private Sub FetchVideoDetailWorker()
        Dim body As String = ""
        Dim errMsg As String = ""

        WriteLog("detail fetch start bvid=" & detailBvid)

        Try
            Dim url As String = "https://api.bilibili.com/x/web-interface/view?bvid=" & detailBvid
            If detailBvid = "" Then
                url = "https://api.bilibili.com/x/web-interface/view?aid=" & detailAid
            End If
            ' WM 网络不稳，重试一次（NetWorkUtil 内部已重试，这里再包一层）。
            Dim attempt As Integer = 0
            While attempt < 2
                attempt += 1
                Try
                    body = NetWorkUtil.GetText(url, savedCookies)
                    Exit While
                Catch exR As Exception
                    If attempt >= 2 Then
                        Throw exR
                    End If
                    Try
                        System.Threading.Thread.Sleep(800)
                    Catch exS As Exception
                    End Try
                End Try
            End While
        Catch ex As Exception
            errMsg = ex.GetType().FullName & " | " & ex.Message
            WriteLog("detail fetch: " & errMsg & " | " & ex.StackTrace)
        End Try

        detailLastBody = body
        detailLastErr = errMsg
        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowVideoDetailOnUi))
        Catch ex As Exception
        End Try
    End Sub

    Private Sub ShowVideoDetailOnUi()
        detailLoading = False
        If detailLastErr <> "" Then
            ShowDetailMessage(If(isChinese, "加载失败：" & detailLastErr, "Failed: " & detailLastErr))
            Return
        End If

        Dim body As String = detailLastBody
        Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
        If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
            ShowDetailMessage(If(isChinese, "视频不存在或已失效", "Video unavailable"))
            Return
        End If

        ' 提取 data 对象区域（只取首个 "data":{" 到结尾）。
        Dim dStart As Integer = body.IndexOf("""data"":{")
        If dStart < 0 Then
            ShowDetailMessage(If(isChinese, "视频信息解析失败", "Failed to parse"))
            Return
        End If
        body = body.Substring(dStart)

        Dim mTitle As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """title"":\s*""((?:[^""\\]|\\.)*)""")
        Dim title As String = ""
        If mTitle.Success Then
            title = StripHtml(UnescapeJson(mTitle.Groups(1).Value))
        End If

        Dim mPic As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """pic"":\s*""([^""]+)""")
        Dim pic As String = ""
        If mPic.Success Then
            pic = mPic.Groups(1).Value
        End If

        ' owner.name：取 data 区域内最后一个 name（owner 在最外层 data 中）。
        Dim mOwner As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """owner"":\s*\{.*?""name"":\s*""([^""]*)""")
        Dim author As String = ""
        If mOwner.Success Then
            author = UnescapeJson(mOwner.Groups(1).Value)
        End If

        ' stat 内的数据。
        Dim sStart As Integer = body.IndexOf("""stat"":{")
        Dim statBody As String = body
        If sStart >= 0 Then
            Dim sEnd As Integer = statBody.IndexOf("}", sStart)
            If sEnd > sStart Then
                statBody = statBody.Substring(sStart, sEnd - sStart + 1)
            End If
        End If

        Dim view As String = ""
        Dim mView As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(statBody, """view"":\s*(\d+)")
        If mView.Success Then
            view = mView.Groups(1).Value
        End If
        Dim danmaku As String = ""
        Dim mDan As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(statBody, """danmaku"":\s*(\d+)")
        If mDan.Success Then
            danmaku = mDan.Groups(1).Value
        End If
        Dim likeC As String = ""
        Dim mLike As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(statBody, """like"":\s*(\d+)")
        If mLike.Success Then
            likeC = mLike.Groups(1).Value
        End If
        Dim coinC As String = ""
        Dim mCoin As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(statBody, """coin"":\s*(\d+)")
        If mCoin.Success Then
            coinC = mCoin.Groups(1).Value
        End If
        Dim favC As String = ""
        Dim mFav As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(statBody, """favorite"":\s*(\d+)")
        If mFav.Success Then
            favC = mFav.Groups(1).Value
        End If
        Dim replyC As String = ""
        Dim mReply As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(statBody, """reply"":\s*(\d+)")
        If mReply.Success Then
            replyC = mReply.Groups(1).Value
        End If

        Dim desc As String = ""
        Dim mDesc As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """desc"":\s*""((?:[^""\\]|\\.)*)""")
        If mDesc.Success Then
            desc = UnescapeJson(mDesc.Groups(1).Value)
        End If

        Dim pubdate As String = ""
        Dim mPub As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """pubdate"":\s*(\d+)")
        If mPub.Success Then
            Try
                Dim ts As Double = CDbl(mPub.Groups(1).Value)
                pubdate = DateTimeFromUnix(CLng(ts))
            Catch exD As Exception
            End Try
        End If

        Dim duration As String = ""
        Dim mDur As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """duration"":\s*(\d+)")
        If mDur.Success Then
            duration = FormatDuration(mDur.Groups(1).Value)
        End If

        ' 分P 数量。
        Dim partCount As String = ""
        Dim mVideos As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """videos"":\s*(\d+)")
        If mVideos.Success Then
            partCount = mVideos.Groups(1).Value
        End If

        BuildVideoDetail(pic, title, author, view, danmaku, likeC, coinC, favC, replyC, desc, pubdate, duration, partCount)
    End Sub

    Private Sub ShowDetailMessage(ByVal msg As String)
        If detailVideoControl IsNot Nothing Then
            detailVideoControl.ShowStateText(msg)
            detailVideoControl.Refresh()
            Return
        End If
        detailPanel.SuspendLayout()
        detailPanel.Controls.Clear()
        Dim lbl As New System.Windows.Forms.Label()
        lbl.Text = msg
        lbl.Location = New System.Drawing.Point(12, 8)
        lbl.Size = New System.Drawing.Size(detailPanel.ClientSize.Width - 24, 24)
        detailPanel.Controls.Add(lbl)
        detailPanel.ResumeLayout()
        detailPanel.Refresh()
    End Sub

    Private Function DateTimeFromUnix(ByVal secs As Long) As String
        Try
            Dim epoch As New DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)
            Dim dt As DateTime = epoch.AddSeconds(secs).ToLocalTime()
            Return dt.ToString("yyyy-MM-dd HH:mm")
        Catch ex As Exception
            Return ""
        End Try
    End Function

    Private Function FormatDuration(ByVal secs As String) As String
        Try
            Dim total As Integer = CInt(secs)
            Dim mm As Integer = total \ 60
            Dim ss As Integer = total Mod 60
            If mm >= 60 Then
                Dim hh As Integer = mm \ 60
                mm = mm Mod 60
                Return hh.ToString() & ":" & mm.ToString("00") & ":" & ss.ToString("00")
            End If
            Return mm.ToString() & ":" & ss.ToString("00")
        Catch ex As Exception
            Return ""
        End Try
    End Function

    ' 估算多行 Label 高度：按中文字符宽度估算行数，保守偏大，避免文字溢出被裁剪。
    Private Function EstMultilineH(ByVal text As String, ByVal f As System.Drawing.Font, ByVal width As Integer) As Integer
        Try
            Dim g As System.Drawing.Graphics = detailPanel.CreateGraphics()
            Dim cw As Single = g.MeasureString("测", f).Width
            If cw <= 1 Then
                cw = 14
            End If
            g.Dispose()
            If width < 1 Then
                width = 1
            End If
            Dim charsPerLine As Integer = CInt(width / cw)
            If charsPerLine < 1 Then
                charsPerLine = 1
            End If
            Dim lines As Integer = CInt(Math.Ceiling(text.Length / CDbl(charsPerLine)))
            If lines < 1 Then
                lines = 1
            End If
            Return lines * fontLineH + 2
        Catch ex As Exception
            Return fontLineH * 2
        End Try
    End Function

    ' 构建详情页 UI：数据交给自绘 VideoDetailControl（双缓冲 + 触摸滚动）。
    Private Sub BuildVideoDetail(ByVal pic As String, ByVal title As String, ByVal author As String, ByVal view As String, ByVal danmaku As String, ByVal likeC As String, ByVal coinC As String, ByVal favC As String, ByVal replyC As String, ByVal desc As String, ByVal pubdate As String, ByVal duration As String, ByVal partCount As String)
        If detailVideoControl IsNot Nothing Then
            detailVideoControl.SetData(title, author, duration, partCount, view, danmaku, likeC, coinC, favC, replyC, pubdate, desc, isChinese)
            If pic <> "" Then
                detailVideoControl.SetCover(Nothing)
                StartDetailCoverLoad(pic)
            End If
            detailPanel.Refresh()
        End If
    End Sub

    Private Sub DetailPlay_Click(ByVal sender As Object, ByVal e As System.EventArgs)
        If detailBvid = "" Then
            Return
        End If
        ' 走 StartPlay 全流程：加载层（小电视动画）→ 后台获取地址 → 直接启动播放器。
        playFromDetail = True
        pendingPlayBvid = detailBvid
        av706Fetched = False
        av706VideoUrl = ""
        av706Converted = False
        If av706Proxy IsNot Nothing Then
            av706Proxy.Shutdown()
            av706Proxy = Nothing
        End If
        StartPlay()
    End Sub

    ' 返回推荐列表。
    Private Sub CloseVideoDetail()
        WriteLog("detail back clicked")
        ' 捕获详情页整屏作为 from 快照（须在 detailView 置 False 之前）。
        Dim fromBmp As System.Drawing.Bitmap = CaptureFormBitmap()
        detailView = False
        txtResult.Visible = False
        ' 返回列表前清除按下高亮，避免残留进返回过渡的目标快照。
        Try
            recListControl.ClearPressedHighlight()
        Catch exClr As Exception
        End Try

        If detailFromFav Then
            ' 返回收藏视频卡片列表。
            recView = False
            histView = False
            favView = "videos"
            favListActive = True
            lstSearch.Visible = False
            recListControl.HistoryMode = False
            coverItems = favVideoItems
            Dim backTitle As String = favFolderName
            If backTitle = "" Then
                backTitle = If(isChinese, "我的收藏", "Favorites")
            End If
            Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(backTitle, recListControl.RenderToBitmap())
            recPanel.Refresh()
            StartFullTransition(fromBmp, toBmp, 1, New SimpleCallback(AddressOf FinishCloseDetailFav))
        ElseIf detailFromRec Then
            ' 返回推荐或历史卡片列表（历史与推荐共用 recPanel）。
            recView = True
            histView = detailFromHist
            favView = ""
            favListActive = False
            lstSearch.Visible = False
            Dim backTitle As String = If(isChinese, "推荐视频", "Recommendations")
            If detailFromHist Then
                recListControl.HistoryMode = True
                coverItems = histItems
                backTitle = If(isChinese, "播放历史", "History")
            Else
                recListControl.HistoryMode = False
                coverItems = recItems
            End If
            Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(backTitle, recListControl.RenderToBitmap())
            recPanel.Refresh()
            StartFullTransition(fromBmp, toBmp, 1, New SimpleCallback(AddressOf FinishCloseDetailRec))
        Else
            ' 返回搜索卡片列表（整屏捕获，含标题栏搜索框 + 列表）。
            recView = False
            searchView = True
            recListControl.HistoryMode = False
            recPanel.BringToFront()
            recPanel.Visible = True
            If searchResults IsNot Nothing AndAlso searchResults.Count > 0 Then
                BuildSearchRows()
            End If
            Me.Update()
            Dim toBmp As System.Drawing.Bitmap = CaptureFormBitmap()
            recPanel.Refresh()
            StartFullTransition(fromBmp, toBmp, 1, New SimpleCallback(AddressOf FinishCloseDetailSearch))
        End If
    End Sub

    ' 关闭详情过渡结束：显示推荐/历史面板并释放封面。
    Private Sub FinishCloseDetailRec()
        Try
            If detailFromHist Then
                SetPageContext("history")
                histView = True
                recListControl.HistoryMode = True
                coverItems = histItems
            Else
                SetPageContext("rec")
                histView = False
                recListControl.HistoryMode = False
                coverItems = recItems
            End If
            recPanel.BringToFront()
            recPanel.Visible = True
            recListControl.ClearPressedHighlight()
            ResumeCoverLoad()
        Catch ex As Exception
        End Try
        ReleaseDetailCover()
    End Sub

    ' 关闭详情过渡结束：恢复收藏视频列表并释放封面。
    Private Sub FinishCloseDetailFav()
        Try
            SetPageContext("favvideos")
            favView = "videos"
            favListActive = True
            histView = False
            recView = False
            recListControl.HistoryMode = False
            coverItems = favVideoItems
            recPanel.BringToFront()
            recPanel.Visible = True
            recListControl.ClearPressedHighlight()
            ResumeCoverLoad()
        Catch ex As Exception
        End Try
        ReleaseDetailCover()
    End Sub

    ' 关闭详情过渡结束：恢复来源列表页（含标题栏搜索框）。
    Private Sub FinishCloseDetailSearch()
        Try
            SetPageContext("search")
            ShowSearchPage()
        Catch ex As Exception
        End Try
        ResumeCoverLoad()
        ReleaseDetailCover()
    End Sub

    ' 动画结束后释放详情封面（此时 detailPanel 已隐藏，PictureBox 不再重绘，安全）。
    Private Sub ReleaseDetailCover()
        Try
            If detailVideoControl IsNot Nothing Then
                detailVideoControl.ClearCover()
            End If
        Catch exNull As Exception
        End Try
        Try
            If detailCoverBitmap IsNot Nothing Then
                detailCoverBitmap.Dispose()
                detailCoverBitmap = Nothing
            End If
        Catch exD As Exception
        End Try
        detailCoverBox = Nothing
    End Sub

    ' 详情页封面：单张下载后填图。
    ' 下载图片：先完整读入字节数组，再解码为 Bitmap。
    ' CF 的 Bitmap(Stream) 是延迟解码，流关闭后解码不完整/失败，必须先读入内存。
    ' maxW/maxH>0 时按比例缩小到该尺寸内（封面用小图，避免 WM 内存不足）。
    Private Function DownloadImageBytes(ByVal url As String) As System.Drawing.Bitmap
        Return NetWorkUtil.GetImage(url, 0, 0)
    End Function

    Private Function DownloadImageScaled(ByVal url As String, ByVal maxW As Integer, ByVal maxH As Integer) As System.Drawing.Bitmap
        Return NetWorkUtil.GetImage(url, maxW, maxH)
    End Function

    Private Sub StartDetailCoverLoad(ByVal url As String)
        If url = "" Then
            Return
        End If
        ' 强制每次详情页都重新加载封面（旧线程的结果通过 generation 丢弃）。
        detailCoverLoading = True
        detailCoverIdx = 0
        detailCoverUrl = url
        detailCoverGeneration += 1
        Dim gen As Integer = detailCoverGeneration
        Dim t As New System.Threading.Thread(AddressOf FetchDetailCoverWorker)
        t.Start()
        ' 记住本代 generation，供 worker 校验。
        detailCoverWorkerGen = gen
    End Sub

    Private Sub FetchDetailCoverWorker()
        Dim myGen As Integer = detailCoverWorkerGen
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            If Not String.IsNullOrEmpty(detailCoverUrl) Then
                bmp = DownloadImageBytes(detailCoverUrl)
            End If
        Catch ex As Exception
        End Try

        ' 若期间已开启新一代加载（新详情页），丢弃本结果。
        If myGen <> detailCoverGeneration Then
            If bmp IsNot Nothing Then
                Try
                    bmp.Dispose()
                Catch exD As Exception
                End Try
            End If
            Return
        End If

        If detailCoverBitmap IsNot Nothing Then
            Try
                detailCoverBitmap.Dispose()
            Catch exD As Exception
            End Try
        End If
        detailCoverBitmap = bmp
        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowDetailCoverOnUi))
        Catch ex As Exception
        End Try
    End Sub

    Private Sub ShowDetailCoverOnUi()
        Try
            If detailCoverBitmap IsNot Nothing Then
                ' 若用户已返回推荐页，不把封面交给已断开的控件。
                If Not detailView Then
                    Dim orphan As System.Drawing.Bitmap = detailCoverBitmap
                    detailCoverBitmap = Nothing
                    Try
                        orphan.Dispose()
                    Catch exO As Exception
                    End Try
                Else
                    If detailVideoControl IsNot Nothing Then
                        detailVideoControl.SetCover(detailCoverBitmap)
                        detailVideoControl.Refresh()
                    End If
                End If
            End If
        Catch ex As Exception
        End Try
        detailCoverLoading = False
    End Sub

    ' 封面加载：按可见窗口并发下载（推荐/历史共用）。
    ' 关键：跳过"无图/已加载/加载中/下载失败"的条目，失败不再重试，避免卡死封面链。

    ' 封面管线当前作用的数据列表。
    Private Function CurrentCoverItems() As System.Collections.ArrayList
        If coverItems IsNot Nothing Then
            Return coverItems
        End If
        Return recItems
    End Function

    ' 封面加载是否应推进（推荐/搜索/历史/收藏列表页可见）。
    Private Function IsCoverViewActive() As Boolean
        Return (recView OrElse searchView OrElse histView OrElse favView <> "")
    End Function

    Private Sub StartCoverLoad(ByVal idx As Integer)
        If CurrentCoverItems() Is Nothing Then
            Return
        End If
        If recCoverLoading Then
            ' 防卡死：若标记加载中但无活跃线程，重置后重新推进。
            If recCoverActive = 0 Then
                recCoverLoading = False
            Else
                Return
            End If
        End If
        recCoverLoading = True
        recCoverActive = 0
        PumpCoverWorkers()
    End Sub

    ' 按可见窗口加载封面：只加载可见行 ± 缓冲区内的缺失封面（随滚动移动），远处不重载。
    Private Sub PumpCoverWorkers()
        Try
            Dim items As System.Collections.ArrayList = CurrentCoverItems()
            If Not IsCoverViewActive() Then
                ' 不在列表页：暂停推进。清空加载标志，允许回到列表页后重新开始。
                recCoverLoading = False
                Return
            End If
            If items Is Nothing OrElse recListControl Is Nothing Then
                recCoverLoading = False
                Return
            End If
            Dim first As Integer = recListControl.FirstVisibleRow()
            Dim last As Integer = recListControl.LastVisibleRow()
            Dim loadFirst As Integer = first - 12
            If loadFirst < 0 Then
                loadFirst = 0
            End If
            Dim loadLast As Integer = last + 12
            If loadLast >= items.Count Then
                loadLast = items.Count - 1
            End If
            While recCoverActive < recCoverMaxActive
                Dim nextIdx As Integer = FindNextUnloadedCover(items, loadFirst, loadLast)
                If nextIdx < 0 Then
                    Exit While
                End If
                Dim ri As RecItem = CType(items(nextIdx), RecItem)
                ri.CoverLoading = True
                recCoverActive += 1
                Dim worker As New CoverLoader(Me, ri)
                Dim t As New System.Threading.Thread(AddressOf worker.Run)
                t.Start()
            End While
            If recCoverActive = 0 Then
                recCoverLoading = False
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 在 [first, last] 窗口内找下一个未加载的封面索引（-1 = 无）。
    Private Function FindNextUnloadedCover(ByVal items As System.Collections.ArrayList, ByVal first As Integer, ByVal last As Integer) As Integer
        Try
            If items Is Nothing Then
                Return -1
            End If
            If first < 0 Then
                first = 0
            End If
            If last >= items.Count Then
                last = items.Count - 1
            End If
            Dim i As Integer
            For i = first To last
                Dim ri As RecItem = CType(items(i), RecItem)
                If (Not String.IsNullOrEmpty(ri.Pic)) AndAlso ri.Bitmap Is Nothing AndAlso (Not ri.CoverFailed) AndAlso (Not ri.CoverLoading) Then
                    Return i
                End If
            Next
        Catch ex As Exception
        End Try
        Return -1
    End Function

    ' 每个下载任务独立对象（CF 2.0 无 ParameterizedThreadStart，用实例方法传参）。
    ' 直接持有 RecItem 引用（而非列表+索引），列表刷新/重排后仍写回正确条目。
    Private Class CoverLoader
        Public mOwner As MainForm
        Public mItem As RecItem
        Public Sub New(ByVal owner As MainForm, ByVal item As RecItem)
            mOwner = owner
            mItem = item
        End Sub
        Public Sub Run()
            mOwner.FetchCoverItem(mItem)
        End Sub
    End Class

    Private Sub FetchCoverItem(ByVal ri As RecItem)
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            If ri IsNot Nothing AndAlso Not String.IsNullOrEmpty(ri.Pic) Then
                Dim url As String = ri.Pic
                ' 部分接口返回协议相对地址（//i0.hdslb.com/...），补 https 前缀。
                If url.StartsWith("//") Then
                    url = "https:" & url
                End If
                bmp = DownloadImageScaled(url, 336, 188)
                If ri.Bitmap IsNot Nothing Then
                    Try
                        ri.Bitmap.Dispose()
                    Catch exD As Exception
                    End Try
                End If
                ri.Bitmap = bmp
                If bmp Is Nothing Then
                    ri.CoverFailed = True
                End If
            End If
            If ri IsNot Nothing Then
                ri.CoverLoading = False
            End If
        Catch ex As Exception
            ' 失败也清标志，允许后续重试。
            Try
                If ri IsNot Nothing Then
                    ri.CoverLoading = False
                    ri.CoverFailed = True
                End If
            Catch ex2 As Exception
            End Try
        End Try

        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowCoverOnUi))
        Catch ex As Exception
        End Try
    End Sub

    Private Sub ShowCoverOnUi()
        ' 自绘列表：封面位图已就绪，整表重绘（只重绘可见行，开销小）。
        Try
            If recListControl IsNot Nothing Then
                recListControl.Invalidate()
            End If
        Catch ex As Exception
        End Try
        If recCoverActive > 0 Then
            recCoverActive -= 1
        End If
        PumpCoverWorkers()
    End Sub

    Private Sub AdvanceCoverLoad()
        Try
            If recView Then
                StartCoverLoad(0)
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 回到推荐页或滚动后：恢复封面加载管线（处理 recCoverLoading 被暂停卡住的情况）。
    Private Sub ResumeCoverLoad()
        Try
            Dim items As System.Collections.ArrayList = CurrentCoverItems()
            If Not IsCoverViewActive() OrElse items Is Nothing OrElse items.Count = 0 Then
                Return
            End If
            If recCoverLoading Then
                PumpCoverWorkers()
            Else
                StartCoverLoad(0)
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 滚动变化：按可见窗口清理远处封面位图（节省 WM 内存），并恢复缺失封面加载。
    Private Sub RecListScrollChanged(ByVal sender As Object, ByVal e As System.EventArgs)
        TrimCovers()
    End Sub

    ' 桌面鼠标滚轮：路由到推荐列表（WndProcHooker 挂钩，CF 无 OnMouseWheel）。
    ' 设备硬件返回键：多数 WM 设备发 WM_KEYDOWN + VK_ESC(0x1B)；部分 HTC 发 VK_TBACK(0x7F)；另有 VK_BACK(0x08)。
    ' 拦截后统一走 OnBackClick（返回上一页），并置 handled 阻止默认关闭行为。
    Private Const WM_MOUSEWHEEL As Integer = &H20A
    Private Const WM_MOUSEHOVER As Integer = &H2A1
    Private Const WM_KEYDOWN As Integer = &H100
    Private Const WM_KEYUP As Integer = &H101
    Private Const VK_ESC As Integer = &H1B
    Private Const VK_TBACK As Integer = &H7F
    Private Const VK_BACK As Integer = &H08
    Private Const VK_UP As Integer = &H26
    Private Const VK_DOWN As Integer = &H28
    Private Const VK_LEFT As Integer = &H25
    Private Const VK_RIGHT As Integer = &H27
    Private Const VK_RETURN As Integer = &H0D
    Private Const VK_SPACE As Integer = &H20
    Private Const VK_1 As Integer = &H31
    Private Function MainFormWheelProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer, ByRef handled As Boolean) As Integer
        If msg = WM_MOUSEHOVER Then
            ' 吞掉悬停消息：桌面 .NET 2.0 的 ListView.OnMouseHover -> GetItemAt 会算术溢出崩溃，
            ' 本应用不用悬停提示，直接跳过（设备端也无需 hover）。
            handled = True
            Return 0
        End If
        If msg = WM_KEYDOWN Then
            Try
                Dim vk As Integer = wParam And &HFFFF
                If vk = VK_ESC OrElse vk = VK_TBACK Then
                    OnBackClick(Me, System.EventArgs.Empty)
                    handled = True
                    Return 0
                End If
                If vk = VK_BACK Then
                    ' VK_BACK 既是硬件返回键也是 Backspace 键。
                    ' 文本框持有焦点时（正在输入）必须当作 Backspace 删除字符，不能触发返回。
                    Dim editingText As Boolean = (txtSearch IsNot Nothing AndAlso txtSearch.Focused) OrElse (txtResult IsNot Nothing AndAlso txtResult.Focused)
                    If Not editingText Then
                        OnBackClick(Me, System.EventArgs.Empty)
                        handled = True
                        Return 0
                    End If
                End If
                ' 方向键 + 确定键：按键选择导航。
                If HandleNavigationKey(vk) Then
                    handled = True
                    Return 0
                End If
            Catch ex As Exception
            End Try
        End If
        If msg = WM_KEYUP Then
            Try
                Dim vk As Integer = wParam And &HFFFF
                If vk = VK_ESC OrElse vk = VK_TBACK Then
                    ' 吞掉对应的 keyup，防止默认关闭行为。
                    handled = True
                    Return 0
                End If
                If vk = VK_BACK Then
                    Dim editingText2 As Boolean = (txtSearch IsNot Nothing AndAlso txtSearch.Focused) OrElse (txtResult IsNot Nothing AndAlso txtResult.Focused)
                    If Not editingText2 Then
                        handled = True
                        Return 0
                    End If
                End If
            Catch ex As Exception
            End Try
        End If
        If msg = WM_MOUSEWHEEL Then
            Try
                Dim delta As Short = CShort((wParam >> 16) And &HFFFF)
                If (recView OrElse histView OrElse favView <> "" OrElse searchView) AndAlso recListControl IsNot Nothing AndAlso recListControl.Visible Then
                    recListControl.ScrollByWheel(delta)
                    handled = True
                    Return 0
                End If
                If detailView AndAlso detailVideoControl IsNot Nothing AndAlso detailVideoControl.Visible Then
                    detailVideoControl.ScrollByWheel(delta)
                    handled = True
                    Return 0
                End If
            Catch ex As Exception
            End Try
        End If
        handled = False
        Return 0
    End Function

    ' 按键选择导航：根据当前页面把方向键/确定键路由到对应选择逻辑。
    ' 返回 True 表示按键已处理（上层应置 handled 阻止继续传递）。
    Private Function HandleNavigationKey(ByVal vk As Integer) As Boolean
        Try
            ' 详情页优先（打开详情时 recView 已复位，但 histView/favView 可能仍非空）。
            If detailView Then
                If vk = VK_UP Then
                    If detailVideoControl IsNot Nothing Then
                        detailVideoControl.ScrollByWheel(120)
                    End If
                    Return True
                End If
                If vk = VK_DOWN Then
                    If detailVideoControl IsNot Nothing Then
                        detailVideoControl.ScrollByWheel(-120)
                    End If
                    Return True
                End If
                If vk = VK_RETURN OrElse vk = VK_SPACE OrElse vk = VK_RIGHT Then
                    If detailVideoControl IsNot Nothing Then
                        detailVideoControl.PerformPlayClick()
                    End If
                    Return True
                End If
                Return False
            End If

            ' 列表页（推荐/搜索/历史/收藏）。
            If recView OrElse histView OrElse favView <> "" OrElse searchView Then
                If recListControl Is Nothing OrElse Not recListControl.Visible Then
                    Return False
                End If
                ' 搜索页输入框持有焦点时：方向键/回车应留给输入框（移动光标/再次搜索），
                ' 不抢走列表导航。列表获得焦点后方向键/回车才导航。
                If searchView AndAlso txtSearch IsNot Nothing AndAlso txtSearch.Focused Then
                    Return False
                End If
                If vk = VK_UP Then
                    recListControl.MoveFocus(-1)
                    Return True
                End If
                If vk = VK_DOWN Then
                    recListControl.MoveFocus(1)
                    Return True
                End If
                If vk = VK_RETURN OrElse vk = VK_SPACE OrElse vk = VK_RIGHT Then
                    recListControl.ActivateFocusedRow()
                    Return True
                End If
                Return False
            End If

            ' 主页：竖排选择栏。
            If currentPage = "home" OrElse (homePanel IsNot Nothing AndAlso homePanel.Visible) Then
                If vk = VK_UP Then
                    homeKeyActive = True
                    homeKeySel -= 1
                    If homeKeySel < 0 Then
                        homeKeySel = homeButtons.Count - 1
                    End If
                    homePanel.Invalidate()
                    homePanel.Update()
                    Return True
                End If
                If vk = VK_DOWN Then
                    homeKeyActive = True
                    homeKeySel += 1
                    If homeKeySel >= homeButtons.Count Then
                        homeKeySel = 0
                    End If
                    homePanel.Invalidate()
                    homePanel.Update()
                    Return True
                End If
                If vk = VK_RETURN OrElse vk = VK_SPACE OrElse vk = VK_RIGHT Then
                    If homeKeySel >= 0 AndAlso homeKeySel < homeButtons.Count Then
                        HomeNavigate(homeKeySel)
                    End If
                    Return True
                End If
                ' 数字键直接进入对应菜单（PMC 式：1=个人中心 ... 5=收藏视频）。
                Dim numIdx As Integer = vk - VK_1
                If numIdx >= 0 AndAlso numIdx < homeButtons.Count Then
                    homeKeyActive = True
                    homeKeySel = numIdx
                    HomeNavigate(numIdx)
                    Return True
                End If
                Return False
            End If

            ' 个人中心：确定键触发退出登录（已登录视图）。
            If currentPage = "profile" Then
                If vk = VK_RETURN OrElse vk = VK_SPACE Then
                    If profileControl IsNot Nothing Then
                        profileControl.PerformAction()
                    End If
                    Return True
                End If
            End If
        Catch ex As Exception
        End Try
        Return False
    End Function

    ' 清理不可见区域（可见行 ± 缓冲区之外）的封面位图；重新进入时按需重载。
    Private Sub TrimCovers()
        Try
            Dim items As System.Collections.ArrayList = CurrentCoverItems()
            If items Is Nothing OrElse items.Count = 0 OrElse recListControl Is Nothing Then
                Return
            End If
            Dim first As Integer = recListControl.FirstVisibleRow()
            Dim last As Integer = recListControl.LastVisibleRow()
            If first < 0 Then
                first = 0
            End If
            If last >= items.Count Then
                last = items.Count - 1
            End If
            Const KEEP As Integer = 18
            Dim keepStart As Integer = first - KEEP
            If keepStart < 0 Then
                keepStart = 0
            End If
            Dim keepEnd As Integer = last + KEEP
            If keepEnd >= items.Count Then
                keepEnd = items.Count - 1
            End If
            Dim i As Integer
            For i = 0 To items.Count - 1
                If i < keepStart OrElse i > keepEnd Then
                    Dim ri As RecItem = CType(items(i), RecItem)
                    If ri.Bitmap IsNot Nothing Then
                        Try
                            ri.Bitmap.Dispose()
                        Catch ex As Exception
                        End Try
                        ri.Bitmap = Nothing
                        ri.CoverFailed = False
                    End If
                End If
            Next
            ' 当前可见区若有缺封面条目（例如被清理后回滚），恢复加载。
            ResumeCoverLoad()
        Catch ex As Exception
        End Try
    End Sub

    ' 推荐条目数据容器（CF 2.0 无属性语法限制，用字段）。
    Friend Class RecItem
        Public Bvid As String
        Public Pic As String
        Public Title As String
        Public Author As String
        Public View As String
        Public Danmaku As String = ""
        Public Bitmap As System.Drawing.Bitmap = Nothing
        Public CoverFailed As Boolean = False
        Public CoverLoading As Boolean = False

        Public Sub New(ByVal sBv As String, ByVal sPic As String, ByVal sTitle As String, ByVal sAuthor As String, ByVal sView As String)
            Bvid = sBv
            Pic = sPic
            Title = sTitle
            Author = sAuthor
            View = sView
        End Sub
    End Class

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

    ' 子页面标准控件统一应用微软雅黑（桌面）；WM 用 Tahoma。自绘控件（RecListControl/HomePanel）各自处理。
    Private Sub ApplyUiFonts()
        Try
            lstSearch.Font = GetUiFont(9.0!, False)
        Catch ex As Exception
        End Try
        Try
            txtResult.Font = GetUiFont(9.0!, False)
        Catch ex As Exception
        End Try
        Try
            txtSearch.Font = GetUiFont(9.0!, False)
        Catch ex As Exception
        End Try
        Try
            btnPlayStream.Font = GetUiFont(9.0!, False)
            btnPlayOffline.Font = GetUiFont(9.0!, False)
        Catch ex As Exception
        End Try
    End Sub

    Private Sub LayoutControls()
        Dim w As Integer = Me.ClientSize.Width
        Dim h As Integer = Me.ClientSize.Height
        Dim titleH As Integer = TITLE_BAR_H

        ' Title bar now hosts the search: back button left, search box middle, button right.
        Panel1.Height = titleH

        ' 搜索框起始位置：返回按钮(32px + 边距)之后。
        txtSearch.Height = 24
        txtSearch.Top = CInt((titleH - txtSearch.Height) / 2)
        txtSearch.Left = 42
        txtSearch.Width = w - txtSearch.Left - 38
        If txtSearch.Width < 80 Then
            txtSearch.Width = 80
        End If

        btnSearch.Height = 32
        btnSearch.Top = CInt((titleH - btnSearch.Height) / 2)
        btnSearch.Left = txtSearch.Right + 4
        btnSearch.Width = 32

        ' 个人中心面板。
        If profilePanel IsNot Nothing Then
            profilePanel.Left = 4
            profilePanel.Width = w - 8
            profilePanel.Top = titleH + 6
            profilePanel.Height = h - profilePanel.Top - 10
        End If

        lstSearch.Left = 4
        lstSearch.Width = w - 8
        lstSearch.Top = titleH + 6
        lstSearch.Height = h - lstSearch.Top - 10
        If lstSearch.Height < 50 Then
            lstSearch.Height = 50
        End If

        recPanel.Left = 4
        recPanel.Width = w - 8
        recPanel.Top = titleH + 6
        recPanel.Height = h - recPanel.Top - 10

        detailPanel.Left = 4
        detailPanel.Width = w - 8
        detailPanel.Top = titleH + 6
        detailPanel.Height = h - detailPanel.Top - 10

        txtResult.Left = 8
        txtResult.Width = w - 16
        txtResult.Top = titleH + 12
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

        ' 白条背景 + 底部浅灰分隔线。
        g.Clear(System.Drawing.Color.White)
        Dim linePen As New System.Drawing.Pen(System.Drawing.Color.FromArgb(&HE0, &HE0, &HE0))
        g.DrawLine(linePen, 0, Panel1.Height - 1, Panel1.Width, Panel1.Height - 1)
        linePen.Dispose()

        ' 页面标题（深色文字，位于返回按钮右侧；主页/搜索页无标题）。
        If pageTitle.Length > 0 Then
            Dim textBrush As New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H22, &H22, &H22))
            Dim font As System.Drawing.Font = GetUiFont(9.0!, True)
            Dim textLeft As Single = CSng(42)
            Dim textRect As New System.Drawing.RectangleF(textLeft, 0, CSng(Panel1.Width - textLeft), Panel1.Height)
            Dim sf As New System.Drawing.StringFormat()
            sf.Alignment = System.Drawing.StringAlignment.Near
            sf.LineAlignment = System.Drawing.StringAlignment.Center
            sf.FormatFlags = System.Drawing.StringFormatFlags.NoWrap
            g.DrawString(pageTitle, font, textBrush, textRect, sf)
            font.Dispose()
            textBrush.Dispose()
            sf.Dispose()
        End If
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
        MenuNavigate(If(isChinese, "播放历史", "History"), New SimpleCallback(AddressOf LoadHistory))
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

    ' ---- 图片加载线程设置（单/双） ----
    Private Sub mnuImageThreadsSingle_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuImageThreadsSingle.Click
        SetImageThreads(1)
    End Sub

    Private Sub mnuImageThreadsDual_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuImageThreadsDual.Click
        SetImageThreads(2)
    End Sub

    Private Sub SetImageThreads(ByVal n As Integer)
        imageThreads = n
        If recCoverMaxActive <> n Then
            recCoverMaxActive = n
            ' 若当前无加载任务，直接生效；有任务则下次加载时生效。
            If recView AndAlso Not recCoverLoading Then
                StartCoverLoad(0)
            End If
        End If
        UpdateImageThreadsMenu()
        SaveImageThreads()
        If isChinese Then
            txtResult.Text = "图片加载线程：" & If(n = 1, "单线程", "双线程")
        Else
            txtResult.Text = "Image threads: " & If(n = 1, "Single", "Dual")
        End If
    End Sub

    Private Sub UpdateImageThreadsMenu()
        mnuImageThreadsSingle.Checked = (imageThreads = 1)
        mnuImageThreadsDual.Checked = (imageThreads = 2)
    End Sub

    Private Function GetImageThreadsPath() As String
        Return GetAppDir() & "\imgthreads.txt"
    End Function

    Private Sub SaveImageThreads()
        Try
            System.IO.Directory.CreateDirectory(GetAppDir())
            Dim fs As New System.IO.FileStream(GetImageThreadsPath(), System.IO.FileMode.Create)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.ASCII)
            w.Write(imageThreads.ToString())
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub LoadImageThreads()
        imageThreads = 1
        Try
            If System.IO.File.Exists(GetImageThreadsPath()) Then
                Dim sr As New System.IO.StreamReader(GetImageThreadsPath())
                Dim s As String = sr.ReadToEnd().Trim()
                sr.Close()
                If s = "2" Then
                    imageThreads = 2
                End If
            End If
        Catch ex As Exception
        End Try
        recCoverMaxActive = imageThreads
        UpdateImageThreadsMenu()
    End Sub

    ' ---- 播放器选择（TCPMP / CorePlayer / Ostwind 内置） ----
    Private Sub mnuPlayerTcpmp_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuPlayerTcpmp.Click
        SetSelectedPlayer("tcpmp")
    End Sub

    Private Sub mnuPlayerCorePlayer_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuPlayerCorePlayer.Click
        SetSelectedPlayer("coreplayer")
    End Sub

    Private Sub mnuPlayerOstwind_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuPlayerOstwind.Click
        SetSelectedPlayer("ostwind")
    End Sub

    Private Sub SetSelectedPlayer(ByVal name As String)
        selectedPlayer = name
        UpdatePlayerMenu()
        SavePlayerSelection()
        If isChinese Then
            Dim pn As String = "内置播放器"
            If name = "tcpmp" Then
                pn = "TCPMP"
            ElseIf name = "coreplayer" Then
                pn = "CorePlayer"
            End If
            txtResult.Text = "播放器已设为：" & pn
        Else
            txtResult.Text = "Player: " & name
        End If
    End Sub

    Private Sub UpdatePlayerMenu()
        mnuPlayerTcpmp.Checked = (selectedPlayer = "tcpmp")
        mnuPlayerCorePlayer.Checked = (selectedPlayer = "coreplayer")
        mnuPlayerOstwind.Checked = (selectedPlayer = "ostwind")
    End Sub

    Private Function GetPlayerSelectionPath() As String
        Return GetAppDir() & "\player.txt"
    End Function

    Private Sub SavePlayerSelection()
        Try
            System.IO.Directory.CreateDirectory(GetAppDir())
            Dim fs As New System.IO.FileStream(GetPlayerSelectionPath(), System.IO.FileMode.Create)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.ASCII)
            w.Write(selectedPlayer)
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub LoadPlayerSelection()
        selectedPlayer = "tcpmp"
        Try
            If System.IO.File.Exists(GetPlayerSelectionPath()) Then
                Dim sr As New System.IO.StreamReader(GetPlayerSelectionPath())
                Dim s As String = sr.ReadToEnd().Trim()
                sr.Close()
                If s = "tcpmp" OrElse s = "coreplayer" OrElse s = "ostwind" Then
                    selectedPlayer = s
                End If
            End If
        Catch ex As Exception
        End Try
        UpdatePlayerMenu()
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
        ShowResultView()
        If isChinese Then
            txtResult.Text = "正在检查更新..."
        Else
            txtResult.Text = "Checking for updates..."
        End If
        updateBody = ""
        updateErr = ""
        Dim t As New System.Threading.Thread(AddressOf CheckUpdateWorker)
        t.Start()
    End Sub

    ' 后台线程：拉取版本信息（避免阻塞 UI）。
    Private Sub CheckUpdateWorker()
        Dim url As String = "http://www.biliclassic.cn/wm/api/version.json"
        Try
            updateBody = NetWorkUtil.GetTextEx(url, "", "https://www.bilibili.com/")
            If String.IsNullOrEmpty(updateBody) Then
                updateErr = If(isChinese, "检查更新失败: 服务器无响应", "Update check failed: no response")
            End If
        Catch ex As Exception
            updateErr = If(isChinese, "检查更新失败: " & ex.Message, "Update check failed: " & ex.Message)
        End Try
        Try
            Me.Invoke(New SimpleCallback(AddressOf FinishUpdateCheck))
        Catch exI As Exception
            WriteLog("update invoke: " & exI.GetType().FullName & " | " & exI.Message)
        End Try
    End Sub

    ' UI 线程：解析并展示更新结果。
    Private Sub FinishUpdateCheck()
        If updateErr <> "" Then
            txtResult.Text = updateErr
            Return
        End If

        Try
            Dim body As String = updateBody
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

    Private Sub StartPlay()
        StopLoginPolling()
        ShowResultView()

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
            btnPlayOffline_Click(Me, New System.EventArgs())
        Else
            btnPlayStream_Click(Me, New System.EventArgs())
        End If
    End Sub

    ' 后台线程：获取 360P 播放地址（避免同步阻塞 UI 消息泵），完成后回 UI 线程继续。
    Private Sub FetchAndPlayWorker()
        Dim sbLocal As System.Text.StringBuilder = fetchSb
        Try
            If pendingPlayBvid <> "" Then
                DoPlayVideoFlow("", pendingPlayBvid, sbLocal)
            Else
                DoPlayVideoFlow(currentAv, "", sbLocal)
            End If
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
        If playFromDetail Then
            playFromDetail = False
            RestoreDetailAfterPlay()
        End If
    End Sub

    Private Sub PlayOfflineOnUi()
        If loadingCancelRequested Then
            HideLoadingOverlay()
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
        If playFromDetail Then
            playFromDetail = False
            RestoreDetailAfterPlay()
        End If
    End Sub

    ' 播放流程结束后回到详情页（详情内容未被清空，仅重新显示）。
    Private Sub RestoreDetailAfterPlay()
        If detailBvid = "" Then
            Return
        End If
        detailView = True
        txtResult.Visible = False
        lstSearch.Visible = False
        recView = False
        ' 从播放返回详情页：来源页整屏向左滑出，详情页整屏从右侧推入。
        Dim fromBmp As System.Drawing.Bitmap = CaptureFormBitmap()
        Dim rx As Integer = 0
        Dim ry As Integer = 0
        Dim rw As Integer = 0
        Dim rh As Integer = 0
        GetContentRegion(rx, ry, rw, rh)
        Dim detailContent As System.Drawing.Bitmap = Nothing
        If detailPanel.Visible Then
            If detailVideoControl IsNot Nothing Then
                detailContent = detailVideoControl.RenderToBitmap()
            End If
            If detailContent Is Nothing Then
                detailContent = CaptureControlImage(detailPanel)
            End If
        End If
        If detailContent Is Nothing Then
            detailContent = CreateDetailLoadingBitmap(rw, rh)
        End If
        Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(If(isChinese, "视频详情", "Video Detail"), detailContent)
        StartFullTransition(fromBmp, toBmp, -1, New SimpleCallback(AddressOf FinishDetailEntry))
        detailPanel.Refresh()
    End Sub

    ' 全屏加载层：显示并带状态文字。
    Private Sub ShowLoadingOverlay(ByVal status As String)
        If loadingOverlay Is Nothing Then
            Return
        End If
        ' 隐藏顶部标题栏（返回按钮 + 页面标题）：加载层自带返回按钮，避免重复。
        ' 加载中可能连续多次 Show（换状态文字），只在首次隐藏前记录原可见状态。
        Try
            If Not loadingOverlay.IsShowing Then
                loadingPanel1Visible = Panel1.Visible
                Panel1.Visible = False
            End If
        Catch exP As Exception
        End Try
        loadingOverlay.ShowOverlay(status)
    End Sub

    Private Sub HideLoadingOverlay()
        If loadingOverlay IsNot Nothing Then
            loadingOverlay.HideOverlay()
        End If
        ' 恢复顶部标题栏。
        Try
            Panel1.Visible = loadingPanel1Visible
        Catch exP As Exception
        End Try
    End Sub

    ' 返回键：请求取消转码/加载，回到主界面。
    Private Sub LoadingOverlay_ReturnPressed(ByVal sender As Object, ByVal e As System.EventArgs)
        loadingCancelRequested = True
        HideLoadingOverlay()
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
            If playFromDetail Then
                playFromDetail = False
                RestoreDetailAfterPlay()
            End If
        Catch ex As Exception
            WriteLog("convert resume: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
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

    ' 硬件键盘/方向键：搜索框内按回车直接搜索（WM 无 Tab 键确认，Enter 最顺手）。
    Private Sub txtSearch_KeyDown(ByVal sender As Object, ByVal e As System.Windows.Forms.KeyEventArgs) Handles txtSearch.KeyDown
        Try
            If e.KeyCode = System.Windows.Forms.Keys.Enter Then
                btnSearch_Click(btnSearch, System.EventArgs.Empty)
                e.Handled = True
            End If
        Catch ex As Exception
        End Try
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
            btnSearch.Enabled = True
            If bvid <> "" Then
                OpenVideoDetail(bvid)
            Else
                OpenVideoDetailAid(aid)
            End If
            Return
        End If

        ' Keyword -> video search (后台线程，避免阻塞 UI 消息泵)
        searchKeyword = kw
        searchPage = 1
        searchHasMore = False
        searchLoading = True
        searchResults = New System.Collections.ArrayList()
        AddSearchHistory(kw)
        ' 重新搜索：清空页面，立即显示搜索加载状态（小电视动画）。
        searchView = True
        recListControl.HistoryMode = False
        recPanel.BringToFront()
        recPanel.Visible = True
        recListControl.ShowStateText(If(isChinese, "正在搜索...", "Searching..."))
        recPanel.Refresh()
        Dim t As New System.Threading.Thread(AddressOf SearchVideoWorker)
        t.Start()
    End Sub

    ' ===== 搜索历史 =====

    ' 从文件加载搜索历史（最近在前）。
    Private Sub LoadSearchHistory()
        searchHistory = New System.Collections.ArrayList()
        Try
            Dim p As String = GetAppDir() & "\search_history.txt"
            If System.IO.File.Exists(p) Then
                Dim sr As New System.IO.StreamReader(p, System.Text.Encoding.UTF8)
                While Not sr.EndOfStream
                    Dim line As String = sr.ReadLine().Trim()
                    If line <> "" Then
                        searchHistory.Add(line)
                        If searchHistory.Count >= 20 Then
                            Exit While
                        End If
                    End If
                End While
                sr.Close()
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 保存搜索历史到文件。
    Private Sub SaveSearchHistory()
        Try
            If searchHistory Is Nothing Then
                Return
            End If
            System.IO.Directory.CreateDirectory(GetAppDir())
            Dim fs As New System.IO.FileStream(GetAppDir() & "\search_history.txt", System.IO.FileMode.Create)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.UTF8)
            Dim i As Integer
            For i = 0 To searchHistory.Count - 1
                w.WriteLine(CStr(searchHistory(i)))
            Next
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    ' 记录一次搜索：去重后插到最前，上限 20 条。
    Private Sub AddSearchHistory(ByVal kw As String)
        If String.IsNullOrEmpty(kw) Then
            Return
        End If
        If searchHistory Is Nothing Then
            searchHistory = New System.Collections.ArrayList()
        End If
        Dim i As Integer = 0
        While i < searchHistory.Count
            If String.Compare(CStr(searchHistory(i)), kw, True) = 0 Then
                searchHistory.RemoveAt(i)
            Else
                i += 1
            End If
        End While
        searchHistory.Insert(0, kw)
        While searchHistory.Count > 20
            searchHistory.RemoveAt(searchHistory.Count - 1)
        End While
        SaveSearchHistory()
    End Sub

    ' 构建搜索历史行（自绘卡片：关键词 + 占位封面 + "搜索历史"标签）。
    Private Sub BuildSearchHistoryRows()
        Try
            If recListControl Is Nothing OrElse searchHistory Is Nothing OrElse searchHistory.Count = 0 Then
                Return
            End If
            Dim items As New System.Collections.ArrayList()
            Dim i As Integer
            For i = 0 To searchHistory.Count - 1
                Dim kw As String = CStr(searchHistory(i))
                ' 每行只显示关键词，不重复"搜索历史"标签。
                Dim ri As New RecItem("", "", kw, "", "")
                items.Add(ri)
            Next
            histView = False
            favView = ""
            favListActive = False
            recListControl.HistoryMode = False
            coverItems = items
            Dim w As Integer = recPanel.ClientSize.Width
            If w < 10 Then
                w = 460
            End If
            ' 紧凑单行：只显示关键词。
            Dim rowH As Integer = 34
            recListControl.SetData(items, rowH, fontLineH, fontSubH, isChinese)
            recListControl.SetShowMore(False)
            recListControl.NoCoverMode = True
        Catch ex As Exception
        End Try
    End Sub

    ' 搜索分页：加载下一页（后台线程）。
    Private Sub LoadMoreSearch()
        If searchLoading Then
            Return
        End If
        searchLoading = True
        searchPage += 1
        Dim t As New System.Threading.Thread(AddressOf SearchVideoWorker)
        t.Start()
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
                        OpenVideoDetail(mBv2.Groups(1).Value)
                        Return
                    End If
                End If
                ' Extract the trailing BV number from the list line
                Dim mBv As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(sel, "(BV\w+)")
                If mBv.Success Then
                    OpenVideoDetail(mBv.Groups(1).Value)
                End If
            End If
        End If
    End Sub

    Private Sub PlayBvid(ByVal bvid As String)
        btnSearch.Enabled = False
        pendingPlayBvid = ""
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
        recPanel.Visible = False
        recView = False
        detailPanel.Visible = False
        detailView = False
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

    ' 后台线程：搜索（避免同步阻塞 UI 消息泵），完成后回 UI 线程更新列表。
    Private Sub SearchVideoWorker()
        Dim kw As String = searchKeyword
        searchErrText = ""


        Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        Try
            Dim signer As New WbiSigner()
            If Not signer.FetchKeys(ua, "https://www.bilibili.com/") Then
                searchErrText = If(isChinese, "WBI 密钥获取失败", "WBI key fetch failed")
            Else
                Dim vp As New System.Collections.Generic.Dictionary(Of String, String)()
                vp("search_type") = "video"
                vp("keyword") = kw
                vp("page") = searchPage.ToString()
                Dim searchUrl As String = signer.SignUrl("https://api.bilibili.com/x/web-interface/wbi/search/type", vp)

                Dim body As String = NetWorkUtil.GetTextEx(searchUrl, savedCookies, "https://search.bilibili.com/")

                ' Anchor on each bvid; take title forward and author backward so the
                ' fields stay correctly paired even when some result types lack bvid.
                Dim mB As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, """bvid"":\s*""([^""]+)""")
                Dim patT As String = """title"":\s*""((?:[^""\\]|\\.)*)"""
                Dim patA As String = """author"":\s*""([^""]*)"""
                Dim patPic As String = """pic"":\s*""([^""]+)"""
                Dim patPlay As String = """play"":\s*(\d+)"
                Dim patDan As String = """video_review"":\s*(\d+)"
                Dim rawCount As Integer = 0
                Dim newAdded As Integer = 0
                For Each m As System.Text.RegularExpressions.Match In mB
                    If rawCount >= 20 Then
                        Exit For
                    End If
                    rawCount += 1
                    Dim bv As String = m.Groups(1).Value
                    ' 去重：跨页/重复返回的同一 bvid 只保留一条。
                    Dim dupFound As Boolean = False
                    Dim di As Integer
                    For di = 0 To searchResults.Count - 1
                        If CType(searchResults(di), RecItem).Bvid = bv Then
                            dupFound = True
                            Exit For
                        End If
                    Next
                    If dupFound Then
                        Continue For
                    End If
                    Dim tail As Integer = System.Math.Min(600, body.Length - m.Index)
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
                    ' 封面/播放量/弹幕数：取 bvid 之后的 pic/play/video_review（部分条目可能缺失）。
                    Dim pic As String = ""
                    Dim mp As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patPic)
                    If mp.Success Then
                        pic = mp.Groups(1).Value
                        If pic.StartsWith("//") Then
                            pic = "https:" & pic
                        End If
                    End If
                    Dim view As String = ""
                    Dim mv As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patPlay)
                    If mv.Success Then
                        view = mv.Groups(1).Value
                    End If
                    Dim danmaku As String = ""
                    Dim md As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, patDan)
                    If md.Success Then
                        danmaku = md.Groups(1).Value
                    End If
                    If bv <> "" Then
                        Dim ri As New RecItem(bv, pic, title, author, view)
                        ri.Danmaku = danmaku
                        searchResults.Add(ri)
                        newAdded += 1
                    End If
                Next
                ' 本页拉满（20 条）且新增了内容才可能有下一页；全重复则停。
                If rawCount >= 20 AndAlso newAdded > 0 Then
                    searchHasMore = True
                Else
                    searchHasMore = False
                End If
            End If
        Catch ex As Exception
            WriteLog("search: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            searchErrText = If(isChinese, "搜索失败: " & ex.Message, "search failed: " & ex.Message)
        End Try
        Try
            Me.Invoke(New SimpleCallback(AddressOf FinishSearchAsync))
        Catch exI As Exception
            WriteLog("search invoke: " & exI.GetType().FullName & " | " & exI.Message)
            btnSearch.Enabled = True
        End Try
    End Sub

    ' UI 线程：展示搜索结果（自绘卡片列表）。
    Private Sub FinishSearchAsync()
        searchLoading = False
        btnSearch.Enabled = True
        If searchErrText <> "" Then
            txtResult.Text = searchErrText
            lstSearch.Visible = False
            recPanel.Visible = False
            searchView = False
            txtResult.Visible = True
            Return
        End If
        lstSearch.Visible = False
        txtResult.Visible = False
        searchView = True
        recListControl.HistoryMode = False
        recPanel.BringToFront()
        recPanel.Visible = True
        BuildSearchRows()
        recPanel.Refresh()
        ' 封面加载：有结果就推进（跳过无封面/已加载条目）。
        If searchResults IsNot Nothing AndAlso searchResults.Count > 0 Then
            If recCoverLoading Then
                PumpCoverWorkers()
            Else
                StartCoverLoad(0)
            End If
            ' 有结果：焦点移到结果列表，方向键选择、回车打开。
            Try
                recListControl.Focus()
            Catch exF As Exception
            End Try
        End If
    End Sub

    ' Load the logged-in user's watch history (mirrors Android HistoryApi).
    ' bilibili returns a cursor (max/view_at/business) used to request the next page.
    Private Sub LoadHistory()
        If String.IsNullOrEmpty(savedCookies) Then
            SetPageContext("history")
            ShowSubPageTop()
            recPanel.BringToFront()
            recPanel.Visible = True
            recListControl.HistoryMode = True
            recListControl.ShowStateText(If(isChinese, "请先登录后再查看历史记录", "Please log in first to view history"))
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
        histPendingLoadMore = loadMore
        Try
            histView = True
            recListControl.HistoryMode = True
            SetPageContext("history")
            ShowSubPageTop()
            recPanel.BringToFront()
            recPanel.Visible = True
            lstSearch.Visible = False
            txtResult.Visible = False
            If loadMore Then
                recListControl.ShowStateText(If(isChinese, "正在加载更多...", "Loading more..."))
            Else
                recListControl.ShowStateText(If(isChinese, "正在加载历史记录...", "Loading history..."))
            End If
            Dim t As New System.Threading.Thread(AddressOf FetchHistoryWorker)
            t.Start()
        Catch ex As Exception
            WriteLog("LoadHistoryPage: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            histLoading = False
        End Try
    End Sub

    ' 后台线程拉取历史并解析（异步，不阻塞 UI/过渡回调）。
    Private Sub FetchHistoryWorker()
        histPendingErr = ""
        histPendingResults = New System.Collections.ArrayList()

        Try
            Dim url As String = "https://api.bilibili.com/x/web-interface/history/cursor?type=archive&ps=20&max=" & histMax
            If histMax <> "0" Then
                url &= "&view_at=" & histViewAt & "&business=" & histBusiness
            End If
            WriteLog("history: url=" & url)
            Dim body As String = NetWorkUtil.GetText(url, savedCookies)

            WriteLog("history: " & Microsoft.VisualBasic.Left(body, 200))

            Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
            If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
                histPendingErr = "code=" & mCode.Groups(1).Value
            Else
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

                ' 每条历史：bvid 前找 title，bvid 后找 author_name/progress/pic。
                Dim mB As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(body, """bvid"":\s*""([^""]+)""")
                Dim seen As New System.Collections.ArrayList()
                For Each m As System.Text.RegularExpressions.Match In mB
                    Dim bv As String = m.Groups(1).Value
                    If seen.Contains(bv) Then
                        Continue For
                    End If
                    seen.Add(bv)
                    If histPendingResults.Count >= 20 Then
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
                    ' 封面地址：当前条目 pic 通常在 bvid 之后；cover 在 bvid 之前。只在当前条目范围内取，避免误用上一条封面。
                    Dim pic As String = ""
                    Dim mPic As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, """pic"":\s*""([^""]+)""")
                    If mPic.Success Then
                        pic = mPic.Groups(1).Value
                    End If
                    If pic = "" Then
                        Dim mCovs As System.Text.RegularExpressions.MatchCollection = System.Text.RegularExpressions.Regex.Matches(before, """cover"":\s*""([^""]+)""")
                        If mCovs.Count > 0 Then
                            pic = mCovs(mCovs.Count - 1).Groups(1).Value
                        End If
                    End If

                    Dim ri As New RecItem(bv, pic, title, author, "")
                    ri.Danmaku = progStr
                    histPendingResults.Add(ri)
                Next
            End If

            ' Keep paging as long as this page returned records.
            histHasMore = (histPendingResults.Count > 0)
        Catch ex As Exception
            histPendingErr = ex.Message
            WriteLog("history: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
        End Try

        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowHistoryOnUi))
        Catch ex As Exception
            histLoading = False
        End Try
    End Sub

    ' UI 线程：把历史结果渲染成卡片列表（与推荐同样式）。
    Private Sub ShowHistoryOnUi()
        Try
            histLoading = False
            histView = True
            recListControl.HistoryMode = True
            If histPendingErr <> "" Then
                recListControl.ShowStateTextStatic(If(isChinese, "历史记录加载失败: " & histPendingErr, "History load failed: " & histPendingErr))
                Return
            End If
            If histItems Is Nothing Then
                histItems = New System.Collections.ArrayList()
            End If
            If Not histPendingLoadMore Then
                histItems.Clear()
            End If
            If histPendingResults Is Nothing OrElse histPendingResults.Count = 0 Then
                If histPendingLoadMore Then
                    recListControl.ShowStateTextStatic(If(isChinese, "没有更多历史记录了", "No more history"))
                Else
                    recListControl.ShowStateTextStatic(If(isChinese, "暂无历史记录", "No history yet"))
                End If
                Return
            End If
            Dim i As Integer
            For i = 0 To histPendingResults.Count - 1
                histItems.Add(histPendingResults(i))
            Next
            histPendingResults = Nothing

            Dim rowH As Integer = 94 + 12
            Dim minTitleH As Integer = fontLineH * 2 + 8
            If rowH < minTitleH Then
                rowH = minTitleH
            End If
            coverItems = histItems
            recListControl.SetData(histItems, rowH, fontLineH, fontSubH, isChinese)
            recListControl.SetShowMore(histHasMore)
            recListControl.Invalidate()
            Dim withPic As Integer = 0
            For i = 0 To histItems.Count - 1
                If Not String.IsNullOrEmpty(CType(histItems(i), RecItem).Pic) Then
                    withPic += 1
                End If
            Next
            WriteLog("history OK: page=" & histItems.Count.ToString() & " hasMore=" & histHasMore.ToString() & " max=" & histMax & " withPic=" & withPic.ToString())
            StartCoverLoad(0)
        Catch ex As Exception
            WriteLog("ShowHistoryOnUi: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
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
                Dim body As String = NetWorkUtil.GetText(viewUrl, savedCookies)

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

                Dim playResp As String = NetWorkUtil.GetText(playUrl, savedCookies)

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

            ' 按设置的播放器路由：Ostwind 用内置 DirectShow 播放器。
            If selectedPlayer = "ostwind" Then
                If StartBuiltInPlayer(sb) Then
                    txtResult.Text = sb.ToString()
                    btnPlayStream.Enabled = True
                    Return
                End If
                ' 内置失败：回退到外部播放器（若设置了 tcpmp/coreplayer 则用对应播放器）。
                SBLine(sb, "内置播放器失败，回退外部播放器")
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

    ' 内置 DirectShow 播放器：通过 LocalStreamProxy 提供本地 URL，内嵌播放。
    ' 成功返回 True（已启动播放界面），失败返回 False（调用方回退外部播放器）。
    Private Function StartBuiltInPlayer(ByVal sb As System.Text.StringBuilder) As Boolean
        Try
            If String.IsNullOrEmpty(av706VideoUrl) Then
                WriteLog("builtin: no av706VideoUrl")
                Return False
            End If

            Dim ua As String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            Dim proxy As New LocalStreamProxy(av706VideoUrl, ua, "https://www.bilibili.com/", "", False)
            Dim localUrl As String = proxy.Start()
            If String.IsNullOrEmpty(localUrl) Then
                WriteLog("builtin: proxy no URL")
                SBLine(sb, "proxy: no URL")
                Return False
            End If
            av706Proxy = proxy

            SBLine(sb, "")
            SBLine(sb, "proxy: " & localUrl)
            SBLine(sb, "内置播放器：DirectShow 内嵌播放")
            WriteLog("builtin: starting player form url=" & Microsoft.VisualBasic.Left(localUrl, 60))

            ' 全屏播放界面（模态，播放结束/返回后恢复）。
            Dim pf As New PlayerForm(isChinese)
            AddHandler pf.PlaybackEnded, AddressOf OnBuiltInPlayerEnded
            pf.Show()
            pf.BringToFront()
            WriteLog("builtin: player form shown, opening file")
            pf.OpenAndPlay(localUrl)
            WriteLog("builtin: OpenAndPlay returned")
            Return True
        Catch ex As Exception
            WriteLog("builtin player: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            Return False
        End Try
    End Function

    ' 内置播放器播放结束回调。
    Private Sub OnBuiltInPlayerEnded(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            If av706Proxy IsNot Nothing Then
                av706Proxy.Shutdown()
                av706Proxy = Nothing
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' ---- QR scan login (mirrors Android BiliClassic QRLoginFragment) ----
    ' API: generate -> { qrcode_key, url }; poll -> code: 0=success, 86090=scanned,
    ' 86101=not scanned, 86038=expired. Poll every ~1s like the Android client.

    Private Sub mnuProfile_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuProfile.Click
        StopLoginPolling()
        MenuNavigate(If(isChinese, "个人中心", "Profile"), New SimpleCallback(AddressOf ShowProfile))
    End Sub

    Private Sub mnuFavs_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuFavs.Click
        StopLoginPolling()
        MenuNavigate(If(isChinese, "我的收藏", "Favorites"), New SimpleCallback(AddressOf LoadFavoriteFolders))
    End Sub

    ' 收藏夹：先取 mid，再显示收藏夹卡片列表（recPanel/recListControl）。
    Private Sub LoadFavoriteFolders()
        SetPageContext("fav")
        ShowSubPageTop()
        favView = "folders"
        favListActive = True
        recListControl.HistoryMode = True
        recPanel.BringToFront()
        recPanel.Visible = True
        lstSearch.Visible = False
        txtResult.Visible = False
        If String.IsNullOrEmpty(savedCookies) Then
            recListControl.ShowStateTextStatic(If(isChinese, "请先登录后再查看收藏夹", "Please log in first to view favorites"))
            Return
        End If

        recListControl.ShowStateText(If(isChinese, "正在加载收藏夹...", "Loading favorites..."))
        favLoading = True

        Dim t As New System.Threading.Thread(AddressOf FetchFoldersWorker)
        t.Start()
    End Sub

    Private Sub FetchFoldersWorker()
        Dim body As String = ""
        Dim errMsg As String = ""
        Try
            If String.IsNullOrEmpty(favMid) Then
                favMid = FetchMidFromNav()
            End If
            If String.IsNullOrEmpty(favMid) Then
                errMsg = If(isChinese, "无法获取账号 UID，请重新登录", "Cannot get UID, please re-login")
            Else
                Dim url As String = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" & favMid & "&type=0"
                body = NetWorkUtil.GetTextEx(url, savedCookies, "https://space.bilibili.com/")
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
        favView = "folders"
        favListActive = True
        recListControl.HistoryMode = True
        If favLastErr <> "" Then
            recListControl.ShowStateTextStatic(favLastErr)
            Return
        End If

        Dim body As String = favLastBody
        Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
        If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
            recListControl.ShowStateTextStatic(If(isChinese, "获取收藏夹失败 (code=" & mCode.Groups(1).Value & ")", "Favorites failed (code=" & mCode.Groups(1).Value & ")"))
            Return
        End If

        If favFolderItems Is Nothing Then
            favFolderItems = New System.Collections.ArrayList()
        End If
        favFolderItems.Clear()
        Dim folderIdx As Integer = 0
        For Each fm As System.Text.RegularExpressions.Match In System.Text.RegularExpressions.Regex.Matches(body, """fid"":\s*(\d+)")
            If folderIdx >= 50 Then
                Exit For
            End If
            Dim fid As String = fm.Groups(1).Value
            ' 收藏夹对象结构: {"id":...,"fid":<fid>,"mid":...,"attr":...,"title":"...",...,"cover":"...","media_count":...}
            ' title/cover/media_count 在 fid 之后。取 fid 到本对象结束(})之间的文本，避免跨对象误匹配。
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
            Dim mCov As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, """cover"":\s*""([^""]+)""")
            Dim cover As String = ""
            If mCov.Success Then
                cover = mCov.Groups(1).Value
            End If
            Dim mCnt As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(after, """media_count"":\s*(\d+)")
            Dim cnt As String = ""
            If mCnt.Success Then
                cnt = mCnt.Groups(1).Value
            End If
            Dim ri As New RecItem(fid, cover, title, "", "")
            If cnt <> "" Then
                ri.Danmaku = "[" & cnt & If(isChinese, " 个视频", " videos") & "]"
            Else
                ri.Danmaku = ""
            End If
            favFolderItems.Add(ri)
            folderIdx += 1
        Next

        If favFolderItems.Count = 0 Then
            recListControl.ShowStateTextStatic(If(isChinese, "没有收藏夹", "No favorite folders"))
            Return
        End If

        Dim rowH As Integer = 94 + 12
        Dim minTitleH As Integer = fontLineH * 2 + 8
        If rowH < minTitleH Then
            rowH = minTitleH
        End If
        Dim withPic As Integer = 0
        Dim i2 As Integer
        For i2 = 0 To favFolderItems.Count - 1
            If Not String.IsNullOrEmpty(CType(favFolderItems(i2), RecItem).Pic) Then
                withPic += 1
            End If
        Next
        coverItems = favFolderItems
        recListControl.SetData(favFolderItems, rowH, fontLineH, fontSubH, isChinese)
        recListControl.SetShowMore(False)
        recListControl.Invalidate()
        WriteLog("fav folders OK: " & favFolderItems.Count.ToString() & " withPic=" & withPic.ToString())
        StartCoverLoad(0)
        ' 后台补全收藏夹封面（列表先显示，封面陆续出现）。
        Try
            Dim ct As New System.Threading.Thread(AddressOf ResolveFolderCoversWorker)
            ct.Start()
        Catch ex As Exception
        End Try
    End Sub

    ' 收藏夹封面补全：created/list-all 不返回 cover 字段。
    ' 逐个请求 /x/space/fav/arc?vmid=<mid>&ps=1&fid=<老式fid>（与 FetchVideosWorker 同接口），取 archives[0].pic 作为封面，
    ' 回填到对应 RecItem.Pic，再由封面管线下载图片。
    Private Sub ResolveFolderCoversWorker()

        Dim resolved As Integer = 0
        Try
            Dim items As System.Collections.ArrayList = favFolderItems
            If items Is Nothing Then
                Return
            End If
            Dim urlBase As String = "https://api.bilibili.com/x/space/fav/arc"
            Dim i As Integer
            For i = 0 To items.Count - 1
                Dim ri As RecItem = CType(items(i), RecItem)
                If ri Is Nothing OrElse String.IsNullOrEmpty(ri.Bvid) OrElse Not String.IsNullOrEmpty(ri.Pic) Then
                    Continue For
                End If
                Try
                    Dim url As String = urlBase & "?vmid=" & favMid & "&ps=1&fid=" & ri.Bvid & "&tid=0&keyword=&pn=1&order=fav_time"
                    Dim body2 As String = NetWorkUtil.GetTextEx(url, savedCookies, "https://space.bilibili.com/")
                    ' archives[0].pic 是响应里第一个 "pic"。
                    Dim mCov As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body2, """pic"":\s*""([^""]+)""")
                    If mCov.Success Then
                        ri.Pic = mCov.Groups(1).Value
                        resolved += 1
                        Try
                            Me.Invoke(New SimpleCallback(AddressOf ShowFolderCoverOnUi))
                        Catch exI As Exception
                        End Try
                    End If
                Catch ex2 As Exception
                End Try
            Next
        Catch ex As Exception
        End Try
        Try
            WriteLog("fav covers resolved: " & resolved.ToString())
        Catch exLog As Exception
        End Try
    End Sub

    ' 收藏夹封面就绪（后台线程回 UI）：刷新列表并推进封面管线。
    Private Sub ShowFolderCoverOnUi()
        Try
            If favView = "folders" AndAlso recListControl IsNot Nothing Then
                recListControl.Invalidate()
                StartCoverLoad(0)
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 进入收藏夹：作为新页面推入（有过场动画、标题用收藏夹名、可返回收藏夹列表）。
    Private Sub OpenFavoriteFolder(ByVal fid As String, ByVal name As String)
        favFolderId = fid
        favFolderName = name
        favView = "videos"
        favListActive = True
        favPage = 1
        favHasMore = True
        SetPageContext("favvideos")
        ShowSubPageTop()

        ' 先捕获收藏夹列表整屏（含标题栏，在隐藏之前）。
        Dim fromBmp As System.Drawing.Bitmap = CaptureFormBitmap()

        txtResult.Visible = False
        lstSearch.Visible = False
        recView = False
        histView = False
        recListControl.HistoryMode = False
        recPanel.BringToFront()
        recPanel.Visible = True
        LoadFolderVideosPage(False)

        Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(name, recListControl.RenderToBitmap())
        StartFullTransition(fromBmp, toBmp, -1, New SimpleCallback(AddressOf FinishOpenFavoriteFolder))
    End Sub

    ' 过渡结束：显示收藏夹视频面板。
    Private Sub FinishOpenFavoriteFolder()
        Try
            recPanel.BringToFront()
            recPanel.Visible = True
        Catch ex As Exception
        End Try
    End Sub

    ' 返回收藏夹列表（后退过渡）。
    Private Sub BackFromFolderVideos()
        Try
            WriteLog("fav back from folder")
            Dim fromBmp As System.Drawing.Bitmap = CaptureFormBitmap()
            favView = "folders"
            favListActive = True
            recListControl.HistoryMode = True
            coverItems = favFolderItems
            Dim rowH As Integer = 94 + 12
            Dim minTitleH As Integer = fontLineH * 2 + 8
            If rowH < minTitleH Then
                rowH = minTitleH
            End If
            recListControl.SetData(favFolderItems, rowH, fontLineH, fontSubH, isChinese)
            recListControl.SetShowMore(False)
            recListControl.ClearPressedHighlight()
            recListControl.Invalidate()
            Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(If(isChinese, "我的收藏", "Favorites"), recListControl.RenderToBitmap())
            recPanel.Refresh()
            StartFullTransition(fromBmp, toBmp, 1, New SimpleCallback(AddressOf FinishBackFromFolder))
        Catch ex As Exception
            FinishBackFromFolder()
        End Try
    End Sub

    ' 返回过渡结束：恢复收藏夹列表并恢复封面加载。
    Private Sub FinishBackFromFolder()
        Try
            SetPageContext("fav")
            favView = "folders"
            favListActive = True
            recListControl.HistoryMode = True
            coverItems = favFolderItems
            recPanel.BringToFront()
            recPanel.Visible = True
            ResumeCoverLoad()
        Catch ex As Exception
        End Try
    End Sub

    Private Sub LoadFolderVideosPage(ByVal loadMore As Boolean)
        If favLoading Then
            Return
        End If
        favLoading = True
        favPendingLoadMore = loadMore
        If loadMore Then
            recListControl.ShowStateText(If(isChinese, "正在加载更多...", "Loading more..."))
        Else
            recListControl.ShowStateText(If(isChinese, "正在加载视频...", "Loading videos..."))
        End If
        Dim t As New System.Threading.Thread(AddressOf FetchVideosWorker)
        t.Start()
    End Sub

    Private Sub FetchVideosWorker()
        Dim errMsg As String = ""
        Try
            Dim url As String = "https://api.bilibili.com/x/space/fav/arc?vmid=" & favMid _
                & "&ps=30&fid=" & favFolderId & "&tid=0&keyword=&pn=" & favPage.ToString() & "&order=fav_time"
            favLastBody = NetWorkUtil.GetTextEx(url, savedCookies, "https://space.bilibili.com/")
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
        favView = "videos"
        favListActive = True
        recListControl.HistoryMode = False
        If favLastErr <> "" Then
            recListControl.ShowStateTextStatic(favLastErr)
            Return
        End If

        Dim body As String = favLastBody
        Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
        If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
            recListControl.ShowStateTextStatic(If(isChinese, "获取视频失败 (code=" & mCode.Groups(1).Value & ")", "Videos failed (code=" & mCode.Groups(1).Value & ")"))
            Return
        End If

        If favVideoItems Is Nothing Then
            favVideoItems = New System.Collections.ArrayList()
        End If
        If Not favPendingLoadMore Then
            favVideoItems.Clear()
        End If

        Dim added As Integer = 0
        Dim seenBv As New System.Collections.ArrayList()
        ' fav/arc 接口不返回独立 "bvid" 字段，BV 号出现在 short_link_v2 等位置。
        ' 每个 archive 对象以 {"aid" 开头，title/pic/owner/stat 在开头，BV 在末尾的 short_link_v2。
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
            Dim pic As String = ""
            Dim mPic As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(objM.Value, """pic"":\s*""([^""]+)""")
            If mPic.Success Then
                pic = mPic.Groups(1).Value
            End If
            Dim author As String = ""
            Dim mAuth As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(objM.Value, """owner"":\s*\{""mid"":\s*\d+,\s*""name"":\s*""([^""]*)""")
            If mAuth.Success Then
                author = mAuth.Groups(1).Value
            End If
            Dim mView As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(objM.Value, """view"":\s*(\d+)")
            Dim view As String = ""
            If mView.Success Then
                view = mView.Groups(1).Value
            End If
            Dim mDan As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(objM.Value, """danmaku"":\s*(\d+)")
            Dim dan As String = ""
            If mDan.Success Then
                dan = mDan.Groups(1).Value
            End If
            Dim ri As New RecItem(bv, pic, title, author, view)
            ri.Danmaku = dan
            favVideoItems.Add(ri)
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

        If added = 0 AndAlso favVideoItems.Count = 0 Then
            recListControl.ShowStateTextStatic(If(isChinese, "这个收藏夹还没有视频", "This folder has no videos"))
            Return
        End If

        Dim rowH As Integer = 94 + 12
        Dim minTitleH As Integer = fontLineH * 2 + 8
        If rowH < minTitleH Then
            rowH = minTitleH
        End If
        coverItems = favVideoItems
        recListControl.SetData(favVideoItems, rowH, fontLineH, fontSubH, isChinese)
        recListControl.SetShowMore(favHasMore)
        recListControl.Invalidate()
        WriteLog("fav videos OK: " & favVideoItems.Count.ToString() & " hasMore=" & favHasMore.ToString())
        StartCoverLoad(0)
    End Sub

    ' 从 nav API 获取当前登录账号的 mid。
    Private Function FetchMidFromNav() As String
        Try
            Dim body As String = NetWorkUtil.GetText("https://api.bilibili.com/x/web-interface/nav", savedCookies)
            Dim mM As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """mid"":\s*(\d+)")
            If mM.Success Then
                Return mM.Groups(1).Value
            End If
        Catch ex As Exception
            WriteLog("fav mid: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
        Return ""
    End Function

    ' 菜单栏跳转带过渡：捕获当前整屏 → 目标标题页推入，过渡结束后执行目标动作。
    Private Sub MenuNavigate(ByVal titleText As String, ByVal targetAction As SimpleCallback)
        Try
            Dim fromBmp As System.Drawing.Bitmap = CaptureFormBitmap()
            Dim toBmp As System.Drawing.Bitmap = CreateToPageBitmap(titleText, Nothing)
            StartFullTransition(fromBmp, toBmp, -1, targetAction)
        Catch ex As Exception
            If targetAction IsNot Nothing Then
                targetAction()
            End If
        End Try
    End Sub

    Private Sub ShowProfile()
        Try
            ShowResultView()
            SetPageContext("profile")
            ShowSubPageTop()


            ' 显示个人中心自绘面板。
            txtResult.Visible = False
            If profilePanel IsNot Nothing Then
                ' 首次进入时 profilePanel 尚未布局（第一次 LayoutControls 时它还是 Nothing），按内容区手动定位。
                If profilePanel.Width <= 0 Then
                    Dim w As Integer = Me.ClientSize.Width
                    Dim h As Integer = Me.ClientSize.Height
                    Dim titleH As Integer = TITLE_BAR_H
                    If w > 10 AndAlso h > 10 Then
                        profilePanel.Left = 4
                        profilePanel.Width = w - 8
                        profilePanel.Top = titleH + 6
                        profilePanel.Height = h - profilePanel.Top - 10
                    End If
                End If
                profilePanel.Visible = True
                profilePanel.BringToFront()
                profilePanel.Refresh()
            End If

            If String.IsNullOrEmpty(savedCookies) Then
                ' 未登录：显示扫码登录界面。
                If profileControl IsNot Nothing Then
                    profileControl.ShowNotLoggedIn()
                End If
                StartProfileQrLogin()
                Return
            End If

            ' 已登录：加载个人信息 + 头像。
            If profileControl IsNot Nothing Then
                profileControl.ShowStateText(If(isChinese, "正在加载个人中心...", "Loading profile..."))
            End If
            Dim t As New System.Threading.Thread(AddressOf FetchProfileWorker)
            t.Start()
        Catch ex As Exception
            WriteLog("ShowProfile: " & ex.GetType().FullName & " | " & ex.Message)
        End Try
    End Sub

    ' 个人中心扫码登录：复用 LoginWorker 获取二维码，二维码交给 profileControl。
    Private Sub StartProfileQrLogin()
        Try
            StopLoginPolling()
            loginQrContent = ""
            loginErrMsg = ""
            Dim t As New System.Threading.Thread(AddressOf LoginWorker)
            t.Start()
        Catch ex As Exception
        End Try
    End Sub

    ' 后台线程：取 nav（uname/mid/money/vip/face）+ 下载头像 → UI 设置 ProfileControl。
    Private Sub FetchProfileWorker()
        Dim name As String = ""
        Dim uid As String = ""
        Dim coins As String = "0"
        Dim isVip As Boolean = False
        Dim faceUrl As String = ""
        Dim sign As String = ""
        Dim level As String = ""
        Dim errMsg As String = ""
        Dim avatar As System.Drawing.Bitmap = Nothing
        Try
            Dim navUrl As String = "https://api.bilibili.com/x/web-interface/nav"
            Dim body As String = NetWorkUtil.GetText(navUrl, savedCookies)

            WriteLog("mine: " & Microsoft.VisualBasic.Left(body, 200))

            Dim mCode As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """code"":\s*(-?\d+)")
            If mCode.Success AndAlso mCode.Groups(1).Value <> "0" Then
                errMsg = If(isChinese, "获取个人信息失败 (code=" & mCode.Groups(1).Value & ")，请重新登录", "Profile failed (code=" & mCode.Groups(1).Value & "), please re-login")
            Else
                Dim mU As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """uname"":\s*""([^""]*)""")
                If mU.Success Then
                    name = UnescapeJson(mU.Groups(1).Value)
                End If
                Dim mM As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """mid"":\s*(\d+)")
                If mM.Success Then
                    uid = mM.Groups(1).Value
                End If
                Dim mCo As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """money"":\s*(\d+)")
                If mCo.Success Then
                    coins = mCo.Groups(1).Value
                End If
                Dim mFace As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """face"":\s*""([^""]*)""")
                If mFace.Success Then
                    faceUrl = mFace.Groups(1).Value
                End If
                Dim mSign As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """sign"":\s*""((?:[^""\\]|\\.)*)""")
                If mSign.Success Then
                    sign = UnescapeJson(mSign.Groups(1).Value)
                End If
                Dim mVipType As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """type"":\s*(\d+)")
                Dim mVipStatus As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """status"":\s*(\d+)")
                If mVipType.Success AndAlso mVipStatus.Success Then
                    If CLng(mVipType.Groups(1).Value) > 0 AndAlso CLng(mVipStatus.Groups(1).Value) = 1 Then
                        isVip = True
                    End If
                End If
                ' 等级：level_info.current_level。
                Dim mLevel As System.Text.RegularExpressions.Match = System.Text.RegularExpressions.Regex.Match(body, """current_level"":\s*(\d+)")
                If mLevel.Success Then
                    level = "Lv" & mLevel.Groups(1).Value
                End If

                ' 下载头像（缩到 96px）。
                If faceUrl <> "" Then
                    ' 部分接口返回协议相对地址（//i0.hdslb.com/...），补 https 前缀。
                    If faceUrl.StartsWith("//") Then
                        faceUrl = "https:" & faceUrl
                    End If
                    avatar = DownloadImageScaled(faceUrl, 96, 96)
                    WriteLog("mine avatar: face=" & faceUrl & " result=" & If(avatar Is Nothing, "NULL", avatar.Width.ToString() & "x" & avatar.Height.ToString()))
                Else
                    WriteLog("mine avatar: face EMPTY")
                End If
            End If
        Catch ex As Exception
            WriteLog("mine: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            errMsg = If(isChinese, "个人中心加载失败: " & ex.Message, "Profile load failed: " & ex.Message)
        End Try

        ' 缓存结果供 UI 线程读取（须在 Invoke 之前，Invoke 是异步的）。
        profileFetchName = name
        profileFetchUid = uid
        profileFetchCoins = coins
        profileFetchIsVip = isVip
        profileFetchLevel = level
        profileFetchSign = sign
        profileFetchErr = errMsg
        profileAvatar = avatar

        Try
            Me.Invoke(New SimpleCallback(AddressOf ShowProfileOnUi))
        Catch exI As Exception
        End Try
    End Sub

    ' UI 线程：把个人中心数据交给 ProfileControl。
    Private Sub ShowProfileOnUi()
        If profileControl Is Nothing Then
            Return
        End If
        If profileFetchErr <> "" Then
            profileControl.ShowStateTextStatic(profileFetchErr)
            Return
        End If
        Dim vipTag As String = ""
        If profileFetchIsVip Then
            vipTag = If(isChinese, "大会员", "Big Member")
        End If
        profileControl.SetUserData(profileAvatar, profileFetchName, profileFetchUid, profileFetchCoins, profileFetchLevel, vipTag, profileFetchSign, isChinese)
    End Sub

    ' 退出登录：清 cookie，回到未登录视图。
    Private Sub ProfileLogout_Click(ByVal sender As Object, ByVal e As System.EventArgs)
        Try
            StopLoginPolling()
        Catch ex As Exception
        End Try
        Try
            savedCookies = ""
            Dim cp As String = GetCookiePath()
            If System.IO.File.Exists(cp) Then
                System.IO.File.Delete(cp)
            End If
        Catch ex As Exception
        End Try
        Try
            If profileAvatar IsNot Nothing Then
                profileAvatar.Dispose()
                profileAvatar = Nothing
            End If
        Catch ex As Exception
        End Try
        If profileControl IsNot Nothing Then
            profileControl.ClearAvatar()
            profileControl.ShowNotLoggedIn()
        End If
        StartProfileQrLogin()
    End Sub

    Private Sub mnuLogin_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles mnuLogin.Click
        If loginPolling Then
            StopLoginPolling()
            If IsProfileView() Then
                If profileControl IsNot Nothing Then
                    profileControl.ShowNotLoggedIn()
                End If
            Else
                ShowResultView()
                txtResult.Text = If(isChinese, "已取消扫码登录", "QR login cancelled")
            End If
            Return
        End If
        MenuNavigate(If(isChinese, "个人中心", "Profile"), New SimpleCallback(AddressOf ShowProfile))
    End Sub

    Private Sub StopLoginPolling()
        loginPolling = False
        If loginTimer IsNot Nothing Then
            loginTimer.Enabled = False
        End If
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

    ' 后台线程：获取登录二维码（避免阻塞 UI）。
    Private Sub LoginWorker()

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
                loginErrMsg = If(isChinese, "二维码获取失败（无 qrcode_key）", "QR fetch failed (no qrcode_key)")
            Else
                loginQrKey = mKey.Groups(1).Value
                loginQrContent = mUrl.Groups(1).Value
                loginQrContent = loginQrContent.Replace("\u0026", "&")
            End If
        Catch ex As Exception
            WriteLog("login: " & ex.GetType().FullName & " | " & ex.Message & " | " & ex.StackTrace)
            loginErrMsg = If(isChinese, "扫码登录失败: " & ex.Message, "QR login failed: " & ex.Message)
        End Try
        Try
            Me.Invoke(New SimpleCallback(AddressOf FinishLoginAsync))
        Catch exI As Exception
            WriteLog("login invoke: " & exI.GetType().FullName & " | " & exI.Message)
        End Try
    End Sub

    ' UI 线程：生成二维码并开始轮询。
    Private Sub FinishLoginAsync()
        If loginErrMsg <> "" Then
            ShowLoginStatus(loginErrMsg)
            Return
        End If
        Try
            ' 2. Generate the QR bitmap locally (Nayuki algorithm port, byte mode)
            Dim bmp As System.Drawing.Bitmap = QrCode.MakeQrBitmap(loginQrContent, QrEcc.ECC_MEDIUM, 3, 2)
            If profileControl IsNot Nothing Then
                profileControl.SetQrImage(bmp)
            End If
            bmp = Nothing

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
                ShowLoginStatus("请用 B 站手机 App 扫码登录（180 秒有效）")
            Else
                ShowLoginStatus("Scan with the Bilibili app (valid 180s)")
            End If
        Catch ex As Exception
            WriteLog("login qr: " & ex.GetType().FullName & " | " & ex.Message)
            If isChinese Then
                ShowLoginStatus("扫码登录失败: " & ex.Message)
            Else
                ShowLoginStatus("QR login failed: " & ex.Message)
            End If
        End Try
    End Sub

    Private Function IsProfileView() As Boolean
        Try
            Return (profilePanel IsNot Nothing AndAlso profilePanel.Visible)
        Catch ex As Exception
            Return False
        End Try
    End Function

    ' 登录状态消息：个人中心可见时显示到 ProfileControl，否则显示到 txtResult。
    ' 未登录（扫码）视图下只更新二维码下方提示，不切换到小电视动画，避免盖掉二维码。
    Private Sub ShowLoginStatus(ByVal msg As String)
        Try
            If IsProfileView() Then
                If profileControl IsNot Nothing Then
                    If profileControl.IsLoginMode Then
                        profileControl.SetLoginHint(msg)
                    Else
                        profileControl.ShowStateText(msg)
                    End If
                End If
            Else
                txtResult.Text = msg
            End If
        Catch ex As Exception
        End Try
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
                ShowLoginStatus("二维码已过期，请重新获取")
            Else
                ShowLoginStatus("QR expired, please refresh")
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
                    SaveLoginCookies(body, resp)
                    UpdateLoginMenuText()
                    If isChinese Then
                        ShowLoginStatus("登录成功！")
                    Else
                        ShowLoginStatus("Login OK!")
                    End If
                    ' 登录成功：若在个人中心，刷新为已登录视图。
                    If IsProfileView() Then
                        If profileControl IsNot Nothing Then
                            profileControl.ShowStateText(If(isChinese, "登录成功，正在加载...", "Logged in, loading..."))
                        End If
                        Dim tf As New System.Threading.Thread(AddressOf FetchProfileWorker)
                        tf.Start()
                    End If
                Case 86090 ' Scanned, waiting for confirmation on phone
                    If isChinese Then
                        ShowLoginStatus("已扫码，请在手机上确认登录...")
                    Else
                        ShowLoginStatus("Scanned, confirm on your phone...")
                    End If
                Case 86038 ' Expired
                    loginPolling = False
                    loginTimer.Enabled = False
                    UpdateLoginMenuText()
                    If isChinese Then
                        ShowLoginStatus("二维码已过期，请重新获取")
                    Else
                        ShowLoginStatus("QR expired, please refresh")
                    End If
                Case Else ' 86101 = not scanned yet, keep waiting
                    If isChinese Then
                        ShowLoginStatus("等待扫码...")
                    Else
                        ShowLoginStatus("Waiting for scan...")
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
            Dim body As String = NetWorkUtil.GetText(navUrl, savedCookies)

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

    Private Sub btnPlayOffline_Click(ByVal sender As Object, ByVal e As System.EventArgs) Handles btnPlayOffline.Click
        ShowResultView()
        btnPlayOffline.Enabled = False


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
            ' 按用户选择的播放器优先精确查找。
            Dim prefCandidates As String() = Nothing
            If selectedPlayer = "tcpmp" Then
                prefCandidates = New String() {"TCPMP\tcpmp.exe", "tcpmp\tcpmp.exe", "TCPMP\TCPMP.exe", "tcpmp.exe"}
            ElseIf selectedPlayer = "coreplayer" Then
                prefCandidates = New String() {"CorePlayer\CorePlayer.exe", "CorePlayer\tcpmp.exe", "CorePlayer.exe"}
            End If
            If prefCandidates IsNot Nothing Then
                Dim rootDirs0 As String() = New String() {"\Program Files", "\Storage Card\Program Files", "\Storage Card", "\Windows"}
                For Each d As String In rootDirs0
                    If String.IsNullOrEmpty(d) OrElse Not System.IO.Directory.Exists(d) Then
                        Continue For
                    End If
                    For Each c As String In prefCandidates
                        Try
                            Dim p As String = System.IO.Path.Combine(d, c)
                            If System.IO.File.Exists(p) Then
                                WriteLog("findplayer: pref hit " & p)
                                Return p
                            End If
                        Catch exF As Exception
                        End Try
                    Next c
                Next d
            End If

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