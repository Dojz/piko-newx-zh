package app.morphe.extension.instagram.patches.translate;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

public final class InstagramUiEnhancements {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Map<View, Boolean> INSTALLED_ROOTS =
        Collections.synchronizedMap(new WeakHashMap<View, Boolean>());
    private static final Map<View, CameraState> COMMENT_BUTTONS =
        Collections.synchronizedMap(new WeakHashMap<View, CameraState>());
    private static final Map<View, ImageView> DOWNLOAD_BUTTONS =
        Collections.synchronizedMap(new WeakHashMap<View, ImageView>());

    private InstagramUiEnhancements() {}

    public static void install(View anchor) {
        if (anchor == null) return;
        final View root = anchor.getRootView();
        if (root == null) return;

        synchronized (INSTALLED_ROOTS) {
            if (INSTALLED_ROOTS.containsKey(root)) return;
            INSTALLED_ROOTS.put(root, Boolean.TRUE);
        }

        root.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    syncCommentButton(root);
                    syncQuickDownloadButtons(root);
                }
            }
        );
        syncCommentButton(root);
        syncQuickDownloadButtons(root);
    }

    private static void syncCommentButton(View root) {
        if (!UiFeaturePrefs.commentTranslateMenu()) {
            restoreCommentButtons();
            return;
        }

        Set<View> candidates = Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
        collectViewsById(root, id("comment_composer_left_image_view"), candidates);
        collectViewsById(root, id("camera_reply_shortcut_button"), candidates);

        for (View view : candidates) {
            if (!(view instanceof ImageView) || COMMENT_BUTTONS.containsKey(view)) continue;
            ImageView imageView = (ImageView) view;
            CameraState state = new CameraState(
                cloneDrawable(imageView.getDrawable()),
                imageView.getContentDescription(),
                readOnClickListener(view)
            );
            COMMENT_BUTTONS.put(view, state);

            UI.setThemedIcon(imageView, "instagram_add_outline_24");
            imageView.setContentDescription(str("piko_zh_comment_plus"));
            imageView.setOnClickListener(v -> showCommentMenu(imageView, state));
        }
    }

    private static void restoreCommentButtons() {
        List<Map.Entry<View, CameraState>> entries;
        synchronized (COMMENT_BUTTONS) {
            entries = new ArrayList<>(COMMENT_BUTTONS.entrySet());
            COMMENT_BUTTONS.clear();
        }
        for (Map.Entry<View, CameraState> entry : entries) {
            View view = entry.getKey();
            CameraState state = entry.getValue();
            if (view == null) continue;
            if (view instanceof ImageView && state.drawable != null) {
                ((ImageView) view).setImageDrawable(state.drawable);
            }
            view.setContentDescription(state.contentDescription);
            view.setOnClickListener(state.originalClick);
        }
    }

    private static void showCommentMenu(ImageView source, CameraState state) {
        try {
            InstagramDialogBox dialog = new InstagramDialogBox(source.getContext());
            ArrayList<String> options = new ArrayList<>();
            options.add(str("piko_zh_translate_visible"));
            if (state.originalClick != null) options.add(str("piko_zh_camera"));

            dialog.addDialogMenuItems(
                options.toArray(new CharSequence[0]),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which < 0 || which >= options.size()) return;
                        String selected = options.get(which);
                        if (selected.equals(str("piko_zh_translate_visible"))) {
                            ScreenTranslator.translateVisibleScreen();
                        } else if (selected.equals(str("piko_zh_camera")) && state.originalClick != null) {
                            state.originalClick.onClick(source);
                        }
                    }
                }
            );
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            Dialog dlg = dialog.getDialog();
            dlg.show();
        } catch (Exception e) {
            Logger.printException(() -> "Comment translate menu failed", e);
        }
    }

    private static void syncQuickDownloadButtons(View root) {
        if (!UiFeaturePrefs.showQuickDownloadButton() || !Pref.enableDownload()) {
            removeQuickDownloadButtons();
            return;
        }

        Set<View> shareButtons = Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
        collectViewsById(root, id("row_feed_button_share"), shareButtons);
        collectViewsById(root, id("clips_ufi_share_button"), shareButtons);

        for (View share : shareButtons) {
            if (share == null || DOWNLOAD_BUTTONS.containsKey(share)) continue;
            ViewParent parentObject = share.getParent();
            if (!(parentObject instanceof ViewGroup)) continue;
            ViewGroup parent = (ViewGroup) parentObject;

            ImageView button = new ImageView(share.getContext());
            UI.setThemedIcon(button, UI.DRAWABLE_DOWNLOAD_ICON);
            button.setContentDescription(str("piko_category_download_media"));
            button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int size = quickButtonSize(parent, share);
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(size, size);
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            button.setLayoutParams(params);
            button.setPadding(dp(6), dp(6), dp(6), dp(6));
            button.setClickable(true);
            button.setFocusable(true);
            button.setOnClickListener(v -> triggerQuickDownload(share));

            int index = parent.indexOfChild(share);
            parent.addView(button, Math.min(index + 1, parent.getChildCount()));
            DOWNLOAD_BUTTONS.put(share, button);
        }
    }

    private static void removeQuickDownloadButtons() {
        List<ImageView> buttons;
        synchronized (DOWNLOAD_BUTTONS) {
            buttons = new ArrayList<>(DOWNLOAD_BUTTONS.values());
            DOWNLOAD_BUTTONS.clear();
        }
        for (ImageView button : buttons) {
            if (button == null) continue;
            ViewParent parent = button.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(button);
        }
    }

    private static int quickButtonSize(ViewGroup parent, View share) {
        int compact = dp(36);
        int normal = dp(40);
        if (parent instanceof LinearLayout
            && ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL
            && parent.getWidth() > 0
            && parent.getChildCount() >= 6) {
            return compact;
        }
        if (share.getHeight() > 0) {
            return Math.max(compact, Math.min(normal, share.getHeight()));
        }
        return normal;
    }

    private static void triggerQuickDownload(View share) {
        View overflow = findOverflowNear(share);
        if (overflow == null) return;
        overflow.performClick();

        MAIN.postDelayed(() -> {
            boolean clicked = ScreenTranslator.clickVisibleText(
                str("piko_category_download_media"),
                str("piko_download_options")
            );
            if (!clicked) return;
            MAIN.postDelayed(
                () -> ScreenTranslator.clickVisibleText(str("piko_download_current_media")),
                120L
            );
        }, 120L);
    }

    private static View findOverflowNear(View share) {
        int[] overflowIds = new int[] {
            id("row_feed_button_options"),
            id("clips_ufi_more_button"),
            id("row_reel_viewer_overflow_button")
        };
        View current = share;
        for (int depth = 0; depth < 10 && current != null; depth++) {
            if (current instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) current;
                for (int overflowId : overflowIds) {
                    if (overflowId == 0) continue;
                    View result = group.findViewById(overflowId);
                    if (result != null && result.isShown()) return result;
                }
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static void collectViewsById(View view, int targetId, Set<View> out) {
        if (view == null || targetId == 0) return;
        if (view.getId() == targetId && view.isShown()) out.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectViewsById(group.getChildAt(i), targetId, out);
            }
        }
    }

    private static int id(String name) {
        try {
            return ResourceUtils.getIdentifier(ResourceType.ID, name);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int dp(int value) {
        return Math.round(value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    private static Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            return state == null ? drawable : state.newDrawable().mutate();
        } catch (Exception ignored) {
            return drawable;
        }
    }

    private static View.OnClickListener readOnClickListener(View view) {
        try {
            Field listenerInfoField = View.class.getDeclaredField("mListenerInfo");
            listenerInfoField.setAccessible(true);
            Object listenerInfo = listenerInfoField.get(view);
            if (listenerInfo == null) return null;
            Field onClickField = listenerInfo.getClass().getDeclaredField("mOnClickListener");
            onClickField.setAccessible(true);
            Object listener = onClickField.get(listenerInfo);
            return listener instanceof View.OnClickListener ? (View.OnClickListener) listener : null;
        } catch (Exception e) {
            Logger.printException(() -> "Could not read original comment camera click listener", e);
            return null;
        }
    }

    private static final class CameraState {
        final Drawable drawable;
        final CharSequence contentDescription;
        final View.OnClickListener originalClick;

        CameraState(
            Drawable drawable,
            CharSequence contentDescription,
            View.OnClickListener originalClick
        ) {
            this.drawable = drawable;
            this.contentDescription = contentDescription;
            this.originalClick = originalClick;
        }
    }
}
