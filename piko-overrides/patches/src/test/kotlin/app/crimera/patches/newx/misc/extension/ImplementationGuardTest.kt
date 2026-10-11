package app.crimera.patches.newx.misc.extension

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ImplementationGuardTest {
    @Test
    fun `the extension rejects the other family even when manager compatibility is bypassed`() {
        val current = Compatibility(
            packageName = "com.twitter.android",
            targets = listOf(AppTarget(version = "12.30.0-prod.01")),
        )
        val legacy = Compatibility(
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
}
