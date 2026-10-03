/* =====================================================================
 * MyWindow.cpp - 「我的」：登录（验证码/扫码/手动）+ 资料 + 收藏
 * ===================================================================== */
#include "MyWindow.h"
#include "TextUtil.h"
#include "../app/AppContext.h"
#include "../core/login.h"
#include "../core/qrcode.h"
#include <Mzfc/SipHelper.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

MZ_IMPLEMENT_DYNAMIC(MyWindow)

/* nav 返回的 code/mid 是数字（"code":0），json_get_string 只认 "0" 这种
 * 字符串形式，取不到会让登录状态永远判成未登录，所以自己按裸值读。 */
static long body_num(const char *body, const char *key)
{
    const char *p = json_find(body, key);

    if (p == NULL) {
        return -1;
    }
    /* json_find 返回的是 "key" 的开头，跳过引号+key+引号，再跳过空格和冒号 */
    p += (int)strlen(key) + 2;
    while (*p == ' ' || *p == '\t') {
        p++;
    }
    if (*p == ':') {
        p++;
    }
    while (*p == ' ' || *p == '\t') {
        p++;
    }
    if (*p == '"') {
        p++;
    }
    return atol(p);
}

enum { MY_ID_BACK = 9201, MY_ID_SMS, MY_ID_QR, MY_ID_COOKIE,
       MY_ID_SEND, MY_ID_LOGIN, MY_ID_OK, MY_ID_LOGOUT,
       MY_EDIT_PHONE = 9301, MY_EDIT_CODE, MY_EDIT_COOKIE };

enum { OP_QR_GEN = 1, OP_QR_POLL, OP_SMS_SEND, OP_SMS_LOGIN, OP_NAV, OP_FAVS };

static MyWindow *s_self = NULL;

/* ===================== UiQr ===================== */

UiQr::UiQr()
{
    m_modules = NULL;
    m_ok = 0;
    m_active = 0;
}

void UiQr::OnPaint(HDC hdc, RECT *prcWin, RECT *prcUpdate)
{
    RECT rc = *prcWin;
    int w = rc.right - rc.left;
    int h = rc.bottom - rc.top;
    int side = (w < h) ? w : h;
    int q = 2;                 /* 静默区（模块数） */
    int total = QR_SIZE + q * 2;
    int cell;
    int x;
    int y;
    HBRUSH b;

    (void)prcUpdate;
    if (!m_active) {
        return;                /* 非扫码模式不画，交给底层（别盖住按钮） */
    }
    b = CreateSolidBrush(RGB(255, 255, 255));
    FillRect(hdc, &rc, b);
    DeleteObject(b);
    if (!m_ok || m_modules == NULL || side <= 0) {
        return;
    }
    cell = side / total;
    if (cell < 1) {
        cell = 1;
    }
    /* 居中 */
    {
        int ox = rc.left + (w - cell * total) / 2;
        int oy = rc.top + (h - cell * total) / 2;
        HBRUSH kb = CreateSolidBrush(RGB(0, 0, 0));

        for (y = 0; y < QR_SIZE; y++) {
            for (x = 0; x < QR_SIZE; x++) {
                if (m_modules[y * QR_SIZE + x]) {
                    RECT mr;
                    mr.left = ox + (x + q) * cell;
                    mr.top = oy + (y + q) * cell;
                    mr.right = mr.left + cell;
                    mr.bottom = mr.top + cell;
                    FillRect(hdc, &mr, kb);
                }
            }
        }
        DeleteObject(kb);
    }
}

/* ===================== MyWindow ===================== */

MyWindow::MyWindow()
{
    m_mode = 0;
    m_qrInited = 0;
    m_qrKey[0] = '\0';
    m_qrUrl[0] = '\0';
    m_qrErr[0] = '\0';
    m_qrState = -2;
    m_asyncOp = 0;
    m_asyncMsg[0] = '\0';
    m_phone[0] = '\0';
    m_code[0] = '\0';
    m_hPhone = NULL;
    m_hCode = NULL;
    m_hCookie = NULL;
}

MyWindow::~MyWindow()
{
    if (s_self == this) {
        s_self = NULL;
    }
}

/* ---------- 后台任务 ---------- */

