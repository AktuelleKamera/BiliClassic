' 自绘视频详情页控件（.NET CF 2.0 兼容）。
' 双缓冲：整页绘制到内存位图，OnPaint 单次 DrawImage，杜绝闪烁/撕裂。
' 触摸滚动：拖动 + 惯性 fling + 桌面鼠标滚轮，内容超高时上下滑动。
' 布局与旧版标准控件版保持一致：封面(16:9全宽) → 标题(多行粗体) → 播放按钮 → UP/时长/分P → 数据统计 → 发布时间 → 简介。
' 用法：
'   ctrl.SetData(title, author, duration, partCount, view, danmaku, likeC, coinC, favC, replyC, pubdate, desc, isChinese)
'   ctrl.SetCover(bmp) / ctrl.ClearCover()
'   ctrl.ShowStateText(msg)          ' 状态文本（加载中/错误/空）
'   ctrl.PlayClick 事件              ' 点击播放按钮
'   ctrl.RenderToBitmap()            ' 供过渡动画快照

Public Class VideoDetailControl
    Inherits System.Windows.Forms.Control

    Public Event PlayClick As EventHandler

    ' 数据。
    Private mTitle As String = ""
    Private mAuthor As String = ""
    Private mDuration As String = ""
    Private mPartCount As String = ""
    Private mView As String = ""
    Private mDanmaku As String = ""
    Private mLikeC As String = ""
    Private mCoinC As String = ""
    Private mFavC As String = ""
    Private mReplyC As String = ""
    Private mPubdate As String = ""
    Private mDesc As String = ""
    Private mIsChinese As Boolean = False
    Private mCover As System.Drawing.Bitmap = Nothing

    ' 状态文本（加载中/错误/空）。
    Private mStateText As String = ""

    ' 字体。
    Private mFontTitle As System.Drawing.Font = Nothing
    Private mFontBody As System.Drawing.Font = Nothing

    ' 缓存画刷。
    Private mBrushWhite As System.Drawing.SolidBrush = Nothing
    Private mBrushTitle As System.Drawing.SolidBrush = Nothing
    Private mBrushSub As System.Drawing.SolidBrush = Nothing
    Private mBrushStats As System.Drawing.SolidBrush = Nothing
    Private mBrushPlaceholder As System.Drawing.SolidBrush = Nothing
    Private mBrushPlay As System.Drawing.SolidBrush = Nothing
    Private mBrushPlayText As System.Drawing.SolidBrush = Nothing

    ' 行高。
    Private mLineH As Integer = 18
    Private mSubH As Integer = 14

    ' 内容总高（决定可滚动范围）。
    Private mContentH As Integer = 0
    Private mCoverH As Integer = 0
    Private mPlayRect As System.Drawing.Rectangle = New System.Drawing.Rectangle(0, 0, 0, 0)

    ' 滚动状态。
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

    ' 双缓冲。
    Private mBackBuf As System.Drawing.Bitmap = Nothing
    Private mBackG As System.Drawing.Graphics = Nothing
    Private mBufW As Integer = 0
    Private mBufH As Integer = 0

    ' 桌面运行时检测（鼠标与触摸行为不同：更大拖动阈值、无惯性、支持滚轮）。
    Private Function IsDesktopRuntime() As Boolean
        Try
            Return (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)
        Catch ex As Exception
            Return False
        End Try
    End Function

    ' 字体：桌面优先微软雅黑（无则回退 Tahoma），WM 用 Tahoma。
    Private Function GetUiFont(ByVal size As Single, ByVal bold As Boolean) As System.Drawing.Font
        Try
            Dim style As System.Drawing.FontStyle = If(bold, System.Drawing.FontStyle.Bold, System.Drawing.FontStyle.Regular)
            If IsDesktopRuntime() Then
                Try
                    Return New System.Drawing.Font("Microsoft YaHei", size, style)
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

    Public Sub New()
        MyBase.New()
        mFontTitle = GetUiFont(9.0!, True)
        mFontBody = GetUiFont(9.0!, False)
        mBrushWhite = New System.Drawing.SolidBrush(System.Drawing.Color.White)
        mBrushTitle = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H22, &H22, &H22))
        mBrushSub = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H88, &H88, &H88))
        mBrushStats = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H55, &H55, &H55))
        mBrushPlaceholder = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HE8, &HE8, &HE8))
        mBrushPlay = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HD8, &H6D, &HA5))
        mBrushPlayText = New System.Drawing.SolidBrush(System.Drawing.Color.White)
        mFlingTimer = New System.Windows.Forms.Timer()
        AddHandler mFlingTimer.Tick, AddressOf OnFlingTick
        mFlingTimer.Interval = 16
        mFlingTimer.Enabled = False
        Me.BackColor = System.Drawing.Color.White
        ComputeLineHeights()
    End Sub

    ' 用实际字体测量行高。
    Private Sub ComputeLineHeights()
        Try
            Dim g As System.Drawing.Graphics = Me.CreateGraphics()
            mSubH = CInt(Math.Ceiling(g.MeasureString("BiliClassic", mFontBody).Height))
            mLineH = CInt(Math.Ceiling(g.MeasureString("BiliClassic", mFontTitle).Height))
            If mSubH < 12 Then
                mSubH = 14
            End If
            If mLineH < mSubH + 2 Then
                mLineH = mSubH + 4
            End If
            g.Dispose()
        Catch ex As Exception
            mSubH = 14
            mLineH = 18
        End Try
    End Sub

    ' 设置详情数据（UI 线程调用）。
    Public Sub SetData(ByVal title As String, ByVal author As String, ByVal duration As String, ByVal partCount As String, ByVal view As String, ByVal danmaku As String, ByVal likeC As String, ByVal coinC As String, ByVal favC As String, ByVal replyC As String, ByVal pubdate As String, ByVal desc As String, ByVal isChinese As Boolean)
        mTitle = title
        mAuthor = author
        mDuration = duration
        mPartCount = partCount
        mView = view
        mDanmaku = danmaku
        mLikeC = likeC
        mCoinC = coinC
        mFavC = favC
        mReplyC = replyC
        mPubdate = pubdate
        mDesc = desc
        mIsChinese = isChinese
        mStateText = ""
        mScrollTop = 0
        UpdateMaxScroll()
        Invalidate()
    End Sub

    ' 显示状态文本（加载中/错误/空），不绘制内容。
    Public Sub ShowStateText(ByVal text As String)
        mStateText = text
        mScrollTop = 0
        mMaxScroll = 0
        Invalidate()
    End Sub

    ' 设置封面位图（外部负责生命周期，ClearCover 时置空）。
    Public Sub SetCover(ByVal bmp As System.Drawing.Bitmap)
        mCover = bmp
        Invalidate()
    End Sub

    ' 键盘确认键：内容已就绪时触发播放（等同点击播放按钮）。
    Public Sub PerformPlayClick()
        If mStateText = "" AndAlso mPlayRect.Width > 0 Then
            RaiseEvent PlayClick(Me, System.EventArgs.Empty)
        End If
    End Sub

    Public Sub ClearCover()
        mCover = Nothing
        Invalidate()
    End Sub

    ' 估算多行文本高度：按中文字符宽度估算行数，保守偏大。
    Private Function EstMultilineH(ByVal text As String, ByVal f As System.Drawing.Font, ByVal width As Integer) As Integer
        Try
            Dim g As System.Drawing.Graphics = Me.CreateGraphics()
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
            Return lines * mLineH + 2
        Catch ex As Exception
            Return mLineH * 2
        End Try
    End Function

    ' 计算内容总高与可滚动范围（布局与绘制共用同一几何）。
    Private Sub UpdateMaxScroll()
        Dim w As Integer = Me.ClientSize.Width
        If w < 10 Then
            w = 460
        End If
        Dim h As Integer = Me.ClientSize.Height
        If h <= 0 Then
            h = 100
        End If

        Dim contentW As Integer = w - 16
        If contentW < 40 Then
            contentW = 40
        End If
        mCoverH = CInt(contentW * 9 / 16)
        If mCoverH < 40 Then
            mCoverH = 40
        End If

        Dim y As Integer = 6
        y += mCoverH + 8

        ' 标题（多行粗体）。
        y += EstMultilineH(mTitle, mFontTitle, contentW) + 6

        ' 播放按钮。
        mPlayRect = New System.Drawing.Rectangle(8, y, contentW, 40)
        y += 48

        ' UP主 + 时长 + 分P。
        y += mSubH + 4

        ' 数据统计（可能两行）。
        Dim statsTxt As String = BuildStatsText()
        y += EstMultilineH(statsTxt, mFontBody, contentW) + 4

        ' 发布时间。
        If mPubdate <> "" Then
            y += mSubH + 4
        End If

        ' 简介标题。
        y += mLineH + 2

        ' 简介正文。
        y += EstMultilineH(mDesc, mFontBody, contentW) + 12

        mContentH = y
        mMaxScroll = mContentH - h
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

    Private Function BuildStatsText() As String
        Dim statsTxt As String = ""
        If mView <> "" Then
            statsTxt = If(mIsChinese, "播放 ", "Views ") & FormatCount(mView)
        End If
        If mDanmaku <> "" Then
            If statsTxt <> "" Then
                statsTxt &= "    "
            End If
            statsTxt &= If(mIsChinese, "弹幕 ", "Danmaku ") & FormatCount(mDanmaku)
        End If
        If mLikeC <> "" Then
            If statsTxt <> "" Then
                statsTxt &= "    "
            End If
            statsTxt &= If(mIsChinese, "赞 ", "Like ") & FormatCount(mLikeC)
        End If
        If mCoinC <> "" Then
            If statsTxt <> "" Then
                statsTxt &= "    "
            End If
            statsTxt &= If(mIsChinese, "币 ", "Coin ") & FormatCount(mCoinC)
        End If
        If mFavC <> "" Then
            If statsTxt <> "" Then
                statsTxt &= "    "
            End If
            statsTxt &= If(mIsChinese, "藏 ", "Fav ") & FormatCount(mFavC)
        End If
        If mReplyC <> "" Then
            If statsTxt <> "" Then
                statsTxt &= "    "
            End If
            statsTxt &= If(mIsChinese, "评 ", "Reply ") & FormatCount(mReplyC)
        End If
        Return statsTxt
    End Function

    Private Function FormatCount(ByVal n As String) As String
        Try
            Dim v As Long = CLng(n)
            If v >= 100000000 Then
                Return (v / 100000000.0).ToString("0.0") & "亿"
            ElseIf v >= 10000 Then
                Return (v / 10000.0).ToString("0.0") & "万"
            Else
                Return n
            End If
        Catch ex As Exception
            Return n
        End Try
    End Function

    ' 滚动。
    Private Sub UpdateMaxScrollAndClamp()
        UpdateMaxScroll()
    End Sub

    Public Sub SetScrollTop(ByVal value As Integer)
        mScrollTop = value
        If mScrollTop < 0 Then
            mScrollTop = 0
        End If
        If mScrollTop > mMaxScroll Then
            mScrollTop = mMaxScroll
        End If
        Invalidate()
    End Sub

    Public Sub ScrollByWheel(ByVal delta As Integer)
        Try
            If mStateText <> "" Then
                Return
            End If
            Dim stepPx As Integer = 60
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
        Catch ex As Exception
        End Try
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
    End Sub

    ' 双缓冲与绘制。
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

    Protected Overrides Sub OnResize(ByVal e As System.EventArgs)
        MyBase.OnResize(e)
        UpdateMaxScroll()
        DisposeBuffer()
        Invalidate()
    End Sub

    Private Sub UpdateBackBuffer()
        Dim w As Integer = Me.ClientSize.Width
        Dim h As Integer = Me.ClientSize.Height
        If w <= 0 OrElse h <= 0 Then
            Return
        End If

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
            g.DrawString(mStateText, mFontBody, mBrushSub, 12, 8)
            Return
        End If

        Dim contentW As Integer = w - 16
        If contentW < 40 Then
            contentW = 40
        End If

        Dim y As Integer = 6

        ' 封面：16:9 全宽。
        Dim coverRect As New System.Drawing.Rectangle(8, y - mScrollTop, contentW, mCoverH)
        If mCover IsNot Nothing Then
            Try
                Dim srcRect As New System.Drawing.Rectangle(0, 0, mCover.Width, mCover.Height)
                g.DrawImage(mCover, coverRect, srcRect, System.Drawing.GraphicsUnit.Pixel)
            Catch exImg As Exception
                g.FillRectangle(mBrushPlaceholder, coverRect)
            End Try
        Else
            g.FillRectangle(mBrushPlaceholder, coverRect)
        End If
        y += mCoverH + 8

        ' 标题（多行粗体）。
        Dim titleRect As New System.Drawing.RectangleF(8, y - mScrollTop, contentW, EstMultilineH(mTitle, mFontTitle, contentW))
        g.DrawString(mTitle, mFontTitle, mBrushTitle, titleRect)
        y += CInt(titleRect.Height) + 6

        ' 播放按钮：粉色，白色居中文字 + 播放三角。
        Dim playTop As Integer = y - mScrollTop
        mPlayRect = New System.Drawing.Rectangle(8, y, contentW, 40)
        g.FillRectangle(mBrushPlay, 8, playTop, contentW, 40)
        Dim btnText As String = If(mIsChinese, "播放视频", "Play Video")
        Dim tf As System.Drawing.Font = mFontTitle
        Dim tw As Single = g.MeasureString(btnText, tf).Width
        Dim th As Single = g.MeasureString(btnText, tf).Height
        Dim textX As Integer = CInt((contentW - tw) / 2) + 8
        Dim textY As Integer = playTop + CInt((40 - th) / 2)
        ' 播放三角图标。
        Dim triY As Integer = playTop + 13
        Dim triX As Integer = 8 + CInt((contentW - tw) / 2) - 16
        Try
            Dim triPts As System.Drawing.Point() = New System.Drawing.Point(2) {}
            triPts(0) = New System.Drawing.Point(triX, triY)
            triPts(1) = New System.Drawing.Point(triX, triY + 14)
            triPts(2) = New System.Drawing.Point(triX + 12, triY + 7)
            g.FillPolygon(mBrushPlayText, triPts)
        Catch exTri As Exception
        End Try
        g.DrawString(btnText, tf, mBrushPlayText, textX, textY)
        y += 48

        ' UP主 + 时长 + 分P。
        Dim infoTxt As String = ""
        If mAuthor <> "" Then
            infoTxt = If(mIsChinese, "UP主：", "UP: ") & mAuthor
        End If
        If mDuration <> "" Then
            If infoTxt <> "" Then
                infoTxt &= "  |  "
            End If
            infoTxt &= If(mIsChinese, "时长 ", "") & mDuration
        End If
        If mPartCount <> "" AndAlso mPartCount <> "1" Then
            If infoTxt <> "" Then
                infoTxt &= "  |  "
            End If
            infoTxt &= If(mIsChinese, "共 ", "Parts: ") & mPartCount & If(mIsChinese, "P", "")
        End If
        g.DrawString(infoTxt, mFontBody, mBrushSub, 8, y - mScrollTop)
        y += mSubH + 4

        ' 数据统计（可能两行）。
        Dim statsTxt As String = BuildStatsText()
        Dim statsRect As New System.Drawing.RectangleF(8, y - mScrollTop, contentW, EstMultilineH(statsTxt, mFontBody, contentW))
        g.DrawString(statsTxt, mFontBody, mBrushStats, statsRect)
        y += CInt(statsRect.Height) + 4

        ' 发布时间。
        If mPubdate <> "" Then
            g.DrawString(If(mIsChinese, "发布时间：", "Published: ") & mPubdate, mFontBody, mBrushSub, 8, y - mScrollTop)
            y += mSubH + 4
        End If

        ' 简介标题。
        g.DrawString(If(mIsChinese, "简介", "Description"), mFontTitle, mBrushTitle, 8, y - mScrollTop)
        y += mLineH + 2

        ' 简介正文。
        Dim descTxt As String = mDesc
        If descTxt = "" Then
            descTxt = If(mIsChinese, "暂无简介", "No description")
        End If
        Dim descRect As New System.Drawing.RectangleF(8, y - mScrollTop, contentW, EstMultilineH(descTxt, mFontBody, contentW))
        g.DrawString(descTxt, mFontBody, mBrushStats, descRect)
    End Sub

    ' 把当前内容导出为 Bitmap（供过渡动画快照）。
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

    ' 触摸/鼠标：拖动滚动 + 点击播放按钮。
    Protected Overrides Sub OnMouseDown(ByVal e As System.Windows.Forms.MouseEventArgs)
        MyBase.OnMouseDown(e)
        mDownY = e.Y
        mDownScroll = mScrollTop
        mDragging = False
        mVelY = 0
        mLastY = e.Y
        mLastTick = System.Environment.TickCount
        If mStateText <> "" Then
            Return
        End If
        If IsDesktopRuntime() Then
            ' 桌面：预判播放按钮区域（防误拖）。
            Dim contentY As Integer = e.Y + mScrollTop
            If contentY >= mPlayRect.Top AndAlso contentY <= mPlayRect.Bottom Then
                Return
            End If
        End If
    End Sub

    Protected Overrides Sub OnMouseMove(ByVal e As System.Windows.Forms.MouseEventArgs)
        MyBase.OnMouseMove(e)
        If mStateText <> "" Then
            Return
        End If
        If IsDesktopRuntime() Then
            If (e.Button And System.Windows.Forms.MouseButtons.Left) = 0 Then
                mDragging = False
                Return
            End If
        End If
        Dim dy As Integer = e.Y - mDownY
        If Not mDragging Then
            Dim dragThreshold As Integer = If(IsDesktopRuntime(), 24, 8)
            If System.Math.Abs(dy) > dragThreshold Then
                mDragging = True
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

            Dim target As Integer = mDownScroll - dy
            If target < 0 Then
                target = 0
            End If
            If target > mMaxScroll Then
                target = mMaxScroll
            End If
            mScrollTop = target
            Invalidate()
        End If
    End Sub

    Protected Overrides Sub OnMouseUp(ByVal e As System.Windows.Forms.MouseEventArgs)
        MyBase.OnMouseUp(e)
        If mStateText <> "" Then
            Return
        End If
        If mDragging Then
            mDragging = False
            mDownY = e.Y
            mDownScroll = mScrollTop
            Invalidate()
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
        ' 点击播放按钮（未拖动）→ 触发播放。
        Dim contentY As Integer = e.Y + mScrollTop
        If contentY >= mPlayRect.Top AndAlso contentY <= mPlayRect.Bottom Then
            If mPlayRect.Width > 0 Then
                RaiseEvent PlayClick(Me, System.EventArgs.Empty)
            End If
        End If
    End Sub

    Protected Overrides Sub Dispose(ByVal disposing As Boolean)
        If disposing Then
            If mFlingTimer IsNot Nothing Then
                mFlingTimer.Enabled = False
                mFlingTimer.Dispose()
                mFlingTimer = Nothing
            End If
            DisposeBuffer()
            If mFontTitle IsNot Nothing Then
                mFontTitle.Dispose()
                mFontTitle = Nothing
            End If
            If mFontBody IsNot Nothing Then
                mFontBody.Dispose()
                mFontBody = Nothing
            End If
            If mBrushWhite IsNot Nothing Then
                mBrushWhite.Dispose()
                mBrushWhite = Nothing
            End If
            If mBrushTitle IsNot Nothing Then
                mBrushTitle.Dispose()
                mBrushTitle = Nothing
            End If
            If mBrushSub IsNot Nothing Then
                mBrushSub.Dispose()
                mBrushSub = Nothing
            End If
            If mBrushStats IsNot Nothing Then
                mBrushStats.Dispose()
                mBrushStats = Nothing
            End If
            If mBrushPlaceholder IsNot Nothing Then
                mBrushPlaceholder.Dispose()
                mBrushPlaceholder = Nothing
            End If
            If mBrushPlay IsNot Nothing Then
                mBrushPlay.Dispose()
                mBrushPlay = Nothing
            End If
            If mBrushPlayText IsNot Nothing Then
                mBrushPlayText.Dispose()
                mBrushPlayText = Nothing
            End If
            mCover = Nothing
        End If
        MyBase.Dispose(disposing)
    End Sub

End Class
