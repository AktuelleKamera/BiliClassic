' DirectShow GUID 常量（VB.NET，兼容 .NET CF 2.0）。
' 来源：DirectShow.NetCF DirectShowGuids.cs（转译，仅播放所需部分）。
' 类型名保持与原 C# 工程一致（DirectShowImports.vb 引用这些类型）。
Public NotInheritable Class CLSID_
    Public Shared ReadOnly FilterGraph As Guid = New Guid("E436EBB3-524F-11CE-9F53-0020AF0BA770")
    Public Shared ReadOnly VideoRenderer As Guid = New Guid("4D4B1600-33AC-11CF-BF30-00AA0055595A")
    Public Shared ReadOnly AudioRender As Guid = New Guid("E30629D1-27E5-11CE-875D-00608CB78066")
    Public Shared ReadOnly FileSource As Guid = New Guid("E436EBB5-524F-11CE-9F53-0020AF0BA770")
    Public Shared ReadOnly MEDIATYPE_Video As Guid = New Guid("73646976-0000-0010-8000-00AA00389B71")
    Public Shared ReadOnly MEDIATYPE_Audio As Guid = New Guid("73647561-0000-0010-8000-00AA00389B71")
    Public Shared ReadOnly VideoInfo As Guid = New Guid("05589F80-C356-11CE-BF01-00AA0055595A")
    Public Shared ReadOnly VideoInfo2 As Guid = New Guid("F72A76A0-EB0A-11D0-ACE4-0000C0CC16BA")
End Class

Public NotInheritable Class IID_
    Public Shared ReadOnly IFilterGraph2 As Guid = New Guid("36B73882-C2C8-11CF-8B46-00805F6CEF60")
    Public Shared ReadOnly IBaseFilter As Guid = New Guid("56A86895-0AD4-11CE-B03A-0020AF0BA770")
End Class
