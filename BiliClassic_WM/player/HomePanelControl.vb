' 主页自绘控件（.NET CF 2.0 兼容）。
' 双缓冲：绘制全在内存位图完成，再一次性 DrawImage 到屏幕，消除重绘闪烁。
' 采用与 RecListControl 相同的已验证方案。
Public Class HomePanelControl
    Inherits System.Windows.Forms.Panel

    ' 绘制回调：由 MainForm 提供主页绘制逻辑。
    Public Delegate Sub PaintCallback(ByVal g As System.Drawing.Graphics, ByVal w As Integer, ByVal h As Integer)
    Public OnDraw As PaintCallback = Nothing

    Private mBackBuf As System.Drawing.Bitmap = Nothing
    Private mBackG As System.Drawing.Graphics = Nothing
    Private mBufW As Integer = 0
    Private mBufH As Integer = 0

    Public Sub New()
        MyBase.New()
        Me.BackColor = System.Drawing.Color.FromArgb(&H1A, &H3A, &H63)
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

    Protected Overrides Sub OnPaintBackground(ByVal e As System.Windows.Forms.PaintEventArgs)
    End Sub

    Protected Overrides Sub OnPaint(ByVal e As System.Windows.Forms.PaintEventArgs)
        Try
            Dim w As Integer = Me.ClientSize.Width
            Dim h As Integer = Me.ClientSize.Height
            If w <= 0 OrElse h <= 0 Then
                Return
            End If
            UpdateBackBuffer()
            If mBackBuf Is Nothing Then
                Return
            End If
            ' 一次性把内存位图画到屏幕。
            Dim srcR As New System.Drawing.Rectangle(0, 0, mBackBuf.Width, mBackBuf.Height)
            Dim dstR As New System.Drawing.Rectangle(0, 0, mBackBuf.Width, mBackBuf.Height)
            e.Graphics.DrawImage(mBackBuf, dstR, srcR, System.Drawing.GraphicsUnit.Pixel)
        Catch ex As Exception
        End Try
    End Sub

    ' 把当前场景绘制到双缓冲位图（OnPaint 与 RenderToBitmap 共用，隐藏状态下也可靠）。
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
        ' 绘制到内存位图。
        mBackG.Clear(Me.BackColor)
        If OnDraw IsNot Nothing Then
            OnDraw(mBackG, w, h)
        End If
    End Sub

    ' 导出当前场景为 Bitmap（供过渡动画快照，无需控件可见）。
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
            DisposeBuffer()
        End If
        MyBase.Dispose(disposing)
    End Sub
End Class
