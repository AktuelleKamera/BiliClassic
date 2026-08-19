' LoadingOverlay.vb
' 全屏横屏加载层：仿安卓 B 站播放器 preloading 界面。
'   - 背景色 #bababa
'   - 居中「小电视」帧动画（嵌入资源 bili_anim_tv_chan_1/3/5/7/9.png，100ms/帧）
'   - 左上角返回按钮（点击触发 ReturnPressed 事件）
'   - 底部左对齐状态文字（两行：第一行获取播放地址，第二行正在加载视频）
' 用法：
'   overlay.ShowOverlay("正在获取播放地址...")
'   overlay.SetStatus(text)
'   overlay.HideOverlay()

Public Class LoadingOverlay
    Inherits System.Windows.Forms.Panel

    Private Const FRAME_MS As Integer = 100
    Private Shared ReadOnly BACK_COLOR As System.Drawing.Color = System.Drawing.Color.FromArgb(CType(186, Byte), CType(186, Byte), CType(186, Byte))
    Private Shared ReadOnly TEXT_COLOR As System.Drawing.Color = System.Drawing.Color.FromArgb(CType(97, Byte), CType(97, Byte), CType(97, Byte))
    Private Shared ReadOnly TV_SIZE As Integer = 120

    Private mFrames As AlphaMobileControls.AlphaImage() = Nothing
    ' 设备端 CreateImageFromBuffer 只借用缓冲指针（BufferDisposalFlagNone），须保引用防 GC 回收。
    Private mFrameStreams As System.IO.MemoryStream() = Nothing
    Private mFrameIndex As Integer = 0
    Private mTimer As System.Windows.Forms.Timer = Nothing
    Private mBtnBack As System.Windows.Forms.PictureBox = Nothing
    Private mLblStatus As System.Windows.Forms.Label = Nothing
    Private mVisible As Boolean = False
    ' 小电视绘制区域（LayoutForSize 计算，OnPaint 使用）。
    Private mTvRect As System.Drawing.Rectangle = New System.Drawing.Rectangle(0, 0, 0, 0)

    Public Event ReturnPressed As EventHandler

    Public Sub New()
        Me.Dock = System.Windows.Forms.DockStyle.Fill
        Me.BackColor = BACK_COLOR
        Me.BringToFront()
        BuildControls()
    End Sub

    Private Sub BuildControls()
        mBtnBack = New System.Windows.Forms.PictureBox()
        mBtnBack.Size = New System.Drawing.Size(32, 32)
        mBtnBack.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage
        mBtnBack.BackColor = BACK_COLOR
        Try
            Dim iconBmp As System.Drawing.Bitmap = CreateBackIconBitmap()
            If iconBmp IsNot Nothing Then
                mBtnBack.Image = iconBmp
            End If
        Catch ex As Exception
        End Try
        AddHandler mBtnBack.Click, AddressOf OnBackClick

        mLblStatus = New System.Windows.Forms.Label()
        mLblStatus.BackColor = BACK_COLOR
        mLblStatus.ForeColor = TEXT_COLOR

        Me.Controls.Add(mLblStatus)
        Me.Controls.Add(mBtnBack)
    End Sub

    ' 生成返回箭头图标：ic_back.png（透明）用 AlphaImage 平铺到灰底生成不透明位图（CF 透明 PNG 直接渲染不可靠）。
    Private Function CreateBackIconBitmap() As System.Drawing.Bitmap
        Dim bmp As System.Drawing.Bitmap = Nothing
        Try
            bmp = New System.Drawing.Bitmap(32, 32)
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            g.Clear(BACK_COLOR)
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
            ' 回退：画深灰左箭头（‹）。
            Dim pen As New System.Drawing.Pen(System.Drawing.Color.FromArgb(&H61, &H61, &H61), 3)
            Dim pts As System.Drawing.Point() = New System.Drawing.Point() {New System.Drawing.Point(12, 7), New System.Drawing.Point(5, 16), New System.Drawing.Point(12, 25)}
            g.DrawLines(pen, pts)
            pen.Dispose()
            g.Dispose()
        Catch ex As Exception
            bmp = Nothing
        End Try
        Return bmp
    End Function

    Private Sub OnBackClick(ByVal sender As Object, ByVal e As System.EventArgs)
        RaiseEvent ReturnPressed(Me, e)
    End Sub

    ' 加载小电视帧（从嵌入资源）。失败时静默返回。
    ' 透明帧用 AlphaImage（设备端 IImage 逐像素 alpha，桌面端 GDI+ 回退），保证 WM 上透明背景正确。
    Private Sub LoadFrames()
        If mFrames IsNot Nothing Then
            Return
        End If
        Dim names As String() = New String() {"BiliClassic_WM.bili_anim_tv_chan_1.png", "BiliClassic_WM.bili_anim_tv_chan_3.png", "BiliClassic_WM.bili_anim_tv_chan_5.png", "BiliClassic_WM.bili_anim_tv_chan_7.png", "BiliClassic_WM.bili_anim_tv_chan_9.png"}
        Dim tmp(4) As AlphaMobileControls.AlphaImage
        Dim tmpStreams(4) As System.IO.MemoryStream
        Dim loaded As Integer = 0
        For i As Integer = 0 To names.Length - 1
            Try
                Dim s As System.IO.Stream = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream(names(i))
                If s IsNot Nothing Then
                    Dim ms As New System.IO.MemoryStream()
                    Dim buf(4095) As Byte
                    Dim n As Integer = s.Read(buf, 0, buf.Length)
                    While n > 0
                        ms.Write(buf, 0, n)
                        n = s.Read(buf, 0, buf.Length)
                    End While
                    s.Close()
                    ms.Position = 0
                    tmpStreams(i) = ms
                    tmp(i) = AlphaMobileControls.AlphaImage.CreateFromStream(ms)
                    loaded += 1
                End If
            Catch ex As Exception
                tmp(i) = Nothing
            End Try
        Next
        If loaded = 0 Then
            Return
        End If
        mFrames = tmp
        mFrameStreams = tmpStreams
    End Sub

    ' 显示加载层并开始动画。
    Public Sub ShowOverlay(ByVal status As String)
        Me.BringToFront()
        Me.Visible = True
        mVisible = True
        LayoutForSize()
        LoadFrames()
        SetStatus(status)
        StartAnimation()
        Me.Refresh()
    End Sub

    Public Sub SetStatus(ByVal text As String)
        If mLblStatus IsNot Nothing Then
            mLblStatus.Text = text
            LayoutForSize()
        End If
    End Sub

    Public Sub HideOverlay()
        mVisible = False
        StopAnimation()
        Me.Visible = False
    End Sub

    Public ReadOnly Property IsShowing() As Boolean
        Get
            Return mVisible
        End Get
    End Property

    ' 动画：Timer 100ms 切帧。
    Private Sub StartAnimation()
        If mTimer Is Nothing Then
            mTimer = New System.Windows.Forms.Timer()
            mTimer.Interval = FRAME_MS
            AddHandler mTimer.Tick, AddressOf OnFrameTick
        End If
        mFrameIndex = 0
        mTimer.Enabled = True
    End Sub

    Private Sub StopAnimation()
        If mTimer IsNot Nothing Then
            mTimer.Enabled = False
        End If
    End Sub

    Private Sub OnFrameTick(ByVal sender As Object, ByVal e As System.EventArgs)
        If Not mVisible Then
            Return
        End If
        If mFrames Is Nothing Then
            Return
        End If
        mFrameIndex = (mFrameIndex + 1) Mod mFrames.Length
        Invalidate()
    End Sub

    ' 每次大小变化时重新布局（全屏 Fill + 居中电视 + 左上返回 + 底部文字）。
    Public Sub LayoutForSize()
        Dim w As Integer = Me.ClientSize.Width
        Dim h As Integer = Me.ClientSize.Height
        If w <= 0 OrElse h <= 0 Then
            Return
        End If

        mTvRect = New System.Drawing.Rectangle((w - TV_SIZE) \ 2, (h - TV_SIZE) \ 2, TV_SIZE, TV_SIZE)

        mBtnBack.Left = 8
        mBtnBack.Top = 8

        mLblStatus.Left = 10
        mLblStatus.Top = h - 70
        mLblStatus.Width = w - 20
        mLblStatus.Height = 60
    End Sub

    Protected Overrides Sub OnResize(ByVal e As System.EventArgs)
        MyBase.OnResize(e)
        LayoutForSize()
    End Sub

    Protected Overrides Sub OnPaintBackground(ByVal e As System.Windows.Forms.PaintEventArgs)
        e.Graphics.Clear(BACK_COLOR)
    End Sub

    ' 自绘当前小电视帧（AlphaImage：设备端 IImage 逐像素 alpha 缩放，桌面端 GDI+）。
    Protected Overrides Sub OnPaint(ByVal e As System.Windows.Forms.PaintEventArgs)
        MyBase.OnPaint(e)
        If mFrames Is Nothing Then
            Return
        End If
        If mTvRect.Width <= 0 OrElse mTvRect.Height <= 0 Then
            LayoutForSize()
        End If
        If mFrameIndex >= 0 AndAlso mFrameIndex < mFrames.Length Then
            If mFrames(mFrameIndex) IsNot Nothing Then
                Try
                    mFrames(mFrameIndex).DrawStretched(e.Graphics, mTvRect)
                Catch ex As Exception
                End Try
            End If
        End If
    End Sub

End Class