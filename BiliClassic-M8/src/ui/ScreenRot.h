/* =====================================================================
 * ScreenRot.h - 屏幕方向切换（横屏播放 / 恢复原始方向）
 * ---------------------------------------------------------------------
 * 开机时记住原始 dmDisplayOrientation；进播放转横屏，退出按原值恢复。
 * 单次 ChangeDisplaySettingsEx 常不生效，内部带重试。
 * ===================================================================== */
#ifndef UI_SCREENROT_H
#define UI_SCREENROT_H

/* 是否横屏（宽 > 高） */
int  bc_screen_is_landscape(void);

/* 转横屏（已是横屏则不动） */
void bc_screen_landscape(void);

/* 恢复开机时的原始方向（通常是竖屏） */
void bc_screen_portrait(void);

#endif /* UI_SCREENROT_H */
