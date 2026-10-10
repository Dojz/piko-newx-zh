import app.morphe.patcher.patch.AppTarget;
import app.morphe.patcher.patch.BytecodePatch;
import app.morphe.patcher.patch.Compatibility;
import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchLoader;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.zip.ZipFile;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;

public final class VerifyBundle {
    public static void main(String[] args) throws Exception {
        var patches = new PatchLoader.Jar(Set.of(new File(args[0])));
        var counts = new TreeMap<String, Integer>();
        for (Patch<?> patch : patches) {
            if (patch.getName() == null) continue;
            if (patch.getCompatibility() == null) continue;
            for (Compatibility compatibility : patch.getCompatibility()) {
                if (!"com.twitter.android".equals(compatibility.getPackageName())) continue;
                for (AppTarget target : compatibility.getTargets()) {
                    counts.merge(target.getVersion(), 1, Integer::sum);
                }
            }
        }
        if (counts.isEmpty()) throw new IllegalStateException("No NewX patches loaded");
        counts.forEach((version, count) -> System.out.println(version + ": " + count));
        Set<Patch<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Patch<?> patch : patches) checkDependencies(patch, visited);
        try (var zip = new ZipFile(args[0])) {
            var dexEntries = zip.stream().filter(e -> e.getName().matches("classes[0-9]*\\.dex")).toList();
            if (!dexEntries.isEmpty()) {
                Set<String> dexClasses = new HashSet<>();
                for (var entry : dexEntries) {
                    try (var stream = zip.getInputStream(entry)) {
                        var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), stream);
                        for (var definition : dex.getClasses()) {
                            if (!dexClasses.add(definition.getType())) throw new IllegalStateException("Duplicate dex class");
                        }
                    }
                }
                for (var entry : zip.stream().filter(e -> e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")).toList()) {
                    String descriptor = "L" + entry.getName().substring(0, entry.getName().length() - 6) + ";";
                    if (!dexClasses.contains(descriptor)) throw new IllegalStateException("Missing dex class " + descriptor);
                }
            }
        }
        for (int i = 1; i < args.length; i++) {
            String version = args[i];
            if (!counts.containsKey(version)) throw new IllegalStateException("Missing target " + version);
            var names = new HashSet<String>();
            for (Patch<?> patch : patches) {
                if (patch.getName() == null || patch.getCompatibility() == null) continue;
                boolean supported = patch.getCompatibility().stream().anyMatch(c ->
                    "com.twitter.android".equals(c.getPackageName()) && c.getTargets().stream().anyMatch(t -> version.equals(t.getVersion()))
                );
                if (supported && !names.add(patch.getName())) throw new IllegalStateException("Duplicate compatible patch " + patch.getName());
            }
        }
    }

    private static void checkDependencies(Patch<?> patch, Set<Patch<?>> visited) throws Exception {
        if (!visited.add(patch)) return;
        if (patch instanceof BytecodePatch bytecode) {
            for (var provider : bytecode.getExtensionStreamProviders$morphe_patcher()) {
                for (var supplier : provider.get()) {
                    try (var stream = supplier.get()) {
                        if (stream == null || stream.readNBytes(4).length != 4) throw new IllegalStateException("Missing extension for " + patch);
                    }
                }
            }
        }
        for (Patch<?> dependency : patch.getDependencies()) checkDependencies(dependency, visited);
    }
}