void job_qr_gen(void *arg)
{
    (void)arg;
    if (s_self == NULL) {
        return;
    }
    if (bc_login_qr_generate(s_self->m_qrKey, (int)sizeof(s_self->m_qrKey),
                             s_self->m_qrUrl, (int)sizeof(s_self->m_qrUrl),
                             s_self->m_qrErr, (int)sizeof(s_self->m_qrErr)) == 0) {
        s_self->m_qrState = 0;
    } else {
        s_self->m_qrState = -1;
    }
    ::PostMessage(s_self->m_hWnd, MZ_WM_MY_ASYNC, OP_QR_GEN, 0);
}

void job_qr_poll(void *arg)
{
    int state = -1;
    (void)arg;
    if (s_self == NULL) {
        return;
    }
    bc_login_qr_poll(&g_app.Cfg(), s_self->m_qrKey, &state,
                     s_self->m_qrErr, (int)sizeof(s_self->m_qrErr));
    s_self->m_qrState = state;
    ::PostMessage(s_self->m_hWnd, MZ_WM_MY_ASYNC, OP_QR_POLL, 0);
}

void job_sms_send(void *arg)
{
    char ck[128];
    (void)arg;
    if (s_self == NULL) {
        return;
    }
    ck[0] = '\0';
    if (bc_login_send_sms(s_self->m_phone, ck, (int)sizeof(ck),
                          s_self->m_asyncMsg, (int)sizeof(s_self->m_asyncMsg)) == 0) {
        g_app.GetLogger().Log("MyWindow: 验证码已发送 msg=%s", s_self->m_asyncMsg);
        str_copy(s_self->m_captchaKey, (int)sizeof(s_self->m_captchaKey), ck);
        s_self->m_qrState = 0;
    } else {
        g_app.GetLogger().Log("MyWindow: 验证码发送失败 msg=%s", s_self->m_asyncMsg);
        s_self->m_qrState = -1;
    }
    ::PostMessage(s_self->m_hWnd, MZ_WM_MY_ASYNC, OP_SMS_SEND, 0);
}

void job_sms_login(void *arg)
{
    (void)arg;
    if (s_self == NULL) {
        return;
    }
    if (bc_login_by_sms(&g_app.Cfg(), s_self->m_phone, s_self->m_code,
                        s_self->m_captchaKey,
                        s_self->m_asyncMsg, (int)sizeof(s_self->m_asyncMsg)) == 0) {
        s_self->m_qrState = 0;
    } else {
        s_self->m_qrState = -1;
    }
    ::PostMessage(s_self->m_hWnd, MZ_WM_MY_ASYNC, OP_SMS_LOGIN, 0);
}

void job_nav(void *arg)
{
    char *body = NULL;
    int len = 0;
    char err[160];
    (void)arg;
    if (s_self == NULL) {
        return;
    }
    err[0] = '\0';
    s_self->m_qrState = -1;
    s_self->m_asyncMsg[0] = '\0';
    if (bc_login_nav(&g_app.Cfg(), &body, &len, err, (int)sizeof(err)) == 0 &&
        body != NULL) {
        char uname[128];
        char uansi[128];
        long mid;
        long code;

        uname[0] = '\0';
        uansi[0] = '\0';
        json_get_string(body, "uname", uname, (int)sizeof(uname));
        /* 接口回的是 UTF-8，界面/日志都按 GBK 走，这里先转一道 */
        utf8_to_ansi(uname, uansi, (int)sizeof(uansi));
        mid = body_num(body, "mid");
        code = body_num(body, "code");
        g_app.GetLogger().Log("MyWindow: nav code=%ld mid=%ld uname=%.40s",
                              code, mid, uansi);
        if (code == 0 && uansi[0] != '\0') {
            sprintf(s_self->m_asyncMsg, "%s (UID %ld)", uansi, mid);
            s_self->m_qrState = 0;
        } else {
            sprintf(s_self->m_asyncMsg, "未登录或 Cookie 失效 (code=%ld)",
                    code);
        }
        free(body);
    } else {
        str_copy(s_self->m_asyncMsg, (int)sizeof(s_self->m_asyncMsg),
                 (err[0] != '\0') ? err : "取资料失败");
    }
    ::PostMessage(s_self->m_hWnd, MZ_WM_MY_ASYNC, OP_NAV, 0);
}

/* ---------- 初始化 ---------- */

