package mod.sdb.agente;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
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
import android.util.Pair;
import a.a.a.jC;
import a.a.a.oq;

public class SdbSnapshotManager {

    private static final Stack<Snapshot> undoStack = new Stack<>();
    private static final Gson gson = new Gson();

    public static class Snapshot {
        public String scId;
        public long timestamp;
        // Layout
        public Map<String, String> xmlBeansJson = new HashMap<>();
        public Map<String, String> xmlRootBeansJson = new HashMap<>();
        public String resourcesJson;
        // Logic: javaName → (eventKey → blocksJson)
        public Map<String, Map<String, String>> eventsJson = new HashMap<>();
        // MoreBlock specs: javaName → (mbName → spec)
        public Map<String, Map<String, String>> moreBlockSpecsJson = new HashMap<>();
        // MoreBlock bodies: javaName → (mbName+"_moreBlock" → blocksJson)
        public Map<String, Map<String, String>> moreBlockBodiesJson = new HashMap<>();

        public Snapshot(String scId) {
            this.scId = scId;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static void takeSnapshot(String scId, ArrayList<String> xmlNames) {
        try {
            Snapshot snapshot = new Snapshot(scId);

            // --- Layout & Resources ---
            ArrayList<ProjectResourceBean> resources = jC.d(scId).b;
            snapshot.resourcesJson = gson.toJson(resources);

            if (xmlNames != null) {
                for (String xmlName : xmlNames) {
                    String baseXml = xmlName.replace(".xml", "");
                    ArrayList<ViewBean> beans = jC.a(scId).d(baseXml);
                    ViewBean rootBean = jC.a(scId).h(baseXml);
                    if (beans != null) snapshot.xmlBeansJson.put(baseXml, gson.toJson(beans));
                    if (rootBean != null) snapshot.xmlRootBeansJson.put(baseXml, gson.toJson(rootBean));
                }
            }

            // --- Events & MoreBlocks (all screens) ---
            try {
                ArrayList<ProjectFileBean> files = jC.b(scId).b();
                if (files != null) {
                    for (ProjectFileBean file : files) {
                        String javaName = file.getJavaName();

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

            undoStack.push(snapshot);
            if (undoStack.size() > 5) undoStack.remove(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean undo(String scId) {
        if (undoStack.isEmpty()) return false;

        Snapshot snapshot = undoStack.pop();
        if (!snapshot.scId.equals(scId)) return false;

        try {
            // --- Restore Resources ---
            if (snapshot.resourcesJson != null) {
                Type rt = new TypeToken<ArrayList<ProjectResourceBean>>(){}.getType();
                ArrayList<ProjectResourceBean> resources = gson.fromJson(snapshot.resourcesJson, rt);
                ArrayList<ProjectResourceBean> cur = jC.d(scId).b;
                if (cur != null) { cur.clear(); cur.addAll(resources); }
            }

            // --- Restore Layouts ---
            Type vbListType = new TypeToken<ArrayList<ViewBean>>(){}.getType();
            for (Map.Entry<String, String> e : snapshot.xmlBeansJson.entrySet()) {
                ArrayList<ViewBean> beans = gson.fromJson(e.getValue(), vbListType);
                ArrayList<ViewBean> cur = jC.a(scId).d(e.getKey());
                if (cur != null) { cur.clear(); cur.addAll(beans); }
            }
            for (Map.Entry<String, String> e : snapshot.xmlRootBeansJson.entrySet()) {
                ViewBean rootBean = gson.fromJson(e.getValue(), ViewBean.class);
                try { jC.a(scId).a(e.getKey(), rootBean); } catch (Throwable ignored) {}
            }

            // --- Restore Events ---
            Type blockListType = new TypeToken<ArrayList<BlockBean>>(){}.getType();
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

            jC.d(scId).y();
            jC.a(scId).k();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean canUndo() { return !undoStack.isEmpty(); }

    public static void clear() { undoStack.clear(); }
}
