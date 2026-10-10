# Translation presenter receiver reuse

- Target: `com.twitter.android` / `12.28.0-prod.01`, Android 11 (API 30).
- Reported bundle: fork `3.54.2`; legacy Piko source `a3939be95cd27dd9cf72e25afea32285bd7e80ac`.
- Failure: `VerifyError` in `com.x.urt.items.post.translate.i.a(Composer, int)`, attempting to
  read presenter field `b:com.x.models.p6` from a `com.x.repositories.post.b0` object at code offset `0x114`.
- Classification: fork hook register lifetime error, not resolver/linter failure or a network request.

The original method has 16 registers and three incoming words; its receiver initially occupies
v13. At original code offset `0x00bd`, `move-result-object v13` overwrites the receiver with
translation state. Later paths reuse v13 for a repository response, a boolean/int, a string or
a translation state singleton. Reading the presenter field through p0 at a return is invalid.

The fix preserves the complete original body under a derived private method name and keeps
its original register frame, instructions, try ranges and branch targets unchanged. A public
entry with the original signature invokes that body, receives the state in a dedicated local,
and reads the post through its own receiver before recording the result. Reuse of the body's
p0 cannot affect the entry frame. No return hooks or branch relocation occur inside the body.

`DownloadCaptionPatchTest` executes object-reuse and primitive-reuse paths through the emitted
entry and original body in 16- and 48-register methods, including a jump to a shared return. It
checks the field-read receiver type, exact post/state/event arguments and preserved return value.
Both retained legacy and current implementations must be rebuilt; rebuilding only the latest
implementation would leave affected 12.28 users on the old embedded hook.

During validation, the initial in-body hook approach also reproduced a dependency issue:
`morphe-bytecode:0.1.3` unplaces a goto target while relocating return labels, and dexlib2's
`fixInstructions` rejects it at `Hook.kt:109`. The entry approach needs no such relocation and
leaves those branches intact. The library itself is not modified by this change.

## Verified publication

- Source build: `eac36118c81a88d53b65e9146f35017a0acaf764`.
- Actions run: https://github.com/Dojz/piko-newx-zh/actions/runs/38059937888.
- Rebuilt Piko sources: legacy `a3939be95cd27dd9cf72e25afea32285bd7e80ac` and current
  `9abde9777d92af50fe1a53634262498fb630d7d3`.
- Both `:patches:build` test suites and Android builds passed, along with native-caption and
  filename checks. The unified bundle retains all 15 prior targets with no duplicate X patches.
- Released `v3.54.3/patches.mpp`, SHA-256
  `f6da0770cb6b78f8315d1ca93a6afbc78d54f8ec237cad63ede6223c7e584ee3`.
- The supplied original 12.28.0-prod.01 APK was patched using the rebuilt unified bundle and
  Morphe Desktop 1.18.1 through `patch-twitter.sh` with adapted tool/output paths, unsigned and
  without forcing compatibility. All 44 patches and the rebuild passed.
- `tests/check_caption_apk.py` compared final classes13.dex against the original APK: three
  presenters, six original returns, identical original body instructions/registers/branch targets;
  every entry invokes the private body, receives its result and reads the post via its own p0.
- No phone installation or runtime reproduction was performed; the private LAN was unreachable.
