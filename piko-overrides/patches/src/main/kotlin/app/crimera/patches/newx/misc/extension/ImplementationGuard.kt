package app.crimera.patches.newx.misc.extension

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

// Compatibility is normally enforced by the manager. Expert selection can bypass it,
// but extension classes from different implementations must never be merged into one APK.
internal val newXImplementationGuardPatch = bytecodePatch(default = false) {
    compatibleWith(COMPATIBILITY_NEW_X)
    execute {
        validateNewXImplementation(packageMetadata.packageName, packageMetadata.versionName)
    }
}

internal fun validateNewXImplementation(
    packageName: String?,
    versionName: String?,
    compatibility: Compatibility = COMPATIBILITY_NEW_X,
) {
    if (packageName != compatibility.packageName ||
        compatibility.targets.none { it.version == versionName }
    ) {
        throw PatchException(
            "NewX extension implementation does not support $packageName $versionName. " +
                "Select only patches compatible with this APK; do not mix same-name " +
                "patches for other versions or sources. Supported versions: " +
                compatibility.targets.joinToString { it.version },
        )
    }
}
