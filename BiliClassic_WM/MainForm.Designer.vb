<Global.Microsoft.VisualBasic.CompilerServices.DesignerGenerated()> _
Partial Public Class MainForm
    Inherits System.Windows.Forms.Form

    '窗体重写释放，以清理组件列表。
    <System.Diagnostics.DebuggerNonUserCode()> _
    Protected Overrides Sub Dispose(ByVal disposing As Boolean)
        If disposing AndAlso components IsNot Nothing Then
            components.Dispose()
        End If
        MyBase.Dispose(disposing)
    End Sub

    'Windows 窗体设计器所必需的
Private components As System.ComponentModel.IContainer
    Private WithEvents mainMenu1 As System.Windows.Forms.MainMenu
    Private WithEvents mnuSettings As System.Windows.Forms.MenuItem
    Private WithEvents mnuStream As System.Windows.Forms.MenuItem
    Private WithEvents mnuOffline As System.Windows.Forms.MenuItem
    Private WithEvents mnuHistory As System.Windows.Forms.MenuItem
    Private WithEvents mnuCheckUpdate As System.Windows.Forms.MenuItem
    Private WithEvents mnuWebsite As System.Windows.Forms.MenuItem
    Private WithEvents mnuMine As System.Windows.Forms.MenuItem
    Private WithEvents mnuProfile As System.Windows.Forms.MenuItem
    Private WithEvents mnuLogin As System.Windows.Forms.MenuItem
    Private WithEvents mnuExit As System.Windows.Forms.MenuItem
    Private WithEvents Panel1 As System.Windows.Forms.Panel
    Private WithEvents btnTestNet As System.Windows.Forms.Button
    Private WithEvents btnPlayAv706 As System.Windows.Forms.Button
Private WithEvents btnPlayStream As System.Windows.Forms.Button
    Private WithEvents btnPlayOffline As System.Windows.Forms.Button
Private WithEvents btnLogin As System.Windows.Forms.Button
    Private WithEvents btnMine As System.Windows.Forms.Button
    Private WithEvents txtSearch As System.Windows.Forms.TextBox
    Private WithEvents btnSearch As System.Windows.Forms.Button
    Private WithEvents picQr As System.Windows.Forms.PictureBox
    Private WithEvents txtResult As System.Windows.Forms.TextBox
    Private WithEvents lstSearch As System.Windows.Forms.ListView

    '注意: 以下过程是 Windows 窗体设计器所必需的
    '可以使用 Windows 窗体设计器修改它。
    '不要使用代码编辑器修改它。
    <System.Diagnostics.DebuggerStepThrough()> _
