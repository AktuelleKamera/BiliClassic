/* =====================================================================
 * MyWindow.h - 「我的」：登录（验证码/扫码/手动）+ 资料 + 收藏
 * ===================================================================== */
#ifndef UI_MYWINDOW_H
#define UI_MYWINDOW_H

#include <mzfc_inc.h>
#include <string>

#define MZ_WM_MY_ASYNC (WM_APP + 0x530)

/* 二维码显示控件（只画矩阵） */
class UiQr : public UiWin
{
public:
    UiQr();
    const unsigned char *m_modules;   /* QR_SIZE*QR_SIZE，1=黑 */
    int  m_ok;
    int  m_active;                    /* 非扫码模式假 => 不画，避免盖住别的 */
    virtual void OnPaint(HDC hdcDst, RECT *prcWin, RECT *prcUpdate);
};

class MyWindow : public CMzWndEx
{
    MZ_DECLARE_DYNAMIC(MyWindow);
public:
    MyWindow();
    virtual ~MyWindow();

    virtual int DoModal();

    friend void job_qr_gen(void *);
    friend void job_qr_poll(void *);
    friend void job_sms_send(void *);
    friend void job_sms_login(void *);
    friend void job_nav(void *);

protected:
    virtual BOOL OnInitDialog();
    virtual void OnSize(int nWidth, int nHeight);
    virtual void OnTimer(UINT_PTR nIDEvent);
    virtual void OnMzCommand(WPARAM wParam, LPARAM lParam);
    virtual LRESULT MzDefWndProc(UINT message, WPARAM wParam, LPARAM lParam);

private:
    void Layout();
    void ApplyMode();          /* 按 m_mode 显示/隐藏控件 */
    void RefreshProfileText();
    void DrawQr(HDC hdc);

    void StartQr();            /* 异步取二维码 */
    void PollQr();             /* 异步轮询 */
    void SendSms();            /* 异步发验证码 */
    void SubmitSms();          /* 异步短信登录 */
    void SubmitCookie();       /* 本地解析 Cookie + 异步 nav */
    void DoLogout();
    void LoadNav();            /* 异步取资料 */

    UiCaption m_caption;
    UiButton  m_btnBack;

    /* 未登录入口 */
    UiButton  m_btnSms;
    UiButton  m_btnQr;
    UiButton  m_btnCookie;

    /* 验证码页 */
    UiStatic  m_lblPhone;
    UiStatic  m_lblCode;
    HWND      m_hPhone;
    HWND      m_hCode;
    UiButton  m_btnSend;
    UiButton  m_btnLogin;

    /* 手动 Cookie 页 */
    HWND      m_hCookie;
    UiButton  m_btnOk;

    /* 扫码页 */
    UiQr      m_qr;
    UiStatic  m_lblQr;
    UiStatic  m_lblQrStat;

    /* 状态 + 已登录页 */
    UiStatic  m_lblStatus;
    UiButton  m_btnLogout;

    int       m_mode;          /* 0 入口 1 验证码 2 扫码 3 手动 4 已登录 */
    int       m_qrInited;
    char      m_qrKey[128];
    char      m_qrUrl[600];
    char      m_qrErr[160];
    int       m_qrState;
    int       m_asyncOp;       /* 当前异步操作码 */
    char      m_asyncMsg[256]; /* 异步结果文本 */
    char      m_phone[32];
    char      m_code[16];
    char      m_captchaKey[128];   /* 发送验证码返回的 captcha_key，登录要带 */
    std::wstring m_profile;    /* 昵称/UID 文本 */
};

#endif /* UI_MYWINDOW_H */
