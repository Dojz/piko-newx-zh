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
