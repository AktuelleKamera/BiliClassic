' =====================================================================
' PlayerControl（VB.NET，兼容 .NET Compact Framework 2.0）
'
' 来源：DirectShow.NetCF（Alex Mogurenko）
'   C# 原文件：DirectShowNETCF.Controls\PlayerControl\PlayerControl\PlayerControl.cs
'
' 转译说明：
'   - partial class + Designer 文件 -> 单一完整类；InitializeComponent() 置空，
'     并保留 components 字段（IContainer = Nothing）。
'   - 原 C# 顶部大段注释掉的 NullPlayer 类未转译（本就是注释代码）。
'   - Timer -> System.Windows.Forms.Timer；Tick += / -= -> AddHandler / RemoveHandler。
'   - 事件 += / -= -> AddHandler / RemoveHandler；RaiseEvent 触发（field-like 事件
'     在无订阅者时不抛异常，等价于 C# 的 null 检查后调用）。
'   - Stop / Loop 为 VB 关键字 -> [Stop] / [Loop]。
'   - WndProcHooker / ProgressEventArgs 见 player\WndProc.vb、player\ProgressEventArgs.vb。
' =====================================================================

Imports System
Imports System.Collections.Generic
Imports System.ComponentModel
Imports System.Drawing
Imports System.Data
Imports System.Text
Imports System.Windows.Forms
Imports System.Runtime.InteropServices

