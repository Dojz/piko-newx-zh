# Stable selections in a unified NewX source

Manager 1.34.0 saves patch selections and options by a per-app name key. Its
`PatchInfo.uniqueNames` assigns numeric suffixes to duplicate declarations,
ordering by compatible package names. Two NewX implementations have the same
package declaration; that tie follows incoming load order. The UI metadata and
patcher runtime load the bundle separately. A numbered key can consequently
resolve to the other implementation if their orders differ.

Primary implementation references:

- [PatchInfo.uniqueNames](https://github.com/MorpheApp/morphe-manager/blob/v1.34.0/app/src/main/java/app/morphe/manager/patcher/patch/PatchInfo.kt)
- [PatchBundle.Loader.patches](https://github.com/MorpheApp/morphe-manager/blob/v1.34.0/app/src/main/java/app/morphe/manager/patcher/patch/PatchBundle.kt)
- [SelectedPatch](https://github.com/MorpheApp/morphe-manager/blob/v1.34.0/app/src/main/java/app/morphe/manager/data/room/selection/SelectedPatch.kt)

The observed 12.28.0-prod.01 failure selected the current icon implementation,
which correctly rejected that target. The extension guard remains necessary,
but prevention alone does not resolve the ambiguous selection keys.

Assembly now suffixes retained NewX patch declarations with their original
compatibility cohort. The label depends on declared targets, independent of
bundle content hashes, file/class load order, or which newer targets are
excluded while assembling. The current implementation keeps upstream names.
Every final unified bundle must have unique patch names across the entire NewX
package list, including implementations whose version sets do not overlap.

`VerifyManagerSelection` reproduces the manager's key assignment and resolves
selections after reversing independently shuffled UI/runtime orders. The
previously published bundle reproduces an implementation mismatch; the renamed
assembly passes for all 15 targets across 12 orderings. Assembly also checks
that changing a retained bundle's relocation namespace preserves its keys.

These checks run inside `assemble_compatible_bundle`, used by the regular
automatic release workflow. Future releases keep one unified default download
and preserve retained supported targets. Runtime compatibility/startup guards,
filename behavior, extensions, and the upstream version remain unchanged.

The final unified MPP has 95 distinct NewX declaration names, all present in
its Android DEX entries. A clean 12.28.0-prod.01 input patched successfully
with 42 compatible retained patches, including Choose app icon and Browse
tweet object. The resulting APK passed the single settings implementation and
startup loader check. These are internal build/structure checks, not a device
startup test.

Existing selections made with ambiguous plain/numeric keys may need a one-time
reset after this migration. The compatible retained declarations then use the
stable cohort names on subsequent rebuilds. Device startup is not covered by
the JVM/Dex structure checks and must be tested on a device.
