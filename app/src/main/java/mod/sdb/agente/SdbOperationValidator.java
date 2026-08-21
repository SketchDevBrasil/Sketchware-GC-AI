package mod.sdb.agente;

import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import a.a.a.jC;

/** Preflight validation and conservative repair for agent operations. */
final class SdbOperationValidator {

    private static final Set<String> ENGINE_OPS = new HashSet<>(Arrays.asList(
            "add_component", "add_custom_block", "add_direct_code", "add_drawable",
            "add_icon_resource", "add_image", "add_image_view", "add_image_widget",
            "add_import", "add_list", "add_moreblock", "add_permission", "add_variable",
            "add_view_event", "add_widget", "create_activity", "create_java_file",
            "delete_custom_block", "delete_drawable", "delete_java_file", "delete_moreblock",
            "delete_palette", "edit_activity_layout", "edit_java_file", "edit_layout_xml",
            "enable_material3", "remove_permission", "remove_widget", "rename_widget", "set_custom_view", "update_custom_block",
            "update_moreblock", "update_widget"
    ));

    static final class Validation {
        final List<String> errors = new ArrayList<>();
        final List<String> corrections = new ArrayList<>();
        final LinkedHashSet<String> affectedXmls = new LinkedHashSet<>();
        final LinkedHashSet<String> affectedViewIds = new LinkedHashSet<>();

        boolean isValid() {
            return errors.isEmpty();
        }
    }

    private SdbOperationValidator() {
    }

