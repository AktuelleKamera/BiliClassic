' =====================================================================
' WndProcHooker + Win32（VB.NET，兼容 .NET Compact Framework 2.0）
'
' 来源：DirectShow.NetCF（Alex Mogurenko）
'   C# 原文件：DirectShowNETCF.Controls\PlayerControl\PlayerControl\WndProc.cs
'   （该 C# 文件说明：代码取自 msdn blog，不保证完全正确）
'
' 转译说明：
'   - 静态类 -> Public NotInheritable Class + Private Sub New()
'   - 静态成员 -> Public Shared / Private Shared
'   - Dictionary<uint, T> -> Dictionary(Of UInteger, T)
'   - ref bool -> ByRef Boolean；Delegate/委托签名保持一一对应
'   - AddHandler/RemoveHandler 代替 += / -=
'   - Debug.Assert 原样保留
' =====================================================================

Imports System
Imports System.Collections
Imports System.Collections.Generic
Imports System.Runtime.InteropServices
Imports System.Text
Imports System.Windows.Forms

''' <summary>
''' This code was taken from msdn blog,
''' not sure if it works correct
''' </summary>
Public NotInheritable Class WndProcHooker
    Private Sub New()
    End Sub

    Public Shared watchControl As Control = Nothing
    ''' <summary>
    ''' The callback used when a hooked window's message map contains the
    ''' hooked message
    ''' </summary>
    ''' <param name="hwnd">The handle to the window for which the message
    ''' was received</param>
    ''' <param name="msg">The message</param>
    ''' <param name="wParam">The message's parameters (part 1)</param>
    ''' <param name="lParam">The message's parameters (part 2)</param>
    ''' <param name="handled">The invoked function sets this to true if it
    ''' handled the message. If the value is false when the callback
    ''' returns, the next window procedure in the wndproc chain is
    ''' called</param>
    ''' <returns>A value specified for the given message in the MSDN
    ''' documentation</returns>
    Public Delegate Function WndProcCallback(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer, ByRef handled As Boolean) As Integer

    ''' <summary>
    ''' This is the global list of all the window procedures we have
    ''' hooked. The key is an hwnd. The value is a HookedProcInformation
    ''' object which contains a pointer to the old wndproc and a map of
    ''' messages/callbacks for the window specified. Controls whose handles
    ''' have been created go into this dictionary.
    ''' </summary>
    Private Shared hwndDict As Dictionary(Of IntPtr, HookedProcInformation) = New Dictionary(Of IntPtr, HookedProcInformation)()

    ''' <summary>
    ''' See hwndDict. The key is a control and the value is a
    ''' HookedProcInformation. Controls whose handles have not been created
    ''' go into this dictionary. When the HandleCreated event for the
    ''' control is fired the control is moved into hwndDict.
    ''' </summary>
    Private Shared ctlDict As Dictionary(Of Control, HookedProcInformation) = New Dictionary(Of Control, HookedProcInformation)()

    ''' <summary>
    ''' Makes a connection between a message on a specified window handle
    ''' and the callback to be called when that message is received. If the
    ''' window was not previously hooked it is added to the global list of
    ''' all the window procedures hooked.
    ''' </summary>
    ''' <param name="ctl">The control whose wndproc we are hooking</param>
    ''' <param name="callback">The method to call when the specified
    ''' message is received for the specified window</param>
    ''' <param name="msg">The message we are hooking.</param>
    Public Shared Sub HookWndProc(ByVal ctl As Control, ByVal callback As WndProcCallback, ByVal msg As Integer)
        watchControl = ctl
        Dim hpi As HookedProcInformation = Nothing
        If ctlDict.ContainsKey(ctl) Then
            hpi = ctlDict(ctl)
        ElseIf hwndDict.ContainsKey(ctl.Handle) Then
            hpi = hwndDict(ctl.Handle)
        End If
        If hpi Is Nothing Then
            ' We haven't seen this control before. Create a new
            ' HookedProcInformation for it
            hpi = New HookedProcInformation(ctl, New Win32.WndProc(AddressOf WndProcHooker.WindowProc))
            AddHandler ctl.HandleCreated, AddressOf ctl_HandleCreated
            AddHandler ctl.HandleDestroyed, AddressOf ctl_HandleDestroyed
            AddHandler ctl.Disposed, AddressOf ctl_Disposed

            ' If the handle has already been created set the hook. If it
            ' hasn't been created yet, the hook will get set in the
            ' ctl_HandleCreated event handler
            If ctl.Handle <> IntPtr.Zero Then
                hpi.SetHook()
            End If
        End If

        ' stick hpi into the correct dictionary
        If ctl.Handle = IntPtr.Zero Then
            ctlDict(ctl) = hpi
        Else
            hwndDict(ctl.Handle) = hpi
        End If

        ' add the message/callback into the message map
        hpi.messageMap(msg) = callback
    End Sub

    ''' <summary>
    ''' The event handler called when a control is disposed.
    ''' </summary>
    ''' <param name="sender">The object that raised this event</param>
    ''' <param name="e">The arguments for this event</param>
    Private Shared Sub ctl_Disposed(ByVal sender As Object, ByVal e As EventArgs)
        Dim ctl As Control = TryCast(sender, Control)
        Try
            If ctlDict.ContainsKey(ctl) Then
                ctlDict.Remove(ctl)
            End If
        Catch ex As Exception
        End Try
    End Sub

    ''' <summary>
    ''' The event handler called when a control's handle is destroyed.
    ''' We remove the HookedProcInformation from hwndDict and
    ''' put it back into ctlDict in case the control gets re-
    ''' created and we still want to hook its messages.
    ''' </summary>
    ''' <param name="sender">The object that raised this event</param>
    ''' <param name="e">The arguments for this event</param>
    Private Shared Sub ctl_HandleDestroyed(ByVal sender As Object, ByVal e As EventArgs)
        ' When the handle for a control is destroyed, we want to
        ' unhook its wndproc and update our lists.
        ' 注意：HandleDestroyed 触发时 ctl.Handle 可能已失效，不能依赖它查找。
        Dim ctl As Control = TryCast(sender, Control)
        Try
            If hwndDict.ContainsKey(ctl.Handle) Then
                UnhookWndProc(ctl, False)
            ElseIf ctlDict.ContainsKey(ctl) Then
                ' 已在 ctlDict（handle 尚未真正建立），无需处理。
            Else
                ' 找不到：可能是 Dispose 清理后的残余事件，静默忽略。
            End If
        Catch ex As Exception
            ' 容错：不因清理异常中断 Dispose。
        End Try
    End Sub

    ''' <summary>
    ''' The event handler called when a control's handle is created. We
    ''' call SetHook() on the associated HookedProcInformation object and
    ''' move it from ctlDict to hwndDict.
    ''' </summary>
    ''' <param name="sender"></param>
    ''' <param name="e"></param>
    Private Shared Sub ctl_HandleCreated(ByVal sender As Object, ByVal e As EventArgs)
        Dim ctl As Control = TryCast(sender, Control)
        Try
            If ctlDict.ContainsKey(ctl) Then
                Dim hpi As HookedProcInformation = ctlDict(ctl)
                hwndDict(ctl.Handle) = hpi
                ctlDict.Remove(ctl)
                hpi.SetHook()
            End If
        Catch ex As Exception
        End Try
    End Sub

    ''' <summary>
    ''' This is a generic wndproc. It is the callback for all hooked
    ''' windows. If we get into this function, we look up the hwnd in the
    ''' global list of all hooked windows to get its message map. If the
    ''' message received is present in the message map, its callback is
    ''' invoked with the parameters listed here.
    ''' </summary>
    ''' <param name="hwnd">The handle to the window that received the
    ''' message</param>
    ''' <param name="msg">The message</param>
    ''' <param name="wParam">The message's parameters (part 1)</param>
    ''' <param name="lParam">The messages's parameters (part 2)</param>
    ''' <returns>If the callback handled the message, the callback's return
    ''' value is returned form this function. If the callback didn't handle
    ''' the message, the message is forwarded on to the previous wndproc.
    ''' </returns>
    Private Shared Function WindowProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
        If hwndDict.ContainsKey(hwnd) Then
            Dim hpi As HookedProcInformation = hwndDict(hwnd)
            Dim callback As WndProcCallback = hpi.messageMap(&HFFFFFF)
            Dim handled As Boolean = False
            Dim retval As Integer = callback(hwnd, msg, wParam, lParam, handled)
            If handled Then
                Return retval
            End If

            ' if we didn't hook the message passed or we did, but the
            ' callback didn't set the handled property to true, call
            ' the original window procedure
            Return hpi.CallOldWindowProc(hwnd, msg, wParam, lParam)
        End If

        ' 未挂钩的窗口/消息：交给默认窗口过程。
        Return Win32.DefWindowProc(hwnd, msg, wParam, lParam)
    End Function

    ''' <summary>
    ''' This method removes the specified message from the message map for
    ''' the specified hwnd.
    ''' </summary>
    ''' <param name="ctl">The control whose message we are unhooking
    ''' </param>
    ''' <param name="msg">The message no longer want to hook</param>
    Public Shared Sub UnhookWndProc(ByVal ctl As Control, ByVal msg As Integer)
        ' look for the HookedProcInformation in the control and hwnd
        ' dictionaries
        Dim hpi As HookedProcInformation = Nothing
        If ctlDict.ContainsKey(ctl) Then
            hpi = ctlDict(ctl)
        ElseIf hwndDict.ContainsKey(ctl.Handle) Then
            hpi = hwndDict(ctl.Handle)
        End If

        ' if we couldn't find a HookedProcInformation, throw
        If hpi Is Nothing Then
            Throw New ArgumentException("No hook exists for this control")
        End If

        ' look for the message we are removing in the messageMap
        If hpi.messageMap.ContainsKey(msg) Then
            hpi.messageMap.Remove(msg)
        Else
            ' if we couldn't find the message, throw
            Throw New ArgumentException(String.Format("No hook exists for message ({0}) on this control", msg))
        End If
    End Sub

    ''' <summary>
    ''' Restores the previous wndproc for the specified window.
    ''' </summary>
    ''' <param name="ctl">The control whose wndproc we no longer want to
    ''' hook</param>
    ''' <param name="disposing">if true we remove don't readd the
    ''' HookedProcInformation back into ctlDict</param>
    Public Shared Sub UnhookWndProc(ByVal ctl As Control, ByVal disposing As Boolean)
        Dim hpi As HookedProcInformation = Nothing
        If ctlDict.ContainsKey(ctl) Then
            hpi = ctlDict(ctl)
        ElseIf hwndDict.ContainsKey(ctl.Handle) Then
            hpi = hwndDict(ctl.Handle)
        End If

        If hpi Is Nothing Then
            Throw New ArgumentException("No hook exists for this control")
        End If

        ' If we found our HookedProcInformation in ctlDict and we are
        ' disposing remove it from ctlDict
        If ctlDict.ContainsKey(ctl) AndAlso disposing Then
            ctlDict.Remove(ctl)
        End If

        ' If we found our HookedProcInformation in hwndDict, remove it
        ' and if we are not disposing stick it in ctlDict
        If hwndDict.ContainsKey(ctl.Handle) Then
            hpi.Unhook()
            hwndDict.Remove(ctl.Handle)
            If Not disposing Then
                ctlDict(ctl) = hpi
            End If
        End If
    End Sub

    ''' <summary>
    ''' This class remembers the old window procedure for the specified
    ''' window handle and also provides the message map for the messages
    ''' hooked on that window.
    ''' </summary>
    Private Class HookedProcInformation
        ''' <summary>
        ''' The message map for the window
        ''' </summary>
        Public messageMap As Dictionary(Of Integer, WndProcCallback)

        ''' <summary>
        ''' The old window procedure for the window
        ''' </summary>
        Private oldWndProc As IntPtr

        ''' <summary>
        ''' The delegate that gets called in place of this window's
        ''' wndproc.
        ''' </summary>
        Private newWndProc As Win32.WndProc

        ''' <summary>
        ''' Control whose wndproc we are hooking
        ''' </summary>
        Private control As Control

        ''' <summary>
        ''' Constructs a new HookedProcInformation object
        ''' </summary>
        ''' <param name="ctl">The handle to the window being hooked</param>
        ''' <param name="wndproc">The window procedure to replace the
        ''' original one for the control</param>
        Public Sub New(ByVal ctl As Control, ByVal wndproc As Win32.WndProc)
            control = ctl
            newWndProc = wndproc
            messageMap = New Dictionary(Of Integer, WndProcCallback)()
        End Sub

        ''' <summary>
        ''' Replaces the windows procedure for control with the
        ''' one specified in the constructor.
        ''' </summary>
        Public Sub SetHook()
            Dim hwnd As IntPtr = control.Handle
            If hwnd = IntPtr.Zero Then
                Throw New InvalidOperationException("Handle for control has not been created")
            End If

            oldWndProc = Win32.SetWindowLong(hwnd, Win32.GWL_WNDPROC, Marshal.GetFunctionPointerForDelegate(newWndProc))
        End Sub

        ''' <summary>
        ''' Restores the original window procedure for the control.
        ''' </summary>
        Public Sub Unhook()
            Dim hwnd As IntPtr = control.Handle
            If hwnd = IntPtr.Zero Then
                Throw New InvalidOperationException("Handle for control has not been created")
            End If

            Win32.SetWindowLong(hwnd, Win32.GWL_WNDPROC, oldWndProc)
        End Sub

        ''' <summary>
        ''' Calls the original window procedure of the control with the
        ''' arguments provided.
        ''' </summary>
        ''' <param name="hwnd">The handle of the window that received the
        ''' message</param>
        ''' <param name="msg">The message</param>
        ''' <param name="wParam">The message's arguments (part 1)</param>
        ''' <param name="lParam">The message's arguments (part 2)</param>
        ''' <returns>The value returned by the control's original wndproc
        ''' </returns>
        Public Function CallOldWindowProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
            Return Win32.CallWindowProc(oldWndProc, hwnd, msg, wParam, lParam)
        End Function
    End Class
