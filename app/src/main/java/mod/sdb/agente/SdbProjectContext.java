package mod.sdb.agente;

import android.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.oq;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import pro.sketchware.utility.GsonUtils;

public class SdbProjectContext {

    /** Full context with no screen filter. */
    public static String getFullProjectContext(String scId) {
        return getFullProjectContext(scId, null);
    }

    /**
     * Context with pruning: the current screen gets full detail (events + blocks JSON);
     * other screens get a compact summary (widget count + MoreBlock names only).
     * This reduces token usage on large projects while keeping all relevant info sharp.
     *
     * @param currentJavaName Activity/xml name currently open in the editor (may be null)
     */
    public static String getFullProjectContext(String scId, String currentJavaName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FULL PROJECT CONTEXT (ID: ").append(scId).append(") ===\n\n");

        // 1. Metadata
        sb.append("== METADATA ==\n");
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata != null) sb.append(GsonUtils.getGson().toJson(metadata)).append("\n\n");

        // 2. Views and Logic
        ArrayList<ProjectFileBean> files = jC.b(scId).b();
        sb.append("== VIEWS & LOGIC ==\n");
        for (ProjectFileBean file : files) {
            String javaName = file.getJavaName();
            String xmlName  = file.getXmlName();
            boolean isCurrent = isCurrent(javaName, xmlName, currentJavaName);

            sb.append("View: ").append(file.fileName)
              .append(" (").append(javaName).append(", ").append(xmlName).append(")")
              .append(isCurrent ? " ← CURRENT\n" : "\n");

            sb.append("  Metadata: type=").append(file.fileType)
              .append(", orientation=").append(file.orientation)
              .append(", keyboard=").append(file.keyboardSetting)
              .append(", options=").append(file.options).append("\n");

            // Widget hierarchy
            ArrayList<com.besome.sketch.beans.ViewBean> layoutViews = jC.a(scId).d(xmlName);
            if (isCurrent) {
                sb.append("  Widgets (Layout):\n");
                if (layoutViews == null || layoutViews.isEmpty()) {
                    sb.append("    (No widgets found)\n");
                } else {
                    for (com.besome.sketch.beans.ViewBean v : layoutViews) {
                        sb.append("    - ID: ").append(v.id)
                          .append(", Type: ").append(v.type)
                          .append(", Parent: ").append(v.parent).append("\n");
                    }
                }
            } else {
                sb.append("  Widgets: ").append(layoutViews != null ? layoutViews.size() : 0).append(" widget(s)\n");
            }

            // Components (full only for current screen)
            ArrayList<ComponentBean> components = jC.a(scId).e(javaName);
            if (isCurrent) {
                sb.append("  Components:\n");
                if (components == null || components.isEmpty()) {
                    sb.append("    (No components)\n");
                } else {
                    for (ComponentBean comp : components) {
                        sb.append("    - ID: ").append(comp.componentId)
                          .append(", Type: ").append(comp.type).append("\n");
                    }
                }
            }

            // Events with block JSON — only for the current screen
            if (isCurrent) {
                sb.append("  Events:\n");
                boolean hasEvents = false;
                for (String eventName : oq.ACTIVITY_EVENTS) {
                    if (appendEventIfNotEmpty(sb, scId, javaName, "0_" + eventName)) hasEvents = true;
                }
                if (components != null) {
                    for (ComponentBean comp : components) {
                        String[] commonCompEvents = {"onResponse", "onCancelled", "onChildAdded", "onClick"};
                        for (String ce : commonCompEvents) {
                            if (appendEventIfNotEmpty(sb, scId, javaName, comp.componentId + "_" + ce)) hasEvents = true;
                        }
                    }
                }
                if (!hasEvents) sb.append("    (No event logic yet)\n");
            }

            // MoreBlocks — names+specs for all; bodies only for current screen
            sb.append("  MoreBlocks:\n");
            try {
                ArrayList<Pair<String, String>> moreBlocks = jC.a(scId).i(javaName);
                if (moreBlocks != null && !moreBlocks.isEmpty()) {
                    for (Pair<String, String> mb : moreBlocks) {
                        sb.append("    - name: ").append(mb.first)
                          .append(", spec: ").append(mb.second).append("\n");
                        if (isCurrent) {
                            String bodyJson = jC.a(scId).b(javaName, mb.first + "_moreBlock");
                            if (bodyJson != null && !bodyJson.isEmpty() && !bodyJson.equals("[]")) {
                                sb.append("      body: ").append(bodyJson).append("\n");
                            }
                        }
                    }
                } else {
                    sb.append("    (None)\n");
                }
            } catch (Exception e) {
                sb.append("    (Could not load MoreBlocks)\n");
            }
            sb.append("\n");
        }

        // 3. Drawables (XML)
        sb.append("== DRAWABLES (XML Resources) ==\n");
        String drawableDir = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/data/" + scId + "/files/resource/drawable/";
        java.io.File dDir = new java.io.File(drawableDir);
        if (dDir.exists() && dDir.isDirectory()) {
            java.io.File[] drawables = dDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (drawables != null && drawables.length > 0) {
                for (java.io.File d : drawables) {
                    sb.append("  - @drawable/").append(d.getName().replace(".xml", "")).append("\n");
                }
            } else {
                sb.append("  (No custom XML drawables)\n");
            }
        } else {
            sb.append("  (No custom XML drawables)\n");
        }

        // 4. Resource Images
        sb.append("== RESOURCE IMAGES ==\n");
        try {
            ArrayList<com.besome.sketch.beans.ProjectResourceBean> images = jC.d(scId).b;
            if (images != null && !images.isEmpty()) {
                for (com.besome.sketch.beans.ProjectResourceBean img : images) {
                    sb.append("  - @drawable/").append(img.resName)
                      .append(" (").append(img.resFullName).append(")\n");
                }
            } else {
                sb.append("  (No resource images)\n");
            }
        } catch (Exception e) {
            sb.append("  (Could not load resource images)\n");
        }

        return sb.toString();
    }

    /** True if this screen matches the currently open context. */
    private static boolean isCurrent(String javaName, String xmlName, String currentJavaName) {
        if (currentJavaName == null) return true; // No filter → full detail for all
        String cn = currentJavaName.replace("Activity", "").toLowerCase();
        return javaName.equalsIgnoreCase(currentJavaName)
                || xmlName.equalsIgnoreCase(currentJavaName)
                || xmlName.replace(".xml", "").equalsIgnoreCase(currentJavaName)
                || javaName.replace("Activity", "").equalsIgnoreCase(cn);
    }

    private static boolean appendEventIfNotEmpty(StringBuilder sb, String scId, String javaName, String eventName) {
        String blocksJson = jC.a(scId).b(javaName, eventName);
        if (blocksJson != null && !blocksJson.isEmpty() && !blocksJson.equals("[]")) {
            sb.append("    Event: ").append(eventName).append("\n");
            sb.append("      Blocks: ").append(blocksJson).append("\n");
            return true;
        }
        return false;
    }
}