BOOL MyWindow::OnInitDialog()
{
    HFONT hFont;

    if (!CMzWndEx::OnInitDialog()) {
        return FALSE;
    }
    s_self = this;
    SetBgColor(RGB(236, 236, 236));

    m_caption.SetPos(0, 0, GetWidth(), MZM_HEIGHT_CAPTION);
    m_caption.SetText(L"我的");
    AddUiWin(&m_caption);

    m_btnBack.SetButtonType(MZC_BUTTON_PELLUCID);
    m_btnBack.SetID(MY_ID_BACK);
    m_btnBack.SetText(L"返回");
    AddUiWin(&m_btnBack);

    /* 入口三按钮 */
    m_btnSms.SetButtonType(MZC_BUTTON_GREEN);
    m_btnSms.SetID(MY_ID_SMS);
    m_btnSms.SetText(L"验证码登录");
    AddUiWin(&m_btnSms);

    m_btnQr.SetButtonType(MZC_BUTTON_PELLUCID);
    m_btnQr.SetID(MY_ID_QR);
    m_btnQr.SetText(L"扫码登录");
    AddUiWin(&m_btnQr);

    m_btnCookie.SetButtonType(MZC_BUTTON_PELLUCID);
    m_btnCookie.SetID(MY_ID_COOKIE);
    m_btnCookie.SetText(L"手动登录");
    AddUiWin(&m_btnCookie);

    /* 验证码页 */
    m_lblPhone.SetText(L"手机号");
    m_lblPhone.SetTextSize(MZFS_TINY);
    AddUiWin(&m_lblPhone);

    m_hPhone = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
        WS_CHILD | ES_AUTOHSCROLL,
        10, 60, 200, 46, m_hWnd, (HMENU)(INT_PTR)MY_EDIT_PHONE, NULL, NULL);
    m_lblCode.SetText(L"验证码");
    m_lblCode.SetTextSize(MZFS_TINY);
    AddUiWin(&m_lblCode);

    m_hCode = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
        WS_CHILD | ES_AUTOHSCROLL,
        10, 120, 200, 46, m_hWnd, (HMENU)(INT_PTR)MY_EDIT_CODE, NULL, NULL);

    m_btnSend.SetButtonType(MZC_BUTTON_PELLUCID);
    m_btnSend.SetID(MY_ID_SEND);
    m_btnSend.SetText(L"发送验证码");
    AddUiWin(&m_btnSend);

    m_btnLogin.SetButtonType(MZC_BUTTON_GREEN);
    m_btnLogin.SetID(MY_ID_LOGIN);
    m_btnLogin.SetText(L"登录");
    AddUiWin(&m_btnLogin);

    /* 手动 Cookie 页 */
    m_hCookie = CreateWindowExW(0, L"EDIT", L"",
        WS_CHILD | WS_VSCROLL | ES_MULTILINE | ES_AUTOVSCROLL,
        10, 60, 200, 200, m_hWnd, (HMENU)(INT_PTR)MY_EDIT_COOKIE, NULL, NULL);

    m_btnOk.SetButtonType(MZC_BUTTON_GREEN);
    m_btnOk.SetID(MY_ID_OK);
    m_btnOk.SetText(L"确定");
    AddUiWin(&m_btnOk);

    hFont = (HFONT)GetStockObject(SYSTEM_FONT);
    if (hFont != NULL && m_hPhone != NULL) {
        ::SendMessageW(m_hPhone, WM_SETFONT, (WPARAM)hFont, TRUE);
        ::SendMessageW(m_hCode, WM_SETFONT, (WPARAM)hFont, TRUE);
        ::SendMessageW(m_hCookie, WM_SETFONT, (WPARAM)hFont, TRUE);
    }

    /* 扫码页 */
    m_lblQr.SetText(L"请用哔哩哔哩 App 扫码");
    m_lblQr.SetTextSize(MZFS_TINY);
    AddUiWin(&m_lblQr);
    m_lblQrStat.SetText(L"");
    m_lblQrStat.SetTextSize(MZFS_TINY);
    m_lblQrStat.SetTextColor(MZCLR_FONT_DARK_GRAY);
    AddUiWin(&m_lblQrStat);
    m_qr.m_modules = NULL;
    AddUiWin(&m_qr);

    /* 已登录页 */
    m_lblStatus.SetText(L"");
    m_lblStatus.SetTextSize(MZFS_TINY);
    AddUiWin(&m_lblStatus);

    m_btnLogout.SetButtonType(MZC_BUTTON_PELLUCID);
    m_btnLogout.SetID(MY_ID_LOGOUT);
    m_btnLogout.SetText(L"退出登录");
    AddUiWin(&m_btnLogout);

    /* 可点的控件提到最上层：Mzfc 里只要被别的控件压住就点不到，
     * 返回/验证码登录只有一半能点就是这个原因。
     * （CMzWndEx 自身不是 UiWin，z-order 接口在它的 WinManager 上） */
    GetWinManager()->SetZOrderOfChild(&m_btnBack, 999);
    GetWinManager()->SetZOrderOfChild(&m_btnSms, 999);
    GetWinManager()->SetZOrderOfChild(&m_btnQr, 999);
    GetWinManager()->SetZOrderOfChild(&m_btnCookie, 999);
    GetWinManager()->SetZOrderOfChild(&m_btnSend, 999);
    GetWinManager()->SetZOrderOfChild(&m_btnLogin, 999);
    GetWinManager()->SetZOrderOfChild(&m_btnOk, 999);
    GetWinManager()->SetZOrderOfChild(&m_btnLogout, 999);

    if (bc_login_is_logged_in(&g_app.Cfg())) {
        m_mode = 4;
        LoadNav();
    } else {
        m_mode = 0;
    }
    Layout();
    ApplyMode();
    g_app.GetLogger().Log("MyWindow: OnInitDialog 完成 size=%dx%d mode=%d captionH=%d",
                          GetWidth(), GetHeight(), m_mode, (int)MZM_HEIGHT_CAPTION);
    return TRUE;
}

