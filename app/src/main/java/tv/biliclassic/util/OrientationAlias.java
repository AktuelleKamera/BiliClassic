package tv.biliclassic.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import tv.biliclassic.R;

public final class OrientationAlias {

    private static final String[][] MAP = {
            {"tv.biliclassic.MainActivity", "tv.biliclassic.MainActivity",
                    "tv.biliclassic.MainActivityLandscape", "tv.biliclassic.MainActivitySensor"},
            {"tv.biliclassic.SearchActivity", "tv.biliclassic.SearchActivity",
                    "tv.biliclassic.SearchActivityLandscape", "tv.biliclassic.SearchActivitySensor"},
            {"tv.biliclassic.VideoDetailActivity", "tv.biliclassic.VideoDetailActivity",
                    "tv.biliclassic.VideoDetailActivityLandscape", "tv.biliclassic.VideoDetailActivitySensor"},
            {"tv.biliclassic.HistoryActivity", "tv.biliclassic.HistoryActivity",
                    "tv.biliclassic.HistoryActivityLandscape", "tv.biliclassic.HistoryActivitySensor"},
            {"tv.biliclassic.WebViewActivity", "tv.biliclassic.WebViewActivity",
                    "tv.biliclassic.WebViewActivityLandscape", "tv.biliclassic.WebViewActivitySensor"},
            {"tv.biliclassic.OfflineActivity", "tv.biliclassic.OfflineActivity",
                    "tv.biliclassic.OfflineActivityLandscape", "tv.biliclassic.OfflineActivitySensor"},
            {"tv.biliclassic.metro.MetroHomeActivity", "tv.biliclassic.metro.MetroHomeActivity",
                    "tv.biliclassic.metro.MetroHomeActivityLandscape", "tv.biliclassic.metro.MetroHomeActivitySensor"},
            {"tv.biliclassic.metro.MetroSetupActivity", "tv.biliclassic.metro.MetroSetupActivity",
                    "tv.biliclassic.metro.MetroSetupActivityLandscape", "tv.biliclassic.metro.MetroSetupActivitySensor"},
            {"tv.biliclassic.AboutActivity", "tv.biliclassic.AboutActivity",
                    "tv.biliclassic.AboutActivityLandscape", "tv.biliclassic.AboutActivitySensor"},
            {"tv.biliclassic.ArticleActivity", "tv.biliclassic.ArticleActivity",
                    "tv.biliclassic.ArticleActivityLandscape", "tv.biliclassic.ArticleActivitySensor"},
            {"tv.biliclassic.CrashReportActivity", "tv.biliclassic.CrashReportActivity",
                    "tv.biliclassic.CrashReportActivityLandscape", "tv.biliclassic.CrashReportActivitySensor"},
            {"tv.biliclassic.CropActivity", "tv.biliclassic.CropActivity",
                    "tv.biliclassic.CropActivityLandscape", "tv.biliclassic.CropActivitySensor"},
            {"tv.biliclassic.DeviceInfoActivity", "tv.biliclassic.DeviceInfoActivity",
                    "tv.biliclassic.DeviceInfoActivityLandscape", "tv.biliclassic.DeviceInfoActivitySensor"},
            {"tv.biliclassic.DynamicDetailActivity", "tv.biliclassic.DynamicDetailActivity",
                    "tv.biliclassic.DynamicDetailActivityLandscape", "tv.biliclassic.DynamicDetailActivitySensor"},
            {"tv.biliclassic.FavoriteFolderListActivity", "tv.biliclassic.FavoriteFolderListActivity",
                    "tv.biliclassic.FavoriteFolderListActivityLandscape", "tv.biliclassic.FavoriteFolderListActivitySensor"},
            {"tv.biliclassic.FavoriteVideoListActivity", "tv.biliclassic.FavoriteVideoListActivity",
                    "tv.biliclassic.FavoriteVideoListActivityLandscape", "tv.biliclassic.FavoriteVideoListActivitySensor"},
            {"tv.biliclassic.FollowingListActivity", "tv.biliclassic.FollowingListActivity",
                    "tv.biliclassic.FollowingListActivityLandscape", "tv.biliclassic.FollowingListActivitySensor"},
            {"tv.biliclassic.HelpListActivity", "tv.biliclassic.HelpListActivity",
                    "tv.biliclassic.HelpListActivityLandscape", "tv.biliclassic.HelpListActivitySensor"},
            {"tv.biliclassic.ImageViewerActivity", "tv.biliclassic.ImageViewerActivity",
                    "tv.biliclassic.ImageViewerActivityLandscape", "tv.biliclassic.ImageViewerActivitySensor"},
            {"tv.biliclassic.KeyBindingSetupActivity", "tv.biliclassic.KeyBindingSetupActivity",
                    "tv.biliclassic.KeyBindingSetupActivityLandscape", "tv.biliclassic.KeyBindingSetupActivitySensor"},
            {"tv.biliclassic.LiveInfoActivity", "tv.biliclassic.LiveInfoActivity",
                    "tv.biliclassic.LiveInfoActivityLandscape", "tv.biliclassic.LiveInfoActivitySensor"},
            {"tv.biliclassic.LiveRoomListActivity", "tv.biliclassic.LiveRoomListActivity",
                    "tv.biliclassic.LiveRoomListActivityLandscape", "tv.biliclassic.LiveRoomListActivitySensor"},
            {"tv.biliclassic.LoginActivity", "tv.biliclassic.LoginActivity",
                    "tv.biliclassic.LoginActivityLandscape", "tv.biliclassic.LoginActivitySensor"},
            {"tv.biliclassic.PartitionDetailActivity", "tv.biliclassic.PartitionDetailActivity",
                    "tv.biliclassic.PartitionDetailActivityLandscape", "tv.biliclassic.PartitionDetailActivitySensor"},
            {"tv.biliclassic.PrivateMsgActivity", "tv.biliclassic.PrivateMsgActivity",
                    "tv.biliclassic.PrivateMsgActivityLandscape", "tv.biliclassic.PrivateMsgActivitySensor"},
            {"tv.biliclassic.PrivateMsgListActivity", "tv.biliclassic.PrivateMsgListActivity",
                    "tv.biliclassic.PrivateMsgListActivityLandscape", "tv.biliclassic.PrivateMsgListActivitySensor"},
            {"tv.biliclassic.ReplyListActivity", "tv.biliclassic.ReplyListActivity",
                    "tv.biliclassic.ReplyListActivityLandscape", "tv.biliclassic.ReplyListActivitySensor"},
            {"tv.biliclassic.SettingsActivity", "tv.biliclassic.SettingsActivity",
                    "tv.biliclassic.SettingsActivityLandscape", "tv.biliclassic.SettingsActivitySensor"},
            {"tv.biliclassic.UserProfileActivity", "tv.biliclassic.UserProfileActivity",
                    "tv.biliclassic.UserProfileActivityLandscape", "tv.biliclassic.UserProfileActivitySensor"},
            {"tv.biliclassic.subsettings.DecoderSettingsActivity", "tv.biliclassic.subsettings.DecoderSettingsActivity",
                    "tv.biliclassic.subsettings.DecoderSettingsActivityLandscape", "tv.biliclassic.subsettings.DecoderSettingsActivitySensor"},
            {"tv.biliclassic.player.AudioPlayerActivity", "tv.biliclassic.player.AudioPlayerActivity",
                    "tv.biliclassic.player.AudioPlayerActivityLandscape", "tv.biliclassic.player.AudioPlayerActivitySensor"},
    };

