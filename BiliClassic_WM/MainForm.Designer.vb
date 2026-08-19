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
    Private WithEvents mnuPlayMode As System.Windows.Forms.MenuItem
    Private WithEvents mnuStream As System.Windows.Forms.MenuItem
    Private WithEvents mnuOffline As System.Windows.Forms.MenuItem
    Private WithEvents mnuConvert As System.Windows.Forms.MenuItem
    Private WithEvents mnuConvertFormat As System.Windows.Forms.MenuItem
    Private WithEvents mnuConvertH264 As System.Windows.Forms.MenuItem
    Private WithEvents mnuConvertMpeg4 As System.Windows.Forms.MenuItem
    Private WithEvents mnuImageThreads As System.Windows.Forms.MenuItem
    Private WithEvents mnuImageThreadsSingle As System.Windows.Forms.MenuItem
    Private WithEvents mnuImageThreadsDual As System.Windows.Forms.MenuItem
    Private WithEvents mnuPlayer As System.Windows.Forms.MenuItem
    Private WithEvents mnuPlayerTcpmp As System.Windows.Forms.MenuItem
    Private WithEvents mnuPlayerCorePlayer As System.Windows.Forms.MenuItem
    Private WithEvents mnuPlayerOstwind As System.Windows.Forms.MenuItem
    Private WithEvents mnuHistory As System.Windows.Forms.MenuItem
    Private WithEvents mnuFavs As System.Windows.Forms.MenuItem
    Private WithEvents mnuCheckUpdate As System.Windows.Forms.MenuItem
    Private WithEvents mnuWebsite As System.Windows.Forms.MenuItem
    Private WithEvents mnuMine As System.Windows.Forms.MenuItem
    Private WithEvents mnuProfile As System.Windows.Forms.MenuItem
    Private WithEvents mnuLogin As System.Windows.Forms.MenuItem
    Private WithEvents mnuExit As System.Windows.Forms.MenuItem
    Private WithEvents Panel1 As System.Windows.Forms.Panel
Private WithEvents btnPlayStream As System.Windows.Forms.Button
    Private WithEvents btnPlayOffline As System.Windows.Forms.Button
    Private WithEvents txtSearch As System.Windows.Forms.TextBox
    Private WithEvents btnSearch As System.Windows.Forms.PictureBox
    Private WithEvents btnBack As System.Windows.Forms.PictureBox
    Private WithEvents txtResult As System.Windows.Forms.TextBox
    Private WithEvents lstSearch As System.Windows.Forms.ListView
    Private WithEvents recPanel As System.Windows.Forms.Panel
    Private WithEvents detailPanel As System.Windows.Forms.Panel

    '注意: 以下过程是 Windows 窗体设计器所必需的
    '可以使用 Windows 窗体设计器修改它。
    '不要使用代码编辑器修改它。
    <System.Diagnostics.DebuggerStepThrough()> _
