' =====================================================================
' ProgressEventArgs（VB.NET，兼容 .NET Compact Framework 2.0）
'
' 来源：DirectShow.NetCF（Alex Mogurenko）
'   C# 原文件：DirectShowNETCF.Controls\PlayerControl\PlayerControl\ProgressEventArgs.cs
' =====================================================================

Imports System

Public Class ProgressEventArgs : Inherits EventArgs
    Private _progress As TimeSpan

    Public Sub New(ByVal progress As TimeSpan)
        _progress = progress
    End Sub

    Public ReadOnly Property Progress() As TimeSpan
        Get
            Return _progress
        End Get
    End Property
End Class