void MyWindow::Layout()
{
    int w = GetWidth();
    int h = GetHeight();
    int y = MZM_HEIGHT_CAPTION + 10;
    int gap = 10;
    int fh = 46;

    m_btnBack.SetPos(w - 120, h - 70, 100, 50);

    /* 入口 */
    m_btnSms.SetPos(20, y, w - 40, 60); y += 60 + gap;
    m_btnQr.SetPos(20, y, w - 40, 60); y += 60 + gap;
    m_btnCookie.SetPos(20, y, w - 40, 60);

    /* 验证码 */
    y = MZM_HEIGHT_CAPTION + 10;
    m_lblPhone.SetPos(20, y, w - 40, 20); y += 22;
    MoveWindow(m_hPhone, 20, y, w - 40, fh, TRUE); y += fh + gap;
    m_lblCode.SetPos(20, y, w - 40, 20); y += 22;
    MoveWindow(m_hCode, 20, y, w - 40, fh, TRUE); y += fh + gap;
    m_btnSend.SetPos(20, y, (w - 50) / 2, 56);
    m_btnLogin.SetPos(30 + (w - 50) / 2, y, (w - 50) / 2, 56);

    /* 手动 */
    MoveWindow(m_hCookie, 20, MZM_HEIGHT_CAPTION + 10, w - 40,
               h - MZM_HEIGHT_CAPTION - 160, TRUE);
    m_btnOk.SetPos(20, h - 70, w - 150, 50);

    /* 扫码 */
    m_lblQr.SetPos(20, MZM_HEIGHT_CAPTION + 10, w - 40, 20);
    m_qr.SetPos(20, MZM_HEIGHT_CAPTION + 34, w - 40, 300);
    m_lblQrStat.SetPos(20, MZM_HEIGHT_CAPTION + 340, w - 40, 40);

    /* 已登录：退出登录放最上面 */
    m_btnLogout.SetPos(20, MZM_HEIGHT_CAPTION + 10, w - 40, 60);
    /* 状态行：躲开右下角的「返回」和底部的「确定」，
     * 否则它会把点击都吃掉（返回点不动就是这个原因）。 */
    m_lblStatus.SetPos(20, h - 140, w - 160, 52);
}

