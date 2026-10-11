package app.crimera.patches.newx.misc.extension

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ImplementationGuardTest {
    @Test
    fun `the extension rejects the other family even when manager compatibility is bypassed`() {
        val current = Compatibility(
            name = "NewX current test",
            packageName = "com.twitter.android",
            targets = listOf(AppTarget(version = "12.30.0-prod.01")),
        )
        val legacy = Compatibility(
            name = "NewX legacy test",
            packageName = "com.twitter.android",
            targets = listOf(AppTarget(version = "12.28.0-prod.01")),
        )
        assertFailsWith<PatchException> {
            validateNewXImplementation("com.twitter.android", "12.28.0-prod.01", current)
        }
        assertFailsWith<PatchException> {
            validateNewXImplementation("com.twitter.android", "12.30.0-prod.01", legacy)
        }
        for (target in COMPATIBILITY_NEW_X.targets) {
            validateNewXImplementation("com.twitter.android", target.version)
        }
        assertFailsWith<PatchException> {
            validateNewXImplementation("different.package", COMPATIBILITY_NEW_X.targets.first().version)
        }
    }

    @Test
    fun `a previously injected settings startup cannot be patched again`() {
        for (owner in listOf(
            "Lapp/morphe/extension/newx/settings/SettingsRegistry;",
            "Lapp/morphe/extension/crimera/settings/SettingsRegistry;",
        )) {
            assertFailsWith<PatchException> {
                validateNewXStartup(listOf(ImmutableMethodReference(owner, "load", emptyList<String>(), "V")))
            }
        }
        assertFailsWith<PatchException> {
            validateNewXStartup(listOf(ImmutableMethodReference(
                "Lapp/morphe/extension/newx/settings/NewXSettingsHost;", "install", emptyList<String>(), "V")))
        }
        validateNewXStartup(listOf(ImmutableMethodReference(
            "Landroid/app/Application;", "onCreate", emptyList<String>(), "V")))
    }
}
