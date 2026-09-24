/*
 * Piko zh-CN: translate all currently visible native Instagram translation actions.
 */
package app.morphe.extension.instagram.patches.translate;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class ScreenTranslator {
    private static final int MAX_VISIBLE_TRANSLATIONS = 30;
    private static final long CLICK_INTERVAL_MS = 90L;

    private static final Set<String> TRANSLATE_LABELS = new HashSet<>(Arrays.asList(
        "see translation",
        "view translation",
        "translate",
        "查看翻译",
        "显示翻译",
        "翻译",
        "查看翻譯",
        "顯示翻譯",
        "翻譯",
        "翻訳を見る",
        "翻訳",
        "번역 보기",
        "ver traducción",
        "voir la traduction",
        "übersetzung ansehen",
        "ver tradução",
        "vedi traduzione",
        "посмотреть перевод"
    ));

    private ScreenTranslator() {}

    public static void translateVisibleScreen() {
        try {
            final Activity activity = Utils.getActivity();
            if (activity == null || activity.getWindow() == null) return;

            activity.runOnUiThread(() -> {
                try {
                    View rootView = activity.getWindow().getDecorView();
                    AccessibilityNodeInfo root = rootView.createAccessibilityNodeInfo();
                    if (root == null) return;

                    List<AccessibilityNodeInfo> targets = new ArrayList<>();
                    Set<String> seen = new HashSet<>();
                    collectTranslationTargets(root, targets, seen);

                    Handler handler = new Handler(Looper.getMainLooper());
                    int limit = Math.min(MAX_VISIBLE_TRANSLATIONS, targets.size());
                    for (int i = 0; i < limit; i++) {
                        final AccessibilityNodeInfo target = targets.get(i);
                        handler.postDelayed(() -> performClick(target), i * CLICK_INTERVAL_MS);
                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Screen translation failed", e);
                }
            });
        } catch (Exception e) {
            Logger.printException(() -> "Screen translation setup failed", e);
        }
    }

    public static boolean clickVisibleText(String... labels) {
        try {
            final Activity activity = Utils.getActivity();
            if (activity == null || activity.getWindow() == null) return false;
            AccessibilityNodeInfo root = activity.getWindow().getDecorView().createAccessibilityNodeInfo();
            if (root == null) return false;

            Set<String> normalizedLabels = new HashSet<>();
            for (String label : labels) {
                if (label != null && !label.isEmpty()) normalizedLabels.add(normalize(label));
            }
            AccessibilityNodeInfo match = findFirstByText(root, normalizedLabels);
            return match != null && performClick(match);
        } catch (Exception e) {
            Logger.printException(() -> "Accessibility text click failed", e);
            return false;
        }
    }

    private static void collectTranslationTargets(
        AccessibilityNodeInfo node,
        List<AccessibilityNodeInfo> targets,
        Set<String> seen
    ) {
        if (node == null || targets.size() >= MAX_VISIBLE_TRANSLATIONS) return;

        if (matchesTranslationNode(node)) {
            AccessibilityNodeInfo clickable = findClickableNode(node);
            if (clickable != null) {
                String key = nodeKey(clickable);
                if (seen.add(key)) targets.add(clickable);
            }
        }

        for (int i = 0; i < node.getChildCount() && targets.size() < MAX_VISIBLE_TRANSLATIONS; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectTranslationTargets(child, targets, seen);
        }
    }

    private static AccessibilityNodeInfo findFirstByText(
        AccessibilityNodeInfo node,
        Set<String> labels
    ) {
        if (node == null) return null;
        if (matchesAny(node.getText(), labels) || matchesAny(node.getContentDescription(), labels)) {
            AccessibilityNodeInfo clickable = findClickableNode(node);
            if (clickable != null) return clickable;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findFirstByText(child, labels);
            if (result != null) return result;
        }
        return null;
    }

    private static boolean matchesTranslationNode(AccessibilityNodeInfo node) {
        if (matchesTranslate(node.getText()) || matchesTranslate(node.getContentDescription())) {
            return true;
        }
        try {
            String viewId = node.getViewIdResourceName();
            if (viewId != null) {
                String lower = viewId.toLowerCase(Locale.ROOT);
                return lower.contains("translate") || lower.contains("translation");
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean matchesTranslate(CharSequence value) {
        return value != null && TRANSLATE_LABELS.contains(normalize(value.toString()));
    }

    private static boolean matchesAny(CharSequence value, Set<String> labels) {
        return value != null && labels.contains(normalize(value.toString()));
    }

    private static String normalize(String value) {
        return value
            .replace('\u00A0', ' ')
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
    }

    private static AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; depth < 5 && current != null; depth++) {
            if (current.isVisibleToUser() && current.isEnabled() && current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return node.isVisibleToUser() && node.isEnabled() ? node : null;
    }

    private static boolean performClick(AccessibilityNodeInfo node) {
        try {
            AccessibilityNodeInfo current = node;
            for (int depth = 0; depth < 5 && current != null; depth++) {
                if (current.isVisibleToUser() && current.isEnabled()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
                current = current.getParent();
            }
        } catch (Exception e) {
            Logger.printException(() -> "Accessibility click failed", e);
        }
        return false;
    }

    private static String nodeKey(AccessibilityNodeInfo node) {
        android.graphics.Rect rect = new android.graphics.Rect();
        node.getBoundsInScreen(rect);
        return String.valueOf(node.getViewIdResourceName()) + "|" + rect.toShortString()
            + "|" + String.valueOf(node.getText()) + "|" + String.valueOf(node.getContentDescription());
    }
}
