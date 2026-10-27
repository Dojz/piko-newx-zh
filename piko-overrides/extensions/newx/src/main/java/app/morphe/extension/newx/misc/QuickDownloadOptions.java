package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.Utils;

import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public final class QuickDownloadOptions {
    private static final QuickDownloadState STATE = new QuickDownloadState();
    private static final ThreadLocal<QuickDownloadState.Values> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TEMPORARY = new ThreadLocal<>();
    private static java.lang.ref.WeakReference<Editor> active = new java.lang.ref.WeakReference<>(null);

    private QuickDownloadOptions() {}

    private static QuickDownloadState.Values saved() {
        return new QuickDownloadState.Values(DownloadSettings.imagesTreeUri(), DownloadSettings.imagesDisplayPath(),
                DownloadSettings.videosTreeUri(), DownloadSettings.videosDisplayPath(), DownloadSettings.filenameTemplate());
    }

    static String override(String id) {
        QuickDownloadState.Values value = CURRENT.get();
        if (value == null) return null;
        switch (id) {
            case DownloadSettings.IMAGES_TREE_URI: return value.imagesUri;
            case DownloadSettings.IMAGES_DISPLAY_PATH: return value.imagesPath;
            case DownloadSettings.VIDEOS_TREE_URI: return value.videosUri;
            case DownloadSettings.VIDEOS_DISPLAY_PATH: return value.videosPath;
            case DownloadSettings.FILENAME_TEMPLATE: return value.template;
            default: return null;
        }
    }

    /** Bind once per selected action, including every item of a batch or a merged output. */
    static void begin(DownloadFileName.PostContext post) {
        synchronized (post) {
            if (post.quickDownloadOptions == null) {
                QuickDownloadState.Values persistent = saved();
                QuickDownloadState.Values chosen = STATE.claim(persistent);
                post.quickDownloadOptions = chosen;
                post.quickDownloadTemporary = !chosen.equals(persistent);
            }
            CURRENT.set((QuickDownloadState.Values) post.quickDownloadOptions);
            TEMPORARY.set(post.quickDownloadTemporary);
        }
    }

    static void end() { CURRENT.remove(); TEMPORARY.remove(); }
    static boolean isTemporary() { return Boolean.TRUE.equals(TEMPORARY.get()); }

    static boolean nativeSavedFolder(String kind) {
        return saved().nativeFolder("VIDEOS".equals(kind));
    }

    static boolean nativeFolder(String kind) {
        QuickDownloadState.Values current = CURRENT.get();
        if (current == null) current = STATE.peek(saved());
        return current.nativeFolder("VIDEOS".equals(kind));
    }

    static DownloadDestination.DestinationState destinationState(Context context, DownloadDestination.MediaKind kind) {
        QuickDownloadState.Values value = STATE.peek(saved());
        boolean video = kind == DownloadDestination.MediaKind.VIDEOS;
        if (value.nativeFolder(video)) return DownloadDestination.DestinationState.PERSISTED;
        String raw = video ? value.videosUri : value.imagesUri;
        if (raw.isEmpty()) return DownloadDestination.DestinationState.UNSET;
        try {
            Uri tree = Uri.parse(raw);
            if (DownloadDestination.hasPersistedWritePermission(context.getContentResolver(), tree))
                return DownloadDestination.DestinationState.PERSISTED;
            return DownloadDestination.hasLiveTreeAccess(context.getContentResolver(), tree)
                    ? DownloadDestination.DestinationState.LIVE : DownloadDestination.DestinationState.UNUSABLE;
        } catch (RuntimeException ignored) {
            return DownloadDestination.DestinationState.UNUSABLE;
        }
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Editor previous = active.get();
        if (previous != null && previous.dialog.getDialog().isShowing()) return;
        Editor editor = new Editor(activity);
        active = new java.lang.ref.WeakReference<>(editor);
        editor.dialog.show();
    }

    private static ButtonView button(Activity activity, String title, Runnable click) {
        ButtonView button = new ButtonView(activity, ButtonView.ButtonStyle.TEXT, title);
        button.setOnClickListener(ignored -> click.run());
        return button;
    }

    private static void persist(QuickDownloadState.Values value) {
        QuickDownloadState.Values before = saved();
        try {
            write(value);
            STATE.clear();
        } catch (RuntimeException failure) {
            try { write(before); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    private static void write(QuickDownloadState.Values value) {
        DownloadSettings.setString(DownloadSettings.IMAGES_TREE_URI, value.imagesUri);
        DownloadSettings.setString(DownloadSettings.IMAGES_DISPLAY_PATH, value.imagesPath);
        DownloadSettings.setString(DownloadSettings.VIDEOS_TREE_URI, value.videosUri);
        DownloadSettings.setString(DownloadSettings.VIDEOS_DISPLAY_PATH, value.videosPath);
        DownloadSettings.setString(DownloadSettings.FILENAME_TEMPLATE, value.template);
    }

    private static final class Editor {
        final Activity activity;
        final QuickDownloadState.Values original;
        QuickDownloadState.Values draft;
        final DialogView dialog;
        final TextView images, videos;
        final EditText filename;
        Editor(Activity activity) {
            this.activity = activity;
            original = draft = STATE.peek(saved());
            LinearLayout form = new LinearLayout(activity);
            form.setOrientation(LinearLayout.VERTICAL);
            int padding = Theme.dpToPx(activity, 24f);
            form.setPadding(padding, 0, padding, 0);
            images = row(form, "图片", false);
            videos = row(form, "视频", true);
            TextView label = new TextView(activity);
            label.setText("文件名模板");
            label.setTextColor(Theme.primaryText(activity));
            form.addView(label);
            filename = new EditText(activity);
            filename.setInputType(InputType.TYPE_CLASS_TEXT);
            filename.setTextColor(Theme.primaryText(activity));
            filename.setText(draft.template);
            form.addView(filename, new LinearLayout.LayoutParams(-1, -2));
            // Use the same tokens as the existing full-page editor.
            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout tokens = new LinearLayout(activity);
            for (String token : DownloadFileName.editorTokens()) {
                tokens.addView(button(activity, "{" + token + "}", () -> {
                    int start = Math.max(0, filename.getSelectionStart());
                    int end = Math.max(start, filename.getSelectionEnd());
                    filename.getText().replace(start, end, "{" + token + "}");
                }));
            }
            scroll.addView(tokens);
            form.addView(scroll);
            dialog = new DialogView(activity).setTitle("下载选项").setScrollableBodyView(form);
            dialog.getDialog().setCanceledOnTouchOutside(false);
            dialog.getDialog().setOnKeyListener((ignored, key, event) -> {
                if (key != KeyEvent.KEYCODE_BACK) return false;
                if (event.getAction() == KeyEvent.ACTION_UP) cancel();
                return true;
            });
            dialog.addButton(button(activity, "取消", this::cancel))
                    .addButton(button(activity, "恢复默认", () -> apply(QuickDownloadState.Values.defaults())))
                    .addButton(button(activity, "确认", () -> apply(value())));
            refresh();
        }
        TextView row(LinearLayout form, String title, boolean video) {
            TextView row = new TextView(activity);
            row.setTextSize(16);
            row.setTextColor(Theme.primaryText(activity));
            int pad = Theme.dpToPx(activity, 14f);
            row.setPadding(0, pad, 0, pad);
            row.setOnClickListener(ignored -> pick(video));
            row.setTag(title);
            form.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return row;
        }
        QuickDownloadState.Values value() { return draft.template(filename.getText().toString()); }
        void refresh() {
            images.setText("图片\n" + path(draft.imagesUri, draft.imagesPath));
            videos.setText("视频\n" + path(draft.videosUri, draft.videosPath));
        }
        String path(String uri, String label) {
            return !label.isEmpty() ? label : uri.isEmpty() ? "未设置" : uri;
        }
        void pick(boolean video) {
            draft = value();
            Picker picker = new Picker();
            picker.callback = tree -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (tree != null) {
                    draft = draft.folder(video, tree.toString(), DownloadDestination.displayPathFor(tree));
                    refresh();
                }
                dialog.getDialog().show();
            };
            activity.getFragmentManager().beginTransaction().add(picker, "piko-quick-download-picker").commit();
            activity.getFragmentManager().executePendingTransactions();
            dialog.getDialog().hide();
            picker.launch(video ? draft.videosUri : draft.imagesUri);
        }
        void cancel() {
            if (value().equals(original)) { dialog.dismiss(); return; }
            DialogView confirm = new DialogView(activity).setTitle("保存修改将丢失");
            confirm.addButton(button(activity, "继续编辑", confirm::dismiss));
            confirm.addButton(button(activity, "放弃修改", () -> { confirm.dismiss(); dialog.dismiss(); }));
            confirm.show();
        }
        void apply(QuickDownloadState.Values value) {
            String error = DownloadFileName.validationError(value.template);
            if (error != null) { filename.setError(error); return; }
            DialogView choice = new DialogView(activity).setTitle("应用下载选项");
            choice.addButton(button(activity, "仅一次", () -> {
                STATE.once(value);
                choice.dismiss();
                dialog.dismiss();
            }));
            choice.addButton(button(activity, "持续应用", () -> {
                try {
                    persist(value);
                    choice.dismiss();
                    dialog.dismiss();
                } catch (RuntimeException failure) {
                    Utils.showToastShort("无法保存下载选项");
                }
            }));
            choice.show();
        }
    }

    /** A transient native picker host; selection only updates the dialog draft. */
    public static final class Picker extends Fragment {
        Consumer<Uri> callback;
        public Picker() {}
        void launch(String previous) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if (previous.startsWith("content:")) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(previous));
            try { startActivityForResult(intent, 43); }
            catch (RuntimeException failure) { finish(null); }
        }
        @Override public void onActivityResult(int request, int result, Intent data) {
            super.onActivityResult(request, result, data);
            if (request != 43) return;
            Uri tree = result == Activity.RESULT_OK && data != null ? data.getData() : null;
            try {
                if (tree != null) {
                    if (!DownloadDestination.hasLiveTreeAccess(getActivity().getContentResolver(), tree))
                        throw new IllegalArgumentException("Unusable folder");
                    int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    try { getActivity().getContentResolver().takePersistableUriPermission(tree, flags); }
                    catch (SecurityException ignored) { /* The live grant still works in this process. */ }
                }
            } catch (RuntimeException failure) {
                Utils.showToastShort("目录不可用");
                tree = null;
            }
            finish(tree);
        }
        void finish(Uri tree) {
            Consumer<Uri> listener = callback;
            callback = null;
            if (listener != null) listener.accept(tree);
            if (isAdded()) getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
