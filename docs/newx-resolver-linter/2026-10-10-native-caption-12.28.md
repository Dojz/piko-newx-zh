# 12.28 native translation patch validation

Package: `com.twitter.android`; version/build: `12.28.0-prod.01` production.
Input: original uploaded APKS base.apk; SHA-256 `be1799f5162dc30cdccde37a820efdec77c7bbd6f9689fca1dbe3f1a196e80ef`.
Overlay source: `e955f35667b47d520cffdf801a70ffeb13e829d9`.
Upstream sources: old `a3939be95cd27dd9cf72e25afea32285bd7e80ac`, current `f116ed9349db4f486bfb216071d18207901d5107`.
Both real Android MPPs and full `:patches:build` tests succeeded in Actions run 38035446085.
Unified MPP SHA-256: `488a403567be352cbe95390c367a6df8012762e7ca6e95603b21af971e33050d`.
Extensions were built in that same run and embedded in the MPP.

Command: `PATH=<JDK21>/bin:$PATH ./patch-twitter.sh /tmp/x1228/base.apk --unsigned --result-file=/tmp/x1228/native-patch-result.json -t=/tmp/x1228/native-patch-scratch`.
Script adjustments: exact current CLI 1.18.1, unsigned local output `/tmp/x1228/piko-native-12.28-patched.apk`; no installation.

The script initially contained `--force`, which bypassed version selection in the unified bundle and executed both implementations (88 patches). The second classic-spacing mutation failed because the first implementation had already changed its loop. Removing the compatibility bypass selected the declared 44 old-target patches; classic spacing then applied successfully. This was tooling setup, unrelated to resolver linter behavior.

The native translation dependency then found no presenter field of the concrete contextual-post type. Tightening the fingerprint to require that concrete field produced no matches in a second full-build retest (overlay `4bde5de0deb196b785fda26e50b6e62e346f709d`; run 38035885809; unified MPP SHA-256 `36c40e50a153aa355e3ae3516fc98cbed01d1927651a0fadee90acf755194bfe`). Both failures have the same cause: the native translation presenters retain a post interface implemented by ContextualPost, rather than the concrete ContextualPost descriptor. The earlier auxiliary-method interpretation was incorrect.

Read-only inspection with Morphe's actual model resolver confirms the concrete model and its implemented interfaces. Accepting the concrete type or those interfaces resolves exactly two Grok presenters and one standard presenter in the uploaded DEX. Each must still have exactly one post field. Unsupported post implementations are ignored by the recorder when their identifier bridge returns null, preventing unrelated UI crashes. This is model-interface resolution logic, not a resolver linter or cardinality helper defect. The final full build and exact APK retest passed; see the final results below.

Complete native presenter error:

