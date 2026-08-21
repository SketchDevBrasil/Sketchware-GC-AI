package mod.sdb.agente;

import android.content.Context;
import android.content.Intent;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import a.a.a.jC;
import a.a.a.oq;

/**
 * Central project mutation layer for SDBCodFlow.
 *
 * The agent must operate on Sketchware's internal project model, not on the
 * currently opened editor. This class keeps the Sketchware event-key rules in
 * one place so every chat entrypoint edits the same project state.
 */
public class SdbProjectMutationEngine {
    private static final String TAG_SYNC = "// SDBCodFlow Sync Block";
    private static final String TAG_APPEND = "// SDBCodFlow Append Block";

    private static final Set<String> OPS = new HashSet<>(Arrays.asList(
            "inject_code",
            "replace_code_block",
            "append_code_block",
            "repair_view_reference",
            "repair_local_variable",
            "repair_list_indices"
    ));

    enum CodeMode {
        UPSERT_SYNC,
        REPLACE_SYNC,
        APPEND
    }

    public static class Result {
        public final boolean success;
        public final boolean changed;
        public final String message;

        Result(boolean success, boolean changed, String message) {
            this.success = success;
            this.changed = changed;
            this.message = message;
        }
    }

    private static class EventTarget {
        String key;
        String targetId;
        String eventName;
        boolean activityEvent;
        boolean registerEvent;
    }

    public static boolean canHandle(String op) {
        return op != null && OPS.contains(op);
    }

    public static Result apply(String scId, SdbEditEngine.Operation op, String defaultJavaName) {
        if (op == null || op.op == null) return fail("Operacao ausente.");
        SdbEditEngine.OperationData data = op.data;
        if (data == null) return fail(op.op + " precisa de data.");

        String javaName = firstNonEmpty(data.java_name, attr(data, "java_name"), defaultJavaName);
        String eventName = firstNonEmpty(data.event_name, attr(data, "event_name"), "initializeLogic");
        String code = firstNonEmpty(data.code, attr(data, "code"), data.content, data.xml_content);

        if ("repair_view_reference".equals(op.op)) {
            String viewId = firstNonEmpty(data.view_id, data.widget_id, data.id,
                    attr(data, "view_id"), attr(data, "widget_id"));
            int viewType = data.widget_type != -1 ? data.widget_type : data.type;
            if (javaName == null) return fail("repair_view_reference precisa de java_name.");
            if (viewId == null) return fail("repair_view_reference precisa de view_id.");
            return repairViewReference(scId, javaName, viewId, viewType);
        }

        if ("repair_local_variable".equals(op.op)) {
            String variableName = firstNonEmpty(data.name, data.var_name, data.variable_name);
            String variableType = firstNonEmpty(data.var_type, "EditText");
            if (javaName == null) return fail("repair_local_variable precisa de java_name.");
            if (variableName == null) return fail("repair_local_variable precisa de name.");
            return repairLocalVariable(scId, javaName, variableName, variableType);
        }

        if ("repair_list_indices".equals(op.op)) {
            String listName = firstNonEmpty(data.name, data.list_name);
            String indexName = firstNonEmpty(data.variable_name, data.var_name);
            if (javaName == null) return fail("repair_list_indices precisa de java_name.");
            if (listName == null) return fail("repair_list_indices precisa de name.");
            if (indexName == null) return fail("repair_list_indices precisa de variable_name.");
            return repairListIndices(scId, javaName, listName, indexName);
        }

        if (code == null) return fail(op.op + " precisa de code.");
        if (javaName == null) return fail(op.op + " precisa de java_name.");

        CodeMode mode = CodeMode.UPSERT_SYNC;
        if ("replace_code_block".equals(op.op)) mode = CodeMode.REPLACE_SYNC;
        if ("append_code_block".equals(op.op)) mode = CodeMode.APPEND;

        return mutateCodeBlock(scId, javaName, eventName, code, mode);
    }

    static Result repairViewReference(String scId, String requestedJavaName,
                                      String viewId, int viewType) {
        try {
            String javaName = resolveJavaName(scId, requestedJavaName);
            if (javaName == null) return fail("Activity nao encontrada: " + requestedJavaName);
            if (!isJavaIdentifier(viewId)) return fail("ID de View invalido: " + viewId);
            a.a.a.eC projectData = jC.a(scId);
            java.util.HashMap<String, ArrayList<BlockBean>> logic = projectData.b(javaName);
            if (logic == null) return fail("Logica nao encontrada em " + javaName);

            String fqcn = viewClassName(viewType);
            String declaration = "final " + fqcn + " " + viewId + " = (" + fqcn
                    + ") findViewById(R.id." + viewId + ");";
            java.util.regex.Pattern usage = java.util.regex.Pattern.compile(
                    "(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote(viewId)
                            + "(?![A-Za-z0-9_$])");
            java.util.regex.Pattern declared = java.util.regex.Pattern.compile(
                    "(?m)\\b(?:final\\s+)?[A-Za-z_$][A-Za-z0-9_$.<>?, ]*\\s+"
                            + java.util.regex.Pattern.quote(viewId) + "\\s*(?:=|;)");
            ArrayList<String> changedKeys = new ArrayList<>();
            for (java.util.Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
                if (entry.getKey().startsWith("Import_")) continue;
                ArrayList<BlockBean> blocks = entry.getValue();
                if (blocks == null) continue;
                BlockBean insertionTarget = null;
                boolean used = false;
                boolean alreadyDeclared = false;
                for (BlockBean block : blocks) {
                    if (block == null || !"addSourceDirectly".equals(block.opCode)
                            || block.parameters == null || block.parameters.isEmpty()) continue;
                    String source = block.parameters.get(0);
                    String safeSource = source == null ? "" : source;
                    if (declared.matcher(safeSource).find()) {
                        alreadyDeclared = true;
                        break;
                    }
                    if (usage.matcher(safeSource).find()) {
                        used = true;
                        if (insertionTarget == null) insertionTarget = block;
                    }
                }
                if (!used || alreadyDeclared || insertionTarget == null) continue;
                String source = insertionTarget.parameters.get(0);
                insertionTarget.parameters.set(0, declaration + "\n"
                        + (source == null ? "" : source));
                changedKeys.add(entry.getKey());
            }
            for (String key : changedKeys) projectData.y(javaName, key);
            if (!changedKeys.isEmpty()) {
                projectData.k();
                notifyRefresh(scId, javaName, changedKeys.get(0));
            }
            return ok(!changedKeys.isEmpty(), changedKeys.isEmpty()
                    ? "Referencias de " + viewId + " ja estavam declaradas."
                    : "Referencia local de " + viewId + " adicionada em "
                    + changedKeys.size() + " evento(s)/MoreBlock(s).");
        } catch (Exception error) {
            android.util.Log.e("SdbMutationEngine", "View reference repair failed", error);
            return fail(error.getMessage() != null ? error.getMessage() : error.toString());
        }
    }

