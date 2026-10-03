/* =====================================================================
 * ScreenRot.cpp - 屏幕方向切换实现
 * ---------------------------------------------------------------------
 * 这台 M8 的 EnumDisplaySettings 返回的 dmDisplayOrientation 不可靠
 *（竖屏时仍报 0/720x480），所以恢复竖屏不猜单一值，而是依次试
 * 常见方向值，直到 GetSystemMetrics 真的变回竖屏为止。
 * ===================================================================== */
#include "ScreenRot.h"
#include "../app/AppContext.h"

extern "C" LONG APIENTRY ChangeDisplaySettingsEx(LPCWSTR, LPDEVMODEW, HWND,
                                                 DWORD, LPVOID);
extern "C" BOOL APIENTRY EnumDisplaySettings(LPCWSTR, DWORD, LPDEVMODEW);

#ifndef ENUM_CURRENT_SETTINGS
#define ENUM_CURRENT_SETTINGS ((DWORD)-1)
#endif
#ifndef DM_DISPLAYORIENTATION
#define DM_DISPLAYORIENTATION 0x00000080
#endif

/* 试一个方向值；返回 0 表示调用成功（不代表已生效） */
static int bc_try_orient(DWORD orient)
{
    DEVMODE dm;

    memset(&dm, 0, sizeof(dm));
    dm.dmSize = sizeof(dm);
    if (!EnumDisplaySettings(NULL, ENUM_CURRENT_SETTINGS, &dm)) {
        return -1000;
    }
    dm.dmFields = DM_DISPLAYORIENTATION;
    dm.dmDisplayOrientation = orient;
    return (int)ChangeDisplaySettingsEx(NULL, &dm, NULL, 0, NULL);
}

int bc_screen_is_landscape(void)
{
    return GetSystemMetrics(SM_CXSCREEN) > GetSystemMetrics(SM_CYSCREEN);
}

void bc_screen_landscape(void)
{
    int i;

    for (i = 0; i < 6 && !bc_screen_is_landscape(); i++) {
        if (bc_try_orient(0) == -1000) {
            break;
        }
        Sleep(300);
    }
}

void bc_screen_portrait(void)
{
    /* DMDO_90=1 优先，再试度数风格 90、270 等 */
    static const DWORD cands[] = { 1, 90, 3, 270, 2, 180 };
    int c;
    int i;

    for (c = 0; c < (int)(sizeof(cands) / sizeof(cands[0])); c++) {
        for (i = 0; i < 3 && bc_screen_is_landscape(); i++) {
            if (bc_try_orient(cands[c]) == -1000) {
                break;
            }
            Sleep(250);
        }
        if (!bc_screen_is_landscape()) {
            break;
        }
    }
    g_app.GetLogger().Log("ScreenRot: 恢复竖屏 用 orient=%lu 屏幕 %dx%d",
                          (unsigned long)((c < 6) ? cands[c] : 0),
                          GetSystemMetrics(SM_CXSCREEN),
                          GetSystemMetrics(SM_CYSCREEN));
}
