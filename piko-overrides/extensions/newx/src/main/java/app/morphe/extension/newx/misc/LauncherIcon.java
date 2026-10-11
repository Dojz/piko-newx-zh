package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.crimera.settings.SettingsActionHandler;
import app.morphe.extension.crimera.theme.PikoTheme;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.crimera.ui.DialogView;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.StringRef;

/** Switches installed launcher aliases, preserving the real activity and all deep links. */
public final class LauncherIcon {
    private static final String PREFIX = "app.morphe.extension.newx.launcher.";
    private LauncherIcon() {}

    public static final class ChooseAction implements SettingsActionHandler {
        @Override public void run(Activity activity) { showChooser(activity); }
    }

    private static void showChooser(Activity activity) {
        try {
            PackageManager manager = activity.getPackageManager();
            ComponentName bird = new ComponentName(activity, PREFIX + "BlueBird");
            ComponentName original = new ComponentName(activity, PREFIX + "Default");
            // Validate both choices before allowing any changes.
            manager.getActivityInfo(bird, PackageManager.MATCH_DISABLED_COMPONENTS);
            manager.getActivityInfo(original, PackageManager.MATCH_DISABLED_COMPONENTS);
            DialogView dialog = new DialogView(activity).setTitle(str("piko_newx_app_icon_title"))
                    .setSubtitle(str("piko_newx_app_icon_summary"));
            LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setGravity(Gravity.CENTER);
            int padding = PikoTheme.dpToPx(activity, 16f);
            body.setPadding(padding, padding, padding, padding);
            addChoice(activity, body, dialog, bird, str("piko_newx_app_icon_blue_bird"));
            addChoice(activity, body, dialog, original, str("piko_newx_app_icon_original"));
            dialog.setBodyView(body);
            ButtonView cancel = new ButtonView(activity, ButtonView.ButtonStyle.TEXT, str("piko_newx_settings_cancel"));
            cancel.setOnClickListener(ignored -> dialog.dismiss());
            dialog.addButton(cancel).show();
        } catch (Exception exception) { failed(exception); }
    }

    private static void addChoice(Activity activity, LinearLayout body, DialogView chooser,
                                  ComponentName component, String label) throws PackageManager.NameNotFoundException {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        PackageManager manager = activity.getPackageManager();
        Drawable icon = manager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).loadIcon(manager);
        ImageView preview = new ImageView(activity);
        preview.setImageDrawable(icon);
        preview.setContentDescription(label);
        int size = PikoTheme.dpToPx(activity, 72f);
        cell.addView(preview, new LinearLayout.LayoutParams(size, size));
        ButtonView choose = new ButtonView(activity, ButtonView.ButtonStyle.TEXT, label);
        choose.setOnClickListener(ignored -> {
            chooser.dismiss();
            confirm(activity, component, label, icon);
        });
        preview.setOnClickListener(ignored -> choose.performClick());
        cell.addView(choose);
        body.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private static void confirm(Activity activity, ComponentName component, String label, Drawable icon) {
        DialogView dialog = new DialogView(activity).setTitle(label)
                .setSubtitle(str("piko_newx_app_icon_confirm_summary"));
        ImageView preview = new ImageView(activity);
        preview.setImageDrawable(icon);
        preview.setContentDescription(label);
        int padding = PikoTheme.dpToPx(activity, 24f);
        preview.setPadding(padding, padding, padding, padding);
        preview.setAdjustViewBounds(true);
        preview.setMaxHeight(PikoTheme.dpToPx(activity, 144f));
        dialog.setBodyView(preview);
        ButtonView cancel = new ButtonView(activity, ButtonView.ButtonStyle.TEXT, str("piko_newx_settings_cancel"));
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        ButtonView apply = new ButtonView(activity, ButtonView.ButtonStyle.FILLED, str("piko_newx_app_icon_confirm"));
        apply.setOnClickListener(ignored -> {
            try { switchIcon(activity, component); dialog.dismiss(); }
            catch (Exception exception) { failed(exception); }
        });
        dialog.addButton(cancel).addButton(apply).show();
    }

    private static void switchIcon(Activity activity, ComponentName selected) throws PackageManager.NameNotFoundException {
        PackageManager manager = activity.getPackageManager();
        ActivityInfo target = manager.getActivityInfo(selected, PackageManager.MATCH_DISABLED_COMPONENTS);
        if (target.targetActivity == null || !selected.getClassName().startsWith(PREFIX)) {
            throw new IllegalStateException("Missing launcher alias");
        }
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(activity.getPackageName());
        String originalTarget = manager.getActivityInfo(new ComponentName(activity, PREFIX + "Default"),
                PackageManager.MATCH_DISABLED_COMPONENTS).targetActivity;
        String birdTarget = manager.getActivityInfo(new ComponentName(activity, PREFIX + "BlueBird"),
                PackageManager.MATCH_DISABLED_COMPONENTS).targetActivity;
        List<ComponentName> previous = new ArrayList<>();
        for (ResolveInfo result : manager.queryIntentActivities(launcherIntent, PackageManager.MATCH_DISABLED_COMPONENTS)) {
            ActivityInfo info = result.activityInfo;
            if (activity.getPackageName().equals(info.packageName) && (originalTarget.equals(info.targetActivity) || birdTarget.equals(info.targetActivity))) {
                ComponentName component = new ComponentName(info.packageName, info.name);
                if (!component.equals(selected)) previous.add(component);
            }
        }
        // Enable first: even if the launcher refreshes midway, there is always a launchable icon.
        manager.setComponentEnabledSetting(selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        for (ComponentName component : previous) {
            manager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        app.morphe.extension.newx.misc.NewXInAppNotification.show(str("piko_newx_app_icon_changed"));
    }

    private static String str(String name) { return StringRef.str(name); }
    private static void failed(Exception exception) {
        NewXLogger.printException(() -> "Could not change launcher icon", exception);
        app.morphe.extension.newx.misc.NewXInAppNotification.show(str("piko_newx_app_icon_failed"));
    }
}