    static Result repairLocalVariable(String scId, String requestedJavaName,
                                      String variableName, String variableType) {
        try {
            String javaName = resolveJavaName(scId, requestedJavaName);
            if (javaName == null) return fail("Activity nao encontrada: " + requestedJavaName);
            if (!isJavaIdentifier(variableName)) return fail("Variavel local invalida: " + variableName);
            String activityClass = javaName.endsWith(".java")
                    ? javaName.substring(0, javaName.length() - 5) : javaName;
            int slash = Math.max(activityClass.lastIndexOf('/'), activityClass.lastIndexOf('.'));
            if (slash >= 0) activityClass = activityClass.substring(slash + 1);
            String fqcn;
            if ("TextView".equalsIgnoreCase(variableType)) fqcn = "android.widget.TextView";
            else fqcn = "android.widget.EditText";
            String declaration = "final " + fqcn + " " + variableName + " = new " + fqcn
                    + "(" + activityClass + ".this);";
            java.util.regex.Pattern usage = java.util.regex.Pattern.compile(
                    "(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote(variableName)
                            + "(?![A-Za-z0-9_$])");
            java.util.regex.Pattern declared = java.util.regex.Pattern.compile(
                    "(?m)\\b(?:final\\s+)?[A-Za-z_$][A-Za-z0-9_$.<>?, ]*\\s+"
                            + java.util.regex.Pattern.quote(variableName) + "\\s*(?:=|;)");
            a.a.a.eC projectData = jC.a(scId);
            java.util.HashMap<String, ArrayList<BlockBean>> logic = projectData.b(javaName);
            if (logic == null) return fail("Logica nao encontrada em " + javaName);
            ArrayList<String> changedKeys = new ArrayList<>();
            for (java.util.Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
                if (entry.getKey().startsWith("Import_")) continue;
                if (entry.getValue() == null) continue;
                BlockBean target = null;
                boolean used = false;
                boolean alreadyDeclared = false;
                for (BlockBean block : entry.getValue()) {
                    if (block == null || !"addSourceDirectly".equals(block.opCode)
                            || block.parameters == null || block.parameters.isEmpty()) continue;
                    String source = block.parameters.get(0);
                    String safe = source == null ? "" : source;
                    if (declared.matcher(safe).find()) { alreadyDeclared = true; break; }
                    if (usage.matcher(safe).find()) { used = true; if (target == null) target = block; }
                }
                if (!used || alreadyDeclared || target == null) continue;
                String source = target.parameters.get(0);
                target.parameters.set(0, declaration + "\n" + (source == null ? "" : source));
                changedKeys.add(entry.getKey());
            }
            for (String key : changedKeys) projectData.y(javaName, key);
            if (!changedKeys.isEmpty()) projectData.k();
            return ok(!changedKeys.isEmpty(), changedKeys.isEmpty()
                    ? "Variavel local " + variableName + " ja estava declarada."
                    : "Variavel local " + variableName + " declarada em "
                    + changedKeys.size() + " evento(s)/MoreBlock(s).");
        } catch (Exception error) {
            return fail(error.getMessage() != null ? error.getMessage() : error.toString());
        }
    }