End Class

''' <summary>
''' Contains managed wrappers or implementations of Win32 structs, delegates,
''' constants and PInvokes that are useful for this sample.
'''
''' See the documentation on MSDN for more information on the elements provided
''' in this file.
''' </summary>
Public NotInheritable Class Win32
    Private Sub New()
    End Sub

    ''' <summary>
    ''' A callback to a Win32 window procedure (wndproc)
    ''' </summary>
    ''' <param name="hwnd">The handle of the window receiving a message</param>
    ''' <param name="msg">The message</param>
    ''' <param name="wParam">The message's parameters (part 1)</param>
    ''' <param name="lParam">The message's parameters (part 2)</param>
    ''' <returns>A integer as described for the given message in MSDN</returns>
    Public Delegate Function WndProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer

    ' 桌面模拟用 user32.dll；WM 设备用 coredll.dll。
    Private Shared ReadOnly IsDesktop As Boolean = (System.Environment.OSVersion.Platform = System.PlatformID.Win32NT)

    <DllImport("coredll.dll", EntryPoint:="DefWindowProc")> _
    Private Shared Function Core_DefWindowProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
    End Function

    <DllImport("user32.dll", EntryPoint:="DefWindowProcW")> _
    Private Shared Function User32_DefWindowProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
    End Function

    Public Shared Function DefWindowProc(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
        If IsDesktop Then
            Return User32_DefWindowProc(hwnd, msg, wParam, lParam)
        Else
            Return Core_DefWindowProc(hwnd, msg, wParam, lParam)
        End If
    End Function

    <DllImport("coredll.dll", EntryPoint:="SetWindowLong")> _
    Private Shared Function Core_SetWindowLong(ByVal hwnd As IntPtr, ByVal nIndex As Integer, ByVal dwNewLong As IntPtr) As IntPtr
    End Function

    <DllImport("user32.dll", EntryPoint:="SetWindowLongW")> _
    Private Shared Function User32_SetWindowLong(ByVal hwnd As IntPtr, ByVal nIndex As Integer, ByVal dwNewLong As IntPtr) As IntPtr
    End Function

    ' 64 位桌面必须用 SetWindowLongPtrW：SetWindowLongW 会把 64 位窗口过程指针截断，
    ' 导致挂钩失效/崩溃。SetWindowLongPtrW 仅在 64 位系统导出，32 位系统不可调用。
    <DllImport("user32.dll", EntryPoint:="SetWindowLongPtrW")> _
    Private Shared Function User32_SetWindowLongPtr(ByVal hwnd As IntPtr, ByVal nIndex As Integer, ByVal dwNewLong As IntPtr) As IntPtr
    End Function

    Public Shared Function SetWindowLong(ByVal hwnd As IntPtr, ByVal nIndex As Integer, ByVal dwNewLong As IntPtr) As IntPtr
        If IsDesktop Then
            If IntPtr.Size = 8 Then
                Return User32_SetWindowLongPtr(hwnd, nIndex, dwNewLong)
            Else
                Return User32_SetWindowLong(hwnd, nIndex, dwNewLong)
            End If
        Else
            Return Core_SetWindowLong(hwnd, nIndex, dwNewLong)
        End If
    End Function

    Public Const GWL_WNDPROC As Integer = -4

    <DllImport("coredll.dll", EntryPoint:="CallWindowProc")> _
    Private Shared Function Core_CallWindowProc(ByVal lpPrevWndFunc As IntPtr, ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
    End Function

    <DllImport("user32.dll", EntryPoint:="CallWindowProcW")> _
    Private Shared Function User32_CallWindowProc(ByVal lpPrevWndFunc As IntPtr, ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
    End Function

    Public Shared Function CallWindowProc(ByVal lpPrevWndFunc As IntPtr, ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As Integer, ByVal lParam As Integer) As Integer
        If IsDesktop Then
            Return User32_CallWindowProc(lpPrevWndFunc, hwnd, msg, wParam, lParam)
        Else
            Return Core_CallWindowProc(lpPrevWndFunc, hwnd, msg, wParam, lParam)
        End If
    End Function
End Class
