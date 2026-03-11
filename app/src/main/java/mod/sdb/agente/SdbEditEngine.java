package mod.sdb.agente;

import java.util.ArrayList;
import java.util.List;
import a.a.a.jC;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import pro.sketchware.utility.GsonUtils;

/**
 * Engine to apply AI-generated edits to the project.
 */
public class SdbEditEngine {

    public static class ProjectEdit {
        public String type; // "block", "layout", "file"
        public String javaName; // For blocks
        public String eventName; // For blocks
        public String xmlName; // For layouts
        public List<BlockBean> blocks;
        public ViewBean view;
        public ProjectFileBean file;
    }

    public static class EditResponse {
        public String scId;
        public List<ProjectEdit> edits;
        public List<Operation> operations; // Support shorthand operations
    }

    public static class Operation {
        public String op; // "add_widget", "remove_widget", "update_widget"
        public String xmlName;
        public OperationData data;
    }

    public static class OperationData {
        public String view_id; // For backward compatibility with AI output
        public String widget_id;
        public String parent_id;
        public int widget_type = -1;
        public int index = -1;
        public java.util.Map<String, String> attributes;
        public String drawable_name;
        public String xml_content;
        
        // For add_custom_block
        public String palette_name;
        public String palette_color;
        public List<java.util.Map<String, Object>> blocks;
    }