    static Validation validate(String scId, List<SdbEditEngine.Operation> operations,
                               String currentXmlName, String defaultJavaName) {
        Validation result = new Validation();
        if (operations == null || operations.isEmpty()) {
            result.errors.add("Nenhuma operacao foi informada.");
            return result;
        }
        int skillOperations = 0;
        for (SdbEditEngine.Operation operation : operations) {
            if (operation != null && SdbSkillOperationEngine.canHandle(operation.op)) skillOperations++;
        }
        if (skillOperations > 0 && operations.size() != 1) {
            result.errors.add("Operacoes de Skill devem ser executadas sozinhas, fora de edicoes do projeto.");
            return result;
        }

        Map<String, Set<String>> simulatedViews = new HashMap<>();
        for (int index = 0; index < operations.size(); index++) {
            SdbEditEngine.Operation operation = operations.get(index);
            if (operation == null || operation.op == null || operation.op.trim().isEmpty()) {
                result.errors.add(label(index, "campo op ausente"));
                continue;
            }

            String originalOp = operation.op;
            operation.op = operation.op.trim().toLowerCase(Locale.US);
            if (!originalOp.equals(operation.op)) {
                result.corrections.add(label(index, "op normalizada para " + operation.op));
            }
            if ("edit_activity_layout".equals(operation.op)) {
                operation.op = "edit_layout_xml";
                result.corrections.add(label(index, "edit_activity_layout convertido para edit_layout_xml"));
            }

            if (!isSupported(operation.op)) {
                result.errors.add(label(index, "operacao nao suportada: " + operation.op));
                continue;
            }
            if (operation.data == null && !"refresh_project".equals(operation.op)) {
                operation.data = new SdbEditEngine.OperationData();
                result.corrections.add(label(index, "objeto data criado"));
            }

            SdbEditEngine.OperationData data = operation.data;
            normalizeOperationName(operation.op, data, index, result);
            if (data != null && needsJavaContext(operation.op)
                    && firstNonEmpty(data.java_name) == null
                    && firstNonEmpty(defaultJavaName) != null) {
                data.java_name = normalizeJava(defaultJavaName);
                result.corrections.add(label(index, "java_name inferido: " + data.java_name));
            }
            if (data != null && data.widget_id == null && data.id != null) {
                data.widget_id = data.id.trim();
                result.corrections.add(label(index, "data.id convertido para widget_id"));
            }
            if (data != null && "edit_layout_xml".equals(operation.op)
                    && data.xml_content == null && data.content != null) {
                data.xml_content = data.content;
                result.corrections.add(label(index, "data.content convertido para xml_content"));
            }
            if (data != null && data.attributes == null && data.params != null
                    && (isAddWidget(operation.op) || "update_widget".equals(operation.op))) {
                data.attributes = new HashMap<>();
                for (Map.Entry<String, Object> entry : data.params.entrySet()) {
                    data.attributes.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                result.corrections.add(label(index, "data.params convertido para attributes"));
            }

            if ("create_activity".equals(operation.op) && data != null) {
                String screen = firstNonEmpty(data.screen_name, data.activity_name, data.file_name, data.name);
                if (screen == null) {
                    result.errors.add(label(index, "screen_name ausente"));
                } else {
                    if (screen.endsWith("Activity")) screen = screen.substring(0, screen.length() - 8);
                    screen = screen.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                            .toLowerCase(Locale.US).replaceAll("[^a-z0-9_]", "_");
                    result.affectedXmls.add(normalizeXml(screen));
                }
            } else if ("create_layout_xml".equals(operation.op) && data != null) {
                String layout = firstNonEmpty(data.layout_name, data.name, data.file_name);
                if (layout != null) result.affectedXmls.add(normalizeXml(layout));
            }

            boolean changesLayout = isLayoutOperation(operation.op)
                    && !("add_icon_resource".equals(operation.op)
                    && (data == null || firstNonEmpty(data.target_view_id, data.view_id) == null));
            if (changesLayout) {
                String xml = firstNonEmpty(operation.xmlName,
                        data != null ? data.target_xml_name : null, currentXmlName);
                if (xml == null) {
                    result.errors.add(label(index, "xmlName ausente"));
                    continue;
                }
                xml = normalizeXml(xml);
                operation.xmlName = xml;
                if (data != null) data.target_xml_name = xml;
                result.affectedXmls.add(xml);

                if (data != null) normalizeWidgetAttributes(data, index, result);
                validateLayoutMutation(scId, operation, xml, index, simulatedViews, result);
            }

            validateRequiredFields(operation, index, result);
        }
        return result;
    }

    private static boolean isSupported(String op) {
        return ENGINE_OPS.contains(op)
                || SdbDirectFileEngine.isDirectOperation(op)
                || SdbProjectMutationEngine.canHandle(op)
                || SdbSkillOperationEngine.canHandle(op);
    }

    private static boolean isLayoutOperation(String op) {
        return "add_widget".equals(op) || "add_image".equals(op)
                || "add_image_view".equals(op) || "add_image_widget".equals(op)
                || "update_widget".equals(op) || "remove_widget".equals(op) || "rename_widget".equals(op)
                || "edit_layout_xml".equals(op) || "add_view_event".equals(op)
                || "set_custom_view".equals(op) || "add_icon_resource".equals(op);
    }

    private static void validateLayoutMutation(String scId, SdbEditEngine.Operation operation,
                                                 String xml, int index,
                                                 Map<String, Set<String>> simulatedViews,
                                                 Validation result) {
        SdbEditEngine.OperationData data = operation.data;
        Set<String> ids = simulatedViews.computeIfAbsent(xml, key -> loadViewIds(scId, key));
        String id = data == null ? null : firstNonEmpty(data.widget_id, data.view_id, data.target_view_id);
        if (id != null) result.affectedViewIds.add(id);

        if (isAddWidget(operation.op)) {
            if (id == null) {
                id = generateViewId(data, ids);
                data.widget_id = id;
                result.affectedViewIds.add(id);
                result.corrections.add(label(index, "widget_id gerado: " + id));
            }
            if (!isValidId(id)) {
                result.errors.add(label(index, "widget_id invalido: " + id));
                return;
            }
            if (ids.contains(id)) {
                result.errors.add(label(index, "widget duplicado em " + xml + ": " + id));
                return;
            }
            String parent = firstNonEmpty(data.parent_id, data.parent);
            if (parent != null && !"root".equals(parent) && !ids.contains(parent)) {
                result.errors.add(label(index, "parent inexistente em " + xml + ": " + parent));
                return;
            }
            ids.add(id);
        } else if ("rename_widget".equals(operation.op)) {
            String newId = data == null ? null : firstNonEmpty(data.new_id, data.name);
            if (id == null || newId == null) {
                result.errors.add(label(index, "rename_widget precisa de widget_id e new_id"));
            } else if (!ids.contains(id)) {
                result.errors.add(label(index, "widget nao encontrado em " + xml + ": " + id));
            } else if (!isValidId(newId)) {
                result.errors.add(label(index, "new_id invalido: " + newId));
            } else if (ids.contains(newId)) {
                result.errors.add(label(index, "new_id ja existe em " + xml + ": " + newId));
            } else {
                ids.remove(id);
                ids.add(newId);
                result.affectedViewIds.add(newId);
            }
        } else if ("update_widget".equals(operation.op) || "remove_widget".equals(operation.op)) {
            if (id == null) {
                result.errors.add(label(index, "widget_id ausente"));
            } else if (!ids.contains(id)) {
                result.errors.add(label(index, "widget nao encontrado em " + xml + ": " + id));
            } else if ("remove_widget".equals(operation.op)) {
                ids.remove(id);
            }
        } else if ("edit_layout_xml".equals(operation.op)) {
            if (data == null || firstNonEmpty(data.xml_content, data.content) == null) {
                result.errors.add(label(index, "xml_content ausente"));
                return;
            }
            try {
                String xmlContent = firstNonEmpty(data.xml_content, data.content);
                if (containsEditorShell(xmlContent)) {
                    result.errors.add(label(index, "XML inclui _coordinator/_app_bar/_toolbar; edite somente o conteudo da tela"));
                    return;
                }
                pro.sketchware.tools.ViewBeanParser parser =
                        new pro.sketchware.tools.ViewBeanParser(stripFence(xmlContent));
                parser.setSkipRoot(true);
                ArrayList<ViewBean> parsed = parser.parse();
                if (parser.getRootAttributes() == null || parser.getRootAttributes().first == null) {
                    result.errors.add(label(index, "XML sem ViewGroup raiz valida"));
                } else {
                    String rootName = pro.sketchware.tools.ViewBeanParser.getNameFromTag(
                            parser.getRootAttributes().first);
                    if (!isEditorSafeRoot(rootName)) {
                        result.errors.add(label(index, "raiz nao suportada pelo editor visual: " + rootName));
                        return;
                    }
                    int rootType = pro.sketchware.tools.ViewBeanParser.getViewTypeByClassName(
                            parser.getRootAttributes().first);
                    if (rootType != ViewBean.VIEW_TYPE_LAYOUT_LINEAR
                            && rootType != ViewBean.VIEW_TYPE_LAYOUT_RELATIVE
                            && rootType != ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW
                            && rootType != ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW) {
                        result.errors.add(label(index, "a raiz do XML deve ser um container de layout"));
                        return;
                    }
                    ids.clear();
                    for (ViewBean bean : parsed) if (bean != null && bean.id != null) ids.add(bean.id);
                    result.affectedViewIds.addAll(ids);
                }
            } catch (Exception error) {
                result.errors.add(label(index, "XML invalido: " + safeMessage(error)));
            }
        }
    }

    private static boolean containsEditorShell(String xml) {
        if (xml == null) return false;
        String lower = xml.toLowerCase(Locale.US);
        return lower.contains("@+id/_coordinator")
                || lower.contains("@+id/_app_bar")
                || lower.contains("@+id/_toolbar")
                || lower.contains("materialtoolbar")
                || lower.contains("appbarlayout");
    }

    private static boolean isEditorSafeRoot(String name) {
        return "LinearLayout".equals(name) || "RelativeLayout".equals(name)
                || "ScrollView".equals(name) || "HorizontalScrollView".equals(name);
    }

    private static void validateRequiredFields(SdbEditEngine.Operation operation, int index,
                                               Validation result) {
        SdbEditEngine.OperationData data = operation.data;
        String op = operation.op;
        if ("add_variable".equals(op) || "add_list".equals(op)) {
            String name = data == null ? null : firstNonEmpty(data.name);
            if (name == null) {
                result.errors.add(label(index, "name ausente"));
            } else if (!isValidId(name)) {
                result.errors.add(label(index, "nome Java invalido: " + name));
            }
            if (data == null || firstNonEmpty(data.java_name) == null) {
                result.errors.add(label(index, "java_name ausente"));
            }
        }
        if ("add_moreblock".equals(op) || "update_moreblock".equals(op)
                || "delete_moreblock".equals(op)) {
            String name = data == null ? null : firstNonEmpty(data.name);
            if (name == null) {
                result.errors.add(label(index, "name ausente"));
            } else if (!isValidId(name)) {
                result.errors.add(label(index, "nome de MoreBlock invalido: " + name));
            }
            if (!"delete_moreblock".equals(op) && data != null && data.code != null) {
                String bodyError = validateMethodBody(data.code);
                if (bodyError != null) result.errors.add(label(index, bodyError));
            }
        }
        if (("inject_code".equals(op) || "replace_code_block".equals(op)
                || "append_code_block".equals(op) || "add_direct_code".equals(op))
                && data != null && firstNonEmpty(data.code, data.content) == null) {
            result.errors.add(label(index, "code ausente"));
        }
        if (("write_file".equals(op) || "patch_file".equals(op)
                || "read_file".equals(op) || "write_project_file".equals(op)
                || "patch_project_file".equals(op) || "read_project_file".equals(op))
                && data != null && firstNonEmpty(data.path) == null) {
            result.errors.add(label(index, "path ausente"));
        }
        if ("patch_file".equals(op) || "patch_project_file".equals(op)) {
            if (data != null && firstNonEmpty(data.find) == null) {
                result.errors.add(label(index, "data.find ausente"));
            }
        }
        if ("repair_local_variable".equals(op)) {
            if (data == null || firstNonEmpty(data.name, data.var_name, data.variable_name) == null) {
                result.errors.add(label(index, "repair_local_variable precisa de name"));
            }
            if (data == null || firstNonEmpty(data.java_name) == null) {
                result.errors.add(label(index, "repair_local_variable precisa de java_name"));
            }
        }
        if ("repair_list_indices".equals(op)) {
            if (data == null || firstNonEmpty(data.name, data.list_name) == null) {
                result.errors.add(label(index, "repair_list_indices precisa de name"));
            }
            if (data == null || firstNonEmpty(data.variable_name, data.var_name) == null) {
                result.errors.add(label(index, "repair_list_indices precisa de variable_name"));
            }
            if (data == null || firstNonEmpty(data.java_name) == null) {
                result.errors.add(label(index, "repair_list_indices precisa de java_name"));
            }
        }
        if ("create_skill".equals(op) || "update_skill".equals(op)) {
            if (data == null || firstNonEmpty(data.skill_id, data.id) == null) {
                result.errors.add(label(index, op + " precisa de skill_id"));
            }
            if (data == null || firstNonEmpty(data.name) == null) {
                result.errors.add(label(index, op + " precisa de name"));
            }
            if (data == null || data.triggers == null || data.triggers.isEmpty()) {
                result.errors.add(label(index, op + " precisa de triggers"));
            }
            if (data == null || data.rules == null || data.rules.isEmpty()) {
                result.errors.add(label(index, op + " precisa de rules"));
            }
        }
    }

    private static String validateMethodBody(String code) {
        int depth = 0;
        boolean string = false;
        boolean character = false;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean escaped = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') { blockComment = false; i++; }
                continue;
            }
            if (string || character) {
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (string && c == '"') string = false;
                if (character && c == '\'') character = false;
                continue;
            }
            if (c == '/' && next == '/') { lineComment = true; i++; continue; }
            if (c == '/' && next == '*') { blockComment = true; i++; continue; }
            if (c == '"') { string = true; continue; }
            if (c == '\'') { character = true; continue; }
            if (c == '{') depth++;
            if (c == '}' && --depth < 0) {
                return "codigo do MoreBlock tenta fechar o metodo hospedeiro";
            }
        }
        if (depth != 0 || string || character || blockComment) {
            return "codigo do MoreBlock possui delimitadores desbalanceados";
        }
        return null;
    }

    private static void normalizeWidgetAttributes(SdbEditEngine.OperationData data, int index,
                                                   Validation result) {
        if (data.attributes == null || data.attributes.isEmpty()) return;
        Map<String, String> normalized = new HashMap<>();
        boolean changed = false;
        for (Map.Entry<String, String> entry : data.attributes.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue();
            if ("width".equals(key)) {
                key = "android:layout_width";
                changed = true;
            } else if ("height".equals(key)) {
                key = "android:layout_height";
                changed = true;
            } else if ("margin".equals(key)) {
                key = "android:layout_margin";
                changed = true;
            }
            if (!key.contains(":") && isAndroidViewAttribute(key)) {
                key = "android:" + key;
                changed = true;
            }
            if (("android:layout_width".equals(key) || "android:layout_height".equals(key)
                    || "android:padding".equals(key) || key.startsWith("android:layout_margin"))
                    && value != null && value.matches("-?\\d+")) {
                if ("-1".equals(value)) value = "match_parent";
                else if ("-2".equals(value)) value = "wrap_content";
                else value += "dp";
                changed = true;
            }
            normalized.put(key, value);
        }
        if (changed) {
            data.attributes = normalized;
            result.corrections.add(label(index, "atributos Android normalizados"));
        }
    }

    private static boolean isAndroidViewAttribute(String key) {
        return key.startsWith("layout_") || "text".equals(key) || "hint".equals(key)
                || "src".equals(key) || "background".equals(key) || "visibility".equals(key)
                || "orientation".equals(key) || "gravity".equals(key) || "padding".equals(key)
                || "textColor".equals(key) || "textSize".equals(key) || "scaleType".equals(key)
                || "adjustViewBounds".equals(key) || "elevation".equals(key);
    }

    private static Set<String> loadViewIds(String scId, String xml) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            ArrayList<ViewBean> beans = jC.a(scId).d(xml);
            if (beans != null) {
                for (ViewBean bean : beans) if (bean != null && bean.id != null) ids.add(bean.id);
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private static boolean isAddWidget(String op) {
        return "add_widget".equals(op) || "add_image".equals(op)
                || "add_image_view".equals(op) || "add_image_widget".equals(op);
    }

    private static String generateViewId(SdbEditEngine.OperationData data, Set<String> ids) {
        String prefix = data != null && data.widget_type == ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW
                ? "image" : "view";
        int suffix = 1;
        while (ids.contains(prefix + suffix)) suffix++;
        return prefix + suffix;
    }

    private static boolean isValidId(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private static boolean needsJavaContext(String op) {
        return "add_variable".equals(op) || "add_list".equals(op)
                || "add_component".equals(op) || "add_import".equals(op)
                || "inject_code".equals(op) || "add_view_event".equals(op)
                || "add_moreblock".equals(op) || "update_moreblock".equals(op)
                || "delete_moreblock".equals(op) || "repair_local_variable".equals(op)
                || "repair_list_indices".equals(op);
    }

    private static void normalizeOperationName(String op, SdbEditEngine.OperationData data,
                                               int index, Validation result) {
        if (data == null || firstNonEmpty(data.name) != null) return;
        String inferred = null;
        if ("add_variable".equals(op)) {
            inferred = firstNonEmpty(data.var_name, data.variable_name, data.id,
                    param(data, "name"), param(data, "var_name"), param(data, "variable_name"));
        } else if ("add_list".equals(op)) {
            inferred = firstNonEmpty(data.list_name, data.id,
                    param(data, "name"), param(data, "list_name"));
        } else if ("add_moreblock".equals(op) || "update_moreblock".equals(op)
                || "delete_moreblock".equals(op)) {
            inferred = firstNonEmpty(data.moreblock_name, data.block_name,
                    param(data, "name"), param(data, "moreblock_name"), param(data, "block_name"));
        }
        if (inferred != null) {
            data.name = inferred;
            result.corrections.add(label(index, "alias de nome convertido para data.name: " + inferred));
        }
    }

    private static String param(SdbEditEngine.OperationData data, String key) {
        if (data == null || data.params == null) return null;
        Object value = data.params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeJava(String javaName) {
        String clean = javaName.trim();
        return clean.endsWith(".java") ? clean : clean + ".java";
    }

    private static String normalizeXml(String xml) {
        String clean = xml.trim();
        return clean.endsWith(".xml") ? clean : clean + ".xml";
    }

    private static String stripFence(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.startsWith("```")) {
            int newline = clean.indexOf('\n');
            int end = clean.lastIndexOf("```");
            if (newline >= 0 && end > newline) clean = clean.substring(newline + 1, end).trim();
        }
        int start = clean.indexOf('<');
        return start > 0 ? clean.substring(start) : clean;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static String label(int index, String message) {
        return "#" + (index + 1) + " " + message;
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
