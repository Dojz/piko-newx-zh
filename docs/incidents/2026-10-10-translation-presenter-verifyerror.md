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

The fix clones the method with `numberOfParameterRegisters + 1` extra registers, reserves a
receiver local outside the complete original register frame, and captures the incoming receiver
before original instructions execute. Every return hook stages that saved receiver into a
four-bit scratch register before reading its post field. The original return value is excluded
from scratch allocation. Branches to returns run the hook; original entry labels are not moved.

`DownloadCaptionPatchTest` executes object-reuse and primitive-reuse paths through the emitted
hooks in 16- and 48-register methods, including a direct jump to the return instruction. It
checks the field-read receiver type, exact post/state/event arguments and preserved return value.
Both retained legacy and current implementations must be rebuilt; rebuilding only the latest
implementation would leave affected 12.28 users on the old embedded hook.
