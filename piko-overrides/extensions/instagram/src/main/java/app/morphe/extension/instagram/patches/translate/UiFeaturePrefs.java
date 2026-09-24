package app.morphe.extension.instagram.patches.translate;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.sharedPreference.SharedPref;

public final class UiFeaturePrefs {
    public static final BooleanSetting SHOW_TOP_TRANSLATE_BUTTON =
        new BooleanSetting("piko_zh_show_top_translate_button", false);
    public static final BooleanSetting COMMENT_TRANSLATE_MENU =
        new BooleanSetting("piko_zh_comment_translate_menu", false);
    public static final BooleanSetting SHOW_QUICK_DOWNLOAD_BUTTON =
        new BooleanSetting("piko_zh_show_quick_download_button", false);

    private UiFeaturePrefs() {}

    public static boolean showTopTranslateButton() {
        return SharedPref.getBooleanPref(SHOW_TOP_TRANSLATE_BUTTON);
    }

    public static boolean commentTranslateMenu() {
        return SharedPref.getBooleanPref(COMMENT_TRANSLATE_MENU);
    }

    public static boolean showQuickDownloadButton() {
        return SharedPref.getBooleanPref(SHOW_QUICK_DOWNLOAD_BUTTON);
    }
}
