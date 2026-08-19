' =====================================================================
' PlayerModel（VB.NET，兼容 .NET Compact Framework 2.0）
'
' 来源：DirectShow.NetCF（Alex Mogurenko）
'   C# 原文件：DirectShowNETCF.Controls\PlayerControl\PlayerControl\PlayerModel.cs
'
' 转译说明：
'   - 原 C# 调用 LoggerDSCF.Logger.Instance.WriteLog(...) 记录日志；本工程
'     （BiliClassic_WM）没有该 Logger 类，故这些日志调用行保留为注释。
'   - 原 C# 使用 CLSID_.DMOWrapperFilter / CLSID_.DMO_Mp3 /
'     CLSID_.DMOCATEGORY_AUDIO_DECODER（位于 DirectShowGuids.cs）。本工程
'     DirectShowGuids.vb 未定义这三个 GUID，故在本类顶部补齐为
'     Private Shared ReadOnly 字段，InitDecoder() 逻辑保持完整。
'   - lock -> SyncLock；out/ref -> ByRef；Stop -> [Stop]；
'     do{}while -> Do ... Loop While。
' =====================================================================

Imports System
Imports System.Runtime.InteropServices

Public Class PlayerModel : Implements IDisposable
    '#region private declarations

    Private _graphBuilder As IGraphBuilder = Nothing
    Private _videoWindow As IVideoWindow = Nothing
    Private _mediaControl As IMediaControl = Nothing
    Private _sourceFilter As IBaseFilter = Nothing
    Private _renderer As IBaseFilter = Nothing
    Private _mediaEventEx As IMediaEventEx = Nothing
    Private _mediaSeeking As IMediaSeeking = Nothing
    Private _basicAudio As IBasicAudio = Nothing
    Private _basicVideo As IBasicVideo = Nothing
    Private _decoder As IBaseFilter = Nothing
    Private _locker As Object = New Object()

    ' 原 C# DirectShowGuids.cs 中的三个 GUID（本工程 DirectShowGuids.vb 未定义，此处补齐）。
    Private Shared ReadOnly DMOWrapperFilter As Guid = New Guid("94297043-BD82-4DFD-B0DE-8177739C6D20")
    Private Shared ReadOnly DMO_Mp3 As Guid = New Guid("86A495AC-64CE-42DE-A13A-321ACC0F02DB")
    Private Shared ReadOnly DMOCATEGORY_AUDIO_DECODER As Guid = New Guid("57f2db8b-e6bb-4513-9d43-dcd2a6593125")

    '#endregion

    '#region Events

    Public Event MediaFailed As EventHandler

    '#endregion

    Public Sub New()
    End Sub

    ' 诊断日志（写入程序目录 playerlog.txt，供 WM 实机排查）。
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

    Public Sub Dispose() Implements IDisposable.Dispose
        Clear(True)
    End Sub

    ' 显式 QueryInterface 获取 IVideoWindow。
    ' CType(obj, ComImport接口) 在 CF 2.0 上不执行 COM QI，必须手动用 IUnknown 查询。
    Private Function QueryVideoWindow(ByVal from As Object) As IVideoWindow
        Return QueryByIid(from, New Guid("56A868B4-0AD4-11CE-B03A-0020AF0BA770"))
    End Function

    ' 显式 QueryInterface 获取 IMediaControl。
    Private Function QueryMediaControl(ByVal from As Object) As IMediaControl
        Return QueryByIid(from, New Guid("56A868B1-0AD4-11CE-B03A-0020AF0BA770"))
    End Function

    Private Function QueryMediaEventEx(ByVal from As Object) As IMediaEventEx
        Return QueryByIid(from, New Guid("56A868C0-0AD4-11CE-B03A-0020AF0BA770"))
    End Function

    Private Function QueryMediaSeeking(ByVal from As Object) As IMediaSeeking
        Return QueryByIid(from, New Guid("36B73880-C2C8-11CF-8B46-00805F6CEF60"))
    End Function

    Private Function QueryBasicAudio(ByVal from As Object) As IBasicAudio
        Return QueryByIid(from, New Guid("56A868B3-0AD4-11CE-B03A-0020AF0BA770"))
    End Function

    Private Function QueryBasicVideo(ByVal from As Object) As IBasicVideo
        Return QueryByIid(from, New Guid("56A868B6-0AD4-11CE-B03A-0020AF0BA770"))
    End Function

    Private Function QueryFileSourceFilter(ByVal from As Object) As IFileSourceFilter
        Return QueryByIid(from, New Guid("56A868A6-0AD4-11CE-B03A-0020AF0BA770"))
    End Function

    ' 核心：IUnknown -> QueryInterface -> 包装为托管接口对象。
    Private Function QueryByIid(ByVal from As Object, ByVal iid As Guid) As Object
        Dim result As Object = Nothing
        Dim pUnk As IntPtr = IntPtr.Zero
        Dim pInt As IntPtr = IntPtr.Zero
        Try
            If from Is Nothing Then
                Return Nothing
            End If
            pUnk = Marshal.GetIUnknownForObject(from)
            Dim hr As Integer = Marshal.QueryInterface(pUnk, iid, pInt)
            If hr = 0 Then
                result = Marshal.GetObjectForIUnknown(pInt)
            End If
        Catch ex As Exception
            result = Nothing
        Finally
            If pInt <> IntPtr.Zero Then
                Marshal.Release(pInt)
            End If
            If pUnk <> IntPtr.Zero Then
                Marshal.Release(pUnk)
            End If
        End Try
        Return result
    End Function

    Public Sub LoadFile(ByVal filePath As String)
        Log("LoadFile enter: " & filePath)
        Clear(True)
        InitGraph()

        If _graphBuilder Is Nothing Then
            Log("LoadFile: graphBuilder is Nothing")
            OnMediaFailed()
            Return
        End If

        ' for wmv/wma/mp3 and some network files graph can find source filter
        ' without problem by it self, otherwise we will ask it to render files as we want
        Do
            Dim hr As Integer = 0
            Try
                hr = _graphBuilder.RenderFile(filePath, Nothing)
                Log("RenderFile hr=0x" & hr.ToString("X8"))
            Catch exR As Exception
                hr = -1
                Log("RenderFile EXCEPTION: " & exR.GetType().Name & " | " & exR.Message)
            End Try
            If hr > -1 Then
                ' LoggerDSCF.Logger.Instance.WriteLog("Rendered correct")
                ' 用显式 QueryInterface 获取 IVideoWindow（CType 对 ComImport 接口不执行 QI）。
                _videoWindow = QueryVideoWindow(_graphBuilder)
                Log("videoWindow via QI, isNothing=" & (_videoWindow Is Nothing).ToString())
                Exit Do
            End If
            ' LoggerDSCF.Logger.Instance.WriteLog("Lets try render file manualy")
            Log("RenderFile failed, trying manual graph")
            Try
                Marshal.ReleaseComObject(_graphBuilder)
            Catch exRel As Exception
            End Try
            _graphBuilder = Nothing
            InitGraph()

            InitSource()
            hr = _graphBuilder.AddFilter(_sourceFilter, filePath)
            Log("manual: AddFilter hr=0x" & hr.ToString("X8"))
            If hr < 0 Then
                ' LoggerDSCF.Logger.Instance.WriteLog("Cannot add source to graph...")
                OnMediaFailed()
                Return
            End If

            ' LoggerDSCF.Logger.Instance.WriteLog("Filter Added")
            Dim srcIF As IFileSourceFilter = Nothing
            Try
                srcIF = QueryFileSourceFilter(_sourceFilter)
                Log("manual: IFileSourceFilter isNothing=" & (srcIF Is Nothing).ToString())
            Catch exIF As Exception
                srcIF = Nothing
                Log("manual: IFileSourceFilter FAIL: " & exIF.Message)
            End Try
            If srcIF Is Nothing Then
                OnMediaFailed()
                Return
            End If
            hr = srcIF.Load(filePath, Nothing)
            Log("manual: source.Load hr=0x" & hr.ToString("X8"))
            If hr < 0 Then
                ' LoggerDSCF.Logger.Instance.WriteLog("Cannot load file...")
                OnMediaFailed()
                Return
            End If

            ' InitRenderer();
            '
            ' hr = _graphBuilder.AddFilter(_renderer, "VideoRenderer");
            ' if (hr < 0)
            ' {
            '     OnMediaFailed();
            '     return;
            ' }

            Dim res_ As Boolean = DirectShowHelper.RenderPins(_graphBuilder, _sourceFilter) ' DirectShowHelper.ConnectPins(_graphBuilder, _sourceFilter, _splitter);
            Log("manual: RenderPins res=" & res_.ToString())

            If Not res_ Then
                ' 建图失败（解码器/渲染器缺失），直接失败返回，避免后续枚举死锁。
                Log("manual: RenderPins failed, cannot build graph")
                OnMediaFailed()
                Return
            End If

            If filePath.ToUpper().IndexOf(".MP3") <> -1 Then
                ' LoggerDSCF.Logger.Instance.WriteLog("Load dmo decoder")
                InitDecoder()
                If _decoder Is Nothing Then
                    ' LoggerDSCF.Logger.Instance.WriteLog("cannot init dmo decoder")
                    OnMediaFailed()
                    Return
                End If

                ' LoggerDSCF.Logger.Instance.WriteLog("inited")

                hr = _graphBuilder.AddFilter(_decoder, "DMO_Decoder")
                If hr < 0 Then
                    ' LoggerDSCF.Logger.Instance.WriteLog("Cannot add decoder to graph...")
                    OnMediaFailed()
                    Return
                End If

                ' LoggerDSCF.Logger.Instance.WriteLog("decoder added to graph...")

                If Not DirectShowHelper.ConnectPins(_graphBuilder, _sourceFilter, _decoder) Then
                    ' LoggerDSCF.Logger.Instance.WriteLog("Cannot connect source filter and decoder")
                    OnMediaFailed()
                    Return
                End If

                ' LoggerDSCF.Logger.Instance.WriteLog("decoder and source connected")

                If Not DirectShowHelper.RenderPins(_graphBuilder, _decoder) Then
                    ' LoggerDSCF.Logger.Instance.WriteLog("Cannot connect decoder pins")
                    OnMediaFailed()
                    Return
                End If
                ' LoggerDSCF.Logger.Instance.WriteLog("Everything done correct")
            End If
        Loop While False

        If _videoWindow Is Nothing Then
            ' 遍历 graph 内 filter 显式 QI 找视频窗口（渲染器）。
            Dim filters As New CFilterList()
            filters.Assign(_graphBuilder)
            Log("filters enumerator count=" & filters.Count.ToString())
            Dim i As Integer
            For i = 0 To filters.Count - 1
                Dim vw As IVideoWindow = QueryVideoWindow(filters(i))
                Log("  filter[" & i.ToString() & "] vw=" & (vw IsNot Nothing).ToString())
                If vw IsNot Nothing Then
                    _videoWindow = vw
                    Exit For
                End If
            Next
            filters.Free()
            filters = Nothing
        End If

        ' 全部用显式 QI（CType 对 ComImport 接口不执行 COM QI）。
        _mediaEventEx = QueryMediaEventEx(_graphBuilder)
        _mediaControl = QueryMediaControl(_graphBuilder)
        _mediaSeeking = QueryMediaSeeking(_graphBuilder)
        _basicAudio = QueryBasicAudio(_graphBuilder)
        _basicVideo = QueryBasicVideo(_graphBuilder)
        Log("LoadFile done: vw=" & (_videoWindow IsNot Nothing).ToString() & " mc=" & (_mediaControl IsNot Nothing).ToString() & " ms=" & (_mediaSeeking IsNot Nothing).ToString())

        Seek(TimeSpan.Zero)
    End Sub

    Public Sub Play()
        If _mediaControl Is Nothing Then
            Log("Play: mediaControl is Nothing")
            OnMediaFailed()
            Return
        End If

        Dim hr As Integer = _mediaControl.Run()
        Log("Play: Run hr=0x" & hr.ToString("X8"))

        If hr < 0 Then
            OnMediaFailed()
            Return
        End If
    End Sub

    Public Sub Pause()
        If _mediaControl IsNot Nothing Then
            _mediaControl.Pause()
        End If
    End Sub

    Public Sub [Stop]()
        Clear(True)
    End Sub

    Public Sub Seek(ByVal position As TimeSpan)
        If _mediaSeeking Is Nothing Then
            Return
        End If

        Dim stopPos As Long = 0
        Dim current As Long = position.Ticks
        SyncLock _locker
            _mediaSeeking.SetPositions(current, CInt(AmSeeking.AbsolutePositioning), stopPos, CInt(AmSeeking.NoPositioning))
        End SyncLock
    End Sub

    Public Function GetVolume() As Integer
        Dim result As Integer = 0
        If _basicAudio IsNot Nothing Then
            _basicAudio.get_Volume(result)
            result += 10000
        End If
        Return result
    End Function

    Public Sub SetVolume(ByVal volume As Integer)
        If _basicAudio IsNot Nothing Then
            _basicAudio.put_Volume(volume - 10000)
        End If
    End Sub

    Public Function GetBalance() As Integer
        Dim result As Integer = 0
        If _basicAudio IsNot Nothing Then
            _basicAudio.get_Balance(result)
        End If
        Return result
    End Function

    Public Sub SetBalance(ByVal balance As Integer)
        If _basicAudio IsNot Nothing Then
            _basicAudio.put_Balance(balance)
        End If
    End Sub

    Public Function GetWidth() As Integer
        Dim result As Integer = 0
        If _basicVideo IsNot Nothing Then
            _basicVideo.get_VideoWidth(result)
        End If
        Return result
    End Function

    Public Function GetHeight() As Integer
        Dim result As Integer = 0
        If _basicVideo IsNot Nothing Then
            _basicVideo.get_VideoHeight(result)
        End If
        Return result
    End Function

    Public Function GetBitRate() As Integer
        Dim result As Integer = 0
        If _basicVideo IsNot Nothing Then
            _basicVideo.get_BitRate(result)
        End If
        Return result
    End Function

    Public Function GetCurrentPosition() As TimeSpan
        Dim currentPosition As TimeSpan = TimeSpan.Zero

        SyncLock _locker
            If _mediaSeeking IsNot Nothing Then
                Dim current As Long
                Dim hr As Integer = _mediaSeeking.GetCurrentPosition(current)
                If hr > -1 Then
                    currentPosition = New TimeSpan(current)
                End If
            End If
        End SyncLock

        Return currentPosition
    End Function

    Public Sub SetEventHendler(ByVal handle As IntPtr)
        _mediaEventEx.SetNotifyWindow(handle, CInt(NotifyMessages.WM_GRAPHNOTIFY), IntPtr.Zero)
    End Sub

    Public Function SetVideoWindow(ByVal handle As IntPtr, ByVal x As Integer, ByVal y As Integer, ByVal width As Integer, ByVal height As Integer) As Boolean
        If _videoWindow IsNot Nothing Then
            Log("SetVideoWindow: owner=" & handle.ToString())
            Dim hr As Integer = _videoWindow.put_Owner(handle)
            Log("  put_Owner hr=0x" & hr.ToString("X8"))
            hr = _videoWindow.SetWindowPosition(x, y, width, height)
            Log("  SetWindowPosition hr=0x" & hr.ToString("X8"))
            hr = _videoWindow.put_WindowStyle(&H40000000 Or &H2000000)
            Log("  put_WindowStyle hr=0x" & hr.ToString("X8"))
            hr = _videoWindow.put_MessageDrain(handle)
            hr = _videoWindow.put_Visible(-1)
            Log("  put_Visible hr=0x" & hr.ToString("X8"))
            Return hr > -1
        End If
        Log("SetVideoWindow: _videoWindow is Nothing")
        Return False

        ' filters.Free();
        ' filters = null;
    End Function

    Public Sub Resize(ByVal width As Integer, ByVal height As Integer)
        If _videoWindow IsNot Nothing Then
            _videoWindow.SetWindowPosition(0, 0, width, height)
        End If
    End Sub

    Public Sub SetVisible(ByVal visible As Boolean)
        If _videoWindow IsNot Nothing Then
            _videoWindow.put_Visible(IIf(visible, 0, -1))
        End If
    End Sub

    Public Function GetEvent(ByRef evCode As Integer, ByRef evParam1 As Integer, ByRef evParam2 As Integer) As Integer
        If _mediaEventEx Is Nothing Then
            evCode = 0
            evParam1 = 0
            evParam2 = 0
            Return -1
        End If

        Return _mediaEventEx.GetEvent(evCode, evParam1, evParam2, 0)
    End Function

    Public Function FreeEventParams(ByVal evCode As Integer, ByVal evParam1 As Integer, ByVal evParam2 As Integer) As Integer
        Return _mediaEventEx.FreeEventParams(evCode, evParam1, evParam2)
    End Function

    Public Function GetDuration() As TimeSpan
        Dim duration As Long
        Dim hr As Integer = _mediaSeeking.GetDuration(duration)
        If hr < 0 Then
            Return TimeSpan.Zero
        Else
            Return New TimeSpan(duration)
        End If
    End Function

    '#region private methods

    Private Sub InitGraph()
        Dim obj As Object = Nothing
        Dim clsid As Guid = CLSID_.FilterGraph
        Dim riid As Guid = IID_.IFilterGraph2
        Dim hr As Integer = PInvokes.CoCreateInstance(clsid, IntPtr.Zero, CLSCTX_.INPROC_SERVER, riid, obj)
        Log("CoCreateInstance FilterGraph hr=0x" & hr.ToString("X8") & " obj=" & (obj IsNot Nothing).ToString())
        Try
            _graphBuilder = CType(obj, IGraphBuilder)
        Catch exC As Exception
            _graphBuilder = Nothing
            Log("CType graphBuilder FAIL: " & exC.Message)
        End Try
        obj = Nothing
    End Sub

    Private Sub InitRenderer()
        Dim obj As Object = Nothing
        Dim clsid As Guid = CLSID_.VideoRenderer
        Dim riid As Guid = IID_.IBaseFilter
        Dim hr As Integer = PInvokes.CoCreateInstance(clsid, IntPtr.Zero, CLSCTX_.INPROC_SERVER, riid, obj)
        Log("InitRenderer hr=0x" & hr.ToString("X8") & " obj=" & (obj IsNot Nothing).ToString())
        Try
            _renderer = CType(obj, IBaseFilter)
        Catch exC As Exception
            _renderer = Nothing
        End Try
        obj = Nothing
    End Sub

    Private Sub InitSource()
        Dim obj As Object = Nothing
        Dim clsid As Guid = CLSID_.FileSource
        Dim riid As Guid = IID_.IBaseFilter
        Dim hr As Integer = PInvokes.CoCreateInstance(clsid, IntPtr.Zero, CLSCTX_.INPROC_SERVER, riid, obj)
        Log("InitSource hr=0x" & hr.ToString("X8") & " obj=" & (obj IsNot Nothing).ToString())
        Try
            _sourceFilter = CType(obj, IBaseFilter)
        Catch exC As Exception
            _sourceFilter = Nothing
            Log("InitSource CType FAIL: " & exC.Message)
        End Try
        obj = Nothing
    End Sub

    Private Sub InitDecoder()
        Dim obj As Object = Nothing
        Dim clsid As Guid = DMOWrapperFilter
        Dim riid As Guid = IID_.IBaseFilter
        PInvokes.CoCreateInstance(clsid, IntPtr.Zero, CLSCTX_.INPROC_SERVER, riid, obj)
        If obj IsNot Nothing Then
            _decoder = CType(obj, IBaseFilter)
            Dim wrapper As IDMOWrapperFilter = CType(_decoder, IDMOWrapperFilter)

            wrapper.Init(New CGuid(DMO_Mp3), New CGuid(DMOCATEGORY_AUDIO_DECODER))
        End If
        obj = Nothing
    End Sub

    Private Sub OnMediaFailed()
        RaiseEvent MediaFailed(Me, EventArgs.Empty)
    End Sub

    Private Sub Clear(ByVal dispose As Boolean)
        If _graphBuilder IsNot Nothing Then
            If _videoWindow IsNot Nothing Then
                _videoWindow.put_Visible(0)
                _videoWindow.put_Owner(IntPtr.Zero)
            End If

            If _mediaControl IsNot Nothing Then
                _mediaControl.[Stop]()
            End If

            If _mediaEventEx IsNot Nothing Then
                _mediaEventEx.SetNotifyWindow(IntPtr.Zero, CInt(NotifyMessages.WM_GRAPHNOTIFY), IntPtr.Zero)
            End If

            If _graphBuilder IsNot Nothing Then
                DirectShowHelper.ClearGraph(_graphBuilder, Nothing)
            End If

            If _sourceFilter IsNot Nothing Then
                Marshal.ReleaseComObject(_sourceFilter)
                _sourceFilter = Nothing
            End If

            If _decoder IsNot Nothing Then
                Marshal.ReleaseComObject(_decoder)
                _decoder = Nothing
            End If

            If _renderer IsNot Nothing Then
                Marshal.ReleaseComObject(_renderer)
                _renderer = Nothing
            End If

            ReleaseInterfaces(dispose)

            If _graphBuilder IsNot Nothing Then
                Marshal.ReleaseComObject(_graphBuilder)
                _graphBuilder = Nothing
            End If
        End If
    End Sub

    Private Sub ReleaseInterfaces(ByVal dispose As Boolean)
        If dispose Then
            Marshal.ReleaseComObject(_videoWindow)
            _videoWindow = Nothing
            Marshal.ReleaseComObject(_mediaControl)
            _mediaControl = Nothing
            Marshal.ReleaseComObject(_mediaEventEx)
            _mediaEventEx = Nothing
            Marshal.ReleaseComObject(_mediaSeeking)
            _mediaSeeking = Nothing
            Marshal.ReleaseComObject(_basicAudio)
            _basicAudio = Nothing
            Marshal.ReleaseComObject(_basicVideo)
            _basicVideo = Nothing
        End If
    End Sub

    '#endregion
End Class
