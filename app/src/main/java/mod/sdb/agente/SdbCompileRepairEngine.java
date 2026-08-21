package mod.sdb.agente;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.beans.ComponentBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.jC;

/** Adds missing model declarations that compiler-fix responses commonly overlook. */
final class SdbCompileRepairEngine {

    static final class Result {
        final String json;
        final ArrayList<String> repairs;

        Result(String json, ArrayList<String> repairs) {
            this.json = json;
            this.repairs = repairs;
        }
    }

    static final class ImmediateResult {
        final boolean complete;
        final ArrayList<String> repairs;

        ImmediateResult(boolean complete, ArrayList<String> repairs) {
            this.complete = complete;
            this.repairs = repairs;
        }
    }

    private SdbCompileRepairEngine() {
    }

    static ImmediateResult repairViewReferencesImmediately(String scId, String compileLog,
                                                            String defaultJavaName) {
        ArrayList<String> repairs = new ArrayList<>();
        if (scId == null || compileLog == null || defaultJavaName == null) {
            return new ImmediateResult(false, repairs);
        }
        boolean found = false;
        boolean complete = true;
        String javaName = normalizeJava(defaultJavaName);
        String xmlName = findXmlName(scId, javaName);
        for (String symbol : unresolvedSymbols(compileLog)) {
            int viewType = inferViewType(compileLog, symbol);
            if (viewType == -1 || xmlName == null || !modelHasView(scId, xmlName, symbol)) {
                complete = false;
                continue;
            }
            found = true;
            SdbProjectMutationEngine.Result result = SdbProjectMutationEngine
                    .repairViewReference(scId, javaName, symbol, viewType);
            if (result == null || !result.success) {
                complete = false;
                continue;
            }
            if (result.changed) repairs.add(result.message);
        }
        return new ImmediateResult(found && complete, repairs);
    }

    static ImmediateResult repairCompileIssuesImmediately(String scId, String compileLog,
                                                           String defaultJavaName) {
        ImmediateResult views = repairViewReferencesImmediately(scId, compileLog, defaultJavaName);
        ArrayList<String> repairs = new ArrayList<>(views.repairs);
        boolean found = views.complete || !views.repairs.isEmpty();
        boolean complete = views.complete;
        if (scId == null || compileLog == null || defaultJavaName == null) {
            return new ImmediateResult(complete, repairs);
        }
        for (ListIndexIssue issue : listIndexIssues(compileLog)) {
            found = true;
            SdbProjectMutationEngine.Result result = SdbProjectMutationEngine.repairListIndices(
                    scId, normalizeJava(defaultJavaName), issue.listName, issue.indexName);
            if (result == null || !result.success) {
                complete = false;
                continue;
            }
            complete = true;
            if (result.changed) repairs.add(result.message);
        }
        String bindingXml = bindingXmlName(compileLog);
        for (String duplicateId : duplicateBindingIds(compileLog)) {
            found = true;
            if (bindingXml == null) {
                complete = false;
                continue;
            }
            int removed = SdbProjectIntegrityGuard.removeDuplicateViewIds(
                    scId, bindingXml, duplicateId);
            if (removed <= 0) {
                complete = false;
                continue;
            }
            complete = true;
            repairs.add(removed + " widget(s) duplicado(s) removido(s) de "
                    + bindingXml + ": " + duplicateId);
        }
        return new ImmediateResult(found && complete, repairs);
    }