    public static void updateLauncher(Context context, boolean landscape) {
        if (context == null) return;
        boolean tablet = SdkHelper.getBooleanResource(context.getResources(), R.bool.is_tablet);
        String target = tablet ? "tv.biliclassic.MainLauncherSensor"
                : (landscape ? "tv.biliclassic.MainLauncherLandscape"
                        : "tv.biliclassic.MainLauncherPortrait");
        String[] aliases = {
                "tv.biliclassic.MainLauncherPortrait",
                "tv.biliclassic.MainLauncherLandscape",
                "tv.biliclassic.MainLauncherSensor"
        };
        try {
            for (String alias : aliases) {
                context.getPackageManager().setComponentEnabledSetting(
                        new ComponentName(context.getPackageName(), alias),
                        alias.equals(target)
                                ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP);
            }
        } catch (Throwable ignored) {
        }
    }

    public static Intent wrap(Context context, Intent intent) {
        if (context == null || intent == null || intent.getComponent() == null) {
            return intent;
        }
        String className = intent.getComponent().getClassName();
        for (String[] row : MAP) {
            if (!row[0].equals(className)) continue;
            boolean tablet = SdkHelper.getBooleanResource(context.getResources(), R.bool.is_tablet);
            boolean landscape = SharedPreferencesUtil.getBoolean("landscape_enabled", false);
            String target = tablet ? row[3] : (landscape ? row[2] : row[1]);
            if (target == null || target.equals(className)) return intent;
            Intent copy = new Intent(intent);
            copy.setComponent(new ComponentName(context.getPackageName(), target));
            return copy;
        }
        return intent;
    }

    private OrientationAlias() {
    }
}