    static Result repairListIndices(String scId, String requestedJavaName,
                                    String listName, String indexName) {
        try {
            String javaName = resolveJavaName(scId, requestedJavaName);
            if (javaName == null) return fail("Activity nao encontrada: " + requestedJavaName);
            if (!isJavaIdentifier(listName)) return fail("Nome de lista invalido: " + listName);
            if (!isJavaIdentifier(indexName)) return fail("Indice de lista invalido: " + indexName);

            a.a.a.eC projectData = jC.a(scId);
            java.util.HashMap<String, ArrayList<BlockBean>> logic = projectData.b(javaName);
            if (logic == null) return fail("Logica nao encontrada em " + javaName);

            // Numeric MoreBlock parameters are emitted as double by Sketchware, while
            // every positional List API requires an int. Match only a bare parameter,
            // so an existing cast remains untouched on repeated repair passes.
            java.util.regex.Pattern positionalCall = java.util.regex.Pattern.compile(
                    "((?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote(listName)
                            + "\\s*\\.\\s*(?:get|set|remove|add|listIterator|subList)"
                            + "\\s*\\(\\s*)"
                            + java.util.regex.Pattern.quote(indexName)
                            + "(?=\\s*[,\\)])");
            ArrayList<String> changedKeys = new ArrayList<>();
            int changedBlocks = 0;
            for (java.util.Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
                if (entry.getKey().startsWith("Import_") || entry.getValue() == null) continue;
                for (BlockBean block : entry.getValue()) {
                    if (block == null || !"addSourceDirectly".equals(block.opCode)
                            || block.parameters == null || block.parameters.isEmpty()) continue;
                    String before = block.parameters.get(0);
                    if (before == null || before.isEmpty()) continue;
                    String after = positionalCall.matcher(before)
                            .replaceAll("$1(int) " + java.util.regex.Matcher.quoteReplacement(indexName));
                    if (!before.equals(after)) {
                        block.parameters.set(0, after);
                        changedBlocks++;
                        if (!changedKeys.contains(entry.getKey())) changedKeys.add(entry.getKey());
                    }
                }
            }
            for (String key : changedKeys) projectData.y(javaName, key);
            if (!changedKeys.isEmpty()) {
                projectData.k();
                notifyRefresh(scId, javaName, changedKeys.get(0));
            }
            return ok(!changedKeys.isEmpty(), changedKeys.isEmpty()
                    ? "Indices de " + listName + " ja estavam tipados corretamente."
                    : "Indice " + indexName + " convertido para int em " + changedBlocks
                    + " bloco(s) que usam " + listName + ".");
        } catch (Exception error) {
            android.util.Log.e("SdbMutationEngine", "List index repair failed", error);
            return fail(error.getMessage() != null ? error.getMessage() : error.toString());
        }
    }

    private static String viewClassName(int viewType) {
        switch (viewType) {
            case ViewBean.VIEW_TYPE_WIDGET_LISTVIEW: return "android.widget.ListView";
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON: return "android.widget.Button";
            case ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW: return "android.widget.TextView";
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT: return "android.widget.EditText";
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW: return "android.widget.ImageView";
            case ViewBean.VIEW_TYPE_WIDGET_CHECKBOX: return "android.widget.CheckBox";
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH: return "android.widget.Switch";
            case ViewBean.VIEW_TYPE_WIDGET_WEBVIEW: return "android.webkit.WebView";
            case ViewBean.VIEW_TYPE_WIDGET_FAB:
                return "com.google.android.material.floatingactionbutton.FloatingActionButton";
            default: return "android.view.View";
        }
    }

    public static Result injectCode(String scId, String javaName, String eventName, String code, boolean replace) {
        return mutateCodeBlock(scId, javaName, eventName, code, replace ? CodeMode.REPLACE_SYNC : CodeMode.UPSERT_SYNC);
    }

    public static int synchronizeVisibleEvents(String scId) {
        int added = 0;
        try {
            a.a.a.eC projectData = jC.a(scId);
            ArrayList<ProjectFileBean> files = jC.b(scId).b();
            if (files == null) return 0;
            for (ProjectFileBean file : files) {
                if (file == null || file.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) continue;
                String javaName = file.getJavaName();
                java.util.HashMap<String, ArrayList<BlockBean>> logic = projectData.b(javaName);
                if (logic == null) continue;
                for (java.util.Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
                    EventTarget target = resolveEventTarget(entry.getKey());
                    if (target == null || "initializeLogic".equals(target.eventName)
                            || hasRegisteredEvent(projectData, javaName, target)) continue;
                    if (ensureVisibleEvent(scId, projectData, javaName, target)) added++;
                }
            }
            if (added > 0) projectData.k();
        } catch (Exception error) {
            android.util.Log.e("SdbMutationEngine", "event visibility sync failed", error);
        }
        return added;
    }

    public static int repairNestedMethods(String scId, String requestedJavaName) {
        int repaired = 0;
        try {
            String javaName = resolveJavaName(scId, requestedJavaName);
            if (javaName == null) return 0;
            a.a.a.eC projectData = jC.a(scId);
            java.util.HashMap<String, ArrayList<BlockBean>> logic = projectData.b(javaName);
            if (logic == null) return 0;

            ArrayList<ExtractedMethod> methods = new ArrayList<>();
            ArrayList<String> changedKeys = new ArrayList<>();
            for (java.util.Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
                if (entry.getKey().endsWith("_moreBlock")) continue;
                for (BlockBean block : entry.getValue()) {
                    if (block == null || !"addSourceDirectly".equals(block.opCode)
                            || block.parameters == null || block.parameters.isEmpty()) continue;
                    MethodExtraction extraction = extractNestedVoidMethods(block.parameters.get(0));
                    if (extraction.methods.isEmpty()) continue;
                    block.parameters.set(0, extraction.remainingCode);
                    methods.addAll(extraction.methods);
                    if (!changedKeys.contains(entry.getKey())) changedKeys.add(entry.getKey());
                }
            }
            if (methods.isEmpty()) return 0;

            for (java.util.Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
                for (BlockBean block : entry.getValue()) {
                    if (block == null || !"addSourceDirectly".equals(block.opCode)
                            || block.parameters == null || block.parameters.isEmpty()) continue;
                    String source = block.parameters.get(0);
                    for (ExtractedMethod method : methods) {
                        source = renameMethodCalls(source, method.name);
                    }
                    block.parameters.set(0, source);
                }
            }
            for (String key : changedKeys) projectData.y(javaName, key);
            projectData.k();

            for (ExtractedMethod method : methods) {
                String body = method.body;
                for (ExtractedMethod candidate : methods) {
                    body = renameMethodCalls(body, candidate.name);
                }
                SdbEditEngine.updateMoreBlockAndCode(scId, javaName,
                        method.name, method.name, body);
                repaired++;
            }
        } catch (Exception error) {
            android.util.Log.e("SdbMutationEngine", "nested method repair failed", error);
        }
        return repaired;
    }

