' DirectShow 结构体（VB.NET，兼容 .NET CF 2.0）。
' 来源：DirectShow.NetCF DirectShowStructs.cs（转译，仅播放所需部分）。
' 类型名保持与原 C# 工程一致（DirectShowImports.vb 引用这些类型）。
Imports System.Runtime.InteropServices

<StructLayout(LayoutKind.Sequential)> _
Public Structure Rect
    Public Left As Integer
    Public Top As Integer
    Public Right As Integer
    Public Bottom As Integer
End Structure

<StructLayout(LayoutKind.Sequential)> _
Public Structure Size
    Public Width As Integer
    Public Height As Integer
End Structure

<StructLayout(LayoutKind.Sequential)> _
Public Structure PinInfo
    Public filter As IntPtr
    Public dir As PinDirection
    <MarshalAs(UnmanagedType.ByValTStr, SizeConst:=128)> _
    Public name As String
End Structure

<StructLayout(LayoutKind.Sequential)> _
Public Class AMMediaType
    Public majorType As Guid
    Public subType As Guid
    Public fixedSizeSamples As Integer
    Public temporalCompression As Integer
    Public sampleSize As Integer
    Public formatType As Guid
    Public unkPtr As IntPtr
    Public formatSize As Integer
    Public formatPtr As IntPtr
End Class