void MyWindow::ApplyMode()
{
    int login = (m_mode == 4);
    int sms = (m_mode == 1);
    int qr = (m_mode == 2);
    int ck = (m_mode == 3);
    int w = GetWidth();

    Layout();   /* 先按当前尺寸摆好所有控件，再处理不属于当前模式的 */

    /* 关键：Mzfc 的 SetVisible(0) 只是不画，控件仍然参与命中测试。
     * 不挪走的话，它们会把下面按钮的点击全吃掉（返回点不动、
     * 验证码登录只有一半能点，都是这个原因）。所以一律挪到屏幕外。 */
    #define MY_PARK(win) do { (win).SetVisible(0); (win).SetPos(0, -2000, 8, 8); } while (0)

    if (m_mode != 0) {
        MY_PARK(m_btnSms);
        MY_PARK(m_btnQr);
        MY_PARK(m_btnCookie);
    } else {
        m_btnSms.SetVisible(1);
        m_btnQr.SetVisible(1);
        m_btnCookie.SetVisible(1);
    }

    if (sms) {
        m_lblPhone.SetVisible(1);
        m_lblCode.SetVisible(1);
        m_btnSend.SetVisible(1);
        m_btnLogin.SetVisible(1);
        ::ShowWindow(m_hPhone, SW_SHOW);
        ::ShowWindow(m_hCode, SW_SHOW);
    } else {
        MY_PARK(m_lblPhone);
        MY_PARK(m_lblCode);
        MY_PARK(m_btnSend);
        MY_PARK(m_btnLogin);
        ::ShowWindow(m_hPhone, SW_HIDE);
        ::ShowWindow(m_hCode, SW_HIDE);
        MoveWindow(m_hPhone, 0, 0, 0, 0, TRUE);
        MoveWindow(m_hCode, 0, 0, 0, 0, TRUE);
    }

    if (ck) {
        m_btnOk.SetVisible(1);
        ::ShowWindow(m_hCookie, SW_SHOW);
    } else {
        MY_PARK(m_btnOk);
        ::ShowWindow(m_hCookie, SW_HIDE);
        MoveWindow(m_hCookie, 0, 0, 0, 0, TRUE);
    }

    if (qr) {
        m_lblQr.SetVisible(1);
        m_lblQrStat.SetVisible(1);
        m_qr.m_active = 1;
        m_qr.SetVisible(1);
        m_qr.SetPos(20, MZM_HEIGHT_CAPTION + 34, w - 40, 300);
    } else {
        MY_PARK(m_lblQr);
        MY_PARK(m_lblQrStat);
        m_qr.m_active = 0;
        m_qr.SetVisible(0);
        m_qr.SetPos(0, -2000, w - 40, 300);
    }
    m_qr.Invalidate();
    m_qr.Update();

    if (login) {
        m_btnLogout.SetVisible(1);
    } else {
        MY_PARK(m_btnLogout);
    }

    m_lblStatus.SetVisible(1);

    /* 连背景一起擦再重画：否则二维码那块停画后旧像素会留在屏幕上 */
    InvalidateRect(m_hWnd, NULL, TRUE);
    ::UpdateWindow(m_hWnd);
}

/* ---------- 动作 ---------- */

void MyWindow::StartQr()
{
    m_qrState = -2;
    m_lblQrStat.SetText(L"正在获取二维码…");
    m_lblQrStat.Invalidate();
    m_lblQrStat.Update();
    g_app.RunAsync(job_qr_gen, NULL);
}

void MyWindow::PollQr()
{
    if (m_qrKey[0] == '\0' || m_asyncOp != 0) {
        return;
    }
    m_asyncOp = OP_QR_POLL;
    g_app.RunAsync(job_qr_poll, NULL);
}

void MyWindow::SendSms()
{
    wchar_t w[32];

    w[0] = L'\0';
    ::GetWindowTextW(m_hPhone, w, 32);
    WideCharToMultiByte(CP_ACP, 0, w, -1, m_phone, (int)sizeof(m_phone), NULL, NULL);
    if (m_phone[0] == '\0') {
        m_lblStatus.SetText(L"请先填手机号");
        m_lblStatus.SetVisible(1);
        m_lblStatus.Invalidate(); m_lblStatus.Update();
        return;
    }
    m_asyncMsg[0] = '\0';
    m_lblStatus.SetText(L"正在发送验证码…");
    m_lblStatus.SetVisible(1);
    m_lblStatus.Invalidate(); m_lblStatus.Update();
    g_app.GetLogger().Log("MyWindow: 发送验证码 phone=%s", m_phone);
    m_asyncOp = OP_SMS_SEND;
    g_app.RunAsync(job_sms_send, NULL);
}

