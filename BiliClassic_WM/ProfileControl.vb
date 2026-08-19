' 个人中心自绘控件（.NET CF 2.0 兼容）。
' 双缓冲：整页绘制到内存位图，OnPaint 单次 DrawImage，杜绝闪烁/撕裂。
' 两种模式：
'   - 已登录：左上头像 + 右侧用户名/UID/硬币/VIP/签名等文字，底部"退出登录"按钮。
'   - 未登录：居中"请扫码登录"提示 + 二维码（SetQrImage 传入），提示用手机扫码。
' 用法：
'   ctrl.ShowStateText(msg)                 ' 加载中/错误
'   ctrl.ShowNotLoggedIn()                  ' 未登录视图（随后 SetQrImage 填二维码）
'   ctrl.SetUserData(avatar, name, uid, coins, vipText, sign)   ' 已登录视图
'   ctrl.SetQrImage(bmp)                    ' 设置二维码位图
'   ctrl.LogoutClick 事件                   ' 点击退出登录
'   ctrl.RenderToBitmap()                   ' 供过渡动画快照

Public Class ProfileControl
    Inherits System.Windows.Forms.Control

    Public Event LogoutClick As EventHandler

    Private mMode As Integer = 0 ' 0=状态文本 1=未登录 2=已登录
    Private mStateText As String = ""

    ' 已登录数据。
    Private mAvatar As System.Drawing.Bitmap = Nothing
    Private mDefaultAvatar As System.Drawing.Bitmap = Nothing
    Private mName As String = ""
    Private mUid As String = ""
    Private mCoins As String = ""
    Private mLevel As String = "" ' 等级，如 "Lv6"
    Private mVipTag As String = "" ' 大会员标记（附加到名字后），如 "大会员"
    Private mSign As String = ""
    Private mIsChinese As Boolean = True

    ' 二维码。
    Private mQr As System.Drawing.Bitmap = Nothing
    Private mQrHint As String = "" ' 未登录视图二维码下方提示（登录状态）。

    ' 状态动画：小电视抖动帧（AlphaImage，透明）。
    Private mStateFrames As AlphaMobileControls.AlphaImage() = Nothing
    Private mStateFrameStreams As System.IO.MemoryStream() = Nothing
    Private mStateFrameIndex As Integer = 0
    Private mStateTimer As System.Windows.Forms.Timer = Nothing

    ' 字体。
    Private mFontTitle As System.Drawing.Font = Nothing
    Private mFontBody As System.Drawing.Font = Nothing
    Private mFontSub As System.Drawing.Font = Nothing

    ' 缓存画刷。
    Private mBrushWhite As System.Drawing.SolidBrush = Nothing
    Private mBrushTitle As System.Drawing.SolidBrush = Nothing
    Private mBrushSub As System.Drawing.SolidBrush = Nothing
    Private mBrushPink As System.Drawing.SolidBrush = Nothing
    Private mBrushAvatarBg As System.Drawing.SolidBrush = Nothing

    ' 退出登录按钮矩形（命中测试）。
    Private mLogoutRect As System.Drawing.Rectangle = New System.Drawing.Rectangle(0, 0, 0, 0)

    ' 双缓冲。
    Private mBackBuf As System.Drawing.Bitmap = Nothing
    Private mBackG As System.Drawing.Graphics = Nothing
    Private mBufW As Integer = 0
    Private mBufH As Integer = 0

    ' 桌面运行时检测。
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
        mFontTitle = GetUiFont(14.0!, True)
        mFontBody = GetUiFont(11.0!, False)
        mFontSub = GetUiFont(9.0!, False)
        mBrushWhite = New System.Drawing.SolidBrush(System.Drawing.Color.White)
        mBrushTitle = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H22, &H22, &H22))
        mBrushSub = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&H88, &H88, &H88))
        mBrushPink = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HD8, &H6D, &HA5))
        mBrushAvatarBg = New System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(&HE8, &HE8, &HE8))
        Me.BackColor = System.Drawing.Color.White
        mStateTimer = New System.Windows.Forms.Timer()
        AddHandler mStateTimer.Tick, AddressOf OnStateTick
        mStateTimer.Interval = 100
        mStateTimer.Enabled = False
        LoadDefaultAvatar()
        LoadStateFrames()
    End Sub

    ' 加载默认头像（bili_default_avatar.png，头像加载失败时显示）。
    Private Sub LoadDefaultAvatar()
        Try
            Dim s As System.IO.Stream = System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceStream("BiliClassic_WM.bili_default_avatar.png")
            If s IsNot Nothing Then
                mDefaultAvatar = New System.Drawing.Bitmap(s)
            End If
        Catch ex As Exception
            mDefaultAvatar = Nothing
        End Try
    End Sub

    ' 加载小电视状态动画帧（bili_anim_tv_chan_1/3/5/7/9，透明）。
    Private Sub LoadStateFrames()
        Try
            Dim names As String() = New String() {"BiliClassic_WM.bili_anim_tv_chan_1.png", "BiliClassic_WM.bili_anim_tv_chan_3.png", "BiliClassic_WM.bili_anim_tv_chan_5.png", "BiliClassic_WM.bili_anim_tv_chan_7.png", "BiliClassic_WM.bili_anim_tv_chan_9.png"}
            Dim tmp(4) As AlphaMobileControls.AlphaImage
            Dim tmpStreams(4) As System.IO.MemoryStream
            Dim loaded As Integer = 0
            Dim asm As System.Reflection.Assembly = System.Reflection.Assembly.GetExecutingAssembly()
            Dim i As Integer
            For i = 0 To names.Length - 1
                Try
                    Dim s As System.IO.Stream = asm.GetManifestResourceStream(names(i))
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

    ' 状态动画 tick：切帧 + 重绘。
    Private Sub OnStateTick(ByVal sender As Object, ByVal e As System.EventArgs)
        If mMode <> 0 Then
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

    ' 停止状态动画。
    Private Sub StopStateAnimation()
        If mStateTimer IsNot Nothing Then
            mStateTimer.Enabled = False
        End If
    End Sub

    ' 显示状态文本（加载中/错误）。加载类显示小电视抖动动画。
    Public Sub ShowStateText(ByVal text As String)
        ShowStateText(text, True)
    End Sub

    ' 显示状态文本但小电视静止（失败状态：请求失败时动画停下）。
    Public Sub ShowStateTextStatic(ByVal text As String)
        ShowStateText(text, False)
    End Sub

    Private Sub ShowStateText(ByVal text As String, ByVal animate As Boolean)
        mMode = 0
        mStateText = text
        If mStateFrames IsNot Nothing Then
            mStateFrameIndex = 0
            mStateTimer.Enabled = animate
        End If
        Invalidate()
    End Sub

    ' 未登录视图：提示扫码登录。
    Public Sub ShowNotLoggedIn()
        mMode = 1
        mQr = Nothing
        mQrHint = ""
        StopStateAnimation()
        Invalidate()
    End Sub

    ' 未登录视图下更新二维码下方提示文字（不切换模式，避免覆盖二维码）。
    Public Sub SetLoginHint(ByVal text As String)
        mQrHint = text
        If mMode = 1 Then
            Invalidate()
        End If
    End Sub

    ' 是否处于未登录（扫码）视图。
    Public ReadOnly Property IsLoginMode() As Boolean
        Get
            Return (mMode = 1)
        End Get
    End Property

    ' 设置二维码位图（未登录视图用）。
    Public Sub SetQrImage(ByVal bmp As System.Drawing.Bitmap)
        mQr = bmp
        Invalidate()
    End Sub

    ' 已登录视图：头像 + 文字 + 退出按钮。
    Public Sub SetUserData(ByVal avatar As System.Drawing.Bitmap, ByVal name As String, ByVal uid As String, ByVal coins As String, ByVal level As String, ByVal vipTag As String, ByVal sign As String, ByVal isChinese As Boolean)
        mMode = 2
        mAvatar = avatar
        mName = name
        mUid = uid
        mCoins = coins
        mLevel = level
        mVipTag = vipTag
        mSign = sign
        mIsChinese = isChinese
        StopStateAnimation()
        Invalidate()
    End Sub

    Public Sub ClearAvatar()
        mAvatar = Nothing
        Invalidate()
    End Sub

    ' 键盘确认键：已登录视图下触发退出登录（等同点击按钮）。
    Public Sub PerformAction()
        If mMode = 2 Then
            RaiseEvent LogoutClick(Me, System.EventArgs.Empty)
        End If
    End Sub

    ' 双缓冲。
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

        If mMode = 0 Then
            DrawStateText(g, w, h)
            Return
        End If

        If mMode = 1 Then
            DrawNotLoggedIn(g, w, h)
            Return
        End If

        DrawLoggedIn(g, w, h)
    End Sub

    ' 未登录视图：标题 + 二维码 + 提示。
    ' 状态文本绘制：居中显示小电视抖动动画，正下方配状态文字。
    Private Sub DrawStateText(ByVal g As System.Drawing.Graphics, ByVal w As Integer, ByVal h As Integer)
        If mStateFrames Is Nothing Then
            g.DrawString(mStateText, mFontSub, mBrushSub, 12, 8)
            Return
        End If

        Dim tvSize As Integer = h \ 3
        If tvSize < 60 Then
            tvSize = 60
        End If
        If tvSize > 140 Then
            tvSize = 140
        End If

        ' 抖动：随帧 index 小幅摆动。
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

        Dim tw As Integer = CInt(g.MeasureString(mStateText, mFontSub).Width)
        Dim textX As Integer = (w - tw) \ 2
        If textX < 4 Then
            textX = 4
        End If
        g.DrawString(mStateText, mFontSub, mBrushSub, textX, top + tvSize + 10)
    End Sub

    Private Sub DrawNotLoggedIn(ByVal g As System.Drawing.Graphics, ByVal w As Integer, ByVal h As Integer)
        Dim title As String = If(mIsChinese, "请扫码登录", "Please scan to login")
        Dim titleH As Integer = CInt(g.MeasureString(title, mFontTitle).Height)
        g.DrawString(title, mFontTitle, mBrushTitle, 12, 10)

        ' 二维码：最大 45% 宽，居中。
        Dim qrMax As Integer = CInt(w * 0.45)
        If qrMax > 260 Then
            qrMax = 260
        End If
        If qrMax > h - 130 Then
            qrMax = h - 130
        End If
        If qrMax < 80 Then
            qrMax = 80
        End If
        Dim qrX As Integer = (w - qrMax) \ 2
        Dim qrY As Integer = 20 + titleH + 14
        If mQr IsNot Nothing Then
            Try
                Dim srcRect As New System.Drawing.Rectangle(0, 0, mQr.Width, mQr.Height)
                Dim dstRect As New System.Drawing.Rectangle(qrX, qrY, qrMax, qrMax)
                g.DrawImage(mQr, dstRect, srcRect, System.Drawing.GraphicsUnit.Pixel)
            Catch ex As Exception
            End Try
        Else
            ' 无二维码：占位框 + 提示。
            g.FillRectangle(mBrushAvatarBg, qrX, qrY, qrMax, qrMax)
            Dim phText As String = If(mIsChinese, "正在获取二维码...", "Fetching QR...")
            Dim phW As Integer = CInt(g.MeasureString(phText, mFontSub).Width)
            g.DrawString(phText, mFontSub, mBrushSub, qrX + (qrMax - phW) \ 2, qrY + (qrMax - 14) \ 2)
        End If

        Dim hint As String = mQrHint
        If hint = "" Then
            hint = If(mIsChinese, "使用 B 站手机 App 扫码登录（180 秒有效）", "Scan with the Bilibili app (valid 180s)")
        End If
        Dim hintW As Integer = CInt(g.MeasureString(hint, mFontSub).Width)
        Dim hintX As Integer = (w - hintW) \ 2
        If hintX < 4 Then
            hintX = 4
        End If
        g.DrawString(hint, mFontSub, mBrushSub, hintX, qrY + qrMax + 12)
    End Sub

    ' 已登录视图：头像左 + 文字右 + 底部退出按钮。
    Private Sub DrawLoggedIn(ByVal g As System.Drawing.Graphics, ByVal w As Integer, ByVal h As Integer)
        Dim margin As Integer = 12
        Dim avatarSize As Integer = 72
        If avatarSize > w \ 4 Then
            avatarSize = w \ 4
        End If
        If avatarSize < 48 Then
            avatarSize = 48
        End If

        ' 头像（方形，底部浅灰底，有图则画图）。
        Dim avaRect As New System.Drawing.Rectangle(margin, 14, avatarSize, avatarSize)
        g.FillRectangle(mBrushAvatarBg, avaRect)
        Dim avaToDraw As System.Drawing.Bitmap = mAvatar
        If avaToDraw Is Nothing Then
            avaToDraw = mDefaultAvatar
        End If
        If avaToDraw IsNot Nothing Then
            Try
                Dim srcRect As New System.Drawing.Rectangle(0, 0, avaToDraw.Width, avaToDraw.Height)
                g.DrawImage(avaToDraw, avaRect, srcRect, System.Drawing.GraphicsUnit.Pixel)
            Catch ex As Exception
            End Try
        End If

        ' 右侧文字。
        Dim textX As Integer = margin + avatarSize + 12
        Dim textW As Integer = w - textX - margin
        If textW < 60 Then
            textW = 60
        End If
        Dim y As Integer = 16
        ' WM 字体行高较大，信息行间距需更大。
        Dim lineStep As Integer = 22
        If Not IsDesktopRuntime() Then
            lineStep = 38
        End If

        ' 名字 + 大会员标记（支持换行：名字过长自动换行，标记紧跟其后）。
        If mName <> "" Then
            Dim fullName As String = mName
            If mVipTag <> "" Then
                fullName &= "  [" & mVipTag & "]"
            End If
            Dim nameRect As New System.Drawing.RectangleF(textX, y, textW, 70)
            g.DrawString(fullName, mFontTitle, mBrushPink, nameRect)
            ' 估算名字块高度（按字宽估算行数，两行以内）。
            Dim nameH As Integer = 40
            Try
                Dim cw As Single = g.MeasureString("测", mFontTitle).Width
                If cw <= 1 Then
                    cw = 16
                End If
                Dim charsPerLine As Integer = CInt(textW / cw)
                If charsPerLine < 1 Then
                    charsPerLine = 1
                End If
                Dim lines As Integer = CInt(Math.Ceiling(fullName.Length / CDbl(charsPerLine)))
                If lines < 1 Then
                    lines = 1
                End If
                Dim lh As Integer = CInt(g.MeasureString("测", mFontTitle).Height)
                nameH = lines * lh + 6
            Catch ex As Exception
                nameH = 40
            End Try
            If nameH > 76 Then
                nameH = 76
            End If
            y += nameH
        End If

        If mUid <> "" Then
            Dim uidTxt As String = "UID: " & mUid
            g.DrawString(uidTxt, mFontBody, mBrushTitle, textX, y)
            y += lineStep
        End If
        If mCoins <> "" Then
            g.DrawString(If(mIsChinese, "硬币: ", "Coins: ") & mCoins, mFontBody, mBrushTitle, textX, y)
            y += lineStep
        End If
        If mLevel <> "" Then
            g.DrawString(If(mIsChinese, "等级: ", "Level: ") & mLevel, mFontBody, mBrushTitle, textX, y)
            y += lineStep
        End If

        ' 签名（多行，换行）。
        If mSign <> "" Then
            Dim signRect As New System.Drawing.RectangleF(textX, y, textW, 60)
            g.DrawString(mSign, mFontSub, mBrushSub, signRect)
        End If

        ' 底部退出登录按钮。
        Dim btnW As Integer = CInt(w * 0.5)
        If btnW > 220 Then
            btnW = 220
        End If
        If btnW < 120 Then
            btnW = 120
        End If
        Dim btnH As Integer = 38
        Dim btnX As Integer = (w - btnW) \ 2
        Dim btnY As Integer = h - btnH - 14
        mLogoutRect = New System.Drawing.Rectangle(btnX, btnY, btnW, btnH)
        g.FillRectangle(mBrushPink, mLogoutRect)
        Dim logoutText As String = If(mIsChinese, "退出登录", "Log out")
        Dim tw As Single = g.MeasureString(logoutText, mFontBody).Width
        Dim th As Single = g.MeasureString(logoutText, mFontBody).Height
        g.DrawString(logoutText, mFontBody, mBrushWhite, btnX + CInt((btnW - tw) / 2), btnY + CInt((btnH - th) / 2))
    End Sub

    ' 触摸：点击退出登录按钮。
    Protected Overrides Sub OnMouseUp(ByVal e As System.Windows.Forms.MouseEventArgs)
        MyBase.OnMouseUp(e)
        If mMode <> 2 Then
            Return
        End If
        If mLogoutRect.Width <= 0 Then
            Return
        End If
        If e.X >= mLogoutRect.Left AndAlso e.X <= mLogoutRect.Right AndAlso e.Y >= mLogoutRect.Top AndAlso e.Y <= mLogoutRect.Bottom Then
            RaiseEvent LogoutClick(Me, System.EventArgs.Empty)
        End If
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

    Protected Overrides Sub Dispose(ByVal disposing As Boolean)
        If disposing Then
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
            If mFontBody IsNot Nothing Then
                mFontBody.Dispose()
                mFontBody = Nothing
            End If
            If mFontSub IsNot Nothing Then
                mFontSub.Dispose()
                mFontSub = Nothing
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
            If mBrushPink IsNot Nothing Then
                mBrushPink.Dispose()
                mBrushPink = Nothing
            End If
            If mBrushAvatarBg IsNot Nothing Then
                mBrushAvatarBg.Dispose()
                mBrushAvatarBg = Nothing
            End If
            mAvatar = Nothing
            mQr = Nothing
            If mDefaultAvatar IsNot Nothing Then
                mDefaultAvatar.Dispose()
                mDefaultAvatar = Nothing
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
