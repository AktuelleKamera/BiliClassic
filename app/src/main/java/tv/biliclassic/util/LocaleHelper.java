package tv.biliclassic.util;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {

    private static final String KEY_LOCALE = "app_locale";
    private static final String LOCALE_ZH_CN = "zh_CN";
    private static final String LOCALE_ZH_TW = "zh_TW";

    private static Locale sCurrentLocale;
    private static boolean sInitialized = false;

    public static void init(Context context) {
        String saved = SharedPreferencesUtil.getString(KEY_LOCALE, LOCALE_ZH_CN);
        sCurrentLocale = createLocale(saved);
        Locale.setDefault(sCurrentLocale);
        sInitialized = true;
    }

    public static Context wrapContext(Context context) {
        if (!sInitialized) {
            init(context);
        }
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.locale = sCurrentLocale;
        if (SdkHelper.getSdkInt() >= 17) {
            try {
                Configuration.class.getMethod("setLocale", Locale.class).invoke(config, sCurrentLocale);
                Context ctx = (Context) Context.class.getMethod("createConfigurationContext", Configuration.class).invoke(context, config);
                if (ctx != null) return ctx;
            } catch (Exception e) {
            }
        }
        android.content.res.Resources res = context.getResources();
        res.updateConfiguration(config, res.getDisplayMetrics());
        return context;
    }

    public static void updateResourcesLocale(Context context) {
        if (!sInitialized) {
            init(context);
        }
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (SdkHelper.getSdkInt() >= 17) {
            try {
                Configuration.class.getMethod("setLocale", Locale.class).invoke(config, sCurrentLocale);
            } catch (Exception e) {
                config.locale = sCurrentLocale;
            }
        } else {
            config.locale = sCurrentLocale;
        }
        android.content.res.Resources res = context.getResources();
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    public static String getCurrentLocale() {
        return SharedPreferencesUtil.getString(KEY_LOCALE, LOCALE_ZH_CN);
    }

    public static void setCurrentLocale(String lang) {
        SharedPreferencesUtil.putString(KEY_LOCALE, lang);
        sCurrentLocale = createLocale(lang);
        Locale.setDefault(sCurrentLocale);
    }

    public static String[] getLocaleNames() {
        return new String[]{"简体中文", "繁體中文"};
    }

    public static String[] getLocaleValues() {
        return new String[]{LOCALE_ZH_CN, LOCALE_ZH_TW};
    }

    public static Locale getLocale() {
        if (!sInitialized) {
            return new Locale("zh", "CN");
        }
        return sCurrentLocale;
    }

    private static Locale createLocale(String lang) {
        if (LOCALE_ZH_TW.equals(lang)) {
            return new Locale("zh", "TW");
        }
        return new Locale("zh", "CN");
    }
}