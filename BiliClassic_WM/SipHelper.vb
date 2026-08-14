' Helper to show/hide the WM soft input panel (SIP).
' The InputPanel type lives in Microsoft.WindowsCE.Forms, which only exists
' on devices. We access it via reflection so the app also starts on the
' desktop where that assembly is absent.
Public Class SipHelper

    Private Shared _panelObj As Object = Nothing
    Private Shared _panelType As System.Type = Nothing
    Private Shared _enabledProp As System.Reflection.PropertyInfo = Nothing

    Public Shared Function Available() As Boolean
        Return (GetPanelType() IsNot Nothing)
    End Function

    Public Shared Sub Show()
        Dim p As Object = GetPanel()
        If p IsNot Nothing Then
            Try
                _enabledProp.SetValue(p, True, Nothing)
            Catch ex As Exception
            End Try
        End If
    End Sub

    Public Shared Sub Hide()
        Dim p As Object = GetPanel()
        If p IsNot Nothing Then
            Try
                _enabledProp.SetValue(p, False, Nothing)
            Catch ex As Exception
            End Try
        End If
    End Sub

    Private Shared Function GetPanel() As Object
        Try
            If _panelObj Is Nothing Then
                Dim t As System.Type = GetPanelType()
                If t Is Nothing Then
                    Return Nothing
                End If
                _panelObj = System.Activator.CreateInstance(t)
                _enabledProp = t.GetProperty("Enabled")
            End If
            Return _panelObj
        Catch ex As Exception
            Return Nothing
        End Try
    End Function

    Private Shared Function GetPanelType() As System.Type
        Try
            If _panelType Is Nothing Then
                Dim asm As System.Reflection.Assembly = Nothing
                Try
                    asm = System.Reflection.Assembly.Load("Microsoft.WindowsCE.Forms")
                Catch ex1 As Exception
                    Try
                        asm = System.Reflection.Assembly.Load("Microsoft.WindowsCE.Forms, Version=2.0.0.0, Culture=neutral, PublicKeyToken=969db8053d3322ac")
                    Catch ex2 As Exception
                    End Try
                End Try
                If asm IsNot Nothing Then
                    _panelType = asm.GetType("Microsoft.WindowsCE.Forms.InputPanel")
                End If
            End If
            Return _panelType
        Catch ex As Exception
            Return Nothing
        End Try
    End Function

End Class
