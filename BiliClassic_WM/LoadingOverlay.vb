' LoadingOverlay.vb
' 全屏横屏加载层：仿安卓 B 站播放器 preloading 界面。
'   - 背景色 #bababa
'   - 居中「小电视」帧动画（嵌入资源 tv_chan_1/3/5/7/9.png，100ms/帧）
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

    Private mFrames As System.Drawing.Image() = Nothing
    Private mFrameIndex As Integer = 0
    Private mTimer As System.Windows.Forms.Timer = Nothing
    Private mPic As System.Windows.Forms.PictureBox = Nothing
    Private mBtnBack As System.Windows.Forms.Button = Nothing
    Private mLblStatus As System.Windows.Forms.Label = Nothing
    Private mVisible As Boolean = False

    Public Event ReturnPressed As EventHandler

    Public Sub New()
        Me.Dock = System.Windows.Forms.DockStyle.Fill
        Me.BackColor = BACK_COLOR
        Me.BringToFront()
        BuildControls()
    End Sub

    Private Sub BuildControls()
        mPic = New System.Windows.Forms.PictureBox()
        mPic.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage
        mPic.BackColor = BACK_COLOR

        mBtnBack = New System.Windows.Forms.Button()
        mBtnBack.Text = "< 返回"
        mBtnBack.Size = New System.Drawing.Size(72, 32)
        AddHandler mBtnBack.Click, AddressOf OnBackClick

        mLblStatus = New System.Windows.Forms.Label()
        mLblStatus.BackColor = BACK_COLOR
        mLblStatus.ForeColor = TEXT_COLOR

        Me.Controls.Add(mLblStatus)
        Me.Controls.Add(mBtnBack)
        Me.Controls.Add(mPic)
    End Sub

    Private Sub OnBackClick(ByVal sender As Object, ByVal e As System.EventArgs)
        RaiseEvent ReturnPressed(Me, e)
    End Sub

    ' 加载小电视帧（从嵌入资源）。失败时静默返回。
    Private Sub LoadFrames()
        If mFrames IsNot Nothing Then
            Return
        End If
        Dim names As String() = New String() {"BiliClassic_WM.tv_chan_1.png", "BiliClassic_WM.tv_chan_3.png", "BiliClassic_WM.tv_chan_5.png", "BiliClassic_WM.tv_chan_7.png", "BiliClassic_WM.tv_chan_9.png"}
        Dim tmp(4) As System.Drawing.Image
        Dim loaded As Integer = 0
        For i As Integer = 0 To names.Length - 1
            Try
                Dim s As System.IO.Stream = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream(names(i))
                If s IsNot Nothing Then
                    tmp(i) = New System.Drawing.Bitmap(s)
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
        If mFrames(mFrameIndex) IsNot Nothing Then
            mPic.Image = mFrames(mFrameIndex)
        End If
        mFrameIndex = (mFrameIndex + 1) Mod mFrames.Length
    End Sub

    ' 每次大小变化时重新布局（全屏 Fill + 居中电视 + 左上返回 + 底部文字）。
    Public Sub LayoutForSize()
        Dim w As Integer = Me.ClientSize.Width
        Dim h As Integer = Me.ClientSize.Height
        If w <= 0 OrElse h <= 0 Then
            Return
        End If

        mPic.Size = New System.Drawing.Size(TV_SIZE, TV_SIZE)
        mPic.Left = (w - TV_SIZE) \ 2
        mPic.Top = (h - TV_SIZE) \ 2

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

End Class