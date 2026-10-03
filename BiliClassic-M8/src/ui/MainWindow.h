/* =====================================================================
 * MainWindow.h - 主窗口（布局、控件、事件路由、IUiSink 转发）
 * ---------------------------------------------------------------------
 * 分层约定：
 *   - 本类只负责「显示」和「把用户点击转发给 app 层服务」；
 *   - 业务逻辑（解析/下载/转码）全部在 src\app，通过 MainSink 回来。
 * ===================================================================== */
#ifndef UI_MAINWINDOW_H
#define UI_MAINWINDOW_H

#include <mzfc_inc.h>
#include "../app/UiSink.h"
#include <string>

/* 控件 ID */
enum
{
    MZ_IDC_EDIT_KEYWORD = 110,   /* 原生 EDIT：关键词 */
    MZ_IDC_BTN_SEARCH   = 101,   /* 顶部搜索按钮 */
    MZ_IDC_TOOLBAR      = 103,   /* 底部文字工具栏 */

    /* 底部 Meizu 风格栏 */
    MZ_IDC_TAB_RECOMMEND = 120,
    MZ_IDC_TAB_SEARCH    = 121,
    MZ_IDC_TAB_SETTINGS  = 122,

    /* 底部菜单栏按钮 */
    MZ_IDC_BAR_MENU     = 140,
    MZ_IDC_BAR_MINE     = 141,
    MZ_IDC_BAR_SETTINGS = 142,
    MZ_IDC_BAR_EXIT     = 143,

    /* 底栏 4 个导航按钮 */
    MZ_IDC_NAV_MENU     = 150,
    MZ_IDC_NAV_BACK     = 151,
    MZ_IDC_NAV_MINE     = 152,
    MZ_IDC_NAV_SET      = 153,

    /* 设置菜单返回值 */
    MZ_MENU_CONNS_1 = 1001,
    MZ_MENU_CONNS_2 = 1002,
    MZ_MENU_CONNS_4 = 1003,
    MZ_MENU_OFFLINE = 1004,
    MZ_MENU_TRANS   = 1005,
    MZ_MENU_REPLAY  = 1006,
    MZ_MENU_RECOMMEND = 1008,
    MZ_MENU_SEARCH  = 1009,
    MZ_MENU_LOGIN   = 1010,
    MZ_MENU_FAVS    = 1011,
    MZ_MENU_PROFILE = 1012,
    MZ_MENU_EXIT    = 1013,
    MZ_MENU_HISTORY = 1014,
    MZ_MENU_BACK    = 1015,
    MZ_MENU_ABOUT   = 1016,
    MZ_MENU_UPDATE  = 1017,
    MZ_MENU_CLEARCACHE = 1018
};

class MainWindow;

/* 结果列表：单击选中；700ms 内双击同一条 -> 播放；按住 >=600ms 不动 -> 长按 */
class UiResultList : public UiList
{
public:
    UiResultList();
    void SetOwner(MainWindow *w) { m_owner = w; }
    void CheckBottom();     /* 最后一条已可见 -> 让 owner 触发翻页 */
    void ResetBottom() { m_bottomOk = false; }   /* 换一批结果时复位 */
    virtual int OnLButtonDown(UINT fwKeys, int xPos, int yPos);
    virtual int OnMouseMove(UINT fwKeys, int xPos, int yPos);
    virtual int OnLButtonUp(UINT fwKeys, int xPos, int yPos);
    virtual int OnTimer(UINT_PTR nIDEvent);
    virtual void DrawItem(HDC hdcDst, int nIndex, RECT *prcItem,
                          RECT *prcWin, RECT *prcUpdate);

private:
    MainWindow *m_owner;
    int         m_lastIdx;
    DWORD       m_lastTick;
    int         m_downIdx;   /* 按下瞬间的条目（长按判定用） */
    DWORD       m_downTick;
    bool        m_moved;    /* 本次按下是否发生过拖动（滚动） */
    bool        m_bottomOk; /* 已经在底部触发过翻页 */
};

/* 自定义消息：后台线程投递 UI 事件，主线程在 MzDefWndProc 里排空 */
#define WM_APP_UI_EVENT (WM_APP + 1)
#define WM_APP_COVER_DONE (WM_APP + 2)
#define WM_APP_STARTUP (WM_APP + 3)
#define WM_APP_UPDATE_DONE (WM_APP + 4)

enum UiEventType
{
    UI_EVT_LOG = 1,
    UI_EVT_STATUS,
    UI_EVT_ITEMS,
    UI_EVT_BUSY,
    UI_EVT_PLAY,
    UI_EVT_LOADING
};

