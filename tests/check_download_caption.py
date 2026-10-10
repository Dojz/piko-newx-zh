import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "piko-overrides/extensions/newx/src/main/java/app/morphe/extension/newx/misc/DownloadCaption.java"
PACKAGE = "app/morphe/extension/newx/misc/"

with tempfile.TemporaryDirectory(prefix="native-caption-") as temporary:
    root = Path(temporary)
    def write(name, content):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    source = SOURCE.read_text().replace("TIMEOUT_MS = 20000", "TIMEOUT_MS = 300")
    bridges = {
        "sourceLanguage": "return ((CaptionCheck.Post) post).language;",
        "postId": "return ((CaptionCheck.Post) post).id;",
        "cachedText": "return ((CaptionCheck.Post) post).cached;",
        "stateText": "if (state == null) return null; CaptionCheck.reads++; return ((CaptionCheck.State) state).text;",
        "eventSink": "return ((CaptionCheck.State) state).sink;",
        "dispatch": "((Runnable) sink).run();",
    }
    for name, body in bridges.items():
        source = source.replace(f'throw new IllegalStateException("Unpatched {name}");', body)
    write(PACKAGE + "DownloadCaption.java", source)
    write("android/content/res/Resources.java", '''package android.content.res;
public class Resources { public static Resources getSystem(){return new Resources();}
public Resources getConfiguration(){return this;} public Resources getLocales(){return this;}
public java.util.Locale get(int i){return java.util.Locale.CHINA;} }''')
    write("android/os/Looper.java", '''package android.os;
public class Looper {static final Looper MAIN=new Looper(); public static Looper myLooper(){return null;}
public static Looper getMainLooper(){return MAIN;} }''')
    write("android/os/SystemClock.java", '''package android.os;
public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;} }''')
    write("app/morphe/extension/newx/utils/NewXUtils.java", '''package app.morphe.extension.newx.utils;
public class NewXUtils {public static void runOnUiThread(Runnable task){new Thread(task).start();} }''')
    write(PACKAGE + "CaptionCheck.java", '''package app.morphe.extension.newx.misc;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class CaptionCheck {
 static int reads;
 static class Post {String id,language,cached; Post(String id,String language,String cached){this.id=id;this.language=language;this.cached=cached;}}
 static class State {String text;Runnable sink;State(String text,Runnable sink){this.text=text;this.sink=sink;}}
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void fail(Runnable task){try{task.run();throw new AssertionError("Expected failure");}catch(IllegalStateException expected){}}
 public static void main(String[]args)throws Exception{
  check(DownloadCaption.translate(new Post("same","zh",null),"原文 空格").equals("原文 空格"));
  check(DownloadCaption.translate(new Post("cached","en","译文"),"original").equals("译文"));
  fail(()->DownloadCaption.translate(new Post("missing","en",null),"original"));
  Post unrelated=new Post("unrelated","en",null);State unused=new State("unrelated text",()->{});
  DownloadCaption.record(unrelated,unused,new Object());check(reads==0);
  Post post=new Post("selected","en",null);AtomicInteger calls=new AtomicInteger();
  State initial=new State(null,null);List<State> keep=new ArrayList<>();keep.add(initial);
  initial.sink=()->{calls.incrementAndGet();try{Thread.sleep(50);}catch(Exception e){throw new RuntimeException(e);}
   State result=new State("目标译文",initial.sink);keep.add(result);DownloadCaption.record(post,result,new Object());};
  DownloadCaption.record(post,initial,new Object());check(reads==0);
  ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
  Callable<String> task=()->{gate.await();return DownloadCaption.translate(post,"original");};
  Future<String>a=executor.submit(task),b=executor.submit(task);gate.countDown();
  check(a.get().equals("目标译文")&&b.get().equals("目标译文"));check(calls.get()==1);executor.shutdown();
  var field=DownloadCaption.class.getDeclaredField("SESSIONS");field.setAccessible(true);
  for(Object session:((Map<?,?>)field.get(null)).values()){
   var text=session.getClass().getDeclaredField("text");text.setAccessible(true);check(text.get(session)==null);
  }
  Post timeout=new Post("timeout","en",null);State stalled=new State(null,()->{});
  DownloadCaption.record(timeout,stalled,new Object());fail(()->DownloadCaption.translate(timeout,"original"));
  Post error=new Post("error","en",null);State broken=new State(null,()->{throw new IllegalStateException("native failure");});
  DownloadCaption.record(error,broken,new Object());fail(()->DownloadCaption.translate(error,"original"));
  System.out.println("Native translation checks passed: same language, cached text, selected post, duplicate requests, cleanup, timeout, failure");
 }
}''')
    java = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else Path("/usr/bin")
    subprocess.run([str(java / "java"), "com.sun.tools.javac.Main", "-d", str(root / "classes"), *map(str, root.rglob("*.java"))], check=True)
    subprocess.run([str(java / "java"), "-cp", str(root / "classes"), "app.morphe.extension.newx.misc.CaptionCheck"], check=True)
