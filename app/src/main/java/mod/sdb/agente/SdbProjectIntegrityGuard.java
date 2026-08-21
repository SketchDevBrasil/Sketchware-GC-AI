package mod.sdb.agente;

import com.besome.sketch.beans.ImageBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.TextBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import a.a.a.eC;
import a.a.a.jC;

/** Keeps agent-generated layout metadata renderable by Sketchware's visual editor. */
public final class SdbProjectIntegrityGuard {

    public static final class Result {
        public boolean valid = true;
        public boolean changed;
        public final List<String> errors = new ArrayList<>();

        void fail(String message) {
            valid = false;
            errors.add(message);
        }
    }

    private SdbProjectIntegrityGuard() {
    }

    public static Result repairAndValidate(String scId, Iterable<String> xmlNames) {
        Result total = new Result();
        if (scId == null || xmlNames == null) return total;
        for (String xmlName : xmlNames) {
            Result layout = repairAndValidate(scId, xmlName);
            total.valid &= layout.valid;
            total.changed |= layout.changed;
            total.errors.addAll(layout.errors);
        }
        return total;
    }

    public static Result repairAndValidate(String scId, String xmlName) {
        Result result = new Result();
        if (scId == null || xmlName == null || xmlName.trim().isEmpty()) return result;
        String normalized = xmlName.endsWith(".xml") ? xmlName : xmlName + ".xml";
        ArrayList<ViewBean> views;
        try {
            views = jC.a(scId).d(normalized);
        } catch (Throwable error) {
            result.fail(normalized + ": nao foi possivel ler os widgets.");
            return result;
        }
        if (views == null) return result;

        for (int index = views.size() - 1; index >= 0; index--) {
            if (views.get(index) == null) {
                views.remove(index);
                result.changed = true;
            }
        }

        Set<String> ids = new LinkedHashSet<>();
        for (ViewBean view : views) {
            if (view.id == null || view.id.trim().isEmpty()) {
                result.fail(normalized + ": widget sem ID na posicao " + view.index + ".");
                continue;
            }
            if (!ids.add(view.id)) {
                result.fail(normalized + ": ID duplicado " + view.id + ".");
            }
            result.changed |= fillRequiredFields(view);
        }

        for (ViewBean view : views) {
            if (view.id == null) continue;
            if (view.parent == null || view.parent.trim().isEmpty()
                    || view.id.equals(view.parent) || (!"root".equals(view.parent) && !ids.contains(view.parent))) {
                view.parent = "root";
                view.parentType = -1;
                result.changed = true;
            }
            if (hasParentCycle(view, views)) {
                view.parent = "root";
                view.parentType = -1;
                result.changed = true;
            }
        }

        if (result.changed && result.valid) {
            try {
                jC.a(scId).c.put(normalized, views);
                sanitizeAllLayouts(scId);
                jC.a(scId).k();
            } catch (Throwable error) {
                result.fail(normalized + ": falha ao persistir reparo estrutural.");
            }
        }
        return result;
    }

    /**
     * Repairs every layout of the project so Sketchware's own serializer cannot crash on it.
     *
     * <p>{@code a.a.a.eC.a(ArrayList)} picks the root widgets with
     * {@code view.parent.equals("root")} and has no null check, so a single widget with a
     * null parent makes saving the whole project fail with a NullPointerException - the
     * project data file is written as one blob, so a bad widget in any layout takes down
     * layouts the user never touched. Run this right before saving. It never throws.
     *
     * @return how many widgets were repaired or dropped
     */
    public static int sanitizeAllLayouts(String scId) {
        if (scId == null) return 0;
        try {
            eC projectData = jC.a(scId);
            if (projectData == null) return 0;
            synchronized (projectData) {
                return sanitizeLayoutMap(projectData.c) + sanitizeFabMap(projectData.j);
            }
        } catch (Throwable error) {
            android.util.Log.e("SdbIntegrityGuard", "Layout sanitize failed", error);
            return 0;
        }
    }

    /**
     * Sanitizes layout metadata and then persists the project data, never throwing.
     *
     * <p>Use this instead of a bare {@code jC.a(scId).k()}: saving is the one moment where
     * losing the user's work is unacceptable, so bad metadata is repaired first and any
     * remaining failure is logged rather than propagated.
     */
    public static void saveProjectData(String scId) {
        if (scId == null) return;
        sanitizeAllLayouts(scId);
        try {
            jC.a(scId).k();
        } catch (Throwable error) {
            android.util.Log.e("SdbIntegrityGuard", "Saving project data failed", error);
        }
    }