    static Result reinforce(String scId, String compileLog, String responseJson,
                            String defaultJavaName) {
        ArrayList<String> repairs = new ArrayList<>();
        if (compileLog == null || responseJson == null || defaultJavaName == null) {
            return new Result(responseJson, repairs);
        }
        try {
            String javaName = normalizeJava(defaultJavaName);
            String xmlName = findXmlName(scId, javaName);
            JSONArray operations = readOperations(responseJson);
            operations = removeMisclassifiedOperations(compileLog, operations, repairs);
            JSONArray structural = new JSONArray();
            JSONArray trailing = new JSONArray();

            addKnownMissingImports(compileLog, javaName, operations, structural, repairs);

            for (ListIndexIssue issue : listIndexIssues(compileLog)) {
                JSONObject data = new JSONObject();
                data.put("java_name", javaName);
                data.put("name", issue.listName);
                data.put("variable_name", issue.indexName);
                trailing.put(operation("repair_list_indices", null, data));
                repairs.add("Indice de lista convertido para int: " + issue.listName
                        + "[" + issue.indexName + "]");
            }

            for (String symbol : unresolvedSymbols(compileLog)) {
                String componentType = inferComponentType(compileLog, symbol);
                if (componentType != null
                        && !hasNamedOperation(operations, "add_component", symbol)
                        && !modelHasComponent(scId, javaName, symbol)) {
                    JSONObject data = new JSONObject();
                    data.put("java_name", javaName);
                    data.put("name", symbol);
                    data.put("component_type", componentType);
                    if ("SharedPreferences".equals(componentType)) data.put("param1", "config");
                    structural.put(operation("add_component", null, data));
                    repairs.add("Componente " + componentType + " criado: " + symbol);
                    continue;
                }

                String listType = inferListType(compileLog, symbol);
                if (listType != null
                        && !hasNamedOperation(operations, "add_list", symbol)
                        && !modelHasList(scId, javaName, symbol)) {
                    JSONObject data = new JSONObject();
                    data.put("java_name", javaName);
                    data.put("name", symbol);
                    data.put("list_type", listType);
                    structural.put(operation("add_list", null, data));
                    repairs.add("Lista " + listType + " criada: " + symbol);
                    continue;
                }

                int viewType = isExplicitViewSymbol(compileLog, symbol)
                        ? inferViewType(compileLog, symbol) : -1;
                if (viewType != -1 && xmlName != null) {
                    if (!hasWidgetOperation(operations, symbol)
                            && !modelHasView(scId, xmlName, symbol)) {
                        JSONObject data = new JSONObject();
                        data.put("widget_id", symbol);
                        data.put("widget_type", viewType);
                        JSONObject attributes = new JSONObject();
                        if (viewType == ViewBean.VIEW_TYPE_WIDGET_FAB) {
                            attributes.put("android:layout_width", "56dp");
                            attributes.put("android:layout_height", "56dp");
                        } else {
                            attributes.put("android:layout_width", "match_parent");
                            attributes.put("android:layout_height", "240dp");
                        }
                        data.put("attributes", attributes);
                        structural.put(operation("add_widget", xmlName, data));
                        repairs.add("Widget criado: " + symbol + " ("
                                + ViewBean.getViewTypeName(viewType) + ")");
                    }
                    continue;
                }

                String localUiType = inferLocalUiType(compileLog, symbol);
                if (localUiType != null) {
                    JSONObject data = new JSONObject();
                    data.put("java_name", javaName);
                    data.put("name", symbol);
                    data.put("var_type", localUiType);
                    trailing.put(operation("repair_local_variable", null, data));
                    repairs.add("Variavel local " + localUiType + " reparada: " + symbol);
                    continue;
                }

                String variableType = inferVariableType(compileLog, symbol);
                if (variableType != null
                        && !hasNamedOperation(operations, "add_variable", symbol)
                        && !modelHasVariable(scId, javaName, symbol)) {
                    JSONObject data = new JSONObject();
                    data.put("java_name", javaName);
                    data.put("name", symbol);
                    data.put("var_type", variableType);
                    structural.put(operation("add_variable", null, data));
                    repairs.add("Variavel " + variableType + " criada: " + symbol);
                }
            }

            if (structural.length() == 0 && trailing.length() == 0
                    && operations.toString().equals(readOperations(responseJson).toString())) {
                return new Result(responseJson, repairs);
            }
            JSONArray merged = new JSONArray();
            for (int i = 0; i < structural.length(); i++) merged.put(structural.get(i));
            for (int i = 0; i < operations.length(); i++) merged.put(operations.get(i));
            for (int i = 0; i < trailing.length(); i++) merged.put(trailing.get(i));
            String cleanResponse = responseJson.trim();
            JSONObject root = !cleanResponse.startsWith("[")
                    ? new JSONObject(cleanResponse) : new JSONObject();
            if (root.has("op")) root = new JSONObject();
            root.put("scId", scId);
            root.put("operations", merged);
            return new Result(root.toString(), repairs);
        } catch (Exception error) {
            android.util.Log.e("SdbCompileRepair", "Could not reinforce compiler repair", error);
            return new Result(responseJson, repairs);
        }
    }

    private static JSONArray readOperations(String json) throws Exception {
        String clean = json.trim();
        if (clean.startsWith("[")) return new JSONArray(clean);
        JSONObject root = new JSONObject(clean);
        JSONArray operations = root.optJSONArray("operations");
        if (operations != null) return operations;
        return new JSONArray().put(root);
    }

    private static JSONObject operation(String op, String xmlName, JSONObject data)
            throws Exception {
        JSONObject operation = new JSONObject();
        operation.put("op", op);
        if (xmlName != null) operation.put("xmlName", xmlName);
        operation.put("data", data);
        return operation;
    }

