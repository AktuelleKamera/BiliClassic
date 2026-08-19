Imports System
Imports System.Drawing
Imports System.Windows.Forms

Namespace AlphaMobileControls
    ''' <summary>
    ''' Base Alpha Control.
    '''
    ''' The drawing is not managed by the framework so the base Control
    ''' is made as not visible and the Visible property is replaced by a new one.
    '''
    ''' Note that this base control should be abstract but is not to allow
    ''' the designer to handle it correctly (maybe there is a nice way to do that?).
    ''' </summary>
    Public Class AlphaControl
        Inherits Control

        Private _visible As Boolean = True

        ''' <summary>
        ''' Show or Hide this Control.
        ''' Note: Don't use the Control.Visible property...
        ''' </summary>
        Public Shadows Property Visible() As Boolean
            Get
                Return _visible
            End Get
            Set(ByVal value As Boolean)
                If _visible = value Then
                    Return
                End If
                _visible = value
                Refresh()
            End Set
        End Property

        ''' <summary>
        ''' When the Text property is updated the Control is refreshed.
        ''' </summary>
        Public Overrides Property Text() As String
            Get
                Return MyBase.Text
            End Get
            Set(ByVal value As String)
                If MyBase.Text = value Then
                    Return
                End If
                MyBase.Text = value
                Refresh()
            End Set
        End Property

        ''' <summary>
        ''' Default constructor.
        ''' </summary>
        Public Sub New()
            MyBase.Visible = False
        End Sub

        ''' <summary>
        ''' Overrides Control.Refresh() to forward to action to the
        ''' parent control, which is responsible to handle the drawing process.
        ''' </summary>
        Public Overrides Sub Refresh()
            If Me.Parent IsNot Nothing Then
                Me.Parent.Invalidate(Me.Bounds)
            End If
        End Sub

        ''' <summary>
        ''' Checks if the given coordinates are contained by this control.
        ''' </summary>
        Public Function HitTest(ByVal x As Integer, ByVal y As Integer) As Boolean
            Return Me.Bounds.Contains(x, y)
        End Function

        ''' <summary>
        ''' Overrides Control.Resize() to force a custom Refresh.
        ''' </summary>
        Protected Overrides Sub OnResize(ByVal e As EventArgs)
            MyBase.OnResize(e)
            Refresh()
        End Sub

        ''' <summary>
        ''' Internal Draw method, called by the container.
        ''' Will call the actual Draw method if the control is visible.
        ''' </summary>
        Friend Sub DrawInternal(ByVal gx As Graphics)
            If _visible Then
                Draw(gx)
            End If
        End Sub

        ''' <summary>
        ''' Must be overridden.
        ''' </summary>
        Public Overridable Sub Draw(ByVal gx As Graphics)
        End Sub
    End Class
End Namespace