void MyWindow::SubmitSms()
{
    wchar_t w[16];

    w[0] = L'\0';
    ::GetWindowTextW(m_hCode, w, 16);
    WideCharToMultiByte(CP_ACP, 0, w, -1, m_code, (int)sizeof(m_code), NULL, NULL);
    if (m_code[0] == '\0') {
        m_lblStatus.SetText(L"请填验证码");
        m_lblStatus.SetVisible(1);
        m_lblStatus.Invalidate(); m_lblStatus.Update();
        return;
    }
    m_lblStatus.SetText(L"正在登录…");
    m_lblStatus.SetVisible(1);
    m_lblStatus.Invalidate(); m_lblStatus.Update();
    m_asyncOp = OP_SMS_LOGIN;
    g_app.RunAsync(job_sms_login, NULL);
}

void MyWindow::SubmitCookie()
{
    int len;
    wchar_t *w;
    char *c;

    len = ::GetWindowTextLengthW(m_hCookie);
    if (len <= 0) {
        m_lblStatus.SetText(L"请粘贴 Cookie");
        m_lblStatus.SetVisible(1);
        m_lblStatus.Invalidate(); m_lblStatus.Update();
        return;
    }
    w = (wchar_t *)malloc((size_t)(len + 1) * sizeof(wchar_t));
    c = (char *)malloc((size_t)(len + 1) * 4);
    if (w == NULL || c == NULL) {
        if (w) free(w);
        if (c) free(c);
        return;
    }
    ::GetWindowTextW(m_hCookie, w, len + 1);
    WideCharToMultiByte(CP_ACP, 0, w, -1, c, (len + 1) * 4, NULL, NULL);
    free(w);
    bc_login_set_cookie(&g_app.Cfg(), c);
    free(c);
    m_lblStatus.SetText(L"Cookie 已保存，正在取资料…");
    m_lblStatus.SetVisible(1);
    m_lblStatus.Invalidate(); m_lblStatus.Update();
    m_asyncOp = OP_NAV;
    g_app.RunAsync(job_nav, NULL);
}

void MyWindow::DoLogout()
{
    bc_login_logout(&g_app.Cfg());
    m_mode = 0;
    m_profile = L"";
    ApplyMode();
}

void MyWindow::LoadNav()
{
    m_lblStatus.SetText(L"正在取资料…");
    m_lblStatus.Invalidate(); m_lblStatus.Update();
    m_asyncOp = OP_NAV;
    g_app.RunAsync(job_nav, NULL);
}

void MyWindow::RefreshProfileText()
{
    m_lblStatus.SetText(m_profile.c_str());
    m_lblStatus.Invalidate();
    m_lblStatus.Update();
}

void MyWindow::DrawQr(HDC hdc)
{
    (void)hdc;
}

/* ---------- 事件 ---------- */

void MyWindow::OnMzCommand(WPARAM wParam, LPARAM lParam)
{
    UINT id = LOWORD(wParam);

    switch (id) {
    case MY_ID_BACK:
        EndModal(ID_CANCEL);
        break;
    case MY_ID_SMS:
        m_mode = 1;
        ApplyMode();
        break;
    case MY_ID_QR:
        m_mode = 2;
        ApplyMode();
        if (!m_qrInited) {
            m_qrInited = 1;
            StartQr();
            SetTimer(m_hWnd, 1, 2500, NULL);
        }
        break;
    case MY_ID_COOKIE:
        m_mode = 3;
        ApplyMode();
        break;
    case MY_ID_SEND:
        SendSms();
        break;
    case MY_ID_LOGIN:
        SubmitSms();
        break;
    case MY_ID_OK:
        SubmitCookie();
        break;
    case MY_ID_LOGOUT:
        DoLogout();
        break;
    default:
        break;
    }
}

void MyWindow::OnTimer(UINT_PTR nIDEvent)
{
    if (nIDEvent == 1) {
        if (m_mode == 2 && m_qrKey[0] != '\0') {
            PollQr();
        }
        return;
    }
    CMzWndEx::OnTimer(nIDEvent);
}

void MyWindow::OnSize(int nWidth, int nHeight)
{
    CMzWndEx::OnSize(nWidth, nHeight);
    Layout();
}

