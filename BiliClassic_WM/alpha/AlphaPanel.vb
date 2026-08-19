Imports System
Imports System.Windows.Forms

Namespace AlphaMobileControls
    ''' <summary>
    ''' This Panel is able to handle alpha channel for its child controls
    ''' inheriting from AlphaControl.
    ''' </summary>
    Public Class AlphaPanel
        Inherits Panel

        Private _alphaManager As AlphaContainer
        Private components As System.ComponentModel.IContainer

        ''' <summary>
        ''' Default constructor.
        ''' </summary>
        Public Sub New()
            ' Instantiate before the components to handle events triggered by InitializeComponent
            _alphaManager = New AlphaContainer(Me)

            InitializeComponent()
        End Sub

        ''' <summary>
        ''' Clean up any resources being used.
        ''' </summary>
        Protected Overrides Sub Dispose(ByVal disposing As Boolean)
            If disposing AndAlso (components IsNot Nothing) Then
                components.Dispose()
            End If
            MyBase.Dispose(disposing)
        End Sub

        Private Sub InitializeComponent()
            components = New System.ComponentModel.Container()
        End Sub

        Protected Overrides Sub OnResize(ByVal e As EventArgs)
            _alphaManager.OnResize(e)
            MyBase.OnResize(e)
        End Sub

        Protected Overrides Sub OnPaintBackground(ByVal e As PaintEventArgs)
            ' Prevent flicker, we will take care of the background in OnPaint()
        End Sub

        Protected Overrides Sub OnPaint(ByVal e As PaintEventArgs)
            _alphaManager.OnPaint(e)
        End Sub
    End Class
End Namespace
