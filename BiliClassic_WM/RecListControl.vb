' 自绘虚拟列表控件（.NET CF 2.0 兼容）。
' 只绘制可见行（双缓冲），滚动由内部偏移量驱动，适合 WM 流畅触摸滚动。
' 行布局：左侧封面缩略图（16:9），封面底部半透明灰条放播放量/弹幕数，右侧标题占整行高。
Public Class RecListControl
    Inherits System.Windows.Forms.Control

    Public Delegate Sub RowTapHandler(ByVal index As Integer)
    Public OnRowTap As RowTapHandler = Nothing
    Public OnMoreTap As System.EventHandler = Nothing
    ' 滚动变化通知（供外部清理不可见行的封面位图，节省 WM 内存）。
    Public OnScrollChanged As System.EventHandler = Nothing

    Private mItems As System.Collections.ArrayList = Nothing
    Private mRowH As Integer = 56
    Private mCoverW As Integer = 168
    Private mCoverH As Integer = 94
    Private mTitleH As Integer = 40
    Private mFontTitle As System.Drawing.Font = Nothing
    Private mFontSub As System.Drawing.Font = Nothing
    Private mFontInfo As System.Drawing.Font = Nothing
    Private mLineH As Integer = 18
    Private mSubH As Integer = 14
    Private mIsChinese As Boolean = False

    ' 资源图（嵌入 drawable/）。
    Private mDefaultBg As System.Drawing.Bitmap = Nothing
    Private mIconViews As System.Drawing.Bitmap = Nothing
    Private mIconDanmakus As System.Drawing.Bitmap = Nothing

    ' 状态动画：小电视抖动帧（AlphaImage，透明，设备端 IImage 逐像素 alpha）。
    Private mStateFrames As AlphaMobileControls.AlphaImage() = Nothing
    Private mStateFrameStreams As System.IO.MemoryStream() = Nothing
    Private mStateFrameIndex As Integer = 0
    Private mStateTimer As System.Windows.Forms.Timer = Nothing

    ' 滚动状态（内容向上偏移量，0 = 顶部）。
    Private mScrollTop As Integer = 0
    Private mMaxScroll As Integer = 0

    ' 触摸拖动 / 惯性。
    Private mDownY As Integer = 0
    Private mDownScroll As Integer = 0
    Private mDragging As Boolean = False
    Private mLastY As Integer = 0
    Private mLastTick As Integer = 0
    Private mVelY As Single = 0
    Private mFlingTimer As System.Windows.Forms.Timer = Nothing
    Private mFlingVel As Single = 0

    ' 桌面运行时检测（鼠标与触摸行为不同：更大拖动阈值、无惯性、支持滚轮）。
    Private Function IsDesktopRuntime() As Boolean
        Try
            Return (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)
        Catch ex As Exception
            Return False
        End Try
    End Function

    ' 列表字体：桌面优先微软雅黑（无则回退 Tahoma），WM 用 Tahoma。
    Private Function GetUiFont(ByVal size As Single, ByVal bold As Boolean) As System.Drawing.Font
        Try
            Dim style As System.Drawing.FontStyle = If(bold, System.Drawing.FontStyle.Bold, System.Drawing.FontStyle.Regular)
            If IsDesktopRuntime() Then
                Try
                    Dim fYH As System.Drawing.Font = New System.Drawing.Font("Microsoft YaHei", size, style)
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

    ' 按压高亮。
    Private mPressedRow As Integer = -1

    ' 键盘/方向键焦点行（-1=无）。用粉色左边条+描边区别于按压高亮。
    Private mFocusRow As Integer = -1

    ' 双缓冲。
    Private mBackBuf As System.Drawing.Bitmap = Nothing
    Private mBackG As System.Drawing.Graphics = Nothing
    Private mBufW As Integer = 0
    Private mBufH As Integer = 0

    ' 缓存画刷（每帧创建/销毁 SolidBrush 在 WM 上开销大、加剧闪烁/卡顿）。
    Private mBrushWhite As System.Drawing.SolidBrush = Nothing
    Private mBrushGray As System.Drawing.SolidBrush = Nothing
    Private mBrushPressed As System.Drawing.SolidBrush = Nothing
    Private mBrushTitle As System.Drawing.SolidBrush = Nothing
    Private mBrushSub As System.Drawing.SolidBrush = Nothing
    Private mBrushMore As System.Drawing.SolidBrush = Nothing
    Private mBrushPh As System.Drawing.SolidBrush = Nothing
    Private mBrushInfoBar As System.Drawing.SolidBrush = Nothing
    Private mBrushInfoText As System.Drawing.SolidBrush = Nothing
    Private mBrushFocus As System.Drawing.SolidBrush = Nothing
    Private mPenFocus As System.Drawing.Pen = Nothing
    Private mStringFormat As System.Drawing.StringFormat = Nothing

    ' 状态文本（加载中/错误/空）。
    Private mStateText As String = ""
    ' True 时状态只画文字（不画小电视），用于搜索提示/无结果等场景。
    Private mStateTextOnly As Boolean = False
    Private mShowMore As Boolean = False
    Private mMoreH As Integer = 40

    ' 历史记录行样式（True 时行布局：封面+进度条+标题+作者；False 为推荐样式：封面+播放量/弹幕+标题）。
    Public HistoryMode As Boolean = False

    ' 纯文字行模式（True 时不画封面/信息条，仅标题+副文字，用于搜索历史等紧凑列表）。
    Private mNoCover As Boolean = False
    Public Property NoCoverMode() As Boolean
        Get
            Return mNoCover
        End Get
        Set(ByVal value As Boolean)
            mNoCover = value
            Invalidate()
        End Set
    End Property

    Public Sub New()
        MyBase.New()
        mFontTitle = GetUiFont(9.0!, True)
        mFontSub = GetUiFont(9.0!, False)
        mFontInfo = GetUiFont(8.0!, True)
        mFlingTimer = New System.Windows.Forms.Timer()
        AddHandler mFlingTimer.Tick, AddressOf OnFlingTick
        mFlingTimer.Interval = 16
        mFlingTimer.Enabled = False
        Me.BackColor = System.Drawing.Color.White
        mBrushWhite = New System.Drawing.SolidBrush(System.Drawing.Color.White)
        mBrushGray = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HF2, &HF2, &HF2))
        mBrushPressed = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HF5, &HDA, &HE8))
        mBrushTitle = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H22, &H22, &H22))
        mBrushSub = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H88, &H88, &H88))
        mBrushMore = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H66, &H66, &H66))
        mBrushPh = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HE8, &HE8, &HE8))
        ' 底部半透明灰条：约 55% 不透明黑，文字用白。
        mBrushInfoBar = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H8C000000))
        mBrushInfoText = New System.Drawing.SolidBrush(System.Drawing.Color.White)
        mBrushFocus = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HD8, &H6D, &HA5))
        mPenFocus = New System.Drawing.Pen(System.Drawing.Color.FromArgb(&HD8, &H6D, &HA5), 2)
        mStringFormat = New System.Drawing.StringFormat()
        mStateTimer = New System.Windows.Forms.Timer()
        AddHandler mStateTimer.Tick, AddressOf OnStateTick
        mStateTimer.Interval = 100
        mStateTimer.Enabled = False
        LoadResources()
    End Sub

    ' 从嵌入资源加载图标（失败静默，界面仍可用）。
    Private Sub LoadResources()
        Try
            Dim asm As System.Reflection.Assembly = System.Reflection.Assembly.GetExecutingAssembly()
            Dim s1 As System.IO.Stream = asm.GetManifestResourceStream("BiliClassic_WM.defaultbg.png")
            If s1 IsNot Nothing Then
                mDefaultBg = New System.Drawing.Bitmap(s1)
            End If
            Dim s2 As System.IO.Stream = asm.GetManifestResourceStream("BiliClassic_WM.ic_views.png")
            If s2 IsNot Nothing Then
                mIconViews = New System.Drawing.Bitmap(s2)
            End If
            Dim s3 As System.IO.Stream = asm.GetManifestResourceStream("BiliClassic_WM.ic_danmakus.png")
            If s3 IsNot Nothing Then
                mIconDanmakus = New System.Drawing.Bitmap(s3)
            End If
        Catch ex As Exception
        End Try

        ' 状态动画帧（bili_anim_tv_chan_1/3/5/7/9，透明，AlphaImage 保证 WM 逐像素 alpha）。
        Try
            Dim names As String() = New String() {"BiliClassic_WM.bili_anim_tv_chan_1.png", "BiliClassic_WM.bili_anim_tv_chan_3.png", "BiliClassic_WM.bili_anim_tv_chan_5.png", "BiliClassic_WM.bili_anim_tv_chan_7.png", "BiliClassic_WM.bili_anim_tv_chan_9.png"}
            Dim tmp(4) As AlphaMobileControls.AlphaImage
            Dim tmpStreams(4) As System.IO.MemoryStream
            Dim loaded As Integer = 0
            Dim asm2 As System.Reflection.Assembly = System.Reflection.Assembly.GetExecutingAssembly()
            Dim i As Integer
            For i = 0 To names.Length - 1
                Try
                    Dim s As System.IO.Stream = asm2.GetManifestResourceStream(names(i))
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
            If loaded > 0 Then
                mStateFrames = tmp
                mStateFrameStreams = tmpStreams
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 设置封面缩略图尺寸（小屏 QVGA 时缩小，行高须与封面高匹配）。
    Public Sub SetCoverSize(ByVal coverW As Integer, ByVal coverH As Integer)
        If coverW > 0 Then
            mCoverW = coverW
        End If
        If coverH > 0 Then
            mCoverH = coverH
        End If
        Invalidate()
    End Sub

    ' 配置数据源与行高。
    Public Sub SetData(ByVal items As System.Collections.ArrayList, ByVal rowH As Integer, ByVal lineH As Integer, ByVal subH As Integer, ByVal isChinese As Boolean)
        mItems = items
        mRowH = rowH
        mLineH = lineH
        mSubH = subH
        mIsChinese = isChinese
        mStateText = ""
        mShowMore = (items IsNot Nothing AndAlso items.Count > 0)
        mPressedRow = -1
        mFocusRow = -1
        mNoCover = False
        StopStateAnimation()
        UpdateMaxScroll()
        Invalidate()
    End Sub

    ' 显示状态文本（加载中/错误/空），不绘制列表。加载类提示显示小电视抖动动画。
    Public Sub ShowStateText(ByVal text As String)
        ShowStateText(text, True)
    End Sub

    ' 显示状态文本但小电视静止（失败/空状态：请求失败时动画停下）。
    Public Sub ShowStateTextStatic(ByVal text As String)
        ShowStateText(text, False)
    End Sub

    Private Sub ShowStateText(ByVal text As String, ByVal animate As Boolean)
        mStateText = text
        mShowMore = False
        mItems = Nothing
        mPressedRow = -1
        mFocusRow = -1
        mScrollTop = 0
        mMaxScroll = 0
        mStateTextOnly = False
        If mStateFrames IsNot Nothing Then
            mStateFrameIndex = 0
            mStateTimer.Enabled = animate
        End If
        Invalidate()
    End Sub

    ' 纯文字状态（不显示小电视）：用于无需动画/小电视的提示（如搜索页初始提示、无结果）。
    Public Sub ShowStateTextPlain(ByVal text As String)
        mStateText = text
        mShowMore = False
        mItems = Nothing
        mPressedRow = -1
        mFocusRow = -1
        mScrollTop = 0
        mMaxScroll = 0
        mStateTextOnly = True
        If mStateTimer IsNot Nothing Then
            mStateTimer.Enabled = False
        End If
        Invalidate()
    End Sub

    ' 清除按下高亮并同步重绘（返回列表页时调用，避免旧高亮残留在下一帧）。
    Public Sub ClearPressedHighlight()
        If mPressedRow <> -1 Then
            mPressedRow = -1
            Invalidate()
        End If
    End Sub

    ' 当前键盘焦点行索引（-1=无）。
    Public ReadOnly Property FocusedRow() As Integer
        Get
            Return mFocusRow
        End Get
    End Property

    ' 是否已建立键盘焦点（列表有数据且焦点行有效）。
    Public ReadOnly Property HasKeyboardFocus() As Boolean
        Get
            Return (mItems IsNot Nothing AndAlso mItems.Count > 0 AndAlso mFocusRow >= 0 AndAlso mFocusRow < mItems.Count)
        End Get
    End Property

    ' 焦点是否停在“加载更多”行。
    Public ReadOnly Property FocusIsOnMore() As Boolean
        Get
            Return (mItems IsNot Nothing AndAlso mShowMore AndAlso mFocusRow >= mItems.Count)
        End Get
    End Property

    ' 设置焦点行（自动滚动到可见区域），超界时钳位到首/尾。
    Public Sub SetFocusedRow(ByVal idx As Integer)
        If mItems Is Nothing OrElse mItems.Count = 0 Then
            mFocusRow = -1
            Invalidate()
            Return
        End If
        If idx < 0 Then
            idx = 0
        End If
        Dim max As Integer = mItems.Count - 1
        If mShowMore Then
            max = mItems.Count
        End If
        If idx > max Then
            idx = max
        End If
        mFocusRow = idx
        EnsureFocusVisible()
        Invalidate()
    End Sub

    ' 方向键移动焦点（delta=±1）。
    Public Sub MoveFocus(ByVal delta As Integer)
        If mFocusRow < 0 Then
            SetFocusedRow(0)
        Else
            SetFocusedRow(mFocusRow + delta)
        End If
    End Sub

    ' 让焦点行滚动到可见区域。
    Private Sub EnsureFocusVisible()
        If mItems Is Nothing OrElse mFocusRow < 0 Then
            Return
        End If
        Dim h As Integer = Me.ClientSize.Height
        If h <= 0 Then
            Return
        End If
        If mFocusRow < mItems.Count Then
            Dim rowTop As Integer = mFocusRow * mRowH
            If rowTop < mScrollTop Then
                SetScrollTop(rowTop)
            ElseIf rowTop + mRowH > mScrollTop + h Then
                SetScrollTop(rowTop + mRowH - h)
            End If
        Else
            Dim moreTop As Integer = mItems.Count * mRowH
            If moreTop + mMoreH > mScrollTop + h Then
                SetScrollTop(moreTop + mMoreH - h)
            ElseIf moreTop < mScrollTop Then
                SetScrollTop(moreTop)
            End If
        End If
    End Sub

    ' 激活当前焦点行：普通行触发 OnRowTap，加载更多行触发 OnMoreTap。返回 True 表示已处理。
    Public Function ActivateFocusedRow() As Boolean
        If mFocusRow < 0 OrElse mItems Is Nothing Then
            Return False
        End If
        If mFocusRow >= mItems.Count Then
            If mShowMore Then
                If OnMoreTap IsNot Nothing Then
                    OnMoreTap(Me, System.EventArgs.Empty)
                End If
                Return True
            End If
            Return False
        End If
        If OnRowTap IsNot Nothing Then
            OnRowTap(mFocusRow)
        End If
        Return True
    End Function

    ' 清除键盘焦点高亮（检测到触摸/鼠标操作时调用）：触摸后高亮立即消失，
    ' 等下一次按方向键才重新出现，避免与按压高亮并存。
    Public Sub ClearKeyboardFocus()
        If mFocusRow <> -1 Then
            mFocusRow = -1
            Invalidate()
        End If
    End Sub

    ' 停止状态动画（进入列表数据时调用）。
    Private Sub StopStateAnimation()
        If mStateTimer IsNot Nothing Then
            mStateTimer.Enabled = False
        End If
    End Sub

    ' 状态动画 tick：切帧 + 重绘（小电视抖动）。
    Private Sub OnStateTick(ByVal sender As Object, ByVal e As System.EventArgs)
        If mStateText = "" Then
            mStateTimer.Enabled = False
            Return
        End If
        If mStateFrames Is Nothing Then
            mStateTimer.Enabled = False
            Return
        End If
        mStateFrameIndex = (mStateFrameIndex + 1) Mod mStateFrames.Length
        Invalidate()
    End Sub

    ' 是否显示“加载更多”行（分页开关）。
    Public Sub SetShowMore(ByVal value As Boolean)
        mShowMore = value
        UpdateMaxScroll()
        Invalidate()
    End Sub

    ' 把当前内容导出为 Bitmap（供过渡动画截图，无需控件可见）。
    Public Function RenderToBitmap() As System.Drawing.Bitmap
        UpdateBackBuffer()
        If mBackBuf Is Nothing Then
            Return Nothing
        End If
        Dim copy As System.Drawing.Bitmap = Nothing
        Try
            copy = New System.Drawing.Bitmap(mBackBuf.Width, mBackBuf.Height)
            Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(copy)
            Dim srcR As New System.Drawing.Rectangle(0, 0, mBackBuf.Width, mBackBuf.Height)
            Dim dstR As New System.Drawing.Rectangle(0, 0, mBackBuf.Width, mBackBuf.Height)
            g.DrawImage(mBackBuf, dstR, srcR, System.Drawing.GraphicsUnit.Pixel)
            g.Dispose()
        Catch ex As Exception
            copy = Nothing
        End Try
        Return copy
    End Function

    Private Sub UpdateMaxScroll()
        Dim totalH As Integer = 0
        If mItems IsNot Nothing Then
            totalH = mItems.Count * mRowH
        End If
        If mShowMore Then
            totalH += mMoreH
        End If
        mMaxScroll = totalH - Me.Height
        If mMaxScroll < 0 Then
            mMaxScroll = 0
        End If
        If mScrollTop > mMaxScroll Then
            mScrollTop = mMaxScroll
        End If
        If mScrollTop < 0 Then
            mScrollTop = 0
        End If
    End Sub

    Protected Overrides Sub OnResize(ByVal e As System.EventArgs)
        MyBase.OnResize(e)
        UpdateMaxScroll()
        DisposeBuffer()
        Invalidate()
    End Sub

    Private Sub DisposeBuffer()
        If mBackG IsNot Nothing Then
            mBackG.Dispose()
            mBackG = Nothing
        End If
        If mBackBuf IsNot Nothing Then
            mBackBuf.Dispose()
            mBackBuf = Nothing
        End If
        mBufW = 0
        mBufH = 0
    End Sub

    ' 关键：禁止系统先擦背景（否则每次重绘闪白屏）。绘制全在 OnPaint 完成。
    Protected Overrides Sub OnPaintBackground(ByVal e As System.Windows.Forms.PaintEventArgs)
    End Sub

    Protected Overrides Sub OnPaint(ByVal e As System.Windows.Forms.PaintEventArgs)
        UpdateBackBuffer()
        If mBackBuf IsNot Nothing Then
            Try
                e.Graphics.DrawImage(mBackBuf, 0, 0)
            Catch ex As Exception
            End Try
        End If
    End Sub

    ' 把当前内容绘制到双缓冲位图（OnPaint 与 WM_PRINT 共用）。
    Private Sub UpdateBackBuffer()
        Dim w As Integer = Me.ClientSize.Width
        Dim h As Integer = Me.ClientSize.Height
        If w <= 0 OrElse h <= 0 Then
            Return
        End If

        ' 复用双缓冲位图（WM 上创建大 Bitmap 开销大，滚动时绝不重建）。
        If mBackBuf Is Nothing OrElse mBufW <> w OrElse mBufH <> h Then
            DisposeBuffer()
            mBackBuf = New System.Drawing.Bitmap(w, h)
            mBackG = System.Drawing.Graphics.FromImage(mBackBuf)
            mBufW = w
            mBufH = h
        End If

        Dim g As System.Drawing.Graphics = mBackG
        g.FillRectangle(mBrushWhite, 0, 0, w, h)

        If mStateText <> "" Then
            DrawStateText(g, w, h)
            Return
        End If

        If mItems Is Nothing OrElse mItems.Count = 0 Then
            Return
        End If

        ' 卡片几何：左侧封面缩略图 16:9，右侧标题占整行高。
        Dim coverLeft As Integer = 4
        Dim coverW As Integer = mCoverW
        If coverW <= 0 Then
            coverW = 96
        End If
        Dim coverH As Integer = mCoverH
        If coverH <= 0 Then
            coverH = 54
        End If
        Dim textX As Integer = coverLeft + coverW + 10
        Dim textW As Integer = w - textX - 6
        If textW < 80 Then
            textW = 80
        End If
        Dim coverTopPad As Integer = (mRowH - coverH) \ 2
        If coverTopPad < 2 Then
            coverTopPad = 2
        End If

        ' 只绘制可见行（含上下各 1 行的缓冲）。
        Dim firstRow As Integer = mScrollTop \ mRowH
        If firstRow < 0 Then
            firstRow = 0
        End If
        Dim lastRow As Integer = (mScrollTop + h) \ mRowH + 1
        If lastRow >= mItems.Count Then
            lastRow = mItems.Count - 1
        End If

        Dim i As Integer
        For i = firstRow To lastRow
            Dim ry As Integer = i * mRowH - mScrollTop
            Dim ri As Object = mItems(i)

            ' 行背景。
            If i = mPressedRow Then
                g.FillRectangle(mBrushPressed, 0, ry, w, mRowH)
            ElseIf i Mod 2 = 0 Then
                g.FillRectangle(mBrushWhite, 0, ry, w, mRowH)
            Else
                g.FillRectangle(mBrushGray, 0, ry, w, mRowH)
            End If

            ' 键盘焦点行：浅粉底 + 粉色左边条（区别于按压高亮，方向键可辨识）。
            If i = mFocusRow Then
                g.FillRectangle(mBrushPressed, 0, ry, w, mRowH)
                g.FillRectangle(mBrushFocus, 0, ry, 5, mRowH)
                g.DrawRectangle(mPenFocus, 0, ry, w - 1, mRowH - 1)
            End If

            Dim title As String = ""
            Dim view As String = ""
            Dim danmaku As String = ""
            Dim author As String = ""
            Dim bmp As System.Drawing.Bitmap = Nothing
            Try
                title = CType(ri, MainForm.RecItem).Title
                view = CType(ri, MainForm.RecItem).View
                danmaku = CType(ri, MainForm.RecItem).Danmaku
                author = CType(ri, MainForm.RecItem).Author
                bmp = CType(ri, MainForm.RecItem).Bitmap
            Catch ex As Exception
            End Try

            ' 纯文字行（搜索历史等）：无封面/信息条，标题在上、副文字在下。
            If mNoCover Then
                Dim txtX As Integer = 10
                Dim txtW As Integer = w - txtX - 6
                If txtW < 80 Then
                    txtW = 80
                End If
                Dim tRect As New System.Drawing.RectangleF(txtX, ry + 3, txtW, mLineH)
                g.DrawString(title, mFontTitle, mBrushTitle, tRect)
                If author <> "" Then
                    Dim sRect As New System.Drawing.RectangleF(txtX, ry + 3 + mLineH + 1, txtW, mSubH)
                    g.DrawString(author, mFontSub, mBrushSub, sRect)
                End If
                Continue For
            End If

            ' 封面：已加载图或默认图；都没有则画占位底色。
            Dim coverRect As New System.Drawing.Rectangle(coverLeft, ry + coverTopPad, coverW, coverH)
            If bmp IsNot Nothing Then
                Try
                    Dim srcRect As New System.Drawing.Rectangle(0, 0, bmp.Width, bmp.Height)
                    g.DrawImage(bmp, coverRect, srcRect, System.Drawing.GraphicsUnit.Pixel)
                Catch exImg As Exception
                    DrawCoverFallback(g, coverRect)
                End Try
            ElseIf mDefaultBg IsNot Nothing Then
                Try
                    Dim srcRect As New System.Drawing.Rectangle(0, 0, mDefaultBg.Width, mDefaultBg.Height)
                    g.DrawImage(mDefaultBg, coverRect, srcRect, System.Drawing.GraphicsUnit.Pixel)
                Catch exImg As Exception
                    DrawCoverFallback(g, coverRect)
                End Try
            Else
                DrawCoverFallback(g, coverRect)
            End If

            If HistoryMode Then
                ' ===== 历史行：左侧封面 + 右侧标题两行 + 作者 + 右下角进度文字 =====
                ' 标题（两行）。
                Dim hTitleRect As New System.Drawing.RectangleF(textX, ry + 3, textW, mLineH * 2)
                g.DrawString(title, mFontTitle, mBrushTitle, hTitleRect, mStringFormat)
                ' 作者行。
                Dim hAuthRect As New System.Drawing.RectangleF(textX, ry + 3 + mLineH * 2 + 1, textW, mSubH)
                g.DrawString(author, mFontSub, mBrushSub, hAuthRect)
                ' 右下角进度文字（右对齐）。
                Dim progText As String = danmaku
                If progText = "" Then
                    progText = If(mIsChinese, "还没看过", "Not watched")
                End If
                Dim progW As Integer = CInt(g.MeasureString(progText, mFontInfo).Width)
                Dim progX As Integer = textX + textW - progW
                Dim progY As Integer = ry + mRowH - mSubH - 4
                If progX < textX Then
                    progX = textX
                End If
                g.DrawString(progText, mFontInfo, mBrushSub, progX, progY)
            Else
                ' ===== 推荐行：封面底部播放量+弹幕，右侧标题 =====
                ' 封面底部半透明灰条：播放量 + 弹幕数（不占标题空间）。
                ' 数字宽度自适应：先按空间限制格式，超宽用紧凑格式，避免顶出封面。
                Dim infoBarH As Integer = 20
                If infoBarH > coverH Then
                    infoBarH = coverH
                End If
                Dim infoTop As Integer = ry + coverTopPad + coverH - infoBarH
                g.FillRectangle(mBrushInfoBar, coverLeft, infoTop, coverW, infoBarH)

                Dim iconH As Integer = 13
                If iconH > infoBarH - 5 Then
                    iconH = infoBarH - 5
                End If
                If iconH < 4 Then
                    iconH = 4
                End If
                Dim iconY As Integer = infoTop + (infoBarH - iconH) \ 2
                Dim textY As Integer = infoTop + (infoBarH - mSubH) \ 2

                ' 可用宽度：封面宽减左右边距；两个数据块各占一半（含图标）。
                Dim availW As Integer = coverW - 10
                Dim blockW As Integer = availW \ 2
                If blockW < 30 Then
                    blockW = 30
                End If
                Dim textMax As Integer = blockW - iconH - 3 - 3
                If textMax < 12 Then
                    textMax = 12
                End If

                Dim x As Integer = coverLeft + 5

                If view = "" AndAlso danmaku = "" Then
                    ' 无播放量/弹幕数据（如搜索结果）：信息条改显示 UP 主，不显示无意义的 0。
                    Dim authTxt As String = author
                    If authTxt = "" Then
                        authTxt = If(mIsChinese, "未知UP", "Unknown UP")
                    End If
                    If CInt(g.MeasureString(authTxt, mFontInfo).Width) > availW Then
                        While CInt(g.MeasureString(authTxt, mFontInfo).Width) > availW AndAlso authTxt.Length > 1
                            authTxt = authTxt.Substring(0, authTxt.Length - 1)
                        End While
                        If authTxt.Length > 0 Then
                            authTxt = authTxt.Substring(0, authTxt.Length - 1) & "..."
                        End If
                    End If
                    g.DrawString(authTxt, mFontInfo, mBrushInfoText, x, textY)
                Else
                    ' 播放量块。
                    If mIconViews IsNot Nothing Then
                        Try
                            Dim iSrc As New System.Drawing.Rectangle(0, 0, mIconViews.Width, mIconViews.Height)
                            Dim iDst As New System.Drawing.Rectangle(x, iconY, iconH, iconH)
                            g.DrawImage(mIconViews, iDst, iSrc, System.Drawing.GraphicsUnit.Pixel)
                            x += iconH + 3
                        Catch exImg As Exception
                        End Try
                    End If
                    Dim viewTxt As String = FormatCount(view)
                    Dim vw As Integer = CInt(g.MeasureString(viewTxt, mFontInfo).Width)
                    If vw > textMax Then
                        viewTxt = FormatCompact(view)
                        vw = CInt(g.MeasureString(viewTxt, mFontInfo).Width)
                        If vw > textMax Then
                            viewTxt = FormatCountRaw(view, textMax, g)
                            vw = CInt(g.MeasureString(viewTxt, mFontInfo).Width)
                        End If
                    End If
                    g.DrawString(viewTxt, mFontInfo, mBrushInfoText, x, textY)
                    x += vw + 8

                    ' 弹幕数块。
                    If mIconDanmakus IsNot Nothing Then
                        Try
                            Dim iSrc As New System.Drawing.Rectangle(0, 0, mIconDanmakus.Width, mIconDanmakus.Height)
                            Dim iDst As New System.Drawing.Rectangle(x, iconY, iconH, iconH)
                            g.DrawImage(mIconDanmakus, iDst, iSrc, System.Drawing.GraphicsUnit.Pixel)
                            x += iconH + 3
                        Catch exImg As Exception
                        End Try
                    End If
                    Dim danmakuTxt As String = FormatCount(danmaku)
                    Dim dw As Integer = CInt(g.MeasureString(danmakuTxt, mFontInfo).Width)
                    If dw > textMax Then
                        danmakuTxt = FormatCompact(danmaku)
                        dw = CInt(g.MeasureString(danmakuTxt, mFontInfo).Width)
                        If dw > textMax Then
                            danmakuTxt = FormatCountRaw(danmaku, textMax, g)
                            dw = CInt(g.MeasureString(danmakuTxt, mFontInfo).Width)
                        End If
                    End If
                    g.DrawString(danmakuTxt, mFontInfo, mBrushInfoText, x, textY)
                End If

                ' 标题：右侧，占整行高，两行截断。
                Dim titleRect As New System.Drawing.RectangleF(textX, ry + 3, textW, mLineH * 2)
                g.DrawString(title, mFontTitle, mBrushTitle, titleRect, mStringFormat)
            End If
        Next

        ' 加载更多行。
        If mShowMore Then
            Dim moreTop As Integer = mItems.Count * mRowH - mScrollTop
            If moreTop < h Then
                If mFocusRow >= mItems.Count Then
                    g.FillRectangle(mBrushPressed, 0, moreTop, w, mMoreH)
                    g.FillRectangle(mBrushFocus, 0, moreTop, 5, mMoreH)
                    g.DrawRectangle(mPenFocus, 0, moreTop, w - 1, mMoreH - 1)
                Else
                    g.FillRectangle(mBrushWhite, 0, moreTop, w, mMoreH)
                End If
                Dim moreText As String = ""
                If mIsChinese Then
                    moreText = "加载更多"
                Else
                    moreText = "Load more"
                End If
                g.DrawString(moreText, mFontSub, mBrushMore, 4, moreTop + 10)
            End If
        End If
    End Sub

    ' 状态文本绘制：居中显示小电视抖动动画（bili_anim_tv_chan 帧），正下方配状态文字。
    Private Sub DrawStateText(ByVal g As System.Drawing.Graphics, ByVal w As Integer, ByVal h As Integer)
        If mStateFrames Is Nothing Then
            g.DrawString(mStateText, mFontSub, mBrushSub, 12, 8)
            Return
        End If
        If mStateTextOnly Then
            ' 纯文字提示（搜索页初始/无结果）：居中一行灰字，不画小电视。
            Dim tww As Integer = CInt(g.MeasureString(mStateText, mFontSub).Width)
            Dim thh As Integer = CInt(g.MeasureString(mStateText, mFontSub).Height)
            Dim txx As Integer = (w - tww) \ 2
            If txx < 4 Then
                txx = 4
            End If
            g.DrawString(mStateText, mFontSub, mBrushSub, txx, (h - thh) \ 2)
            Return
        End If

        ' 小电视：约 1/3 屏高，居中。
        Dim tvSize As Integer = h \ 3
        If tvSize < 60 Then
            tvSize = 60
        End If
        If tvSize > 140 Then
            tvSize = 140
        End If

        ' 抖动：随帧 index 做小幅水平/垂直摆动（趣味抖动）。
        Dim shakeX As Integer = 0
        Dim shakeY As Integer = 0
        Try
            If mStateFrames.Length > 0 Then
                Dim ph As Double = (mStateFrameIndex * System.Math.PI * 2.0) / mStateFrames.Length
                shakeX = CInt(System.Math.Sin(ph) * 2.0)
                shakeY = CInt(System.Math.Abs(System.Math.Cos(ph)) * 3.0)
            End If
        Catch ex As Exception
        End Try

        ' 文字高度（用于垂直居中整块）。
        Dim th As Integer = CInt(g.MeasureString(mStateText, mFontSub).Height)
        If th < 14 Then
            th = 14
        End If
        Dim blockH As Integer = tvSize + 10 + th
        Dim top As Integer = (h - blockH) \ 2
        If top < 10 Then
            top = 10
        End If

        Dim tvRect As New System.Drawing.Rectangle((w - tvSize) \ 2 + shakeX, top + shakeY, tvSize, tvSize)
        If mStateFrameIndex >= 0 AndAlso mStateFrameIndex < mStateFrames.Length AndAlso mStateFrames(mStateFrameIndex) IsNot Nothing Then
            Try
                mStateFrames(mStateFrameIndex).DrawStretched(g, tvRect)
            Catch ex As Exception
            End Try
        End If

        ' 正下方文字（居中）。
        Dim tw As Integer = CInt(g.MeasureString(mStateText, mFontSub).Width)
        Dim textX As Integer = (w - tw) \ 2
        If textX < 4 Then
            textX = 4
        End If
        g.DrawString(mStateText, mFontSub, mBrushSub, textX, top + tvSize + 10)
    End Sub

    ' 封面占位底色（无图/解码失败）。
    Private Sub DrawCoverFallback(ByVal g As System.Drawing.Graphics, ByVal rect As System.Drawing.Rectangle)
        g.FillRectangle(mBrushPh, rect.X, rect.Y, rect.Width, rect.Height)
    End Sub

    ' 数字美化：1234 -> 1.2千 / 12345 -> 1.2万（中文）或 K/M（英文）。
    Private Function FormatCount(ByVal s As String) As String
        If s = "" Then
            Return "0"
        End If
        Dim n As Long = 0
        Try
            n = System.Convert.ToInt64(s)
        Catch ex As Exception
            Return s
        End Try
        If n < 10000 Then
            Return n.ToString()
        End If
        If mIsChinese Then
            Return (CInt(n / 1000) / 10.0).ToString("0.0") & "万"
        End If
        If n >= 1000000 Then
            Return (CInt(n / 100000) / 10.0).ToString("0.0") & "M"
        End If
        Return (CInt(n / 1000) / 10.0).ToString("0.0") & "K"
    End Function

    ' 紧凑格式：整数万/K/M（省宽度）。
    Private Function FormatCompact(ByVal s As String) As String
        If s = "" Then
            Return "0"
        End If
        Dim n As Long = 0
        Try
            n = System.Convert.ToInt64(s)
        Catch ex As Exception
            Return s
        End Try
        If n < 1000 Then
            Return n.ToString()
        End If
        If mIsChinese Then
            Return (n \ 10000).ToString() & "万"
        End If
        If n >= 1000000 Then
            Return (n \ 1000000).ToString() & "M"
        End If
        Return (n \ 1000).ToString() & "K"
    End Function

    ' 极限截断：逐步缩短直到能放进 maxWidth（后缀 W/M/K）。
    Private Function FormatCountRaw(ByVal s As String, ByVal maxWidth As Integer, ByVal g As System.Drawing.Graphics) As String
        Dim base As String = FormatCompact(s)
        While CInt(g.MeasureString(base, mFontInfo).Width) > maxWidth AndAlso base.Length > 1
            base = base.Substring(0, base.Length - 1)
        End While
        Return base
    End Function

    ' 命中测试：返回行索引（-1=无，-2=加载更多）。
    Private Function HitTest(ByVal clientY As Integer) As Integer
        If mItems Is Nothing OrElse mItems.Count = 0 Then
            Return -1
        End If
        Dim contentY As Integer = clientY + mScrollTop
        If mShowMore AndAlso contentY >= mItems.Count * mRowH Then
            Return -2
        End If
        Dim idx As Integer = contentY \ mRowH
        If idx < 0 OrElse idx >= mItems.Count Then
            Return -1
        End If
        Return idx
    End Function

    Protected Overrides Sub OnMouseDown(ByVal e As System.Windows.Forms.MouseEventArgs)
        MyBase.OnMouseDown(e)
        ClearKeyboardFocus()
        mDownY = e.Y
        mDownScroll = mScrollTop
        mDragging = False
        mVelY = 0
        mLastY = e.Y
        mLastTick = System.Environment.TickCount
        If mStateText = "" AndAlso mItems IsNot Nothing Then
            Dim idx As Integer = HitTest(e.Y)
            If idx >= 0 Then
                mPressedRow = idx
                Invalidate()
            End If
        End If
    End Sub

    Protected Overrides Sub OnMouseMove(ByVal e As System.Windows.Forms.MouseEventArgs)
        MyBase.OnMouseMove(e)
        If mStateText <> "" Then
            Return
        End If
        ' 桌面：仅左键按下时才拖动。松开后鼠标继续移动不得重新触发拖动（否则列表跟手不结束）。
        If IsDesktopRuntime() Then
            If (e.Button And System.Windows.Forms.MouseButtons.Left) = 0 Then
                mDragging = False
                Return
            End If
        End If
        Dim dy As Integer = e.Y - mDownY
        If Not mDragging Then
            ' 桌面鼠标点击常有小抖动，阈值取更大，避免误判为拖动。
            Dim dragThreshold As Integer = If(IsDesktopRuntime(), 24, 8)
            If System.Math.Abs(dy) > dragThreshold Then
                mDragging = True
                mPressedRow = -1
            End If
        End If
        If mDragging Then
            Dim nowTick As Integer = System.Environment.TickCount
            Dim dt As Integer = nowTick - mLastTick
            If dt > 0 Then
                mVelY = (e.Y - mLastY) * 1000.0F / CSng(dt)
            End If
            mLastY = e.Y
            mLastTick = nowTick

            ' 手指上滑(e.Y 减小)想看下面 → scrollTop 增大。
            Dim target As Integer = mDownScroll - dy
            If target < 0 Then
                target = 0
            End If
            If target > mMaxScroll Then
                target = mMaxScroll
            End If
            mScrollTop = target
            Invalidate()
            NotifyScroll()
        End If
    End Sub

    Protected Overrides Sub OnMouseUp(ByVal e As System.Windows.Forms.MouseEventArgs)
        MyBase.OnMouseUp(e)
        If mStateText <> "" Then
            Return
        End If
        If mDragging Then
            mDragging = False
            mPressedRow = -1
            mDownY = e.Y
            mDownScroll = mScrollTop
            Invalidate()
            ' 桌面鼠标拖动不启动惯性（惯性是触摸滚动的手感）。
            If Not IsDesktopRuntime() Then
                mFlingVel = mVelY
                If mFlingVel > 4000 Then
                    mFlingVel = 4000
                End If
                If mFlingVel < -4000 Then
                    mFlingVel = -4000
                End If
                mFlingTimer.Enabled = True
            End If
            Return
        End If
        mDragging = False
        mDownY = e.Y
        mDownScroll = mScrollTop
        ' 保留按下高亮（点击后不立即消失，作为按压反馈随过渡滑走）；
        ' 由返回列表页的路径统一调用 ClearPressedHighlight 清除，避免残留。
        Invalidate()
        Dim idx As Integer = HitTest(e.Y)
        If idx = -2 Then
            If OnMoreTap IsNot Nothing Then
                OnMoreTap(Me, System.EventArgs.Empty)
            End If
        ElseIf idx >= 0 Then
            If OnRowTap IsNot Nothing Then
                OnRowTap(idx)
            End If
        End If
    End Sub

    Private Sub OnFlingTick(ByVal sender As Object, ByVal e As System.EventArgs)
        If System.Math.Abs(mFlingVel) < 30 Then
            mFlingTimer.Enabled = False
            Return
        End If
        mScrollTop = mScrollTop - CInt(mFlingVel / 1000.0F * 16.0F)
        If mScrollTop < 0 Then
            mScrollTop = 0
        End If
        If mScrollTop > mMaxScroll Then
            mScrollTop = mMaxScroll
        End If
        mFlingVel = mFlingVel * 0.9F
        Invalidate()
        NotifyScroll()
    End Sub

    ' 滚动到指定内容偏移（供外部复位）。
    Public Sub SetScrollTop(ByVal value As Integer)
        mScrollTop = value
        If mScrollTop < 0 Then
            mScrollTop = 0
        End If
        If mScrollTop > mMaxScroll Then
            mScrollTop = mMaxScroll
        End If
        Invalidate()
        NotifyScroll()
    End Sub

    ' 桌面鼠标滚轮滚动（delta：-120 向下 / +120 向上）。
    Public Sub ScrollByWheel(ByVal delta As Integer)
        Try
            If mStateText <> "" Then
                Return
            End If
            ClearKeyboardFocus()
            Dim stepPx As Integer = mRowH * 3
            If stepPx < 40 Then
                stepPx = 40
            End If
            If delta < 0 Then
                mScrollTop += stepPx
            ElseIf delta > 0 Then
                mScrollTop -= stepPx
            End If
            If mScrollTop < 0 Then
                mScrollTop = 0
            End If
            If mScrollTop > mMaxScroll Then
                mScrollTop = mMaxScroll
            End If
            mFlingTimer.Enabled = False
            Invalidate()
            NotifyScroll()
        Catch ex As Exception
        End Try
    End Sub

    Public ReadOnly Property ScrollTop() As Integer
        Get
            Return mScrollTop
        End Get
    End Property

    ' 可见首行（含部分可见行）。
    Public Function FirstVisibleRow() As Integer
        If mItems Is Nothing OrElse mItems.Count = 0 OrElse mRowH <= 0 Then
            Return 0
        End If
        Dim first As Integer = mScrollTop \ mRowH
        If first < 0 Then
            first = 0
        End If
        If first >= mItems.Count Then
            first = mItems.Count - 1
        End If
        Return first
    End Function

    ' 可见末行（含部分可见行）。
    Public Function LastVisibleRow() As Integer
        If mItems Is Nothing OrElse mItems.Count = 0 OrElse mRowH <= 0 Then
            Return 0
        End If
        Dim h As Integer = Me.ClientSize.Height
        If h <= 0 Then
            h = 100
        End If
        Dim last As Integer = (mScrollTop + h) \ mRowH
        If last >= mItems.Count Then
            last = mItems.Count - 1
        End If
        If last < 0 Then
            last = 0
        End If
        Return last
    End Function

    ' 滚动变化通知（供外部按可见窗口清理封面内存）。
    Private Sub NotifyScroll()
        If OnScrollChanged IsNot Nothing Then
            Try
                OnScrollChanged(Me, System.EventArgs.Empty)
            Catch ex As Exception
            End Try
        End If
    End Sub

    Protected Overrides Sub Dispose(ByVal disposing As Boolean)
        If disposing Then
            If mFlingTimer IsNot Nothing Then
                mFlingTimer.Enabled = False
                mFlingTimer.Dispose()
                mFlingTimer = Nothing
            End If
            If mStateTimer IsNot Nothing Then
                mStateTimer.Enabled = False
                mStateTimer.Dispose()
                mStateTimer = Nothing
            End If
            DisposeBuffer()
            If mFontTitle IsNot Nothing Then
                mFontTitle.Dispose()
                mFontTitle = Nothing
            End If
            If mFontSub IsNot Nothing Then
                mFontSub.Dispose()
                mFontSub = Nothing
            End If
            If mFontInfo IsNot Nothing Then
                mFontInfo.Dispose()
                mFontInfo = Nothing
            End If
            If mDefaultBg IsNot Nothing Then
                mDefaultBg.Dispose()
                mDefaultBg = Nothing
            End If
            If mIconViews IsNot Nothing Then
                mIconViews.Dispose()
                mIconViews = Nothing
            End If
            If mIconDanmakus IsNot Nothing Then
                mIconDanmakus.Dispose()
                mIconDanmakus = Nothing
            End If
            If mBrushFocus IsNot Nothing Then
                mBrushFocus.Dispose()
                mBrushFocus = Nothing
            End If
            If mPenFocus IsNot Nothing Then
                mPenFocus.Dispose()
                mPenFocus = Nothing
            End If
            If mStateFrames IsNot Nothing Then
                Dim i As Integer
                For i = 0 To mStateFrames.Length - 1
                    Try
                        If mStateFrames(i) IsNot Nothing Then
                            mStateFrames(i).Dispose()
                        End If
                    Catch ex As Exception
                    End Try
                    mStateFrames(i) = Nothing
                Next
                mStateFrames = Nothing
            End If
            If mStateFrameStreams IsNot Nothing Then
                Dim j As Integer
                For j = 0 To mStateFrameStreams.Length - 1
                    Try
                        If mStateFrameStreams(j) IsNot Nothing Then
                            mStateFrameStreams(j).Close()
                        End If
                    Catch ex As Exception
                    End Try
                    mStateFrameStreams(j) = Nothing
                Next
                mStateFrameStreams = Nothing
            End If
        End If
        MyBase.Dispose(disposing)
    End Sub

End Class
