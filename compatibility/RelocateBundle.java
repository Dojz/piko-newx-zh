import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public final class RelocateBundle {
    public static void main(String[] args) throws Exception {
        String prefix = "piko/compat/" + args[2] + "/";
        Set<String> excluded = new HashSet<>(List.of(args).subList(3, args.length));
        try (var input = new ZipFile(args[0]);
             var output = new ZipOutputStream(Files.newOutputStream(new File(args[1]).toPath()))) {
            Set<String> classes = new HashSet<>();
            input.stream().filter(e -> e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/"))
                .forEach(e -> classes.add(e.getName().substring(0, e.getName().length() - 6)));
            var remapper = new Remapper() {
                @Override public String map(String name) {
                    return classes.contains(name) ? prefix + name : name;
                }
                @Override public Object mapValue(Object value) {
                    if (!(value instanceof String text)) return super.mapValue(value);
                    for (String root : List.of("addresources", "extensions", "twitter")) {
                        if (text.equals(root) || text.startsWith(root + "/")) return prefix + text;
                    }
                    for (String root : List.of("app/crimera/", "app/morphe/patches/", "app/morphe/util/", "com/google/gson/")) {
                        text = text.replace(root, prefix + root);
                        text = text.replace(root.replace('/', '.'), prefix.replace('/', '.') + root.replace('/', '.'));
                    }
                    return text;
                }
            };
            for (ZipEntry entry : input.stream().toList()) {
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/") || name.endsWith(".dex")) continue;
                if (name.startsWith("app/crimera/patches/instagram/") && name.endsWith(".class")) continue;
                byte[] data = input.getInputStream(entry).readAllBytes();
                if (name.endsWith(".class")) {
                    var reader = new ClassReader(data);
                    var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    var filtering = new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                            var visitor = super.visitMethod(access, method, descriptor, signature, exceptions);
                            if (!name.equals("app/crimera/patches/newx/utils/Constants.class") ||
                                !method.equals("getCOMPATIBILITY_NEW_X") || excluded.isEmpty()) return visitor;
                            return new MethodVisitor(Opcodes.ASM9, visitor) {
                                @Override public void visitInsn(int opcode) {
                                    if (opcode == Opcodes.ARETURN) {
                                        visitLdcInsn(excluded.size());
                                        visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
                                        int index = 0;
                                        for (String version : excluded.stream().sorted().toList()) {
                                            visitInsn(Opcodes.DUP);
                                            visitLdcInsn(index++);
                                            visitLdcInsn(version);
                                            visitInsn(Opcodes.AASTORE);
                                        }
                                        visitMethodInsn(Opcodes.INVOKEVIRTUAL, "app/morphe/patcher/patch/Compatibility", "excluding",
                                            "([Ljava/lang/String;)Lapp/morphe/patcher/patch/Compatibility;", false);
                                    }
                                    super.visitInsn(opcode);
                                }
                            };
                        }
                    };
                    reader.accept(new ClassRemapper(filtering, remapper), 0);
                    data = writer.toByteArray();
                }
                output.putNextEntry(new ZipEntry(prefix + name));
                output.write(data);
                output.closeEntry();
            }
        }
    }
}
