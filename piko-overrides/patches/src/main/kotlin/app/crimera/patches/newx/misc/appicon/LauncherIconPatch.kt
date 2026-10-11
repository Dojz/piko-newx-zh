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
import org.w3c.dom.Element

private const val ALIAS = "app.morphe.extension.newx.launcher."
private const val BLUE_ACTIVITY = "app.morphe.extension.newx.misc.BlueBirdActivity"
private var launcherDescriptor: String? = null

internal val newXLauncherIconResources = resourcePatch {
    execute {
        copyResources("twitter/bringbacktwitter", ResourceGroup("mipmap-xxhdpi", "ic_launcher_twitter.webp"))
        val icons = get("res").resolve("mipmap-xxhdpi")
        icons.resolve("ic_launcher_twitter.webp").copyTo(icons.resolve("piko_launcher_blue_bird.webp"), overwrite = true)
        copyResources("twitter/bringbacktwitter", ResourceGroup("drawable", "splash_screen_icon.xml"))
        val drawable = get("res").resolve("drawable")
        drawable.resolve("splash_screen_icon.xml").copyTo(drawable.resolve("piko_blue_bird_splash.xml"), overwrite = true)
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as Element
            val activities = doc.getElementsByTagName("activity")
            val launchers = (0 until activities.length).map { activities.item(it) as Element }.filter { activity ->
                val categories = activity.getElementsByTagName("category")
                (0 until categories.length).any {
                    (categories.item(it) as Element).getAttribute("android:name") == "android.intent.category.LAUNCHER"
                }
            }
            if (launchers.size != 1) throw PatchException("NewX launcher activity: expected one, got ${launchers.size}")
            val launcher = launchers.single()
            val launcherName = launcher.getAttribute("android:name")
            val packageName = doc.documentElement.getAttribute("package")
            val fullName = if (launcherName.startsWith(".")) packageName + launcherName else launcherName
            launcherDescriptor = "L" + fullName.replace('.', '/') + ";"
            val theme = launcher.getAttribute("android:theme")
            if (!theme.startsWith("@style/")) throw PatchException("NewX launcher splash theme missing")
            document("res/values/styles.xml").use { styles ->
                val style = styles.createElement("style")
                style.setAttribute("name", "PikoBlueBirdSplash")
                style.setAttribute("parent", theme)
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
            val blueActivity = launcher.cloneNode(true) as Element
            blueActivity.setAttribute("android:name", BLUE_ACTIVITY)
            blueActivity.setAttribute("android:theme", "@style/PikoBlueBirdSplash")
            blueActivity.setAttribute("android:icon", "@mipmap/piko_launcher_blue_bird")
            blueActivity.setAttribute("android:roundIcon", "@mipmap/piko_launcher_blue_bird")
            val blueFilters = blueActivity.getElementsByTagName("intent-filter")
            (0 until blueFilters.length).map { blueFilters.item(it) }.forEach { blueActivity.removeChild(it) }
            application.appendChild(blueActivity)
            val filters = launcher.getElementsByTagName("intent-filter")
            val launcherFilters = (0 until filters.length).map { filters.item(it) as Element }.filter { filter ->
                val categories = filter.getElementsByTagName("category")
                (0 until categories.length).any {
                    (categories.item(it) as Element).getAttribute("android:name") == "android.intent.category.LAUNCHER"
                }
            }
            // Keep the real activity enabled for deep links and in-app navigation. Switch aliases only.
            for ((suffix, enabled) in listOf("Default" to true, "BlueBird" to false)) {
                val alias = doc.createElement("activity-alias")
                alias.setAttribute("android:name", ALIAS + suffix)
                alias.setAttribute("android:targetActivity", if (suffix == "BlueBird") BLUE_ACTIVITY else launcherName)
                alias.setAttribute("android:enabled", enabled.toString())
                alias.setAttribute("android:exported", "true")
                if (suffix == "BlueBird") {
                    alias.setAttribute("android:icon", "@mipmap/piko_launcher_blue_bird")
                    alias.setAttribute("android:roundIcon", "@mipmap/piko_launcher_blue_bird")
                } else {
                    for (attribute in listOf("android:icon", "android:roundIcon")) {
                        val value = launcher.getAttribute(attribute).ifEmpty { application.getAttribute(attribute) }
                        if (value.isNotEmpty()) alias.setAttribute(attribute, value)
                    }
                }
                launcherFilters.forEach { alias.appendChild(it.cloneNode(true)) }
                application.appendChild(alias)
            }
            launcherFilters.forEach { launcher.removeChild(it) }
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
