# NewX settings extension ABI mix

A reported startup failure raised `IllegalStateException: Duplicate setting ID:
newx.advanced.debug_tools.logging` from shared `SettingsRegistry.load()`.

The resulting APK contained both the legacy NewX settings registry and the
shared registry/host. Its application initializer invoked both loaders. The
legacy registry called `BuiltInSettings.register()`, but the class present in
the APK was the shared implementation; the subsequent shared host contribution
registered the same diagnostics setting again.

The unified MPP's individual dependency graphs resolve the appropriate extension
streams. Mixed selection or applying another implementation to an already
modified input can still merge conflicting classes when manager compatibility
checks are bypassed. The available APK does not distinguish those two inputs.

The extension dependency now enforces its centralized compatibility targets and
rejects an application initializer with previously injected NewX settings calls,
independently of manager selection. It fails instead of
producing a runnable APK with an incompatible extension implementation. This is
validation of the existing support allowlist, not version-based hook routing.
Both implementation builds include a regression for selecting the other
implementation's production target.

`tests/check_settings_apk.py` detects mixed registry ABIs and multiple startup
loaders in a final APK. It rejects the observed mixed layout. Correctly selected
patches must retain a single settings implementation and startup loader.

Both pinned implementation builds and their test suites passed. The unified
bundle retains version 3.54.2 and 15 supported app targets. Internal integration
checks with Morphe Desktop 1.18.1 patched an unmodified 12.28.0-prod.01 input
using the unified bundle, with the default patches and with Browse tweet object
enabled. Both resulting APKs passed the single implementation/startup check.
Forcing the current implementation on that target and applying the legacy
implementation to the observed mixed APK each failed at the guard and produced
no output APK.

The fix does not repair an already patched APK. Use an unmodified input, one
source, and the patches declared compatible with its version. Device startup
and feature behavior have not been tested and require a subsequent runtime test.
