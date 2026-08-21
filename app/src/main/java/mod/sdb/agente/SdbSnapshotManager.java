package mod.sdb.agente;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.LinkedHashMap;
import android.util.Pair;
import a.a.a.jC;
import a.a.a.oq;

public class SdbSnapshotManager {

    private static final Map<String, Stack<Snapshot>> undoStacks = new HashMap<>();
    private static final Gson gson = new Gson();

    public static class Snapshot {
        public String scId;
        public long timestamp;
        // Layout
        public Map<String, String> xmlBeansJson = new HashMap<>();
        public Map<String, String> xmlRootLayoutsJson = new HashMap<>();
        public Map<String, String> xmlFabBeansJson = new HashMap<>();
        public String resourcesJson;
        public String projectFilesJson;
        public Map<String, FileState> files = new LinkedHashMap<>();
        public String label;
        public String resultSummary;
        // Logic: javaName → (eventKey → blocksJson)
        public Map<String, Map<String, String>> eventsJson = new HashMap<>();
        // MoreBlock specs: javaName → (mbName → spec)
        public Map<String, Map<String, String>> moreBlockSpecsJson = new HashMap<>();
        // MoreBlock bodies: javaName → (mbName+"_moreBlock" → blocksJson)
        public Map<String, Map<String, String>> moreBlockBodiesJson = new HashMap<>();
        public Map<String, String> componentsJson = new HashMap<>();
        public Map<String, String> eventRegistryJson = new HashMap<>();
        public Map<String, String> variablesJson = new HashMap<>();
        public Map<String, String> listsJson = new HashMap<>();

