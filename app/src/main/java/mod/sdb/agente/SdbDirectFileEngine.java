package mod.sdb.agente;

import android.content.Context;
import android.content.Intent;

import org.w3c.dom.Document;

import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import a.a.a.lC;
import a.a.a.yB;
import org.xml.sax.InputSource;

public class SdbDirectFileEngine {
    private static final Set<String> DIRECT_OPS = new HashSet<>(Arrays.asList(
            "read_file",
            "write_file",
            "patch_file",
            "read_project_file",
            "write_project_file",
            "patch_project_file",
            "create_java_class",
            "create_layout_xml",
            "create_drawable_xml",
            "refresh_project"
    ));

    private static final Set<String> DRAWABLE_ROOTS = new HashSet<>(Arrays.asList(
            "shape",
            "selector",
            "vector",
            "animated-vector",
            "layer-list",
            "inset",
            "ripple",
            "bitmap",
            "level-list",
            "transition",
            "adaptive-icon"
    ));

    private static StringBuilder resultBuffer = new StringBuilder();

    public static class Result {
        public final boolean success;
        public final boolean changed;
        public final String message;

        public Result(boolean success, boolean changed, String message) {
            this.success = success;
            this.changed = changed;
            this.message = message;
        }
    }

    public static boolean isDirectOperation(String op) {
        return op != null && DIRECT_OPS.contains(op);
    }

    public static String consumeSummary() {
        String summary = resultBuffer.toString().trim();
        resultBuffer = new StringBuilder();
        return summary;
    }

    public static Result apply(String scId, SdbEditEngine.Operation op) {
        if (scId == null || scId.trim().isEmpty() || op == null || op.op == null) {
            return fail("Operacao direta invalida.");
        }
        if ("refresh_project".equals(op.op)) {
            notifyRefresh(scId, null);
            return ok(false, "Interface do projeto atualizada.");
        }

        SdbEditEngine.OperationData data = op.data;
        if (data == null) return fail("Dados ausentes para " + op.op + ".");

        try {
            switch (op.op) {
                case "read_file":
                case "read_project_file":
                    return read(scId, data.path);
                case "write_file":
                case "write_project_file":
                    return write(scId, data.path, data.content, null);
                case "patch_file":
                case "patch_project_file":
                    return patch(scId, data.path, data.find, data.replace);
                case "create_java_class":
                    return createJavaClass(scId, data);
                case "create_layout_xml":
                    return createLayoutXml(scId, data);
                case "create_drawable_xml":
                    return createDrawableXml(scId, data);
                default:
                    return fail("Operacao direta nao suportada: " + op.op);
            }
        } catch (Exception e) {
            return fail("Falha em " + op.op + ": " + e.getMessage());
        }
    }

    private static Result read(String scId, String path) throws Exception {
        File file = resolveProjectFile(scId, path);
        if (!file.exists() || !file.isFile()) return fail("Arquivo nao encontrado: " + path);
        String content = pro.sketchware.utility.FileUtil.readFileIfExist(file.getAbsolutePath());
        String preview = content.length() > 1200 ? content.substring(0, 1200) + "\n..." : content;
        return ok(false, "Arquivo lido: " + relative(scId, file) + "\n```text\n" + preview + "\n```");
    }

    private static Result write(String scId, String path, String content, String validationKind) throws Exception {
        if (content == null) content = "";
        File file = resolveProjectFile(scId, path);
        protectGeneratedActivityOverride(scId, file);
        validateByPath(file, content, validationKind);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        pro.sketchware.utility.FileUtil.writeFile(file.getAbsolutePath(), content);
        String relativePath = relative(scId, file);
        boolean syncedLayout = syncLayoutFileToProject(scId, relativePath, content);
        notifyRefresh(scId, relativePath);
        String suffix = syncedLayout ? " e aplicado no design" : "";
        return ok(true, "Arquivo atualizado" + suffix + ": " + relativePath);
    }

