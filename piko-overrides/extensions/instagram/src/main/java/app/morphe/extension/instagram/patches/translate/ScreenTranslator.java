/*
 * Simplified Chinese fork addition for Piko Instagram.
 * Uses Instagram's own visible "See translation" controls instead of a third-party API.
 */
package app.morphe.extension.instagram.patches.translate;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class ScreenTranslator {
    private static final int MAX_VISIBLE_TRANSLATIONS = 30;
    private static final long CLICK_INTERVAL_MS = 70L;

    private static final Set<String> TRANSLATE_LABELS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "see translation", "view translation", "translate",
            "查看翻译", "显示翻译", "翻译",
            "查看翻譯", "顯示翻譯", "翻譯",
            "翻訳を見る", "翻訳", "번역 보기",
            "ver traducción", "voir la traduction", "übersetzung ansehen",
            "ver tradução", "vedi traduzione", "посмотреть перевод"
        ))
    );

    private ScreenTranslator() {}

    public static void translateVisibleScreen() {
        try {
            final Activity activity = Utils.getActivity();
            if (activity == null || activity.getWindow() == null) {
                Utils.showToastShort("当前屏幕没有可翻译内容");
                return;
            }
            activity.runOnUiThread(() -> {
                try {
                    View root = activity.getWindow().getDecorView();
                    List<View> targets = new ArrayList<>();
                    Set<View> seenTargets = Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
                    collectTranslationTargets(root, targets, seenTargets);
                    if (targets.isEmpty()) {
                        Utils.showToastShort("当前屏幕没有可翻译内容");
                        return;
                    }
                    Utils.showToastShort("正在翻译当前屏幕的 " + targets.size() + " 项内容");
                    Handler handler = new Handler(Looper.getMainLooper());
                    for (int i = 0; i < targets.size(); i++) {
                        final View target = targets.get(i);
                        handler.postDelayed(() -> {
                            try {
                                if (target.isShown() && target.isEnabled()) target.performClick();
                            } catch (Exception e) {
                                Logger.printException(() -> "Screen translation click failed", e);
                            }
                        }, i * CLICK_INTERVAL_MS);
                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Screen translation failed", e);
                }
            });
        } catch (Exception e) {
            Logger.printException(() -> "Screen translation setup failed", e);
        }
    }

    private static void collectTranslationTargets(View view, List<View> targets, Set<View> seenTargets) {
        if (view == null || targets.size() >= MAX_VISIBLE_TRANSLATIONS || !isVisible(view)) return;
        if (isNativeTranslationControl(view)) {
            View clickable = findClickableTarget(view);
            if (clickable != null && seenTargets.add(clickable)) {
                targets.add(clickable);
                if (targets.size() >= MAX_VISIBLE_TRANSLATIONS) return;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTranslationTargets(group.getChildAt(i), targets, seenTargets);
                if (targets.size() >= MAX_VISIBLE_TRANSLATIONS) return;
            }
        }
    }

    private static boolean isVisible(View view) {
        if (view.getVisibility() != View.VISIBLE || !view.isShown()) return false;
        Rect rect = new Rect();
        return view.getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0;
    }

    private static boolean isNativeTranslationControl(View view) {
        if (view instanceof TextView && matchesTranslationLabel(((TextView) view).getText())) return true;
        if (matchesTranslationLabel(view.getContentDescription())) return true;
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                String name = view.getResources().getResourceEntryName(id).toLowerCase(Locale.ROOT);
                boolean translationName = name.contains("translation") || name.contains("translate");
                boolean actionName = name.contains("button") || name.contains("cta") || name.contains("see_");
                return translationName && actionName;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static boolean matchesTranslationLabel(CharSequence value) {
        if (value == null) return false;
        String normalized = value.toString()
            .replace('\u00A0', ' ')
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
        return TRANSLATE_LABELS.contains(normalized);
    }

    private static View findClickableTarget(View view) {
        View current = view;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (current.isClickable() && current.isEnabled()) return current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }
}
