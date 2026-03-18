package pro.sketchware.activities.editor.view;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import a.a.a.Lx;
import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivityCodeViewerBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.UI;

public class CodeViewerActivity extends BaseAppCompatActivity {

    public static final String SCHEME_XML = "xml";
    public static final String SCHEME_JAVA = "java";

    private ActivityCodeViewerBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        var code = getIntent().getStringExtra("code");
        var scheme = getIntent().getStringExtra("scheme");
        var scId = getIntent().getStringExtra("sc_id");

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setSubtitle(scId);

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        binding.editor.setText(Lx.j(code, false));
        binding.editor.setWordwrap(false);
        loadColorScheme(scheme);

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setSubtitle(scId);
        binding.toolbar.setTitle("Code Editor");

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);

        var javaName = getIntent().getStringExtra("java_name");
        var xmlName = getIntent().getStringExtra("xml_name");

        binding.fabAgente.setOnClickListener(v -> {
            mod.sdb.agente.SdbAgenteChatSheet sheet = mod.sdb.agente.SdbAgenteChatSheet.newInstanceForCode(
                    scId,
                    javaName,
                    xmlName,
                    binding.editor.getText().toString(),
                    newCode -> {
                        binding.editor.setText(newCode);
                        applyCodeToProject(newCode);
                    }
            );
            sheet.show(getSupportFragmentManager(), "SdbAgenteChatSheet");
        });
    }

    /**
     * Called by SdbAgenteChatSheet via reflection or by the manual save button.
     */
    public void save() {
        handleManualSave();
    }

    private void applyCodeToProject(String newCode) {
        var scId = getIntent().getStringExtra("sc_id");
        var javaName = getIntent().getStringExtra("java_name");
        var xmlName = getIntent().getStringExtra("xml_name");
        var scheme = getIntent().getStringExtra("scheme");

        if (SCHEME_XML.equals(scheme)) {
            String json = "{ \"op\": \"edit_layout_xml\", \"xmlName\": \"" + xmlName.replace(".xml", "") + "\", \"data\": { \"xml_content\": " + pro.sketchware.utility.GsonUtils.getGson().toJson(newCode) + " } }";
            mod.sdb.agente.SdbEditEngine.applyEdits(scId, json, xmlName);
        } else {
            // Injects entire code as a single block for simplicity of sync
            String eventName = getIntent().getStringExtra("event_name");
            if (eventName == null || eventName.isEmpty()) eventName = "onCreate";
            // Replace=true ensure manual code replaces all existing blocks for this event
            mod.sdb.agente.SdbEditEngine.addEventToActivityAndInjectCode(scId, javaName, eventName, newCode, true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 100, 0, "Salvar")
            .setIcon(pro.sketchware.R.drawable.save_white_24)
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 100) {
            save();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleManualSave() {
        var scId = getIntent().getStringExtra("sc_id");
        var javaName = getIntent().getStringExtra("java_name");
        var xmlName = getIntent().getStringExtra("xml_name");
        var scheme = getIntent().getStringExtra("scheme");
        String currentCode = binding.editor.getText().toString();
        String targetId = getIntent().getStringExtra("id");
        String eventName = getIntent().getStringExtra("event_name");
        if (eventName == null || eventName.isEmpty()) eventName = "onCreate";
        
        String eventKey;
        if (targetId != null && !targetId.isEmpty()) {
            eventKey = targetId + "_" + eventName;
        } else {
            eventKey = eventName;
        }

        if (SCHEME_JAVA.equals(scheme)) {
            // Sincroniza diretamente o código Java manual com os blocos, substituindo-os
            mod.sdb.agente.SdbEditEngine.addEventToActivityAndInjectCode(scId, javaName, eventKey, currentCode, true);
            pro.sketchware.utility.SketchwareUtil.toast("Código sincronizado com os blocos!");
        } else {
            // Para XML, mantém o fluxo via Agente para garantir consistência estrutural
            String prompt = "O usuário editou manualmente o XML.\n"
                    + "Código:\n```xml\n" + currentCode + "\n```\n\n"
                    + "Aplique as mudanças de layout.";

            mod.sdb.agente.SdbAgenteChatSheet sheet = mod.sdb.agente.SdbAgenteChatSheet.newInstanceWithLogic(
                    scId,
                    javaName,
                    xmlName,
                    prompt,
                    instruction -> {}
            );
            sheet.show(getSupportFragmentManager(), "SdbAgenteChatSheet");
        }
    }

    private void loadColorScheme(String scheme) {
        if (scheme.equals(SCHEME_XML)) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }
}