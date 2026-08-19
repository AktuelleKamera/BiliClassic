' DirectShow 辅助函数（VB.NET，兼容 .NET CF 2.0）。
' 来源：DirectShow.NetCF DirectShowHelper.cs（转译，仅播放所需部分）。
Imports System.Runtime.InteropServices

Public NotInheritable Class DirectShowHelper
    Private Sub New()
    End Sub

    ' 从 graph 移除除 filter 外的所有过滤器。
    Public Shared Sub ClearGraph(ByVal graph As IGraphBuilder, ByVal keep As IBaseFilter)
        Try
            Dim filters As New CFilterList()
            filters.Assign(graph)
            Dim i As Integer
            For i = 0 To filters.Count - 1
                If filters(i) IsNot keep Then
                    Try
                        graph.RemoveFilter(filters(i))
                    Catch ex As Exception
                    End Try
                End If
            Next
            filters.Free()
        Catch ex As Exception
        End Try
    End Sub

    ' 连接 output 的某个输出 pin 到 input 的输入 pin。
    Public Shared Function ConnectPins(ByVal graph As IGraphBuilder, ByVal output As IBaseFilter, ByVal input As IBaseFilter) As Boolean
        Dim inputs As New CPinList()
        inputs.Assign(input)
        Dim outputs As New CPinList()
        outputs.Assign(output)
        Dim hr As Integer = -1
        Try
            Dim i As Integer
            For i = 0 To outputs.Count - 1
                Try
                    hr = graph.Connect(outputs(i), inputs(0))
                Catch ex As Exception
                    hr = -1
                End Try
                If hr >= 0 Then
                    Exit For
                End If
            Next
        Finally
            inputs.Free()
            outputs.Free()
        End Try
        Return (hr >= 0)
    End Function

    ' 渲染 output filter 的每个输出 pin（自动补全 graph 下游）。
    Public Shared Function RenderPins(ByVal graph As IGraphBuilder, ByVal output As IBaseFilter) As Boolean
        Dim result As Boolean = False
        Dim pins As New CPinList()
        pins.Assign(output)
        Try
            Dim i As Integer
            For i = 0 To pins.Count - 1
                Dim direction As Integer = 0
                Try
                    pins(i).QueryDirection(direction)
                Catch ex As Exception
                    direction = -1
                End Try
                If direction <> 1 Then
                    Continue For
                End If
                Dim hr As Integer = -1
                Try
                    hr = graph.Render(pins(i))
                Catch ex As Exception
                    hr = -1
                End Try
                If hr >= 0 Then
                    result = True
                End If
            Next
        Finally
            pins.Free()
        End Try
        Return result
    End Function

    ' 释放 AMMediaType 内部资源。
    Public Shared Sub FreeMediaType(ByVal mediaType As AMMediaType)
        If mediaType Is Nothing Then
            Return
        End If
        Try
            If mediaType.formatSize <> 0 Then
                Marshal.FreeCoTaskMem(mediaType.formatPtr)
                mediaType.formatSize = 0
                mediaType.formatPtr = IntPtr.Zero
            End If
            If mediaType.unkPtr <> IntPtr.Zero Then
                Marshal.Release(mediaType.unkPtr)
                mediaType.unkPtr = IntPtr.Zero
            End If
        Catch ex As Exception
        End Try
    End Sub

    ' 释放 PinInfo 内部引用。
    Public Shared Sub FreePinInfo(ByVal info As PinInfo)
        Try
            If info.filter <> IntPtr.Zero Then
                Marshal.Release(info.filter)
                info.filter = IntPtr.Zero
            End If
        Catch ex As Exception
        End Try
    End Sub
End Class
