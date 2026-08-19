Imports System.Drawing

Namespace AlphaMobileControls
    ''' <summary>
    ''' Simple Alpha Label control.
    ''' </summary>
    Public Class AlphaLabel
        Inherits AlphaControl

        Private _border As Boolean

        ''' <summary>
        ''' Active or not a thin border around this label.
        ''' </summary>
        Public Property Border() As Boolean
            Get
                Return _border
            End Get
            Set(ByVal value As Boolean)
                If _border = value Then
                    Return
                End If
                _border = value
                Refresh()
            End Set
        End Property

        ''' <summary>
        ''' Draws the label.
        ''' </summary>
        Public Overrides Sub Draw(ByVal gx As Graphics)
            Dim rect As New Rectangle(Me.Bounds.X, Me.Bounds.Y, Me.Bounds.Width - 1, Me.Bounds.Height - 1)

            ' Draw the border
            If _border Then
                Dim pen As New Pen(Me.ForeColor)
                gx.DrawRectangle(pen, rect)
            End If

            ' Specify a rectangle to activate the line wrapping
            rect.Inflate(-4, -2)
            Dim brush As New SolidBrush(Me.ForeColor)
            gx.DrawString(Me.Text, Me.Font, brush, rect)
        End Sub
    End Class
End Namespace
