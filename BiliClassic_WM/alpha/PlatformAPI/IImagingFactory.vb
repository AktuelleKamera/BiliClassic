Imports System
Imports System.Drawing
Imports System.Runtime.InteropServices

Namespace PlatformAPI

    ' Pulled from gdipluspixelformats.h in the Windows Mobile 5.0 Pocket PC SDK
    Public Enum PixelFormatID As Integer
        PixelFormatIndexed = &H10000 ' Indexes into a palette
        PixelFormatGDI = &H20000 ' Is a GDI-supported format
        PixelFormatAlpha = &H40000 ' Has an alpha component
        PixelFormatPAlpha = &H80000 ' Pre-multiplied alpha
        PixelFormatExtended = &H100000 ' Extended color 16 bits/channel
        PixelFormatCanonical = &H200000

        PixelFormatUndefined = 0
        PixelFormatDontCare = 0

        PixelFormat1bppIndexed = (1 Or (1 << 8) Or PixelFormatIndexed Or PixelFormatGDI)
        PixelFormat4bppIndexed = (2 Or (4 << 8) Or PixelFormatIndexed Or PixelFormatGDI)
        PixelFormat8bppIndexed = (3 Or (8 << 8) Or PixelFormatIndexed Or PixelFormatGDI)
        PixelFormat16bppRGB555 = (5 Or (16 << 8) Or PixelFormatGDI)
        PixelFormat16bppRGB565 = (6 Or (16 << 8) Or PixelFormatGDI)
        PixelFormat16bppARGB1555 = (7 Or (16 << 8) Or PixelFormatAlpha Or PixelFormatGDI)
        PixelFormat24bppRGB = (8 Or (24 << 8) Or PixelFormatGDI)
        PixelFormat32bppRGB = (9 Or (32 << 8) Or PixelFormatGDI)
        PixelFormat32bppARGB = (10 Or (32 << 8) Or PixelFormatAlpha Or PixelFormatGDI Or PixelFormatCanonical)
        PixelFormat32bppPARGB = (11 Or (32 << 8) Or PixelFormatAlpha Or PixelFormatPAlpha Or PixelFormatGDI)
        PixelFormat48bppRGB = (12 Or (48 << 8) Or PixelFormatExtended)
        PixelFormat64bppARGB = (13 Or (64 << 8) Or PixelFormatAlpha Or PixelFormatCanonical Or PixelFormatExtended)
        PixelFormat64bppPARGB = (14 Or (64 << 8) Or PixelFormatAlpha Or PixelFormatPAlpha Or PixelFormatExtended)
        PixelFormatMax = 15
    End Enum

    ' Pulled from imaging.h in the Windows Mobile 5.0 Pocket PC SDK
    Public Enum BufferDisposalFlag As Integer
        BufferDisposalFlagNone
        BufferDisposalFlagGlobalFree
        BufferDisposalFlagCoTaskMemFree
        BufferDisposalFlagUnmapView
    End Enum

    ' Pulled from imaging.h in the Windows Mobile 5.0 Pocket PC SDK
    Public Enum InterpolationHint As Integer
        InterpolationHintDefault
        InterpolationHintNearestNeighbor
        InterpolationHintBilinear
        InterpolationHintAveraging
        InterpolationHintBicubic
    End Enum

    ' Pulled from gdiplusimaging.h in the Windows Mobile 5.0 Pocket PC SDK
    Public Structure BitmapData
        Public Width As Integer
        Public Height As Integer
        Public Stride As Integer
        Public PixelFormat As PixelFormatID
        Public Scan0 As IntPtr
        Public Reserved As IntPtr
    End Structure

    ' Pulled from imaging.h in the Windows Mobile 5.0 Pocket PC SDK
    Public Structure ImageInfo
        Public GuidPart1 As Integer ' I am being lazy here, I don't care at this point about the RawDataFormat GUID
        Public GuidPart2 As Integer ' I am being lazy here, I don't care at this point about the RawDataFormat GUID
        Public GuidPart3 As Integer ' I am being lazy here, I don't care at this point about the RawDataFormat GUID
        Public GuidPart4 As Integer ' I am being lazy here, I don't care at this point about the RawDataFormat GUID
        Public pixelFormat As PixelFormatID
        Public Width As Integer
        Public Height As Integer
        Public TileWidth As Integer
        Public TileHeight As Integer
        Public Xdpi As Double
        Public Ydpi As Double
        Public Flags As Integer
    End Structure

    ' Pulled from imaging.h in the Windows Mobile 5.0 Pocket PC SDK
    <ComVisible(True), ComImport(), _
    Guid("327ABDA7-072B-11D3-9D7B-0000F81EF32E"), _
    InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
    Public Interface IImagingFactory
        Function CreateImageFromStream() As Integer ' This is a place holder, note the lack of arguments
        Function CreateImageFromFile(ByVal filename As String, <Out()> ByRef image As IImage) As Integer
        ' We need the MarshalAs attribute here to keep COM interop from sending the buffer down as a Safe Array.
        Function CreateImageFromBuffer(<MarshalAs(UnmanagedType.LPArray)> ByVal buffer As Byte(), ByVal size As Integer, ByVal disposalFlag As BufferDisposalFlag, <Out()> ByRef image As IImage) As Integer
        Function CreateNewBitmap(ByVal width As Integer, ByVal height As Integer, ByVal pixelFormat As PixelFormatID, <Out()> ByRef bitmap As IBitmapImage) As Integer
        Function CreateBitmapFromImage(ByVal image As IImage, ByVal width As Integer, ByVal height As Integer, ByVal pixelFormat As PixelFormatID, ByVal hints As InterpolationHint, <Out()> ByRef bitmap As IBitmapImage) As Integer
        Function CreateBitmapFromBuffer() As Integer ' This is a place holder, note the lack of arguments
        Function CreateImageDecoder() As Integer ' This is a place holder, note the lack of arguments
        Function CreateImageEncoderToStream() As Integer ' This is a place holder, note the lack of arguments
        Function CreateImageEncoderToFile() As Integer ' This is a place holder, note the lack of arguments
        Function GetInstalledDecoders() As Integer ' This is a place holder, note the lack of arguments
        Function GetInstalledEncoders() As Integer ' This is a place holder, note the lack of arguments
        Function InstallImageCodec() As Integer ' This is a place holder, note the lack of arguments
        Function UninstallImageCodec() As Integer ' This is a place holder, note the lack of arguments
    End Interface

    ' Pulled from imaging.h in the Windows Mobile 5.0 Pocket PC SDK
    <ComVisible(True), ComImport(), _
    Guid("327ABDA9-072B-11D3-9D7B-0000F81EF32E"), _
    InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
    Public Interface IImage
        Function GetPhysicalDimension(<Out()> ByRef size As System.Drawing.Size) As Integer
        Function GetImageInfo(<Out()> ByRef info As ImageInfo) As Integer
        Function SetImageFlags(ByVal flags As Integer) As Integer
        Function Draw(ByVal hdc As IntPtr, ByRef dstRect As Rectangle, ByVal nullParam As IntPtr) As Integer ' "Correct" declaration: uint Draw(IntPtr hdc, ref Rectangle dstRect, ref Rectangle srcRect);
        Function PushIntoSink() As Integer ' This is a place holder, note the lack of arguments
        Function GetThumbnail(ByVal thumbWidth As Integer, ByVal thumbHeight As Integer, <Out()> ByRef thumbImage As IImage) As Integer
    End Interface

    ' Pulled from imaging.h in the Windows Mobile 5.0 Pocket PC SDK
    <ComVisible(True), ComImport(), _
    Guid("327ABDAA-072B-11D3-9D7B-0000F81EF32E"), _
    InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
    Public Interface IBitmapImage
        Function GetSize(<Out()> ByRef size As System.Drawing.Size) As Integer
        Function GetPixelFormatID(<Out()> ByRef pixelFormat As PixelFormatID) As Integer
        Function LockBits(ByRef rect As Rectangle, ByVal flags As Integer, ByVal pixelFormat As PixelFormatID, <Out()> ByRef lockedBitmapData As BitmapData) As Integer
        Function UnlockBits(ByRef lockedBitmapData As BitmapData) As Integer
        Function GetPalette() As Integer ' This is a place holder, note the lack of arguments
        Function SetPalette() As Integer ' This is a place holder, note the lack of arguments
    End Interface
End Namespace
