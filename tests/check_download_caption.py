"""Guard cached-but-hidden translations, cross-view leaks and unwanted native requests."""
import os
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "piko-overrides/extensions/newx/src/main/java/app/morphe/extension/newx/misc/DownloadCaption.java"
PACKAGE = "app/morphe/extension/newx/misc/"

with tempfile.TemporaryDirectory(prefix="displayed-caption-") as temporary:
    root = Path(temporary)
    def write(name, content):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    source = SOURCE.read_text().replace('throw new IllegalStateException("Unpatched stateText");',
        'CaptionCheck.reads++; CaptionCheck.State value = (CaptionCheck.State) state; return value.visible ? value.text : null;')
    write(PACKAGE + "DownloadCaption.java", source)
    write(PACKAGE + "CaptionCheck.java", '''package app.morphe.extension.newx.misc;
import java.util.*;import java.util.Objects;
public class CaptionCheck {
 static int reads;
 static class Post {String id;Post(String id){this.id=id;} public boolean equals(Object other){return other instanceof Post&&id.equals(((Post)other).id);}public int hashCode(){return id.hashCode();}}
 static class State {boolean visible;String text;State(boolean visible,String text){this.visible=visible;this.text=text;}}
 static void equal(String expected,String actual){if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+" got "+actual);}
 public static void main(String[]args)throws Exception{
  Post selected=new Post("selected"),otherView=new Post("selected"),unrelated=new Post("other");
  State shown=new State(true,"屏幕译文"),hidden=new State(false,"缓存译文"),other=new State(true,"其他窗口");
  DownloadCaption.record(unrelated,other);DownloadCaption.record(otherView,other);
  if(reads!=0)throw new AssertionError("Read unrelated captions");
  equal("原文",DownloadCaption.displayedText(selected,"原文"));
  DownloadCaption.record(selected,hidden);equal("原文",DownloadCaption.displayedText(selected,"原文"));
  DownloadCaption.record(selected,shown);equal("屏幕译文",DownloadCaption.displayedText(selected,"原文"));
  String snapshot=DownloadCaption.displayedText(selected,"原文");
  DownloadCaption.record(selected,hidden);equal("原文",DownloadCaption.displayedText(selected,"原文"));
  equal("屏幕译文",snapshot);equal("其他窗口",DownloadCaption.displayedText(otherView,"original"));
  DownloadCaption.record(selected,null);equal("同语言原文",DownloadCaption.displayedText(selected,"同语言原文"));
  var field=DownloadCaption.class.getDeclaredField("STATES");field.setAccessible(true);
  DownloadCaption.record(selected,shown);
  var records=(java.util.ArrayDeque<?>)field.get(null);Object reused=records.peekLast();
  var stateField=reused.getClass().getDeclaredField("state");stateField.setAccessible(true);
  Object reference=stateField.get(reused);
  for(int i=0;i<1000;i++)DownloadCaption.record(selected,shown);
  if(records.peekLast()!=reused||stateField.get(reused)!=reference)throw new AssertionError("Recomposition allocates duplicate records/references");
  DownloadCaption.record(selected,hidden);
  if(records.peekLast()!=reused||stateField.get(reused)==reference)throw new AssertionError("State update failed to reuse record");
  equal("original",DownloadCaption.displayedText(selected,"original"));
  List<Object> posts=new ArrayList<>();List<State> states=new ArrayList<>();
  for(int i=0;i<400;i++){Object post=new Object();State state=new State(true,"unused");posts.add(post);states.add(state);DownloadCaption.record(post,state);}
  if(((Collection<?>)field.get(null)).size()>256)throw new AssertionError("Unbounded observer");
  System.out.println("Displayed-caption checks passed: original, shown translation, hidden cache, toggled original, tap snapshot, separate views, no eager reads, bounded weak state");
 }
}''')
    java = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else Path("/usr/bin")
    subprocess.run([str(java / "java"), "com.sun.tools.javac.Main", "-d", str(root / "classes"), *map(str, root.rglob("*.java"))], check=True)
    subprocess.run([str(java / "java"), "-cp", str(root / "classes"), "app.morphe.extension.newx.misc.CaptionCheck"], check=True)
