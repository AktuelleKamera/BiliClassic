Imports System
Imports System.Drawing
Imports System.Windows.Forms

Namespace AlphaMobileControls
    ''' <summary>
    ''' Alpha Image Button.
    ''' 3 images can be associated: Normal, Pushed and Disabled.
    ''' </summary>
    Public Class AlphaImageButton
        Inherits AlphaControl

        Private _backgroundImage As AlphaImage = New AlphaImage()
        Private _activeBackgroundImage As AlphaImage = New AlphaImage()
        Private _disabledBackgroundImage As AlphaImage = New AlphaImage()
        Private _alpha As Integer
        Private _pushed As Boolean
        Private _hover As Boolean

        ''' <summary>
        ''' Background Image for the Normal state.
        ''' </summary>
        Public Property BackgroundImage() As AlphaImage
            Get
                Return _backgroundImage
            End Get
            Set(ByVal value As AlphaImage)
                _backgroundImage = value
            End Set
        End Property

        ''' <summary>
        ''' Background Image for the Pushed state.
        ''' </summary>
        Public Property ActiveBackgroundImage() As AlphaImage
            Get
                Return _activeBackgroundImage
            End Get
            Set(ByVal value As AlphaImage)
                _activeBackgroundImage = value
            End Set
        End Property

        ''' <summary>
        ''' Background Image for the Disabled state.
        ''' </summary>
        Public Property DisabledBackgroundImage() As AlphaImage
            Get
                Return _disabledBackgroundImage
            End Get
            Set(ByVal value As AlphaImage)
                _disabledBackgroundImage = value
            End Set
        End Property

        ''' <summary>
        ''' Sets the same Alpha value for all background images.
        ''' </summary>
        Public Property Alpha() As Integer
            Get
                Return _alpha
            End Get
            Set(ByVal value As Integer)
                _alpha = value
                If _backgroundImage IsNot Nothing Then
                    _backgroundImage.Alpha = _alpha
                End If
                If _activeBackgroundImage IsNot Nothing Then
                    _activeBackgroundImage.Alpha = _alpha
                End If
                If _disabledBackgroundImage IsNot Nothing Then
                    _disabledBackgroundImage.Alpha = _alpha
                End If
            End Set
        End Property

        ''' <summary>
        ''' Default constructor.
        ''' </summary>
        Public Sub New()
            ' Set some default values
            Me.Size = New System.Drawing.Size(72, 20)
            Me.ForeColor = SystemColors.ControlText

            AddHandler Me.ParentChanged, AddressOf AlphaImageButton_ParentChanged
            AddHandler Me.EnabledChanged, AddressOf AlphaImageButton_EnabledChanged
        End Sub

        ''' <summary>
        ''' Cleaning.
        ''' </summary>
        Protected Overrides Sub Dispose(ByVal disposing As Boolean)
            If disposing Then
                If _backgroundImage IsNot Nothing Then
                    _backgroundImage.Dispose()
                End If
                If _activeBackgroundImage IsNot Nothing Then
                    _activeBackgroundImage.Dispose()
                End If
                If _disabledBackgroundImage IsNot Nothing Then
                    _disabledBackgroundImage.Dispose()
                End If
            End If
            MyBase.Dispose(disposing)
        End Sub

        ''' <summary>
        ''' Get notified when the parent control is set to register to some mouse events.
        ''' </summary>
        Private Sub AlphaImageButton_ParentChanged(ByVal sender As Object, ByVal e As EventArgs)
            AddHandler Me.Parent.MouseDown, AddressOf Parent_MouseDown
            AddHandler Me.Parent.MouseUp, AddressOf Parent_MouseUp
            AddHandler Me.Parent.MouseMove, AddressOf Parent_MouseMove
        End Sub

        Private Sub AlphaImageButton_EnabledChanged(ByVal sender As Object, ByVal e As EventArgs)
            Refresh()
        End Sub

        Private Sub Parent_MouseDown(ByVal sender As Object, ByVal e As MouseEventArgs)
            If Not Me.Visible OrElse Not Me.Enabled OrElse Not HitTest(e.X, e.Y) Then
                Return
            End If

            _pushed = True
            _hover = True

            Refresh()
        End Sub

        Private Sub Parent_MouseUp(ByVal sender As Object, ByVal e As MouseEventArgs)
            If Not Me.Visible OrElse Not Me.Enabled OrElse Not _pushed OrElse Not HitTest(e.X, e.Y) Then
                Return
            End If

            _pushed = False
            _hover = False

            Refresh()

            Me.OnClick(Nothing)
        End Sub

        Private Sub Parent_MouseMove(ByVal sender As Object, ByVal e As MouseEventArgs)
            If Not Me.Visible OrElse Not Me.Enabled OrElse Not _pushed Then
                Return
            End If

            Dim hit As Boolean = HitTest(e.X, e.Y)
            If hit = _hover Then
                Return
            End If

            _hover = hit

            Refresh()
        End Sub

        ''' <summary>
        ''' Draws the button according to its current state.
        ''' </summary>
        Public Overrides Sub Draw(ByVal gx As Graphics)
            ' Draw the background image
            Dim image As AlphaImage = Nothing

            If Not Me.Enabled Then
                image = _disabledBackgroundImage
            ElseIf _pushed AndAlso _hover Then
                image = _activeBackgroundImage
            Else
                image = _backgroundImage
            End If

            If image IsNot Nothing Then
                image.Draw(gx, Me.Bounds)
            End If

            ' Draw the text if any
            If Not String.IsNullOrEmpty(Me.Text) Then
                Dim stringSize As SizeF = gx.MeasureString(Me.Text, Me.Font)
                Dim stringPosX As Single = Me.Bounds.X + ((Me.Bounds.Width / 2) - (stringSize.Width / 2))
                Dim stringPosY As Single = Me.Bounds.Y + ((Me.Bounds.Height / 2) - (stringSize.Height / 2))
                gx.DrawString(Me.Text, Me.Font, New SolidBrush(Me.ForeColor), stringPosX, stringPosY)
            End If
        End Sub
    End Class
End Namespace