LRESULT MyWindow::MzDefWndProc(UINT message, WPARAM wParam, LPARAM lParam)
{
    if (message == WM_KEYDOWN &&
        (wParam == VK_ESCAPE || wParam == VK_BACK)) {
        EndModal(ID_CANCEL);
        return 0;
    }
    if (message == MZ_WM_MY_ASYNC) {
        int op = (int)wParam;

        m_asyncOp = 0;
        if (op == OP_QR_GEN) {
            if (m_qrState == 0) {
                static unsigned char mods[QR_SIZE * QR_SIZE];
                if (qr_encode(m_qrUrl, mods)) {
                    m_qr.m_modules = mods;
                    m_qr.m_ok = 1;
                } else {
                    m_qr.m_ok = 0;
                }
                m_qr.Invalidate(); m_qr.Update();
                m_lblQrStat.SetText(L"请用哔哩哔哩 App 扫码");
            } else {
                m_lblQrStat.SetText(gbk2w(m_qrErr).c_str());
            }
            m_lblQrStat.Invalidate(); m_lblQrStat.Update();
        } else if (op == OP_QR_POLL) {
            if (m_qrState == 0) {
                wchar_t *t = (wchar_t *)L"登录成功！";
                KillTimer(m_hWnd, 1);
                m_lblQrStat.SetText(t);
                m_lblQrStat.Invalidate(); m_lblQrStat.Update();
                m_mode = 4;
                LoadNav();
                ApplyMode();
            } else if (m_qrState == 86090) {
                m_lblQrStat.SetText(L"已扫码，请在手机上确认");
            } else if (m_qrState == 86038) {
                m_lblQrStat.SetText(L"二维码已过期，重新获取");
                m_qrInited = 0;
            } else if (m_qrState == 86101) {
                m_lblQrStat.SetText(L"等待扫码…");
            } else {
                g_app.GetLogger().Log("MyWindow: 扫码失败 cross=%.300s",
                                      g_login_qr_cross);
                m_lblQrStat.SetText(gbk2w(m_qrErr).c_str());
            }
            m_lblQrStat.Invalidate(); m_lblQrStat.Update();
        } else if (op == OP_SMS_SEND) {
            g_app.GetLogger().Log("MyWindow: 短信请求=%.500s", g_login_sms_req);
            g_app.GetLogger().Log("MyWindow: 短信原始响应=%.300s", g_login_sms_raw);
            m_lblStatus.SetText(gbk2w(m_asyncMsg).c_str());
            m_lblStatus.Invalidate(); m_lblStatus.Update();
        } else if (op == OP_SMS_LOGIN) {
            if (m_qrState == 0) {
                m_mode = 4;
                LoadNav();
                ApplyMode();
            } else {
                m_lblStatus.SetText(gbk2w(m_asyncMsg).c_str());
                m_lblStatus.Invalidate(); m_lblStatus.Update();
            }
        } else if (op == OP_NAV) {
            if (m_qrState == 0) {
                m_profile = gbk2w(m_asyncMsg);
                RefreshProfileText();
            } else {
                m_lblStatus.SetText(gbk2w(m_asyncMsg).c_str());
                m_lblStatus.Invalidate(); m_lblStatus.Update();
            }
        }
        return 0;
    }
    if (message == WM_COMMAND) {
        UINT id = LOWORD(wParam);
        UINT code = HIWORD(wParam);

        if (id == MY_EDIT_PHONE || id == MY_EDIT_CODE) {
            if (code == EN_SETFOCUS) {
                MzOpenSip(IM_SIP_MODE_DIGIT);
            } else if (code == EN_KILLFOCUS) {
                MzCloseSip();
            }
            return 0;
        }
        if (id == MY_EDIT_COOKIE) {
            if (code == EN_SETFOCUS) {
                MzOpenSip();
            } else if (code == EN_KILLFOCUS) {
                MzCloseSip();
            }
            return 0;
        }
    }
    return CMzWndEx::MzDefWndProc(message, wParam, lParam);
}

int MyWindow::DoModal()
{
    int sw = GetSystemMetrics(SM_CXSCREEN);
    int sh = GetSystemMetrics(SM_CYSCREEN);

    if (sw <= 0) {
        sw = 480;
    }
    if (sh <= 0) {
        sh = 720;
    }
    s_self = this;
    g_app.GetLogger().Log("MyWindow: DoModal 创建 %dx%d", sw, sh);
    if (!Create(0, 0, sw, sh, GetForegroundWindow(), 0, WS_POPUP)) {
        Create(0, 0, sw, sh, NULL, 0, WS_POPUP);
    }
    return CMzWndEx::DoModal();
}
