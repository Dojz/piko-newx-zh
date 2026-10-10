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

Validation so far: exact uploaded DEX anchor/field inspection; Kotlin compilation against
both original patch implementations; host checks for same-language fallback, cached
results, lazy selected-post reads, one request for concurrent waiters, temporary-data
cleanup, timeouts and native callback errors. Local full Gradle build cannot resolve
GitHub Packages dependencies without credentials. GitHub CI builds both checkouts,
including `:patches:build`, and produces artifacts without updating a release source.
Full APK patching and phone runtime results must be recorded before release.