    private static Result patch(String scId, String path, String find, String replace) throws Exception {
        if (find == null || find.isEmpty()) return fail("patch_file precisa de data.find.");
        if (replace == null) replace = "";
        File file = resolveProjectFile(scId, path);
        if (!file.exists() || !file.isFile()) return fail("Arquivo nao encontrado: " + path);
        protectGeneratedActivityOverride(scId, file);
        String original = pro.sketchware.utility.FileUtil.readFileIfExist(file.getAbsolutePath());
        if (!original.contains(find)) return fail("Trecho nao encontrado em " + relative(scId, file));
        String updated = original.replace(find, replace);
        validateByPath(file, updated, null);
        pro.sketchware.utility.FileUtil.writeFile(file.getAbsolutePath(), updated);
        String relativePath = relative(scId, file);
        boolean syncedLayout = syncLayoutFileToProject(scId, relativePath, updated);
        notifyRefresh(scId, relativePath);
        String suffix = syncedLayout ? " e aplicado no design" : "";
        return ok(true, "Patch aplicado" + suffix + ": " + relativePath);
    }

    private static Result createJavaClass(String scId, SdbEditEngine.OperationData data) throws Exception {
        String className = firstNonEmpty(data.class_name, data.file_name, data.name);
        if (className == null) return fail("create_java_class precisa de class_name.");
        className = sanitizeClassName(className);
        if (className == null) return fail("Nome de classe Java invalido.");
        if (SdbProjectMutationEngine.isGeneratedActivityJava(scId, className + ".java")) {
            return fail("Bloqueado: " + className + ".java e Activity gerada pelo Sketchware. Use inject_code/edit_activity_layout.");
        }
        String content = data.content;
        if (content == null || content.trim().isEmpty()) {
            String pkg = getProjectPackage(scId);
            content = (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n")
                    + "public class " + className + " {\n"
                    + "}\n";
        } else if (!getProjectPackage(scId).isEmpty() && !content.trim().startsWith("package ")) {
            content = "package " + getProjectPackage(scId) + ";\n\n" + content;
        }
        return write(scId, "files/java/" + className + ".java", content, "java");
    }

    private static Result createLayoutXml(String scId, SdbEditEngine.OperationData data) throws Exception {
        String name = sanitizeResourceName(firstNonEmpty(data.layout_name, data.name, data.file_name));
        if (name == null) return fail("create_layout_xml precisa de layout_name.");
        String content = firstNonEmpty(data.xml_content, data.content);
        if (content == null || content.trim().isEmpty()) return fail("create_layout_xml precisa de xml_content.");
        return write(scId, "files/resource/layout/" + name + ".xml", content, "layout");
    }

    private static Result createDrawableXml(String scId, SdbEditEngine.OperationData data) throws Exception {
        String name = sanitizeResourceName(firstNonEmpty(data.drawable_name, data.name, data.file_name));
        if (name == null) return fail("create_drawable_xml precisa de drawable_name.");
        String content = firstNonEmpty(data.xml_content, data.content);
        if (content == null || content.trim().isEmpty()) return fail("create_drawable_xml precisa de xml_content.");
        return write(scId, "files/resource/drawable/" + name + ".xml", content, "drawable");
    }

    private static void validateByPath(File file, String content, String validationKind) throws Exception {
        String name = file.getName().toLowerCase(Locale.US);
        String path = file.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.US);
        String kind = validationKind;
        if (kind == null) {
            if (name.endsWith(".xml") && path.contains("/files/resource/drawable/")) kind = "drawable";
            else if (name.endsWith(".xml") && path.contains("/files/resource/layout/")) kind = "layout";
            else if (name.endsWith(".xml")) kind = "xml";
            else if (name.endsWith(".java")) kind = "java";
        }
        if ("java".equals(kind)) {
            if (content.contains("<LinearLayout") || content.contains("<shape") || content.contains("</")) {
                throw new IllegalArgumentException("conteudo XML nao pode ser salvo como Java");
            }
            return;
        }
        if ("xml".equals(kind) || "layout".equals(kind) || "drawable".equals(kind)) {
            Document doc = parseXml(content);
            String root = doc.getDocumentElement().getTagName();
            if ("drawable".equals(kind) && !DRAWABLE_ROOTS.contains(root)) {
                throw new IllegalArgumentException("root drawable invalida: " + root);
            }
            if ("layout".equals(kind) && DRAWABLE_ROOTS.contains(root)) {
                throw new IllegalArgumentException("drawable nao pode ser salvo como layout");
            }
        }
    }

