package mod.sdb.agente;

import android.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.oq;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import pro.sketchware.utility.GsonUtils;

public class SdbProjectContext {

    private static final int PROJECT_WIDE_DETAIL_BUDGET = 70000;
    private static final int MAX_EVENT_JSON_CHARS = 12000;
    private static final int MAX_MOREBLOCK_JSON_CHARS = 8000;
    private static final int MAX_METADATA_CHARS = 12000;

    /** Full context with no screen filter. */
    public static String getFullProjectContext(String scId) {
        return getFullProjectContext(scId, null, null);
    }

    /**
     * Context with pruning: the current screen gets full detail (events + blocks JSON,
     * variables, lists, view events with customView); other screens get a compact summary.
     */
    public static String getFullProjectContext(String scId, String currentJavaName) {
        return getFullProjectContext(scId, currentJavaName, null);
    }

    public static String getFullProjectContext(String scId, String currentJavaName,
                                               String diagnosticText) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FULL PROJECT CONTEXT (ID: ").append(scId).append(") ===\n\n");

        // 1. Metadata
        sb.append("== METADATA ==\n");
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata != null) {
            sb.append(abbreviate(GsonUtils.getGson().toJson(metadata), MAX_METADATA_CHARS))
                    .append("\n\n");
        }

        // 2. Views and Logic
        ArrayList<ProjectFileBean> files = jC.b(scId).b();
        if (files == null) files = new ArrayList<>();
        int diagnosticFilesIncluded = 0;
        sb.append("== VIEWS & LOGIC ==\n");
        for (ProjectFileBean file : files) {
            String javaName = file.getJavaName();
            String xmlName  = file.getXmlName();
            boolean isCurrent = currentJavaName != null
                    && isCurrent(javaName, xmlName, currentJavaName);
            boolean includeFullDetail = currentJavaName == null
                    ? sb.length() < PROJECT_WIDE_DETAIL_BUDGET
                    : isCurrent;

            sb.append("View: ").append(file.fileName)
              .append(" (java_name: \"").append(javaName)
              .append("\", xmlName: \"").append(xmlName).append("\")")
              .append(isCurrent ? " ← CURRENT\n" : "\n");

            // Widget hierarchy — full for current, compact for others
            ArrayList<ViewBean> layoutViews = jC.a(scId).d(xmlName);
            if (includeFullDetail) {
                try {
                    pro.sketchware.managers.inject.InjectRootLayoutManager.Root root =
                            new pro.sketchware.managers.inject.InjectRootLayoutManager(scId)
                                    .getLayoutByFileName(xmlName);
                    sb.append("  Root: ").append(GsonUtils.getGson().toJson(root)).append("\n");
                } catch (Exception ignored) {}
                sb.append("  Widgets (Layout):\n");
                if (layoutViews == null || layoutViews.isEmpty()) {
                    sb.append("    (No widgets)\n");
                } else {
                    for (ViewBean v : layoutViews) {
                        String typeName = ViewBean.getViewTypeName(v.type);
                        sb.append("    - id: \"").append(v.id)
                          .append("\", type: ").append(typeName)
                          .append(", parent: \"").append(v.parent).append("\"");
                        if (v.customView != null && !v.customView.isEmpty()) {
                            sb.append(", customView: \"").append(v.customView).append("\"");
                        }
                        sb.append(", layout: ").append(abbreviate(
                                GsonUtils.getGson().toJson(v.layout), 2500));
                        if (v.text != null) {
                            sb.append(", text: ").append(abbreviate(
                                    GsonUtils.getGson().toJson(v.text), 1000));
                        }
                        if (v.image != null) {
                            sb.append(", image: ").append(abbreviate(
                                    GsonUtils.getGson().toJson(v.image), 1000));
                        }
                        sb.append("\n");
                    }
                }
            } else {
                sb.append("  Widgets: ").append(layoutViews != null ? layoutViews.size() : 0).append(" widget(s)\n");
                if (currentJavaName == null) {
                    sb.append("  Detail: compacted after the project context budget was reached\n");
                }
            }

            // Variables — only for current screen (globals declared in this activity)
            if (includeFullDetail) {
                sb.append("  Variables (Globals):\n");
                appendVariables(sb, scId, javaName);
            }

            // Components
            ArrayList<ComponentBean> components = jC.a(scId).e(javaName);
            if (includeFullDetail) {
                sb.append("  Components:\n");
                if (components == null || components.isEmpty()) {
                    sb.append("    (No components)\n");
                } else {
                    for (ComponentBean comp : components) {
                        sb.append("    - id: \"").append(comp.componentId)
                          .append("\", type: ").append(comp.type).append("\n");
                    }
                }
            }

            // Events — full for current screen
            if (includeFullDetail) {
                sb.append("  Events (registered):\n");
                boolean hasEvents = false;

                // Activity events (onCreate, onResume, etc.)
                for (String evName : oq.ACTIVITY_EVENTS) {
                    if (appendEventIfNotEmpty(sb, scId, javaName, "0_" + evName, "    ")) hasEvents = true;
                }

                // View events (onClick, onBindCustomView, etc.) — from EventBean registry
                try {
                    ArrayList<EventBean> allEvents = jC.a(scId).g(javaName);
                    if (allEvents != null) {
                        for (EventBean eb : allEvents) {
                            if (eb.eventType == EventBean.EVENT_TYPE_VIEW
                                    || eb.eventType == EventBean.EVENT_TYPE_COMPONENT) {
                                String key = eb.targetId + "_" + eb.eventName;
                                if (appendEventIfNotEmpty(sb, scId, javaName, key, "    ")) hasEvents = true;
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // Component events
                if (components != null) {
                    for (ComponentBean comp : components) {
                        String[] compEvents = {"onResponse", "onCancelled", "onChildAdded",
                                "onClick", "onError", "onSuccess", "onComplete"};
                        for (String ce : compEvents) {
                            if (appendEventIfNotEmpty(sb, scId, javaName, comp.componentId + "_" + ce, "    ")) hasEvents = true;
                        }
                    }
                }

                if (!hasEvents) sb.append("    (No event logic yet)\n");
            }

            // MoreBlocks
            sb.append("  MoreBlocks:\n");
            try {
                ArrayList<Pair<String, String>> moreBlocks = jC.a(scId).i(javaName);
                if (moreBlocks != null && !moreBlocks.isEmpty()) {
                    for (Pair<String, String> mb : moreBlocks) {
                        sb.append("    - name: \"").append(mb.first)
                          .append("\", spec: \"").append(mb.second).append("\"\n");
                        if (includeFullDetail) {
                            String bodyJson = jC.a(scId).b(javaName, mb.first + "_moreBlock");
                            if (bodyJson != null && !bodyJson.isEmpty() && !bodyJson.equals("[]")) {
                                sb.append("      body: ").append(abbreviate(
                                        bodyJson, MAX_MOREBLOCK_JSON_CHARS)).append("\n");
                            }
                        }
                    }
                } else {
                    sb.append("    (None)\n");
                }
            } catch (Exception e) {
                sb.append("    (Could not load MoreBlocks)\n");
            }

            boolean hasDiagnostic = diagnosticText != null
                    && !diagnosticText.trim().isEmpty();
            boolean referencedByDiagnostic = hasDiagnostic
                    && javaName != null
                    && diagnosticText.contains(javaName + ":");
            if (hasDiagnostic && (isCurrent || referencedByDiagnostic)
                    && diagnosticFilesIncluded < 3) {
                appendGeneratedJavaDiagnostic(sb, scId, javaName, diagnosticText);
                diagnosticFilesIncluded++;
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

        sb.append("\n== CODFLOW EDIT CONTRACT ==\n")
          .append("Return one JSON object with an operations array. Use exact xmlName values including .xml.\n")
          .append("Layout changes are atomic: an invalid operation rolls back the entire batch.\n")
          .append("Prefer update_widget for local changes and edit_layout_xml only for structural rewrites.\n");

        return sb.toString();
    }

    private static void appendGeneratedJavaDiagnostic(StringBuilder sb, String scId,
                                                       String javaName, String diagnosticText) {
        try {
            java.io.File root = new java.io.File(
                    pro.sketchware.utility.FileUtil.getExternalStorageDir()
                            + "/.sketchware/mysc/" + scId + "/app/src/main/java");
            java.io.File sourceFile = findFile(root, javaName, 0);
            if (sourceFile == null || !sourceFile.isFile()) {
                sb.append("  Generated Java diagnostic: source not found on disk. Compile once to generate it.\n");
                return;
            }
            String source = pro.sketchware.utility.FileUtil.readFile(sourceFile.getAbsolutePath());
            if (source == null || source.isEmpty()) return;
            String[] lines = source.split("\\r?\\n", -1);
            java.util.TreeSet<Integer> targets = new java.util.TreeSet<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(javaName) + ":(\\d+)")
                    .matcher(diagnosticText);
            while (matcher.find()) {
                try { targets.add(Integer.parseInt(matcher.group(1))); }
                catch (Exception ignored) {}
            }
            if (targets.isEmpty()) return;
            sb.append("  == GENERATED JAVA DIAGNOSTIC (READ ONLY) ==\n")
                    .append("  File: ").append(sourceFile.getAbsolutePath()).append("\n")
                    .append("  These numbered lines match the compiled stack trace. Fix the owning event/MoreBlock/model; do not edit generated Java directly.\n");
            boolean[] included = new boolean[lines.length];
            for (int target : targets) {
                int start = Math.max(1, target - 24);
                int end = Math.min(lines.length, target + 24);
                for (int line = start; line <= end; line++) included[line - 1] = true;
            }
            boolean gap = false;
            for (int i = 0; i < lines.length; i++) {
                if (!included[i]) {
                    gap = true;
                    continue;
                }
                if (gap) {
                    sb.append("  ...\n");
                    gap = false;
                }
                sb.append(String.format(java.util.Locale.US, "  %4d | %s%n", i + 1, lines[i]));
            }
        } catch (Exception error) {
            sb.append("  Generated Java diagnostic unavailable: ")
                    .append(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
                    .append("\n");
        }
    }

    private static java.io.File findFile(java.io.File directory, String fileName, int depth) {
        if (directory == null || !directory.isDirectory() || depth > 8) return null;
        java.io.File direct = new java.io.File(directory, fileName);
        if (direct.isFile()) return direct;
        java.io.File[] children = directory.listFiles(java.io.File::isDirectory);
        if (children == null) return null;
        for (java.io.File child : children) {
            java.io.File found = findFile(child, fileName, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Appends variables and lists declared in this activity.
     * Uses eC.e(javaName, type) for variables and eC.d(javaName, type) for lists.
     */
    private static void appendVariables(StringBuilder sb, String scId, String javaName) {
        try {
            ArrayList<String> boolVars   = jC.a(scId).e(javaName, 0);
            ArrayList<String> numVars    = jC.a(scId).e(javaName, 1);
            ArrayList<String> strVars    = jC.a(scId).e(javaName, 2);
            ArrayList<String> mapVars    = jC.a(scId).e(javaName, 3);
            ArrayList<String> numLists   = jC.a(scId).d(javaName, 1);
            ArrayList<String> strLists   = jC.a(scId).d(javaName, 2);
            ArrayList<String> mapLists   = jC.a(scId).d(javaName, 3);

            boolean any = false;
            any |= appendVarGroup(sb, "Boolean", boolVars);
            any |= appendVarGroup(sb, "Number",  numVars);
            any |= appendVarGroup(sb, "String",  strVars);
            any |= appendVarGroup(sb, "Map",     mapVars);
            any |= appendVarGroup(sb, "List<Number>", numLists);
            any |= appendVarGroup(sb, "List<String>", strLists);
            any |= appendVarGroup(sb, "List<Map>",    mapLists);

            if (!any) sb.append("    (None declared yet — use add_variable / add_list to create)\n");
        } catch (Exception ignored) {
            sb.append("    (Could not load variables)\n");
        }
    }

    private static boolean appendVarGroup(StringBuilder sb, String typeName, ArrayList<String> vars) {
        if (vars == null || vars.isEmpty()) return false;
        for (String v : vars) {
            sb.append("    - ").append(typeName).append(": \"").append(v).append("\"\n");
        }
        return true;
    }

    /** True if this screen matches the currently open context. */
    private static boolean isCurrent(String javaName, String xmlName, String currentJavaName) {
        if (currentJavaName == null) return true;
        String cn = currentJavaName.replace("Activity", "").toLowerCase();
        return javaName.equalsIgnoreCase(currentJavaName)
                || xmlName.equalsIgnoreCase(currentJavaName)
                || xmlName.replace(".xml", "").equalsIgnoreCase(currentJavaName)
                || javaName.replace("Activity", "").equalsIgnoreCase(cn);
    }

    private static boolean appendEventIfNotEmpty(StringBuilder sb, String scId, String javaName, String eventName, String indent) {
        String blocksJson = jC.a(scId).b(javaName, eventName);
        if (blocksJson != null && !blocksJson.isEmpty() && !blocksJson.equals("[]")) {
            sb.append(indent).append("Event: \"").append(eventName).append("\"\n");
            sb.append(indent).append("  Blocks: ")
                    .append(abbreviate(blocksJson, MAX_EVENT_JSON_CHARS)).append("\n");
            return true;
        }
        return false;
    }

    private static String abbreviate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return value;
        return value.substring(0, Math.max(0, maxChars))
                + "\n... [context truncated; inspect the exact target before editing]";
    }
}