/* IUiSink -> MainWindow 的适配器（避免主窗口多重继承）。
 * 业务层现在跑在后台线程，这里把回调统一编成事件、PostMessage 给 UI 线程；
 * 若本就在 UI 线程调用则直接生效。UI 控件只在 UI 线程被触碰。 */
class MainSink : public IUiSink
{
public:
    MainSink();
    ~MainSink();

    MainWindow *m_w;

    /* UI 线程初始化后调用：记录窗口句柄与本线程 id */
    void AttachThread(HWND hwnd);

    /* 窗口销毁前调用：停收新事件 */
    void Stop();

    /* UI 线程排空事件队列并应用到界面 */
    void Drain();

    virtual void OnLog(const char *line);
    virtual void OnStatus(const char *text);
    virtual void OnLoading(const char *text);
    virtual void OnItems(const BcItem *items, int count, const char *what,
                         int append);
    virtual void OnBusyChanged(int busy);
    virtual void OnDownloadProgress(long done, long total);
    virtual void OnPlayMedia(const char *path);
    virtual void PumpMessages();

private:
    struct UiEvent
    {
        int      type;
        char     text[BC_LOG_LEN];
        BcItem  *items;      /* 仅 ITEMS 事件分配，投递后释放 */
        int      count;
        int      append;
        int      busy;
        long     done;
        long     total;
        UiEvent *next;
    };

    bool     OnUiThread();
    void     Enqueue(UiEvent *e);
    void     Post(int type, const char *text, const BcItem *items,
                  int count, int busy, long done, long total, int append);
    UiEvent *DequeueAll();

    HWND             m_hwnd;
    DWORD            m_uiThread;
    volatile LONG    m_alive;
    CRITICAL_SECTION m_lock;
    UiEvent         *m_head;
    UiEvent         *m_tail;
    long             m_lastProgKB;
};

class MainWindow : public CMzWndEx
{
    MZ_DECLARE_DYNAMIC(MainWindow);
    friend class MainSink;

public:
    MainWindow();

    MainSink &GetSink() { return m_sink; }

    /* 配置变化后刷新显示（App 层初始化完成后调用一次） */
    void RefreshUI();

    /* 列表双击回调（UiResultList 调用） */
    void OnListDoubleTap(int index);

    /* 列表长按回调（UiResultList 调用） -> 弹条目详情 */
    void OnListLongPress(int index);

    /* 滑到底部（UiResultList 调用） -> 自动翻页 */
    void OnListBottom();

    /* 播放一个媒体（先内嵌 PlayerCore，失败再回退系统播放器） */
    void PlayMedia(const char *pathGbk);

    /* 业务层同步等待期间泵消息（MainSink 调用） */
    void PumpNow();

protected:
    virtual BOOL OnInitDialog();
    virtual void OnMzCommand(WPARAM wParam, LPARAM lParam);
    virtual LRESULT MzDefWndProc(UINT message, WPARAM wParam, LPARAM lParam);
    virtual void OnTimer(UINT_PTR nIDEvent);   /* 忙态时周期性排空后台事件 */

private:
    void Layout();                       /* 按窗口尺寸摆放控件 */
    void AppendLog(const char *line);    /* GBK 行 -> 追加到日志框 */
    void SetStatus(const char *text);
    void SetLoading(const char *text);
    void FillList(const BcItem *items, int count, int append);
    void SetBusyUI(int busy);
    void ShowDetail(int index);            /* 弹出指定条目详情 */
    void DoBack();                         /* 底栏「返回」：退回上一层列表 */
    void ShowAbout();                      /* 设置 -> 关于 */
    void CheckUpdate();                    /* 设置 -> 检查更新 */
    void ShowUpdateResult();               /* 检查更新结果窗口 */
    void ClearCache();                     /* 设置 -> 清除缓存 */
    void ToggleTranscode();
    void DoSearch(int popular);
    void DoPlay(int forceTranscode);
    int  SelectedIndex();
    void ShowMenu(int which);            /* 0=菜单 1=我的 2=设置 */

    UiCaption      m_caption;
    UiResultList   m_list;
    UiButton       m_btnSearch;
    UiToolbar_Text m_toolbar;   /* MZ 原生底部工具栏：菜单 / 返回 / 我的 */
    HWND           m_hEdit;    /* 关键词（原生单行 EDIT） */

    MainSink m_sink;
};

#endif /* UI_MAINWINDOW_H */
