Imports System
Imports System.Drawing

Namespace PlatformAPI

    ' These structures, enumerations and p/invoke signatures come from
    ' wingdi.h in the Windows Mobile 5.0 Pocket PC SDK

    Public Structure BlendFunction
        Public BlendOp As Byte
        Public BlendFlags As Byte
        Public SourceConstantAlpha As Byte
        Public AlphaFormat As Byte
    End Structure

    Public Enum BlendOperation As Byte
        AC_SRC_OVER = &H0
    End Enum

    Public Enum BlendFlags As Byte
        Zero = &H0
    End Enum

    Public Enum SourceConstantAlpha As Byte
        Transparent = &H0
        Opaque = &HFF
    End Enum

    Public Enum AlphaFormat As Byte
        AC_SRC_ALPHA = &H1
    End Enum

    Public Module DrawingAPI
        Declare Function AlphaBlend Lib "coredll.dll" (ByVal hdcDest As IntPtr, ByVal nXDest As Int32, ByVal nYDest As Int32, ByVal nWidthDst As Int32, ByVal nHeightDst As Int32, ByVal hdcSrc As IntPtr, ByVal nXSrc As Int32, ByVal nYSrc As Int32, ByVal nWidthSrc As Int32, ByVal nHeightSrc As Int32, ByVal blendFunction As BlendFunction) As Boolean
        Declare Function BitBlt Lib "coredll.dll" (ByVal hdcDest As IntPtr, ByVal nXDest As Int32, ByVal nYDest As Int32, ByVal nWidth As Int32, ByVal nHeight As Int32, ByVal hdcSrc As IntPtr, ByVal nXSrc As Int32, ByVal nYSrc As Int32, ByVal dwRop As Int32) As Boolean

        Public Const SRCCOPY As Int32 = &HCC0020
    End Module

    Public Module AlphaBlend
        Public Sub DrawAlpha(ByVal gx As Graphics, ByVal image As Bitmap, ByVal transp As Byte, ByVal x As Integer, ByVal y As Integer)
            Using gxSrc As Graphics = Graphics.FromImage(image)
                Dim hdcDst As IntPtr = gx.GetHdc()
                Dim hdcSrc As IntPtr = gxSrc.GetHdc()
                Dim blendFunction As New BlendFunction()
                blendFunction.BlendOp = CType(BlendOperation.AC_SRC_OVER, Byte)
                blendFunction.BlendFlags = CType(BlendFlags.Zero, Byte)
                blendFunction.SourceConstantAlpha = transp
                blendFunction.AlphaFormat = CType(0, Byte)
                DrawingAPI.AlphaBlend(hdcDst, x, y, image.Width, image.Height, hdcSrc, 0, 0, image.Width, image.Height, blendFunction)
                gx.ReleaseHdc(hdcDst)
                gxSrc.ReleaseHdc(hdcSrc)
            End Using
        End Sub
    End Module
End Namespace
