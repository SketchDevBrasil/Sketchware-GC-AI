package mod.sdb.agente;

import java.util.ArrayList;
import java.util.List;
import a.a.a.jC;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import pro.sketchware.utility.GsonUtils;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Engine to apply AI-generated edits to the project.
 */
public class SdbEditEngine {
    public static final String ACTION_REFRESH_PROJECT = "mod.sdb.agente.REFRESH_PROJECT";

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
        public String id; // Alias for widget_id
        public String parent_id;
        public String parent; // Alias for parent_id
        public int widget_type = -1;
        public int type = -1; // Alias for widget_type
        public int index = -1;
        public java.util.Map<String, String> attributes;
        public java.util.Map<String, Object> params; // Alias for attributes/generic params
        public String drawable_name;
        public String xml_content;
        
        // For add_custom_block / update_custom_block / delete_custom_block / delete_palette
        public String palette_name;
        public String palette_color;
        public List<java.util.Map<String, Object>> blocks;
        public String op_code; // opCode to identify a specific block
        
        // For add_icon_resource
        public String icon_name;
        public String style; // outline, sharp, twotone, round, baseline
        public String color; 
        public String target_view_id; // To auto-apply to a widget
        public String target_xml_name; // Context for target_view_id

        // For add_moreblock / inject_code (top-level shorthand)
        public String name;
        public String spec;
        public String code;
        public String java_name; // shorthand: AI can use data.java_name instead of data.attributes.java_name
        public String event_name; // shorthand

        // For create_java_file / edit_java_file / delete_java_file
        public String file_name; // class name, e.g. "MyHelper" or "com.example.MyHelper"
        public String content;   // full Java source code

        // For add_variable / add_list
        public String var_type;  // "Boolean", "Number", "String", "Map"
        public String list_type; // "Number", "String", "Map"

        // For set_custom_view (connect ListView to item layout)
        public String custom_view; // layout name, e.g. "list_item" (no .xml)