    private static Document parseXml(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
    }

    private static File resolveProjectFile(String scId, String path) throws Exception {
        if (path == null || path.trim().isEmpty()) throw new IllegalArgumentException("path vazio");
        File root = projectRoot(scId);
        String clean = path.trim().replace('\\', '/');
        String marker = ".sketchware/data/" + scId + "/";
        int markerIndex = clean.indexOf(marker);
        if (markerIndex >= 0) clean = clean.substring(markerIndex + marker.length());
        while (clean.startsWith("/")) clean = clean.substring(1);
        File target = new File(root, clean);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
            throw new SecurityException("caminho fora do projeto");
        }
        return target;
    }

    private static File projectRoot(String scId) {
        return new File(pro.sketchware.utility.FileUtil.getExternalStorageDir(), ".sketchware/data/" + scId);
    }

    private static String relative(String scId, File file) throws Exception {
        String root = projectRoot(scId).getCanonicalPath();
        String target = file.getCanonicalPath();
        if (target.startsWith(root + File.separator)) {
            return target.substring(root.length() + 1).replace('\\', '/');
        }
        return file.getName();
    }

    private static boolean syncLayoutFileToProject(String scId, String relativePath, String content) {
        String xmlName = layoutXmlNameFromPath(relativePath);
        if (xmlName == null) return false;
        return SdbEditEngine.applyLayoutXml(scId, xmlName, content);
    }

    private static String layoutXmlNameFromPath(String relativePath) {
        if (relativePath == null) return null;
        String clean = relativePath.replace('\\', '/');
        String lower = clean.toLowerCase(Locale.US);
        if (!lower.startsWith("files/resource/layout/") || !lower.endsWith(".xml")) {
            return null;
        }
        String name = clean.substring(clean.lastIndexOf('/') + 1);
        return name.endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
    }

    private static String sanitizeClassName(String name) {
        String simple = name.trim();
        if (simple.endsWith(".java")) simple = simple.substring(0, simple.length() - 5);
        if (simple.contains(".")) simple = simple.substring(simple.lastIndexOf('.') + 1);
        return simple.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? simple : null;
    }

    private static void protectGeneratedActivityOverride(String scId, File file) throws Exception {
        String path = relative(scId, file).replace('\\', '/');
        if (path.toLowerCase(Locale.US).startsWith("files/java/")
                && SdbProjectMutationEngine.isGeneratedActivityJava(scId, file.getName())) {
            throw new IllegalArgumentException(file.getName()
                    + " e Activity gerada pelo Sketchware. Use inject_code/edit_activity_layout.");
        }
    }

    private static String sanitizeResourceName(String name) {
        if (name == null) return null;
        String clean = name.trim();
        if (clean.endsWith(".xml")) clean = clean.substring(0, clean.length() - 4);
        clean = clean.toLowerCase(Locale.US);
        return clean.matches("[a-z][a-z0-9_]*") ? clean : null;
    }

    private static String getProjectPackage(String scId) {
        try {
            String pkg = yB.c(lC.b(scId), "my_sc_pkg_name");
            return pkg == null ? "" : pkg.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static void notifyRefresh(String scId, String path) {
        Context context = pro.sketchware.SketchApplication.getContext();
        if (context == null) return;
        Intent intent = new Intent(SdbEditEngine.ACTION_REFRESH_PROJECT);
        intent.putExtra("sc_id", scId);
        if (path != null) {
            intent.putExtra("path", path);
            String xmlName = layoutXmlNameFromPath(path);
            if (xmlName != null) intent.putExtra("xml_name", xmlName + ".xml");
        }
        context.sendBroadcast(intent);
    }

    private static Result ok(boolean changed, String message) {
        append(message);
        return new Result(true, changed, message);
    }

    private static Result fail(String message) {
        append("Falha: " + message);
        return new Result(false, false, message);
    }

    private static void append(String message) {
        if (message == null || message.isEmpty()) return;
        if (resultBuffer.length() > 0) resultBuffer.append('\n');
        resultBuffer.append("- ").append(message);
    }
}
