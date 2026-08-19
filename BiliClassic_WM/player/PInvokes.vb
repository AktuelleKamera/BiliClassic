' DirectShow 原生 P/Invoke（VB.NET，兼容 .NET CF 2.0）。
' 来源：DirectShow.NetCF PInvoke.cs（转译，仅播放所需部分）。
Imports System.Runtime.InteropServices

Public NotInheritable Class PInvokes
    Private Sub New()
    End Sub

    <DllImport("ole32.dll")> _
    Public Shared Function CoCreateInstance( _
        ByRef rclsid As Guid, _
        ByVal pUnkOuter As IntPtr, _
        ByVal dwClsContext As Integer, _
        ByRef riid As Guid, _
        <MarshalAs(UnmanagedType.Interface)> ByRef pv As Object) As Integer
    End Function

    <DllImport("coredll.dll")> _
    Public Shared Function GetClientRect(ByVal hWnd As IntPtr, ByRef lpRect As Rect) As Integer
    End Function
End Class