        public Snapshot(String scId) {
            this.scId = scId;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class FileState {
        public boolean existed;
        public String content;
    }

    public static Snapshot takeSnapshot(String scId, ArrayList<String> xmlNames) {
        return takeSnapshot(scId, xmlNames, null);
    }

    public static synchronized Snapshot takeSnapshot(String scId, ArrayList<String> xmlNames,
                                                     String operationsJson) {
        try {
            Snapshot snapshot = new Snapshot(scId);

            // --- Layout & Resources ---
            ArrayList<ProjectResourceBean> resources = jC.d(scId).b;
            snapshot.resourcesJson = gson.toJson(resources);
            snapshot.projectFilesJson = gson.toJson(jC.b(scId).b());

            if (xmlNames != null) {
                for (String xmlName : xmlNames) {
                    String normalized = normalizeXml(xmlName);
                    ArrayList<ViewBean> beans = jC.a(scId).d(normalized);
                    ViewBean fabBean = jC.a(scId).h(normalized);
                    snapshot.xmlBeansJson.put(normalized, beans == null ? null : gson.toJson(beans));
                    snapshot.xmlFabBeansJson.put(normalized, fabBean == null ? null : gson.toJson(fabBean));
                    try {
                        pro.sketchware.managers.inject.InjectRootLayoutManager rootManager =
                                new pro.sketchware.managers.inject.InjectRootLayoutManager(scId);
                        pro.sketchware.managers.inject.InjectRootLayoutManager.Root root =
                                rootManager.get().get(normalized);
                        snapshot.xmlRootLayoutsJson.put(normalized, root == null ? null : gson.toJson(root));
                    } catch (Exception ignored) {}
                }
            }
            captureDirectFiles(scId, operationsJson, snapshot);

            // --- Events & MoreBlocks (all screens) ---
            try {
                ArrayList<ProjectFileBean> files = jC.b(scId).b();
                if (files != null) {
                    for (ProjectFileBean file : files) {
                        String javaName = file.getJavaName();

                        ArrayList<ComponentBean> componentSnapshot = jC.a(scId).e(javaName);
                        snapshot.componentsJson.put(javaName, gson.toJson(componentSnapshot));
                        try {
                            snapshot.eventRegistryJson.put(javaName, gson.toJson(jC.a(scId).g(javaName)));
                        } catch (Exception ignored) {}
                        for (int type = 0; type <= 3; type++) {
                            snapshot.variablesJson.put(javaName + ":" + type,
                                    gson.toJson(jC.a(scId).e(javaName, type)));
                            if (type > 0) {
                                snapshot.listsJson.put(javaName + ":" + type,
                                        gson.toJson(jC.a(scId).d(javaName, type)));
                            }
                        }

                        // Activity events
                        Map<String, String> javaEvents = new HashMap<>();
                        for (String evtName : oq.ACTIVITY_EVENTS) {
                            String key = "0_" + evtName;
                            String json = jC.a(scId).b(javaName, key);
                            if (json != null && !json.isEmpty() && !json.equals("[]")) {
                                javaEvents.put(key, json);
                            }
                        }
                        // Component events
                        ArrayList<ComponentBean> comps = jC.a(scId).e(javaName);
                        if (comps != null) {
                            String[] compEvts = {"onResponse", "onCancelled", "onChildAdded", "onClick", "onTimerTick"};
                            for (ComponentBean comp : comps) {
                                for (String ce : compEvts) {
                                    String key = comp.componentId + "_" + ce;
                                    String json = jC.a(scId).b(javaName, key);
                                    if (json != null && !json.isEmpty() && !json.equals("[]")) {
                                        javaEvents.put(key, json);
                                    }
                                }
                            }
                        }
                        if (!javaEvents.isEmpty()) snapshot.eventsJson.put(javaName, javaEvents);

                        // MoreBlock specs (stored as mbName→spec map, avoids Pair serialization issues)
                        ArrayList<Pair<String, String>> mbs = jC.a(scId).i(javaName);
                        if (mbs != null && !mbs.isEmpty()) {
                            Map<String, String> specMap = new HashMap<>();
                            Map<String, String> bodyMap = new HashMap<>();
                            for (Pair<String, String> mb : mbs) {
                                specMap.put(mb.first, mb.second);
                                // MoreBlock body
                                String bodyKey = mb.first + "_moreBlock";
                                String bodyJson = jC.a(scId).b(javaName, bodyKey);
                                if (bodyJson != null && !bodyJson.isEmpty() && !bodyJson.equals("[]")) {
                                    bodyMap.put(bodyKey, bodyJson);
                                }
                            }
                            snapshot.moreBlockSpecsJson.put(javaName, specMap);
                            if (!bodyMap.isEmpty()) snapshot.moreBlockBodiesJson.put(javaName, bodyMap);
                        }
                    }
                }
            } catch (Exception e) {
                // Non-fatal: layout snapshot still valid
                e.printStackTrace();
            }

            Stack<Snapshot> stack = stackFor(scId);
            stack.push(snapshot);
            if (stack.size() > 10) stack.remove(0);
            return snapshot;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static synchronized boolean undo(String scId) {
        Stack<Snapshot> stack = stackFor(scId);
        if (stack.isEmpty()) return false;
        return restore(stack.pop());
    }

    public static synchronized boolean rollback(Snapshot snapshot) {
        if (snapshot == null) return false;
        Stack<Snapshot> stack = stackFor(snapshot.scId);
        stack.remove(snapshot);
        return restore(snapshot);
    }

    public static synchronized void commit(Snapshot snapshot, String label, String summary) {
        if (snapshot == null) return;
        snapshot.label = label;
        snapshot.resultSummary = summary;
        persistHistory(snapshot);
    }

    private static boolean restore(Snapshot snapshot) {
        String scId = snapshot.scId;

        try {
            // --- Restore Resources ---
            if (snapshot.resourcesJson != null) {
                Type rt = new TypeToken<ArrayList<ProjectResourceBean>>(){}.getType();
                ArrayList<ProjectResourceBean> resources = gson.fromJson(snapshot.resourcesJson, rt);
                ArrayList<ProjectResourceBean> cur = jC.d(scId).b;
                if (cur != null) { cur.clear(); cur.addAll(resources); }
            }

            if (snapshot.projectFilesJson != null) {
                Type pfbType = new TypeToken<ArrayList<ProjectFileBean>>(){}.getType();
                ArrayList<ProjectFileBean> restored = gson.fromJson(snapshot.projectFilesJson, pfbType);
                ArrayList<ProjectFileBean> current = jC.b(scId).b();
                if (current != null && restored != null) {
                    current.clear();
                    current.addAll(restored);
                    jC.b(scId).j();
                }
            }

            // --- Restore Layouts ---
            Type vbListType = new TypeToken<ArrayList<ViewBean>>(){}.getType();
            for (Map.Entry<String, String> e : snapshot.xmlBeansJson.entrySet()) {
                if (e.getValue() == null) {
                    jC.a(scId).c.remove(e.getKey());
                } else {
                    ArrayList<ViewBean> beans = gson.fromJson(e.getValue(), vbListType);
                    jC.a(scId).c.put(e.getKey(), beans);
                }
            }
            for (Map.Entry<String, String> e : snapshot.xmlRootLayoutsJson.entrySet()) {
                try {
                    pro.sketchware.managers.inject.InjectRootLayoutManager manager =
                            new pro.sketchware.managers.inject.InjectRootLayoutManager(scId);
                    pro.sketchware.managers.inject.InjectRootLayoutManager.Root root = e.getValue() == null
                            ? pro.sketchware.managers.inject.InjectRootLayoutManager.getDefaultRootLayout()
                            : gson.fromJson(e.getValue(),
                            pro.sketchware.managers.inject.InjectRootLayoutManager.Root.class);
                    manager.set(e.getKey(), root);
                } catch (Throwable ignored) {}
            }
            for (Map.Entry<String, String> e : snapshot.xmlFabBeansJson.entrySet()) {
                if (e.getValue() == null) continue;
                ViewBean fabBean = gson.fromJson(e.getValue(), ViewBean.class);
                try { jC.a(scId).a(e.getKey(), fabBean); } catch (Throwable ignored) {}
            }

            // --- Restore Events ---
            Type blockListType = new TypeToken<ArrayList<BlockBean>>(){}.getType();
            Type componentListType = new TypeToken<ArrayList<ComponentBean>>(){}.getType();
            for (Map.Entry<String, String> entry : snapshot.componentsJson.entrySet()) {
                ArrayList<ComponentBean> restored = gson.fromJson(entry.getValue(), componentListType);
                ArrayList<ComponentBean> current = jC.a(scId).e(entry.getKey());
                if (current != null) {
                    current.clear();
                    if (restored != null) current.addAll(restored);
                }
            }
            Type eventListType = new TypeToken<ArrayList<EventBean>>(){}.getType();
            for (Map.Entry<String, String> entry : snapshot.eventRegistryJson.entrySet()) {
                try {
                    ArrayList<EventBean> restored = gson.fromJson(entry.getValue(), eventListType);
                    ArrayList<EventBean> current = jC.a(scId).g(entry.getKey());
                    if (current != null) {
                        current.clear();
                        if (restored != null) current.addAll(restored);
                    }
                } catch (Exception ignored) {}
            }
            Type stringListType = new TypeToken<ArrayList<String>>(){}.getType();
            restoreStringLists(scId, snapshot.variablesJson, stringListType, false);
            restoreStringLists(scId, snapshot.listsJson, stringListType, true);
            if (snapshot.eventsJson != null) {
                for (Map.Entry<String, Map<String, String>> je : snapshot.eventsJson.entrySet()) {
                    String javaName = je.getKey();
                    for (Map.Entry<String, String> ee : je.getValue().entrySet()) {
                        try {
                            ArrayList<BlockBean> blocks = gson.fromJson(ee.getValue(), blockListType);
                            jC.a(scId).a(javaName, ee.getKey(), blocks);
                        } catch (Exception ignored) {}
                    }
                }
            }

            // --- Restore MoreBlock specs ---
            if (snapshot.moreBlockSpecsJson != null) {
                for (Map.Entry<String, Map<String, String>> me : snapshot.moreBlockSpecsJson.entrySet()) {
                    String javaName = me.getKey();
                    ArrayList<Pair<String, String>> cur = jC.a(scId).i(javaName);
                    if (cur != null) {
                        cur.clear();
                        for (Map.Entry<String, String> mbe : me.getValue().entrySet()) {
                            cur.add(new Pair<>(mbe.getKey(), mbe.getValue()));
                        }
                    }
                }
            }

            // --- Restore MoreBlock bodies ---
            if (snapshot.moreBlockBodiesJson != null) {
                for (Map.Entry<String, Map<String, String>> me : snapshot.moreBlockBodiesJson.entrySet()) {
                    String javaName = me.getKey();
                    for (Map.Entry<String, String> be : me.getValue().entrySet()) {
                        try {
                            ArrayList<BlockBean> blocks = gson.fromJson(be.getValue(), blockListType);
                            jC.a(scId).a(javaName, be.getKey(), blocks);
                        } catch (Exception ignored) {}
                    }
                }
            }

            restoreDirectFiles(scId, snapshot);

            jC.d(scId).y();
            SdbProjectIntegrityGuard.saveProjectData(scId);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static synchronized boolean canUndo() {
        for (Stack<Snapshot> stack : undoStacks.values()) if (!stack.isEmpty()) return true;
        return false;
    }

    public static synchronized boolean canUndo(String scId) {
        return scId != null && !stackFor(scId).isEmpty();
    }

    public static synchronized ArrayList<String> history(String scId) {
        ArrayList<String> entries = new ArrayList<>();
        try {
            android.content.Context context = pro.sketchware.SketchApplication.getContext();
            if (context == null) return entries;
            String raw = context.getSharedPreferences("sdb_codflow_history", android.content.Context.MODE_PRIVATE)
                    .getString(scId, "[]");
            org.json.JSONArray stored = new org.json.JSONArray(raw);
            for (int i = stored.length() - 1; i >= 0; i--) {
                org.json.JSONObject item = stored.optJSONObject(i);
                if (item == null) continue;
                entries.add(item.optString("label", "Edicao CodFlow") + " - "
                        + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(new java.util.Date(item.optLong("timestamp", 0))));
            }
        } catch (Exception ignored) {}
        return entries;
    }

    public static synchronized void clear() { undoStacks.clear(); }

    private static Stack<Snapshot> stackFor(String scId) {
        String key = scId == null ? "" : scId;
        Stack<Snapshot> stack = undoStacks.get(key);
        if (stack == null) {
            stack = new Stack<>();
            undoStacks.put(key, stack);
        }
        return stack;
    }

    private static String normalizeXml(String xmlName) {
        String clean = xmlName == null ? "" : xmlName.trim();
        return clean.endsWith(".xml") ? clean : clean + ".xml";
    }

    private static void captureDirectFiles(String scId, String json, Snapshot snapshot) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            org.json.JSONArray operations;
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) operations = new org.json.JSONArray(trimmed);
            else {
                org.json.JSONObject root = new org.json.JSONObject(trimmed);
                operations = root.optJSONArray("operations");
                if (operations == null) operations = new org.json.JSONArray().put(root);
            }
            for (int i = 0; i < operations.length(); i++) {
                org.json.JSONObject op = operations.optJSONObject(i);
                if (op == null) continue;
                String path = affectedPath(op.optString("op", ""), op.optJSONObject("data"));
                if (path == null || snapshot.files.containsKey(path)) continue;
                java.io.File file = new java.io.File(projectRoot(scId), path);
                FileState state = new FileState();
                state.existed = file.exists() && file.isFile();
                state.content = state.existed
                        ? pro.sketchware.utility.FileUtil.readFileIfExist(file.getAbsolutePath()) : null;
                snapshot.files.put(path, state);
            }
        } catch (Exception ignored) {}
    }

    private static String affectedPath(String op, org.json.JSONObject data) {
        if (data == null) return null;
        String name;
        if ("create_java_class".equals(op) || "create_java_file".equals(op)
                || "edit_java_file".equals(op) || "delete_java_file".equals(op)) {
            name = firstJson(data, "class_name", "file_name", "name");
            if (name == null) return null;
            if (!name.endsWith(".java")) name += ".java";
            return "files/java/" + new java.io.File(name).getName();
        }
        if ("create_layout_xml".equals(op)) {
            name = firstJson(data, "layout_name", "name", "file_name");
            return name == null ? null : "files/resource/layout/" + xmlFile(name);
        }
        if ("create_drawable_xml".equals(op) || "add_drawable".equals(op)
                || "delete_drawable".equals(op)) {
            name = firstJson(data, "drawable_name", "name", "file_name");
            return name == null ? null : "files/resource/drawable/" + xmlFile(name);
        }
        if (op.contains("file")) return cleanPath(data.optString("path", null));
        return null;
    }

    private static void restoreDirectFiles(String scId, Snapshot snapshot) {
        for (Map.Entry<String, FileState> entry : snapshot.files.entrySet()) {
            java.io.File file = new java.io.File(projectRoot(scId), entry.getKey());
            FileState state = entry.getValue();
            if (state.existed) {
                java.io.File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                pro.sketchware.utility.FileUtil.writeFile(file.getAbsolutePath(),
                        state.content == null ? "" : state.content);
            } else if (file.exists()) {
                pro.sketchware.utility.FileUtil.deleteFile(file.getAbsolutePath());
            }
        }
    }

    private static java.io.File projectRoot(String scId) {
        return new java.io.File(pro.sketchware.utility.FileUtil.getExternalStorageDir(),
                ".sketchware/data/" + scId);
    }

    private static String cleanPath(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        String clean = path.trim().replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        return clean.contains("..") ? null : clean;
    }

    private static String xmlFile(String name) {
        String clean = new java.io.File(name).getName();
        return clean.endsWith(".xml") ? clean : clean + ".xml";
    }

    private static String firstJson(org.json.JSONObject data, String... keys) {
        for (String key : keys) {
            String value = data.optString(key, "");
            if (!value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static void persistHistory(Snapshot snapshot) {
        try {
            android.content.Context context = pro.sketchware.SketchApplication.getContext();
            if (context == null) return;
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "sdb_codflow_history", android.content.Context.MODE_PRIVATE);
            org.json.JSONArray entries = new org.json.JSONArray(prefs.getString(snapshot.scId, "[]"));
            org.json.JSONObject entry = new org.json.JSONObject();
            entry.put("timestamp", snapshot.timestamp);
            entry.put("label", snapshot.label == null ? "Edicao CodFlow" : snapshot.label);
            entry.put("summary", snapshot.resultSummary == null ? "" : snapshot.resultSummary);
            entries.put(entry);
            while (entries.length() > 20) entries.remove(0);
            prefs.edit().putString(snapshot.scId, entries.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void restoreStringLists(String scId, Map<String, String> stored,
                                           Type listType, boolean listVariable) {
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            int separator = entry.getKey().lastIndexOf(':');
            if (separator <= 0) continue;
            String javaName = entry.getKey().substring(0, separator);
            int type;
            try {
                type = Integer.parseInt(entry.getKey().substring(separator + 1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            ArrayList<String> restored = gson.fromJson(entry.getValue(), listType);
            ArrayList<String> current = listVariable
                    ? jC.a(scId).d(javaName, type) : jC.a(scId).e(javaName, type);
            if (current != null) {
                current.clear();
                if (restored != null) current.addAll(restored);
            }
        }
    }
}
