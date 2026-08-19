Imports System
Imports System.Drawing
Imports System.Drawing.Imaging
Imports System.IO
Imports System.Reflection

Imports BiliClassic_WM.PlatformAPI

Namespace AlphaMobileControls
    ''' <summary>
    ''' Alpha Image class supporting both alpha channel from image and alpha blending.
    ''' The alpha channel is handled through IImage COM API and the alpha blending through AlphaBlend WM5 API.
    ''' This class also provides helper creation methods to load an image from a file, a resource or a binary stream.
    ''' </summary>
    Public Class AlphaImage
        Implements IDisposable

        ' The IImage, for alpha channel.
        Private _image As IImage

        ' A buffer used for better performances when performing the alpha blending.
        Private _buffer As Bitmap

        ' The Alpha value.
        Private _alpha As Integer

        ' 桌面回退位图：设备端走 IImage + AlphaBlend，桌面端直接用 GDI+ DrawImage。
        Private _fallbackBitmap As Bitmap

        ' 设备端：CreateImageFromBuffer 借用缓冲指针（BufferDisposalFlagNone），须保引用防 GC 回收。
        Private _bufferBytes As Byte()

        ''' <summary>
        ''' The IImage instance, handling alpha channel (PNG or GIF file).
        ''' </summary>
        Public Property Image() As IImage
            Get
                Return _image
            End Get
            Set(ByVal value As IImage)
                If _image Is value Then
                    Return
                End If

                _image = value

                If _buffer IsNot Nothing Then
                    _buffer.Dispose()
                End If
            End Set
        End Property

        ''' <summary>
        ''' Opacity value used for Alpha Blending.
        ''' If no opacity is required, sets this parameter to 0 (default).
        ''' </summary>
        Public Property Alpha() As Integer
            Get
                Return _alpha
            End Get
            Set(ByVal value As Integer)
                _alpha = value
            End Set
        End Property

        ''' <summary>
        ''' Creates a new AlphaImage from the given file name.
        ''' </summary>
        Public Shared Function CreateFromFile(ByVal imageFileName As String) As AlphaImage
            Dim alphaImage As New AlphaImage()
            If IsDesktop() Then
                alphaImage._fallbackBitmap = New Bitmap(imageFileName)
            Else
                Dim factory As IImagingFactory = CreateFactory()
                factory.CreateImageFromFile(imageFileName, alphaImage._image)
            End If
            Return alphaImage
        End Function

        ''' <summary>
        ''' Creates a new AlphaImage from the given resource name.
        ''' </summary>
        Public Shared Function CreateFromResource(ByVal imageResourceName As String) As AlphaImage
            Dim stream As Stream = Assembly.GetCallingAssembly().GetManifestResourceStream(imageResourceName)
            If stream Is Nothing Then
                Return Nothing
            End If
            Dim ms As New MemoryStream()
            Dim buf(4095) As Byte
            Dim n As Integer = stream.Read(buf, 0, buf.Length)
            While n > 0
                ms.Write(buf, 0, n)
                n = stream.Read(buf, 0, buf.Length)
            End While
            stream.Close()
            ms.Position = 0
            Return CreateFromStream(ms)
        End Function

        ''' <summary>
        ''' Creates a new AlphaImage from the given memory stream.
        ''' </summary>
        Public Shared Function CreateFromStream(ByVal stream As MemoryStream) As AlphaImage
            Dim alphaImage As New AlphaImage()
            If IsDesktop() Then
                stream.Position = 0
                alphaImage._fallbackBitmap = New Bitmap(stream)
            Else
                ' 设备端优先走 IImage（逐像素 alpha）。若设备缺少 imaging 库/COM 失败，
                ' 回退到普通 Bitmap（GDI+，透明区域可能显示为背景色，但保证可见）。
                Try
                    Dim factory As IImagingFactory = CreateFactory()
                    ' CF 2.0 的 MemoryStream 无公开 GetBuffer()，改用 ToArray()（返回新数组，须保引用防 GC）。
                    alphaImage._bufferBytes = stream.ToArray()
                    Dim pbBuf As Byte() = alphaImage._bufferBytes
                    Dim cbBuf As Integer = pbBuf.Length
                    factory.CreateImageFromBuffer(pbBuf, cbBuf, BufferDisposalFlag.BufferDisposalFlagNone, alphaImage._image)
                Catch ex As Exception
                    alphaImage._image = Nothing
                    alphaImage._bufferBytes = Nothing
                End Try
                If alphaImage._image Is Nothing Then
                    ' 回退：GDI+ 解码。
                    Try
                        stream.Position = 0
                        alphaImage._fallbackBitmap = New Bitmap(stream)
                    Catch ex2 As Exception
                        alphaImage._fallbackBitmap = Nothing
                    End Try
                End If
            End If
            Return alphaImage
        End Function

        Private Shared Function CreateFactory() As IImagingFactory
            Dim factory As IImagingFactory = CType(Activator.CreateInstance(Type.GetTypeFromCLSID(New Guid("327ABDA8-072B-11D3-9D7B-0000F81EF32E"))), IImagingFactory)
            Return factory
        End Function

        ' 桌面运行时（Win32NT）无 coredll.dll / CE Imaging，改用 GDI+ 直接绘制。
        Private Shared Function IsDesktop() As Boolean
            Try
                Return (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)
            Catch ex As Exception
                Return False
            End Try
        End Function

        ''' <summary>
        ''' Cleaning.
        ''' </summary>
        Public Sub Dispose() Implements IDisposable.Dispose
            If _buffer IsNot Nothing Then
                _buffer.Dispose()
            End If
            _buffer = Nothing
            _image = Nothing
            _bufferBytes = Nothing
            If _fallbackBitmap IsNot Nothing Then
                _fallbackBitmap.Dispose()
            End If
            _fallbackBitmap = Nothing
        End Sub

        ''' <summary>
        ''' Draws this image into the given Graphics object.
        ''' If the image contains alpha channel, it will used with the buffer.
        ''' If an Alpha value is set (&gt; 0), the image will also be alpha blended with the buffer.
        ''' </summary>
        ''' <param name="gx">The Graphics object to use for drawing.</param>
        ''' <param name="bounds">The bounds where to draw.</param>
        Public Sub Draw(ByVal gx As Graphics, ByVal bounds As Rectangle)
            If IsDesktop() Then
                DrawDesktop(gx, bounds)
                Return
            End If
            If _image Is Nothing Then
                ' 设备端回退：IImage 不可用但有 GDI+ 位图时直接绘制。
                If _fallbackBitmap IsNot Nothing Then
                    DrawDesktop(gx, bounds)
                End If
                Return
            End If

            Dim imgInfo As ImageInfo
            _image.GetImageInfo(imgInfo)

            If _alpha = 0 Then
                ' Draw the image, with alpha channel if any
                Dim hdcDest As IntPtr = gx.GetHdc()
                Dim dstRect As New Rectangle(bounds.X, bounds.Y, CInt(imgInfo.Width) + bounds.X, CInt(imgInfo.Height) + bounds.Y)
                _image.Draw(hdcDest, dstRect, IntPtr.Zero)
                gx.ReleaseHdc(hdcDest)
            Else
                ' Creates buffer on demand
                If _buffer Is Nothing Then
                    _buffer = New Bitmap(CInt(imgInfo.Width), CInt(imgInfo.Height), PixelFormat.Format32bppRgb)
                End If

                Using gxBuffer As Graphics = Graphics.FromImage(_buffer)
                    Dim hdcBuffer As IntPtr = gxBuffer.GetHdc()
                    Dim hdcOrg As IntPtr = gx.GetHdc()

                    ' Copy original DC into Buffer DC to see the background through the image
                    DrawingAPI.BitBlt(hdcBuffer, 0, 0, CInt(imgInfo.Width), CInt(imgInfo.Height), hdcOrg, bounds.X, bounds.Y, DrawingAPI.SRCCOPY)

                    ' Draw the image, with alpha channel if any
                    Dim dstRect As New Rectangle(0, 0, CInt(imgInfo.Width), CInt(imgInfo.Height))
                    _image.Draw(hdcBuffer, dstRect, IntPtr.Zero)

                    ' Alpha blend image
                    Dim blendFunction As New BlendFunction()
                    blendFunction.BlendOp = CType(BlendOperation.AC_SRC_OVER, Byte) ' Only supported blend operation
                    blendFunction.BlendFlags = CType(BlendFlags.Zero, Byte) ' Documentation says put 0 here
                    blendFunction.SourceConstantAlpha = CType(_alpha, Byte) ' Constant alpha factor
                    blendFunction.AlphaFormat = CType(0, Byte) ' Don't look for per pixel alpha

                    DrawingAPI.AlphaBlend(hdcOrg, bounds.X, bounds.Y, bounds.Width, bounds.Height, hdcBuffer, 0, 0, CInt(imgInfo.Width), CInt(imgInfo.Height), blendFunction)

                    gx.ReleaseHdc(hdcOrg) ' Required cleanup to GetHdc()
                    gxBuffer.ReleaseHdc(hdcBuffer) ' Required cleanup to GetHdc()
                End Using
            End If
        End Sub

        ' 桌面回退：GDI+ DrawImage 直接绘制（保留 PNG 自身透明通道）。
        ' 常量 Alpha（Alpha 属性）仅设备端 AlphaBlend 生效，桌面端预览忽略。
        Private Function DrawDesktop(ByVal gx As Graphics, ByVal bounds As Rectangle) As Boolean
            If _fallbackBitmap Is Nothing Then
                Return False
            End If
            Try
                Dim srcRect As New Rectangle(0, 0, _fallbackBitmap.Width, _fallbackBitmap.Height)
                Dim dstRect As New Rectangle(bounds.X, bounds.Y, bounds.Width, bounds.Height)
                gx.DrawImage(_fallbackBitmap, dstRect, srcRect, GraphicsUnit.Pixel)
                Return True
            Catch ex As Exception
                Return False
            End Try
        End Function

        ' 按目标矩形缩放绘制（保留 PNG 自身透明通道）。
        ' 设备端：IImage.Draw 支持缩放 + 逐像素 alpha 合成；桌面端：GDI+ DrawImage 回退。
        ' 返回是否成功（失败时调用方可回退到其它绘制方案）。
        Public Function DrawStretched(ByVal gx As Graphics, ByVal bounds As Rectangle) As Boolean
            If IsDesktop() Then
                Return DrawDesktop(gx, bounds)
            End If
            If _image Is Nothing Then
                ' 设备端回退：IImage 不可用但有 GDI+ 位图时直接绘制。
                If _fallbackBitmap IsNot Nothing Then
                    Return DrawDesktop(gx, bounds)
                End If
                Return False
            End If
            Try
                Dim hdcDest As IntPtr = gx.GetHdc()
                ' IImage.Draw 期望 RECT(left, top, right, bottom)，而 Rectangle 传参被当作 left/top/right/bottom。
                ' 必须用 X+Width、Y+Height 作为 right/bottom，否则非原点绘制会错位。
                Dim dstRect As New Rectangle(bounds.X, bounds.Y, bounds.X + bounds.Width, bounds.Y + bounds.Height)
                _image.Draw(hdcDest, dstRect, IntPtr.Zero)
                gx.ReleaseHdc(hdcDest)
                Return True
            Catch ex As Exception
                Return False
            End Try
        End Function
    End Class
End Namespace
