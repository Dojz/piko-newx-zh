package app.crimera.patches.newx.misc.appicon

import app.crimera.patches.newx.utils.requireExactlyOne
import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ANDROID = "http://schemas.android.com/apk/res/android"
private const val ALIAS = "app.morphe.extension.newx.launcher."

internal data class LauncherManifest(val descriptor: String, val theme: String, val added: Boolean)

private fun Element.android(name: String): String =
    getAttributeNS(ANDROID, name).ifEmpty { getAttribute("android:$name") }

private fun Element.setAndroid(name: String, value: String) {
    removeAttribute("android:$name")
    removeAttributeNS(ANDROID, name)
    setAttributeNS(ANDROID, "android:$name", value)
}

private fun Element.elements(tag: String): List<Element> = (0 until childNodes.length)
    .mapNotNull { childNodes.item(it) as? Element }
    .filter { (it.localName ?: it.tagName) == tag }

private fun Element.launcherFilters(): List<Element> = elements("intent-filter").filter { filter ->
    filter.elements("action").any { it.android("name") == "android.intent.action.MAIN" } &&
        filter.elements("category").any { it.android("name") == "android.intent.category.LAUNCHER" }
}

/** Resolve the real activity through MAIN/LAUNCHER entries, including aliases; never guess its name. */
internal fun configureLauncherManifest(document: Document): LauncherManifest {
    val application = requireExactlyOne("NewX manifest application", document.documentElement.elements("application"))
    val packageName = document.documentElement.getAttribute("package")
    fun fullName(name: String): String = when {
        name.isEmpty() -> throw PatchException("NewX launcher component name is missing")
        name.startsWith(".") -> packageName + name
        '.' !in name -> "$packageName.$name"
        else -> name
    }
    val activities = application.elements("activity")
    val aliases = application.elements("activity-alias")
    fun activity(name: String): Element = requireExactlyOne("NewX launcher target Activity",
        activities.filter { fullName(it.android("name")) == fullName(name) }) { it.android("name") }
    fun result(native: Element, added: Boolean): LauncherManifest {
        val theme = native.android("theme").ifEmpty { application.android("theme") }
        if (!theme.startsWith("@style/")) throw PatchException("NewX launcher splash theme missing")
        if (native.android("enabled") == "false") throw PatchException("NewX launcher target Activity is disabled")
        return LauncherManifest("L${fullName(native.android("name")).replace('.', '/')};", theme, added)
    }

    // A shared resource step can encounter a launcher already converted in this patch session.
    // Validate the complete shape before reusing it; partial or conflicting entries still fail closed.
    val owned = aliases.filter { fullName(it.android("name")).startsWith(ALIAS) }
    val blueActivities = activities.filter { fullName(it.android("name")) == BLUE_ACTIVITY }
    if (owned.isNotEmpty() || blueActivities.isNotEmpty()) {
        val original = requireExactlyOne("NewX original launcher alias",
            owned.filter { fullName(it.android("name")) == ALIAS + "Default" })
        val bird = requireExactlyOne("NewX blue bird launcher alias",
            owned.filter { fullName(it.android("name")) == ALIAS + "BlueBird" })
        val blue = requireExactlyOne("NewX blue bird Activity", blueActivities)
        val native = activity(original.android("targetActivity"))
        if (native === blue || fullName(bird.android("targetActivity")) != BLUE_ACTIVITY ||
            blue.android("theme") != "@style/PikoBlueBirdSplash" || blue.launcherFilters().isNotEmpty() ||
            original.launcherFilters().isEmpty() || bird.launcherFilters().isEmpty() ||
            native.launcherFilters().isNotEmpty()) {
            throw PatchException("NewX launcher aliases have an incomplete or conflicting layout")
        }
        return result(native, false)
    }

    val entries = (activities + aliases).filter { it.launcherFilters().isNotEmpty() }
    val targetName = requireExactlyOne("NewX MAIN/LAUNCHER target", entries.map { entry ->
        fullName(if (entry.tagName == "activity-alias") entry.android("targetActivity") else entry.android("name"))
    }.distinct())
    val native = activity(targetName)
    val resolved = result(native, true)
    val enabledEntries = entries.filter { it.android("enabled") != "false" }
    val source = requireExactlyOne("NewX enabled launcher entry", enabledEntries) { it.android("name") }
    val filters = source.launcherFilters()

    // Android attributes may use another prefix in a namespace-aware DOM.
    document.documentElement.setAttribute("xmlns:android", ANDROID)
    val blue = native.cloneNode(true) as Element
    blue.setAndroid("name", BLUE_ACTIVITY)
    blue.setAndroid("theme", "@style/PikoBlueBirdSplash")
    blue.setAndroid("enabled", "true")
    blue.setAndroid("icon", "@mipmap/piko_launcher_blue_bird")
    blue.setAndroid("roundIcon", "@mipmap/piko_launcher_blue_bird")
    blue.elements("intent-filter").forEach { blue.removeChild(it) }
    application.appendChild(blue)

    for ((suffix, enabled) in listOf("Default" to true, "BlueBird" to false)) {
        val alias = document.createElement("activity-alias")
        alias.setAndroid("name", ALIAS + suffix)
        alias.setAndroid("targetActivity", if (suffix == "BlueBird") BLUE_ACTIVITY else targetName)
        alias.setAndroid("enabled", enabled.toString())
        alias.setAndroid("exported", "true")
        for (attribute in listOf("icon", "roundIcon", "label")) {
            val value = if (suffix == "BlueBird" && attribute != "label") "@mipmap/piko_launcher_blue_bird"
                else source.android(attribute).ifEmpty { application.android(attribute) }
            if (value.isNotEmpty()) alias.setAndroid("$attribute", value)
        }
        filters.forEach { alias.appendChild(it.cloneNode(true)) }
        // Preserve launcher metadata such as shortcuts when its original entry was an alias.
        if (source.tagName == "activity-alias") source.elements("meta-data").forEach {
            alias.appendChild(it.cloneNode(true))
        }
        application.appendChild(alias)
    }
    native.launcherFilters().forEach { native.removeChild(it) }
    aliases.filter { it in entries }.forEach { it.setAndroid("enabled", "false") }
    return resolved
}
