Imports System
Imports System.Drawing
Imports System.Drawing.Imaging
Imports System.Windows.Forms

Namespace AlphaMobileControls
    ''' <summary>
    ''' Helper class for container controls handling alpha channel.
    ''' The controlled control must forward the Resize and Paint events.
    ''' It uses a double buffer to avoid flickering.
    ''' </summary>
    Friend Class AlphaContainer

        ''' <summary> Controlled Control. </summary>
        Private _control As Control

        ''' <summary> Back buffer used for double buffering. </summary>
        Private _backBuffer As Bitmap

        ''' <summary>
        ''' Constructor, the controlled control must be supplied.
        ''' </summary>
        Public Sub New(ByVal control As Control)
            If control Is Nothing Then
                Throw New ArgumentNullException("A valid control must be supplied.")
            End If

            _control = control

            CreateBackBuffer()
        End Sub

        Private Sub CreateBackBuffer()
            If _backBuffer IsNot Nothing Then
                _backBuffer.Dispose()
            End If

            ' The bitmap needs to be created with the 32bpp pixel format for the IImage to do the right thing.
            _backBuffer = New Bitmap(_control.ClientSize.Width, _control.ClientSize.Height, PixelFormat.Format32bppRgb)
        End Sub

        ''' <summary>
        ''' Handles the Resize event to update the back buffer.
        ''' </summary>
        Public Sub OnResize(ByVal e As EventArgs)
            CreateBackBuffer()
        End Sub

        ''' <summary>
        ''' Handles the Paint event, it is where the magic happens ;o)
        ''' </summary>
        Public Sub OnPaint(ByVal e As PaintEventArgs)
            If _backBuffer IsNot Nothing Then
                ' We need a Graphics object on the buffer to get an HDC
                Using gxBuffer As Graphics = Graphics.FromImage(_backBuffer)
                    ' Since we nop'd OnPaintBackground, take care of it here
                    gxBuffer.Clear(_control.BackColor)

                    Dim gxClipBounds As Region = New Region(Rectangle.Ceiling(gxBuffer.ClipBounds))

                    ' Iterates the child control list in reverse order
                    ' to respect the Z-order
                    Dim i As Integer
                    For i = _control.Controls.Count - 1 To 0 Step -1
                        ' Handle controls inheriting AlphaControl only
                        Dim ctrl As AlphaControl = TryCast(_control.Controls(i), AlphaControl)
                        If ctrl Is Nothing Then
                            Continue For
                        End If

                        ' Something to draw?
                        Dim clipRect As Rectangle = Rectangle.Intersect(e.ClipRectangle, ctrl.Bounds)
                        If clipRect.IsEmpty Then
                            Continue For
                        End If

                        ' Clip to the control bounds
                        gxBuffer.Clip = New Region(clipRect)

                        ' Perform the actual drawing
                        ctrl.DrawInternal(gxBuffer)
                    Next

                    ' Restore clip bounds
                    gxBuffer.Clip = gxClipBounds
                End Using

                ' Put the final composed image on screen.
                e.Graphics.DrawImage(_backBuffer, e.ClipRectangle, e.ClipRectangle, GraphicsUnit.Pixel)
            Else
                ' This should never happen, should it?
                e.Graphics.Clear(_control.BackColor)
            End If
        End Sub
    End Class
End Namespace
