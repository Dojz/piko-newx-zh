import app.morphe.patcher.patch.Patch;
import app.morphe.patcher.patch.PatchLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Regression for Manager 1.34.0 selection keys, loaded separately by UI/patcher.
 * See MorpheApp/morphe-manager v1.34.0, PatchInfo.uniqueNames and PatchBundle.Loader.
 * Different reflection/Dex load orders must resolve each saved key to the same implementation.
 */
public final class VerifyManagerSelection {
    private static final String PACKAGE = "com.twitter.android";

    public static void main(String[] args) {
        if (args.length == 3 && "--same-names".equals(args[0])) {
            if (!names(args[1]).equals(names(args[2]))) {
                throw new IllegalStateException("Retained selection names changed with bundle namespace");
            }
            System.out.println("PASS: retained selection names survive different rebuild namespaces");
            return;
        }
        List<Patch<?>> patches = new ArrayList<>();
        for (var patch : new PatchLoader.Jar(Set.of(new File(args[0])))) {
            if (patch.getName() == null) continue;
            if (patch.getCompatibility() != null && patch.getCompatibility().stream().noneMatch(c ->
                c.getPackageName() == null || PACKAGE.equals(c.getPackageName()))) continue;
            patches.add(patch);
        }
        if (patches.isEmpty() || args.length < 2) throw new IllegalArgumentException("Bundle and target versions required");
        for (int seed = 0; seed < 12; seed++) {
            List<Patch<?>> uiOrder = new ArrayList<>(patches);
            Collections.shuffle(uiOrder, new Random(seed));
            List<Patch<?>> runtimeOrder = new ArrayList<>(uiOrder);
            Collections.reverse(runtimeOrder);
            Map<String, Patch<?>> ui = managerKeys(uiOrder);
            Map<String, Patch<?>> runtime = managerKeys(runtimeOrder);
            for (int i = 1; i < args.length; i++) {
                String version = args[i];
                int selected = 0;
                for (var entry : ui.entrySet()) {
                    Patch<?> expected = entry.getValue();
                    if (!supports(expected, version)) continue;
                    selected++;
                    Patch<?> actual = runtime.get(entry.getKey());
                    if (actual != expected) throw new IllegalStateException(
                        "Manager selection changed implementation for " + version + ": " + entry.getKey());
                }
                if (selected == 0) throw new IllegalStateException("No selectable patches for " + version);
            }
        }
        System.out.println("PASS: Manager selection/options keys retain implementations for " +
            (args.length - 1) + " versions across 12 independently reordered UI/runtime loads");
    }

    private static Set<String> names(String path) {
        Set<String> result = new HashSet<>();
        for (var patch : new PatchLoader.Jar(Set.of(new File(path)))) {
            if (patch.getName() != null) result.add(patch.getName());
        }
        if (result.isEmpty()) throw new IllegalStateException("No selection names in " + path);
        return result;
    }

    private static boolean supports(Patch<?> patch, String version) {
        if (patch.getCompatibility() == null) return true;
        return patch.getCompatibility().stream().anyMatch(c ->
            c.getPackageName() == null || (PACKAGE.equals(c.getPackageName()) &&
                c.getTargets().stream().anyMatch(t -> version.equals(t.getVersion()))));
    }

    private static String packageKey(Patch<?> patch) {
        if (patch.getCompatibility() == null) return "";
        return String.join(", ", patch.getCompatibility().stream().map(c -> c.getPackageName())
            .filter(p -> p != null).sorted().toList());
    }

    // Model the manager's per-package collision numbering: most specific declaration
    // keeps the plain name; equal package declarations keep incoming load order.
    private static Map<String, Patch<?>> managerKeys(List<Patch<?>> patches) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < patches.size(); i++) {
            String name = patches.get(i).getName();
            keys.add(name);
            groups.computeIfAbsent(name, k -> new ArrayList<>()).add(i);
        }
        Set<String> taken = new HashSet<>(keys);
        for (var group : groups.entrySet()) {
            List<Integer> indices = group.getValue();
            indices.sort(Comparator.comparing((Integer i) -> packageKey(patches.get(i))).reversed());
            for (int j = 1; j < indices.size(); j++) {
                int number = 1;
                String name = group.getKey();
                String key = name;
                while (!taken.add(key)) key = name + " (" + (++number) + ")";
                keys.set(indices.get(j), key);
            }
        }
        Map<String, Patch<?>> result = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) result.put(keys.get(i), patches.get(i));
        return result;
    }
}
