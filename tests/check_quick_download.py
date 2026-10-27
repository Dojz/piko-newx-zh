"""Host regressions for draft equality and one-shot action isolation."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = Path("app/morphe/extension/newx/misc")
with tempfile.TemporaryDirectory(prefix="quick-download-") as temporary:
    root = Path(temporary)
    package = root / PACKAGE
    package.mkdir(parents=True)
    source = ROOT / "piko-overrides/extensions/newx/src/main/java" / PACKAGE / "QuickDownloadState.java"
    (package / source.name).write_text(source.read_text())
    (package / "QuickCheck.java").write_text(r'''package app.morphe.extension.newx.misc;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class QuickCheck {
 static void check(boolean value) { if (!value) throw new AssertionError(); }
 public static void main(String[] args) throws Exception {
  var saved = new QuickDownloadState.Values("image-uri", "old/images", "video-uri", "old/videos", "{id}");
  var defaults = QuickDownloadState.Values.defaults();
  check(defaults.imagesPath.equals("Pictures/Twitter/"));
  check(defaults.videosPath.equals("Movies/Twitter/"));
  check(defaults.template.equals("{downloadTimestamp}"));
  check(defaults.nativeFolder(false) && defaults.nativeFolder(true));
  var state = new QuickDownloadState();
  check(state.peek(saved) == saved);
  check(!saved.folder(false, "changed", "changed").equals(saved));
  check(saved.folder(false, "changed", "changed").folder(false, "image-uri", "old/images").equals(saved));
  check(!saved.folder(true, "changed", "changed").equals(saved));
  check(!saved.template("{text}").equals(saved));
  state.once(defaults);
  check(state.peek(saved) == defaults);
  check(state.peek(saved) == defaults);
  check(state.claim(saved) == defaults);
  check(state.claim(saved) == saved);
  check(saved.template.equals("{id}") && saved.imagesUri.equals("image-uri"));
  state.once(defaults);
  var pool = Executors.newFixedThreadPool(8);
  var uses = new AtomicInteger();
  var jobs = new java.util.ArrayList<Future<?>>();
  for (int i = 0; i < 64; i++) jobs.add(pool.submit(() -> {
   if (state.claim(saved) == defaults) uses.incrementAndGet();
  }));
  for (var job : jobs) job.get();
  pool.shutdown();
  check(uses.get() == 1);
  state.once(defaults);
  state.once(saved.template("{text}"));
  check(state.claim(saved).template.equals("{text}"));
  state.once(defaults);
  state.clear();
  check(state.claim(saved) == saved);
  System.out.println("Quick download state: defaults, dirty/reverted drafts, cancel/peek, single-use, concurrency, replace and clear passed");
 }
}''')
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
    subprocess.run([java, "com.sun.tools.javac.Main", "-d", str(root / "classes"),
                    *map(str, root.rglob("*.java"))], check=True)
    subprocess.run([java, "-cp", str(root / "classes"),
                    "app.morphe.extension.newx.misc.QuickCheck"], check=True)