Public Class PlayerControl : Inherits System.Windows.Forms.UserControl

    '#region declarations

    Private _wndProc As WndProcHooker.WndProcCallback = Nothing
    Private _playerModel As PlayerModel = Nothing
    Private _timer As System.Windows.Forms.Timer = Nothing
    Private _loop As Boolean = False
    Private components As System.ComponentModel.IContainer = Nothing
    ' 视频宿主窗口句柄（UI 线程缓存，供后台线程 OpenFile 使用，避免跨线程访问 Handle）。
    Private _hostHandle As IntPtr = IntPtr.Zero

    ' 由外部（UI 线程）设置视频宿主窗口句柄。
    Public Property VideoHostHandle() As IntPtr
        Get
            Return _hostHandle
        End Get
        Set(ByVal value As IntPtr)
            _hostHandle = value
        End Set
    End Property

    ' 诊断日志（与 PlayerModel 写同一文件 playerlog.txt）。
    Private Sub Log(ByVal msg As String)
        Try
            Dim dir As String = System.IO.Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().GetName().CodeBase.Replace("file:///", ""))
            If String.IsNullOrEmpty(dir) Then
                dir = "\Program Files\BiliClassic"
            End If
            Try
                System.IO.Directory.CreateDirectory(dir)
            Catch exD As Exception
            End Try
            Dim fs As New System.IO.FileStream(dir & "\playerlog.txt", System.IO.FileMode.Append)
            Dim w As New System.IO.StreamWriter(fs, System.Text.Encoding.UTF8)
            w.WriteLine(DateTime.Now.ToString("HH:mm:ss") & "  " & msg)
            w.Close()
            fs.Close()
        Catch ex As Exception
        End Try
    End Sub

    '#endregion

    '#region constants

    Private Const ALL_MESSAGES As Integer = &HFFFFFF
    Private Const WM_KEYDOWN As Integer = &H100
    Private Const WM_KEYUP As Integer = &H101
    Private Const WM_LBUTTONDBLCLK As Integer = &H203
    Private Const WM_LBUTTONDOWN As Integer = &H201
    Private Const WM_LBUTTONUP As Integer = &H202
    Private Const WM_MBUTTONDBLCLK As Integer = &H209
    Private Const WM_MBUTTONDOWN As Integer = &H207
    Private Const WM_MBUTTONUP As Integer = &H208
    Private Const WM_MOUSEMOVE As Integer = &H200
    Private Const WM_RBUTTONDBLCLK As Integer = &H206
    Private Const WM_RBUTTONDOWN As Integer = &H204
    Private Const WM_RBUTTONUP As Integer = &H205

    '#endregion

    '#region Events

    Public Event MediaFailed As EventHandler
    Public Event MediaEnded As EventHandler
    Public Event MediaProgress As EventHandler(Of ProgressEventArgs)

    '#endregion

    '#region Constructor

    Public Sub New()
        InitializeComponent()

        ' WndProc 钩子：仅 WM 设备需要（接收 WM_GRAPHNOTIFY/鼠标消息）。
        ' CF 桌面模拟下 SetWindowLong 钩子不稳定，可能导致整个消息循环卡死，故跳过。
        If Not IsDesktop Then
            If _wndProc Is Nothing Then
                _wndProc = New WndProcHooker.WndProcCallback(AddressOf WndProc)
                WndProcHooker.HookWndProc(Me, _wndProc, ALL_MESSAGES)
            End If
        End If

        _playerModel = New PlayerModel()
        AddHandler _playerModel.MediaFailed, AddressOf OnMediaFailed

        InitTimer()
    End Sub

    Private Shared ReadOnly IsDesktop As Boolean = (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)

    Private Sub InitializeComponent()
    End Sub

    '#endregion

    '#region Properties

    Public Property [Loop]() As Boolean
        Get
            Return _loop
        End Get
        Set(ByVal value As Boolean)
            _loop = value
        End Set
    End Property

    Public Property Volume() As Integer
        Get
            Return _playerModel.GetVolume()
        End Get
        Set(ByVal value As Integer)
            _playerModel.SetVolume(value)
        End Set
    End Property

    Public Property Balance() As Integer
        Get
            Return _playerModel.GetVolume()
        End Get

        Set(ByVal value As Integer)
            _playerModel.SetBalance(value)
        End Set
    End Property

    Public ReadOnly Property VideoWidth() As Integer
        Get
            Return _playerModel.GetWidth()
        End Get
    End Property

    Public ReadOnly Property VideoHeight() As Integer
        Get
            Return _playerModel.GetHeight()
        End Get
    End Property

    Public ReadOnly Property BitRate() As Integer
        Get
            Return _playerModel.GetBitRate()
        End Get
    End Property

    '#endregion

    '#region public methods

    ''' <summary>
    ''' Builds graph that allows to play required file
    ''' </summary>
    ''' <param name="filePath">Path to file to play</param>
    Public Sub OpenFile(ByVal filePath As String)
        Log("OpenFile enter: " & filePath)
        If _playerModel Is Nothing Then
            Log("OpenFile: _playerModel is Nothing")
            Return
        End If
        _playerModel.LoadFile(filePath)
        Log("OpenFile after LoadFile")
        _playerModel.SetVideoWindow(_hostHandle, 0, 0, Me.Width, Me.Height)
        _playerModel.SetEventHendler(_hostHandle)
        Log("OpenFile exit")
    End Sub

    ''' <summary>
    ''' Starts play file
    ''' </summary>
    Public Sub Play()
        ' MessageBox.Show("You are using demo version of Player control!");
        _playerModel.Play()
        _timer.Enabled = True
    End Sub

    ''' <summary>
    ''' Pauses player
    ''' </summary>
    Public Sub Pause()
        _playerModel.Pause()
        _timer.Enabled = False
    End Sub

    ''' <summary>
    ''' Sets player to required position
    ''' </summary>
    ''' <param name="position"> Position of file</param>
    Public Sub Seek(ByVal position As TimeSpan)
        _playerModel.Seek(position)
    End Sub

    ''' <summary>
    ''' Stops playing, unloads all resources, and destroys graph
    ''' </summary>
    Public Sub [Stop]()
        _timer.Enabled = False
        _playerModel.[Stop]()
    End Sub

    ''' <summary>
    ''' Gets Duration of media file
    ''' </summary>
    ''' <returns> Media duration, or TimeSpan.Zero if cannot get duration</returns>
    Public Function GetDuration() As TimeSpan
        Return _playerModel.GetDuration()
    End Function

    '#endregion

    '#region overrides

    ''' <summary>
    ''' Clean up any resources being used.
    ''' </summary>
    ''' <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
    Protected Overrides Sub Dispose(ByVal disposing As Boolean)
        DestroyTimer()
        If _playerModel IsNot Nothing Then
            Try
                RemoveHandler _playerModel.MediaFailed, AddressOf OnMediaFailed
                _playerModel.Dispose()
            Catch ex As Exception
            End Try
            _playerModel = Nothing
        End If

        Try
            If Not IsDesktop Then
                WndProcHooker.UnhookWndProc(Me, False)
            End If
        Catch ex As Exception
        End Try

        If disposing AndAlso (components IsNot Nothing) Then
            components.Dispose()
        End If
        MyBase.Dispose(disposing)
    End Sub

    Protected Overrides Sub OnResize(ByVal e As EventArgs)
        If _playerModel IsNot Nothing Then
            _playerModel.Resize(Me.Width, Me.Height)
        End If

        MyBase.OnResize(e)
    End Sub

    '#endregion

    '#region private methods

    Private Shared Shadows Function WndProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer, ByRef handled As Boolean) As Integer
        Dim this_ As PlayerControl = CType(WndProcHooker.watchControl, PlayerControl)
        handled = True

        Dim x As Integer = lParam And &HFFFF
        Dim y As Integer = (lParam >> 16) And &HFFFF

        Select Case msg
            Case WM_KEYDOWN, WM_KEYUP
                Return 0
            Case WM_LBUTTONDBLCLK
                this_.OnDoubleClick(EventArgs.Empty)
                Return 0
            Case WM_LBUTTONDOWN
                this_.OnMouseDown(New MouseEventArgs(MouseButtons.Left, 1, x, y, 0))
                Return 0
            Case WM_LBUTTONUP
                this_.OnMouseUp(New MouseEventArgs(MouseButtons.Left, 1, x, y, 0))
                Return 0
            Case WM_MBUTTONDBLCLK
                this_.OnDoubleClick(EventArgs.Empty)
                Return 0
            Case WM_MBUTTONDOWN
                this_.OnMouseDown(New MouseEventArgs(MouseButtons.Middle, 1, x, y, 0))
                Return 0
            Case WM_MBUTTONUP
                this_.OnMouseUp(New MouseEventArgs(MouseButtons.Middle, 1, x, y, 0))
                Return 0
            Case WM_MOUSEMOVE
                Return 0
            Case WM_RBUTTONDBLCLK
                this_.OnDoubleClick(EventArgs.Empty)
                Return 0
            Case WM_RBUTTONDOWN
                this_.OnMouseDown(New MouseEventArgs(MouseButtons.Right, 1, x, y, 0))
                Return 0
            Case WM_RBUTTONUP
                this_.OnMouseDown(New MouseEventArgs(MouseButtons.Right, 1, x, y, 0))
                Return 0
            Case CInt(NotifyMessages.WM_GRAPHNOTIFY)
                this_.HandleGraphEvent()
                Return 0
        End Select
        handled = False
        Return 0
    End Function

    Private Sub HandleGraphEvent()
        Dim evCode As Integer
        Dim evParam1 As Integer
        Dim evParam2 As Integer

        While _playerModel.GetEvent(evCode, evParam1, evParam2) = 0
            _playerModel.FreeEventParams(evCode, evParam1, evParam2)

            Select Case evCode
                Case CInt(NotifyMessages.EC_COMPLETE)
                    OnMediaEnded()
                Case Else
            End Select
        End While
    End Sub

    Private Sub InitTimer()
        _timer = New System.Windows.Forms.Timer()
        _timer.Interval = 200
        AddHandler _timer.Tick, AddressOf OnTimer
    End Sub

    Private Sub DestroyTimer()
        If _timer Is Nothing Then
            Return
        End If
        Try
            _timer.Enabled = False
            RemoveHandler _timer.Tick, AddressOf OnTimer
            _timer.Dispose()
        Catch ex As Exception
        End Try
        _timer = Nothing
    End Sub

    '#endregion

    '#region Event Handlers

    Private Sub OnMediaEnded()
        If Not [Loop] Then
            _timer.Enabled = False

            RaiseEvent MediaEnded(Me, EventArgs.Empty)
        Else
            Seek(TimeSpan.Zero)
        End If
    End Sub

    Private Sub OnMediaFailed(ByVal sender As Object, ByVal e As EventArgs)
        _timer.Enabled = False

        RaiseEvent MediaFailed(Me, e)
    End Sub

    Private Sub OnMediaProgress(ByVal progress As TimeSpan)
        RaiseEvent MediaProgress(Me, New ProgressEventArgs(progress))
    End Sub

    Private Sub OnTimer(ByVal sender As Object, ByVal e As EventArgs)
        OnMediaProgress(_playerModel.GetCurrentPosition())
    End Sub

    '#endregion
End Class
