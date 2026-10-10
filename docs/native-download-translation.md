# Native download translation

Target: `com.twitter.android`, `12.28.0-prod.01`.
The uploaded APKS contains `base.apk` plus arm64, xxhdpi and zh splits.
All 12 base DEX files are byte-for-byte identical to the uploaded MT-converted APK.
Base APK SHA-256: `be1799f5162dc30cdccde37a820efdec77c7bbd6f9689fca1dbe3f1a196e80ef`.

The production DEX has a `CanonicalPost` language string and
`grokAutoTranslation` value. `GrokAutoTranslation.Available` holds the full
translation separately from the preview. The native Grok UI state has
`Completed(content=TranslatedPost(text=...))`; the older translation UI state
has `Translated(translatePostResponse=TranslatePostResponse(..., translation=...))`.
Both native UI state types carry a `Function1` event sink. Their corresponding
`RequestTranslation` singletons are the events used by the existing buttons.
The sink starts X's own coroutine and repository flow. The extension constructs
no network request and uses no external translation provider.

The patch resolves these contracts from semantic model labels, field types,
interfaces and Compose presenter signatures. Obfuscated descriptors from this
APK are not embedded in the runtime extension. Model fields and request
singletons must resolve unambiguously; missing or changed contracts stop patching.
Final `eventSink` labels use a specialized string-builder helper, so sink fields
are resolved by their unique `Function1` type rather than guessed instruction offsets.
The post language field follows its label until the next model label, with exactly
one owner/string field required. The typed emitter reserves the return and presenter
registers when capturing native state before a return.

Only weak native state/callback handles are retained for visible posts. Translation
strings are read for the requested post only. An uncached request temporarily keeps
its result for active waiters, then clears it. The filename context clears its temporary
translation in `finally` after rendering, including a failed render. Source-language
and system-language equality returns the original text without requesting translation.
Missing language metadata does not authorize original-text fallback. Requests run on
the UI thread while the download worker waits at most 20 seconds. Errors abort naming
with a localized message and do not silently substitute the source language.

Validated overlay source: `6659a0bdaebe082c5669694e67d617314cc538a5`.
Both pinned implementations passed real Android builds and full `:patches:build`
tests in [Actions run 38036580283](https://github.com/Dojz/piko-newx-zh/actions/runs/38036580283).
Host checks cover same-language fallback, cached results, selected-post reads,
concurrent request deduplication, cleanup, timeout, callback failure and unsupported
post objects. Filename checks cover spaces, Unicode byte limits and temporary cleanup.

Native presenters retain a model interface implemented by ContextualPost. The resolver
accepts that interface or the concrete model, requires a unique post field, and identifies
two Grok presenters plus one standard presenter in the uploaded DEX. Exact original-base
APK patching through `patch-twitter.sh` succeeded at 512 MB: 44 patches applied once,
including `NewX: Inline download button`, and the APK was saved. Final DEX inspection
confirms six injected bridge methods and six state-capture calls across the three presenters.
Unified MPP SHA-256: `8f99240d1181b6f5ba28f74a7908eeb0d7f2af93fea9dccb424719206ed51a9a`.
The two setup/resolver failures and their retests are recorded in
[newx-resolver-linter/2026-10-10-native-caption-12.28.md](newx-resolver-linter/2026-10-10-native-caption-12.28.md).
Phone runtime remains untested; the artifact is a test MPP and no release source was updated.