        // For add_component
        public String component_type; // "FirebaseDB", "FirebaseAuth", "RequestNetwork", etc.
        public String param1;         // reference path (Firebase), filename (SharedPrefs), unused otherwise
    }

    public static boolean applyEdits(String scId, String jsonResponse, String currentXmlName) {
        return applyEdits(scId, jsonResponse, currentXmlName, null);
    }

    public static boolean applyEdits(String scId, String jsonResponse, String currentXmlName, String defaultContextName) {
        try {
            // First try as a full response with operations array
            EditResponse response = GsonUtils.getGson().fromJson(jsonResponse, EditResponse.class);
            boolean applied = false;

            // Engine-level application

            if (response != null && response.operations != null) {
                // If scId is not in JSON, use the one provided as argument
                String finalScId = response.scId != null ? response.scId : scId;
                if (finalScId == null) return false;
                
                // Handle legacy "edits"
                if (response.edits != null && !response.edits.isEmpty()) {
                    for (ProjectEdit edit : response.edits) {
                        applyLegacyEdit(finalScId, edit);
                    }
                    applied = true;
                }

                // Handle new "operations"
                if (response.operations != null && !response.operations.isEmpty()) {
                    java.util.List<String> opErrors = new java.util.ArrayList<>();
                    for (Operation op : response.operations) {
                        if (applyOperation(finalScId, op, currentXmlName, defaultContextName)) {
                            applied = true;
                        } else if (op.op != null) {
                            opErrors.add(op.op);
                        }
                    }
                    if (!opErrors.isEmpty()) {
                        pro.sketchware.utility.SketchwareUtil.toastError(
                            "Falha nas operações: " + android.text.TextUtils.join(", ", opErrors));
                    }
                }
            }
            
            // If still not applied, try as a raw array or single Operation object
            if (!applied) {
                String trimmed = jsonResponse.trim();
                if (trimmed.startsWith("[")) {
                     Operation[] ops = GsonUtils.getGson().fromJson(jsonResponse, Operation[].class);
                     if (ops != null) {
                         for (Operation op : ops) {
                             if (applyOperation(scId, op, currentXmlName, defaultContextName)) {
                                 applied = true;
                             }
                         }
                     }
                } else {
                    Operation singleOp = GsonUtils.getGson().fromJson(jsonResponse, Operation.class);
                    if (singleOp != null && singleOp.op != null) {
                        if (scId != null) {
                            applied = applyOperation(scId, singleOp, currentXmlName, defaultContextName);
                        }
                    }
                }
            }

            if (applied) {
                notifyProjectRefresh(scId);
            }
            return applied;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void notifyProjectRefresh(String scId) {
        notifyProjectRefresh(scId, null);
    }

    private static void notifyProjectRefresh(String scId, String eventKey) {
        android.content.Context context = pro.sketchware.SketchApplication.getContext();
        if (context != null) {
            android.content.Intent intent = new android.content.Intent(ACTION_REFRESH_PROJECT);
            intent.putExtra("sc_id", scId);
            if (eventKey != null) intent.putExtra("event_key", eventKey);
            context.sendBroadcast(intent);
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

    private static boolean applyOperation(String scId, Operation op, String currentXmlName, String defaultContextName) {
        if (op == null || op.op == null) return false;
        if ("inject_code".equals(op.op) || "add_direct_code".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;

            String javaName = data.java_name != null ? data.java_name
                    : (data.attributes != null ? data.attributes.get("java_name") : null);
            String eventName = data.event_name != null ? data.event_name
                    : (data.attributes != null ? data.attributes.get("event_name") : null);
            String id = (data.attributes != null ? data.attributes.get("id") : null);
            if (id == null) id = data.id;
            if (id == null) id = data.widget_id;

            String code = data.code != null ? data.code
                    : (data.attributes != null ? data.attributes.get("code") : null);
            if (code == null) code = data.xml_content;
            if (code == null) return false;

            if (javaName == null || javaName.isEmpty()) {
                javaName = defaultContextName;
            }
            if (eventName == null || eventName.isEmpty()) {
                eventName = "onCreate";
            }
            
            // Apply prefix if ID is present and context matches
            if (id != null && !id.isEmpty() && !eventName.startsWith(id + "_")) {
                eventName = id + "_" + eventName;
            }
            
            if (javaName != null && !javaName.isEmpty()) {
                addEventToActivityAndInjectCode(scId, javaName, eventName, code);
                return true;
            }
            return false;
        }

        if ("delete_moreblock".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;
            String javaName = data.java_name != null ? data.java_name
                    : (data.attributes != null ? data.attributes.get("java_name") : null);
            String mbName = data.name != null ? data.name : (data.attributes != null ? data.attributes.get("name") : null);
            if (mbName == null) return false;
            if (javaName == null || javaName.isEmpty()) javaName = defaultContextName;
            if (javaName == null || javaName.isEmpty()) return false;
            try {
                // Remove spec from the MoreBlock list
                java.util.ArrayList<android.util.Pair<String, String>> mbs = jC.a(scId).i(javaName);
                if (mbs != null) {
                    String finalMbName = mbName;
                    mbs.removeIf(p -> finalMbName.equals(p.first));
                }
                // Clear the body blocks
                jC.a(scId).a(javaName, mbName + "_moreBlock", new java.util.ArrayList<>());
                jC.a(scId).k();
                pro.sketchware.utility.SketchwareUtil.toast("MoreBlock '" + mbName + "' removido.");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        if ("update_moreblock".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;

            String javaName = data.java_name != null ? data.java_name
                    : (data.attributes != null ? data.attributes.get("java_name") : null);
            String mbName = data.name != null ? data.name : (data.attributes != null ? data.attributes.get("name") : null);
            String spec = data.spec != null ? data.spec : (data.attributes != null ? data.attributes.get("spec") : null);
            String code = data.code != null ? data.code : (data.attributes != null ? data.attributes.get("code") : null);

            if (mbName == null) return false;
            if (javaName == null || javaName.isEmpty()) javaName = defaultContextName;

            if (javaName != null && !javaName.isEmpty()) {
                updateMoreBlockAndCode(scId, javaName, mbName, spec, code);
                return true;
            }
            return false;
        }

        if ("add_moreblock".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;

            String javaName = data.java_name != null ? data.java_name
                    : (data.attributes != null ? data.attributes.get("java_name") : null);
            String mbName = data.name != null ? data.name : (data.attributes != null ? data.attributes.get("name") : null);
            String spec = data.spec != null ? data.spec : (data.attributes != null ? data.attributes.get("spec") : null);
            String code = data.code != null ? data.code : (data.attributes != null ? data.attributes.get("code") : null);

            if (mbName == null || spec == null) return false;
            if (javaName == null || javaName.isEmpty()) javaName = defaultContextName;

            if (javaName != null && !javaName.isEmpty()) {
                addMoreBlockAndInjectCode(scId, javaName, mbName, spec, code);
                return true;
            }
            return false;
        }

        if ("add_import".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;
            String javaName = data.java_name != null ? data.java_name
                    : (data.attributes != null ? data.attributes.get("java_name") : null);
            String importCode = data.code != null ? data.code
                    : (data.attributes != null ? data.attributes.get("code") : null);
            if (importCode == null && data.xml_content != null) importCode = data.xml_content;
            if (importCode == null) return false;
            if (javaName == null || javaName.isEmpty()) javaName = defaultContextName;
            if (javaName != null && !javaName.isEmpty()) {
                addEventToActivityAndInjectCode(scId, javaName, "Import", importCode, false);
                return true;
            }
            return false;
        }

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

                // Add blocks (skip duplicates by opCode or name)
                for (java.util.Map<String, Object> blockDef : data.blocks) {
                    java.util.HashMap<String, Object> newBlock = new java.util.HashMap<>(blockDef);

                    // Sanitization: Ensure "name" exists and is a String
                    if (!newBlock.containsKey("name") || newBlock.get("name") == null) {
                        Object opCode = newBlock.get("opCode");
                        if (opCode != null) {
                            newBlock.put("name", String.valueOf(opCode));
                        } else {
                            newBlock.put("name", "block_" + System.currentTimeMillis() % 10000);
                        }
                    }

                    // Deduplication: skip if a block with same opCode or name already exists
                    String newOpCode = newBlock.get("opCode") != null ? String.valueOf(newBlock.get("opCode")) : null;
                    String newName = String.valueOf(newBlock.get("name"));
                    boolean alreadyExists = false;
                    for (java.util.HashMap<String, Object> existing : existingBlocks) {
                        String exOpCode = existing.get("opCode") != null ? String.valueOf(existing.get("opCode")) : null;
                        String exName = existing.get("name") != null ? String.valueOf(existing.get("name")) : null;
                        if ((newOpCode != null && newOpCode.equals(exOpCode)) || newName.equals(exName)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (alreadyExists) continue;

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

        if ("update_custom_block".equals(op.op)) {
            OperationData data = op.data;
            if ((data.name == null && data.op_code == null) || data.blocks == null || data.blocks.isEmpty()) return false;
            try {
                String blocksPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + mod.hilal.saif.activities.tools.ConfigActivity.getStringSettingValueOrSetAndGet(
                        mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                        (String) mod.hilal.saif.activities.tools.ConfigActivity.getDefaultValue(mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH)
                    );
                if (!pro.sketchware.utility.FileUtil.isExistFile(blocksPath)) {
                    pro.sketchware.utility.SketchwareUtil.toastError("No custom blocks found");
                    return false;
                }
                java.util.ArrayList<java.util.HashMap<String, Object>> existingBlocks =
                    pro.sketchware.utility.GsonUtils.getGson().fromJson(pro.sketchware.utility.FileUtil.readFile(blocksPath), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                if (existingBlocks == null) existingBlocks = new java.util.ArrayList<>();
                java.util.Map<String, Object> newDef = data.blocks.get(0);
                String findName = data.name;
                String findOpCode = data.op_code;
                boolean updated = false;
                for (int i = 0; i < existingBlocks.size(); i++) {
                    java.util.HashMap<String, Object> blk = existingBlocks.get(i);
                    String exName = blk.get("name") != null ? String.valueOf(blk.get("name")) : null;
                    String exOpCode = blk.get("opCode") != null ? String.valueOf(blk.get("opCode")) : null;
                    boolean match = (findName != null && findName.equals(exName)) || (findOpCode != null && findOpCode.equals(exOpCode));
                    if (match) {
                        // Preserve existing palette assignment if not overridden
                        java.util.HashMap<String, Object> updatedBlock = new java.util.HashMap<>(newDef);
                        if (!updatedBlock.containsKey("palette") && blk.get("palette") != null) {
                            updatedBlock.put("palette", blk.get("palette"));
                        }
                        existingBlocks.set(i, updatedBlock);
                        updated = true;
                        break;
                    }
                }
                if (!updated) {
                    pro.sketchware.utility.SketchwareUtil.toastError("Block not found: " + (findName != null ? findName : findOpCode));
                    return false;
                }
                pro.sketchware.utility.FileUtil.writeFile(blocksPath, pro.sketchware.utility.GsonUtils.getGson().toJson(existingBlocks));
                mod.hey.studios.editor.manage.block.v2.BlockLoader.refresh();
                pro.sketchware.utility.SketchwareUtil.toast("Block updated: " + (findName != null ? findName : findOpCode));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                pro.sketchware.utility.SketchwareUtil.toastError("Failed to update custom block");
                return false;
            }
        }

        if ("delete_custom_block".equals(op.op)) {
            OperationData data = op.data;
            if (data.name == null && data.op_code == null) return false;
            try {
                String blocksPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + mod.hilal.saif.activities.tools.ConfigActivity.getStringSettingValueOrSetAndGet(
                        mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                        (String) mod.hilal.saif.activities.tools.ConfigActivity.getDefaultValue(mod.hilal.saif.activities.tools.ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH)
                    );
                if (!pro.sketchware.utility.FileUtil.isExistFile(blocksPath)) return false;
                java.util.ArrayList<java.util.HashMap<String, Object>> existingBlocks =
                    pro.sketchware.utility.GsonUtils.getGson().fromJson(pro.sketchware.utility.FileUtil.readFile(blocksPath), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                if (existingBlocks == null || existingBlocks.isEmpty()) return false;
                String findName = data.name;
                String findOpCode = data.op_code;
                int removed = 0;
                for (int i = existingBlocks.size() - 1; i >= 0; i--) {
                    java.util.HashMap<String, Object> blk = existingBlocks.get(i);
                    String exName = blk.get("name") != null ? String.valueOf(blk.get("name")) : null;
                    String exOpCode = blk.get("opCode") != null ? String.valueOf(blk.get("opCode")) : null;
                    boolean match = (findName != null && findName.equals(exName)) || (findOpCode != null && findOpCode.equals(exOpCode));
                    if (match) {
                        existingBlocks.remove(i);
                        removed++;
                    }
                }
                if (removed == 0) {
                    pro.sketchware.utility.SketchwareUtil.toastError("Block not found: " + (findName != null ? findName : findOpCode));
                    return false;
                }
                pro.sketchware.utility.FileUtil.writeFile(blocksPath, pro.sketchware.utility.GsonUtils.getGson().toJson(existingBlocks));
                mod.hey.studios.editor.manage.block.v2.BlockLoader.refresh();
                pro.sketchware.utility.SketchwareUtil.toast("Block '" + (findName != null ? findName : findOpCode) + "' deleted.");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                pro.sketchware.utility.SketchwareUtil.toastError("Failed to delete custom block");
                return false;
            }
        }

        if ("delete_palette".equals(op.op)) {
            OperationData data = op.data;
            if (data.palette_name == null) return false;
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
                if (!pro.sketchware.utility.FileUtil.isExistFile(palettePath)) return false;
                java.util.ArrayList<java.util.HashMap<String, Object>> palettes =
                    pro.sketchware.utility.GsonUtils.getGson().fromJson(pro.sketchware.utility.FileUtil.readFile(palettePath), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                if (palettes == null || palettes.isEmpty()) return false;
                int deletedIdx = -1;
                for (int i = 0; i < palettes.size(); i++) {
                    if (data.palette_name.equals(palettes.get(i).get("name"))) {
                        deletedIdx = i;
                        break;
                    }
                }
                if (deletedIdx == -1) {
                    pro.sketchware.utility.SketchwareUtil.toastError("Palette not found: " + data.palette_name);
                    return false;
                }
                final int deletedPaletteId = deletedIdx + 9;
                palettes.remove(deletedIdx);
                pro.sketchware.utility.FileUtil.writeFile(palettePath, pro.sketchware.utility.GsonUtils.getGson().toJson(palettes));
                // Remove blocks of deleted palette and fix IDs of blocks in higher palettes
                if (pro.sketchware.utility.FileUtil.isExistFile(blocksPath)) {
                    java.util.ArrayList<java.util.HashMap<String, Object>> existingBlocks =
                        pro.sketchware.utility.GsonUtils.getGson().fromJson(pro.sketchware.utility.FileUtil.readFile(blocksPath), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                    if (existingBlocks != null) {
                        java.util.ArrayList<java.util.HashMap<String, Object>> kept = new java.util.ArrayList<>();
                        for (java.util.HashMap<String, Object> blk : existingBlocks) {
                            int pid = -1;
                            try { pid = (int) Double.parseDouble(String.valueOf(blk.get("palette"))); } catch (Exception ignored) {}
                            if (pid == deletedPaletteId) continue; // delete this block
                            if (pid > deletedPaletteId) blk.put("palette", String.valueOf((long)(pid - 1))); // shift down
                            kept.add(blk);
                        }
                        pro.sketchware.utility.FileUtil.writeFile(blocksPath, pro.sketchware.utility.GsonUtils.getGson().toJson(kept));
                    }
                }
                mod.hey.studios.editor.manage.block.v2.BlockLoader.refresh();
                pro.sketchware.utility.SketchwareUtil.toast("Palette '" + data.palette_name + "' deleted.");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                pro.sketchware.utility.SketchwareUtil.toastError("Failed to delete palette");
                return false;
            }
        }

        if ("add_drawable".equals(op.op)) {
            OperationData data = op.data;
            if (data.drawable_name == null || data.xml_content == null) {
                 pro.sketchware.utility.SketchwareUtil.toastError("Erro add_drawable: dados ausentes.");
                 return false;
            }
            try {
                String resPath = pro.sketchware.utility.FileUtil.getExternalStorageDir() 
                    + "/.sketchware/data/" + scId + "/files/resource/drawable/";
                
                if (!pro.sketchware.utility.FileUtil.isExistFile(resPath)) {
                    pro.sketchware.utility.FileUtil.makeDir(resPath);
                }

                String targetPath = resPath + data.drawable_name + ".xml";
                pro.sketchware.utility.FileUtil.writeFile(targetPath, formatXml(data.xml_content));
                pro.sketchware.utility.SketchwareUtil.toast("Drawable '" + data.drawable_name + "' salvo!");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                pro.sketchware.utility.SketchwareUtil.toastError("Erro ao salvar drawable: " + e.getMessage());
                return false;
            }
        }

        if ("delete_drawable".equals(op.op)) {
            OperationData data = op.data;
            if (data == null || data.drawable_name == null) return false;
            try {
                String resPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + "/.sketchware/data/" + scId + "/files/resource/drawable/";
                String targetPath = resPath + data.drawable_name + ".xml";
                if (pro.sketchware.utility.FileUtil.isExistFile(targetPath)) {
                    pro.sketchware.utility.FileUtil.deleteFile(targetPath);
                    pro.sketchware.utility.SketchwareUtil.toast("Drawable '" + data.drawable_name + "' deletado.");
                    return true;
                }
                return false;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        if ("add_icon_resource".equals(op.op)) {
            OperationData data = op.data;
            if (data.icon_name == null) return false;
            
            String style = (data.style != null) ? data.style : "round";
            String color = (data.color != null) ? data.color : "#FFFFFF"; // Default white
            
            try {
                String iconStore = a.a.a.wq.getExtractedIconPackStoreLocation();
                if (!pro.sketchware.utility.FileUtil.isExistFile(iconStore + "/svg/")) {
                    pro.sketchware.utility.SketchwareUtil.toast("Extraindo pack de ícones (apenas uma vez)...");
                    a.a.a.KB.a(pro.sketchware.SketchApplication.getContext(), "icons/icon_pack.zip", iconStore);
                }

                String iconPackPath = iconStore + "/svg/" + data.icon_name + "/" + style + ".svg";
                if (!pro.sketchware.utility.FileUtil.isExistFile(iconPackPath)) {
                    // Try fallback icons if not found precisely
                    pro.sketchware.utility.SketchwareUtil.toastError("Ícone '" + data.icon_name + "' não encontrado no pack.");
                    return false;
                }

                String resPath = pro.sketchware.utility.FileUtil.getExternalStorageDir() 
                    + "/.sketchware/data/" + scId + "/files/resource/drawable/";
                if (!pro.sketchware.utility.FileUtil.isExistFile(resPath)) pro.sketchware.utility.FileUtil.makeDir(resPath);

                // Import using SvgUtils
                pro.sketchware.utility.SvgUtils svgUtils = new pro.sketchware.utility.SvgUtils(pro.sketchware.SketchApplication.getContext());
                svgUtils.convert(iconPackPath, resPath, color);

                // Rename if a custom drawable_name is provided and it's valid
                String finalName = data.drawable_name != null ? data.drawable_name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase() : data.icon_name;
                String generatedXml = resPath + data.icon_name + ".xml";
                String targetXml = resPath + finalName + ".xml";

                if (pro.sketchware.utility.FileUtil.isExistFile(generatedXml) && !generatedXml.equals(targetXml)) {
                    pro.sketchware.utility.FileUtil.copyFile(generatedXml, targetXml);
                    pro.sketchware.utility.FileUtil.deleteFile(generatedXml);
                }

                // Validate the generated file — SvgUtils can silently write an empty file,
                // which causes AAPT "no element found" compile errors.
                String xmlContent = pro.sketchware.utility.FileUtil.isExistFile(targetXml)
                        ? pro.sketchware.utility.FileUtil.readFile(targetXml) : null;
                if (xmlContent == null || xmlContent.trim().isEmpty() || !xmlContent.contains("<")) {
                    // Remove the empty/corrupt file so it doesn't cause compile errors
                    if (pro.sketchware.utility.FileUtil.isExistFile(targetXml)) {
                        pro.sketchware.utility.FileUtil.deleteFile(targetXml);
                    }
                    pro.sketchware.utility.SketchwareUtil.toastError(
                        "Ícone '" + data.icon_name + "': conversão SVG falhou. Use add_drawable com <vector> XML.");
                    return false;
                }

                // Register in project resources so it shows up in Image Manager
                java.util.ArrayList<com.besome.sketch.beans.ProjectResourceBean> images = a.a.a.jC.d(scId).b;
                if (images == null) images = new java.util.ArrayList<>();
                
                boolean exists = false;
                for (com.besome.sketch.beans.ProjectResourceBean b : images) {
                    if (finalName.equals(b.resName)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    com.besome.sketch.beans.ProjectResourceBean resource = new com.besome.sketch.beans.ProjectResourceBean(
                        com.besome.sketch.beans.ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                        finalName,
                        finalName + ".xml"
                    );
                    resource.isNew = true;
                    images.add(resource);
                    a.a.a.jC.d(scId).b(images);
                    a.a.a.jC.d(scId).y(); // Save project metadata
                    a.a.a.jC.a(scId).k(); // Refresh project resources
                }

                // Auto-apply to target view if requested
                if (data.target_view_id != null) {
                    String xmlContext = data.target_xml_name != null ? data.target_xml_name : currentXmlName;
                    if (xmlContext != null) {
                        String baseTarget = xmlContext.replace(".xml", "");
                        com.besome.sketch.beans.ViewBean targetBean = a.a.a.jC.a(scId).c(baseTarget, data.target_view_id);
                        if (targetBean != null) {
                            java.util.Map<String, String> attrs = new java.util.HashMap<>();
                            attrs.put("android:src", "@drawable/" + finalName);
                            new pro.sketchware.tools.ViewBeanFactory(targetBean).applyAttributes(attrs);
                            pro.sketchware.utility.SketchwareUtil.toast("Ícone aplicado em '" + data.target_view_id + "'");
                        }
                    }
                }

                pro.sketchware.utility.SketchwareUtil.toast("Ícone '" + finalName + "' importado com sucesso!");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                pro.sketchware.utility.SketchwareUtil.toastError("Falha ao importar ícone: " + e.getMessage());
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
            String finalId = data.widget_id != null ? data.widget_id : data.id;
            int finalType = data.widget_type != -1 ? data.widget_type : data.type;
            
            if (xmlName == null || finalId == null) return false;
            
            if (finalType == -1) finalType = 0; // Default to LinearLayout
            
            com.besome.sketch.beans.ViewBean view = new com.besome.sketch.beans.ViewBean(finalId, finalType);
            view.id = finalId;
            view.type = finalType;
            
            ArrayList<ViewBean> siblings = jC.a(scId).d(xmlName);
            boolean emptyScreen = (siblings == null || siblings.isEmpty());
            
            if (data.parent_id != null) {
                view.parent = data.parent_id;
                view.preParent = data.parent_id;
            } else if (data.parent != null) { // Alias support
                view.parent = data.parent;
                view.preParent = data.parent;
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
                } else if ("root".equals(view.parent)) {
                    view.parentType = 0; // LinearLayout
                    view.preParentType = 0;
                } else {
                    view.parentType = 0;
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
            view.preId = view.id;

            if (data.attributes != null) {
                new pro.sketchware.tools.ViewBeanFactory(view).applyAttributes(data.attributes);
            }
            
            // Handle Alias "params"
            if (data.params != null) {
                java.util.Map<String, String> attrs = new java.util.HashMap<>();
                for (java.util.Map.Entry<String, Object> entry : data.params.entrySet()) {
                    String key = entry.getKey();
                    String val = String.valueOf(entry.getValue());
                    
                    // Simple mapping
                    if ("width".equals(key)) {
                        attrs.put("android:layout_width", "-1".equals(val) || "match_parent".equals(val) ? "match_parent" : "-2".equals(val) || "wrap_content".equals(val) ? "wrap_content" : val);
                    } else if ("height".equals(key)) {
                        attrs.put("android:layout_height", "-1".equals(val) || "match_parent".equals(val) ? "match_parent" : "-2".equals(val) || "wrap_content".equals(val) ? "wrap_content" : val);
                    } else if ("orientation".equals(key)) {
                        attrs.put("android:orientation", "1".equals(val) || "vertical".equals(val) ? "vertical" : "horizontal");
                    } else if ("padding".equals(key)) {
                        attrs.put("android:padding", val.matches("\\d+") ? val + "dp" : val);
                    } else if ("text".equals(key)) {
                        attrs.put("android:text", val);
                    } else {
                        attrs.put(key.contains(":") ? key : "android:" + key, val);
                    }
                }
                new pro.sketchware.tools.ViewBeanFactory(view).applyAttributes(attrs);
            }

            jC.a(scId).a(xmlName, view);
            return true;
        } else if ("update_widget".equals(op.op)) {
            OperationData data = op.data;
            String xmlName = op.xmlName != null ? op.xmlName : data.view_id;
            if (xmlName == null && currentXmlName != null) xmlName = currentXmlName;
            if (xmlName == null || data.widget_id == null) return false;
            
            // Normalize XML names for comparison
            String normXml = xmlName.endsWith(".xml") ? xmlName : xmlName + ".xml";
            String normCurrent = (currentXmlName != null) ? (currentXmlName.endsWith(".xml") ? currentXmlName : currentXmlName + ".xml") : null;
            
            if (normCurrent != null && !normXml.equalsIgnoreCase(normCurrent)) {
                normXml = normCurrent; // Prefer active context if they differ but match roughly
            }

            try {
                ViewBean existing = jC.a(scId).c(normXml, data.widget_id);
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
            
            // Normalize XML names for comparison
            String normXml = xmlName.endsWith(".xml") ? xmlName : xmlName + ".xml";
            String normCurrent = (currentXmlName != null) ? (currentXmlName.endsWith(".xml") ? currentXmlName : currentXmlName + ".xml") : null;

            if (normCurrent != null && !normXml.equalsIgnoreCase(normCurrent)) {
                normXml = normCurrent;
            }

            try {
                ViewBean existing = jC.a(scId).c(normXml, data.widget_id);
                if (existing != null) {
                    ProjectFileBean fileBean = null;
                    for (ProjectFileBean pfb : jC.b(scId).b()) {
                        if (pfb.getXmlName().equals(normXml)) {
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
        } else if ("edit_layout_xml".equals(op.op)) {
            OperationData data = op.data;
            String xmlName = op.xmlName != null ? op.xmlName : data.view_id;
            if (xmlName == null && currentXmlName != null) xmlName = currentXmlName;
            if (xmlName == null || data.xml_content == null) return false;

            // Safety check: Don't allow Drawable content in a Layout operation
            String contentLower = data.xml_content.toLowerCase();
            if (contentLower.contains("<shape") || contentLower.contains("<selector") || contentLower.contains("<layer-list") || contentLower.contains("<gradient")) {
                 pro.sketchware.utility.SketchwareUtil.toastError("Bloqueado: IA tentou salvar Drawable como Layout. Corrigindo...");
                 return false;
            }
            
            String normXml = xmlName.endsWith(".xml") ? xmlName : xmlName + ".xml";
            String baseXmlName = normXml.replace(".xml", "");
            
            try {
                // Resource Manager / Professional Design support:
                // If the file doesn't exist, create it as a Custom View
                if (jC.b(scId).b(normXml) == null) {
                    com.besome.sketch.beans.ProjectFileBean newFile = new com.besome.sketch.beans.ProjectFileBean(
                        com.besome.sketch.beans.ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW,
                        baseXmlName
                    );
                    jC.b(scId).a(newFile);
                    jC.b(scId).j(); // Save project metadata changes
                }

                // Infallible Mode: Parse entire XML into ViewBeans, just like Direct XML Editor
                pro.sketchware.tools.ViewBeanParser parser = new pro.sketchware.tools.ViewBeanParser(data.xml_content);
                parser.setSkipRoot(true);
                ArrayList<ViewBean> parsedLayout = parser.parse();
                android.util.Pair<String, java.util.Map<String, String>> root = parser.getRootAttributes();

                // 1. Update InjectRootLayoutManager for root attributes
                pro.sketchware.managers.inject.InjectRootLayoutManager rootMgr = new pro.sketchware.managers.inject.InjectRootLayoutManager(scId);
                rootMgr.set(baseXmlName, pro.sketchware.managers.inject.InjectRootLayoutManager.toRoot(root));

                // 2. Prepare History/Undo
                com.besome.sketch.beans.HistoryViewBean historyBean = new com.besome.sketch.beans.HistoryViewBean();
                ArrayList<ViewBean> existing = jC.a(scId).d(baseXmlName);
                if (existing == null) existing = new ArrayList<>();
                historyBean.actionOverride(parsedLayout, existing);
                a.a.a.cC historyManager = a.a.a.cC.c(scId);
                if (!historyManager.c.containsKey(baseXmlName)) {
                    historyManager.e(baseXmlName);
                }
                historyManager.a(baseXmlName);
                historyManager.a(baseXmlName, historyBean);

                // 3. Replace the view beans in the project model
                jC.a(scId).c.put(baseXmlName, parsedLayout);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        // ── Add / register a view event (onClick, onLongClick, etc.) ────────
        if ("add_view_event".equals(op.op)) {
            OperationData data = op.data;
            // Resolve view ID
            String viewId = data.view_id != null ? data.view_id
                    : (data.widget_id != null ? data.widget_id : data.id);
            if (viewId == null || viewId.trim().isEmpty()) return false;

            String eventName = (data.event_name != null && !data.event_name.trim().isEmpty())
                    ? data.event_name : "onClick";
            String code = data.code != null ? data.code : "";

            // Resolve XML name (layout)
            String xmlName = op.xmlName != null ? op.xmlName
                    : (data.target_xml_name != null ? data.target_xml_name : currentXmlName);
            if (xmlName == null) return false;
            String normXml = xmlName.replace(".xml", "");

            // Resolve Java name (activity)
            String javaName = data.java_name != null ? data.java_name : defaultContextName;
            if (javaName == null) return false;
            String normJava = javaName.endsWith(".java") ? javaName : javaName + ".java";

            // Look up the view to get its type
            com.besome.sketch.beans.ViewBean view = jC.a(scId).c(normXml, viewId);
            int viewType = view != null ? view.type : 3; // default to Button type

            // Register the EventBean with correct VIEW type so it appears in the UI
            jC.a(scId).a(normJava,
                    com.besome.sketch.beans.EventBean.EVENT_TYPE_VIEW,
                    viewType, viewId, eventName);

            // Inject code (event now exists in g(), so addEventToActivityAndInjectCode
            // will find it and use the correct key)
            if (!code.trim().isEmpty()) {
                addEventToActivityAndInjectCode(scId, normJava, viewId + "_" + eventName, code);
            } else {
                // Register empty event slot so it shows up in Logic Editor
                jC.a(scId).a(normJava, viewId + "_" + eventName, new java.util.ArrayList<>());
            }
            return true;
        }

        // ── AndroidManifest permission operations ────────────────────────────
        if ("add_permission".equals(op.op) || "remove_permission".equals(op.op)) {
            OperationData data = op.data;
            // permission name can be in data.name or data.code (AI flexibility)
            String permission = data.name != null ? data.name.trim()
                    : (data.code != null ? data.code.trim() : null);
            if (permission == null || permission.isEmpty()) return false;
            // Normalize: accept short form "INTERNET" → "android.permission.INTERNET"
            if (!permission.contains(".")) {
                permission = "android.permission." + permission.toUpperCase();
            }

            pro.sketchware.utility.FilePathUtil fpu = new pro.sketchware.utility.FilePathUtil();
            String path = fpu.getPathPermission(scId);

            java.util.ArrayList<String> perms = new java.util.ArrayList<>();
            String existing = pro.sketchware.utility.FileUtil.readFile(path);
            if (existing != null && !existing.isEmpty()) {
                try {
                    perms = new com.google.gson.Gson().fromJson(existing, mod.hey.studios.util.Helper.TYPE_STRING);
                } catch (Exception ignored) {}
            }
            if (perms == null) perms = new java.util.ArrayList<>();

            if ("add_permission".equals(op.op)) {
                if (!perms.contains(permission)) {
                    perms.add(permission);
                    pro.sketchware.utility.FileUtil.writeFile(path, new com.google.gson.Gson().toJson(perms));
                }
            } else {
                perms.remove(permission);
                pro.sketchware.utility.FileUtil.writeFile(path, new com.google.gson.Gson().toJson(perms));
            }
            return true;
        }

        // ── Variable operations ──────────────────────────────────────────────
        if ("add_variable".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;
            String javaName = data.java_name != null ? data.java_name : defaultContextName;
            if (javaName == null || javaName.trim().isEmpty()) return false;
            javaName = javaName.endsWith(".java") ? javaName : javaName + ".java";
            String varName = data.name != null ? data.name.trim() : null;
            if (varName == null || varName.isEmpty()) return false;
            String typeStr = (data.var_type != null ? data.var_type : (data.code != null ? data.code : "String")).trim().toLowerCase();
            int typeConst;
            switch (typeStr) {
                case "boolean": case "bool": typeConst = 0; break;
                case "number": case "int": case "integer": case "double": case "float": typeConst = 1; break;
                case "map": case "hashmap": typeConst = 3; break;
                default: typeConst = 2; break; // String
            }
            try {
                jC.a(scId).f(javaName, typeConst, varName);
                jC.a(scId).k();
            } catch (Exception e) {
                android.util.Log.e("SdbEditEngine", "add_variable failed", e);
                return false;
            }
            return true;
        }

        // ── List operations ──────────────────────────────────────────────────
        if ("add_list".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;
            String javaName = data.java_name != null ? data.java_name : defaultContextName;
            if (javaName == null || javaName.trim().isEmpty()) return false;
            javaName = javaName.endsWith(".java") ? javaName : javaName + ".java";
            String listName = data.name != null ? data.name.trim() : null;
            if (listName == null || listName.isEmpty()) return false;
            String typeStr = (data.list_type != null ? data.list_type : (data.var_type != null ? data.var_type : "String")).trim().toLowerCase();
            int typeConst;
            switch (typeStr) {
                case "number": case "int": case "integer": typeConst = 1; break;
                case "map": case "hashmap": typeConst = 3; break;
                default: typeConst = 2; break; // String
            }
            try {
                jC.a(scId).e(javaName, typeConst, listName);
                jC.a(scId).k();
            } catch (Exception e) {
                android.util.Log.e("SdbEditEngine", "add_list failed", e);
                return false;
            }
            return true;
        }

        // ── Set custom view on a ListView (connects layout to adapter) ───────
        if ("set_custom_view".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;
            // Resolve XML layout name (the screen that contains the ListView)
            String xmlName = op.xmlName;
            if (xmlName == null && data.target_xml_name != null) xmlName = data.target_xml_name;
            if (xmlName == null) xmlName = currentXmlName;
            if (xmlName == null) return false;
            String normXml = xmlName.endsWith(".xml") ? xmlName : xmlName + ".xml";

            // View ID of the ListView widget
            String viewId = data.view_id != null ? data.view_id : (data.widget_id != null ? data.widget_id : data.id);
            if (viewId == null || viewId.trim().isEmpty()) return false;

            // Custom view layout name (the item layout to use)
            String customViewName = data.custom_view != null ? data.custom_view
                    : (data.name != null ? data.name : null);
            if (customViewName == null || customViewName.trim().isEmpty()) return false;
            customViewName = customViewName.replace(".xml", "").trim();

            try {
                // Find and update the ViewBean
                com.besome.sketch.beans.ViewBean view = jC.a(scId).c(normXml, viewId);
                if (view == null) return false;
                view.customView = customViewName;

                // Ensure the custom view layout file exists (create if needed)
                String cvXml = customViewName + ".xml";
                if (jC.b(scId).b(cvXml) == null) {
                    com.besome.sketch.beans.ProjectFileBean newFile = new com.besome.sketch.beans.ProjectFileBean(
                            com.besome.sketch.beans.ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW,
                            customViewName);
                    jC.b(scId).a(newFile);
                    jC.b(scId).j(); // persist file registry (same as edit_layout_xml does)
                }
                // Persist the ViewBean change (customView field)
                jC.a(scId).k();
            } catch (Exception e) {
                android.util.Log.e("SdbEditEngine", "set_custom_view failed", e);
                return false;
            }
            return true;
        }

        // ── Material3 enable operation ───────────────────────────────────────
        if ("enable_material3".equals(op.op)) {
            try {
                com.besome.sketch.beans.ProjectLibraryBean compat = jC.c(scId).c();
                if (compat.configurations == null) compat.configurations = new java.util.HashMap<>();
                compat.useYn = "Y"; // ensure AppCompat is enabled
                compat.configurations.put("material3", true);
                if (!compat.configurations.containsKey("theme")) {
                    compat.configurations.put("theme", "DayNight");
                }
                jC.c(scId).b(compat);
                jC.c(scId).k();
            } catch (Exception e) {
                android.util.Log.e("SdbEditEngine", "enable_material3 failed", e);
                return false;
            }
            return true;
        }

        // ── Java file operations ─────────────────────────────────────────────
        if ("create_java_file".equals(op.op) || "edit_java_file".equals(op.op)) {
            OperationData data = op.data;
            if (data == null || data.file_name == null || data.file_name.trim().isEmpty()) return false;
            // Support both "MyHelper" and "com.example.MyHelper" — use only the simple class name for the file
            String simpleName = data.file_name.contains(".")
                    ? data.file_name.substring(data.file_name.lastIndexOf('.') + 1)
                    : data.file_name;
            if (!simpleName.endsWith(".java")) simpleName += ".java";
            String javaDir = new pro.sketchware.utility.FilePathUtil().getPathJava(scId);
            new java.io.File(javaDir).mkdirs();
            String code = data.content != null ? data.content : "";
            pro.sketchware.utility.FileUtil.writeFile(javaDir + java.io.File.separator + simpleName, code);
            return true;
        }

        if ("delete_java_file".equals(op.op)) {
            OperationData data = op.data;
            if (data == null || data.file_name == null || data.file_name.trim().isEmpty()) return false;
            String simpleName = data.file_name.contains(".")
                    ? data.file_name.substring(data.file_name.lastIndexOf('.') + 1)
                    : data.file_name;
            if (!simpleName.endsWith(".java")) simpleName += ".java";
            String javaDir = new pro.sketchware.utility.FilePathUtil().getPathJava(scId);
            new java.io.File(javaDir + java.io.File.separator + simpleName).delete();
            return true;
        }

        // ── Add component (Firebase, RequestNetwork, SharedPrefs, Timer, etc.) ─
        if ("add_component".equals(op.op)) {
            OperationData data = op.data;
            if (data == null) return false;

            String javaName = data.java_name != null ? data.java_name : defaultContextName;
            if (javaName == null || javaName.trim().isEmpty()) return false;
            javaName = javaName.endsWith(".java") ? javaName : javaName + ".java";

            String componentId = data.name != null ? data.name.trim() : null;
            String typeStr = data.component_type != null ? data.component_type.trim().toLowerCase() : null;
            if (componentId == null || componentId.isEmpty()) return false;
            if (typeStr == null || typeStr.isEmpty()) return false;

            int typeConst;
            switch (typeStr) {
                case "sharedpreferences": case "sharedpref": case "file":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_SHAREDPREF; break;
                case "calendar":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_CALENDAR; break;
                case "timer": case "timertask":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_TIMERTASK; break;
                case "vibrator":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_VIBRATOR; break;
                case "soundpool":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_SOUNDPOOL; break;
                case "mediaplayer":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_MEDIAPLAYER; break;
                case "texttospeech": case "tts":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_TEXT_TO_SPEECH; break;
                case "speechtotext": case "stt": case "speech":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_SPEECH_TO_TEXT; break;
                case "camera":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_CAMERA; break;
                case "firebasedb": case "firebase": case "database": case "rtdb":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_FIREBASE; break;
                case "firebaseauth": case "auth":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH; break;
                case "firebasestorage": case "storage":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE; break;
                case "requestnetwork": case "network": case "http":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK; break;
                case "bluetoothconnect": case "bluetooth":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_BLUETOOTH_CONNECT; break;
                case "locationmanager": case "location":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_LOCATION_MANAGER; break;
                case "cloudmessaging": case "fcm": case "cloudmessage": case "firebasecloudmessage":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_FIREBASE_CLOUD_MESSAGE; break;
                case "interstitialad": case "interstitial":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD; break;
                case "rewardedad": case "rewarded": case "rewardedvideoad":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_REWARDED_VIDEO_AD; break;
                case "dialog":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_DIALOG; break;
                case "progressdialog":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_PROGRESS_DIALOG; break;
                case "notification":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_NOTIFICATION; break;
                case "gyroscope":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_GYROSCOPE; break;
                case "filepicker":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_FILE_PICKER; break;
                case "objectanimator": case "animator":
                    typeConst = com.besome.sketch.beans.ComponentBean.COMPONENT_TYPE_OBJECTANIMATOR; break;
                default:
                    pro.sketchware.utility.SketchwareUtil.toastError("add_component: tipo desconhecido: " + data.component_type);
                    return false;
            }

            try {
                // Register the component in the project data
                jC.a(scId).d(javaName, typeConst, componentId);

                // If param1 is provided (Firebase ref path, SharedPrefs filename), set it
                if (data.param1 != null && !data.param1.trim().isEmpty()) {
                    java.util.ArrayList<com.besome.sketch.beans.ComponentBean> comps = jC.a(scId).e(javaName);
                    if (comps != null) {
                        for (com.besome.sketch.beans.ComponentBean comp : comps) {
                            if (componentId.equals(comp.componentId) && comp.type == typeConst) {
                                comp.param1 = data.param1.trim();
                                break;
                            }
                        }
                    }
                }

                jC.a(scId).k();
                pro.sketchware.utility.SketchwareUtil.toast("Componente '" + componentId + "' adicionado.");
                return true;
            } catch (Exception e) {
                android.util.Log.e("SdbEditEngine", "add_component failed", e);
                pro.sketchware.utility.SketchwareUtil.toastError("Falha ao adicionar componente: " + e.getMessage());
                return false;
            }
        }

        return false;
    }

    public static void addEventToActivityAndInjectCode(String scId, String javaName, String eventNameOrKey, String code) {
        addEventToActivityAndInjectCode(scId, javaName, eventNameOrKey, code, false);
    }

    public static void addEventToActivityAndInjectCode(String scId, String javaName, String eventNameOrKey, String code, boolean replace) {
        a.a.a.eC projectData = a.a.a.jC.a(scId);

        String fullEventKey;
        String targetId;
        String cleanEventName;

        // Normaliza javaName (sem .java) para targetId de Atividades
        String activityName = javaName;
        if (activityName.endsWith(".java")) {
            activityName = activityName.substring(0, activityName.length() - 5);
        }

        if ("Import".equals(eventNameOrKey)) {
            targetId = "Import";
            cleanEventName = "Import";
            fullEventKey = "Import";
        } else if (eventNameOrKey.contains("_")) {
            int underscoreIndex = eventNameOrKey.indexOf("_");
            targetId = eventNameOrKey.substring(0, underscoreIndex);
            
            // Corrige targetId se ele contiver .java incorretamente para fins de comparação
            String comparisonTargetId = targetId;
            if (comparisonTargetId.endsWith(".java")) {
                comparisonTargetId = comparisonTargetId.substring(0, comparisonTargetId.length() - 5);
            }
            
            cleanEventName = eventNameOrKey.substring(underscoreIndex + 1);
            fullEventKey = targetId + "_" + cleanEventName;
        } else {
            targetId = activityName;
            cleanEventName = eventNameOrKey;
            fullEventKey = targetId + "_" + cleanEventName;
        }

        java.util.ArrayList<com.besome.sketch.beans.EventBean> events = projectData.g(javaName);
        boolean exists = false;
        if (events != null) {
            for (com.besome.sketch.beans.EventBean eb : events) {
                // Tolerância: aceita tanto com .java quanto sem no targetId de eventos existentes
                String ebTargetClean = eb.targetId != null && eb.targetId.endsWith(".java") ? eb.targetId.substring(0, eb.targetId.length() - 5) : eb.targetId;
                String targetClean = targetId.endsWith(".java") ? targetId.substring(0, targetId.length() - 5) : targetId;
                
                if (targetClean.equals(ebTargetClean) && cleanEventName.equals(eb.eventName)) {
                    exists = true;
                    // Sincroniza o fullEventKey caso o evento já exista com o nome atual
                    fullEventKey = eb.targetId + "_" + eb.eventName;
                    break;
                }
            }
        }
        
        if (!exists) {
            String targetClean = targetId.endsWith(".java") ? targetId.substring(0, targetId.length() - 5) : targetId;
            com.besome.sketch.beans.EventBean event = new com.besome.sketch.beans.EventBean(
                    com.besome.sketch.beans.EventBean.EVENT_TYPE_ACTIVITY,
                    0,
                    targetClean,
                    cleanEventName
            );
            if (events != null) events.add(event); 
            fullEventKey = targetClean + "_" + cleanEventName;
            projectData.a(javaName, fullEventKey, new java.util.ArrayList<>());
        }

        java.util.ArrayList<com.besome.sketch.beans.BlockBean> blocks = projectData.a(javaName, fullEventKey);
        if (blocks == null) {
            blocks = new java.util.ArrayList<>();
            projectData.a(javaName, fullEventKey, blocks);
        }
        
        if (replace) {
            blocks.clear();
        }

        // Anti-Duplication: Check if there's already an "AI Sync" block to update
        String aiTag = "// SDBCodFlow Sync Block";
        for (com.besome.sketch.beans.BlockBean b : blocks) {
            if ("addSourceDirectly".equals(b.opCode) && !b.parameters.isEmpty()) {
                String content = b.parameters.get(0);
                if (content != null && content.contains(aiTag)) {
                    if ("Import".equals(fullEventKey) && !replace) {
                        // For imports: APPEND new lines (deduplicated) instead of replacing
                        String existingBody = content.contains("\n")
                                ? content.substring(content.indexOf('\n') + 1)
                                : "";
                        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
                        for (String line : existingBody.split("\n")) {
                            String t = line.trim();
                            if (!t.isEmpty()) lines.add(t);
                        }
                        for (String line : code.split("\n")) {
                            String t = line.trim();
                            if (!t.isEmpty()) lines.add(t);
                        }
                        StringBuilder merged = new StringBuilder(aiTag);
                        for (String line : lines) merged.append("\n").append(line);
                        b.parameters.set(0, merged.toString());
                    } else {
                        // Replace existing sync block
                        b.parameters.set(0, aiTag + "\n" + code);
                    }
                    projectData.y(javaName, fullEventKey);
                    projectData.k();
                    notifyProjectRefresh(scId, fullEventKey);
                    return;
                }
            }
        }

        int maxId = 0;
        for (com.besome.sketch.beans.BlockBean b : blocks) {
            try { 
                int idNum = Integer.parseInt(b.id); 
                if (idNum > maxId) maxId = idNum; 
            } catch (Exception ignored) {}
        }
        maxId++;

        com.besome.sketch.beans.BlockBean directCodeBlock = new com.besome.sketch.beans.BlockBean();
        directCodeBlock.id = String.valueOf(maxId);
        directCodeBlock.spec = "add source directly %s.inputOnly";
        directCodeBlock.type = " ";
        directCodeBlock.opCode = "addSourceDirectly";
        directCodeBlock.color = 0xffeebb55;
        directCodeBlock.parameters.add(aiTag + "\n" + code);
        directCodeBlock.nextBlock = -1;
        directCodeBlock.subStack1 = -1;
        directCodeBlock.subStack2 = -1;
        
        blocks.add(directCodeBlock);
        
        projectData.y(javaName, fullEventKey); // Save event logic
        projectData.k(); // Refresh project
        notifyProjectRefresh(scId, fullEventKey);
    }

    public static void addMoreBlockAndInjectCode(String scId, String javaName, String mbName, String spec, String code) {
        a.a.a.eC projectData = a.a.a.jC.a(scId);

        // Normalize spec: ensure each %s/%d/%b param has a .name suffix (e.g. %s → %s.s)
        // BlockUtil.getVariableBlock does spec.substring(3) which requires length >= 4
        if (spec != null) {
            spec = spec.replaceAll("%([sdb])(?![.])", "%$1.$1");
        } else {
            spec = mbName; // fallback: spec is just the name (void moreblock)
        }

        // Check for duplicate — skip if a MoreBlock with this name already exists
        for (android.util.Pair<String, String> existingMb : projectData.i(javaName)) {
            String existingName = mod.hey.studios.moreblock.ReturnMoreblockManager.getMbName(existingMb.first);
            if (existingName.equals(mbName)) {
                return; // Already exists — do not create duplicate
            }
        }

        // Register MoreBlock definition (same API as MoreblockImporter.createEvent)
        // a(javaName, mbName, spec) stores the MoreBlock spec and registers it in i(javaName)
        projectData.a(javaName, mbName, spec);

        // Build body block list
        java.util.ArrayList<com.besome.sketch.beans.BlockBean> blocks = new java.util.ArrayList<>();
        if (code != null && !code.trim().isEmpty()) {
            com.besome.sketch.beans.BlockBean directCodeBlock = new com.besome.sketch.beans.BlockBean();
            // ID=1000: arg blocks (IDs 1,2,...) have int nextBlock field default=0 in Java;
            // getAllChildren() follows nextBlock=0 → finds block ID 0 → infinite traversal.
            // Using ID=1000 means findBlockById(0) returns null → cycle broken at the source.
            directCodeBlock.id = "1000";
            directCodeBlock.spec = "add source directly %s.inputOnly";
            directCodeBlock.type = " ";
            directCodeBlock.opCode = "addSourceDirectly";
            directCodeBlock.color = 0xffeebb55;
            directCodeBlock.parameters.add(code);
            directCodeBlock.nextBlock = -1;
            directCodeBlock.subStack1 = -1;
            directCodeBlock.subStack2 = -1;
            blocks.add(directCodeBlock);
        }

        // Store body under mbName + "_moreBlock" (same as MoreblockImporter.createEvent)
        projectData.a(javaName, mbName + "_moreBlock", blocks);
        projectData.y(javaName, mbName + "_moreBlock"); // Save MoreBlock body
        projectData.k(); // Refresh project state
        notifyProjectRefresh(scId);
    }

    /**
     * Updates an existing MoreBlock's spec and/or code body.
     * If the MoreBlock doesn't exist yet, creates it via addMoreBlockAndInjectCode.
     */
    public static void updateMoreBlockAndCode(String scId, String javaName, String mbName, String newSpec, String newCode) {
        a.a.a.eC projectData = a.a.a.jC.a(scId);

        // Normalize spec
        if (newSpec != null) {
            newSpec = newSpec.replaceAll("%([sdb])(?![.])", "%$1.$1");
        }

        // Find and update spec entry in the MoreBlock list
        java.util.ArrayList<android.util.Pair<String, String>> mbs = projectData.i(javaName);
        boolean found = false;
        if (mbs != null) {
            for (int i = 0; i < mbs.size(); i++) {
                String existingName = mod.hey.studios.moreblock.ReturnMoreblockManager.getMbName(mbs.get(i).first);
                if (existingName.equals(mbName)) {
                    found = true;
                    if (newSpec != null) {
                        mbs.set(i, new android.util.Pair<>(newSpec, mbs.get(i).second));
                    }
                    break;
                }
            }
        }

        if (!found) {
            // Doesn't exist — delegate to create
            addMoreBlockAndInjectCode(scId, javaName, mbName, newSpec != null ? newSpec : mbName, newCode);
            return;
        }

        // Replace body blocks
        java.util.ArrayList<com.besome.sketch.beans.BlockBean> blocks = new java.util.ArrayList<>();
        if (newCode != null && !newCode.trim().isEmpty()) {
            com.besome.sketch.beans.BlockBean block = new com.besome.sketch.beans.BlockBean();
            block.id = "1000";
            block.spec = "add source directly %s.inputOnly";
            block.type = " ";
            block.opCode = "addSourceDirectly";
            block.color = 0xffeebb55;
            block.parameters.add(newCode);
            block.nextBlock = -1;
            block.subStack1 = -1;
            block.subStack2 = -1;
            blocks.add(block);
        }

        projectData.a(javaName, mbName + "_moreBlock", blocks);
        projectData.y(javaName, mbName + "_moreBlock");
        projectData.k();
        notifyProjectRefresh(scId);
    }

    private static String formatXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) return xml;
        try {
            Source xmlInput = new StreamSource(new StringReader(xml));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            try {
                transformerFactory.setAttribute("indent-number", 4);
            } catch (Exception ignored) {}
            
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (Exception e) {
            return xml;
        }
    }
}
