"""Install the same narrow long-press feature on each retained upstream implementation."""
import re
from pathlib import Path

MISC = Path("extensions/newx/src/main/java/app/morphe/extension/newx/misc")


def replace(text, old, new, count=1):
    if text.count(old) != count:
        raise ValueError(f"Quick download integration anchor changed: {old[:100]!r}")
    return text.replace(old, new)


def install(piko: Path) -> None:
    root = piko / MISC
    # These imports moved from NewX into the shared extension. Follow each checkout.
    font = (root / "UpdateFont.java").read_text()
    path = root / "QuickDownloadOptions.java"
    source = path.read_text()
    for name in ("ButtonView", "DialogView"):
        imports = re.findall(r"^import ([\w.]+\." + name + r");$", font, re.MULTILINE)
        if len(imports) != 1:
            raise ValueError(f"Quick download {name} UI ABI changed")
        source = source.replace("app.morphe.extension.newx.ui." + name, imports[0])

    folders = "NewXDownloadFolders" if (root / "NewXDownloadFolders.java").exists() else "DownloadDestination"
    source = source.replace("DownloadDestination", folders)
    path.write_text(source)

    path = root / "DownloadSettings.java"
    source = path.read_text()
    for name in ("IMAGES_TREE_URI", "IMAGES_DISPLAY_PATH", "VIDEOS_TREE_URI", "VIDEOS_DISPLAY_PATH", "FILENAME_TEMPLATE"):
        pattern = r"(?m)^(\s*)return SettingsRegistry\.getStringOrDefault\(" + name + r","
        source, found = re.subn(pattern, r"\1String quick = QuickDownloadOptions.override(" + name
                               + r");\1if (quick != null) return quick;\1return SettingsRegistry.getStringOrDefault("
                               + name + ",", source)
        if found != 1:
            raise ValueError(f"Quick download settings getter changed: {name}")
    path.write_text(source)

    path = root / "DownloadFileName.java"
    source = path.read_text()
    source = replace(source, "public static final class PostContext {", """public static final class PostContext {
        Object quickDownloadOptions;
        boolean quickDownloadTemporary;
        final String downloadTimestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT)
                .format(new java.util.Date());""")
    source = replace(source, 'private static final String[] POST_TOKENS = {',
                     'private static final String[] POST_TOKENS = {\n            "downloadTimestamp",')
    source = replace(source, 'private static final String[] EDITOR_TOKENS = {',
                     'private static final String[] EDITOR_TOKENS = {\n            "downloadTimestamp",')
    source = replace(source, 'return token.equals(TOKEN_TEXT)',
                     'return token.equals("downloadTimestamp") || token.equals(TOKEN_TEXT)')
    source = replace(source, 'String pattern = template == null || template.trim().isEmpty() ? DEFAULT_TEMPLATE : template;',
                     'String pattern = template == null || template.trim().isEmpty() ? DEFAULT_TEMPLATE : template;\n'
                     '        pattern = pattern.replace("{downloadTimestamp}", post.downloadTimestamp);')
    path.write_text(source)

    path = root / "InlineDownloadButton.java"
    source = path.read_text()
    source = replace(source, "            Object post = getPresenterPost(presenter);", """            if (longPress) {
                QuickDownloadOptions.show(currentActivity());
                return true;
            }
            Object post = getPresenterPost(presenter);""")
    source = replace(source, "CLICK_EXECUTOR.execute(() -> {\n            try {",
                     "CLICK_EXECUTOR.execute(() -> {\n            QuickDownloadOptions.begin(postContext);\n            try {")
    source = replace(source, "CLICK_EXECUTOR.execute(() -> {\n            final EnqueueState state;",
                     "CLICK_EXECUTOR.execute(() -> {\n            QuickDownloadOptions.begin(postContext);\n            final EnqueueState state;")
    source = replace(source, "postContext.clearCaption();",
                     "QuickDownloadOptions.end();\n                postContext.clearCaption();", count=2)
    for kind in ("IMAGES", "VIDEOS"):
        source = replace(source, f"{folders}.destinationState(context, {folders}.MediaKind.{kind})",
                         f"QuickDownloadOptions.destinationState(context, {folders}.MediaKind.{kind})")
    anchor = "        final DownloadDestination.Target target;" if folders == "DownloadDestination" else "        DownloadEngine engine = NewXDownloader.get();"
    source = replace(source, anchor, """        if (QuickDownloadOptions.nativeFolder(kind.name())) {
            return QuickDownloadStorage.enqueue(context, download.url, download.mimeType, fileName)
                    ? EnqueueState.QUEUED : EnqueueState.FAILED;
        }

""" + anchor)
    path.write_text(source)

    # An invalid one-shot destination must not clear an unrelated saved destination.
    path = root / (folders + ".java")
    source = replace(path.read_text(), "public static void invalidate(MediaKind kind) {",
                     "public static void invalidate(MediaKind kind) {\n        if (QuickDownloadOptions.isTemporary()) return;")
    path.write_text(source)

    path = root / "MediaMerger.java"
    source = path.read_text()
    source = replace(source, "try { performMerge(safeContext, items, username, postContext); }",
                     "QuickDownloadOptions.begin(postContext);\n            try { performMerge(safeContext, items, username, postContext); }")
    source = replace(source, "finally { postContext.clearCaption(); }",
                     "finally { QuickDownloadOptions.end(); postContext.clearCaption(); }")
    anchor = "            // Reserve before encoding so a skipped merge costs no work."
    source = replace(source, anchor, """            if (QuickDownloadOptions.nativeFolder("IMAGES")) {
                try {
                    QuickDownloadStorage.saveMerged(context, mergedBitmap, format, mimeType, fileName);
                    InlineDownloadButton.reportDownloadStatus("Merged image saved: " + fileName, username);
                } finally {
                    mergedBitmap.recycle();
                }
                return;
            }

""" + anchor)
    path.write_text(source)