Private Sub InitializeComponent()
        Me.mainMenu1 = New System.Windows.Forms.MainMenu
        Me.mnuSettings = New System.Windows.Forms.MenuItem
        Me.mnuPlayMode = New System.Windows.Forms.MenuItem
        Me.mnuStream = New System.Windows.Forms.MenuItem
        Me.mnuOffline = New System.Windows.Forms.MenuItem
        Me.mnuConvert = New System.Windows.Forms.MenuItem
        Me.mnuConvertFormat = New System.Windows.Forms.MenuItem
        Me.mnuConvertH264 = New System.Windows.Forms.MenuItem
        Me.mnuConvertMpeg4 = New System.Windows.Forms.MenuItem
        Me.mnuImageThreads = New System.Windows.Forms.MenuItem
        Me.mnuImageThreadsSingle = New System.Windows.Forms.MenuItem
        Me.mnuImageThreadsDual = New System.Windows.Forms.MenuItem
        Me.mnuPlayer = New System.Windows.Forms.MenuItem
        Me.mnuPlayerTcpmp = New System.Windows.Forms.MenuItem
        Me.mnuPlayerCorePlayer = New System.Windows.Forms.MenuItem
        Me.mnuPlayerOstwind = New System.Windows.Forms.MenuItem
        Me.mnuHistory = New System.Windows.Forms.MenuItem
        Me.mnuFavs = New System.Windows.Forms.MenuItem
        Me.mnuCheckUpdate = New System.Windows.Forms.MenuItem
        Me.mnuWebsite = New System.Windows.Forms.MenuItem
        Me.mnuMine = New System.Windows.Forms.MenuItem
        Me.mnuProfile = New System.Windows.Forms.MenuItem
        Me.mnuLogin = New System.Windows.Forms.MenuItem
        Me.mnuExit = New System.Windows.Forms.MenuItem
        Me.Panel1 = New System.Windows.Forms.Panel
        Me.btnSearch = New System.Windows.Forms.PictureBox
        Me.txtSearch = New System.Windows.Forms.TextBox
        Me.btnPlayStream = New System.Windows.Forms.Button
        Me.btnPlayOffline = New System.Windows.Forms.Button
        Me.txtResult = New System.Windows.Forms.TextBox
        Me.lstSearch = New System.Windows.Forms.ListView
        Me.recPanel = New System.Windows.Forms.Panel
        Me.detailPanel = New System.Windows.Forms.Panel
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
        Me.mnuSettings.MenuItems.Add(Me.mnuPlayMode)
        Me.mnuSettings.MenuItems.Add(Me.mnuConvert)
        Me.mnuSettings.MenuItems.Add(Me.mnuConvertFormat)
        Me.mnuSettings.MenuItems.Add(Me.mnuImageThreads)
        Me.mnuSettings.MenuItems.Add(Me.mnuPlayer)
        Me.mnuSettings.MenuItems.Add(Me.mnuCheckUpdate)
        Me.mnuSettings.MenuItems.Add(Me.mnuWebsite)
        '
        'mnuPlayMode
        '
        Me.mnuPlayMode.Text = "播放方式"
        Me.mnuPlayMode.MenuItems.Add(Me.mnuStream)
        Me.mnuPlayMode.MenuItems.Add(Me.mnuOffline)
        '
        'mnuStream
        '
        Me.mnuStream.Text = "流式播放"
        '
        'mnuOffline
        '
        Me.mnuOffline.Text = "离线播放"
        '
        'mnuConvert
        '
        Me.mnuConvert.Text = "转码播放"
        '
        'mnuConvertFormat
        '
        Me.mnuConvertFormat.Text = "转码格式"
        Me.mnuConvertFormat.MenuItems.Add(Me.mnuConvertH264)
        Me.mnuConvertFormat.MenuItems.Add(Me.mnuConvertMpeg4)
        '
        'mnuImageThreads
        '
        Me.mnuImageThreads.Text = "图片加载线程"
        Me.mnuImageThreads.MenuItems.Add(Me.mnuImageThreadsSingle)
        Me.mnuImageThreads.MenuItems.Add(Me.mnuImageThreadsDual)
        '
        'mnuImageThreadsSingle
        '
        Me.mnuImageThreadsSingle.Text = "单线程"
        '
        'mnuImageThreadsDual
        '
        Me.mnuImageThreadsDual.Text = "双线程"
        '
        'mnuPlayer
        '
        Me.mnuPlayer.Text = "播放器"
        Me.mnuPlayer.MenuItems.Add(Me.mnuPlayerTcpmp)
        Me.mnuPlayer.MenuItems.Add(Me.mnuPlayerCorePlayer)
        Me.mnuPlayer.MenuItems.Add(Me.mnuPlayerOstwind)
        '
        'mnuPlayerTcpmp
        '
        Me.mnuPlayerTcpmp.Text = "TCPMP"
        '
        'mnuPlayerCorePlayer
        '
        Me.mnuPlayerCorePlayer.Text = "CorePlayer"
        '
        'mnuPlayerOstwind
        '
        Me.mnuPlayerOstwind.Text = "OstwindPlayer（内置）"
        '
        'mnuConvertH264
        '
        Me.mnuConvertH264.Text = "H.264 Baseline"
        '
        'mnuConvertMpeg4
        '
        Me.mnuConvertMpeg4.Text = "MPEG-4"
        '
        'mnuHistory
        '
        Me.mnuHistory.Text = "播放历史"
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
        Me.mnuMine.MenuItems.Add(Me.mnuFavs)
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
        'mnuFavs
        '
        Me.mnuFavs.Text = "我的收藏"
        '
        'mnuExit
        '
        Me.mnuExit.Text = "退出"
        '
        'Panel1
        '
        Me.Panel1.BackColor = System.Drawing.Color.White
        Me.Panel1.Controls.Add(Me.btnSearch)
        Me.Panel1.Controls.Add(Me.txtSearch)
        Me.Panel1.Dock = System.Windows.Forms.DockStyle.Top
        Me.Panel1.Location = New System.Drawing.Point(0, 0)
        Me.Panel1.Name = "Panel1"
        Me.Panel1.Size = New System.Drawing.Size(240, 38)
        Me.Panel1.Visible = False
        '
        'btnSearch
        '
        Me.btnSearch.Location = New System.Drawing.Point(194, 4)
        Me.btnSearch.Name = "btnSearch"
        Me.btnSearch.Size = New System.Drawing.Size(32, 32)
        '
        'txtSearch
        '
        Me.txtSearch.Location = New System.Drawing.Point(70, 8)
        Me.txtSearch.Name = "txtSearch"
        Me.txtSearch.Size = New System.Drawing.Size(120, 21)
        Me.txtSearch.TabIndex = 1
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
        'txtResult
        '
        Me.txtResult.Location = New System.Drawing.Point(10, 465)
        Me.txtResult.Multiline = True
        Me.txtResult.Name = "txtResult"
        Me.txtResult.ScrollBars = System.Windows.Forms.ScrollBars.Vertical
        Me.txtResult.Size = New System.Drawing.Size(460, 240)
        Me.txtResult.TabIndex = 0
        Me.txtResult.Visible = False
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
        'recPanel
        '
        Me.recPanel.AutoScroll = True
        Me.recPanel.Location = New System.Drawing.Point(4, 44)
        Me.recPanel.Name = "recPanel"
        Me.recPanel.Size = New System.Drawing.Size(460, 240)
        Me.recPanel.Visible = False
        '
        'detailPanel
        '
        Me.detailPanel.AutoScroll = True
        Me.detailPanel.Location = New System.Drawing.Point(4, 44)
        Me.detailPanel.Name = "detailPanel"
        Me.detailPanel.Size = New System.Drawing.Size(460, 240)
        Me.detailPanel.Visible = False
        '
        'MainForm
        '
        Me.AutoScaleMode = System.Windows.Forms.AutoScaleMode.None
        Me.ClientSize = New System.Drawing.Size(240, 320)
        Me.Controls.Add(Me.txtResult)
        Me.Controls.Add(Me.lstSearch)
        Me.Controls.Add(Me.recPanel)
        Me.Controls.Add(Me.detailPanel)
        Me.Controls.Add(Me.btnPlayOffline)
        Me.Controls.Add(Me.btnPlayStream)
        Me.Controls.Add(Me.Panel1)
        Me.FormBorderStyle = System.Windows.Forms.FormBorderStyle.None
        Me.Menu = Me.mainMenu1
        Me.Name = "MainForm"
        Me.Text = "BiliClassic"
        Me.Panel1.ResumeLayout(False)
        Me.ResumeLayout(False)

    End Sub

End Class