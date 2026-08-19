' =====================================================================
' DirectShow COM 接口声明（VB.NET，兼容 .NET Compact Framework 2.0）
'
' 来源：DirectShow.NetCF（Alex Mogurenko）
'   C# 原文件：DirectShowNETCF.Controls\PlayerControl\PlayerControl\DirectShowImports.cs
'
' 转译规则：
'   - 保留全部 GUID 与 InterfaceType（IUnknown / Dual 与 C# 一致）
'   - 方法顺序与 C# 完全一致（vtable 顺序不可改动）
'   - 所有方法返回 Integer（对应 C# 的 [PreserveSig] int）
'   - C# 的 [In] 参数 -> ByVal；out 参数 -> ByRef
'   - C# 的 long -> Long，uint -> Integer（CF 2.0 兼容），double -> Double
'   - VB 关键字冲突的方法/参数名用方括号转义：[Stop]、[Get]、[Set]、[Property]
'   - C# 的 string + LPWStr 直接用 String（VB ComImport 自动按 LPWStr 编组）
'   - C# 的 [MarshalAs(UnmanagedType.IUnknown)] / IDispatch object 直接用 Object
'
' 依赖的外部类型（定义于 DirectShowUtils.cs / DirectShowStructs.cs / DirectShowEnums.cs，
' 需另行转译，此处仅引用）：
'   CGuid                 (DirectShowNETCF.Utils)
'   PinDirection          (DirectShowNETCF.Enums)
'   RawFrameFormat        (DirectShowNETCF.Enums)
'   AMMediaType           (DirectShowNETCF.Structs)
'   PinInfo               (DirectShowNETCF.Structs)
'   Size、Rect            (DirectShowNETCF.Structs，注意不是 System.Drawing 的类型)
' =====================================================================

Imports System
Imports System.Runtime.InteropServices

