# Displayed caption and blue bird launcher validation

Build source: `99b1900b3cc7f2962b0e096108ca2c741d252702`.
Both upstream implementations were rebuilt as 3.54.2, matching the official upstream version:
legacy `a3939be95cd27dd9cf72e25afea32285bd7e80ac` and current
`9abde9777d92af50fe1a53634262498fb630d7d3`.
[Build and regression checks](https://github.com/Dojz/piko-newx-zh/actions/runs/38107831285) passed.
The unified MPP SHA-256 is `85611f72880f0b8fc8d07978815f5230e3236f918a816bf627fc5274d0f15fc7`.

`DownloadCaption` observes native rendered states with bounded weak references, keyed by post
object identity. Only the tapped post's caption is read. Grok's `shouldShowTranslation` field gates
its completed translation; the standard `Translated` state already expresses that visibility.
The download tap snapshots the value before queueing; the naming context is cleared in finally
blocks after the selected output(s) are named. There is no translation dispatch, timeout, locale
inference, or cached-translation fallback. `{translatedText}` remains a hidden saved-template alias
for the same displayed value. The default filename template remains upstream's original default.

The classic bird is the upstream `twitter/bringbacktwitter` asset. The icon chooser uses the
checkout's existing themed dialog and action-handler contracts. A default launcher alias points
to the native main activity. A blue-bird alias points to a patch-generated subclass of that native
activity with a blue background and white-bird splash theme. The native activity stays enabled,
its other entry points remain intact, and its original theme remains available. The selected
launcher alias determines the splash theme on launches from that icon. Arbitrary image upload was
not implemented; the user clarified that restoring the classic bird was the primary requirement.

## Real APK validation

Input: user's merged unpatched `com.twitter.android`, `12.28.0-prod.01`, production APK.
SHA-256: `e5ac830cf55096ed9fb32b8652a60fa87e5c82ff19645362117cfce69fc6e9b8`.
CLI: Morphe Desktop 1.18.1, SHA-256
`1b506ab5f03d16a2f65026d5e0e1910d01fc1e2152f21eaeb44ed2f30856597b`.
The local `patch-twitter.sh` was used with this CI-built unified MPP, the exact input APK,
`--unsigned`, a separate output path, and without `--force` or device installation.
`Restore Twitter branding` was omitted to verify that the original X icon/theme remain intact.
The new `Choose app icon` patch replaced it in the enabled list.

The APK version filter skipped the current implementation and selected the compatible legacy
implementation. All 44 enabled patches applied successfully, including the new icon patch.
The 768 MiB run completed both `PATCHING` and `REBUILDING`, saving the internal inspection APK.
That APK is not a user deliverable and was not installed or launched on a phone.

`tests/check_caption_apk.py` passed on the final APK: all three translation presenter bodies retain
the original instructions, register counts and branch destinations. The new entries use their own
stable receivers and preserve the returned states. The final launcher check confirms enabled
original/disabled bird defaults, both manifest aliases, the native superclass and constructor,
retained original theme, emitted translation-visibility guard, and absence of request/cache bridges.
Decoded resources resolve the bird drawable/icon and `PikoBlueBirdSplash`, inheriting
`Theme.X.SplashScreen` with `#ff1da1f2` and the white bird drawable.

## Observed heap-floor failure

The 512 MiB desktop run applied all patches and encoded resources, then failed while applying the
compiled APK output base:

```
java.lang.OutOfMemoryError: Java heap space
  java.util.Arrays.copyOf
  com.google.common.io.ByteStreams.combineBuffers
  com.google.common.io.ByteStreams.toByteArrayInternal
  com.android.tools.build.apkzlib.bytestorage.InMemoryByteStorage.fromStream
  com.android.tools.build.apkzlib.bytestorage.OverflowToDiskByteStorage.fromStream
  com.android.tools.build.apkzlib.bytestorage.ChunkBasedByteStorage.fromStream
  com.android.tools.build.apkzlib.zip.ZFile.add
  app.morphe.patcher.apk.ApkUtils.applyTo(ApkUtils.kt:135)
  app.morphe.desktop.command.PatchCommand.call
```

The same bundle/input completed at 768 MiB. This is an observed desktop packaging heap limit;
its attribution to patcher storage, bundle size or feature changes has not been isolated against
a baseline. It does not measure X's runtime memory or Morphe Manager's adaptive heap behavior.
No claim of a successful 512 MiB run, phone runtime validation, or testing all 15 APK targets is made.

## Naming memory optimization after the APK inspection

The subsequent optimization reuses the same rendered-state record and post weak reference on
recomposition, and reuses the state weak reference when the native state object is unchanged.
Queued naming captions retain at most 240 UTF-8 bytes after line-break and unsafe-character
normalization; final template sanitation and extension-aware filename truncation still apply.
No full translation string or additional native post is retained by the naming context.
The context is still cleared after all selected outputs are named.
The displayed-state observer is bounded to 256 weak entries; it never copies their captions.

A long alternating line-break/tab fixture exposed recursive regular-expression matching in the
filename normalizer. Its repetition is now possessive, with the same replacement policy and
without that backtracking. Both implementation filename harnesses cover long original/translated
text, Unicode boundaries, leading line breaks and cleanup; the observer harness checks record
and weak-reference reuse across 1,000 recompositions. The APK inspection above predates this
Java-only optimization; the native hook, launcher bytecode and resources are unchanged.
The desktop heap setting is unrelated to X runtime RAM, which has not been measured.