    public static boolean applyEdits(String jsonResponse, String currentXmlName) {
        try {
            // First try as a full response with operations array
            EditResponse response = GsonUtils.getGson().fromJson(jsonResponse, EditResponse.class);
            boolean applied = false;

            if (response != null && response.scId != null) {
                String scId = response.scId;
                
                // Handle legacy "edits"
                if (response.edits != null && !response.edits.isEmpty()) {
                    for (ProjectEdit edit : response.edits) {
                        applyLegacyEdit(scId, edit);
                    }
                    applied = true;
                }

                // Handle new "operations"
                if (response.operations != null && !response.operations.isEmpty()) {
                    for (Operation op : response.operations) {
                        if (applyOperation(scId, op, currentXmlName)) {
                            applied = true; // At least one success
                        }
                    }
                }
            }
            
            // If still not applied, try as a single Operation object
            if (!applied) {
                Operation singleOp = GsonUtils.getGson().fromJson(jsonResponse, Operation.class);
                if (singleOp != null && singleOp.op != null) {
                    // We might need a scId here. Try to find it in the json manually if needed.
                    // But usually, single ops are passed through LogicEditor which has the context.
                    // For now, let's peek at scId if it's there.
                    String scId = null;
                    try {
                        org.json.JSONObject root = new org.json.JSONObject(jsonResponse);
                        scId = root.optString("scId", null);
                    } catch (Exception ignored) {}
                    
                    if (scId != null) {
                        applied = applyOperation(scId, singleOp, currentXmlName);
                    }
                }
            }

            return applied;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void applyLegacyEdit(String scId, ProjectEdit edit) {
        if ("block".equals(edit.type) || edit.type == null) {
            if (edit.blocks != null) {
                jC.a(scId).a(edit.javaName, edit.eventName, new ArrayList<>(edit.blocks));
            }
        } else if ("layout".equals(edit.type)) {
            if (edit.view != null && edit.xmlName != null) {
                jC.a(scId).a(edit.xmlName, edit.view);
            }
        } else if ("file".equals(edit.type)) {
            if (edit.file != null) {
                jC.b(scId).a(edit.file);
            }
        }
    }

    private static boolean applyOperation(String scId, Operation op, String currentXmlName) {
        if ("add_custom_block".equals(op.op)) {
            OperationData data = op.data;
            if (data.palette_name == null || data.blocks == null) return false;
            try {
                String palettePath = pro.sketchware.utility.FileUtil.getExternalStorageDir() 
                    + mod.hilal.saif.activities.tools.ConfigActivity.getStringSettingValueOrSetAndGet(
                        mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                        (String) mod.hilal.saif.activities.tools.ConfigActivity.getDefaultValue(mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH)
                    );
                String blocksPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + mod.hilal.saif.activities.tools.ConfigActivity.getStringSettingValueOrSetAndGet(
                        mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                        (String) mod.hilal.saif.activities.tools.ConfigActivity.getDefaultValue(mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH)
                    );

                // Load palettes
                java.util.ArrayList<java.util.HashMap<String, Object>> palettes = new java.util.ArrayList<>();
                if (pro.sketchware.utility.FileUtil.isExistFile(palettePath) && !pro.sketchware.utility.FileUtil.readFile(palettePath).isEmpty()) {
                    palettes = pro.sketchware.utility.GsonUtils.getGson().fromJson(pro.sketchware.utility.FileUtil.readFile(palettePath), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                }
                if (palettes == null) palettes = new java.util.ArrayList<>();

                // Find or create palette
                int paletteId = -1;
                for (int i = 0; i < palettes.size(); i++) {
                    if (data.palette_name.equals(palettes.get(i).get("name"))) {
                        paletteId = i + 9;
                        break;
                    }
                }
                if (paletteId == -1) {
                    java.util.HashMap<String, Object> newPalette = new java.util.HashMap<>();
                    newPalette.put("name", data.palette_name);
                    newPalette.put("color", data.palette_color != null ? data.palette_color : "#E3ECEE");
                    palettes.add(newPalette);
                    paletteId = palettes.size() - 1 + 9;
                    pro.sketchware.utility.FileUtil.writeFile(palettePath, pro.sketchware.utility.GsonUtils.getGson().toJson(palettes));
                }

                // Load blocks
                java.util.ArrayList<java.util.HashMap<String, Object>> existingBlocks = new java.util.ArrayList<>();
                if (pro.sketchware.utility.FileUtil.isExistFile(blocksPath) && !pro.sketchware.utility.FileUtil.readFile(blocksPath).isEmpty()) {
                    existingBlocks = pro.sketchware.utility.GsonUtils.getGson().fromJson(pro.sketchware.utility.FileUtil.readFile(blocksPath), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                }
                if (existingBlocks == null) existingBlocks = new java.util.ArrayList<>();

                // Add blocks
                for (java.util.Map<String, Object> blockDef : data.blocks) {
                    java.util.HashMap<String, Object> newBlock = new java.util.HashMap<>(blockDef);
                    newBlock.put("palette", String.valueOf((long)paletteId));
                    existingBlocks.add(newBlock);
                }

                pro.sketchware.utility.FileUtil.writeFile(blocksPath, pro.sketchware.utility.GsonUtils.getGson().toJson(existingBlocks));
                mod.hey.studios.editor.manage.block.v2.BlockLoader.refresh();
                pro.sketchware.utility.SketchwareUtil.toast("AI saved custom blocks to " + data.palette_name);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                pro.sketchware.utility.SketchwareUtil.toastError("AI Failed to create custom block");
                return false;
            }
        }

        if ("add_drawable".equals(op.op)) {
            OperationData data = op.data;
            if (data.drawable_name == null || data.xml_content == null) return false;
            try {
                String targetPath = pro.sketchware.utility.FileUtil.getExternalStorageDir() 
                    + "/.sketchware/data/" + scId + "/files/resource/drawable/" 
                    + data.drawable_name + ".xml";
                pro.sketchware.utility.FileUtil.writeFile(targetPath, data.xml_content);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        if ("add_widget".equals(op.op)) {
            OperationData data = op.data;
            String xmlName = op.xmlName != null ? op.xmlName : data.view_id;
            if (xmlName == null && currentXmlName != null) {
                xmlName = currentXmlName;
            } else if (xmlName != null && xmlName.toLowerCase().contains("activity") && currentXmlName != null) {
                xmlName = currentXmlName; // AI hallucinated "MainActivity"
            }
            if (xmlName == null || data.widget_id == null) return false;
            if (!xmlName.endsWith(".xml")) {
                xmlName = xmlName + ".xml";
            }
            if (currentXmlName != null && !currentXmlName.endsWith(".xml")) {
                currentXmlName = currentXmlName + ".xml";
            }
            // Forcefully override to the current screen if the AI hallucinated the name completely
            if (currentXmlName != null && !xmlName.equals(currentXmlName)) {
                xmlName = currentXmlName;
            }

            ViewBean view = new ViewBean(data.widget_id, data.widget_type);
            
            ArrayList<ViewBean> siblings = jC.a(scId).d(xmlName);
            boolean emptyScreen = (siblings == null || siblings.isEmpty());
            
            if (data.parent_id != null) {
                view.parent = data.parent_id;
                view.preParent = data.parent_id;
            } else if (!emptyScreen) {
                // Fallback to the first widget (often a root linear)
                view.parent = siblings.get(0).id;
                view.preParent = siblings.get(0).id;
            } else {
                // Completely empty screen, this will be the root
                view.parent = "root";
                view.preParent = "root";
            }
            
            try {
                ViewBean parentBean = jC.a(scId).c(xmlName, view.parent);
                if (parentBean != null) {
                    view.parentType = parentBean.type;
                    view.preParentType = parentBean.type;
                } else {
                    view.parentType = 0; // Default to LinearLayout
                    view.preParentType = 0;
                }
            } catch (Exception e) {
                view.parentType = 0;
                view.preParentType = 0;
            }
            
            if (data.index != -1) {
                view.index = data.index;
            } else {
                int maxIndex = -1;
                if (!emptyScreen) {
                    for (ViewBean sibling : siblings) {
                        if (view.parent.equals(sibling.parent) || view.parent.equals(sibling.preParent)) {
                            if (sibling.index > maxIndex) {
                                maxIndex = sibling.index;
                            }
                        }
                    }
                }
                view.index = maxIndex + 1;
            }
            view.preIndex = view.index;
            view.preId = data.widget_id;

            if (data.attributes != null) {
                new pro.sketchware.tools.ViewBeanFactory(view).applyAttributes(data.attributes);
            }

            jC.a(scId).a(xmlName, view);
            return true;
        } else if ("update_widget".equals(op.op)) {
            OperationData data = op.data;
            String xmlName = op.xmlName != null ? op.xmlName : data.view_id;
            if (xmlName == null && currentXmlName != null) xmlName = currentXmlName;
            if (xmlName == null || data.widget_id == null) return false;
            
            if (!xmlName.endsWith(".xml")) xmlName += ".xml";
            if (currentXmlName != null && !currentXmlName.endsWith(".xml")) currentXmlName += ".xml";
            if (currentXmlName != null && !xmlName.equals(currentXmlName)) xmlName = currentXmlName;

            try {
                ViewBean existing = jC.a(scId).c(xmlName, data.widget_id);
                if (existing != null && data.attributes != null) {
                    new pro.sketchware.tools.ViewBeanFactory(existing).applyAttributes(data.attributes);
                    return true;
                }
            } catch (Exception e) {}
            return false;
        } else if ("remove_widget".equals(op.op)) {
            OperationData data = op.data;
            String xmlName = op.xmlName != null ? op.xmlName : data.view_id;
            if (xmlName == null && currentXmlName != null) xmlName = currentXmlName;
            if (xmlName == null || data.widget_id == null) return false;
            
            if (!xmlName.endsWith(".xml")) xmlName += ".xml";
            if (currentXmlName != null && !currentXmlName.endsWith(".xml")) currentXmlName += ".xml";
            if (currentXmlName != null && !xmlName.equals(currentXmlName)) xmlName = currentXmlName;

            try {
                ViewBean existing = jC.a(scId).c(xmlName, data.widget_id);
                if (existing != null) {
                    ProjectFileBean fileBean = null;
                    for (ProjectFileBean pfb : jC.b(scId).b()) {
                        if (pfb.getXmlName().equals(xmlName)) {
                            fileBean = pfb; 
                            break;
                        }
                    }
                    if (fileBean != null) {
                        jC.a(scId).a(fileBean, existing);
                        return true;
                    }
                }
            } catch (Exception e) {}
            return false;
        }
        return false;
    }

    public static void addEventToActivityAndInjectCode(String scId, String javaName, String eventName, String code) {
        a.a.a.eC projectData = a.a.a.jC.a(scId);

        java.util.ArrayList<com.besome.sketch.beans.EventBean> events = projectData.g(javaName);
        boolean exists = false;
        if (events != null) {
            for (com.besome.sketch.beans.EventBean eb : events) {
                if (eventName.equals(eb.eventName)) {
                    exists = true;
                    break;
                }
            }
        }
        
        if (!exists) {
            com.besome.sketch.beans.EventBean event = new com.besome.sketch.beans.EventBean(
                    com.besome.sketch.beans.EventBean.EVENT_TYPE_ACTIVITY,
                    0,
                    javaName,
                    eventName
            );
            if (events != null) events.add(event); 
            projectData.a(javaName, eventName, new java.util.ArrayList<>());
        }

        java.util.ArrayList<com.besome.sketch.beans.BlockBean> blocks = projectData.a(javaName, eventName);
        int maxId = 0;
        if (blocks != null) {
            for (com.besome.sketch.beans.BlockBean b : blocks) {
                try { 
                    int id = Integer.parseInt(b.id); 
                    if (id > maxId) maxId = id; 
                } catch (Exception ignored) {}
            }
        }
        maxId++;

        com.besome.sketch.beans.BlockBean directCodeBlock = new com.besome.sketch.beans.BlockBean();
        directCodeBlock.id = String.valueOf(maxId);
        directCodeBlock.spec = "add source directly %s.inputOnly";
        directCodeBlock.type = " ";
        directCodeBlock.opCode = "addSourceDirectly";
        directCodeBlock.color = 0xffeebb55;
        directCodeBlock.parameters.add(code);
        directCodeBlock.nextBlock = -1;
        directCodeBlock.subStack1 = -1;
        directCodeBlock.subStack2 = -1;
        
        if (blocks != null) blocks.add(directCodeBlock);
    }

    public static void addMoreBlockAndInjectCode(String scId, String javaName, String mbName, String spec, String code) {
        a.a.a.eC projectData = a.a.a.jC.a(scId);

        android.util.Pair<String, String> mbPair = new android.util.Pair<>(mbName, spec);
        java.util.ArrayList<android.util.Pair<String, String>> existingMb = projectData.i(javaName);
        if (existingMb != null) existingMb.add(mbPair);
        
        projectData.a(javaName, mbName, new java.util.ArrayList<>());

        if (code != null && !code.trim().isEmpty()) {
            java.util.ArrayList<com.besome.sketch.beans.BlockBean> blocks = projectData.a(javaName, mbName);
            com.besome.sketch.beans.BlockBean directCodeBlock = new com.besome.sketch.beans.BlockBean();
            directCodeBlock.id = "0";
            directCodeBlock.spec = "add source directly %s.inputOnly";
            directCodeBlock.type = " ";
            directCodeBlock.opCode = "addSourceDirectly";
            directCodeBlock.color = 0xffeebb55;
            directCodeBlock.parameters.add(code);
            directCodeBlock.nextBlock = -1;
            directCodeBlock.subStack1 = -1;
            directCodeBlock.subStack2 = -1;
            
            if (blocks != null) blocks.add(directCodeBlock);
        }
    }
}