    public static int synchronizeMoreBlockCalls(String scId, String requestedJavaName) {
        int changedBlocks = 0;
        try {
            String javaName = resolveJavaName(scId, requestedJavaName);
            if (javaName == null) return 0;
            a.a.a.eC projectData = jC.a(scId);
            java.util.HashMap<String, ArrayList<BlockBean>> logic = projectData.b(javaName);
            if (logic == null) return 0;

            java.util.LinkedHashSet<String> moreBlockNames = new java.util.LinkedHashSet<>();
            ArrayList<android.util.Pair<String, String>> definitions = projectData.i(javaName);
            if (definitions != null) {
                for (android.util.Pair<String, String> definition : definitions) {
                    if (definition == null || definition.first == null) continue;
                    String name = mod.hey.studios.moreblock.ReturnMoreblockManager
                            .getMbName(definition.first);
                    if (isJavaIdentifier(name)) moreBlockNames.add(name);
                }
            }
            // Also recover definitions whose body exists but whose visual definition was
            // created in a previous partial migration.
            for (String key : logic.keySet()) {
                if (!key.endsWith("_moreBlock")) continue;
                String name = key.substring(0, key.length() - "_moreBlock".length());
                if (isJavaIdentifier(name)) moreBlockNames.add(name);
            }
            if (moreBlockNames.isEmpty()) return 0;

            ArrayList<String> changedKeys = new ArrayList<>();
            for (java.util.Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
                if (entry.getValue() == null) continue;
                for (BlockBean block : entry.getValue()) {
                    if (block == null || !"addSourceDirectly".equals(block.opCode)
                            || block.parameters == null || block.parameters.isEmpty()) continue;
                    String before = block.parameters.get(0);
                    String after = before;
                    for (String name : moreBlockNames) after = renameMethodCalls(after, name);
                    if (before == null ? after != null : !before.equals(after)) {
                        block.parameters.set(0, after);
                        changedBlocks++;
                        if (!changedKeys.contains(entry.getKey())) changedKeys.add(entry.getKey());
                    }
                }
            }
            for (String key : changedKeys) projectData.y(javaName, key);
            if (!changedKeys.isEmpty()) projectData.k();
        } catch (Exception error) {
            android.util.Log.e("SdbMutationEngine", "MoreBlock call synchronization failed", error);
        }
        return changedBlocks;
    }

    public static int normalizeMoreBlocks(String scId, String requestedJavaName) {
        int repairs = 0;
        try {
            String javaName = resolveJavaName(scId, requestedJavaName);
            if (javaName == null) return 0;
            a.a.a.eC projectData = jC.a(scId);
            ArrayList<android.util.Pair<String, String>> definitions = projectData.i(javaName);
            if (definitions == null || definitions.isEmpty()) return 0;

            java.util.LinkedHashMap<String, NormalizedMoreBlock> normalized =
                    new java.util.LinkedHashMap<>();
            ArrayList<String> oldKeys = new ArrayList<>();
            for (android.util.Pair<String, String> definition :
                    new ArrayList<>(definitions)) {
                if (definition == null || definition.first == null) continue;
                String rawStorageName = definition.first.trim();
                String storageName = normalizeMoreBlockStorageName(rawStorageName);
                String baseName = mod.hey.studios.moreblock.ReturnMoreblockManager
                        .getMbName(storageName);
                String spec = normalizeMoreBlockSpec(baseName, definition.second,
                        rawStorageName);
                String oldKey = rawStorageName + "_moreBlock";
                oldKeys.add(oldKey);
                ArrayList<BlockBean> body = projectData.a(javaName, oldKey);
                if (body == null) body = new ArrayList<>();
                repairs += normalizeMoreBlockBodyParameters(body, spec);

                NormalizedMoreBlock candidate = new NormalizedMoreBlock();
                candidate.storageName = storageName;
                candidate.baseName = baseName;
                candidate.spec = spec;
                candidate.body = body;
                NormalizedMoreBlock current = normalized.get(baseName);
                if (current == null) {
                    normalized.put(baseName, candidate);
                } else {
                    repairs++;
                    // Corrupted updates may leave the parameterized definition empty and
                    // the duplicate non-parameterized definition holding the real body.
                    // Merge both dimensions independently so neither information is lost.
                    if (moreBlockParameterCount(candidate.spec)
                            > moreBlockParameterCount(current.spec)) {
                        current.spec = candidate.spec;
                    }
                    if ((current.body == null || current.body.isEmpty())
                            && candidate.body != null && !candidate.body.isEmpty()) {
                        current.body = candidate.body;
                    }
                }
                if (!rawStorageName.equals(storageName)
                        || !safeEquals(definition.second, spec)) repairs++;
            }
            if (repairs == 0) return 0;

            definitions.clear();
            java.util.LinkedHashSet<String> canonicalKeys = new java.util.LinkedHashSet<>();
            for (NormalizedMoreBlock item : normalized.values()) {
                repairs += normalizeMoreBlockBodyParameters(item.body, item.spec);
                definitions.add(new android.util.Pair<>(item.storageName, item.spec));
                String key = item.storageName + "_moreBlock";
                canonicalKeys.add(key);
                projectData.a(javaName, key, item.body);
                projectData.y(javaName, key);
            }
            for (String oldKey : oldKeys) {
                if (canonicalKeys.contains(oldKey)) continue;
                projectData.a(javaName, oldKey, new ArrayList<>());
                projectData.y(javaName, oldKey);
            }
            projectData.k();
        } catch (Exception error) {
            android.util.Log.e("SdbMutationEngine", "MoreBlock normalization failed", error);
        }
        return repairs;
    }