    private static Set<String> unresolvedSymbols(String log) {
        Set<String> symbols = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile(
                "\\b([A-Za-z_$][A-Za-z0-9_$]*) cannot be resolved(?: to a variable)?\\b")
                .matcher(log);
        while (matcher.find()) symbols.add(matcher.group(1));
        return symbols;
    }

    private static Set<String> duplicateBindingIds(String log) {
        Set<String> ids = new LinkedHashSet<>();
        if (log == null || !(log.contains("Duplicate field")
                || log.contains("Duplicate parameter")
                || log.contains("Duplicate local variable"))) return ids;
        Matcher matcher = Pattern.compile("\\bR\\.id\\.([A-Za-z_$][A-Za-z0-9_$]*)\\b")
                .matcher(log);
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    private static String bindingXmlName(String log) {
        if (log == null) return null;
        Matcher matcher = Pattern.compile("[/\\\\]databinding[/\\\\]([A-Za-z0-9_$]+)Binding\\.java")
                .matcher(log);
        if (!matcher.find()) return null;
        String base = matcher.group(1).replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(java.util.Locale.US);
        return base + ".xml";
    }

    private static final class ListIndexIssue {
        final String listName;
        final String indexName;

        ListIndexIssue(String listName, String indexName) {
            this.listName = listName;
            this.indexName = indexName;
        }
    }

    private static Set<ListIndexIssue> listIndexIssues(String log) {
        Set<ListIndexIssue> issues = new LinkedHashSet<>();
        if (log == null || !(log.contains("not applicable for the arguments (double)")
                || log.contains("cannot convert from double to int")
                || log.contains("lossy conversion from double to int"))) return issues;
        Matcher matcher = Pattern.compile(
                "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*"
                        + "(?:get|set|remove|add|listIterator|subList)\\s*\\(\\s*"
                        + "([A-Za-z_$][A-Za-z0-9_$]*)").matcher(log);
        Set<String> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            String key = matcher.group(1) + "\u0000" + matcher.group(2);
            if (seen.add(key)) issues.add(new ListIndexIssue(matcher.group(1), matcher.group(2)));
        }
        return issues;
    }

    private static String inferListType(String log, String symbol) {
        String quoted = Pattern.quote(symbol);
        boolean collectionUsage = Pattern.compile("\\b" + quoted + "\\s*=\\s*new\\s+ArrayList").matcher(log).find()
                || Pattern.compile("\\b" + quoted + "\\.(?:add|size|clear|get|remove|contains)\\s*\\(").matcher(log).find();
        if (!collectionUsage) return null;
        if (Pattern.compile("SimpleAdapter\\s*\\([^;]*\\b" + quoted + "\\b").matcher(log).find()
                || Pattern.compile("\\b" + quoted + "\\.add\\s*\\(\\s*(?:_item|new\\s+HashMap)").matcher(log).find()) {
            return "Map";
        }
        if (Pattern.compile("\\b" + quoted + "\\.add\\s*\\(\\s*\"").matcher(log).find()) return "String";
        if (Pattern.compile("\\b" + quoted + "\\.add\\s*\\(\\s*-?\\d+(?:\\.\\d+)?[dDfF]?\\s*\\)").matcher(log).find()) {
            return "Number";
        }
        if (Pattern.compile("\\b" + quoted + "\\.(?:add|size|get)\\s*\\(").matcher(log).find()
                && Pattern.compile("\\b(?:_item|HashMap)\\b").matcher(log).find()) return "Map";
        return null;
    }

    private static int inferViewType(String log, String symbol) {
        String lower = symbol.toLowerCase();
        String quoted = Pattern.quote(symbol);
        if (lower.startsWith("listview")
                || Pattern.compile("\\b" + quoted + "\\.(?:setAdapter|getAdapter)\\s*\\(").matcher(log).find()) {
            return ViewBean.VIEW_TYPE_WIDGET_LISTVIEW;
        }
        if (lower.startsWith("fab") || lower.contains("floatingactionbutton")) {
            return ViewBean.VIEW_TYPE_WIDGET_FAB;
        }
        if (lower.startsWith("button") || lower.startsWith("btn")) return ViewBean.VIEW_TYPE_WIDGET_BUTTON;
        if (lower.startsWith("textview") || lower.startsWith("txt")) return ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW;
        if (lower.startsWith("edittext") || lower.startsWith("edt")) return ViewBean.VIEW_TYPE_WIDGET_EDITTEXT;
        if (lower.startsWith("imageview") || lower.startsWith("img")) return ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW;
        if (Pattern.compile("\\b" + quoted + "\\.(?:setImageResource|setImageBitmap|setImageDrawable|setScaleType)\\s*\\(").matcher(log).find()) {
            return ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW;
        }
        if (Pattern.compile("\\b" + quoted + "\\.(?:setText|getText|setTextColor|setTextSize)\\s*\\(").matcher(log).find()) {
            return ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW;
        }
        if (Pattern.compile("\\b" + quoted + "\\.(?:setChecked|isChecked|setOnCheckedChangeListener)\\s*\\(").matcher(log).find()) {
            return ViewBean.VIEW_TYPE_WIDGET_CHECKBOX;
        }
        if (Pattern.compile("\\b" + quoted + "\\.(?:loadUrl|loadData|getSettings)\\s*\\(").matcher(log).find()) {
            return ViewBean.VIEW_TYPE_WIDGET_WEBVIEW;
        }
        if (Pattern.compile("\\b" + quoted + "\\.setOnClickListener\\s*\\(").matcher(log).find()) {
            return ViewBean.VIEW_TYPE_WIDGET_BUTTON;
        }
        return -1;
    }