```text
app.morphe.patcher.patch.PatchException: The patch "NewX: Inline download button" depends on "BytecodePatch@1602154134", which raised an exception:
app.morphe.patcher.patch.PatchException: Expected exactly one NewX translation presenter post, found 0: []
	at piko.compat.h4a999077b5c2.app.crimera.patches.newx.utils.ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
	at piko.compat.h4a999077b5c2.app.crimera.patches.newx.utils.ResolverCardinalityKt.requireExactlyOne$default(ResolverCardinality.kt:8)
	at piko.compat.h4a999077b5c2.app.crimera.patches.newx.misc.inlineactions.DownloadCaptionPatchKt.newXDownloadCaptionPatch$lambda$0$0(DownloadCaptionPatch.kt:251)
	at app.morphe.patcher.patch.Patch.execute(Patch.kt:120)
	at app.morphe.patcher.patch.BytecodePatch.execute$morphe_patcher(Patch.kt:264)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:96)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:84)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:120)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at kotlinx.coroutines.flow.SafeFlow.collectSafely(Builders.kt:57)
	at kotlinx.coroutines.flow.AbstractFlow.collect(Flow.kt:226)
	at app.morphe.desktop.command.PatchCommand$call$3$3$1.invokeSuspend(PatchCommand.kt:787)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:256)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:54)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlockingImpl(Builders.kt:30)
	at kotlinx.coroutines.BuildersKt.runBlockingImpl(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK(Builders.concurrent.kt:172)
	at kotlinx.coroutines.BuildersKt.runBlockingK(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK$default(Builders.concurrent.kt:157)
	at kotlinx.coroutines.BuildersKt.runBlockingK$default(Unknown Source)
	at app.morphe.desktop.command.PatchCommand.call$lambda$13$2(PatchCommand.kt:786)
	at app.morphe.desktop.command.model.PatchingResultKt.addStepResult(PatchingResult.kt:29)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:785)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:55)
	at picocli.CommandLine.executeUserObject(CommandLine.java:2031)
	at picocli.CommandLine.access$1500(CommandLine.java:148)
	at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
	at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
	at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
	at picocli.CommandLine.execute(CommandLine.java:2174)
	at app.morphe.MorpheLauncherKt.main(MorpheLauncher.kt:85)

	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:87)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:120)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at kotlinx.coroutines.flow.SafeFlow.collectSafely(Builders.kt:57)
	at kotlinx.coroutines.flow.AbstractFlow.collect(Flow.kt:226)
	at app.morphe.desktop.command.PatchCommand$call$3$3$1.invokeSuspend(PatchCommand.kt:787)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:256)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:54)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlockingImpl(Builders.kt:30)
	at kotlinx.coroutines.BuildersKt.runBlockingImpl(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK(Builders.concurrent.kt:172)
	at kotlinx.coroutines.BuildersKt.runBlockingK(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK$default(Builders.concurrent.kt:157)
	at kotlinx.coroutines.BuildersKt.runBlockingK$default(Unknown Source)
	at app.morphe.desktop.command.PatchCommand.call$lambda$13$2(PatchCommand.kt:786)
	at app.morphe.desktop.command.model.PatchingResultKt.addStepResult(PatchingResult.kt:29)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:785)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:55)
	at picocli.CommandLine.executeUserObject(CommandLine.java:2031)
	at picocli.CommandLine.access$1500(CommandLine.java:148)
	at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
	at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
	at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
	at picocli.CommandLine.execute(CommandLine.java:2174)
	at app.morphe.MorpheLauncherKt.main(MorpheLauncher.kt:85)
```

Second failed contract check:

```text
app.morphe.patcher.patch.PatchException: The patch "NewX: Inline download button" depends on "BytecodePatch@1602154134", which raised an exception:
app.morphe.patcher.patch.PatchException: Failed to match the fingerprint: Fingerprint(definingClass=Lcom/x/urt/items/post/translate/grok/, returnType=Lcom/x/urt/items/post/translate/grok/p;, custom)
	at app.morphe.patcher.Fingerprint.patchException(Fingerprint.kt:953)
	at piko.compat.h069f44de97f5.app.crimera.patches.utils.ScopedFingerprintMatchingKt.scopedMatchAll(ScopedFingerprintMatching.kt:142)
	at piko.compat.h069f44de97f5.app.crimera.patches.newx.misc.inlineactions.DownloadCaptionPatchKt.newXDownloadCaptionPatch$lambda$0$0(DownloadCaptionPatch.kt:249)
	at app.morphe.patcher.patch.Patch.execute(Patch.kt:120)
	at app.morphe.patcher.patch.BytecodePatch.execute$morphe_patcher(Patch.kt:264)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:96)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:84)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:120)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at kotlinx.coroutines.flow.SafeFlow.collectSafely(Builders.kt:57)
	at kotlinx.coroutines.flow.AbstractFlow.collect(Flow.kt:226)
	at app.morphe.desktop.command.PatchCommand$call$3$3$1.invokeSuspend(PatchCommand.kt:787)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:256)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:54)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlockingImpl(Builders.kt:30)
	at kotlinx.coroutines.BuildersKt.runBlockingImpl(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK(Builders.concurrent.kt:172)
	at kotlinx.coroutines.BuildersKt.runBlockingK(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK$default(Builders.concurrent.kt:157)
	at kotlinx.coroutines.BuildersKt.runBlockingK$default(Unknown Source)
	at app.morphe.desktop.command.PatchCommand.call$lambda$13$2(PatchCommand.kt:786)
	at app.morphe.desktop.command.model.PatchingResultKt.addStepResult(PatchingResult.kt:29)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:785)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:55)
	at picocli.CommandLine.executeUserObject(CommandLine.java:2031)
	at picocli.CommandLine.access$1500(CommandLine.java:148)
	at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
	at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
	at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
	at picocli.CommandLine.execute(CommandLine.java:2174)
	at app.morphe.MorpheLauncherKt.main(MorpheLauncher.kt:85)

	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend$execute(Patcher.kt:87)
	at app.morphe.patcher.Patcher$invoke$1.invokeSuspend(Patcher.kt:120)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at app.morphe.patcher.Patcher$invoke$1.invoke(Patcher.kt)
	at kotlinx.coroutines.flow.SafeFlow.collectSafely(Builders.kt:57)
	at kotlinx.coroutines.flow.AbstractFlow.collect(Flow.kt:226)
	at app.morphe.desktop.command.PatchCommand$call$3$3$1.invokeSuspend(PatchCommand.kt:787)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:256)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:54)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlockingImpl(Builders.kt:30)
	at kotlinx.coroutines.BuildersKt.runBlockingImpl(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK(Builders.concurrent.kt:172)
	at kotlinx.coroutines.BuildersKt.runBlockingK(Unknown Source)
	at kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK$default(Builders.concurrent.kt:157)
	at kotlinx.coroutines.BuildersKt.runBlockingK$default(Unknown Source)
	at app.morphe.desktop.command.PatchCommand.call$lambda$13$2(PatchCommand.kt:786)
	at app.morphe.desktop.command.model.PatchingResultKt.addStepResult(PatchingResult.kt:29)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:785)
	at app.morphe.desktop.command.PatchCommand.call(PatchCommand.kt:55)
	at picocli.CommandLine.executeUserObject(CommandLine.java:2031)
	at picocli.CommandLine.access$1500(CommandLine.java:148)
	at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
	at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
	at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
	at picocli.CommandLine.execute(CommandLine.java:2174)
	at app.morphe.MorpheLauncherKt.main(MorpheLauncher.kt:85)
```

## Final result

Overlay source: `6659a0bdaebe082c5669694e67d617314cc538a5`.
Actions run: https://github.com/Dojz/piko-newx-zh/actions/runs/38036580283.
Both pinned source builds passed `:patches:build` and `buildAndroid`.
Current MPP SHA-256: `7e8216c9ca079a8efa24815907cf4b728fc3c29a5fafa7a4fc68550ffae546b2`.
Legacy MPP SHA-256: `5fd524c4d03154518242179591c1a5bdd6d04b7a812a1730862bbbb0a2b1a9df`.
Unified MPP SHA-256: `8f99240d1181b6f5ba28f74a7908eeb0d7f2af93fea9dccb424719206ed51a9a`.
All extension streams were freshly built and embedded by that run.

Same exact APK and command as above, with the fresh MPP and no `--force`:
44 named patches applied exactly once; zero failed patches; patching and rebuilding
steps succeeded. `NewX: Inline download button` applied. Output:
`/tmp/x1228/piko-native-12.28-patched.apk` (unsigned validation APK).
Output SHA-256: `8c5b19832b066bcaa65bb8124a6f7e530790b364be88e838bd1d8cd360220ca4`.
Morphe reports `Saved to` that exact output and purges its patching scratch directory.

Final DEX inspection found all six injected bridge methods (source language, post
identifier, native cache text, native state text, event sink, and native event dispatch).
It also found six state-capture calls covering both return paths of the two Grok
presenters and the standard presenter. No device was installed or controlled.
Host behavior and filename checks passed; actual phone downloads remain untested.
