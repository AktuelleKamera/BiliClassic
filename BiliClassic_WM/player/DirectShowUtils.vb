' DirectShow 工具类（VB.NET，兼容 .NET CF 2.0）。
' 来源：DirectShow.NetCF DirectShowUtils.cs（转译，仅播放所需部分）。
Imports System.Runtime.InteropServices

' GUID 包装（原 C# 用 LayoutKind.Explicit + FieldOffset，VB 用 Sequential 等价）。
<StructLayout(LayoutKind.Sequential)> _
Public Structure CGuid
    Public value As Guid
    Public Sub New(ByVal g As Guid)
        value = g
    End Sub
    Public Sub New(ByVal s As String)
        value = New Guid(s)
    End Sub
End Structure

' 过滤器列表（枚举 graph 内全部 filter，用于清理）。
Public Class CFilterList
    Private mFilters As System.Collections.ArrayList
    Private mEnumFilters As IEnumFilters = Nothing

    Public Sub New()
        mFilters = New System.Collections.ArrayList()
    End Sub

    Public ReadOnly Property Count() As Integer
        Get
            If mFilters Is Nothing Then
                Return 0
            End If
            Return mFilters.Count
        End Get
    End Property

    Default Public ReadOnly Property Item(ByVal index As Integer) As IBaseFilter
        Get
            Return CType(mFilters(index), IBaseFilter)
        End Get
    End Property

    Public Sub Assign(ByVal fg As IGraphBuilder)
        Clear()
        Try
            fg.EnumFilters(mEnumFilters)
            Dim filt As IBaseFilter = Nothing
            Dim fetched As Integer = 0
            While mEnumFilters.[Next](1, filt, fetched) = 0
                mFilters.Add(filt)
                filt = Nothing
                fetched = 0
            End While
            Try
                Marshal.ReleaseComObject(mEnumFilters)
            Catch ex As Exception
            End Try
            mEnumFilters = Nothing
        Catch ex As Exception
        End Try
    End Sub

    Public Sub Free()
        Clear()
    End Sub

    Private Sub Clear()
        Try
            While mFilters.Count > 0
                Dim f As IBaseFilter = CType(mFilters(0), IBaseFilter)
                Try
                    Marshal.ReleaseComObject(f)
                Catch ex As Exception
                End Try
                mFilters.RemoveAt(0)
            End While
            mFilters.Clear()
        Catch ex As Exception
        End Try
    End Sub
End Class

' Pin 列表（枚举 filter 的全部 pin）。
Public Class CPinList
    Private mPins As System.Collections.ArrayList
    Private mEnumPins As IEnumPins = Nothing

    Public Sub New()
        mPins = New System.Collections.ArrayList()
    End Sub

    Public ReadOnly Property Count() As Integer
        Get
            If mPins Is Nothing Then
                Return 0
            End If
            Return mPins.Count
        End Get
    End Property

    Default Public ReadOnly Property Item(ByVal index As Integer) As IPin
        Get
            Return CType(mPins(index), IPin)
        End Get
    End Property

    Public Sub Assign(ByVal filter As IBaseFilter)
        Clear()
        Try
            filter.EnumPins(mEnumPins)
            Dim hr As Integer = 0
            Do
                Dim pin As IPin = Nothing
                Dim fetched As Integer = 0
                hr = mEnumPins.[Next](1, pin, fetched)
                If (hr < 0) OrElse (fetched <> 1) Then
                    Exit Do
                End If
                mPins.Add(pin)
                pin = Nothing
                fetched = 0
            Loop While True
            Try
                Marshal.ReleaseComObject(mEnumPins)
            Catch ex As Exception
            End Try
            mEnumPins = Nothing
        Catch ex As Exception
        End Try
    End Sub

    Public Sub Free()
        Clear()
    End Sub

    Private Sub Clear()
        Try
            While mPins.Count > 0
                Dim p As IPin = CType(mPins(0), IPin)
                Try
                    Marshal.ReleaseComObject(p)
                Catch ex As Exception
                End Try
                mPins.RemoveAt(0)
            End While
            mPins.Clear()
        Catch ex As Exception
        End Try
    End Sub
End Class