' ---- IGraphBuilder ----
<ComVisible(True), ComImport(), _
Guid("56A868A9-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IGraphBuilder
    Function AddFilter(ByVal pFilter As IBaseFilter, ByVal pName As String) As Integer
    Function RemoveFilter(ByVal pFilter As IBaseFilter) As Integer
    Function EnumFilters(ByRef ppEnum As IEnumFilters) As Integer
    Function FindFilterByName(ByVal pName As String, ByRef ppFilter As IBaseFilter) As Integer
    Function ConnectDirect(ByVal ppinOut As IPin, ByVal ppinIn As IPin, ByVal pmt As IntPtr) As Integer
    Function Reconnect(ByVal ppin As IPin) As Integer
    Function Disconnect(ByVal ppin As IPin) As Integer
    Function SetDefaultSyncSource() As Integer
    Function Connect(ByVal ppinOut As IPin, ByVal ppinIn As IPin) As Integer
    Function Render(ByVal ppinOut As IPin) As Integer
    Function RenderFile(ByVal lpcwstrFile As String, ByVal lpcwstrPlayList As String) As Integer
    Function AddSourceFilter(ByVal lpcwstrFileName As String, ByVal lpcwstrFilterName As String, ByRef ppFilter As IBaseFilter) As Integer
    Function SetLogFile(ByVal hFile As IntPtr) As Integer
    Function Abort() As Integer
    Function ShouldOperationContinue() As Integer
End Interface

' ---- IMediaControl ----
<ComVisible(True), ComImport(), _
Guid("56A868B1-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IMediaControl
    Function Run() As Integer
    Function Pause() As Integer
    Function [Stop]() As Integer
    Function GetState(ByVal msTimeout As Integer, ByRef pfs As Integer) As Integer
    Function RenderFile(ByVal strFilename As String) As Integer
    Function AddSourceFilter(ByVal strFilename As String, ByRef ppUnk As Object) As Integer
    Function get_FilterCollection(ByRef ppUnk As Object) As Integer
    Function get_RegFilterCollection(ByRef ppUnk As Object) As Integer
    Function StopWhenReady() As Integer
End Interface

' ---- IVideoWindow ----
<ComVisible(True), ComImport(), _
Guid("56A868B4-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IVideoWindow
    Function put_Caption(ByVal caption As String) As Integer
    Function get_Caption(ByRef caption As String) As Integer
    Function put_WindowStyle(ByVal windowStyle As Integer) As Integer
    Function get_WindowStyle(ByRef windowStyle As Integer) As Integer
    Function put_WindowStyleEx(ByVal windowStyleEx As Integer) As Integer
    Function get_WindowStyleEx(ByRef windowStyleEx As Integer) As Integer
    Function put_AutoShow(ByVal autoShow As Integer) As Integer
    Function get_AutoShow(ByRef autoShow As Integer) As Integer
    Function put_WindowState(ByVal windowState As Integer) As Integer
    Function get_WindowState(ByRef windowState As Integer) As Integer
    Function put_BackgroundPalette(ByVal backgroundPalette As Integer) As Integer
    Function get_BackgroundPalette(ByRef backgroundPalette As Integer) As Integer
    Function put_Visible(ByVal visible As Integer) As Integer
    Function get_Visible(ByRef visible As Integer) As Integer
    Function put_Left(ByVal left As Integer) As Integer
    Function get_Left(ByRef left As Integer) As Integer
    Function put_Width(ByVal width As Integer) As Integer
    Function get_Width(ByRef width As Integer) As Integer
    Function put_Top(ByVal top As Integer) As Integer
    Function get_Top(ByRef top As Integer) As Integer
    Function put_Height(ByVal height As Integer) As Integer
    Function get_Height(ByRef height As Integer) As Integer
    Function put_Owner(ByVal owner As IntPtr) As Integer
    Function get_Owner(ByRef owner As IntPtr) As Integer
    Function put_MessageDrain(ByVal drain As IntPtr) As Integer
    Function get_MessageDrain(ByRef drain As IntPtr) As Integer
    Function get_BorderColor(ByRef color As Integer) As Integer
    Function put_BorderColor(ByVal color As Integer) As Integer
    Function get_FullScreenMode(ByRef fullScreenMode As Integer) As Integer
    Function put_FullScreenMode(ByVal fullScreenMode As Integer) As Integer
    Function SetWindowForeground(ByVal focus As Integer) As Integer
    Function NotifyOwnerMessage(ByVal hwnd As IntPtr, ByVal msg As Integer, ByVal wParam As IntPtr, ByVal lParam As IntPtr) As Integer
    Function SetWindowPosition(ByVal left As Integer, ByVal top As Integer, ByVal width As Integer, ByVal height As Integer) As Integer
    Function GetWindowPosition(ByRef left As Integer, ByRef top As Integer, ByRef width As Integer, ByRef height As Integer) As Integer
    Function GetMinIdealImageSize(ByRef width As Integer, ByRef height As Integer) As Integer
    Function GetMaxIdealImageSize(ByRef width As Integer, ByRef height As Integer) As Integer
    Function GetRestorePosition(ByRef left As Integer, ByRef top As Integer, ByRef width As Integer, ByRef height As Integer) As Integer
    Function HideCursor(ByVal nHide As Integer) As Integer
    Function IsCursorHidden(ByRef nHide As Integer) As Integer
End Interface

' ---- IBaseFilter ----
<ComVisible(True), ComImport(), _
Guid("56A86895-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IBaseFilter
    Function GetClassID(ByRef pClassID As Guid) As Integer
    Function [Stop]() As Integer
    Function Pause() As Integer
    Function Run(ByVal tStart As Long) As Integer
    Function GetState(ByVal dwMilliSecsTimeout As Integer, ByRef filtState As Integer) As Integer
    Function SetSyncSource(ByVal pClock As Object) As Integer
    Function GetSyncSource(ByRef pClock As Object) As Integer
    Function EnumPins(ByRef ppEnum As IEnumPins) As Integer
    Function FindPin(ByVal Id As String, ByRef ppPin As IPin) As Integer
    Function QueryFilterInfo(ByVal pInfo As IntPtr) As Integer
    Function JoinFilterGraph(ByVal pGraph As Object, ByVal pName As String) As Integer
    Function QueryVendorInfo(ByRef pVendorInfo As String) As Integer
End Interface

' ---- IPersist ----
<ComVisible(True), ComImport(), _
Guid("0000010C-0000-0000-C000-000000000046"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IPersist
    Function GetClassID(ByRef pClassID As Guid) As Integer
End Interface

' ---- IPersistPropertyBag（继承 IPersist，并重新声明 GetClassID，对应 C# 的 new）----
<ComVisible(True), ComImport(), _
Guid("37D84F60-42CB-11CE-8135-00AA004BB851"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IPersistPropertyBag
    Inherits IPersist

    Overloads Function GetClassID(ByRef pClassID As Guid) As Integer
    Function InitNew() As Integer
    Function Load(ByVal pPropBag As IPropertyBag, ByVal pErrorLog As Object) As Integer
    Function Save(ByVal pPropBag As IPropertyBag, ByVal fClearDirty As Boolean, ByVal fSaveAllProperties As Boolean) As Integer
End Interface

' ---- IPropertyBag ----
<ComVisible(True), ComImport(), _
Guid("55272A00-42CB-11CE-8135-00AA004BB851"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IPropertyBag
    Function Read(ByVal pszPropName As String, ByRef pVar As Object, ByVal pErrorLog As IntPtr) As Integer
    Function Write(ByVal pszPropName As String, ByRef pVar As Object) As Integer
End Interface

' ---- ICaptureGraphBuilder2 ----
<ComVisible(True), ComImport(), _
Guid("93E5A4E0-2D50-11D2-ABFA-00A0C9C6E38D"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface ICaptureGraphBuilder2
    Function SetFiltergraph(ByVal pfg As IGraphBuilder) As Integer
    Function GetFiltergraph(ByRef ppfg As IGraphBuilder) As Integer
    Function SetOutputFileName(ByVal pType As CGuid, ByVal lpstrFile As String, ByRef ppbf As IBaseFilter, ByRef ppSink As IFileSinkFilter) As Integer
    Function FindInterface(ByRef pCategory As CGuid, ByRef pType As CGuid, ByVal pbf As IBaseFilter, ByRef riid As Guid, ByRef ppint As Object) As Integer
    Function RenderStream(ByVal PinCategory As CGuid, ByVal MediaType As CGuid, ByVal pSource As Object, ByVal pfCompressor As IBaseFilter, ByVal pfRenderer As IBaseFilter) As Integer
    Function ControlStream(ByVal pCategory As CGuid, ByVal pType As CGuid, ByVal pFilter As IBaseFilter, ByVal pstart As Long, ByVal pstop As Long, ByVal wStartCookie As Short, ByVal wStopCookie As Short) As Integer
    Function AllocCapFile(ByVal lpstrFile As String, ByVal dwlSize As Long) As Integer
    Function CopyCaptureFile(ByVal lpwstrOld As String, ByVal lpwstrNew As String, ByVal fAllowEscAbort As Integer, ByVal pFilter As Object) As Integer
    Function FindPin(ByVal pSource As Object, ByVal pindir As PinDirection, ByVal pCategory As CGuid, ByVal pType As CGuid, ByVal fUnconnected As Boolean, ByVal num As Integer, ByRef ppPin As IPin) As Integer
End Interface

' ---- IFileSinkFilter ----
<ComVisible(True), ComImport(), _
Guid("A2104830-7C70-11CF-8BCE-00AA00A3F1A6"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IFileSinkFilter
    Function SetFileName(ByVal pszFileName As String, ByVal pmt As AMMediaType) As Integer
    Function GetCurFile(ByRef pszFileName As String, ByVal pmt As AMMediaType) As Integer
End Interface

' ---- IImageSinkFilter ----
<ComVisible(True), ComImport(), _
Guid("7f12e45f-b224-449e-907a-a18fca14a579"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IImageSinkFilter
    Function SetQuality(ByVal dwQuality As Integer) As Integer
    Function SetEncoderParameters(ByVal dwCount As Integer, ByRef Parameter As IntPtr) As Integer
End Interface

' ---- IEnumPins ----
<ComVisible(True), ComImport(), _
Guid("56A86892-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IEnumPins
    Function [Next](ByVal cPins As Integer, ByRef ppPins As IPin, ByRef pcFetched As Integer) As Integer
    Function Skip(ByVal cPins As Integer) As Integer
    Function Reset() As Integer
    Function Clone(ByRef ppEnum As IEnumPins) As Integer
End Interface

' ---- IPin ----
<ComVisible(True), ComImport(), _
Guid("56A86891-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IPin
    Function Connect(ByVal pReceivePin As IPin, ByVal pmt As IntPtr) As Integer
    Function ReceiveConnection(ByVal pReceivePin As IPin, ByVal pmt As IntPtr) As Integer
    Function Disconnect() As Integer
    Function ConnectedTo(ByRef ppPin As IPin) As Integer
    Function ConnectionMediaType(ByVal pmt As IntPtr) As Integer
    Function QueryPinInfo(ByRef pInfo As PinInfo) As Integer
    Function QueryDirection(ByRef pPinDir As Integer) As Integer
    Function QueryId(ByRef Id As String) As Integer
    Function QueryAccept(ByVal pmt As IntPtr) As Integer
    Function EnumMediaTypes(ByRef ppEnum As IEnumMediaTypes) As Integer
    Function QueryInternalConnections(ByVal apPin As IntPtr, ByRef nPin As Integer) As Integer
    Function EndOfStream() As Integer
    Function BeginFlush() As Integer
    Function EndFlush() As Integer
    Function NewSegment(ByVal tStart As Long, ByVal tStop As Long, ByVal dRate As Double) As Integer
End Interface

' ---- IEnumFilters ----
<ComVisible(True), ComImport(), _
Guid("56A86893-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IEnumFilters
    Function [Next](ByVal cFilters As Integer, ByRef ppFilter As IBaseFilter, ByRef pcFetched As Integer) As Integer
    Function Skip(ByVal cFilters As Integer) As Integer
    Function Reset() As Integer
    Function Clone(ByRef ppEnum As IEnumFilters) As Integer
End Interface

' ---- IAMVideoControl ----
<ComVisible(True), ComImport(), _
Guid("6A2E0670-28E4-11D0-A18C-00A0C9118956"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IAMVideoControl
    Function GetCaps(ByVal pPin As IPin, ByRef pCapsFlags As Integer) As Integer
    Function SetMode(ByVal pPin As IPin, ByVal Mode As Integer) As Integer
    Function GetMode(ByVal pPin As IPin, ByRef Mode As Integer) As Integer
    Function GetCurrentActualFrameRate(ByVal pPin As IPin, ByRef ActualFrameRate As Long) As Integer
    Function GetMaxAvailableFrameRate(ByVal pPin As IPin, ByVal iIndex As Integer, ByVal Dimensions As Size, ByRef MaxAvailableFrameRate As Long) As Integer
    Function GetFrameRateList(ByVal pPin As IPin, ByVal iIndex As Integer, ByVal Dimensions As Size, ByRef ListSize As Integer, ByRef FrameRates As IntPtr) As Integer
End Interface

' ---- IEnumMediaTypes ----
<ComVisible(True), ComImport(), _
Guid("89C31040-846B-11CE-97D3-00AA0055595A"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IEnumMediaTypes
    Function [Next](ByVal cMediaTypes As Integer, ByRef ppMediaTypes As IntPtr, ByRef pcFetched As Integer) As Integer
    Function Skip(ByVal cMediaTypes As Integer) As Integer
    Function Reset() As Integer
    Function Clone(ByRef ppEnum As IEnumMediaTypes) As Integer
End Interface

' ---- IAMStreamConfig ----
<ComVisible(True), ComImport(), _
Guid("C6E13340-30AC-11D0-A18C-00A0C9118956"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IAMStreamConfig
    Function SetFormat(ByVal pmt As AMMediaType) As Integer
    Function GetFormat(ByRef pmt As IntPtr) As Integer
    Function GetNumberOfCapabilities(ByRef piCount As Integer, ByRef piSize As Integer) As Integer
    Function GetStreamCaps(ByVal iIndex As Integer, ByRef ppmt As IntPtr, ByVal pSCC As IntPtr) As Integer
End Interface

' ---- IAMCameraControl ----
<ComVisible(True), ComImport(), _
Guid("C6E13370-30AC-11D0-A18C-00A0C9118956"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IAMCameraControl
    Function GetRange(ByVal [Property] As Integer, ByRef pMin As Integer, ByRef pMax As Integer, ByRef pSteppingDelta As Integer, ByRef pDefault As Integer, ByRef pCapsFlags As Integer) As Integer
    Function [Set](ByVal [Property] As Integer, ByVal lValue As Integer, ByVal Flags As Integer) As Integer
    Function [Get](ByVal [Property] As Integer, ByRef Value As Integer, ByRef Flags As Integer) As Integer
End Interface

' ---- IAMVideoProcAmp ----
<ComVisible(True), ComImport(), _
Guid("C6E13360-30AC-11d0-A18C-00A0C9118956"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IAMVideoProcAmp
    Function GetRange(ByVal [Property] As Integer, ByRef pMin As Integer, ByRef pMax As Integer, ByRef pSteppingDelta As Integer, ByRef pDefault As Integer, ByRef pCapsFlags As Integer) As Integer
    Function [Set](ByVal [Property] As Integer, ByVal lValue As Integer, ByVal Flags As Integer) As Integer
    Function [Get](ByVal [Property] As Integer, ByRef lValue As Integer, ByRef Flags As Integer) As Integer
End Interface

' ---- IDMOWrapperFilter ----
<ComVisible(True), ComImport(), _
Guid("52D6F586-9F0F-4824-8FC8-E32CA04930C2"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IDMOWrapperFilter
    Function Init(ByVal clsidDMO As CGuid, ByVal catDMO As CGuid) As Integer
End Interface

' ---- IMediaPosition ----
<ComVisible(True), ComImport(), _
Guid("56A868B2-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IMediaPosition
    Function get_Duration(ByRef pLength As Double) As Integer
    Function put_CurrentPosition(ByVal llTime As Double) As Integer
    Function get_CurrentPosition(ByRef pllTime As Double) As Integer
    Function get_StopTime(ByRef pllTime As Double) As Integer
    Function put_StopTime(ByVal llTime As Double) As Integer
    Function get_PrerollTime(ByRef pllTime As Double) As Integer
    Function put_PrerollTime(ByVal llTime As Double) As Integer
    Function put_Rate(ByVal dRate As Double) As Integer
    Function get_Rate(ByRef pdRate As Double) As Integer
    Function CanSeekForward(ByRef pCanSeekForward As Integer) As Integer
    Function CanSeekBackward(ByRef pCanSeekBackward As Integer) As Integer
End Interface

' ---- IGetFrame ----
<ComVisible(True), ComImport(), _
Guid("2B21644A-D405-4E27-A51C-A4812bE0CE4C"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IGetFrame
    Function getFrame(ByVal pBuff As IntPtr) As Integer
    Function getSize(ByRef size As Long) As Integer
    Function getFrameParams(ByRef width As Integer, ByRef height As Integer, ByRef format As RawFrameFormat) As Integer
    Function drawText(ByVal ptr As IntPtr, ByVal height As Integer, ByVal width As Integer) As Integer
    Function stopDraw() As Integer
    Function getGrayScale(ByVal ptr As IntPtr) As Integer
    Function getRgb(ByVal ptr As IntPtr) As Integer
    Function drawTarget(ByVal rect As Rect, ByVal type As Integer) As Integer
    Function stopDrawTarget() As Integer
End Interface

' ---- IEffectEx ----
<ComVisible(True), ComImport(), _
Guid("2B21655B-D405-4E27-A51C-A4812bE0CE4C"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IEffectEx
    Function getFrame(ByVal buffer As IntPtr) As Integer
    Function getRect(ByRef width As Integer, ByRef height As Integer) As Integer
    Function applyEffect(ByVal effect As Integer) As Integer
    Function drawBitmap(ByVal ptr As IntPtr, ByVal id As Integer, ByVal x As Integer, ByVal y As Integer, ByVal width As Integer, ByVal height As Integer, ByVal transparent As Integer, ByVal r As Integer, ByVal g As Integer, ByVal b As Integer) As Integer
    Function eraseBitmap(ByVal id As Integer) As Integer
    Function doRotate(ByVal rotationType As Integer) As Integer
    Function saveJPEG(ByVal quality As Integer, <MarshalAs(UnmanagedType.LPStr)> ByVal path As String) As Integer
End Interface

' ---- IMediaEventEx ----
<ComImport(), _
Guid("56A868C0-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IMediaEventEx
    Function GetEventHandle(ByRef hEvent As IntPtr) As Integer
    Function GetEvent(ByRef lEventCode As Integer, ByRef lParam1 As Integer, ByRef lParam2 As Integer, ByVal msTimeout As Integer) As Integer
    Function WaitForCompletion(ByVal msTimeout As Integer, ByRef pEvCode As Integer) As Integer
    Function CancelDefaultHandling(ByVal lEvCode As Integer) As Integer
    Function RestoreDefaultHandling(ByVal lEvCode As Integer) As Integer
    Function FreeEventParams(ByVal lEventCode As Integer, ByVal lParam1 As Integer, ByVal lParam2 As Integer) As Integer
    Function SetNotifyWindow(ByVal hwnd As IntPtr, ByVal lMsg As Integer, ByVal lInstanceData As IntPtr) As Integer
    Function SetNotifyWindow(ByVal lNoNotifyFlags As Integer) As Integer
    Function GetNotifyFlags(ByRef lNoNotifyFlags As Integer) As Integer
End Interface

' ---- IBasicAudio ----
<ComImport(), _
Guid("56A868B3-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IBasicAudio
    Function put_Volume(ByVal lVolume As Integer) As Integer
    Function get_Volume(ByRef plVolume As Integer) As Integer
    Function put_Balance(ByVal lBalance As Integer) As Integer
    Function get_Balance(ByRef plBalance As Integer) As Integer
End Interface

' ---- IBasicVideo ----
<ComImport(), _
Guid("56A868B5-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IBasicVideo
    Function get_AvgTimePerFrame(ByRef pAvgTimePerFrame As Double) As Integer
    Function get_BitRate(ByRef pBitRate As Integer) As Integer
    Function get_BitErrorRate(ByRef pBitRate As Integer) As Integer
    Function get_VideoWidth(ByRef pVideoWidth As Integer) As Integer
    Function get_VideoHeight(ByRef pVideoHeight As Integer) As Integer
    Function put_SourceLeft(ByVal SourceLeft As Integer) As Integer
    Function get_SourceLeft(ByRef pSourceLeft As Integer) As Integer
    Function put_SourceWidth(ByVal SourceWidth As Integer) As Integer
    Function get_SourceWidth(ByRef pSourceWidth As Integer) As Integer
    Function put_SourceTop(ByVal SourceTop As Integer) As Integer
    Function get_SourceTop(ByRef pSourceTop As Integer) As Integer
    Function put_SourceHeight(ByVal SourceHeight As Integer) As Integer
    Function get_SourceHeight(ByRef pSourceHeight As Integer) As Integer
    Function put_DestinationLeft(ByVal DestinationLeft As Integer) As Integer
    Function get_DestinationLeft(ByRef pDestinationLeft As Integer) As Integer
    Function put_DestinationWidth(ByVal DestinationWidth As Integer) As Integer
    Function get_DestinationWidth(ByRef pDestinationWidth As Integer) As Integer
    Function put_DestinationTop(ByVal DestinationTop As Integer) As Integer
    Function get_DestinationTop(ByRef pDestinationTop As Integer) As Integer
    Function put_DestinationHeight(ByVal DestinationHeight As Integer) As Integer
    Function get_DestinationHeight(ByRef pDestinationHeight As Integer) As Integer
    Function SetSourcePosition(ByVal left As Integer, ByVal top As Integer, ByVal width As Integer, ByVal height As Integer) As Integer
    Function GetSourcePosition(ByRef left As Integer, ByRef top As Integer, ByRef width As Integer, ByRef height As Integer) As Integer
    Function SetDefaultSourcePosition() As Integer
    Function SetDestinationPosition(ByVal left As Integer, ByVal top As Integer, ByVal width As Integer, ByVal height As Integer) As Integer
    Function GetDestinationPosition(ByRef left As Integer, ByRef top As Integer, ByRef width As Integer, ByRef height As Integer) As Integer
    Function SetDefaultDestinationPosition() As Integer
    Function GetVideoSize(ByRef pWidth As Integer, ByRef pHeight As Integer) As Integer
    Function GetVideoPaletteEntries(ByVal StartIndex As Integer, ByVal Entries As Integer, ByRef pRetrieved As Integer, ByRef pPalette As Integer()) As Integer
    Function GetCurrentImage(ByRef pBufferSize As Integer, ByVal pDIBImage As IntPtr) As Integer
    Function IsUsingDefaultSource() As Integer
    Function IsUsingDefaultDestination() As Integer
End Interface

' ---- IMediaSeeking ----
<ComVisible(True), ComImport(), _
Guid("36B73880-C2C8-11CF-8B46-00805F6CEF60"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IMediaSeeking
    Function GetCapabilities(ByRef pCapabilities As Integer) As Integer
    Function CheckCapabilities(ByRef pCapabilities As Integer) As Integer
    Function IsFormatSupported(ByVal pFormat As CGuid) As Integer
    Function QueryPreferredFormat(ByRef pFormat As Guid) As Integer
    Function GetTimeFormat(ByRef pFormat As Guid) As Integer
    Function IsUsingTimeFormat(ByVal pFormat As CGuid) As Integer
    Function SetTimeFormat(ByVal pFormat As CGuid) As Integer
    Function GetDuration(ByRef pDuration As Long) As Integer
    Function GetStopPosition(ByRef pStop As Long) As Integer
    Function GetCurrentPosition(ByRef pCurrent As Long) As Integer
    Function ConvertTimeFormat(ByRef pTarget As Long, ByVal pTargetFormat As CGuid, ByVal Source As Long, ByVal pSourceFormat As CGuid) As Integer
    Function SetPositions(ByRef pCurrent As Long, ByVal dwCurrentFlags As Integer, ByRef pStop As Long, ByVal dwStopFlags As Integer) As Integer
    Function GetPositions(ByRef pCurrent As Long, ByRef pStop As Long) As Integer
    Function GetAvailable(ByRef pEarliest As Long, ByRef pLatest As Long) As Integer
    Function SetRate(ByVal dRate As Double) As Integer
    Function GetRate(ByRef pdRate As Double) As Integer
    Function GetPreroll(ByRef pllPreroll As Long) As Integer
End Interface

' ---- IFileSourceFilter ----
<ComVisible(True), ComImport(), _
Guid("56A868A6-0AD4-11CE-B03A-0020AF0BA770"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface IFileSourceFilter
    Function Load(ByVal pszFileName As String, ByVal pmt As AMMediaType) As Integer
    Function GetCurFile(ByRef pszFileName As String, ByVal pmt As AMMediaType) As Integer
End Interface

' ---- FrameCallback 委托（INullGrabber.registerCallback 使用）----
Public Delegate Sub FrameCallback(ByVal frame As IntPtr)

' ---- INullGrabber ----
<ComVisible(True), ComImport(), _
Guid("2B21766C-D405-4E27-A51C-A4812BE0CE4C"), _
InterfaceType(ComInterfaceType.InterfaceIsIUnknown)> _
Public Interface INullGrabber
    Function getRect(ByRef width As Integer, ByRef height As Integer) As Integer
    Function registerCallback(<MarshalAs(UnmanagedType.FunctionPtr)> ByVal callback As FrameCallback) As Integer
End Interface
