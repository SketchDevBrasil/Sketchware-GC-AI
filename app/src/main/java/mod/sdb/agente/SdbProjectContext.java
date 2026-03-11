package mod.sdb.agente;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import a.a.a.jC;
import a.a.a.lC;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import pro.sketchware.utility.GsonUtils;

/**
 * Helper to collect all relevant project data for AI context.
 */
public class SdbProjectContext {

    public static String getFullProjectContext(String scId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FULL PROJECT CONTEXT (ID: ").append(scId).append(") ===\n\n");

        // 1. Metadata
        sb.append("== METADATA ==\n");
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata != null) {
            sb.append(GsonUtils.getGson().toJson(metadata)).append("\n\n");
        }

        // 2. Views and Logic
        ArrayList<ProjectFileBean> files = jC.b(scId).b();
        sb.append("== VIEWS & LOGIC ==\n");
        for (ProjectFileBean file : files) {
            String javaName = file.getJavaName();
            String xmlName = file.getXmlName();
            sb.append("View: ").append(file.fileName).append(" (").append(javaName).append(", ").append(xmlName).append(")\n");
            sb.append("  Metadata: type=").append(file.fileType)
              .append(", orientation=").append(file.orientation)
              .append(", keyboard=").append(file.keyboardSetting)
              .append(", options=").append(file.options).append("\n");
            
            // 2.1 View Hierarchy (Existing Widgets)
            sb.append("  Widgets (Layout):\n");
            ArrayList<com.besome.sketch.beans.ViewBean> layoutViews = jC.a(scId).d(xmlName);
            for (com.besome.sketch.beans.ViewBean v : layoutViews) {
                sb.append("    - ID: ").append(v.id).append(", Type: ").append(v.type).append(", Parent: ").append(v.parent).append("\n");
            }

            // Components for this view
            sb.append("  Components:\n");
            ArrayList<ComponentBean> components = jC.a(scId).e(javaName);
            for (ComponentBean comp : components) {
                sb.append("    - ID: ").append(comp.componentId).append(", Type: ").append(comp.type).append("\n");
            }

            // Events for this view
            sb.append("  Events:\n");
            // Activity Events (usually prefixed with 0_)
            for (String eventName : a.a.a.oq.ACTIVITY_EVENTS) {
                appendEventIfNotEmpty(sb, scId, javaName, "0_" + eventName);
            }
            // Component Events
            for (ComponentBean comp : components) {
                // We'd ideally use oq to get events for this component type
                // But we can try common ones or scan all potential ones.
                // For a surgical/deep context, we can search the data folder too.
                // But let's try a few common component events for now.
                String[] commonCompEvents = {"onResponse", "onCancelled", "onChildAdded", "onClick"};
                for (String ce : commonCompEvents) {
                    appendEventIfNotEmpty(sb, scId, javaName, comp.componentId + "_" + ce);
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void appendEventIfNotEmpty(StringBuilder sb, String scId, String javaName, String eventName) {
        String blocksJson = jC.a(scId).b(javaName, eventName);
        if (blocksJson != null && !blocksJson.isEmpty() && !blocksJson.equals("[]")) {
            sb.append("    Event: ").append(eventName).append("\n");
            sb.append("      Blocks: ").append(blocksJson).append("\n");
        }
    }
}