    private static boolean isExplicitViewSymbol(String log, String symbol) {
        String lower = symbol.toLowerCase();
        if (lower.startsWith("listview") || lower.startsWith("fab")
                || lower.startsWith("button") || lower.startsWith("btn")
                || lower.startsWith("textview") || lower.startsWith("txt")
                || lower.startsWith("edittext") || lower.startsWith("edt")
                || lower.startsWith("imageview") || lower.startsWith("img")) return true;
        return Pattern.compile("\\bR\\.id\\." + Pattern.quote(symbol) + "\\b").matcher(log).find();
    }

    private static String inferComponentType(String log, String symbol) {
        String quoted = Pattern.quote(symbol);
        if (Pattern.compile("\\b" + quoted + "\\.(?:edit|contains|getString|getInt|getBoolean|getFloat|getLong|getAll)\\s*\\(")
                .matcher(log).find()) return "SharedPreferences";
        return null;
    }

    private static String inferLocalUiType(String log, String symbol) {
        if (isExplicitViewSymbol(log, symbol)) return null;
        String quoted = Pattern.quote(symbol);
        if (Pattern.compile("\\b" + quoted + "\\.(?:getText|setText|setHint|setInputType|setSelection)\\s*\\(")
                .matcher(log).find()) return "EditText";
        return null;
    }

    private static JSONArray removeMisclassifiedOperations(String log, JSONArray operations,
                                                            ArrayList<String> repairs) {
        JSONArray clean = new JSONArray();
        Set<String> unresolved = unresolvedSymbols(log);
        for (int i = 0; i < operations.length(); i++) {
            JSONObject operation = operations.optJSONObject(i);
            if (operation == null) continue;
            JSONObject data = operation.optJSONObject("data");
            String op = operation.optString("op");
            String name = data == null ? "" : data.optString("name");
            String widgetId = data == null ? "" : data.optString("widget_id", data.optString("id"));
            if ("add_widget".equals(op) && unresolved.contains(widgetId)
                    && !isExplicitViewSymbol(log, widgetId)) {
                repairs.add("Widget incorreto removido da correcao: " + widgetId);
                continue;
            }
            if ("add_component".equals(op) && data != null
                    && "dialog".equalsIgnoreCase(data.optString("component_type"))
                    && !unresolved.contains(name)) {
                repairs.add("Componente Dialog sem relacao com o erro removido: " + name);
                continue;
            }
            clean.put(operation);
        }
        return clean;
    }

    private static String inferVariableType(String log, String symbol) {
        String quoted = Pattern.quote(symbol);
        if (Pattern.compile("\\b" + quoted + "\\s*=\\s*(?:true|false)\\b").matcher(log).find()
                || Pattern.compile("(?:if|while)\\s*\\(\\s*!?\\s*" + quoted + "\\s*\\)").matcher(log).find()) {
            return "Boolean";
        }
        if (Pattern.compile("\\b" + quoted + "\\s*=\\s*\"").matcher(log).find()
                || Pattern.compile("\\b" + quoted + "\\.(?:substring|trim|toLowerCase|toUpperCase|equals|isEmpty|length)\\s*\\(").matcher(log).find()) {
            return "String";
        }
        if (Pattern.compile("\\b" + quoted + "\\.(?:put|putAll|containsKey|keySet)\\s*\\(").matcher(log).find()
                || Pattern.compile("\\b" + quoted + "\\s*=\\s*new\\s+HashMap").matcher(log).find()) {
            return "Map";
        }
        if (Pattern.compile("(?:\\+\\+|--)\\s*\\b" + quoted + "\\b|\\b" + quoted + "\\b\\s*(?:\\+\\+|--|[+\\-*/%]=)").matcher(log).find()
                || Pattern.compile("\\b" + quoted + "\\s*=\\s*-?\\d+(?:\\.\\d+)?[dDfF]?\\s*;").matcher(log).find()) {
            return "Number";
        }
        return null;
    }