    private static class NormalizedMoreBlock {
        String storageName;
        String baseName;
        String spec;
        ArrayList<BlockBean> body;
    }

    private static int moreBlockScore(NormalizedMoreBlock item) {
        int score = item.body == null || item.body.isEmpty() ? 0 : 100;
        score += moreBlockParameterCount(item.spec) * 10;
        if (item.storageName != null && !item.storageName.startsWith("_")) score++;
        return score;
    }

    private static int moreBlockParameterCount(String spec) {
        int count = 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("%[sdbm](?:\\.[A-Za-z_$][A-Za-z0-9_$]*)?")
                .matcher(spec == null ? "" : spec);
        while (matcher.find()) count++;
        return count;
    }

    static String normalizeMoreBlockStorageName(String rawName) {
        String raw = rawName == null ? "" : rawName.trim();
        String typeSuffix = "";
        int typeStart = raw.indexOf('[');
        int typeEnd = raw.lastIndexOf(']');
        if (typeStart >= 0 && typeEnd > typeStart) {
            typeSuffix = raw.substring(typeStart, typeEnd + 1);
            raw = raw.substring(0, typeStart);
        }
        int parameter = raw.indexOf('%');
        if (parameter >= 0) raw = raw.substring(0, parameter);
        raw = raw.trim().replaceFirst("^_+", "");
        if (raw.contains(" ")) raw = raw.substring(0, raw.indexOf(' '));
        raw = raw.replaceAll("[^A-Za-z0-9_$]", "");
        if (raw.isEmpty()) raw = "moreBlock";
        if (!Character.isJavaIdentifierStart(raw.charAt(0))) raw = "moreBlock_" + raw;
        return raw + typeSuffix;
    }

    static String normalizeMoreBlockSpec(String storageName, String rawSpec,
                                         String rawName) {
        String baseName = mod.hey.studios.moreblock.ReturnMoreblockManager
                .getMbName(normalizeMoreBlockStorageName(storageName));
        String source = rawSpec == null ? "" : rawSpec.trim();
        String remainder = "";
        int space = source.indexOf(' ');
        if (space >= 0) remainder = source.substring(space + 1).trim();
        if (!remainder.contains("%") && rawName != null && rawName.contains("%")) {
            remainder = rawName.substring(rawName.indexOf('%')).trim();
        }
        if (source.startsWith("%")) remainder = source;
        remainder = remainder.replaceAll("%([sdb])(?!\\.)", "%$1.$1");
        return remainder.isEmpty() ? baseName : baseName + " " + remainder;
    }

    static String normalizeMoreBlockCode(String code, String spec) {
        String normalized = code;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("%[sdb]\\.([A-Za-z_$][A-Za-z0-9_$]*)")
                .matcher(spec == null ? "" : spec);
        while (matcher.find()) {
            normalized = prefixIdentifierOutsideLiterals(normalized, matcher.group(1));
        }
        return normalized;
    }

    private static int normalizeMoreBlockBodyParameters(ArrayList<BlockBean> body,
                                                         String spec) {
        int changed = 0;
        if (body == null) return changed;
        for (BlockBean block : body) {
            if (block == null || !"addSourceDirectly".equals(block.opCode)
                    || block.parameters == null || block.parameters.isEmpty()) continue;
            String before = block.parameters.get(0);
            String after = normalizeMoreBlockCode(before, spec);
            if (!safeEquals(before, after)) {
                block.parameters.set(0, after);
                changed++;
            }
        }
        return changed;
    }