Private Sub InitializeComponent()
        Me.mainMenu1 = New System.Windows.Forms.MainMenu
        Me.mnuSettings = New System.Windows.Forms.MenuItem
        Me.mnuStream = New System.Windows.Forms.MenuItem
        Me.mnuOffline = New System.Windows.Forms.MenuItem
        Me.mnuHistory = New System.Windows.Forms.MenuItem
        Me.mnuCheckUpdate = New System.Windows.Forms.MenuItem
        Me.mnuWebsite = New System.Windows.Forms.MenuItem
        Me.mnuMine = New System.Windows.Forms.MenuItem
        Me.mnuProfile = New System.Windows.Forms.MenuItem
        Me.mnuLogin = New System.Windows.Forms.MenuItem
        Me.mnuExit = New System.Windows.Forms.MenuItem
        Me.Panel1 = New System.Windows.Forms.Panel
        Me.btnSearch = New System.Windows.Forms.Button
        Me.txtSearch = New System.Windows.Forms.TextBox
        Me.btnTestNet = New System.Windows.Forms.Button
        Me.btnPlayAv706 = New System.Windows.Forms.Button
        Me.btnPlayStream = New System.Windows.Forms.Button
        Me.btnPlayOffline = New System.Windows.Forms.Button
        Me.btnLogin = New System.Windows.Forms.Button
        Me.btnMine = New System.Windows.Forms.Button
        Me.picQr = New System.Windows.Forms.PictureBox
        Me.txtResult = New System.Windows.Forms.TextBox
        Me.lstSearch = New System.Windows.Forms.ListView
        Me.Panel1.SuspendLayout()
        Me.SuspendLayout()
        '
        'mainMenu1
        '
        Me.mainMenu1.MenuItems.Add(Me.mnuMine)
        Me.mainMenu1.MenuItems.Add(Me.mnuSettings)
        Me.mainMenu1.MenuItems.Add(Me.mnuExit)
        '
        'mnuSettings
        '
        Me.mnuSettings.Text = "设置"
        Me.mnuSettings.MenuItems.Add(Me.mnuStream)
        Me.mnuSettings.MenuItems.Add(Me.mnuOffline)
        Me.mnuSettings.MenuItems.Add(Me.mnuCheckUpdate)
        Me.mnuSettings.MenuItems.Add(Me.mnuWebsite)
        '
        'mnuStream
        '
        Me.mnuStream.Text = "流式播放"
        '
        'mnuOffline
        '
        Me.mnuOffline.Text = "离线播放"
        '
        'mnuHistory
        '
        Me.mnuHistory.Text = "历史记录"
        '
        'mnuCheckUpdate
        '
        Me.mnuCheckUpdate.Text = "检查更新"
        '
        'mnuWebsite
        '
        Me.mnuWebsite.Text = "官网"
        '
        'mnuMine
        '
        Me.mnuMine.Text = "我的"
        Me.mnuMine.MenuItems.Add(Me.mnuProfile)
        Me.mnuMine.MenuItems.Add(Me.mnuLogin)
        Me.mnuMine.MenuItems.Add(Me.mnuHistory)
        '
        'mnuProfile
        '
        Me.mnuProfile.Text = "个人中心"
        '
        'mnuLogin
        '
        Me.mnuLogin.Text = "扫码登录"
        '
        'mnuExit
        '
        Me.mnuExit.Text = "退出"
        '
        'Panel1
        '
        Me.Panel1.BackColor = System.Drawing.Color.FromArgb(CType(CType(216, Byte), Integer), CType(CType(109, Byte), Integer), CType(CType(165, Byte), Integer))
        Me.Panel1.Controls.Add(Me.btnSearch)
        Me.Panel1.Controls.Add(Me.txtSearch)
        Me.Panel1.Dock = System.Windows.Forms.DockStyle.Top
        Me.Panel1.Location = New System.Drawing.Point(0, 0)
        Me.Panel1.Name = "Panel1"
        Me.Panel1.Size = New System.Drawing.Size(240, 38)
        '
        'btnSearch
        '
        Me.btnSearch.Location = New System.Drawing.Point(194, 4)
        Me.btnSearch.Name = "btnSearch"
        Me.btnSearch.Size = New System.Drawing.Size(62, 30)
        Me.btnSearch.TabIndex = 0
        Me.btnSearch.Text = "搜索"
        '
        'txtSearch
        '
        Me.txtSearch.Location = New System.Drawing.Point(70, 8)
        Me.txtSearch.Name = "txtSearch"
        Me.txtSearch.Size = New System.Drawing.Size(120, 21)
        Me.txtSearch.TabIndex = 1
        '
        'btnTestNet
        '
        Me.btnTestNet.Location = New System.Drawing.Point(10, 50)
        Me.btnTestNet.Name = "btnTestNet"
        Me.btnTestNet.Size = New System.Drawing.Size(200, 40)
        Me.btnTestNet.TabIndex = 8
        Me.btnTestNet.Text = "测试网络连接B站"
        '
        'btnPlayAv706
        '
        Me.btnPlayAv706.Location = New System.Drawing.Point(10, 90)
        Me.btnPlayAv706.Name = "btnPlayAv706"
        Me.btnPlayAv706.Size = New System.Drawing.Size(200, 40)
        Me.btnPlayAv706.TabIndex = 7
        Me.btnPlayAv706.Text = "播放 av706"
        '
        'btnPlayStream
        '
        Me.btnPlayStream.Location = New System.Drawing.Point(10, 130)
        Me.btnPlayStream.Name = "btnPlayStream"
        Me.btnPlayStream.Size = New System.Drawing.Size(200, 40)
        Me.btnPlayStream.TabIndex = 6
        Me.btnPlayStream.Text = "流式播放"
        Me.btnPlayStream.Visible = False
        '
        'btnPlayOffline
        '
        Me.btnPlayOffline.Location = New System.Drawing.Point(10, 170)
        Me.btnPlayOffline.Name = "btnPlayOffline"
        Me.btnPlayOffline.Size = New System.Drawing.Size(200, 40)
        Me.btnPlayOffline.TabIndex = 5
        Me.btnPlayOffline.Text = "离线播放"
        Me.btnPlayOffline.Visible = False
        '
        'btnLogin
        '
        Me.btnLogin.Location = New System.Drawing.Point(10, 250)
        Me.btnLogin.Name = "btnLogin"
        Me.btnLogin.Size = New System.Drawing.Size(200, 40)
        Me.btnLogin.TabIndex = 3
        Me.btnLogin.Text = "扫码登录"
        Me.btnLogin.Visible = False
        '
        'btnMine
        '
        Me.btnMine.Location = New System.Drawing.Point(10, 250)
        Me.btnMine.Name = "btnMine"
        Me.btnMine.Size = New System.Drawing.Size(200, 40)
        Me.btnMine.TabIndex = 2
        Me.btnMine.Text = "我的"
        Me.btnMine.Visible = False
        '
        'picQr
        '
        Me.picQr.Location = New System.Drawing.Point(10, 285)
        Me.picQr.Name = "picQr"
        Me.picQr.Size = New System.Drawing.Size(200, 200)
        Me.picQr.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage
        '
        'txtResult
        '
        Me.txtResult.Location = New System.Drawing.Point(10, 465)
        Me.txtResult.Multiline = True
        Me.txtResult.Name = "txtResult"
        Me.txtResult.ScrollBars = System.Windows.Forms.ScrollBars.Vertical
        Me.txtResult.Size = New System.Drawing.Size(460, 240)
        Me.txtResult.TabIndex = 0
        '
        'lstSearch
        '
        Me.lstSearch.BackColor = System.Drawing.Color.White
        Me.lstSearch.FullRowSelect = True
        Me.lstSearch.Location = New System.Drawing.Point(10, 465)
        Me.lstSearch.Name = "lstSearch"
        Me.lstSearch.Size = New System.Drawing.Size(460, 240)
        Me.lstSearch.TabIndex = 1
        Me.lstSearch.View = System.Windows.Forms.View.List
        Me.lstSearch.Visible = False
        '
        'MainForm
        '
        Me.AutoScaleMode = System.Windows.Forms.AutoScaleMode.None
        Me.ClientSize = New System.Drawing.Size(240, 320)
        Me.Controls.Add(Me.txtResult)
        Me.Controls.Add(Me.lstSearch)
        Me.Controls.Add(Me.picQr)
        Me.Controls.Add(Me.btnLogin)
        Me.Controls.Add(Me.btnMine)
        Me.Controls.Add(Me.btnPlayOffline)
        Me.Controls.Add(Me.btnPlayStream)
        Me.Controls.Add(Me.btnPlayAv706)
        Me.Controls.Add(Me.btnTestNet)
        Me.Controls.Add(Me.Panel1)
        Me.FormBorderStyle = System.Windows.Forms.FormBorderStyle.None
        Me.Menu = Me.mainMenu1
        Me.Name = "MainForm"
        Me.Text = "BiliClassic"
        Me.Panel1.ResumeLayout(False)
        Me.ResumeLayout(False)

    End Sub

End Class