    private static int sanitizeLayoutMap(java.util.HashMap<String, ArrayList<ViewBean>> layouts) {
        if (layouts == null) return 0;
        int repaired = 0;
        java.util.Iterator<java.util.Map.Entry<String, ArrayList<ViewBean>>> entries =
                layouts.entrySet().iterator();
        while (entries.hasNext()) {
            java.util.Map.Entry<String, ArrayList<ViewBean>> entry = entries.next();
            if (entry.getKey() == null || entry.getValue() == null) {
                entries.remove();
                repaired++;
                continue;
            }
            ArrayList<ViewBean> views = entry.getValue();
            for (int index = views.size() - 1; index >= 0; index--) {
                ViewBean view = views.get(index);
                if (view == null) {
                    views.remove(index);
                    repaired++;
                } else if (fillRequiredFields(view)) {
                    repaired++;
                }
            }
        }
        return repaired;
    }

    private static int sanitizeFabMap(java.util.HashMap<String, ViewBean> fabs) {
        if (fabs == null) return 0;
        int repaired = 0;
        java.util.Iterator<java.util.Map.Entry<String, ViewBean>> entries = fabs.entrySet().iterator();
        while (entries.hasNext()) {
            java.util.Map.Entry<String, ViewBean> entry = entries.next();
            if (entry.getKey() == null || entry.getValue() == null) {
                entries.remove();
                repaired++;
            } else if (fillRequiredFields(entry.getValue())) {
                repaired++;
            }
        }
        return repaired;
    }

    public static int removeDuplicateViewIds(String scId, String xmlName, String targetId) {
        if (scId == null || xmlName == null || targetId == null || targetId.trim().isEmpty()) return 0;
        String normalized = xmlName.endsWith(".xml") ? xmlName : xmlName + ".xml";
        try {
            ArrayList<ViewBean> views = jC.a(scId).d(normalized);
            if (views == null || views.isEmpty()) return 0;
            boolean found = false;
            int removed = 0;
            for (int index = 0; index < views.size();) {
                ViewBean view = views.get(index);
                if (view == null || !targetId.equals(view.id)) {
                    index++;
                    continue;
                }
                if (!found) {
                    found = true;
                    index++; // Keep the original node and remove only later duplicates.
                } else {
                    views.remove(index);
                    removed++;
                }
            }
            if (removed > 0) {
                for (ViewBean view : views) if (view != null) fillRequiredFields(view);
                jC.a(scId).c.put(normalized, views);
                sanitizeAllLayouts(scId);
                jC.a(scId).k();
                android.content.Context context = pro.sketchware.SketchApplication.getContext();
                if (context != null) {
                    android.content.Intent refresh = new android.content.Intent(
                            SdbEditEngine.ACTION_REFRESH_PROJECT);
                    refresh.putExtra("sc_id", scId);
                    refresh.putExtra("change_type", "layout");
                    refresh.putExtra("xml_name", normalized);
                    context.sendBroadcast(refresh);
                }
            }
            return removed;
        } catch (Throwable error) {
            android.util.Log.e("SdbIntegrityGuard", "Duplicate ID repair failed", error);
            return 0;
        }
    }

    private static boolean fillRequiredFields(ViewBean view) {
        boolean changed = false;
        if (view.name == null) { view.name = view.id; changed = true; }
        if (view.parent == null) { view.parent = "root"; changed = true; }
        if (view.customView == null) { view.customView = ""; changed = true; }
        // Empty is Sketchware's native value for standard widgets. A palette
        // label here may be an invalid Java/XML class (e.g. HScrollView).
        if (view.convert == null) { view.convert = ""; changed = true; }
        if (view.inject == null) { view.inject = ""; changed = true; }
        if (view.indeterminate == null) { view.indeterminate = "false"; changed = true; }
        if (view.progressStyle == null) {
            view.progressStyle = ViewBean.PROGRESSBAR_STYLE_CIRCLE;
            changed = true;
        }
        if (view.layout == null) { view.layout = new LayoutBean(); changed = true; }
        if (view.text == null) { view.text = new TextBean(); changed = true; }
        if (view.image == null) { view.image = new ImageBean(); changed = true; }
        if (view.parentAttributes == null) {
            view.parentAttributes = new java.util.HashMap<>();
            changed = true;
        }
        if (view.text.text == null) { view.text.text = ""; changed = true; }
        if (view.text.hint == null) { view.text.hint = ""; changed = true; }
        if (view.text.textFont == null) { view.text.textFont = TextBean.TEXT_FONT; changed = true; }
        if (view.image.scaleType == null) { view.image.scaleType = ImageBean.SCALE_TYPE_CENTER; changed = true; }
        return changed;
    }

    private static boolean hasParentCycle(ViewBean start, ArrayList<ViewBean> views) {
        Set<String> visited = new HashSet<>();
        ViewBean current = start;
        while (current != null && current.parent != null && !"root".equals(current.parent)) {
            if (!visited.add(current.id)) return true;
            current = findById(views, current.parent);
        }
        return false;
    }

    private static ViewBean findById(ArrayList<ViewBean> views, String id) {
        if (id == null) return null;
        for (ViewBean view : views) {
            if (view != null && id.equals(view.id)) return view;
        }
        return null;
    }
}