    private static String prefixIdentifierOutsideLiterals(String source, String identifier) {
        if (source == null || source.isEmpty()) return source;
        StringBuilder out = new StringBuilder(source.length() + 8);
        boolean string = false, character = false, lineComment = false, blockComment = false;
        boolean escaped = false;
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) {
                out.append(c); i++;
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                out.append(c); i++;
                if (c == '*' && next == '/') { out.append(next); i++; blockComment = false; }
                continue;
            }
            if (string || character) {
                out.append(c); i++;
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (string && c == '"') string = false;
                if (character && c == '\'') character = false;
                continue;
            }
            if (c == '/' && next == '/') { out.append(c).append(next); i += 2; lineComment = true; continue; }
            if (c == '/' && next == '*') { out.append(c).append(next); i += 2; blockComment = true; continue; }
            if (c == '"') { out.append(c); i++; string = true; continue; }
            if (c == '\'') { out.append(c); i++; character = true; continue; }
            if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) end++;
                String token = source.substring(i, end);
                int previous = i - 1;
                while (previous >= 0 && Character.isWhitespace(source.charAt(previous))) previous--;
                if (token.equals(identifier) && (previous < 0 || source.charAt(previous) != '.')) {
                    out.append('_');
                }
                out.append(token);
                i = end;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean isJavaIdentifier(String value) {
        return value != null && value.matches("[A-Za-z_$][A-Za-z0-9_$]*");
    }

    private static class ExtractedMethod {
        String name;
        String body;
    }

    private static class MethodExtraction {
        String remainingCode;
        final ArrayList<ExtractedMethod> methods = new ArrayList<>();
    }

    private static MethodExtraction extractNestedVoidMethods(String source) {
        MethodExtraction result = new MethodExtraction();
        result.remainingCode = source == null ? "" : source;
        if (source == null || source.isEmpty()) return result;
        java.util.regex.Pattern declaration = java.util.regex.Pattern.compile(
                "(?m)(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?void\\s+"
                        + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(\\s*\\)\\s*\\{");
        java.util.regex.Matcher matcher = declaration.matcher(source);
        StringBuilder remaining = new StringBuilder();
        int cursor = 0;
        while (matcher.find(cursor)) {
            int close = findClosingBrace(source, matcher.end() - 1);
            if (close < 0) break;
            remaining.append(source, cursor, matcher.start());
            ExtractedMethod method = new ExtractedMethod();
            method.name = matcher.group(1);
            method.body = source.substring(matcher.end(), close).trim();
            result.methods.add(method);
            cursor = close + 1;
        }
        if (result.methods.isEmpty()) return result;
        remaining.append(source.substring(cursor));
        result.remainingCode = remaining.toString().trim();
        for (ExtractedMethod method : result.methods) {
            result.remainingCode = renameMethodCalls(result.remainingCode, method.name);
        }
        return result;
    }

    private static int findClosingBrace(String source, int openingBrace) {
        int depth = 0;
        boolean string = false, character = false, lineComment = false, blockComment = false, escaped = false;
        for (int i = openingBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
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
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static String renameMethodCalls(String source, String methodName) {
        if (source == null || methodName == null) return source;
        return source.replaceAll("(?<![A-Za-z0-9_$.])" + java.util.regex.Pattern.quote(methodName)
                + "\\s*\\(", "_" + methodName + "(");
    }

    public static Result mutateCodeBlock(String scId, String requestedJavaName, String eventNameOrKey,
                                         String code, CodeMode mode) {
        try {
            String javaName = resolveJavaName(scId, requestedJavaName);
            if (javaName == null || javaName.trim().isEmpty()) {
                return fail("Tela/Activity nao encontrada: " + requestedJavaName);
            }
            if (!"Import".equals(eventNameOrKey)) {
                ImportExtraction extraction = extractImports(code);
                if (!extraction.imports.isEmpty()) {
                    Result importResult = mutateCodeBlock(scId, javaName, "Import",
                            extraction.imports, CodeMode.UPSERT_SYNC);
                    if (!importResult.success) return importResult;
                    code = extraction.body;
                    if (code.trim().isEmpty()) {
                        return ok(true, "Imports adicionados em " + javaName);
                    }
                }
            }
            EventTarget target = resolveEventTarget(eventNameOrKey);
            if (target == null || target.key == null) {
                return fail("Evento invalido: " + eventNameOrKey);
            }

            a.a.a.eC projectData = jC.a(scId);
            boolean visibleEventExpected = ensureVisibleEvent(scId, projectData, javaName, target);

            ArrayList<BlockBean> blocks = projectData.a(javaName, target.key);
            if (blocks == null) {
                blocks = new ArrayList<>();
                projectData.a(javaName, target.key, blocks);
            }

            if (mode == CodeMode.REPLACE_SYNC) {
                replaceOrAddSyncBlock(blocks, code, true);
            } else if (mode == CodeMode.APPEND) {
                blocks.add(createDirectBlock(blocks, TAG_APPEND + "\n" + code));
            } else if ("Import".equals(target.eventName)) {
                mergeImportBlock(blocks, code);
            } else {
                replaceOrAddSyncBlock(blocks, code, false);
            }

            projectData.y(javaName, target.key);
            projectData.k();
            if (visibleEventExpected && !hasRegisteredEvent(projectData, javaName, target)) {
                return fail("O evento visual " + target.key + " nao foi registrado.");
            }
            if ("Import".equals(target.eventName)) {
                ArrayList<BlockBean> persisted = projectData.a(javaName, target.key);
                if (!hasDirectCode(persisted, code) || !hasRegisteredEvent(projectData, javaName, target)) {
                    return fail("Imports nao foram persistidos em " + javaName + ".");
                }
            }
            notifyRefresh(scId, javaName, target.key);
            return ok(true, "Alterado " + javaName + " -> " + target.key);
        } catch (Exception e) {
            android.util.Log.e("SdbMutationEngine", "mutation failed", e);
            return fail(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private static class ImportExtraction {
        String imports = "";
        String body = "";
    }

    private static ImportExtraction extractImports(String code) {
        ImportExtraction result = new ImportExtraction();
        if (code == null || code.isEmpty()) return result;
        StringBuilder imports = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (String line : code.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.matches("import\\s+(?:static\\s+)?[A-Za-z_$][A-Za-z0-9_$.]*(?:\\.\\*)?\\s*;")) {
                if (imports.length() > 0) imports.append('\n');
                imports.append(trimmed);
            } else {
                if (body.length() > 0) body.append('\n');
                body.append(line);
            }
        }
        result.imports = imports.toString();
        result.body = body.toString().trim();
        return result;
    }

    public static String resolveJavaName(String scId, String requested) {
        if (requested == null) return null;
        String clean = requested.trim();
        if (clean.isEmpty()) return null;
        clean = clean.replace('\\', '/');
        if (clean.contains("/")) clean = clean.substring(clean.lastIndexOf('/') + 1);
        if (clean.endsWith(".xml")) clean = clean.substring(0, clean.length() - 4);

        try {
            ArrayList<ProjectFileBean> files = jC.b(scId).b();
            if (files != null) {
                for (ProjectFileBean file : files) {
                    if (file.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) continue;
                    String javaName = file.getJavaName();
                    String xmlBase = stripSuffix(file.getXmlName(), ".xml");
                    String javaBase = stripSuffix(javaName, ".java");
                    String activityBase = stripSuffix(javaBase, "Activity");
                    if (matches(clean, javaName, javaBase, activityBase, xmlBase)) {
                        return javaName;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (!clean.endsWith(".java") && clean.endsWith("Activity")) return clean + ".java";
        return clean.endsWith(".java") ? clean : clean + "Activity.java";
    }

    public static String resolveXmlName(String scId, String requested, String fallbackXmlName) {
        String clean = firstNonEmpty(requested, fallbackXmlName);
        if (clean != null) {
            clean = clean.trim().replace('\\', '/');
            if (clean.contains("/")) clean = clean.substring(clean.lastIndexOf('/') + 1);
        }
        try {
            ArrayList<ProjectFileBean> files = jC.b(scId).b();
            if (files != null) {
                if (clean == null || clean.isEmpty()) {
                    for (ProjectFileBean file : files) {
                        if (file.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                            return stripSuffix(file.getXmlName(), ".xml");
                        }
                    }
                }
                String base = clean == null ? "" : stripSuffix(stripSuffix(clean, ".xml"), ".java");
                if (base.endsWith("Activity")) base = stripSuffix(base, "Activity");
                for (ProjectFileBean file : files) {
                    String xmlName = file.getXmlName();
                    String xmlBase = stripSuffix(xmlName, ".xml");
                    String javaName = file.getJavaName();
                    String javaBase = stripSuffix(javaName, ".java");
                    String activityBase = stripSuffix(javaBase, "Activity");
                    if (matches(base, xmlName, xmlBase, javaName, javaBase, activityBase)) {
                        return xmlBase;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (clean == null || clean.isEmpty()) return null;
        return stripSuffix(stripSuffix(clean, ".xml"), ".java");
    }

    public static boolean isGeneratedActivityJava(String scId, String fileNameOrPath) {
        if (fileNameOrPath == null) return false;
        String clean = fileNameOrPath.replace('\\', '/');
        if (clean.contains("/")) clean = clean.substring(clean.lastIndexOf('/') + 1);
        if (!clean.endsWith(".java")) clean += ".java";
        try {
            ArrayList<ProjectFileBean> files = jC.b(scId).b();
            if (files == null) return false;
            for (ProjectFileBean file : files) {
                if (file.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                        && clean.equalsIgnoreCase(file.getJavaName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static EventTarget resolveEventTarget(String rawEvent) {
        String raw = rawEvent == null || rawEvent.trim().isEmpty() ? "initializeLogic" : rawEvent.trim();
        EventTarget target = new EventTarget();

        if ("Import".equals(raw) || "Import_Import".equals(raw)) {
            target.key = "Import_Import";
            target.targetId = "Import";
            target.eventName = "Import";
            target.activityEvent = true;
            target.registerEvent = true;
            return target;
        }

        if ("onCreate".equals(raw)
                || "initializeLogic".equals(raw)
                || "onCreate_initializeLogic".equals(raw)
                || "initializeLogic_initializeLogic".equals(raw)) {
            target.key = "onCreate_initializeLogic";
            target.targetId = "onCreate";
            target.eventName = "initializeLogic";
            target.activityEvent = true;
            target.registerEvent = false;
            return target;
        }

        if (raw.contains("_")) {
            int index = raw.indexOf("_on");
            if (index < 0) index = raw.indexOf("_initializeLogic");
            if (index < 0) index = raw.indexOf('_');
            String targetId = raw.substring(0, index);
            String eventName = raw.substring(index + 1);
            if ("onCreate".equals(targetId) && "initializeLogic".equals(eventName)) {
                return resolveEventTarget("onCreate");
            }
            target.key = targetId + "_" + eventName;
            target.targetId = targetId;
            target.eventName = eventName;
            target.activityEvent = isActivityEvent(eventName) && targetId.equals(eventName);
            target.registerEvent = target.activityEvent && !"initializeLogic".equals(eventName);
            return target;
        }

        if (isActivityEvent(raw)) {
            target.key = raw + "_" + raw;
            target.targetId = raw;
            target.eventName = raw;
            target.activityEvent = true;
            target.registerEvent = !"initializeLogic".equals(raw);
            return target;
        }

        target.key = raw;
        target.targetId = raw;
        target.eventName = raw;
        return target;
    }

    private static boolean ensureVisibleEvent(String scId, a.a.a.eC projectData,
                                              String javaName, EventTarget target) {
        if ("initializeLogic".equals(target.eventName)) return false;
        ArrayList<EventBean> events = projectData.g(javaName);
        if (events == null) return false;
        for (EventBean event : events) {
            if (target.targetId.equals(event.targetId) && target.eventName.equals(event.eventName)) {
                return true;
            }
        }

        int eventType = -1;
        int targetType = -1;
        if ("Import".equals(target.eventName) || target.activityEvent) {
            eventType = EventBean.EVENT_TYPE_ACTIVITY;
        } else {
            ProjectFileBean owner = findOwnerFile(scId, javaName);
            if (owner != null) {
                ViewBean view = projectData.c(owner.getXmlName(), target.targetId);
                if (view != null) {
                    eventType = EventBean.EVENT_TYPE_VIEW;
                    targetType = view.type;
                } else {
                    view = projectData.c("_drawer_" + owner.getXmlName(), target.targetId);
                    if (view != null) {
                        eventType = EventBean.EVENT_TYPE_DRAWER_VIEW;
                        targetType = view.type;
                    }
                }
            }
            if (eventType == -1) {
                ArrayList<ComponentBean> components = projectData.e(javaName);
                if (components != null) {
                    for (ComponentBean component : components) {
                        if (component != null && target.targetId.equals(component.componentId)) {
                            eventType = EventBean.EVENT_TYPE_COMPONENT;
                            targetType = component.type;
                            break;
                        }
                    }
                }
            }
        }
        if (eventType == -1) return false;
        projectData.a(javaName, eventType, targetType, target.targetId, target.eventName);
        return true;
    }

    private static ProjectFileBean findOwnerFile(String scId, String javaName) {
        try {
            ArrayList<ProjectFileBean> files = jC.b(scId).b();
            if (files != null) {
                for (ProjectFileBean file : files) {
                    if (file != null && javaName.equalsIgnoreCase(file.getJavaName())) return file;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean hasRegisteredEvent(a.a.a.eC projectData, String javaName,
                                               EventTarget target) {
        ArrayList<EventBean> events = projectData.g(javaName);
        if (events == null) return false;
        for (EventBean event : events) {
            if (event != null && target.targetId.equals(event.targetId)
                    && target.eventName.equals(event.eventName)) return true;
        }
        return false;
    }

    private static boolean hasDirectCode(ArrayList<BlockBean> blocks, String expected) {
        if (blocks == null || expected == null) return false;
        for (BlockBean block : blocks) {
            if (block != null && "addSourceDirectly".equals(block.opCode)
                    && block.parameters != null && !block.parameters.isEmpty()) {
                String content = block.parameters.get(0);
                if (content != null && content.contains(expected.trim())) return true;
            }
        }
        return false;
    }

    private static void replaceOrAddSyncBlock(ArrayList<BlockBean> blocks, String code, boolean clearNonSync) {
        // "Replace" means replace the SDB-owned block, never the user's visual blocks.
        for (BlockBean block : blocks) {
            if ("addSourceDirectly".equals(block.opCode) && block.parameters != null && !block.parameters.isEmpty()) {
                String content = block.parameters.get(0);
                if (content != null && content.contains(TAG_SYNC)) {
                    block.parameters.set(0, TAG_SYNC + "\n" + code);
                    return;
                }
            }
        }
        blocks.add(createDirectBlock(blocks, TAG_SYNC + "\n" + code));
    }

    private static void mergeImportBlock(ArrayList<BlockBean> blocks, String code) {
        for (BlockBean block : blocks) {
            if ("addSourceDirectly".equals(block.opCode) && block.parameters != null && !block.parameters.isEmpty()) {
                String content = block.parameters.get(0);
                if (content != null && content.contains(TAG_SYNC)) {
                    block.parameters.set(0, TAG_SYNC + "\n" + mergeLines(content, code));
                    return;
                }
            }
        }
        blocks.add(createDirectBlock(blocks, TAG_SYNC + "\n" + code));
    }

    private static String mergeLines(String existingTagged, String code) {
        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
        String existingBody = existingTagged.contains("\n")
                ? existingTagged.substring(existingTagged.indexOf('\n') + 1)
                : "";
        for (String line : existingBody.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) lines.add(trimmed);
        }
        for (String line : code.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) lines.add(trimmed);
        }
        StringBuilder merged = new StringBuilder();
        for (String line : lines) {
            if (merged.length() > 0) merged.append('\n');
            merged.append(line);
        }
        return merged.toString();
    }

    private static BlockBean createDirectBlock(ArrayList<BlockBean> blocks, String content) {
        int maxId = 0;
        for (BlockBean block : blocks) {
            try {
                int id = Integer.parseInt(block.id);
                if (id > maxId) maxId = id;
            } catch (Exception ignored) {
            }
        }
        BlockBean direct = new BlockBean(String.valueOf(maxId + 1),
                "add source directly %s.inputOnly", " ", "addSourceDirectly");
        direct.color = 0xff5cb722;
        direct.parameters.add(content);
        return direct;
    }

    private static boolean isActivityEvent(String eventName) {
        if (eventName == null) return false;
        for (String activityEvent : oq.ACTIVITY_EVENTS) {
            if (eventName.equals(activityEvent)) return true;
        }
        return false;
    }

    private static boolean matches(String clean, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && clean.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private static String stripSuffix(String value, String suffix) {
        if (value == null) return "";
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }

    private static String attr(SdbEditEngine.OperationData data, String key) {
        return data != null && data.attributes != null ? data.attributes.get(key) : null;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static void notifyRefresh(String scId, String javaName, String eventKey) {
        Context context = pro.sketchware.SketchApplication.getContext();
        if (context == null) return;
        Intent intent = new Intent(SdbEditEngine.ACTION_REFRESH_PROJECT);
        intent.putExtra("sc_id", scId);
        intent.putExtra("change_type", "logic");
        intent.putExtra("java_name", javaName);
        intent.putExtra("event_key", eventKey);
        context.sendBroadcast(intent);
    }

    private static Result ok(boolean changed, String message) {
        return new Result(true, changed, message);
    }

    private static Result fail(String message) {
        return new Result(false, false, message == null ? "Falha desconhecida." : message);
    }
}
