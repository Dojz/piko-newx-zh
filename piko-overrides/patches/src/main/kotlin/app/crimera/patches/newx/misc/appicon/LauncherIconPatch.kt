package app.crimera.patches.newx.misc.appicon

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.settings.action
import app.crimera.patches.settings.group
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

internal const val BLUE_ACTIVITY = "app.morphe.extension.newx.misc.BlueBirdActivity"
private var launcherDescriptor: String? = null

internal val newXLauncherIconResources = resourcePatch {
    execute {
        copyResources("twitter/bringbacktwitter", ResourceGroup("mipmap-xxhdpi", "ic_launcher_twitter.webp"))
        val icons = get("res").resolve("mipmap-xxhdpi")
        icons.resolve("ic_launcher_twitter.webp").copyTo(icons.resolve("piko_launcher_blue_bird.webp"), overwrite = true)
        copyResources("twitter/bringbacktwitter", ResourceGroup("drawable", "splash_screen_icon.xml"))
        val drawable = get("res").resolve("drawable")
        drawable.resolve("splash_screen_icon.xml").copyTo(drawable.resolve("piko_blue_bird_splash.xml"), overwrite = true)
        val manifest = document("AndroidManifest.xml").use { configureLauncherManifest(it) }
        launcherDescriptor = manifest.descriptor
        if (manifest.added) document("res/values/styles.xml").use { styles ->
            val style = styles.createElement("style")
            style.setAttribute("name", "PikoBlueBirdSplash")
            style.setAttribute("parent", manifest.theme)
            for ((name, value) in listOf(
                "windowSplashScreenAnimatedIcon" to "@drawable/piko_blue_bird_splash",
                "windowSplashScreenBackground" to "#ff1da1f2")) {
                val item = styles.createElement("item")
                item.setAttribute("name", name)
                item.textContent = value
                style.appendChild(item)
            }
            styles.documentElement.appendChild(style)
        }
    }
}

@Suppress("unused")
val newXLauncherIconPatch = bytecodePatch(
    name = "NewX: Choose app icon",
    description = "Switch between the classic Twitter blue bird and the original app icon in Appearance",
) {
    compatibleWith(COMPATIBILITY_NEW_X)
    dependsOn(newXLauncherIconResources, newXExtensionPatch)
    execute {
        val descriptor = launcherDescriptor ?: throw PatchException("NewX launcher resources were not resolved")
        val launcher = mutableClassDefBy(descriptor)
        val constructors = launcher.methods.filter { it.name == "<init>" && it.parameterTypes.isEmpty() }
        if (constructors.size != 1 || !AccessFlags.PUBLIC.isSet(constructors.single().accessFlags)) {
            throw PatchException("NewX launcher requires one public no-argument constructor")
        }
        // A second launcher entry inherits the exact native activity, differing only in its splash theme.
        launcher.setAccessFlags(launcher.accessFlags and AccessFlags.FINAL.value.inv())
        val blue = mutableClassDefBy("L" + BLUE_ACTIVITY.replace('.', '/') + ";")
        blue.setSuperClass(descriptor)
        blue.methods.removeAll { it.name == "<init>" }
        val constructor = MutableMethod(ImmutableMethod(blue.type, "<init>", emptyList(), "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value, emptySet(), emptySet(),
            ImmutableMethodImplementation(1, listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)), emptyList(), emptyList())))
        constructor.insertHook(0, relocateBranchTargets = false) {
            invokeDirect(methodReference("$descriptor-><init>()V"), 0)
        }
        blue.methods.add(constructor)
    }
    newXSettings {
        category(Categories.APPEARANCE) {
            group(id = "newx.appearance.app_icon", strings = settingStrings("piko_newx_app_icon"),
                iconResourceName = "ic_vector_settings_stroke", order = 300) {
                action(id = "newx.appearance.app_icon.choose", strings = settingStrings("piko_newx_app_icon_choose"),
                    order = 100, handlerClassDescriptor = "Lapp/morphe/extension/newx/misc/LauncherIcon\$ChooseAction;")
            }
        }
    }
}