    private static boolean hasNamedOperation(JSONArray operations, String opName, String name) {
        for (int i = 0; i < operations.length(); i++) {
            JSONObject operation = operations.optJSONObject(i);
            JSONObject data = operation == null ? null : operation.optJSONObject("data");
            if (operation != null && opName.equals(operation.optString("op"))
                    && data != null && name.equals(data.optString("name"))) return true;
        }
        return false;
    }

    private static boolean hasWidgetOperation(JSONArray operations, String id) {
        for (int i = 0; i < operations.length(); i++) {
            JSONObject operation = operations.optJSONObject(i);
            JSONObject data = operation == null ? null : operation.optJSONObject("data");
            if (operation != null && "add_widget".equals(operation.optString("op")) && data != null
                    && (id.equals(data.optString("widget_id")) || id.equals(data.optString("id")))) {
                return true;
            }
        }
        return false;
    }

    private static void addKnownMissingImports(String log, String javaName,
                                                JSONArray operations, JSONArray structural,
                                                ArrayList<String> repairs) throws Exception {
        String[][] knownImports = {
                {"SimpleAdapter", "android.widget.SimpleAdapter"},
                {"BaseAdapter", "android.widget.BaseAdapter"},
                {"ArrayList", "java.util.ArrayList"},
                {"HashMap", "java.util.HashMap"},
                {"ListView", "android.widget.ListView"},
                {"FloatingActionButton", "com.google.android.material.floatingactionbutton.FloatingActionButton"},
                {"ExtendedFloatingActionButton", "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton"},
                {"BottomSheetDialog", "com.google.android.material.bottomsheet.BottomSheetDialog"},
                {"TextInputLayout", "com.google.android.material.textfield.TextInputLayout"},
                {"TextInputEditText", "com.google.android.material.textfield.TextInputEditText"},
                {"MaterialButton", "com.google.android.material.button.MaterialButton"}
        };
        for (String[] item : knownImports) {
            String type = item[0];
            if (!Pattern.compile("\\b" + type
                    + " cannot be resolved to a type\\b").matcher(log).find()) continue;
            String importLine = "import " + item[1] + ";";
            if (hasImportOperation(operations, importLine)) continue;
            JSONObject data = new JSONObject();
            data.put("java_name", javaName);
            data.put("code", importLine);
            structural.put(operation("add_import", null, data));
            repairs.add("Import adicionado: " + item[1]);
        }
    }

    private static boolean hasImportOperation(JSONArray operations, String importLine) {
        for (int i = 0; i < operations.length(); i++) {
            JSONObject operation = operations.optJSONObject(i);
            JSONObject data = operation == null ? null : operation.optJSONObject("data");
            if (operation != null && "add_import".equals(operation.optString("op"))
                    && data != null && data.optString("code").contains(importLine)) return true;
        }
        return false;
    }

    private static boolean modelHasList(String scId, String javaName, String name) {
        try {
            for (int type = 1; type <= 3; type++) {
                ArrayList<String> values = jC.a(scId).d(javaName, type);
                if (values != null && values.contains(name)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean modelHasVariable(String scId, String javaName, String name) {
        try {
            for (int type = 0; type <= 3; type++) {
                ArrayList<String> values = jC.a(scId).e(javaName, type);
                if (values != null && values.contains(name)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean modelHasComponent(String scId, String javaName, String name) {
        try {
            ArrayList<ComponentBean> values = jC.a(scId).e(javaName);
            if (values != null) {
                for (ComponentBean value : values) {
                    if (value != null && name.equals(value.componentId)) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean modelHasView(String scId, String xmlName, String id) {
        try {
            ArrayList<ViewBean> views = jC.a(scId).d(xmlName);
            if (views != null) {
                for (ViewBean view : views) if (view != null && id.equals(view.id)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String findXmlName(String scId, String javaName) {
        try {
            ArrayList<ProjectFileBean> files = jC.b(scId).b();
            if (files != null) {
                for (ProjectFileBean file : files) {
                    if (file != null && javaName.equalsIgnoreCase(file.getJavaName())) {
                        return file.getXmlName();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String normalizeJava(String javaName) {
        String clean = javaName.trim();
        return clean.endsWith(".java") ? clean : clean + ".java";
    }
}
