import os
from pathlib import Path
import subprocess
import sys
import tempfile


SOURCE = Path(sys.argv[1]) / "extensions/newx/src/main/java/app/morphe/extension/newx/misc/DownloadFileName.java"
PACKAGE = "app/morphe/extension/newx/misc/"

# Preserve upstream's fallback and registered defaults; captions remain opt-in.
assert 'DEFAULT_TEMPLATE = "{userName}_{id}"' in SOURCE.read_text()
settings_patch = Path(sys.argv[1]) / "patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/InlineDownloadButtonPatch.kt"
filename_setting = settings_patch.read_text().split('id = "newx.content.inline_download.filename_template",', 1)[1].split('visible = false,', 1)[0]
assert 'defaultValue = "{screenName}_{id}"' in filename_setting

with tempfile.TemporaryDirectory(prefix="caption-filename-") as temporary:
    root = Path(temporary)

    def write(name, content):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    write(PACKAGE + "DownloadFileName.java", SOURCE.read_text())
    write("androidx/annotation/Nullable.java", "package androidx.annotation; public @interface Nullable {}")
    write(PACKAGE + "DownloadSettings.java", 'package app.morphe.extension.newx.misc; public class DownloadSettings {static final String FILENAME_TEMPLATE="template";}')
    write(PACKAGE + "DownloadCaption.java", 'package app.morphe.extension.newx.misc; public class DownloadCaption {public static String translate(Object post,String text){throw new AssertionError("Rendering must use the resolved caption");}}')
    write("app/morphe/extension/shared/StringRef.java", 'package app.morphe.extension.shared; public class StringRef {public static String str(String name,Object...args){return name;}}')
    write("app/morphe/extension/newx/utils/ToStringParser.java", 'package app.morphe.extension.newx.utils; public class ToStringParser {public static String fieldValue(String text,String key){return null;}}')
    write("app/morphe/extension/newx/utils/NewXUtils.java", '''package app.morphe.extension.newx.utils;
public class NewXUtils {
 public static String rawSourcePostId(String text){return null;}
 public static String rawSourceScreenName(String text){return null;}
 public static String rawSourceDisplayName(String text){return null;}
 public static String rawSourceMediaField(String text,String key){return null;}
}''')
    write(PACKAGE + "FilenameCheck.java", r'''package app.morphe.extension.newx.misc;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
public class FilenameCheck {
 static void equal(String expected,String actual){if(!Objects.equals(expected,actual))throw new AssertionError("Expected ["+expected+"] but got ["+actual+"]");}
 public static void main(String[]args){
  for(String separator:new String[]{"\n","\r","\r\n","\t","\n\n\t","\u000b","\f","\u0085","\u2028","\u2029"})
   equal("第一行 第二行",DownloadFileName.sanitizeSegment("第一行"+separator+"第二行",null));
  equal("甲  乙",DownloadFileName.sanitizeSegment("甲  乙",null));
  equal("甲_乙",DownloadFileName.sanitizeSegment("甲/乙",null));
  equal("甲_乙",DownloadFileName.sanitizeSegment("甲\u0000乙",null));
  equal("fallback",DownloadFileName.sanitizeSegment("\n\t\u2028","fallback"));
  DownloadFileName.PostContext post=DownloadFileName.PostContext.sample();
  post.text="原文\r\n第二行";post.translatedText="译文\n第二行";
  equal("原文 第二行.jpg",DownloadFileName.render("{text}",post,0,1,"jpg"));
  equal("译文 第二行.jpg",DownloadFileName.render("{translatedText}",post,0,1,"jpg"));
  equal("译文 第二行_2.jpg",DownloadFileName.render("{translatedText}",post,1,2,"jpg"));
  post.translatedText="中文🙂\n".repeat(100);
  String filename=DownloadFileName.render("{translatedText}",post,0,1,"jpg");
  if(filename.getBytes(StandardCharsets.UTF_8).length>240||!filename.endsWith(".jpg")||filename.contains("\n"))throw new AssertionError(filename);
  System.out.println("Filename checks passed: original/translated captions, CR/LF/CRLF, tabs, Unicode line breaks, spaces, unsafe characters, media index and UTF-8 limit");
 }
}''')
    java = Path(os.environ["JAVA_HOME"]) / "bin/java" if os.environ.get("JAVA_HOME") else Path("/usr/bin/java")
    subprocess.run([str(java), "com.sun.tools.javac.Main", "-d", str(root / "classes"), *map(str, root.rglob("*.java"))], check=True)
    subprocess.run([str(java), "-cp", str(root / "classes"), "app.morphe.extension.newx.misc.FilenameCheck"], check=True)
