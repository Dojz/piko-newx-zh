package app.crimera.patches.newx.misc.extension

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Compatibility is normally enforced by the manager. Expert selection can bypass it,
// but extension classes from different implementations must never be merged into one APK.
internal val newXImplementationGuardPatch = bytecodePatch(default = false) {
    compatibleWith(COMPATIBILITY_NEW_X)
    execute {
        validateNewXImplementation(packageMetadata.packageName, packageMetadata.versionName)
        val application = classDefByOrNull("Lcom/x/android/XApplication;")
            ?: throw PatchException("NewX application initializer was not found")
        validateNewXStartup(application.methods
            .filter { it.name == "onCreate" && it.parameterTypes.isEmpty() }
            .flatMap { it.implementation?.instructions ?: emptyList() }
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference })
    }
}

internal fun validateNewXStartup(calls: Iterable<MethodReference>) {
    val previous = calls.firstOrNull {
        (it.name == "load" && it.definingClass in setOf(
            "Lapp/morphe/extension/newx/settings/SettingsRegistry;",
            "Lapp/morphe/extension/crimera/settings/SettingsRegistry;",
        )) || (it.name == "install" &&
            it.definingClass == "Lapp/morphe/extension/newx/settings/NewXSettingsHost;")
    }
    if (previous != null) {
        throw PatchException(
            "NewX settings initialization is already present ($previous). " +
                "Use an unpatched original APK and one compatible NewX patch source.",
        )
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
