' DirectShow 枚举（VB.NET，兼容 .NET CF 2.0）。
' 来源：DirectShow.NetCF DirectShowEnums.cs（转译，仅播放所需部分）。
' 类型名保持与原 C# 工程一致。
Public Enum CLSCTX_ As Integer
    INPROC_SERVER = 1
End Enum

Public Enum PinDirection
    Input = 0
    Output = 1
End Enum

Public Enum RawFrameFormat As Integer
    YVU9 = 0
    Y411 = 1
    Y41P = 2
    YUY2 = 3
    YVYU = 4
    UYVY = 5
    Y211 = 6
    YV12 = 7
    MJPG = 8
    RGB565 = 9
    RGB24 = 10
    RGB32 = 11
    Unknown = 12
End Enum

Public Enum AmSeeking As Integer
    NoPositioning = 0
    AbsolutePositioning = 1
    RelativePositioning = 2
    IncrementalPositioning = 3
    SeekToKeyFrame = 4
    ReturnTime = 8
    Segment = 16
    NoFlush = 32
End Enum

Public Enum NotifyMessages As Integer
    WM_GRAPHNOTIFY = &H8001
    EC_COMPLETE = 1
End Enum
