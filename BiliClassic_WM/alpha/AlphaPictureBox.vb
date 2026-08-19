Imports System.Drawing

Namespace AlphaMobileControls
    ''' <summary>
    ''' Simple PictureBox control handling alpha channel.
    ''' </summary>
    Public Class AlphaPictureBox
        Inherits AlphaControl

        Private _image As AlphaImage
        Private _alpha As Integer = 0

        ''' <summary>
        ''' The image to draw.
        ''' </summary>
        Public Property Image() As AlphaImage
            Get
                Return _image
            End Get
            Set(ByVal value As AlphaImage)
                _image = value
                If _image IsNot Nothing Then
                    _image.Alpha = _alpha
                End If
            End Set
        End Property

        ''' <summary>
        ''' The Alpha channel for the image.
        ''' </summary>
        Public Property Alpha() As Integer
            Get
                Return _alpha
            End Get
            Set(ByVal value As Integer)
                _alpha = value
                If _image IsNot Nothing Then
                    _image.Alpha = _alpha
                End If
            End Set
        End Property

        ''' <summary>
        ''' Cleaning.
        ''' </summary>
        Protected Overrides Sub Dispose(ByVal disposing As Boolean)
            If disposing Then
                If _image IsNot Nothing Then
                    _image.Dispose()
                End If
            End If
            MyBase.Dispose(disposing)
        End Sub

        ''' <summary>
        ''' Draws the image if any.
        ''' </summary>
        Public Overrides Sub Draw(ByVal gx As Graphics)
            If _image IsNot Nothing Then
                _image.Draw(gx, Me.Bounds)
            End If
        End Sub
    End Class
End Namespace
