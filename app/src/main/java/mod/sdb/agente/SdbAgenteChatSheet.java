package mod.sdb.agente;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;
import com.unity3d.services.banners.BannerErrorInfo;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import a.a.a.jC;
import a.a.a.Ox;
import a.a.a.jq;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import java.util.ArrayList;
import pro.sketchware.activities.preview.LayoutPreviewActivity;

public class SdbAgenteChatSheet extends BottomSheetDialogFragment {

    public static final String ACTION_FREE_BUILD_REQUEST =
            "mod.sdb.agente.ACTION_FREE_BUILD_REQUEST";
    public static final String ACTION_FREE_BUILD_RESULT =
            "mod.sdb.agente.ACTION_FREE_BUILD_RESULT";
    public static final String EXTRA_BUILD_SC_ID = "gc_ai_build_sc_id";
    public static final String EXTRA_BUILD_REQUEST_ID = "gc_ai_build_request_id";
    public static final String EXTRA_BUILD_STATE = "gc_ai_build_state";
    public static final String EXTRA_BUILD_SUCCESS = "gc_ai_build_success";
    public static final String EXTRA_BUILD_ERROR = "gc_ai_build_error";
    public static final String EXTRA_BUILD_APK_PATH = "gc_ai_build_apk_path";

    private String sc_id;
    private SdbAgenteSk agente;
    private RecyclerView chatRecycler;
    private ChatAdapter adapter;
    private TextInputEditText inputText;
    private List<SdbAgenteActivity.ChatMessage> messages = new ArrayList<>();
    private String currentBase64Image = null;
    private String currentMimeType = null;
    private String currentChatFile;
    private View layoutImagePreview;
    private View layoutAgentWorking;
    private android.widget.ImageView imgPreview;
    private android.widget.ImageView imgAgentWorking;
    private com.google.android.material.checkbox.MaterialCheckBox cbAutoApply;
    private com.google.android.material.textfield.TextInputLayout inputLayout;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String[]> skillImportLauncher;
    private ActivityResultLauncher<String> skillExportLauncher;
    private SdbSkillManager.Skill pendingSkillExport;
    private MaterialButton btnSend;
    private MaterialButton btnStopAgent;
    private boolean agentWorking = false;
    private boolean stopRequested = false;
    private long agentRunSerial = 0L;
    private int autoRepairAttempts = 0;
    private static final int MAX_AUTO_REPAIR_ATTEMPTS = 6;
    private static final int MAX_HISTORY_MESSAGES = 16;
    private static final int MAX_HISTORY_CHARS = 24000;
    private String lastAutoRepairJson = null;
    private String lastAutoRepairError = null;
    private int repeatedAutoRepairErrors = 0;
    private String lastUserPrompt = "";
    private String pendingPlanRequest = null;
    private String pendingPlanText = null;
    private java.util.Set<String> activeSkillOperationPolicy = null;
    private static final int MAX_FREE_BUILD_REPAIR_ATTEMPTS = 4;
    private final android.os.Handler freeBuildHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean freeBuildTurnArmed = false;
    private boolean freeBuildLoopActive = false;
    private boolean freeBuildWaiting = false;
    private boolean freeBuildRepairPromptPending = false;
    private int freeBuildRepairAttempts = 0;
    private int repeatedFreeBuildErrors = 0;
    private String lastFreeBuildError = null;
    private String freeBuildOriginalRequest = null;
    private String activeFreeBuildRequestId = null;
    private Runnable freeBuildAckTimeout = null;
    private boolean freeBuildReceiverRegistered = false;
    private android.content.Context freeBuildReceiverContext = null;
    
    private boolean isCodeEditorMode = false;
    private String originalCode = null;
    private OnCodeApplyListener codeApplyListener = null;
    private boolean isCompileErrorMode = false;
    private String compileErrorText = null;
    
    private String contextName = null;
    private String contextXmlName = null;
    private String systemInstructionAddon = null;
    private OnApplyListener applyListener = null;
    private OnAgenteEditListener editListener = null;
    private com.google.android.material.chip.ChipGroup chipGroupIntent;
    private BannerView bannerView = null;
    private boolean adFallbackTried = false;

    private final android.content.BroadcastReceiver freeBuildResultReceiver =
            new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (!ACTION_FREE_BUILD_RESULT.equals(intent.getAction())) return;
            if (!safeEquals(sc_id, intent.getStringExtra(EXTRA_BUILD_SC_ID))) return;
            if (!safeEquals(activeFreeBuildRequestId,
                    intent.getStringExtra(EXTRA_BUILD_REQUEST_ID))) return;
            handleFreeBuildResult(intent);
        }
    };

    public interface OnAgenteEditListener {
        void onEditApplied();
    }

    public interface OnApplyListener {
        void onApply(String instruction);
    }

    public interface OnCodeApplyListener {
        void onCodeApply(String newCode);
    }

    public static SdbAgenteChatSheet newInstance(String sc_id) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        fragment.setArguments(args);
        return fragment;
    }

    public static SdbAgenteChatSheet newInstance(String sc_id, String contextName, String xmlName, String systemInstructionAddon, OnAgenteEditListener listener) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        args.putString("context_name", contextName);
        args.putString("context_xml_name", xmlName);
        args.putString("system_instruction_addon", systemInstructionAddon);
        fragment.setArguments(args);
        fragment.editListener = listener;
        return fragment;
    }

    public static SdbAgenteChatSheet newInstanceWithLogic(String sc_id, String contextName, String xmlName, String systemInstructionAddon, OnApplyListener listener) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        args.putString("context_name", contextName);
        args.putString("context_xml_name", xmlName);
        args.putString("system_instruction_addon", systemInstructionAddon);
        fragment.setArguments(args);
        fragment.applyListener = listener;
        return fragment;
    }

    public static SdbAgenteChatSheet newInstance(String sc_id, String contextName, String xmlName, OnAgenteEditListener listener) {
        return newInstance(sc_id, contextName, xmlName, null, listener);
    }

    public static SdbAgenteChatSheet newInstanceForCode(String sc_id, String contextName, String originalCode, OnCodeApplyListener listener) {
        return newInstanceForCode(sc_id, contextName, null, originalCode, listener);
    }

    public static SdbAgenteChatSheet newInstanceForCode(String sc_id, String contextName, String xmlName, String originalCode, OnCodeApplyListener listener) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        args.putString("context_name", contextName);
        args.putString("context_xml_name", xmlName);
        args.putString("original_code", originalCode);
        fragment.setArguments(args);
        fragment.isCodeEditorMode = true;
        fragment.originalCode = originalCode;
        fragment.codeApplyListener = listener;
        return fragment;
    }


    public static SdbAgenteChatSheet newInstanceForCompileError(String sc_id, String errorText) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        args.putString("compile_error_text", errorText);
        fragment.setArguments(args);
        fragment.isCompileErrorMode = true;
        fragment.compileErrorText = errorText;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            sc_id = getArguments().getString("sc_id");
            contextName = getArguments().getString("context_name");
            contextXmlName = getArguments().getString("context_xml_name");
            systemInstructionAddon = getArguments().getString("system_instruction_addon");
            if (getArguments().containsKey("original_code")) {
                originalCode = getArguments().getString("original_code");
                isCodeEditorMode = true;
            }
            if (getArguments().containsKey("compile_error_text")) {
                compileErrorText = getArguments().getString("compile_error_text");
                isCompileErrorMode = true;
                if (contextName == null || contextName.trim().isEmpty()) {
                    contextName = inferJavaContext(compileErrorText, sc_id);
                }
                if (contextXmlName == null || contextXmlName.trim().isEmpty()) {
                    contextXmlName = inferXmlContext(compileErrorText, sc_id, contextName);
                }
            }
        }
        agente = new SdbAgenteSk(getContext(), sc_id);
        
        // Unity Ads Initialization (Once per session)
        String unityGameId = "6063332";
        boolean testMode = false; // Always false for production
        if (!UnityAds.isInitialized() && getContext() != null) {
            UnityAds.initialize(getContext().getApplicationContext(), unityGameId, testMode, new IUnityAdsInitializationListener() {
                @Override public void onInitializationComplete() {}
                @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                    android.util.Log.e("UnityAds", "Init Failed: " + message);
                }
            });
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sdb_agente_layout, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        android.app.Dialog dialog = getDialog();
        if (dialog == null) return;
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;
        com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
        behavior.setHideable(false);
        behavior.setSkipCollapsed(false);
        behavior.setPeekHeight((int) (96 * getResources().getDisplayMetrics().density));
        behavior.addBottomSheetCallback(new com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(@NonNull View sheet, int newState) {
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
                    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
            @Override public void onSlide(@NonNull View sheet, float slideOffset) {}
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureNotificationPermission();
        
        chipGroupIntent = view.findViewById(R.id.chip_group_intent);
        restoreExecutionMode();
        chipGroupIntent.setOnCheckedChangeListener((group, checkedId) ->
                persistExecutionMode(checkedId));
        registerFreeBuildReceiver();

        adFallbackTried = false;
        view.post(() -> {
            if (UnityAds.isInitialized()) {
                loadBanner(view);
            }
        });

        view.findViewById(R.id.btn_back).setOnClickListener(v -> minimizeSheet());
        
        view.findViewById(R.id.btn_history).setOnClickListener(v -> showHistoryDialog());
        view.findViewById(R.id.btn_config).setOnClickListener(v -> showApiKeyDialog());

        TextView tvTitle = view.findViewById(R.id.tv_title);
        if (tvTitle != null) {
            tvTitle.setSelected(true); // For marquee if needed
        }

        chatRecycler = view.findViewById(R.id.chat_recycler);
        chatRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatAdapter(messages);
        chatRecycler.setAdapter(adapter);

        cbAutoApply = view.findViewById(R.id.cb_auto_apply);
        if (cbAutoApply != null) cbAutoApply.setVisibility(View.GONE);

        inputLoader(view);
        setupChips(view);

        // Load most recent chat or start fresh
        String dirPath = pro.sketchware.utility.FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/";
        java.io.File dir = new java.io.File(dirPath);
        java.io.File[] files = dir.listFiles((d, name) -> name.startsWith("chat_") && name.endsWith(".json"));
        if (files != null && files.length > 0) {
            java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            loadMessages(files[0].getName());
        } else {
            startNewChat();
        }

        refreshUiLanguage(view);

        // Compile error mode: keep the main chat intact and prepare the full log.
        if (isCompileErrorMode && compileErrorText != null && !compileErrorText.trim().isEmpty()) {
            String prompt = s(
                    "Analise e corrija todos os erros de compilacao abaixo. Inspecione o projeto antes de editar e aplique somente as correcoes necessarias.\n\nLOG COMPLETO:\n",
                    "Analyze and fix all compilation errors below. Inspect the project before editing and apply only the required fixes.\n\nFULL LOG:\n")
                    + compileErrorText;
            inputText.setText(prompt);
            inputText.setSelection(inputText.length());
            inputText.requestFocus();
        }

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                processImageData(uri);
            }
        });
        skillImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) inspectSkillImport(uri);
                });
        skillExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                    if (uri != null && pendingSkillExport != null) exportSkillTo(uri, pendingSkillExport);
                    pendingSkillExport = null;
                });
    }

    private void loadBanner(View view) {
        if (adFallbackTried) {
            loadBannerWithSize(view, new UnityBannerSize(300, 250));
        } else {
            loadBannerWithSize(view, new UnityBannerSize(320, 50));
        }
    }

    private void loadBannerWithSize(View view, UnityBannerSize size) {
        try {
            LinearLayout adContainer = view.findViewById(R.id.unity_ads_container);
            if (adContainer == null) return;

            if (bannerView != null) {
                try { bannerView.destroy(); } catch (Exception ignored) {}
                adContainer.removeAllViews();
            }

            bannerView = new BannerView(getActivity(), "Banner_Android", size);
            bannerView.setListener(new BannerView.IListener() {
                @Override public void onBannerLoaded(BannerView bannerAdView) {}
                @Override public void onBannerShown(BannerView bannerAdView) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> adContainer.setVisibility(View.VISIBLE));
                    }
                }
                @Override public void onBannerClick(BannerView bannerAdView) {}
                @Override public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> adContainer.setVisibility(View.GONE));
                    }
                    String errorMsg = errorInfo.errorMessage != null ? errorInfo.errorMessage.toLowerCase() : "";
                    boolean isNoFill = errorMsg.contains("no fill") || errorMsg.contains("no ad");
                    if (isNoFill) {
                        if (!adFallbackTried) {
                            adFallbackTried = true;
                            if (view != null && getActivity() != null) {
                                view.post(() -> loadBanner(view));
                            }
                        }
                    } else if (view != null && getActivity() != null) {
                        view.postDelayed(() -> {
                            if (bannerView != null && UnityAds.isInitialized() && getActivity() != null) {
                                bannerView.load();
                            }
                        }, 20000);
                    }
                }
                @Override public void onBannerLeftApplication(BannerView bannerAdView) {}
            });

            int heightDp = (size.getHeight() > 0) ? size.getHeight() : 50;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(heightDp * getContext().getResources().getDisplayMetrics().density)
            );
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            adContainer.addView(bannerView, lp);
            bannerView.load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        unregisterFreeBuildReceiver();
        if (freeBuildAckTimeout != null) {
            freeBuildHandler.removeCallbacks(freeBuildAckTimeout);
            freeBuildAckTimeout = null;
        }
        if (bannerView != null) {
            try { bannerView.destroy(); } catch (Exception ignored) {}
            bannerView = null;
        }
        super.onDestroyView();
    }

    private void inputLoader(View view) {
        inputText = view.findViewById(R.id.input_text);
        inputLayout = view.findViewById(R.id.input_layout);
        layoutImagePreview = view.findViewById(R.id.layout_image_preview);
        layoutAgentWorking = view.findViewById(R.id.layout_agent_working);
        imgPreview = view.findViewById(R.id.img_preview);
        imgAgentWorking = view.findViewById(R.id.img_agent_working);
        
        btnSend = view.findViewById(R.id.btn_send);
        btnStopAgent = view.findViewById(R.id.btn_stop_agent);
        btnSend.setOnClickListener(v -> sendMessage());
        btnStopAgent.setOnClickListener(v -> interruptAgent());
        view.findViewById(R.id.btn_attach).setOnClickListener(this::showAttachmentMenu);
        view.findViewById(R.id.btn_remove_image).setOnClickListener(v -> {
            currentBase64Image = null;
            currentMimeType = null;
            layoutImagePreview.setVisibility(View.GONE);
        });
        view.findViewById(R.id.btn_prompts).setOnClickListener(v -> showPromptsMenu(v));
        view.findViewById(R.id.btn_info).setOnClickListener(v -> showHelpDialog());
        view.findViewById(R.id.btn_lang).setOnClickListener(v -> {
            boolean isPt = "pt".equals(agente.getLanguage());
            agente.setLanguage(isPt ? "en" : "pt");
            ((TextView) v).setText(isPt ? "EN" : "PT");
            SketchwareUtil.toast(isPt ? "Language: English" : "Idioma: Português");
            refreshUiLanguage(view);
        });
        // Set initial lang label
        String lang = agente.getLanguage();
        ((TextView) view.findViewById(R.id.btn_lang))
            .setText("pt".equals(lang) ? "PT" : "EN");
    }

    private void showAttachmentMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(getContext(), anchor);
        menu.getMenu().add(0, 0, 0, s("Anexar imagem", "Attach image"));
        menu.getMenu().add(0, 1, 1, s("Importar Skill offline", "Import offline Skill"));
        menu.getMenu().add(0, 2, 2, s("Gerenciar Skills", "Manage Skills"));
        menu.getMenu().add(0, 3, 3, s("Criar Skill desta conversa", "Create Skill from this chat"));
        menu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            if (index == 0) {
                imagePickerLauncher.launch("image/*");
            } else if (index == 1) {
                skillImportLauncher.launch(new String[]{"application/octet-stream", "application/zip", "*/*"});
            } else if (index == 2) {
                showSkillManager();
            } else if (index == 3) {
                inputText.setText(s(
                        "Analise esta conversa e crie uma Skill offline generica e reutilizavel para o problema resolvido. Remova nomes especificos, dados privados e caminhos locais. Inclua gatilhos, regras, operacoes permitidas e testes declarativos.",
                        "Analyze this conversation and create a generic reusable offline Skill for the solved problem. Remove specific names, private data and local paths. Include triggers, rules, allowed operations and declarative tests."));
                inputText.setSelection(inputText.length());
            }
            return true;
        });
        menu.show();
    }

    private void inspectSkillImport(Uri uri) {
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
            SdbSkillManager.ImportResult result = SdbSkillManager.inspect(input);
            if (!result.success()) {
                SketchwareUtil.toastError(result.error);
                return;
            }
            SdbSkillManager.Skill skill = result.skill;
            String permissions = skill.permissions.isEmpty() ? s("Nenhuma declarada", "None declared")
                    : android.text.TextUtils.join(", ", skill.permissions);
            String operations = skill.operations.isEmpty() ? s("Somente orientacao", "Guidance only")
                    : android.text.TextUtils.join(", ", skill.operations);
            String fingerprint = skill.fingerprint == null ? "-"
                    : skill.fingerprint.substring(0, Math.min(16, skill.fingerprint.length()));
            String summary = skill.description + "\n\n"
                    + s("Autor: ", "Author: ") + skill.author + "\n"
                    + s("Versao: ", "Version: ") + skill.version + "\n"
                    + s("Permissoes: ", "Permissions: ") + permissions + "\n"
                    + s("Operacoes: ", "Operations: ") + operations + "\n"
                    + "SHA-256: " + fingerprint + "...\n\n"
                    + s("A Skill sera instalada como candidata e exigira aprovacao antes de aplicar mudancas.",
                            "The Skill will be installed as a candidate and will require approval before applying changes.");
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(skill.name)
                    .setMessage(summary)
                    .setPositiveButton(s("Neste projeto", "This project"),
                            (dialog, which) -> installSkill(skill, false))
                    .setNeutralButton(s("Todos os projetos", "All projects"),
                            (dialog, which) -> installSkill(skill, true))
                    .setNegativeButton(s("Cancelar", "Cancel"), null)
                    .show();
        } catch (Exception error) {
            SketchwareUtil.toastError(s("Nao foi possivel ler a Skill: ", "Could not read Skill: ")
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }

    private void installSkill(SdbSkillManager.Skill skill, boolean global) {
        SdbSkillManager.ImportResult result = SdbSkillManager.install(skill, sc_id, global);
        if (!result.success()) {
            SketchwareUtil.toastError(result.error);
            return;
        }
        addMessage(new SdbAgenteActivity.ChatMessage(
                s("Skill offline instalada como candidata: ", "Offline Skill installed as candidate: ")
                        + result.skill.name, false), true);
    }

    private void showSkillManager() {
        List<SdbSkillManager.Skill> skills = SdbSkillManager.list(sc_id);
        if (skills.isEmpty()) {
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(s("Skills offline", "Offline Skills"))
                    .setMessage(s("Nenhuma Skill instalada. Importe um arquivo .gcskill ou crie uma a partir da conversa.",
                            "No Skills installed. Import a .gcskill file or create one from the chat."))
                    .setPositiveButton(s("Importar", "Import"), (dialog, which) ->
                            skillImportLauncher.launch(new String[]{"application/octet-stream", "application/zip", "*/*"}))
                    .setNegativeButton(s("Fechar", "Close"), null)
                    .show();
            return;
        }
        String[] labels = new String[skills.size()];
        for (int i = 0; i < skills.size(); i++) {
            SdbSkillManager.Skill skill = skills.get(i);
            labels[i] = skill.displayName() + "\n"
                    + (skill.global ? s("Todos os projetos", "All projects")
                    : s("Somente este projeto", "This project only"));
        }
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(s("Skills offline", "Offline Skills"))
                .setItems(labels, (dialog, which) -> showSkillActions(skills.get(which)))
                .setPositiveButton(s("Importar", "Import"), (dialog, which) ->
                        skillImportLauncher.launch(new String[]{"application/octet-stream", "application/zip", "*/*"}))
                .setNegativeButton(s("Fechar", "Close"), null)
                .show();
    }

    private void showSkillActions(SdbSkillManager.Skill skill) {
        String toggle = skill.enabled ? s("Desativar", "Disable") : s("Ativar", "Enable");
        String trust = skill.trusted ? s("Voltar para candidata", "Mark as candidate")
                : s("Marcar como confiavel", "Mark as trusted");
        String[] actions = new String[]{toggle, trust, s("Exportar .gcskill", "Export .gcskill"),
                s("Atualizar com GC-AI", "Update with GC-AI"),
                s("Ver detalhes", "View details"), s("Excluir", "Delete")};
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(skill.displayName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (!SdbSkillManager.setEnabled(skill, !skill.enabled)) {
                            SketchwareUtil.toastError(s("Falha ao atualizar Skill.", "Failed to update Skill."));
                        }
                    } else if (which == 1) {
                        if (skill.trusted) {
                            if (!SdbSkillManager.setTrusted(skill, false)) {
                                SketchwareUtil.toastError(s("Falha ao atualizar confianca.", "Failed to update trust."));
                            }
                        } else {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(s("Confiar nesta Skill?", "Trust this Skill?"))
                                    .setMessage(s(
                                            "Skills confiaveis podem permitir autoaplicacao quando seus gatilhos coincidirem. As operacoes continuam limitadas ao manifesto:\n\n",
                                            "Trusted Skills may allow automatic application when their triggers match. Operations remain limited to the manifest:\n\n")
                                            + android.text.TextUtils.join(", ", skill.operations))
                                    .setPositiveButton(s("Confiar", "Trust"), (d, w) -> {
                                        if (!SdbSkillManager.setTrusted(skill, true)) {
                                            SketchwareUtil.toastError(s("Falha ao atualizar confianca.", "Failed to update trust."));
                                        }
                                    })
                                    .setNegativeButton(s("Cancelar", "Cancel"), null)
                                    .show();
                        }
                    } else if (which == 2) {
                        pendingSkillExport = skill;
                        skillExportLauncher.launch(skill.id + "-v" + skill.version + SdbSkillManager.EXTENSION);
                    } else if (which == 3) {
                        inputText.setText(s("Atualize a Skill offline existente usando update_skill. Preserve o ID e generalize qualquer melhoria.\n\n",
                                "Update the existing offline Skill using update_skill. Preserve its ID and generalize every improvement.\n\n")
                                + "skill_id: " + skill.id + "\n"
                                + "version: " + skill.version + "\n"
                                + "triggers: " + android.text.TextUtils.join(", ", skill.triggers) + "\n"
                                + "rules: " + android.text.TextUtils.join(" | ", skill.rules) + "\n"
                                + "operations: " + android.text.TextUtils.join(", ", skill.operations));
                        inputText.setSelection(inputText.length());
                    } else if (which == 4) {
                        showSkillDetails(skill);
                    } else if (which == 5) {
                        new MaterialAlertDialogBuilder(getContext())
                                .setTitle(s("Excluir Skill?", "Delete Skill?"))
                                .setMessage(skill.name)
                                .setPositiveButton(s("Excluir", "Delete"), (d, w) -> {
                                    if (!SdbSkillManager.delete(skill)) {
                                        SketchwareUtil.toastError(s("Falha ao excluir Skill.", "Failed to delete Skill."));
                                    }
                                })
                                .setNegativeButton(s("Cancelar", "Cancel"), null)
                                .show();
                    }
                })
                .setNegativeButton(s("Fechar", "Close"), null)
                .show();
    }

    private void showSkillDetails(SdbSkillManager.Skill skill) {
        String value = skill.description + "\n\n"
                + s("Autor: ", "Author: ") + skill.author + "\n"
                + s("Gatilhos: ", "Triggers: ") + android.text.TextUtils.join(", ", skill.triggers) + "\n"
                + s("Operacoes: ", "Operations: ") + android.text.TextUtils.join(", ", skill.operations) + "\n\n"
                + s("Regras:\n", "Rules:\n") + "- " + android.text.TextUtils.join("\n- ", skill.rules);
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(skill.name + " v" + skill.version)
                .setMessage(value)
                .setPositiveButton("OK", null)
                .show();
    }

    private void exportSkillTo(Uri uri, SdbSkillManager.Skill skill) {
        try (java.io.OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
            SdbSkillManager.export(skill, output);
            SketchwareUtil.toast(s("Skill exportada.", "Skill exported."));
        } catch (Exception error) {
            SketchwareUtil.toastError(s("Falha ao exportar Skill: ", "Failed to export Skill: ")
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }

    private void showPromptsMenu(View anchor) {
        boolean pt = "pt".equals(agente.getLanguage());
        String[][] prompts = pt ? new String[][] {
            {"💉 Injetar código",     "Injete o seguinte código Java no evento onCreate da tela [NomeDaTela]:\n"},
            {"📦 Criar MoreBlock",    "Crie um MoreBlock chamado [nome] na tela [NomeDaTela] que faça:\n"},
            {"🎨 Criar layout",       "Crie um layout completo para a tela [NomeDaTela] com este design:\n"},
            {"➕ Adicionar widget",   "Adicione um [tipo] na tela [NomeDaTela] dentro do pai [parentId]:\n"},
            {"🖼️ Adicionar ícone",   "Crie um ícone vetorial <vector> chamado [nome] com este visual:\n"},
            {"🎨 Criar drawable",     "Crie um drawable chamado [nome] com este estilo (shape/gradiente/seletor):\n"},
            {"🔧 Corrigir bug",       "Corrija o seguinte problema no projeto:\n"},
            {"⚡ Otimizar código",    "Otimize o código do evento [evento] na tela [NomeDaTela]:\n"},
            {"📖 Explicar lógica",    "Explique o que faz a lógica do evento [evento] na tela [NomeDaTela]."},
            {"🔄 Atualizar widget",   "Atualize o widget id=[id] na tela [NomeDaTela] com estes atributos:\n"},
        } : new String[][] {
            {"💉 Inject code",        "Inject the following Java code into the onCreate event of screen [ScreenName]:\n"},
            {"📦 Create MoreBlock",   "Create a MoreBlock named [name] in screen [ScreenName] that does:\n"},
            {"🎨 Create layout",      "Create a full layout for screen [ScreenName] with this design:\n"},
            {"➕ Add widget",         "Add a [type] widget to screen [ScreenName] inside parent [parentId]:\n"},
            {"🖼️ Add icon",          "Create a <vector> icon drawable named [name] with this visual style:\n"},
            {"🎨 Create drawable",    "Create a drawable named [name] with this style (shape/gradient/selector):\n"},
            {"🔧 Fix bug",            "Fix the following issue in the project:\n"},
            {"⚡ Optimize code",      "Optimize the [event] event code in screen [ScreenName]:\n"},
            {"📖 Explain logic",      "Explain what the [event] event logic does in screen [ScreenName]."},
            {"🔄 Update widget",      "Update widget id=[id] in screen [ScreenName] with these attributes:\n"},
        };

        android.widget.PopupMenu popup = new android.widget.PopupMenu(getContext(), anchor);
        for (int i = 0; i < prompts.length; i++) {
            popup.getMenu().add(0, i, i, prompts[i][0]);
        }
        popup.setOnMenuItemClickListener(item -> {
            String template = prompts[item.getItemId()][1];
            String current = inputText.getText() != null ? inputText.getText().toString() : "";
            inputText.setText(current.isEmpty() ? template : template + current);
            inputText.setSelection(inputText.getText().length());
            inputText.requestFocus();
            return true;
        });
        popup.show();
    }

    private void showHelpDialog() {
        boolean pt = "pt".equals(agente.getLanguage());
        String title = pt ? "Manual de Uso - GC-AI" : "User Guide - GC-AI";
        String content = pt ?
            "## O que é o GC-AI?\n" +
            "Agente de IA integrado ao Sketchware Pro. Ele lê o projeto e aplica mudanças diretamente.\n\n" +
            "## Como usar\n" +
            "1. **Digite** sua solicitação no campo de texto\n" +
            "2. **Escolha** o modo pelo chip (Auto, Injetar Código, MoreBlock, Design)\n" +
            "3. **Envie** — o agente responde e aplica as mudanças automaticamente\n" +
            "4. Use **▼** para inserir um template de prompt pré-pronto\n\n" +
            "## Chips de Modo\n" +
            "- **🤖 Auto** — deixa o agente decidir a melhor operação\n" +
            "- **💉 Injetar Código** — foca em injetar Java diretamente em eventos\n" +
            "- **📦 MoreBlock** — cria funções reutilizáveis (MoreBlocks)\n" +
            "- **🎨 Design/Ícones** — foca em layouts, drawables e ícones vetoriais\n" +
            "- **❤️ Apoiar** — exibe anúncio de apoio ao desenvolvedor\n\n" +
            "## Auto-Aplicar vs Manual\n" +
            "- **Auto-Aplicar ativado**: mudanças são aplicadas imediatamente\n" +
            "- **Auto-Aplicar desativado**: aparece botão 'Aplicar Mudanças' para revisar antes\n\n" +
            "## Contexto Automático\n" +
            "Aberto da tela principal do projeto: o agente tem acesso a **todas as telas**.\n" +
            "Aberto de dentro de um editor: contexto focado naquela tela.\n\n" +
            "## Dicas\n" +
            "- Seja específico: informe o nome da tela e do evento\n" +
            "- Use os templates (▼) como ponto de partida\n" +
            "- Ative **Auto-Aplicar** para fluxo rápido\n" +
            "- Long press em ⚙️ abre as configurações da API\n" +
            "- O histórico de chats é salvo automaticamente\n\n" +
            "## Operações suportadas\n" +
            "**Código Java:** `inject_code` · `add_import` · `add_view_event`\n" +
            "**Classes Java:** `create_java_file` · `edit_java_file` · `delete_java_file`\n" +
            "**Variáveis/Listas:** `add_variable` · `add_list`\n" +
            "**MoreBlocks:** `add_moreblock` · `update_moreblock` · `delete_moreblock`\n" +
            "**Drawables:** `add_drawable` · `delete_drawable`\n" +
            "**Blocos de Paleta:** `add_custom_block` · `update_custom_block` · `delete_custom_block` · `delete_palette`\n" +
            "**Design/Widgets:** `edit_layout_xml` · `add_widget` · `update_widget` · `remove_widget` · `rename_widget` · `set_custom_view`\n" +
            "**Permissões:** `add_permission` · `remove_permission`\n" +
            "**Material 3:** `enable_material3`\n" +
            "**Componentes:** `add_component`"
            :
            "## What is GC-AI?\n" +
            "An AI agent integrated into Sketchware Pro. It reads your project and applies changes directly.\n\n" +
            "## How to use\n" +
            "1. **Type** your request in the text field\n" +
            "2. **Select** a mode chip (Auto, Inject Code, MoreBlock, Design)\n" +
            "3. **Send** — the agent responds and applies changes automatically\n" +
            "4. Use **▼** to insert a ready-made prompt template\n\n" +
            "## Mode Chips\n" +
            "- **🤖 Auto** — lets the agent decide the best operation\n" +
            "- **💉 Inject Code** — focuses on injecting Java into events\n" +
            "- **📦 MoreBlock** — creates reusable functions (MoreBlocks)\n" +
            "- **🎨 Design/Icons** — focuses on layouts, drawables and vector icons\n" +
            "- **❤️ Support** — shows a support ad for the developer\n\n" +
            "## Auto-Apply vs Manual\n" +
            "- **Auto-Apply on**: changes are applied immediately\n" +
            "- **Auto-Apply off**: an 'Apply Changes' button appears to review first\n\n" +
            "## Automatic Context\n" +
            "Opened from the project main screen: the agent has access to **all screens**.\n" +
            "Opened from inside an editor: context is focused on that screen.\n\n" +
            "## Tips\n" +
            "- Be specific: include the screen name and event name\n" +
            "- Use templates (▼) as a starting point\n" +
            "- Enable **Auto-Apply** for a fast workflow\n" +
            "- Long press ⚙️ to open API settings\n" +
            "- Chat history is saved automatically\n\n" +
            "## Supported operations\n" +
            "**Java Code:** `inject_code` · `add_import` · `add_view_event`\n" +
            "**Java Classes:** `create_java_file` · `edit_java_file` · `delete_java_file`\n" +
            "**Variables/Lists:** `add_variable` · `add_list`\n" +
            "**MoreBlocks:** `add_moreblock` · `update_moreblock` · `delete_moreblock`\n" +
            "**Drawables:** `add_drawable` · `delete_drawable`\n" +
            "**Palette Blocks:** `add_custom_block` · `update_custom_block` · `delete_custom_block` · `delete_palette`\n" +
            "**Design/Widgets:** `edit_layout_xml` · `add_widget` · `update_widget` · `remove_widget` · `rename_widget` · `set_custom_view`\n" +
            "**Permissions:** `add_permission` · `remove_permission`\n" +
            "**Material 3:** `enable_material3`\n" +
            "**Components:** `add_component`";

        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        TextView tv = new TextView(getContext());
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        tv.setPadding(p, p, p, p);
        tv.setTextSize(13);
        io.noties.markwon.Markwon.create(getContext()).setMarkdown(tv, content);
        scroll.addView(tv);

        new MaterialAlertDialogBuilder(getContext())
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(pt ? "Fechar" : "Close", null)
            .show();
    }

    private void setupChips(View view) {
        view.findViewById(R.id.btn_new_chat).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(getContext())
                .setTitle(s("Nova Conversa", "New Chat"))
                .setMessage(s("Deseja iniciar um novo chat? O histórico atual será salvo.", "Start a new chat? The current history will be saved."))
                .setPositiveButton(s("Sim", "Yes"), (d, w) -> startNewChat())
                .setNegativeButton(s("Não", "No"), null)
                .show();
        });

    }

    private void restoreExecutionMode() {
        if (chipGroupIntent == null || getContext() == null) return;
        String mode = getContext().getSharedPreferences(
                        "gc_ai_chat", android.content.Context.MODE_PRIVATE)
                .getString("execution_mode_" + sc_id, "approve");
        if ("free".equals(mode)) {
            chipGroupIntent.check(R.id.intent_free);
        } else if ("agent".equals(mode)) {
            chipGroupIntent.check(R.id.intent_inject);
        } else if ("plan".equals(mode)) {
            chipGroupIntent.check(R.id.intent_plan);
        } else {
            chipGroupIntent.check(R.id.intent_auto);
        }
    }

    private void persistExecutionMode(int checkedId) {
        if (getContext() == null) return;
        String mode = checkedId == R.id.intent_free ? "free"
                : checkedId == R.id.intent_inject ? "agent"
                : checkedId == R.id.intent_plan ? "plan" : "approve";
        getContext().getSharedPreferences("gc_ai_chat", android.content.Context.MODE_PRIVATE)
                .edit().putString("execution_mode_" + sc_id, mode).apply();
    }

    private void registerFreeBuildReceiver() {
        if (freeBuildReceiverRegistered || getContext() == null) return;
        android.content.IntentFilter filter = new android.content.IntentFilter(
                ACTION_FREE_BUILD_RESULT);
        android.content.Context app = getContext().getApplicationContext();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(freeBuildResultReceiver, filter,
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            app.registerReceiver(freeBuildResultReceiver, filter);
        }
        freeBuildReceiverContext = app;
        freeBuildReceiverRegistered = true;
    }

    private void unregisterFreeBuildReceiver() {
        if (!freeBuildReceiverRegistered) return;
        try {
            if (freeBuildReceiverContext != null) {
                freeBuildReceiverContext.unregisterReceiver(freeBuildResultReceiver);
            }
        } catch (Exception ignored) {
        }
        freeBuildReceiverContext = null;
        freeBuildReceiverRegistered = false;
    }

    private static boolean safeEquals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    /** Updates all static UI text to match the current language (PT or EN). */
    private void refreshUiLanguage(View view) {
        if (view == null || agente == null) return;
        boolean pt = "pt".equals(agente.getLanguage());

        // Chips
        com.google.android.material.chip.Chip chipInject = view.findViewById(R.id.intent_inject);
        com.google.android.material.chip.Chip chipDesign = view.findViewById(R.id.intent_design);
        com.google.android.material.chip.Chip chipSupport = view.findViewById(R.id.intent_support);
        if (chipInject != null) chipInject.setText(pt ? "💉 Injetar Código" : "💉 Inject Code");
        if (chipDesign != null) chipDesign.setText(pt ? "🎨 Design/Ícones" : "🎨 Design/Icons");
        if (chipSupport != null) chipSupport.setText(pt ? "❤️ Apoiar" : "❤️ Support");

        // Genesis-style execution modes. The old specialist chips remain only as
        // prompt templates; mode selection is explicit and predictable.
        com.google.android.material.chip.Chip chipAskMode = view.findViewById(R.id.intent_auto);
        com.google.android.material.chip.Chip chipAgentMode = view.findViewById(R.id.intent_inject);
        com.google.android.material.chip.Chip chipPlanMode = view.findViewById(R.id.intent_plan);
        com.google.android.material.chip.Chip chipFreeMode = view.findViewById(R.id.intent_free);
        if (chipAskMode != null) chipAskMode.setText(pt ? "Aprovar" : "Approve");
        if (chipAgentMode != null) chipAgentMode.setText(pt ? "Agente" : "Agent");
        if (chipPlanMode != null) chipPlanMode.setText(pt ? "Plano" : "Plan");
        if (chipFreeMode != null) chipFreeMode.setText(pt ? "Livre" : "Free");
        View legacyMoreBlock = view.findViewById(R.id.intent_moreblock);
        View legacyDesign = view.findViewById(R.id.intent_design);
        View legacySupport = view.findViewById(R.id.intent_support);
        if (legacyMoreBlock != null) legacyMoreBlock.setVisibility(View.GONE);
        if (legacyDesign != null) legacyDesign.setVisibility(View.GONE);
        if (legacySupport != null) legacySupport.setVisibility(View.GONE);

        // Checkbox
        if (cbAutoApply != null) cbAutoApply.setText(pt ? "Auto-Aplicar Mudanças" : "Auto-Apply Changes");

        // Input hint
        if (inputLayout != null) inputLayout.setHint(pt ? "Sua mensagem..." : "Your message...");

        // Title
        TextView tvTitle = view.findViewById(R.id.tv_title);
        if (tvTitle != null) {
            if (contextName != null) {
                tvTitle.setText("GC-AI: " + contextName);
            } else {
                tvTitle.setText("GC-AI: " + (pt ? "Projeto " : "Project ") + sc_id);
            }
        }
    }

    public void setThinking(boolean thinking) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (btnSend != null) {
                btnSend.setEnabled(!thinking);
                btnSend.setVisibility(thinking ? View.GONE : View.VISIBLE);
            }
            if (btnStopAgent != null) {
                btnStopAgent.setVisibility(thinking ? View.VISIBLE : View.GONE);
            }
            if (inputText != null) {
                inputText.setEnabled(!thinking);
            }
            if (layoutAgentWorking != null) {
                layoutAgentWorking.setVisibility(thinking ? View.VISIBLE : View.GONE);
            }
            if (thinking && imgAgentWorking != null) {
                Glide.with(this).asGif().load(R.drawable.agent_working).into(imgAgentWorking);
            }
        });
    }

    private void interruptAgent() {
        if (!agentWorking) return;
        if (freeBuildWaiting && getContext() != null) {
            Intent cancelBuild = new Intent("com.besome.sketch.design.ACTION_CANCEL_BUILD");
            cancelBuild.setPackage(getContext().getPackageName());
            getContext().sendBroadcast(cancelBuild);
        }
        stopRequested = true;
        agentRunSerial++;
        agentWorking = false;
        resetFreeBuildLoop(true);
        setThinking(false);
        removeWorkingMessages();
        addMessage(new SdbAgenteActivity.ChatMessage(s("Agente interrompido.", "Agent stopped."), false), true);
    }

    private void completeAgentTurn(String message) {
        if (freeBuildWaiting) return;
        agentWorking = false;
        stopRequested = false;
        setThinking(false);
        if (!isResumed() && message != null && !message.trim().isEmpty()) {
            showCompletionNotification(message);
        }
    }

    private void startFreeModeBuild() {
        if (!freeBuildLoopActive || freeBuildWaiting || getContext() == null) return;
        try {
            SdbProjectIntegrityGuard.saveProjectData(sc_id);
            jC.b(sc_id).j();
            jC.d(sc_id).y();
        } catch (Exception error) {
            addMessage(new SdbAgenteActivity.ChatMessage(
                    s("Nao foi possivel salvar o projeto antes da compilacao: ",
                            "Could not save the project before building: ")
                            + (error.getMessage() == null ? error.toString() : error.getMessage()),
                    false), true);
            resetFreeBuildLoop(true);
            completeAgentTurn(s("Modo Livre interrompido.", "Free mode stopped."));
            return;
        }

        registerFreeBuildReceiver();
        freeBuildWaiting = true;
        freeBuildTurnArmed = false;
        agentWorking = true;
        setThinking(true);
        activeFreeBuildRequestId = java.util.UUID.randomUUID().toString();
        final String requestId = activeFreeBuildRequestId;
        addMessage(new SdbAgenteActivity.ChatMessage(s(
                "🔨 **Alteracoes aplicadas. Preparando a compilacao automatica do APK...**",
                "🔨 **Changes applied. Preparing the automatic APK build...**"), false), true);

        Intent request = new Intent(ACTION_FREE_BUILD_REQUEST);
        request.setPackage(getContext().getPackageName());
        request.putExtra(EXTRA_BUILD_SC_ID, sc_id);
        request.putExtra(EXTRA_BUILD_REQUEST_ID, requestId);
        getContext().sendBroadcast(request);

        freeBuildAckTimeout = () -> {
            if (!freeBuildWaiting || !safeEquals(requestId, activeFreeBuildRequestId)) return;
            addMessage(new SdbAgenteActivity.ChatMessage(s(
                    "Nao encontrei o editor de Design aberto para executar a compilacao. Abra o projeto no editor e tente novamente no modo Livre.",
                    "The Design editor was not available to run the build. Open the project editor and try Free mode again."),
                    false), true);
            resetFreeBuildLoop(true);
            completeAgentTurn(s("Compilacao automatica indisponivel.",
                    "Automatic build unavailable."));
        };
        freeBuildHandler.postDelayed(freeBuildAckTimeout, 7000L);
    }

    private void handleFreeBuildResult(Intent intent) {
        String state = intent.getStringExtra(EXTRA_BUILD_STATE);
        if ("started".equals(state)) {
            if (freeBuildAckTimeout != null) {
                freeBuildHandler.removeCallbacks(freeBuildAckTimeout);
                freeBuildAckTimeout = null;
            }
            addMessage(new SdbAgenteActivity.ChatMessage(s(
                    "📦 **Compilando o APK de teste...**",
                    "📦 **Building the test APK...**"), false), true);
            return;
        }

        if ("unavailable".equals(state)) {
            if (freeBuildAckTimeout != null) {
                freeBuildHandler.removeCallbacks(freeBuildAckTimeout);
                freeBuildAckTimeout = null;
            }
            String reason = intent.getStringExtra(EXTRA_BUILD_ERROR);
            addMessage(new SdbAgenteActivity.ChatMessage(s(
                    "A compilacao automatica nao pode iniciar agora: ",
                    "The automatic build cannot start right now: ")
                    + (reason == null ? s("editor indisponivel.", "editor unavailable.") : reason),
                    false), true);
            resetFreeBuildLoop(true);
            completeAgentTurn(s("Compilacao automatica indisponivel.",
                    "Automatic build unavailable."));
            return;
        }

        if (freeBuildAckTimeout != null) {
            freeBuildHandler.removeCallbacks(freeBuildAckTimeout);
            freeBuildAckTimeout = null;
        }
        freeBuildWaiting = false;
        if (intent.getBooleanExtra(EXTRA_BUILD_SUCCESS, false)
                || "success".equals(state)) {
            String apkPath = intent.getStringExtra(EXTRA_BUILD_APK_PATH);
            String message = s("✅ **APK compilado com sucesso.**",
                    "✅ **APK built successfully.**");
            if (apkPath != null && !apkPath.trim().isEmpty()) {
                message += "\n\n`" + apkPath + "`";
            }
            addMessage(new SdbAgenteActivity.ChatMessage(message, false), true);
            resetFreeBuildLoop(true);
            completeAgentTurn(s("Tarefa concluida e APK compilado.",
                    "Task completed and APK built."));
            return;
        }

        String error = intent.getStringExtra(EXTRA_BUILD_ERROR);
        if (error == null || error.trim().isEmpty()) {
            error = s("A compilacao falhou sem log detalhado.",
                    "The build failed without a detailed log.");
        }
        continueFreeModeAfterBuildFailure(error);
    }

    private void continueFreeModeAfterBuildFailure(String error) {
        freeBuildRepairAttempts++;
        String normalized = error.replaceAll("\\d+", "#").replaceAll("\\s+", " ").trim();
        if (normalized.equals(lastFreeBuildError)) {
            repeatedFreeBuildErrors++;
        } else {
            lastFreeBuildError = normalized;
            repeatedFreeBuildErrors = 1;
        }
        if (freeBuildRepairAttempts > MAX_FREE_BUILD_REPAIR_ATTEMPTS
                || repeatedFreeBuildErrors >= 3) {
            addMessage(new SdbAgenteActivity.ChatMessage(s(
                    "❌ O modo Livre interrompeu o ciclo: o APK ainda falhou apos correcoes limitadas. O log completo foi preservado em Erro de compilacao.",
                    "❌ Free mode stopped: the APK still failed after a bounded number of repairs. The full log was preserved in Compile error."),
                    false), true);
            resetFreeBuildLoop(true);
            completeAgentTurn(s("Compilacao nao corrigida automaticamente.",
                    "Build was not repaired automatically."));
            return;
        }

        compileErrorText = error;
        isCompileErrorMode = true;
        String inferredJava = inferJavaContext(error, sc_id);
        if (inferredJava != null && !inferredJava.trim().isEmpty()) {
            contextName = inferredJava;
            String inferredXml = inferXmlContext(error, sc_id, inferredJava);
            if (inferredXml != null && !inferredXml.trim().isEmpty()) {
                contextXmlName = inferredXml;
            }
        }
        addMessage(new SdbAgenteActivity.ChatMessage(s(
                "🛠️ **A compilacao falhou. O GC-AI recebeu o log real e vai corrigir o projeto automaticamente** (tentativa ",
                "🛠️ **The build failed. GC-AI received the real log and will repair the project automatically** (attempt ")
                + freeBuildRepairAttempts + "/" + MAX_FREE_BUILD_REPAIR_ATTEMPTS + ").",
                false), true);

        freeBuildRepairPromptPending = true;
        agentWorking = false;
        setThinking(false);
        inputText.setText(s(
                "Corrija todos os erros do log de compilacao recebido, aplique as correcoes e continue ate o APK compilar.",
                "Fix every error in the received build log, apply the repairs, and continue until the APK builds."));
        inputText.setSelection(inputText.length());
        freeBuildHandler.post(this::sendMessage);
    }

    private void resetFreeBuildLoop(boolean clearCompileMode) {
        if (freeBuildAckTimeout != null) {
            freeBuildHandler.removeCallbacks(freeBuildAckTimeout);
            freeBuildAckTimeout = null;
        }
        freeBuildTurnArmed = false;
        freeBuildLoopActive = false;
        freeBuildWaiting = false;
        freeBuildRepairPromptPending = false;
        activeFreeBuildRequestId = null;
        freeBuildOriginalRequest = null;
        freeBuildRepairAttempts = 0;
        repeatedFreeBuildErrors = 0;
        lastFreeBuildError = null;
        if (clearCompileMode) {
            isCompileErrorMode = false;
            compileErrorText = null;
        }
    }

    private void removeWorkingMessages() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            String text = messages.get(i).text == null ? "" : messages.get(i).text;
            if (text.startsWith("Aguardando resposta")
                    || text.startsWith("Waiting for GC-AI")
                    || text.startsWith("⚙️")
                    || text.startsWith("âš™")) {
                messages.remove(i);
                if (adapter != null) adapter.notifyItemRemoved(i);
            }
        }
    }

    private void minimizeSheet() {
        android.app.Dialog dialog = getDialog();
        if (dialog == null) return;
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;
        com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
        behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED);
        SketchwareUtil.toast(s("Chat minimizado. Toque na aba para voltar.", "Chat minimized. Tap the handle to return."));
    }

    private void showCompletionNotification(String message) {
        android.content.Context ctx = getContext();
        if (ctx == null) return;
        try {
            String channelId = "sdb_agent_tasks";
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        channelId,
                        "GC-AI",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            android.content.Intent openApp = ctx.getPackageManager()
                    .getLaunchIntentForPackage(ctx.getPackageName());
            if (openApp == null) return;
            openApp.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            openApp.putExtra("open_sdb_codflow", true);
            openApp.putExtra("sc_id", sc_id);
            int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pendingFlags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            }
            android.app.PendingIntent contentIntent = android.app.PendingIntent.getActivity(
                    ctx, 1207, openApp, pendingFlags);
            androidx.core.app.NotificationCompat.Builder builder =
                    new androidx.core.app.NotificationCompat.Builder(ctx, channelId)
                            .setSmallIcon(R.drawable.ic_mtrl_deployed_code)
                            .setContentTitle("GC-AI")
                            .setContentText(message)
                            .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                            .setContentIntent(contentIntent)
                            .setAutoCancel(true)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT);
            manager.notify(1207, builder.build());
        } catch (Exception ignored) {
        }
    }

    private void ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33 || getContext() == null) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(getContext(),
                android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1207);
        }
    }

    private void processImageData(Uri uri) {
        try {
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            if (bitmap.getWidth() > 1024 || bitmap.getHeight() > 1024) {
                float scale = Math.min(1024f / bitmap.getWidth(), 1024f / bitmap.getHeight());
                bitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth() * scale), (int)(bitmap.getHeight() * scale), true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] bytes = baos.toByteArray();
            currentBase64Image = Base64.encodeToString(bytes, Base64.NO_WRAP);
            currentMimeType = "image/jpeg";
            
            imgPreview.setImageBitmap(bitmap);
            layoutImagePreview.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            SketchwareUtil.toastError(s("Erro ao processar imagem: ", "Error processing image: ") + e.getMessage());
        }
    }

    private void saveMessages() {
        if (currentChatFile == null) {
            currentChatFile = "chat_" + System.currentTimeMillis() + ".json";
        }
        try {
            String json = new com.google.gson.Gson().toJson(messages);
            String path = pro.sketchware.utility.FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/" + currentChatFile;
            pro.sketchware.utility.FileUtil.writeFile(path, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadMessages(String fileName) {
        this.currentChatFile = fileName;
        String path = pro.sketchware.utility.FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/" + fileName;
        if (pro.sketchware.utility.FileUtil.isExistFile(path)) {
            try {
                String json = pro.sketchware.utility.FileUtil.readFile(path);
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<SdbAgenteActivity.ChatMessage>>() {}.getType();
                List<SdbAgenteActivity.ChatMessage> savedMessages = new com.google.gson.Gson().fromJson(json, type);
                if (savedMessages != null) {
                    messages.clear();
                    messages.addAll(savedMessages);
                    adapter.notifyDataSetChanged();
                    chatRecycler.scrollToPosition(messages.size() - 1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startNewChat() {
        messages.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        currentChatFile = "chat_" + System.currentTimeMillis() + ".json";
        addMessage(new SdbAgenteActivity.ChatMessage(s("Nova conversa iniciada. Como posso ajudar com seu projeto ", "New chat started. How can I help with project ") + sc_id + "?", false), true);
    }

    private void showHistoryDialog() {
        String dirPath = pro.sketchware.utility.FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/";
        java.io.File dir = new java.io.File(dirPath);
        java.io.File[] files = dir.listFiles((d, name) -> name.startsWith("chat_") && name.endsWith(".json"));
        
        if (files == null || files.length == 0) {
            SketchwareUtil.toast(s("Nenhum histórico encontrado.", "No chat history found."));
            return;
        }

        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        String[] fileNames = new String[files.length];
        String[] displayNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
            displayNames[i] = s("Conversa ", "Chat ") + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(files[i].lastModified()));
        }

        new MaterialAlertDialogBuilder(getContext())
            .setTitle(s("Histórico de Conversas", "Chat History"))
            .setItems(displayNames, (dialog, which) -> {
                java.io.File selectedFile = files[which];
                new MaterialAlertDialogBuilder(getContext())
                    .setTitle(displayNames[which])
                    .setMessage(s("Abra esta conversa ou exclua somente este histórico.", "Open this conversation or delete only this history."))
                    .setPositiveButton(s("Abrir", "Open"), (d, w) -> loadMessages(fileNames[which]))
                    .setNeutralButton(s("Excluir conversa", "Delete chat"), (d, w) -> {
                        if (selectedFile.delete()) {
                            if (fileNames[which].equals(currentChatFile)) startNewChat();
                            SketchwareUtil.toast(s("Conversa excluída.", "Chat deleted."));
                        } else {
                            SketchwareUtil.toastError(s("Não foi possível excluir a conversa.", "Could not delete the chat."));
                        }
                    })
                    .setNegativeButton(s("Cancelar", "Cancel"), null)
                    .show();
            })
            .setNeutralButton(s("Apagar Todas", "Delete All"), (d, w) -> {
                for (java.io.File file : files) {
                    file.delete();
                }
                startNewChat();
                SketchwareUtil.toast(s("Histórico apagado!", "History deleted!"));
            })
            .setNegativeButton(s("Fechar", "Close"), null)
            .show();
    }

    private void sendMessage() {
        if (agentWorking) {
            interruptAgent();
            return;
        }
        String text = inputText.getText().toString().trim();
        if (text.isEmpty() && currentBase64Image == null) return;
        boolean freeRepairTurn = freeBuildRepairPromptPending;
        freeBuildRepairPromptPending = false;
        if (!freeRepairTurn) {
            freeBuildTurnArmed = isFreeMode();
            if (freeBuildTurnArmed) {
                freeBuildLoopActive = false;
                freeBuildWaiting = false;
                freeBuildRepairAttempts = 0;
                repeatedFreeBuildErrors = 0;
                lastFreeBuildError = null;
                freeBuildOriginalRequest = text;
            }
        } else {
            freeBuildTurnArmed = true;
        }
        final boolean approvedPlan = isPlanMode()
                && pendingPlanText != null
                && isAffirmative(text);
        final String effectiveRequest = approvedPlan
                ? pendingPlanRequest + "\n\nPLANO APROVADO PELO USUARIO:\n" + pendingPlanText
                : text;
        final String skillQuery = effectiveRequest + "\n"
                + (compileErrorText == null ? "" : compileErrorText);
        final String offlineSkillInstruction = SdbSkillManager.buildPrompt(sc_id, skillQuery);
        final boolean candidateSkillActive = SdbSkillManager.hasCandidateMatch(sc_id, skillQuery);
        activeSkillOperationPolicy = SdbSkillManager.allowedOperationsForMatches(sc_id, skillQuery);
        final java.util.Set<String> turnSkillOperationPolicy = activeSkillOperationPolicy == null
                ? null : new java.util.LinkedHashSet<>(activeSkillOperationPolicy);
        final boolean runtimeCrashMode = looksLikeRuntimeCrash(skillQuery);
        stopRequested = false;
        agentWorking = true;
        autoRepairAttempts = 0;
        lastAutoRepairJson = null;
        lastAutoRepairError = null;
        repeatedAutoRepairErrors = 0;
        lastUserPrompt = text;
        final long runId = ++agentRunSerial;
        setThinking(true);

        SdbAgenteActivity.ChatMessage userMsg = new SdbAgenteActivity.ChatMessage(text, true);
        userMsg.base64Image = currentBase64Image;
        userMsg.mimeType = currentMimeType;
        
        addMessage(userMsg, true);
        inputText.setText("");

        if (isCompileErrorMode) {
            SdbCompileRepairEngine.ImmediateResult directRepair =
                    SdbCompileRepairEngine.repairCompileIssuesImmediately(
                            sc_id, compileErrorText, contextName);
            if (directRepair.complete && !directRepair.repairs.isEmpty()) {
                addMessage(new SdbAgenteActivity.ChatMessage(
                        s("Correcao estrutural aplicada diretamente:\n",
                                "Structural repair applied directly:\n")
                                + android.text.TextUtils.join("\n", directRepair.repairs)
                                + "\n\n"
                                + s("Compile o projeto novamente.", "Compile the project again."),
                        false), true);
                if (freeBuildTurnArmed || freeBuildLoopActive) {
                    freeBuildLoopActive = true;
                    startFreeModeBuild();
                } else {
                    completeAgentTurn(s("Correcao de compilacao concluida.",
                            "Compilation repair completed."));
                }
                return;
            }
        }

        final SdbAgenteActivity.ChatMessage thinkingMsg = new SdbAgenteActivity.ChatMessage(s("GC-AI esta analisando o projeto...", "GC-AI is analyzing the project..."), false);
        messages.add(thinkingMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        chatRecycler.scrollToPosition(messages.size() - 1);

        // Pass contextName so SdbProjectContext shows full event/block detail only for the
        // current screen — other screens get compact summaries to reduce token usage.
        String diagnosticContext = isCompileErrorMode ? compileErrorText
                : runtimeCrashMode ? effectiveRequest : null;
        String contextInfo = SdbProjectContext.getFullProjectContext(
                sc_id, contextName, diagnosticContext);
        SdbAgenteSk.ResponseListener listener = new SdbAgenteSk.ResponseListener() {
            @Override
            public void onResponse(String response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (runId != agentRunSerial || stopRequested) return;
                    int index = messages.indexOf(thinkingMsg);
                    if (index != -1) {
                        messages.remove(index);
                        adapter.notifyItemRemoved(index);
                    }
                    String rawResponse = response;
                    java.util.List<String> jsonBlocks = extractJsonBlocks(rawResponse);
                    
                    if (!jsonBlocks.isEmpty()) {
                        if (!isCompileErrorMode && isPlanMode() && !approvedPlan) {
                            pendingPlanRequest = text;
                            pendingPlanText = response;
                            addMessage(new SdbAgenteActivity.ChatMessage(response, false), true);
                            completeAgentTurn(s("Plano concluído.", "Plan completed."));
                            return;
                        }
                        try {
                            StringBuilder textPartBuilder = new StringBuilder();
                            String lastJson = "";
                            
                            // If we have multiple blocks, we'll try to apply them. 
                            // Usually the AI sends one big JSON or several small ones.
                            // We combine them into an "operations" array if they are just ops.
                            
                            if (jsonBlocks.size() == 1) {
                                lastJson = jsonBlocks.get(0);
                            } else {
                                // Combine into a unified operations response
                                org.json.JSONObject unified = new org.json.JSONObject();
                                org.json.JSONArray ops = new org.json.JSONArray();
                                for (String block : jsonBlocks) {
                                    try {
                                        org.json.JSONObject b = new org.json.JSONObject(block);
                                        if (b.has("operations")) {
                                            org.json.JSONArray bOps = b.getJSONArray("operations");
                                            for (int i = 0; i < bOps.length(); i++) ops.put(bOps.get(i));
                                        } else if (b.has("op")) {
                                            ops.put(b);
                                        }
                                    } catch (Exception ignored) {}
                                }
                                unified.put("operations", ops);
                                lastJson = unified.toString();
                            }

                            String textPart = rawResponse;
                            for (String block : jsonBlocks) {
                                textPart = textPart.replace(block, "");
                            }
                            textPart = textPart.replace("```json", "").replace("```", "").trim();

                            if (!textPart.isEmpty()) {
                                addMessage(new SdbAgenteActivity.ChatMessage(textPart, false), true);
                            }

                            final String finalJson = lastJson;
                            if (!hasActionableOperations(finalJson)) {
                                resetFreeBuildLoop(true);
                                addMessage(new SdbAgenteActivity.ChatMessage(s(
                                        "A resposta nao continha operacoes aplicaveis. Nenhuma edicao foi tentada e o ciclo automatico foi interrompido.",
                                        "The response contained no actionable operations. No edit was attempted and the automatic cycle was stopped."), false), true);
                                completeAgentTurn(s("Resposta sem edicoes.", "Response without edits."));
                                return;
                            }
                            addMessage(new SdbAgenteActivity.ChatMessage(s("⚙️ **Processando alterações...**", "⚙️ **Processing changes...**"), false), true);
                            setThinking(true);
                            
                            new android.os.Handler().postDelayed(() -> {
                                if (runId != agentRunSerial || stopRequested) return;
                                boolean autoApply = (isCompileErrorMode || isAgentMode()
                                        || isFreeMode() || approvedPlan)
                                        && !candidateSkillActive;
                                
                                if (autoApply) {
                                    applyResponseJson(finalJson, turnSkillOperationPolicy);
                                } else {
                                    // Manual Apply Mode
                                    SdbAgenteActivity.ChatMessage manualMsg = new SdbAgenteActivity.ChatMessage(s("⚙️ **Alterações prontas.**", "⚙️ **Changes ready.**"), false);

                                    // Add Preview Action
                                    manualMsg.addAction("preview_code", s("Visualizar Código", "Preview Code"), () -> {
                                        showCodePreviewDialog(finalJson);
                                    });

                                    // Add Apply Action
                                    manualMsg.addAction("apply_edits", s("Aplicar Mudanças", "Apply Changes"), () -> {
                                        applyResponseJson(finalJson, turnSkillOperationPolicy);
                                    });
                                    
                                    addMessage(manualMsg, true);
                                }
                                completeAgentTurn(s("Tarefa do agente concluida.", "Agent task completed."));
                            }, 500);
                        } catch (Exception e) {
                            addMessage(new SdbAgenteActivity.ChatMessage(response, false), true);
                            completeAgentTurn(s("Resposta do agente concluida.", "Agent response completed."));
                        }
                    } else {
                        if (!isCompileErrorMode && isPlanMode() && !approvedPlan) {
                            pendingPlanRequest = text;
                            pendingPlanText = response;
                        }
                        if (isCompileErrorMode) {
                            SdbCompileRepairEngine.ImmediateResult directRepair =
                                    SdbCompileRepairEngine.repairCompileIssuesImmediately(
                                            sc_id, compileErrorText, contextName);
                            if (directRepair.complete) {
                                String detail = directRepair.repairs.isEmpty()
                                        ? s("O reparo deterministico ja estava aplicado.",
                                                "The deterministic repair was already applied.")
                                        : android.text.TextUtils.join("\n", directRepair.repairs);
                                addMessage(new SdbAgenteActivity.ChatMessage(
                                        s("Correcao direta aplicada, mesmo sem operations da IA:\n",
                                                "Direct repair applied even without AI operations:\n")
                                                + detail, false), true);
                                if (freeBuildTurnArmed || freeBuildLoopActive) {
                                    freeBuildLoopActive = true;
                                    startFreeModeBuild();
                                } else {
                                    completeAgentTurn(s("Correcao de compilacao concluida.",
                                            "Compilation repair completed."));
                                }
                                return;
                            }
                        }
                        SdbAgenteActivity.ChatMessage msg = new SdbAgenteActivity.ChatMessage(response, false);
                        if (applyListener != null) {
                            msg.addAction("apply_context", s("🚀 Aplicar ao Contexto", "🚀 Apply to Context"), () -> {
                                applyResponseJson(response, turnSkillOperationPolicy);
                            });
                        }
                        addMessage(msg, true);
                        completeAgentTurn(s("Resposta do agente concluida.", "Agent response completed."));
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (runId != agentRunSerial || stopRequested) return;
                    int index = messages.indexOf(thinkingMsg);
                    if (index != -1) {
                        messages.remove(index);
                        adapter.notifyItemRemoved(index);
                    }
                    SketchwareUtil.toastError(error);
                    resetFreeBuildLoop(true);
                    addMessage(new SdbAgenteActivity.ChatMessage(s("Desculpe, ocorreu um erro: ", "Sorry, an error occurred: ") + error, false), true);
                    completeAgentTurn(s("Agente finalizou com erro.", "Agent finished with an error."));
                });
            }
        };

        java.util.List<SdbAgenteActivity.ChatMessage> historyForApi = new java.util.ArrayList<>(messages);
        if (!historyForApi.isEmpty()) historyForApi.remove(historyForApi.size() - 1); // Remove thinkingMsg
        if (!historyForApi.isEmpty()) historyForApi.remove(historyForApi.size() - 1); // Remove current userMsg
        // Remove ads (isAd=true → empty model turns that break Gemini's role-alternation)
        // and internal system messages (⚙️ processing indicators, "Aguardando..." placeholders)
        historyForApi.removeIf(m -> m.isAd
                || m.text.startsWith("⚙️")
                || m.text.startsWith("Aguardando resposta")
                || m.text.startsWith("Waiting for GC-AI"));
        historyForApi = trimHistoryForApi(historyForApi);

        String contextPrefix = (contextName != null) ? "CONTEXTO ATUAL: " + contextName + "\n" : "";
        String xmlPrefix = (contextXmlName != null) ? "TELA ATUAL (XML): " + contextXmlName + "\n" : "";
        String addon = (systemInstructionAddon != null) ? systemInstructionAddon + "\n" : "";

        // Compile error mode: inject error text into system instruction
        if (isCompileErrorMode && compileErrorText != null && !compileErrorText.trim().isEmpty()) {
            addon = "### ERROS DE COMPILAÇÃO DO PROJETO:\n```\n" + compileErrorText + "\n```\n"
                + "Analise os erros acima e corrija-os usando as operações disponíveis (`inject_code`, `create_java_file`, `edit_java_file`, `add_import`, etc.).\n"
                + "Foque APENAS em corrigir os erros. Não faça mudanças desnecessárias.\n\n";
        }

        if (isCompileErrorMode) {
            addon += "### REGRAS PARA CORRECAO DE COMPILACAO:\n"
                    + "- Leia o erro completo, identifique arquivo, linha, simbolo e causa raiz antes de editar.\n"
                    + "- A Activity citada no log e o contexto principal. Confira a lista completa de widgets, variaveis, listas, componentes e imports dessa tela.\n"
                    + "- Um simbolo ArrayList<HashMap<String, Object>> ausente deve ser criado com add_list e list_type Map antes do codigo que o usa.\n"
                    + "- Parametros numericos `%d.position` de MoreBlock sao gerados como double. Toda API posicional de List exige int: use `lista.get((int) _position)`, `set((int) _position, ...)`, `remove((int) _position)` e `add((int) _position, ...)`.\n"
                    + "- A regra de indice vale para listas Map, String, Number e Object. Nao troque o tipo da lista para corrigir um indice double.\n"
                    + "- Nunca tente resolver uma declaracao ausente adicionando apenas `if (simbolo == null)` ou `simbolo = new ...`; isso continua sem declarar o campo.\n"
                    + "- Um ID de View ausente nunca deve virar apenas uma variavel Java solta: reutilize o ID semantico existente, use rename_widget, ou crie o widget correto com add_widget.\n"
                    + "- Para ListView use o tipo real de ListView; para FAB confira se a tela possui FAB nativo ou crie um widget compativel com o codigo.\n"
                    + "- Use add_variable, add_list, add_component e add_import ANTES de inject_code no mesmo lote atomico.\n"
                    + "- Activities geradas pelo Sketchware devem ser corrigidas pelo modelo do projeto e por inject_code; nao tente sobrescrever o Java gerado como arquivo livre.\n"
                    + "- Arquivos `databinding/*Binding.java` sao gerados a partir do layout. Erros de campo/parametro duplicado devem ser corrigidos removendo ou renomeando IDs duplicados no XML/modelo; nunca edite o Binding.java.\n"
                    + "- Para arquivos livres do projeto, use read_file, patch_file ou write_file com path relativo.\n"
                    + "- Corrija todas as ocorrencias relacionadas no mesmo lote atomico e nao esconda erros com comentarios ou remocoes arbitrarias.\n\n";
        }

        if (runtimeCrashMode) {
            addon += "### DIAGNOSTICO DE CRASH EM EXECUCAO:\n"
                    + "- O contexto inclui o trecho numerado do Java realmente compilado nas linhas citadas pelo stack trace. Leia esse trecho antes de responder.\n"
                    + "- O Java gerado e somente diagnostico: corrija o evento, MoreBlock, lista, componente ou classe de origem usando operations; nao edite a Activity gerada por path.\n"
                    + "- Identifique a excecao, a expressao exata da linha e o estado que pode ser null, indice invalido ou tipo incorreto.\n"
                    + "- Adicione validacoes na origem sem esconder o erro nem apagar funcionalidade. Retorne pelo menos uma operacao aplicavel quando houver causa identificada.\n\n";
        }

        String designFocus = "";
        if (isCompileErrorMode) {
            designFocus = ""; // Sem restrição de modo no compile error — pode precisar de qualquer operação
        } else if (isCodeEditorMode) {
            // Aberto a partir do editor de código Java
            designFocus = "### MODO EDITOR DE CÓDIGO JAVA:\n"
                + "- **FOCO**: O usuário está editando um arquivo Java. Sua prioridade é `inject_code` ou `add_direct_code` para sincronizar lógica.\n"
                + "- **SALVAMENTO MANUAL**: Se o prompt indicar que o usuário editou o código manualmente, use `inject_code` para sincronizar.\n"
                + "- `add_drawable` / `edit_layout_xml` são operações de LAYOUT — use somente se explicitamente pedido junto com a lógica.\n"
                + "- **AUTO-SUFICIÊNCIA**: Gere o JSON completo. Não peça ao usuário para copiar e colar nada.\n\n";
        } else if (contextXmlName != null && contextName == null) {
            // Aberto a partir do editor de layout XML (sem contexto Java)
            designFocus = "### MODO EDITOR DE LAYOUT XML:\n"
                + "- **FOCO**: O usuário está editando um layout de tela. Use `edit_layout_xml`, `add_widget`, `update_widget`, `add_drawable`.\n"
                + "- **PROIBIDO NESTE MODO**: `inject_code`, `add_direct_code` — o usuário está no editor de DESIGN, não de código.\n"
                + "- Layout (.xml em /layout/) aceita SOMENTE Widgets. Drawable (.xml em /drawable/) aceita SOMENTE shapes/selectors. NUNCA misture.\n"
                + "- **AUTO-SUFICIÊNCIA**: Gere o JSON completo. Não peça ao usuário para copiar e colar nada.\n\n";
        }

        String imageInstruction = "";
        if (currentBase64Image != null) {
            imageInstruction = "### MODO REFERÊNCIA VISUAL:\n"
                + "A imagem anexada é seu guia principal. Analise o design, cores, arredondamentos e sombras.\n"
                + "**AÇÃO OBRIGATÓRIA**: Gere AUTOMATICAMENTE as operações JSON (`add_drawable` e `edit_layout_xml`) para implementar o design da imagem no projeto atual. Não apenas descreva, APLIQUE.\n\n";
        }

        String intentInstruction = "";
        if (chipGroupIntent != null) {
            int checkedId = chipGroupIntent.getCheckedChipId();
            if (checkedId == R.id.intent_plan) {
                intentInstruction = "### MODO PLAN / SEM PERMISSAO DE AGENTE:\n"
                    + "- Nao aplique mudancas e nao retorne JSON de operations.\n"
                    + "- Entregue um plano claro, com arquivos/telas que serao alterados e riscos.\n"
                    + "- Se precisar executar, diga que o usuario deve trocar para Auto/Agent e manter Auto-Aplicar ativado.\n\n";
            } else if (checkedId == R.id.intent_inject) {
                intentInstruction = "### OBJETIVO DO USUÁRIO (ESTRITO):\n"
                    + "- Injetar código no evento atual.\n"
                    + "- USE OBRIGATORIAMENTE: `add_direct_code`.\n"
                    + "- PROIBIDO: `add_moreblock`, `add_custom_block`.\n\n";
            } else if (checkedId == R.id.intent_moreblock) {
                intentInstruction = "### OBJETIVO DO USUÁRIO (ESTRITO):\n"
                    + "- Criar um MoreBlock nesta tela.\n"
                    + "- USE OBRIGATORIAMENTE: `add_moreblock`.\n"
                    + "- PROIBIDO: `add_custom_block`.\n\n";
            } else if (checkedId == R.id.intent_design) {
                intentInstruction = "### OBJETIVO DO USUÁRIO (ESTRITO):\n"
                    + "- Foco em DESIGN, ÍCONES e DRAWABLES.\n"
                    + "- USE OBRIGATORIAMENTE: `add_drawable`, `edit_layout_xml`, `update_widget`.\n"
                    + "- ÍCONES: SEMPRE use `add_drawable` com `<vector>` XML para ícones. NUNCA use `add_icon_resource` para ícones (pode gerar arquivos vazios que causam erros de compilação).\n"
                    + "- CRITICAL: Sempre aplique ícones e drawables via `android:src` ou `android:background` no widget alvo.\n\n";
            }
        }
        if (chipGroupIntent != null && chipGroupIntent.getCheckedChipId() == R.id.intent_inject) {
            intentInstruction = "### OBJETIVO DO USUARIO (ESTRITO):\n"
                + "- Injetar codigo em uma tela/evento do projeto, mesmo sem a tela estar aberta.\n"
                + "- USE OBRIGATORIAMENTE: `inject_code` com `java_name` e `event_name` explicitos.\n"
                + "- PROIBIDO: `add_direct_code`, `add_moreblock`, `add_custom_block`.\n\n";
        }

        // Final mode contract wins over the legacy specialist-chip hints above.
        // This mirrors Genesis Code: approval is safe by default, Agent applies,
        // and Plan never mutates until the user explicitly confirms it.
        if (isCompileErrorMode) {
            intentInstruction = "### MODO REPARO AUTOMATICO:\n"
                    + "- Execute a correcao neste turno e retorne operations JSON aplicaveis.\n"
                    + "- NUNCA pergunte se o usuario deseja executar, nunca pare em plano e nunca solicite confirmacao adicional.\n"
                    + "- Declare dependencias no modelo Sketchware antes de injetar o codigo que as utiliza.\n"
                    + "- NUNCA declare metodos public/private/protected dentro de onCreate, eventos ou add_direct_code. "
                    + "Crie ou atualize cada metodo auxiliar com add_moreblock/update_moreblock e deixe no evento apenas a chamada _nomeDoMetodo().\n"
                    + "- Todo MoreBlock e gerado pelo Sketchware como metodo Java prefixado com `_`: "
                    + "um MoreBlock `carregarDados` deve ser chamado como `_carregarDados()`, nunca `carregarDados()`.\n"
                    + "- Parametros `%d.nome` de MoreBlock viram `double _nome`; antes de usa-los como indice de lista, converta com `(int) _nome` em todas as ocorrencias.\n"
                    + "- Ao encontrar erros de sintaxe no inicio de um metodo, inspecione as chaves do evento e mova o metodo inteiro para MoreBlock; nao tente corrigir com imports.\n\n";
        } else if (isPlanMode() && !approvedPlan) {
            intentInstruction = "### MODO PLANO:\n"
                    + "- Responda em texto com um plano curto e numerado.\n"
                    + "- Nao gere JSON e nao aplique alteracoes antes da confirmacao.\n"
                    + "- Termine pedindo confirmacao para executar.\n\n";
        } else if (approvedPlan) {
            intentInstruction = "### PLANO APROVADO / EXECUCAO:\n"
                    + "- Execute agora o plano aprovado com operations JSON completas.\n"
                    + "- Leia o contexto, valide dependencias e nao devolva codigo solto.\n\n";
        } else if (isFreeMode()) {
            intentInstruction = "### MODO LIVRE:\n"
                    + "- Construa tudo o que o pedido exige neste turno, com operations JSON completas e autocontidas.\n"
                    + "- Nao pare em tutorial, sugestao ou plano: edite o projeto real.\n"
                    + "- Depois das edicoes, o app compilara o APK automaticamente. Se houver erro, voce recebera o log real para corrigir e tentar novamente.\n"
                    + "- Leia o contexto, declare dependencias antes do uso e nao anuncie conclusao antes da compilacao passar.\n\n";
        } else if (isAgentMode()) {
            intentInstruction = "### MODO AGENTE:\n"
                    + "- Execute o pedido agora com operations JSON completas e aplicaveis.\n"
                    + "- Aplique as edicoes diretamente, sem pedir uma segunda aprovacao.\n"
                    + "- Nao pare em tutorial ou plano quando o pedido exigir alterar o projeto.\n\n";
        } else {
            intentInstruction = "### MODO APROVACAO:\n"
                    + "- Gere operations JSON reais, mas elas serao mostradas ao usuario antes de aplicar.\n"
                    + "- Nao devolva tutorial, plano ou codigo solto quando o pedido exigir edicao.\n\n";
        }

        String sourceOfTruth;
        if (contextName == null) {
            // Modo projeto completo — listar telas e arquivos Java existentes para a IA usar
            StringBuilder screenList = new StringBuilder();
            StringBuilder javaFileList = new StringBuilder();
            String projectPackage = "";
            try {
                java.util.ArrayList<com.besome.sketch.beans.ProjectFileBean> files = a.a.a.jC.b(sc_id).b();
                if (files != null) {
                    for (com.besome.sketch.beans.ProjectFileBean f : files) {
                        screenList.append("  - java_name: \"").append(f.getJavaName())
                            .append("\" | xmlName: \"").append(f.getXmlName().replace(".xml","")).append("\"\n");
                    }
                }
            } catch (Exception ignored) {}
            try {
                pro.sketchware.utility.FileResConfig frc = new pro.sketchware.utility.FileResConfig(sc_id);
                java.util.ArrayList<String> javaFiles = frc.getJavaFile();
                for (String path : javaFiles) {
                    String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                    javaFileList.append("  - ").append(name).append("\n");
                }
            } catch (Exception ignored) {}
            try {
                for (java.util.HashMap<String, Object> proj : a.a.a.lC.a()) {
                    if (sc_id.equals(a.a.a.yB.c(proj, "sc_id"))) {
                        projectPackage = a.a.a.yB.c(proj, "my_sc_pkg_name");
                        break;
                    }
                }
            } catch (Exception ignored) {}
            // Check Material3 status
            boolean isMaterial3On = false;
            try {
                isMaterial3On = new com.besome.sketch.editor.manage.library.material3.Material3LibraryManager(sc_id).isMaterial3Enabled();
            } catch (Exception ignored) {}
            sourceOfTruth = "### MODO PROJETO COMPLETO (multi-tela):\n"
                + "- Você tem acesso a TODAS as telas do projeto.\n"
                + (projectPackage.isEmpty() ? "" : "- **PACKAGE DO PROJETO**: `" + projectPackage + "` (use SEMPRE no topo das classes Java)\n")
                + "- **MATERIAL 3**: " + (isMaterial3On ? "✅ HABILITADO — use atributos Material 3 (MaterialButton, TextInputLayout, etc.)" : "❌ Desabilitado — use `enable_material3` para ativar antes de usar componentes M3") + "\n"
                + "- **TELAS DISPONÍVEIS** (use estes nomes EXATOS nas operações):\n"
                + (screenList.length() > 0 ? screenList.toString() : "  (ver contexto abaixo)\n")
                + (javaFileList.length() > 0 ? "- **CLASSES JAVA EXISTENTES** (use `edit_java_file` para editar, `create_java_file` para criar novas):\n" + javaFileList.toString() : "")
                + "- **OBRIGATÓRIO**: Sempre inclua `\"java_name\"` e `\"xmlName\"` explicitamente em CADA operação.\n"
                + "- **PROIBIDO**: `add_direct_code` (requer evento aberto). Use SEMPRE `inject_code` com `java_name` + `event_name` para lógica Java.\n"
                + "- **ONCREATE REAL DO SKETCHWARE**: para o onCreate visual use `event_name: \"initializeLogic\"` ou `\"onCreate\"`; o motor salva em `onCreate_initializeLogic` e o código final entra em `initializeLogic()`.\n"
                + "- **ACTIVITY NATIVA**: nunca crie `MainActivity.java`/`LoginActivity.java` em `files/java` para editar tela normal. Use `inject_code`, `create_activity` e `edit_activity_layout`.\n"
                + "- **DESIGN GLOBAL**: pode editar qualquer tela sem ela estar aberta. Use `edit_layout_xml`, `add_widget`, `add_image_widget` ou `update_widget` com `\"xmlName\": \"nomeDaTela\"` explicito.\n"
                + "- **REFRESH VISUAL**: para design, use operacoes visuais sempre que possivel; elas atualizam o editor em tempo real. Se usar `write_file`, `patch_file`, `create_layout_xml` ou `create_drawable_xml`, finalize com `{ \"op\": \"refresh_project\" }`.\n"
                + "- **LAYOUT**: Use `edit_layout_xml` ou `add_widget` com `\"xmlName\": \"nomeDaTela\"` explícito (sem .xml).\n"
                + "- **MOREBLOCK**: Use `add_moreblock` SOMENTE SE o usuário pedir explicitamente uma função reutilizável.\n"
                + "- **COMPONENTES**: Use `add_component` antes de usar métodos de Firebase, RequestNetwork, SharedPreferences, Timer, Bluetooth, Location, Camera, etc. em `inject_code`.\n\n";
        } else {
            sourceOfTruth = "### FONTE DA VERDADE (INQUESTIONÁVEL):\n"
                + "- **CLASSE JAVA/ATIVIDADE**: `" + contextName + "`\n"
                + "- **LAYOUT XML**: `" + (contextXmlName != null ? contextXmlName : "Desconhecido") + "`\n"
                + "Use EXATAMENTE estes nomes em qualquer operação JSON. Não tente adivinhar ou sugerir outros nomes.\n\n";
        }

        String instruction = sourceOfTruth + contextPrefix + addon + intentInstruction + designFocus + imageInstruction
            + "### ⚠️ IDENTIDADE — LEIA ANTES DE QUALQUER COISA:\n"
            + "Você é o agente oficial GC-AI do **Sketchware-GC-AI** - um ambiente Android para criar aplicativos visualmente.\n"
            + "**VOCÊ NÃO ESTÁ NO ANDROID STUDIO.** As operações funcionam assim:\n"
            + "- `edit_layout_xml` / `add_widget` → edita SOMENTE layouts (XML de tela). O campo `xml_content` aceita SOMENTE tags de View (LinearLayout, Button, etc.). **Jamais coloque código Java dentro de xml_content.**\n"
            + "- `edit_layout_xml` representa apenas o CONTEUDO da tela. Nunca recrie `_coordinator`, `_app_bar`, `_toolbar`, AppBarLayout ou MaterialToolbar; o Sketchware gera esse shell automaticamente.\n"
            + "- Design tambem e global: nao precisa estar no editor XML. Sempre informe `xmlName` da tela alvo (`main`, `login`, etc.). Se o usuario pedir design profissional, prefira `edit_layout_xml` com tela completa bem estruturada.\n"
            + "- Apos qualquer mudanca de design, a interface do editor deve refletir o novo estado na hora; se editar arquivos diretamente, inclua `refresh_project` como ultima operacao.\n"
            + "- `inject_code` / `add_direct_code` → injeta código **Java** em eventos. Use SEMPRE para qualquer lógica Java (onClick, onCreate, etc.).\n"
            + "- No chat central do projeto, prefira SEMPRE `inject_code` com `java_name` e `event_name`. Use `add_direct_code` apenas quando o contexto local do editor estiver explicitamente aberto.\n"
            + "- Para onCreate no Sketchware, use `event_name: \"initializeLogic\"` ou `\"onCreate\"`; o motor salva em `onCreate_initializeLogic`. Não use `0_onCreate`.\n"
            + "- `replace_code_block` atualiza o bloco sincronizado do SDBCodFlow sem duplicar; `append_code_block` adiciona um novo bloco direto no evento alvo.\n"
            + "- `create_java_file` / `edit_java_file` → cria/edita classes Java auxiliares (Helper, Model, etc.).\n"
            + "- Não crie classe Java para Activity nativa. Não crie custom view quando o usuário pedir tela/Activity.\n"
            + "- `add_moreblock` → cria funções reutilizáveis. Use **SOMENTE** se o usuário pedir explicitamente.\n"
            + "- `add_component` → registra um componente Sketchware (Firebase, RequestNetwork, SharedPrefs, Timer, etc.). Use ANTES de chamar métodos do componente em `inject_code`.\n\n"
            + "**REGRA DE OURO**: Pediu criar tela/Activity nativa? → `create_activity`. Pediu código/lógica Java? → `inject_code`. Criar classe Java auxiliar? → `create_java_file`/`create_java_class`. Editar layout de tela existente? → `edit_layout_xml`. Firebase/Rede/etc? → `add_component` + `inject_code`. **Lista com item personalizado? → OBRIGATÓRIO os 4 passos: `edit_layout_xml`(com XML real) + `set_custom_view` + `add_view_event(onBindCustomView)` + `inject_code`(popular lista).** NUNCA omita passos.\n\n"
            + "### MODO AVANÇADO: EDIÇÃO DIRETA DE ARQUIVOS INTERNOS\n"
            + "Use este modo quando o usuário pedir criar/ajustar arquivos reais do projeto, classes auxiliares, resources XML, arquivos de injection, patches finos ou quando as operações visuais não forem suficientes.\n"
            + "- Caminho base seguro: `/.sketchware/data/{scId}/`. Em `path`, informe SOMENTE o caminho relativo, ex: `files/java/ApiClient.java`, `files/resource/layout/item_card.xml`, `files/resource/drawable/bg_card.xml`.\n"
            + "- `read_file`: `{ \"op\": \"read_file\", \"data\": { \"path\": \"files/java/ApiClient.java\" } }`\n"
            + "- `write_file`: `{ \"op\": \"write_file\", \"data\": { \"path\": \"files/java/ApiClient.java\", \"content\": \"// conteudo completo\" } }`\n"
            + "- `patch_file`: `{ \"op\": \"patch_file\", \"data\": { \"path\": \"files/java/ApiClient.java\", \"find\": \"trecho exato\", \"replace\": \"novo trecho\" } }`\n"
            + "- Aliases aceitos: `read_project_file`, `write_project_file`, `patch_project_file`.\n"
            + "- `create_java_class`: `{ \"op\": \"create_java_class\", \"data\": { \"class_name\": \"ApiClient\", \"content\": \"package ...;\\n\\npublic class ApiClient { }\" } }`\n"
            + "- `create_layout_xml`: `{ \"op\": \"create_layout_xml\", \"data\": { \"layout_name\": \"item_card\", \"xml_content\": \"<LinearLayout .../>\" } }` para layouts de recurso/custom view, não para tela visual principal.\n"
            + "- `create_drawable_xml`: `{ \"op\": \"create_drawable_xml\", \"data\": { \"drawable_name\": \"bg_card\", \"xml_content\": \"<shape .../>\" } }`\n"
            + "- `refresh_project`: `{ \"op\": \"refresh_project\" }` força a interface a refletir alterações feitas por arquivo.\n"
            + "- Segurança: nunca use `../`, caminho absoluto fora do projeto, Java dentro de XML ou drawable dentro de layout. Para tela principal do Sketchware, prefira `edit_layout_xml`; para classe Java auxiliar real, prefira `create_java_class`/`write_file`.\n\n"
            + "Modifique o projeto retornando um JSON estrito EM ADIÇÃO à sua resposta em texto amigável.\n\n"
            + "### GUIA DE OPERAÇÕES JSON:\n"
            + "1. **Injetar Lógica Local** `{ \"op\": \"add_direct_code\", \"data\": { \"code\": \"// java;\" } }` (Injeta no evento aberto)\n"
            + "2. **Injeção Global** `{ \"op\": \"inject_code\", \"data\": { \"attributes\": { \"java_name\": \"Home\", \"event_name\": \"onCreate\", \"code\": \"// java\" } } }` (Injeta em qualquer evento/tela)\n"
            + "   **ONCREATE/INITIALIZE**: `event_name: \"onCreate\"` ou `\"initializeLogic\"` sempre vira `onCreate_initializeLogic` internamente. Use isto para injetar no onCreate nativo do Sketchware sem abrir o editor de lógica.\n"
            + "   **ATUALIZAR BLOCO SINCRONIZADO**: `{ \"op\": \"replace_code_block\", \"data\": { \"java_name\": \"MainActivity\", \"event_name\": \"initializeLogic\", \"code\": \"// java\" } }`\n"
            + "   **ADICIONAR NOVO BLOCO DIRETO**: `{ \"op\": \"append_code_block\", \"data\": { \"java_name\": \"MainActivity\", \"event_name\": \"onResume\", \"code\": \"// java\" } }`\n"
            + "   **CRIAR EVENTO DE VIEW (onClick, onLongClick, etc.)**: `{ \"op\": \"add_view_event\", \"xmlName\": \"main\", \"data\": { \"java_name\": \"MainActivity.java\", \"view_id\": \"button1\", \"event_name\": \"onClick\", \"code\": \"// handler\" } }` — Registra o evento corretamente E injeta o código. Use SEMPRE isto (não `inject_code`) quando quiser criar um handler para um widget do design que ainda não tem evento. Suporta: `onClick`, `onLongClick`, `onTextChanged`, `onCheckedChanged`, etc.\n"
            + "3. **Adicionar Imports Java** `{ \"op\": \"add_import\", \"data\": { \"java_name\": \"Home\", \"code\": \"import java.util.List;\\nimport java.util.ArrayList;\" } }` (Adiciona imports sem sobrescrever os existentes — use SEMPRE que injetar código que precise de imports)\n"
            + "4. **CRIAR MOREBLOCK**: `{ \"op\": \"add_moreblock\", \"data\": { \"name\": \"meuMbr\", \"spec\": \"meuMbr %s.b\", \"code\": \"// java\" } }` (cria novo; spec: nome + params tipo %s.s=string %d.d=número %b.b=bool)\n"
            + "   **EDITAR MOREBLOCK existente**: `{ \"op\": \"update_moreblock\", \"data\": { \"name\": \"meuMbr\", \"spec\": \"meuMbr %s.s\", \"code\": \"// novo código java\" } }` (atualiza spec E corpo do moreblock existente; se não existir, cria)\n"
            + "   - O `code` de MoreBlock e SOMENTE o corpo do metodo: nunca use chaves para escapar do bloco e nunca declare `private/public void` no nivel superior. Crie cada auxiliar em outro `add_moreblock`/`update_moreblock`.\n"
            + "   **DELETAR MOREBLOCK**: `{ \"op\": \"delete_moreblock\", \"data\": { \"name\": \"meuMbr\" } }` (remove completamente)\n"
            + "5. **CRIAR BLOCO NA PALETA (Block Manager)**: `{ \"op\": \"add_custom_block\", \"data\": { \"palette_name\": \"MinhaPaleta\", \"palette_color\": \"#FF0000\", \"blocks\": [{ \"name\": \"meuBloco\", \"spec\": \"meu bloco %s\", \"type\": \" \", \"typeName\": \"\", \"opCode\": \"m_op\" }] } }` (Tipos: ` ` void, `c` if, `e` if-else, `s` string, `b` boolean, `d` number, `v` var, `a` map, `f` stop, `l` list)\n"
            + "   **EDITAR BLOCO existente**: `{ \"op\": \"update_custom_block\", \"data\": { \"name\": \"meuBloco\", \"blocks\": [{ \"name\": \"meuBloco\", \"spec\": \"novo spec %s\", \"type\": \" \", \"typeName\": \"\", \"opCode\": \"m_op\" }] } }` (identifique pelo `name` ou `op_code`; preserva palette)\n"
            + "   **DELETAR BLOCO**: `{ \"op\": \"delete_custom_block\", \"data\": { \"name\": \"meuBloco\" } }` ou `{ \"op\": \"delete_custom_block\", \"data\": { \"op_code\": \"m_op\" } }`\n"
            + "   **DELETAR PALETA INTEIRA**: `{ \"op\": \"delete_palette\", \"data\": { \"palette_name\": \"MinhaPaleta\" } }` (remove a paleta e todos os seus blocos)\n"
            + "6. **CRIAR/EDITAR DRAWABLE**: `{ \"op\": \"add_drawable\", \"data\": { \"drawable_name\": \"bg_card\", \"xml_content\": \"<shape xmlns:android=\\\"http://schemas.android.com/apk/res/android\\\"><solid android:color=\\\"#FF6200EE\\\"/><corners android:radius=\\\"12dp\\\"/></shape>\" } }` (sobrescreve se já existir)\n"
            + "   **DELETAR DRAWABLE**: `{ \"op\": \"delete_drawable\", \"data\": { \"drawable_name\": \"bg_card\" } }`\n"
            + "7. **ÍCONES — MÉTODO PREFERIDO** (use `add_drawable` com `<vector>`):\n"
            + "   `{ \"op\": \"add_drawable\", \"data\": { \"drawable_name\": \"ic_lock\", \"xml_content\": \"<vector xmlns:android=\\\"http://schemas.android.com/apk/res/android\\\" android:width=\\\"24dp\\\" android:height=\\\"24dp\\\" android:viewportWidth=\\\"24\\\" android:viewportHeight=\\\"24\\\" android:tint=\\\"#FFFFFF\\\"><path android:fillColor=\\\"@android:color/white\\\" android:pathData=\\\"M18,8h-1V6c0-2.76-2.24-5-5-5S7,3.24 7,6v2H6c-1.1,0-2,0.9-2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2-0.9 2-2V10c0-1.1-0.9-2-2-2zm-6,9c-1.1,0-2-0.9-2-2s0.9-2 2-2 2,0.9 2,2-0.9,2-2,2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71,0 3.1,1.39 3.1,3.1v2z\\\"/></vector>\" } }`\n"
            + "   Depois aplique: `{ \"op\": \"update_widget\", \"data\": { \"widget_id\": \"img1\", \"attributes\": { \"android:src\": \"@drawable/ic_lock\" } } }`\n"
            + "8. **Design / Widgets**:\n"
            + "   - **CRIAR ACTIVITY/TELA NATIVA**: `{ \"op\": \"create_activity\", \"data\": { \"screen_name\": \"login\", \"xml_content\": \"<LinearLayout xmlns:android=\\\"http://schemas.android.com/apk/res/android\\\" android:layout_width=\\\"match_parent\\\" android:layout_height=\\\"match_parent\\\" android:orientation=\\\"vertical\\\"/>\" } }`\n"
            + "   - **REGRA CRÍTICA**: Se o usuário pedir \"activity\", \"tela\", \"screen\", \"página\" ou \"view principal\", NUNCA crie custom view nem classe Java solta. Use `create_activity`. Use custom view somente para item de lista, componente reutilizável visual ou quando o usuário pedir explicitamente custom view.\n"
            + "   - **DESIGN PROFISSIONAL FORA DO XML**: mesmo no chat central, use `xmlName` explicito e aplique direto na estrutura interna do SK. Nao diga que precisa abrir a tela. Nao retorne apenas explicacao.\n"
            + "   - **REFRESH IMEDIATO**: `edit_layout_xml`, `add_widget`, `update_widget` e `remove_widget` atualizam o editor visual automaticamente. Para edicao direta de arquivos, adicione `refresh_project` no final.\n"
            + "   - **IMAGEM NA TELA**: `{ \"op\": \"add_image_widget\", \"xmlName\": \"main\", \"data\": { \"widget_id\": \"img_banner\", \"parent_id\": \"root\", \"drawable_name\": \"bg_banner\", \"attributes\": { \"android:layout_width\": \"match_parent\", \"android:layout_height\": \"180dp\", \"android:scaleType\": \"centerCrop\", \"android:adjustViewBounds\": \"true\" } } }`. Se nao houver imagem real, crie antes um drawable/vector/shape decorativo com `add_drawable` ou crie um ImageView placeholder profissional.\n"
            + "   - **RECONSTRUIR TELA BONITA**: para tela simples, prefira `edit_layout_xml` com XML completo contendo root, containers, TextViews, Buttons, ImageViews e estilos. Isso substitui o layout visual da tela alvo e aparece no editor visual.\n"
            + "   - **EDITAR TELA COMPLETA**: `{ \"op\": \"edit_layout_xml\", \"xmlName\": \"main\", \"data\": { \"xml_content\": \"<LinearLayout...>...</LinearLayout>\" } }`\n"
            + "   - **EDITAR ACTIVITY EXISTENTE**: `{ \"op\": \"edit_activity_layout\", \"xmlName\": \"login\", \"data\": { \"xml_content\": \"<LinearLayout...>...</LinearLayout>\" } }` (alias seguro de `edit_layout_xml` para tela nativa).\n"
            + "   - **Adicionar widget**: `{ \"op\": \"add_widget\", \"xmlName\": \"main\", \"data\": { \"widget_id\": \"b1\", \"widget_type\": 3, \"parent_id\": \"root\", \"attributes\": { \"text\": \"Ok\" } } }` (Tipos: 0:Linear, 1:Relative, 3:Button, 4:TextView, 8:ImageView...)\n"
            + "   - **Editar widget**: `{ \"op\": \"update_widget\", \"xmlName\": \"main\", \"data\": { \"widget_id\": \"b1\", \"attributes\": { \"android:text\": \"Novo texto\", \"android:background\": \"@drawable/bg_card\" } } }`\n"
            + "   - **Remover widget**: `{ \"op\": \"remove_widget\", \"xmlName\": \"main\", \"data\": { \"widget_id\": \"b1\" } }`\n\n"
            + "9. **CLASSES JAVA (Helper, Utils, Model, etc.)**:\n"
            + "   - **CRIAR classe**: `{ \"op\": \"create_java_file\", \"data\": { \"file_name\": \"MyHelper\", \"content\": \"package com.example;\\n\\npublic class MyHelper {\\n    // ...\\n}\" } }`\n"
            + "   - **EDITAR classe existente**: `{ \"op\": \"edit_java_file\", \"data\": { \"file_name\": \"MyHelper\", \"content\": \"package com.example;\\n\\npublic class MyHelper {\\n    // código atualizado\\n}\" } }`\n"
            + "   - **DELETAR classe**: `{ \"op\": \"delete_java_file\", \"data\": { \"file_name\": \"MyHelper\" } }`\n"
            + "   - **REGRAS**: Inclua SEMPRE o `package` correto no topo. Use `file_name` apenas com o nome simples da classe (sem .java). Para usar a classe nas activities, combine com `add_import` e `inject_code`.\n\n"
            + "10. **PERMISSÕES (AndroidManifest.xml)**:\n"
            + "   - **ADICIONAR permissão**: `{ \"op\": \"add_permission\", \"data\": { \"name\": \"android.permission.INTERNET\" } }` — Adiciona ao AndroidManifest.xml\n"
            + "   - **Forma curta aceita**: `{ \"op\": \"add_permission\", \"data\": { \"name\": \"INTERNET\" } }` → adiciona `android.permission.INTERNET` automaticamente\n"
            + "   - **REMOVER permissão**: `{ \"op\": \"remove_permission\", \"data\": { \"name\": \"android.permission.CAMERA\" } }`\n"
            + "   - **Permissões comuns**: `INTERNET`, `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `RECORD_AUDIO`, `VIBRATE`, `BLUETOOTH`, `READ_CONTACTS`\n"
            + "   - **REGRA**: Sempre adicione permissões necessárias no mesmo array de operações quando injetar código que as exija. Ex: usar câmera → adicione `CAMERA`; fazer requisições de rede → adicione `INTERNET`.\n\n"
            + "11. **MATERIAL 3 (Google Material Design 3)**:\n"
            + "   - **ATIVAR Material 3**: `{ \"op\": \"enable_material3\" }` — Habilita AppCompat + Material3 no projeto (equivale a marcar o checkbox manualmente)\n"
            + "   - **REGRA**: Se o usuário pedir design profissional/moderno ou componentes M3, SEMPRE inclua `enable_material3` no início do array de operações.\n"
            + "   - **Após ativar**, use nos layouts: `MaterialButton`, `TextInputLayout`, `MaterialCardView`, `BottomNavigationView`, `FloatingActionButton`, `Chip`, `ChipGroup`, `MaterialAlertDialog`.\n"
            + "   - **Atributos M3**: `style=\"@style/Widget.Material3.Button\"`, `style=\"@style/Widget.Material3.CardView.Elevated\"`, `app:cornerRadius=\"12dp\"`\n"
            + "   - **Cores M3**: use `?attr/colorPrimary`, `?attr/colorSecondary`, `?attr/colorSurface`, `?attr/colorOnSurface`, `?attr/colorSurfaceVariant`\n"
            + "   - **Imports necessários**: `com.google.android.material.button.MaterialButton`, `com.google.android.material.card.MaterialCardView`, etc.\n\n"
            + "12. **VARIÁVEIS E LISTAS** (declaradas no editor de lógica do Sketchware):\n"
            + "   - **CRIAR variável**: `{ \"op\": \"add_variable\", \"data\": { \"java_name\": \"MainActivity\", \"name\": \"contador\", \"var_type\": \"Number\" } }`\n"
            + "   - Tipos de var_type: `\"String\"`, `\"Number\"`, `\"Boolean\"`, `\"Map\"` (Map = HashMap<String, Object>)\n"
            + "   - **CRIAR lista**: `{ \"op\": \"add_list\", \"data\": { \"java_name\": \"MainActivity\", \"name\": \"minhaLista\", \"list_type\": \"Map\" } }`\n"
            + "   - Tipos de list_type: `\"String\"`, `\"Number\"`, `\"Map\"` — use SEMPRE `\"Map\"` para dados de RecyclerView/ListView com múltiplos campos\n"
            + "   - **REGRA**: Toda variável ou lista usada em `inject_code` deve ser declarada com `add_variable`/`add_list` ANTES de ser usada no código.\n"
            + "   - **HashMap em Map List**: para adicionar item → `hashMap.put(\"key\", value); lista.add(hashMap);`\n\n"
            + "13. **LISTAS COM CUSTOM VIEW (ListView/GridView com item personalizado)** — SEQUÊNCIA OBRIGATÓRIA, TODOS OS 4 PASSOS:\n"
            + "   ⚠️ **REGRA CRÍTICA**: Para ListView com item personalizado, você DEVE emitir TODOS os passos abaixo no mesmo array de operações. Omitir qualquer passo causa falha visual.\n\n"
            + "   **PASSO 1 — OBRIGATÓRIO: Criar o layout do item com widgets reais** (NÃO use placeholder — coloque widgets de verdade):\n"
            + "   ```json\n"
            + "   { \"op\": \"edit_layout_xml\", \"xmlName\": \"item_lista\", \"data\": { \"xml_content\": \"<LinearLayout xmlns:android=\\\"http://schemas.android.com/apk/res/android\\\" android:layout_width=\\\"match_parent\\\" android:layout_height=\\\"wrap_content\\\" android:orientation=\\\"horizontal\\\" android:padding=\\\"12dp\\\"><ImageView android:id=\\\"@+id/imgItem\\\" android:layout_width=\\\"48dp\\\" android:layout_height=\\\"48dp\\\" android:scaleType=\\\"centerCrop\\\"/><LinearLayout android:layout_width=\\\"0dp\\\" android:layout_height=\\\"wrap_content\\\" android:layout_weight=\\\"1\\\" android:orientation=\\\"vertical\\\" android:paddingStart=\\\"8dp\\\"><TextView android:id=\\\"@+id/txTitulo\\\" android:layout_width=\\\"match_parent\\\" android:layout_height=\\\"wrap_content\\\" android:textSize=\\\"16sp\\\" android:textStyle=\\\"bold\\\"/><TextView android:id=\\\"@+id/txSubtitulo\\\" android:layout_width=\\\"match_parent\\\" android:layout_height=\\\"wrap_content\\\" android:textSize=\\\"13sp\\\"/></LinearLayout></LinearLayout>\" } }\n"
            + "   ```\n"
            + "   ↑ ADAPTE o XML ao contexto do usuário (quantos campos, quais tipos de widget), mas SEMPRE coloque widgets reais com IDs reais.\n\n"
            + "   **PASSO 2 — OBRIGATÓRIO: Conectar o ListView ao layout do item** (SEM ISSO o ListView NÃO exibe o item personalizado no visual):\n"
            + "   ```json\n"
            + "   { \"op\": \"set_custom_view\", \"xmlName\": \"main\", \"data\": { \"view_id\": \"listview1\", \"custom_view\": \"item_lista\" } }\n"
            + "   ```\n"
            + "   ↑ `xmlName` = tela que contém o ListView · `view_id` = ID do ListView · `custom_view` = nome do layout do item (sem .xml)\n\n"
            + "   **PASSO 3 — OBRIGATÓRIO: Registrar o evento onBindCustomView**:\n"
            + "   ```json\n"
            + "   { \"op\": \"add_view_event\", \"xmlName\": \"main\", \"data\": { \"java_name\": \"MainActivity\", \"view_id\": \"listview1\", \"event_name\": \"onBindCustomView\", \"code\": \"HashMap<String, Object> _item = (HashMap<String, Object>) minhaLista.get(position);\\nTextView _txTitulo = (TextView) view.findViewById(R.id.txTitulo);\\n_txTitulo.setText(_item.get(\\\"titulo\\\").toString());\" } }\n"
            + "   ```\n"
            + "   ↑ Parâmetros disponíveis dentro do onBindCustomView: `position` (int) e `view` (View — a raiz do item renderizado). Use `view.findViewById(R.id.xxx)` para acessar os widgets do item.\n\n"
            + "   **PASSO 4 — Popular a lista e notificar o adapter** (via inject_code no evento desejado, ex: onCreate):\n"
            + "   ```java\n"
            + "   HashMap<String, Object> _map = new HashMap<>();\n"
            + "   _map.put(\"titulo\", \"Item 1\");\n"
            + "   minhaLista.add(_map);\n"
            + "   // Para atualizar após adicionar itens:\n"
            + "   if (listview1.getAdapter() instanceof android.widget.BaseAdapter) {\n"
            + "       ((android.widget.BaseAdapter) listview1.getAdapter()).notifyDataSetChanged();\n"
            + "   }\n"
            + "   ```\n\n"
            + "   - **PROIBIDO**: Não injete código de adapter manualmente — o Sketchware gera o adapter automaticamente a partir do `onBindCustomView`.\n"
            + "   - **GridView**: use `ListView` (tipo 9) com `android:numColumns` no XML para comportamento de grid.\n\n"
            + "   - **CUSTOM VIEW DE LISTA**: primeiro use `set_custom_view` no ListView/RecyclerView; depois use `add_view_event` com `view_id` do componente de lista, `event_name: \"onBindCustomView\"` e o codigo de binding. O motor registra e injeta em `<view_id>_onBindCustomView`, nunca no onCreate.\n"
            + "   - Exemplo onBind: `{ \"op\": \"add_view_event\", \"xmlName\": \"main\", \"data\": { \"java_name\": \"MainActivity\", \"view_id\": \"listview1\", \"event_name\": \"onBindCustomView\", \"code\": \"textview1.setText(lista.get((int)_position).get(\\\"titulo\\\").toString());\" } }`.\n"
            + "14. **COMPONENTES** (Firebase, Rede, Bluetooth, Localização, etc.):\n"
            + "   - **Sintaxe**: `{ \"op\": \"add_component\", \"data\": { \"java_name\": \"MainActivity\", \"name\": \"meuBanco\", \"component_type\": \"FirebaseDB\", \"param1\": \"/\" } }`\n"
            + "   - **`name`** = ID do componente (identificador único na atividade, ex: `\"meuBanco\"`, `\"rede\"`)\n"
            + "   - **`component_type`** aceita: `\"FirebaseDB\"` (param1=caminho ref, ex: `\"/\"`) · `\"FirebaseAuth\"` · `\"FirebaseStorage\"` (param1=caminho ref) · `\"RequestNetwork\"` · `\"SharedPreferences\"` (param1=nome do arquivo, ex: `\"config\"`) · `\"Timer\"` · `\"BluetoothConnect\"` · `\"LocationManager\"` · `\"FCM\"` · `\"Camera\"` · `\"MediaPlayer\"` · `\"SoundPool\"` · `\"TextToSpeech\"` · `\"SpeechToText\"` · `\"InterstitialAd\"` · `\"RewardedVideoAd\"` · `\"Notification\"` · `\"Dialog\"` · `\"ProgressDialog\"` · `\"Vibrator\"` · `\"Gyroscope\"` · `\"FilePicker\"` · `\"ObjectAnimator\"`\n"
            + "   - **REGRA**: Sempre adicione um componente ANTES de usar seus métodos em `inject_code`. Ex: para usar `requestNetwork1.startRequestNetwork(...)`, primeiro emita `add_component` com `component_type: \"RequestNetwork\"` e `name: \"requestNetwork1\"`.\n"
            + "   - **REGRA CRITICA DE COMPILACAO**: antes de qualquer `inject_code`, verifique todos os nomes usados (`pd`, `timer`, `t`, `prefs`, `intent`, `rede`, etc.). Se o nome nao for View, parametro local ou variavel local declarada no proprio codigo, crie antes com `add_component`, `add_variable` ou `add_list`. NUNCA injete codigo com identificador nao declarado.\n"
            + "   - **ProgressDialog**: se o codigo usa `pd = new ProgressDialog(...)`, `pd.show()` ou `pd.dismiss()`, emita antes `{ \"op\": \"add_component\", \"data\": { \"java_name\": \"MainActivity\", \"name\": \"pd\", \"component_type\": \"ProgressDialog\" } }`. Depois use `pd.setMessage(...)`, `pd.show()`, `pd.dismiss()` sem redeclarar `ProgressDialog pd` dentro do evento.\n"
            + "   - **Timer**: se usar delay/agendamento, emita antes `{ \"op\": \"add_component\", \"data\": { \"java_name\": \"MainActivity\", \"name\": \"t\", \"component_type\": \"Timer\" } }`. O componente declara `TimerTask t` e o SK cria o timer global `_timer`; no codigo use `_timer.schedule(t, 3000);`, nunca `timer.schedule(t, ...)`.\n"
            + "   - **TimerTask manual**: para codigo com `t = new TimerTask() { ... }`, o nome `t` deve existir como componente Timer. Nao use `add_variable` para `TimerTask`; use `add_component` com `component_type: \"Timer\"` e `name: \"t\"`.\n"
            + "   - **Componentes que precisam de `param1`**: `FirebaseDB` e `FirebaseStorage` exigem o caminho de referência (ex: `\"/users\"`). `SharedPreferences` aceita o nome do arquivo de preferências (ex: `\"config\"`). Os demais não precisam de `param1`.\n"
            + "   - **Exemplo RequestNetwork**: `{ \"op\": \"add_component\", \"data\": { \"java_name\": \"MainActivity\", \"name\": \"rede\", \"component_type\": \"RequestNetwork\" } }` — depois em inject_code: `rede.startRequestNetwork(\"GET\", \"https://api.example.com/data\", \"\", rede_request_listener);`\n"
            + "   - **Exemplo SharedPreferences**: `{ \"op\": \"add_component\", \"data\": { \"java_name\": \"MainActivity\", \"name\": \"prefs\", \"component_type\": \"SharedPreferences\", \"param1\": \"config\" } }`\n\n"
            + "### REGRAS DE DESIGN PROFISSIONAL (OBRIGATÓRIO):\n"
            + "- **ImageView**: SEMPRE inclua `android:scaleType=\"fitCenter\"` e `android:adjustViewBounds=\"true\"` em todo ImageView.\n"
            + "- **Drawables como fundos**: Prefira drawables (shapes/gradients) como `android:background=\"@drawable/nome\"` ao invés de cores brutas para design profissional.\n"
            + "- **Bordas arredondadas**: Use `<shape><corners android:radius=\"Xdp\"/></shape>` via `add_drawable` + aplique com `android:background`.\n"
            + "- **Gradiente**: `<gradient android:startColor=\"#X\" android:endColor=\"#Y\" android:angle=\"270\"/>` dentro de `<shape>`.\n"
            + "- **Sombra/Elevação**: Use `android:elevation=\"4dp\"` e `android:stateListAnimator=\"@null\"` em views que precisem de sombra Material.\n"
            + "- **Paleta Material Design**: Prefira cores Material (ex: #6200EE primary, #03DAC6 accent, #FFFFFF surface, #000000 on-surface).\n"
            + "- **Espaçamento**: Use `android:padding` (8dp, 16dp, 24dp) e `android:layout_margin` consistentes para hierarquia visual clara.\n"
            + "- **Imports**: Sempre que criar lógica Java que usa classes externas, inclua as operações `add_import` correspondentes no mesmo array de operações.\n";
        
        instruction += offlineSkillInstruction;
        instruction += "\n### CONTRATO DE EXECUCAO ATOMICA:\n"
                + "- Retorne um unico objeto JSON com operations; nao misture prosa ou Markdown.\n"
                + "- Use o xmlName exato incluindo .xml e IDs existentes no contexto.\n"
                + "- Ordene dependencias antes do uso: recursos, componentes, parents, widgets, eventos e codigo.\n"
                + "- Prefira update_widget para mudancas locais; use edit_layout_xml para reescrita estrutural.\n"
                + "- Todo o lote e transacional: uma operacao invalida desfaz as anteriores.\n";
        instruction += "\n### SKILLS OFFLINE GC-AI:\n"
                + "- Crie ou atualize uma Skill somente quando o usuario pedir explicitamente para salvar, aprender, criar ou atualizar uma Skill.\n"
                + "- create_skill e update_skill devem ser a unica operacao do lote. Skills novas sempre ficam candidatas.\n"
                + "- Formato: {\"op\":\"create_skill\",\"data\":{\"skill_id\":\"id-minusculo\",\"name\":\"Nome\",\"version\":\"1.0.0\",\"author\":\"GC-AI local\",\"description\":\"...\",\"triggers\":[\"texto do erro\"],\"rules\":[\"regra tecnica generica\"],\"permissions\":[\"read_logic\"],\"skill_operations\":[\"repair_list_indices\"],\"tests\":[{\"input\":\"exemplo\",\"should_match\":true}]}}.\n"
                + "- Nunca salve nomes privados do projeto, chaves de API, caminhos pessoais ou codigo do usuario em uma Skill compartilhavel. Generalize exemplos e regras.\n";

        // Provide current XML layout for better AI context if in design focus
        if (contextXmlName != null) {
            try {
                String baseXml = contextXmlName.endsWith(".xml") ? contextXmlName : contextXmlName + ".xml";
                String currentXml = "";
                
                if (isCodeEditorMode && originalCode != null) {
                    currentXml = originalCode;
                }
                
                if (currentXml.isEmpty()) {
                    ArrayList<ViewBean> beans = jC.a(sc_id).d(baseXml);
                    ProjectFileBean pfb = jC.b(sc_id).b(baseXml);
                    ViewBean fb = jC.a(sc_id).h(baseXml);
                    if (beans != null && pfb != null) {
                        jq buildConfig = new jq();
                        buildConfig.sc_id = sc_id;
                        buildConfig.g = true; // Use AppCompat
                        Ox xmlGenerator = new Ox(buildConfig, pfb);
                        xmlGenerator.a(beans, fb);
                        currentXml = xmlGenerator.b();
                    }
                }
                
                if (!currentXml.isEmpty()) {
                    xmlPrefix = "ESTRUTURA ATUAL DA TELA (" + contextXmlName + "):\n```xml\n" + currentXml + "\n```\n";
                }
            } catch (Exception e) {}
        }
        
        // Re-fetch originalCode if we are in code editor mode just to be safe
        if (isCodeEditorMode && originalCode != null && !xmlPrefix.contains(originalCode)) {
            String codeLang = (contextName != null && contextName.toLowerCase().endsWith(".java")) ? "java" : "xml";
            xmlPrefix = "CÓDIGO ATUAL DO EDITOR:\n```" + codeLang + "\n" + originalCode + "\n```\n" + xmlPrefix;
        }
        
        setThinking(true);
        if (currentBase64Image != null) {
            String combinedPrompt = contextInfo + "\n\n" + instruction + "\n" + xmlPrefix + "\n" + (text.isEmpty() ? "Analise e aplique o design desta imagem." : text);
            agente.askWithImage(combinedPrompt.replace(text, effectiveRequest), contextInfo, currentBase64Image, currentMimeType, listener);
            currentBase64Image = null;
            currentMimeType = null;
            layoutImagePreview.setVisibility(View.GONE);
        } else {
            agente.askWithHistory(effectiveRequest, contextInfo + "\n\n" + instruction + "\n" + xmlPrefix, historyForApi, listener);
        }
    }

    private static boolean looksLikeRuntimeCrash(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(java.util.Locale.US);
        boolean stackLine = java.util.regex.Pattern.compile(
                "[A-Za-z_$][A-Za-z0-9_$]*\\.java:\\d+").matcher(text).find();
        return stackLine && (lower.contains("exception") || lower.contains("crash")
                || lower.contains("fatal") || lower.contains("at "));
    }

    private static boolean hasActionableOperations(String json) {
        if (json == null || json.trim().isEmpty()) return false;
        try {
            String clean = json.trim();
            org.json.JSONArray operations;
            if (clean.startsWith("[")) {
                operations = new org.json.JSONArray(clean);
            } else {
                org.json.JSONObject root = new org.json.JSONObject(clean);
                operations = root.optJSONArray("operations");
                if (operations == null) operations = new org.json.JSONArray().put(root);
            }
            for (int i = 0; i < operations.length(); i++) {
                org.json.JSONObject operation = operations.optJSONObject(i);
                if (operation != null && !operation.optString("op", "").trim().isEmpty()) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isSkillLifecycleResponse(String json) {
        if (json == null) return false;
        try {
            org.json.JSONArray operations;
            String clean = json.trim();
            if (clean.startsWith("[")) {
                operations = new org.json.JSONArray(clean);
            } else {
                org.json.JSONObject root = new org.json.JSONObject(clean);
                operations = root.optJSONArray("operations");
                if (operations == null) operations = new org.json.JSONArray().put(root);
            }
            if (operations.length() != 1) return false;
            String op = operations.optJSONObject(0) == null ? ""
                    : operations.optJSONObject(0).optString("op", "");
            return "create_skill".equals(op) || "update_skill".equals(op);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String validateActiveSkillPolicy(String json, boolean lifecycle,
                                             java.util.Set<String> operationPolicy) {
        if (lifecycle || operationPolicy == null) return null;
        try {
            org.json.JSONArray operations;
            String clean = json == null ? "" : json.trim();
            if (clean.startsWith("[")) {
                operations = new org.json.JSONArray(clean);
            } else {
                org.json.JSONObject root = new org.json.JSONObject(clean);
                operations = root.optJSONArray("operations");
                if (operations == null) operations = new org.json.JSONArray().put(root);
            }
            for (int i = 0; i < operations.length(); i++) {
                org.json.JSONObject operation = operations.optJSONObject(i);
                String name = operation == null ? "" : operation.optString("op", "");
                if (!operationPolicy.contains(name)) {
                    return s("Skill bloqueou uma operacao fora do escopo: ",
                            "Skill blocked an out-of-scope operation: ") + name;
                }
            }
            return null;
        } catch (Exception error) {
            return s("Nao foi possivel validar as permissoes da Skill.",
                    "Could not validate Skill permissions.");
        }
    }

    private void applyResponseJson(String json) {
        applyResponseJson(json, activeSkillOperationPolicy);
    }

    private void applyResponseJson(String json, java.util.Set<String> operationPolicy) {
        boolean skillLifecycleResponse = isSkillLifecycleResponse(json);
        // Keep model and open editors atomic: callbacks run only after commit.
        SdbCompileRepairEngine.ImmediateResult immediateRepair = isCompileErrorMode
                && !skillLifecycleResponse
                ? SdbCompileRepairEngine.repairCompileIssuesImmediately(
                        sc_id, compileErrorText, contextName)
                : new SdbCompileRepairEngine.ImmediateResult(false, new ArrayList<>());
        if (!immediateRepair.repairs.isEmpty()) {
            addMessage(new SdbAgenteActivity.ChatMessage(
                    s("Correcao direta aplicada:\n", "Direct repair applied:\n")
                            + android.text.TextUtils.join("\n", immediateRepair.repairs), false), true);
        }
        SdbCompileRepairEngine.Result reinforced = isCompileErrorMode
                && !skillLifecycleResponse
                ? SdbCompileRepairEngine.reinforce(sc_id, compileErrorText, json, contextName)
                : new SdbCompileRepairEngine.Result(json, new ArrayList<>());
        if (!reinforced.repairs.isEmpty()) {
            addMessage(new SdbAgenteActivity.ChatMessage(
                    s("Reparo estrutural automatico:\n", "Automatic structural repair:\n")
                            + android.text.TextUtils.join("\n", reinforced.repairs), false), true);
        }
        String effectiveJson = reinforced.json;
        String skillPolicyError = validateActiveSkillPolicy(
                effectiveJson, skillLifecycleResponse, operationPolicy);
        if (skillPolicyError != null) {
            addMessage(new SdbAgenteActivity.ChatMessage(skillPolicyError, false), true);
            return;
        }
        String operationFingerprint = operationFingerprint(effectiveJson);
        if (!beginOperationAttempt(operationFingerprint)) {
            addMessage(new SdbAgenteActivity.ChatMessage(s(
                    "Este mesmo lote ja foi processado recentemente. A reaplicacao foi bloqueada para impedir loop e corrupcao do projeto.",
                    "This same batch was processed recently. Reapplication was blocked to prevent loops and project corruption."), false), true);
            resetFreeBuildLoop(true);
            return;
        }
        boolean engineInjected = SdbEditEngine.applyEdits(sc_id, effectiveJson, contextXmlName, contextName);
        if (!engineInjected) {
            finishOperationAttempt(operationFingerprint, "failed");
            if (immediateRepair.complete) {
                addMessage(new SdbAgenteActivity.ChatMessage(
                        s("Correcao estrutural concluida diretamente. Compile o projeto novamente.",
                                "Structural repair completed directly. Compile the project again."),
                        false), true);
                if (freeBuildTurnArmed || freeBuildLoopActive) {
                    freeBuildLoopActive = true;
                    startFreeModeBuild();
                }
                return;
            }
            if (operationPolicy == null && tryAutoRepair(effectiveJson, null)) return;
            SdbEditEngine.ApplyReport failedReport = SdbEditEngine.getLastApplyReport();
            String detail = failedReport != null ? failedReport.toUserSummary()
                    : SdbEditEngine.getLastApplyError();
            addMessage(new SdbAgenteActivity.ChatMessage(
                    s("Falha ao aplicar as edicoes.", "Failed to apply edits.") + "\n\n" + detail,
                    false), true);
            resetFreeBuildLoop(true);
            return;
        }
        finishOperationAttempt(operationFingerprint, "complete");

        if (skillLifecycleResponse) {
            SdbEditEngine.ApplyReport report = SdbEditEngine.getLastApplyReport();
            addMessage(new SdbAgenteActivity.ChatMessage(
                    s("Skill offline salva como candidata. Revise em + > Gerenciar Skills.",
                            "Offline Skill saved as a candidate. Review it in + > Manage Skills.")
                            + (report == null ? "" : "\n\n" + report.toUserSummary()), false), true);
            return;
        }

        if (isCompileErrorMode && !skillLifecycleResponse) {
            SdbCompileRepairEngine.repairViewReferencesImmediately(
                    sc_id, compileErrorText, contextName);
        }

        boolean logicInjected = false;
        if (applyListener != null && !skillLifecycleResponse) {
            // Priority: Logic Editor Injection
            applyListener.onApply(json);
            logicInjected = true;
        }

        boolean codeInjected = false;

        // Handle Code Editor Synchronization
        if (codeApplyListener != null && !skillLifecycleResponse) {
            try {
                org.json.JSONArray ops = null;
                if (json.trim().startsWith("[")) {
                     ops = new org.json.JSONArray(json);
                } else {
                     org.json.JSONObject obj = new org.json.JSONObject(json);
                     if (obj.has("operations")) {
                         ops = obj.getJSONArray("operations");
                     } else if (obj.has("op") && "edit_layout_xml".equals(obj.getString("op"))) {
                         ops = new org.json.JSONArray();
                         ops.put(obj);
                     }
                }

                if (ops != null) {
                    String targetXml = contextXmlName != null ? contextXmlName : "";
                    for (int i = 0; i < ops.length(); i++) {
                        org.json.JSONObject op = ops.getJSONObject(i);
                        String opName = op.optString("op", "");
                        
                        if ("edit_layout_xml".equals(opName)) {
                             String opXml = op.optString("xmlName", "");
                             if (opXml.isEmpty() || opXml.equals(targetXml.replace(".xml", ""))) {
                                 String newXml = op.getJSONObject("data").getString("xml_content");
                                 String lower = newXml.toLowerCase();
                                 if (!lower.contains("<shape") && !lower.contains("<selector") && !lower.contains("<layer-list") && !lower.contains("<gradient")) {
                                     codeApplyListener.onCodeApply(formatXml(newXml));
                                     codeInjected = true;
                                     break;
                                 }
                             }
                        } else if ("add_direct_code".equals(opName) || "inject_code".equals(opName)) {
                             // Sync Java editor with injected code if possible
                             String newCode = op.optJSONObject("data") != null ? op.optJSONObject("data").optString("code", "") : "";
                             if (newCode.isEmpty() && op.optJSONObject("data") != null && op.optJSONObject("data").optJSONObject("attributes") != null) {
                                 newCode = op.optJSONObject("data").optJSONObject("attributes").optString("code", "");
                             }
                             
                             if (!newCode.isEmpty()) {
                                 codeApplyListener.onCodeApply(newCode);
                                 codeInjected = true;
                                 break;
                             }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore sync errors
            }
        }
        
        String directSummary = SdbDirectFileEngine.consumeSummary();
        
        if (logicInjected || engineInjected || codeInjected) {
            String successText = s("✅ **Edições aplicadas!**", "✅ **Edits applied!**");
            if (directSummary != null && !directSummary.isEmpty()) {
                successText += "\n\n" + s("**Arquivos internos:**", "**Internal files:**") + "\n" + directSummary;
            }
            SdbEditEngine.ApplyReport report = SdbEditEngine.getLastApplyReport();
            if (report != null) successText += "\n\n" + report.toUserSummary();
            SdbAgenteActivity.ChatMessage successMsg = new SdbAgenteActivity.ChatMessage(successText, false);

            // Add Global Save Action
            successMsg.addAction("save_project", s("Salvar Mudanças", "Save Changes"), () -> handleSaveAction());

            // Add Layout Preview Action only in XML code editor context
            if (isCodeEditorMode) {
                successMsg.addAction("layout_preview", "Layout Preview", () -> handlePreviewAction());
            }

            // Add Undo Action
            if (SdbSnapshotManager.canUndo(sc_id)) {
                successMsg.addAction("undo_ia", s("Desfazer IA", "Undo AI"), () -> handleUndoAction());
            }

            successMsg.addAction("apply_report", s("Ver Relatorio", "View Report"),
                    this::showLastApplyReport);

            addMessage(successMsg, true);
            if (editListener != null) editListener.onEditApplied();
            if (freeBuildTurnArmed || freeBuildLoopActive) {
                freeBuildLoopActive = true;
                startFreeModeBuild();
            }
        } else {
             if (operationPolicy == null && tryAutoRepair(effectiveJson, null)) {
                 return;
             }
             addMessage(new SdbAgenteActivity.ChatMessage(s("❌ **Falha ao aplicar as edições.**\nVerifique se o JSON é válido.", "❌ **Failed to apply edits.**\nCheck if the JSON is valid."), false), true);
             resetFreeBuildLoop(true);
        }
    }

    private static String inferJavaContext(String errorText, String scId) {
        if (errorText == null) return null;
        String bindingXml = bindingXmlFromLog(errorText);
        if (bindingXml != null && scId != null) {
            try {
                for (ProjectFileBean file : jC.b(scId).b()) {
                    if (file != null && bindingXml.equalsIgnoreCase(file.getXmlName())) {
                        return file.getJavaName();
                    }
                }
            } catch (Exception ignored) {}
        }
        Matcher matcher = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*\\.java)").matcher(errorText);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String inferXmlContext(String errorText, String scId, String javaName) {
        String bindingXml = bindingXmlFromLog(errorText);
        if (bindingXml != null) return bindingXml;
        if (scId == null || javaName == null) return null;
        try {
            for (ProjectFileBean file : jC.b(scId).b()) {
                if (file != null && javaName.equalsIgnoreCase(file.getJavaName())) {
                    return file.getXmlName();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String bindingXmlFromLog(String errorText) {
        if (errorText == null) return null;
        Matcher matcher = Pattern.compile(
                "[/\\\\]databinding[/\\\\]([A-Za-z0-9_$]+)Binding\\.java")
                .matcher(errorText);
        if (!matcher.find()) return null;
        return matcher.group(1).replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(java.util.Locale.US) + ".xml";
    }

    private ArrayList<String> collectAffectedXmlNames(String json) {
        ArrayList<String> names = new ArrayList<>();
        try {
            org.json.JSONArray operations;
            String trimmed = json == null ? "" : json.trim();
            if (trimmed.startsWith("[")) {
                operations = new org.json.JSONArray(trimmed);
            } else {
                org.json.JSONObject root = new org.json.JSONObject(trimmed);
                operations = root.has("operations")
                        ? root.optJSONArray("operations")
                        : new org.json.JSONArray().put(root);
            }
            if (operations == null) return names;
            for (int i = 0; i < operations.length(); i++) {
                org.json.JSONObject op = operations.optJSONObject(i);
                if (op == null) continue;
                String name = op.optString("xmlName", "");
                org.json.JSONObject data = op.optJSONObject("data");
                if (name.isEmpty() && data != null) name = data.optString("target_xml_name", "");
                if (!name.isEmpty()) {
                    if (!name.endsWith(".xml")) name += ".xml";
                    if (!names.contains(name)) names.add(name);
                }
            }
        } catch (Exception ignored) {}
        return names;
    }

    private boolean tryAutoRepair(String failedJson, java.util.Set<String> operationPolicy) {
        if (autoRepairAttempts >= MAX_AUTO_REPAIR_ATTEMPTS || agente == null || getActivity() == null) return false;
        String failedCanonical = canonicalJson(failedJson);
        if (failedCanonical.equals(lastAutoRepairJson)) return false;
        String error = SdbEditEngine.getLastApplyError();
        String errorKey = error == null ? "" : error.replaceAll("#\\d+", "#").trim();
        if (errorKey.equals(lastAutoRepairError)) {
            repeatedAutoRepairErrors++;
        } else {
            lastAutoRepairError = errorKey;
            repeatedAutoRepairErrors = 1;
        }
        if (repeatedAutoRepairErrors >= 3) {
            addMessage(new SdbAgenteActivity.ChatMessage(s(
                    "A autocorrecao repetiu o mesmo erro estrutural. O ciclo foi interrompido e nenhuma nova edicao sera aplicada.",
                    "Automatic repair repeated the same structural error. The cycle was stopped and no further edit will be applied."), false), true);
            return false;
        }
        lastAutoRepairJson = failedCanonical;
        autoRepairAttempts++;
        String repairPrompt = "A aplicação das alterações falhou. Corrija automaticamente e retorne SOMENTE um JSON válido com operations.\n"
                + "Pedido original do usuário:\n" + lastUserPrompt + "\n\n"
                + "Erro detectado:\n" + (error == null || error.isEmpty() ? "Falha sem detalhe do motor." : error) + "\n\n"
                + "JSON que falhou:\n```json\n" + failedJson + "\n```\n\n"
                + "Regras de correção:\n"
                + "- Se o usuário pediu criar Activity/tela nativa, use create_activity, nunca custom view ou classe Java solta.\n"
                + "- Se tentou editar arquivo sem contexto aberto, use operações de projeto inteiro com java_name/xmlName explícitos ou edição direta por path relativo.\n"
                + "- Se XML foi salvo no lugar errado, separe layout, drawable e Java em operações próprias.\n"
                + "- Para edit_layout_xml, xml_content deve ter UMA ViewGroup raiz (LinearLayout, RelativeLayout etc.), xmlns:android e nenhum texto Markdown. Nunca envie um TextView como raiz.\n"
                + "- Se o erro for `cannot be resolved to a variable`, crie a dependencia ANTES do inject_code: `add_component` para ProgressDialog/Timer/Intent/SharedPreferences/RequestNetwork/etc.; `add_variable` para String/Number/Boolean/Map; `add_list` para listas.\n"
                + "- Se aparecer `R.id.nome cannot be resolved or is not a field`, crie/corrija o widget `nome` no layout da Activity; nao repita findViewById antes do ID existir.\n"
                + "- Variaveis locais de dialogo como `input`, `editText` ou `campo` devem ser declaradas como EditText dentro do mesmo evento/MoreBlock. Nao crie componente Dialog para resolver uma variavel local.\n"
                + "- Para `sp.edit()`, `sp.contains()` ou `sp.getString()`, use add_component SharedPreferences com name `sp` e param1 `config`.\n"
                + "- Para ProgressDialog com nome `pd`, use `add_component` component_type `ProgressDialog`, name `pd`.\n"
                + "- Para TimerTask com nome `t`, use `add_component` component_type `Timer`, name `t`, e agende com `_timer.schedule(t, ...)`, nunca `timer.schedule(...)`.\n"
                + "- Retorne apenas JSON, sem explicação.";

        addMessage(new SdbAgenteActivity.ChatMessage(s("🔁 **Falha detectada. Tentando correção automática...**", "🔁 **Failure detected. Trying automatic repair...**"), false), true);
        setThinking(true);
        String fullContext = SdbProjectContext.getFullProjectContext(sc_id, null);
        agente.askWithHistory(repairPrompt, fullContext,
                trimHistoryForApi(new java.util.ArrayList<>(messages)),
                new SdbAgenteSk.ResponseListener() {
            @Override
            public void onResponse(String response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    setThinking(false);
                    String repairedJson = extractUnifiedJson(response);
                    if (repairedJson == null || repairedJson.trim().isEmpty()) {
                        resetFreeBuildLoop(true);
                        addMessage(new SdbAgenteActivity.ChatMessage(s("❌ A correção automática não retornou JSON aplicável.", "❌ Automatic repair did not return applicable JSON."), false), true);
                        return;
                    }
                    if (canonicalJson(repairedJson).equals(failedCanonical)) {
                        resetFreeBuildLoop(true);
                        addMessage(new SdbAgenteActivity.ChatMessage(s(
                                "A correção automática repetiu o mesmo JSON inválido. O ciclo foi interrompido para preservar o projeto.",
                                "Automatic repair repeated the same invalid JSON. The cycle was stopped to preserve the project."), false), true);
                        return;
                    }
                    applyResponseJson(repairedJson, operationPolicy);
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    setThinking(false);
                    resetFreeBuildLoop(true);
                    addMessage(new SdbAgenteActivity.ChatMessage(s("❌ Correção automática falhou: ", "❌ Automatic repair failed: ") + error, false), true);
                });
            }
        });
        return true;
    }

    private String operationFingerprint(String json) {
        String value = sc_id + "\n" + canonicalJson(json);
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : bytes) hex.append(String.format(java.util.Locale.US, "%02x", item));
            return hex.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private boolean beginOperationAttempt(String fingerprint) {
        if (getContext() == null) return true;
        android.content.SharedPreferences prefs = getContext().getSharedPreferences(
                "gc_ai_operation_guard", android.content.Context.MODE_PRIVATE);
        String key = sc_id == null ? "global" : sc_id;
        String previous = prefs.getString(key + "_fingerprint", "");
        long timestamp = prefs.getLong(key + "_timestamp", 0L);
        String state = prefs.getString(key + "_state", "");
        boolean recent = System.currentTimeMillis() - timestamp < 2 * 60 * 1000L;
        if (recent && fingerprint.equals(previous)
                && ("applying".equals(state) || "complete".equals(state) || "failed".equals(state))) {
            return false;
        }
        prefs.edit().putString(key + "_fingerprint", fingerprint)
                .putString(key + "_state", "applying")
                .putLong(key + "_timestamp", System.currentTimeMillis()).apply();
        return true;
    }

    private void finishOperationAttempt(String fingerprint, String state) {
        if (getContext() == null) return;
        String key = sc_id == null ? "global" : sc_id;
        getContext().getSharedPreferences("gc_ai_operation_guard", android.content.Context.MODE_PRIVATE)
                .edit().putString(key + "_fingerprint", fingerprint)
                .putString(key + "_state", state)
                .putLong(key + "_timestamp", System.currentTimeMillis()).apply();
    }

    private String canonicalJson(String json) {
        if (json == null) return "";
        String trimmed = json.trim();
        try {
            return trimmed.startsWith("[")
                    ? new org.json.JSONArray(trimmed).toString()
                    : new org.json.JSONObject(trimmed).toString();
        } catch (Exception ignored) {
            return trimmed.replaceAll("\\s+", "");
        }
    }

    private String extractUnifiedJson(String rawResponse) {
        try {
            java.util.List<String> jsonBlocks = extractJsonBlocks(rawResponse);
            if (jsonBlocks.isEmpty()) return null;
            if (jsonBlocks.size() == 1) return jsonBlocks.get(0);
            org.json.JSONObject unified = new org.json.JSONObject();
            org.json.JSONArray ops = new org.json.JSONArray();
            for (String block : jsonBlocks) {
                try {
                    org.json.JSONObject b = new org.json.JSONObject(block);
                    if (b.has("operations")) {
                        org.json.JSONArray bOps = b.getJSONArray("operations");
                        for (int i = 0; i < bOps.length(); i++) ops.put(bOps.get(i));
                    } else if (b.has("op")) {
                        ops.put(b);
                    }
                } catch (Exception ignored) {}
            }
            unified.put("operations", ops);
            return unified.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private java.util.List<SdbAgenteActivity.ChatMessage> trimHistoryForApi(
            java.util.List<SdbAgenteActivity.ChatMessage> source) {
        java.util.LinkedList<SdbAgenteActivity.ChatMessage> kept = new java.util.LinkedList<>();
        int chars = 0;
        for (int i = source.size() - 1; i >= 0 && kept.size() < MAX_HISTORY_MESSAGES; i--) {
            SdbAgenteActivity.ChatMessage message = source.get(i);
            if (message == null || message.isAd || message.text == null
                    || message.text.trim().isEmpty()) continue;
            int nextChars = message.text.length();
            if (!kept.isEmpty() && chars + nextChars > MAX_HISTORY_CHARS) break;
            kept.addFirst(message);
            chars += nextChars;
        }
        return new java.util.ArrayList<>(kept);
    }

    private boolean isPlanMode() {
        return chipGroupIntent != null && chipGroupIntent.getCheckedChipId() == R.id.intent_plan;
    }

    private boolean isFreeMode() {
        return chipGroupIntent != null && chipGroupIntent.getCheckedChipId() == R.id.intent_free;
    }

    private boolean isAgentMode() {
        return chipGroupIntent != null && chipGroupIntent.getCheckedChipId() == R.id.intent_inject;
    }

    private boolean isAffirmative(String text) {
        if (text == null) return false;
        String value = java.text.Normalizer.normalize(text.trim().toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return value.equals("sim") || value.equals("s") || value.equals("ok")
                || value.equals("pode") || value.equals("pode aplicar")
                || value.equals("aplica") || value.equals("aplicar")
                || value.equals("vai") || value.equals("faz")
                || value.equals("faz isso") || value.equals("yes") || value.equals("y");
    }

    private void showCodePreviewDialog(String json) {
        String displayCode = json;
        try {
            // Try to make it pretty and extract real code if it's a simple direct_code op
            org.json.JSONObject obj = new org.json.JSONObject(json);
            if (obj.has("op") && "add_direct_code".equals(obj.getString("op"))) {
                displayCode = obj.getJSONObject("data").getString("code");
            } else if (obj.has("operations")) {
                displayCode = obj.toString(2); // Pretty print JSON
            }
        } catch (Exception e) {
            // Fallback to raw string
        }

        String operationDiff = buildOperationDiff(json);
        if (!operationDiff.isEmpty()) displayCode = operationDiff + "\n\n" + displayCode;

        View dialogView = LayoutInflater.from(getContext()).inflate(pro.sketchware.R.layout.compile_log, null);
        TextView logText = dialogView.findViewById(pro.sketchware.R.id.tv_compile_log);
        logText.setText(displayCode);
        logText.setTextSize(12);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
            .setTitle(s("Preview do Código/JSON", "Code/JSON Preview"))
            .setView(dialogView)
            .setPositiveButton(s("Fechar", "Close"), null)
            .setNeutralButton(s("Copiar", "Copy"), (d, w) -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("GC-AI Preview", logText.getText());
                clipboard.setPrimaryClip(clip);
                SketchwareUtil.toast(s("Copiado!", "Copied!"));
            })
            .show();
    }

    private String buildOperationDiff(String json) {
        StringBuilder diff = new StringBuilder(s("Mudancas propostas:", "Proposed changes:"));
        int count = 0;
        try {
            org.json.JSONArray operations;
            String trimmed = json == null ? "" : json.trim();
            if (trimmed.startsWith("[")) operations = new org.json.JSONArray(trimmed);
            else {
                org.json.JSONObject root = new org.json.JSONObject(trimmed);
                operations = root.optJSONArray("operations");
                if (operations == null) operations = new org.json.JSONArray().put(root);
            }
            for (int i = 0; i < operations.length(); i++) {
                org.json.JSONObject operation = operations.optJSONObject(i);
                if (operation == null) continue;
                String op = operation.optString("op", "operacao");
                org.json.JSONObject data = operation.optJSONObject("data");
                String target = operation.optString("xmlName", "");
                if (data != null) {
                    if (target.isEmpty()) target = data.optString("target_xml_name", "");
                    String id = data.optString("widget_id", data.optString("id", ""));
                    if (!id.isEmpty()) target += (target.isEmpty() ? "" : " / ") + id;
                    if (target.isEmpty()) target = data.optString("path", data.optString("java_name", ""));
                }
                String marker = op.startsWith("add_") || op.startsWith("create_") ? "+"
                        : op.startsWith("remove_") || op.startsWith("delete_") ? "-" : "~";
                diff.append("\n").append(marker).append(" ").append(op);
                if (!target.isEmpty()) diff.append(" -> ").append(target);
                count++;
            }
        } catch (Exception ignored) {}
        return count == 0 ? "" : diff.toString();
    }

    private void showLastApplyReport() {
        if (getContext() == null) return;
        SdbEditEngine.ApplyReport report = SdbEditEngine.getLastApplyReport();
        StringBuilder reportText = new StringBuilder(report == null
                ? s("Nenhum relatorio disponivel.", "No report available.")
                : report.toUserSummary());
        ArrayList<String> history = SdbSnapshotManager.history(sc_id);
        if (!history.isEmpty()) {
            reportText.append("\n\n").append(s("Historico CodFlow:", "CodFlow history:"));
            for (String entry : history) reportText.append("\n- ").append(entry);
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                .setTitle(s("Relatorio da edicao", "Edit report"))
                .setMessage(reportText.toString())
                .setPositiveButton(s("Fechar", "Close"), null)
                .show();
    }

    public void setOnAgenteEditListener(OnAgenteEditListener listener) {
        this.editListener = listener;
    }

    private void addMessage(SdbAgenteActivity.ChatMessage msg, boolean save) {
        messages.add(msg);

        // Inserir banner no chat a cada 5 mensagens da IA
        if (!msg.isUser && messages.size() % 5 == 2) {
            messages.add(new SdbAgenteActivity.ChatMessage(true));
        }

        if (adapter != null) {
            adapter.notifyItemInserted(messages.size() - 1);
            chatRecycler.scrollToPosition(messages.size() - 1);
        }
        if (save) saveMessages();
    }

    private void showApiKeyDialog() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);
        scrollView.addView(container);

        // ===== Provider Selection =====
        TextView labelProvider = new TextView(getContext());
        labelProvider.setText(s("Provedor de IA:", "AI Provider:"));
        labelProvider.setTextSize(16);
        labelProvider.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(labelProvider);

        com.google.android.material.chip.ChipGroup providerChips = new com.google.android.material.chip.ChipGroup(getContext());
        providerChips.setSingleSelection(true);
        providerChips.setSelectionRequired(true);

        com.google.android.material.chip.Chip chipGemini = new com.google.android.material.chip.Chip(getContext());
        chipGemini.setText("Google Gemini");
        chipGemini.setCheckable(true);

        com.google.android.material.chip.Chip chipOpenRouter = new com.google.android.material.chip.Chip(getContext());
        chipOpenRouter.setText(s("OpenRouter (IAs Grátis)", "OpenRouter (Free AIs)"));
        chipOpenRouter.setCheckable(true);

        com.google.android.material.chip.Chip chipClaude = new com.google.android.material.chip.Chip(getContext());
        chipClaude.setText("Claude (Anthropic)");
        chipClaude.setCheckable(true);

        com.google.android.material.chip.Chip chipOpenAI = new com.google.android.material.chip.Chip(getContext());
        chipOpenAI.setText("GPT / OpenAI");
        chipOpenAI.setCheckable(true);

        com.google.android.material.chip.Chip chipNvidia = new com.google.android.material.chip.Chip(getContext());
        chipNvidia.setText("NVIDIA NIM");
        chipNvidia.setCheckable(true);

        com.google.android.material.chip.Chip chipDeepSeek = new com.google.android.material.chip.Chip(getContext());
        chipDeepSeek.setText("DeepSeek");
        chipDeepSeek.setCheckable(true);

        chipGemini.setId(View.generateViewId());
        chipOpenRouter.setId(View.generateViewId());
        chipClaude.setId(View.generateViewId());
        chipOpenAI.setId(View.generateViewId());
        chipNvidia.setId(View.generateViewId());
        chipDeepSeek.setId(View.generateViewId());

        providerChips.addView(chipGemini);
        providerChips.addView(chipOpenRouter);
        providerChips.addView(chipClaude);
        providerChips.addView(chipOpenAI);
        providerChips.addView(chipNvidia);
        providerChips.addView(chipDeepSeek);
        container.addView(providerChips);

        // ===== Gemini Section =====
        LinearLayout geminiSection = new LinearLayout(getContext());
        geminiSection.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnGetLink = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnGetLink.setText(s("Obter Chave Grátis (AI Studio)", "Get Free Key (AI Studio)"));
        btnGetLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey"));
            startActivity(intent);
        });
        geminiSection.addView(btnGetLink);

        final EditText inputKey = new EditText(getContext());
        inputKey.setText(agente.getApiKey());
        inputKey.setHint("API Key (ex: AIza...)");

        TextView labelKey = new TextView(getContext());
        labelKey.setText("Google Gemini API Key:");
        geminiSection.addView(labelKey);
        geminiSection.addView(inputKey);

        TextView labelModel = new TextView(getContext());
        labelModel.setText(s("\nModelo Gemini:", "\nGemini Model:"));
        geminiSection.addView(labelModel);

        final EditText inputGeminiModel = new EditText(getContext());
        inputGeminiModel.setText(agente.getChatModel());
        inputGeminiModel.setHint(s("Modelo (ex: gemini-2.5-flash)", "Model (e.g. gemini-2.5-flash)"));

        android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(getContext());
        hScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScroll.setHorizontalScrollBarEnabled(false);
        com.google.android.material.chip.ChipGroup geminiChips = new com.google.android.material.chip.ChipGroup(getContext());
        geminiChips.setSingleSelection(false);
        for (String m : SdbAgenteSk.GEMINI_MODELS) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
            chip.setText(m);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> inputGeminiModel.setText(m));
            geminiChips.addView(chip);
        }
        hScroll.addView(geminiChips);
        geminiSection.addView(hScroll);
        geminiSection.addView(inputGeminiModel);
        container.addView(geminiSection);

        // ===== OpenRouter Section =====
        LinearLayout openRouterSection = new LinearLayout(getContext());
        openRouterSection.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnGetOR = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnGetOR.setText(s("Obter Chave (openrouter.ai)", "Get Key (openrouter.ai)"));
        btnGetOR.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://openrouter.ai/keys"));
            startActivity(intent);
        });
        openRouterSection.addView(btnGetOR);

        final EditText inputORKey = new EditText(getContext());
        inputORKey.setText(agente.getOpenRouterKey());
        inputORKey.setHint("OpenRouter Key (ex: sk-or-...)");

        TextView labelORKey = new TextView(getContext());
        labelORKey.setText("OpenRouter API Key:");
        openRouterSection.addView(labelORKey);
        openRouterSection.addView(inputORKey);

        TextView labelORModel = new TextView(getContext());
        labelORModel.setText(s("\nModelos Grátis:", "\nFree Models:"));
        openRouterSection.addView(labelORModel);

        final EditText inputORModel = new EditText(getContext());
        inputORModel.setText(agente.getOpenRouterModel());
        inputORModel.setHint(s("Modelo OpenRouter", "OpenRouter Model"));

        android.widget.HorizontalScrollView hScrollOR = new android.widget.HorizontalScrollView(getContext());
        hScrollOR.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScrollOR.setHorizontalScrollBarEnabled(false);
        com.google.android.material.chip.ChipGroup orChips = new com.google.android.material.chip.ChipGroup(getContext());
        orChips.setSingleSelection(false);
        for (String m : SdbAgenteSk.OPENROUTER_FREE_MODELS) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
            chip.setText(m.replace(":free", " *"));
            chip.setCheckable(false);
            chip.setOnClickListener(v -> inputORModel.setText(m));
            orChips.addView(chip);
        }
        hScrollOR.addView(orChips);
        openRouterSection.addView(hScrollOR);
        MaterialButton btnLoadOpenRouter = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnLoadOpenRouter.setText(s("Carregar catálogo atualizado", "Load current catalog"));
        btnLoadOpenRouter.setOnClickListener(v -> {
            btnLoadOpenRouter.setEnabled(false);
            btnLoadOpenRouter.setText(s("Carregando modelos...", "Loading models..."));
            agente.fetchOpenRouterModels(true, new SdbAgenteSk.ModelsListener() {
                @Override public void onModels(java.util.List<SdbAgenteSk.ProviderModel> models) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        orChips.removeAllViews();
                        for (SdbAgenteSk.ProviderModel model : models) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
                            chip.setText(model.name + (model.isFree ? " - grátis" : ""));
                            chip.setCheckable(false);
                            chip.setOnClickListener(item -> inputORModel.setText(model.id));
                            orChips.addView(chip);
                        }
                        btnLoadOpenRouter.setEnabled(true);
                        btnLoadOpenRouter.setText(models.size() + s(" modelos grátis", " free models"));
                    });
                }
                @Override public void onError(String error) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        btnLoadOpenRouter.setEnabled(true);
                        btnLoadOpenRouter.setText(s("Tentar carregar novamente", "Retry loading"));
                        SketchwareUtil.toastError(error);
                    });
                }
            });
        });
        openRouterSection.addView(btnLoadOpenRouter);
        openRouterSection.addView(inputORModel);
        container.addView(openRouterSection);

        // ===== Claude Section =====
        LinearLayout claudeSection = new LinearLayout(getContext());
        claudeSection.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnGetClaude = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnGetClaude.setText(s("Obter Chave (console.anthropic.com)", "Get Key (console.anthropic.com)"));
        btnGetClaude.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://console.anthropic.com/settings/keys"));
            startActivity(intent);
        });
        claudeSection.addView(btnGetClaude);

        final EditText inputClaudeKey = new EditText(getContext());
        inputClaudeKey.setText(agente.getClaudeKey());
        inputClaudeKey.setHint("Claude Key (ex: sk-ant-...)");

        TextView labelClaudeKey = new TextView(getContext());
        labelClaudeKey.setText("Claude API Key:");
        claudeSection.addView(labelClaudeKey);
        claudeSection.addView(inputClaudeKey);

        TextView labelClaudeModel = new TextView(getContext());
        labelClaudeModel.setText(s("\nModelo Claude:", "\nClaude Model:"));
        claudeSection.addView(labelClaudeModel);

        final EditText inputClaudeModel = new EditText(getContext());
        inputClaudeModel.setText(agente.getClaudeModel());
        inputClaudeModel.setHint(s("Modelo (ex: claude-sonnet-4-20250514)", "Model (e.g. claude-sonnet-4-20250514)"));

        android.widget.HorizontalScrollView hScrollClaude = new android.widget.HorizontalScrollView(getContext());
        hScrollClaude.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScrollClaude.setHorizontalScrollBarEnabled(false);
        com.google.android.material.chip.ChipGroup claudeChips = new com.google.android.material.chip.ChipGroup(getContext());
        claudeChips.setSingleSelection(false);
        for (String m : SdbAgenteSk.CLAUDE_MODELS) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
            chip.setText(m);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> inputClaudeModel.setText(m));
            claudeChips.addView(chip);
        }
        hScrollClaude.addView(claudeChips);
        claudeSection.addView(hScrollClaude);
        claudeSection.addView(inputClaudeModel);
        container.addView(claudeSection);

        // ===== OpenAI / GPT Section =====
        LinearLayout openAISection = new LinearLayout(getContext());
        openAISection.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnGetOpenAI = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnGetOpenAI.setText(s("Obter Chave (platform.openai.com)", "Get Key (platform.openai.com)"));
        btnGetOpenAI.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://platform.openai.com/api-keys"));
            startActivity(intent);
        });
        openAISection.addView(btnGetOpenAI);

        final EditText inputOpenAIKey = new EditText(getContext());
        inputOpenAIKey.setText(agente.getOpenAIKey());
        inputOpenAIKey.setHint("OpenAI Key (ex: sk-...)");

        TextView labelOpenAIKey = new TextView(getContext());
        labelOpenAIKey.setText("OpenAI API Key:");
        openAISection.addView(labelOpenAIKey);
        openAISection.addView(inputOpenAIKey);

        TextView labelOpenAIModel = new TextView(getContext());
        labelOpenAIModel.setText(s("\nModelo GPT:", "\nGPT Model:"));
        openAISection.addView(labelOpenAIModel);

        final EditText inputOpenAIModel = new EditText(getContext());
        inputOpenAIModel.setText(agente.getOpenAIModel());
        inputOpenAIModel.setHint("gpt-4o-mini");

        android.widget.HorizontalScrollView hScrollOpenAI = new android.widget.HorizontalScrollView(getContext());
        hScrollOpenAI.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScrollOpenAI.setHorizontalScrollBarEnabled(false);
        com.google.android.material.chip.ChipGroup openAIChips = new com.google.android.material.chip.ChipGroup(getContext());
        openAIChips.setSingleSelection(false);
        for (String m : SdbAgenteSk.OPENAI_MODELS) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
            chip.setText(m);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> inputOpenAIModel.setText(m));
            openAIChips.addView(chip);
        }
        hScrollOpenAI.addView(openAIChips);
        openAISection.addView(hScrollOpenAI);
        openAISection.addView(inputOpenAIModel);
        container.addView(openAISection);

        // ===== NVIDIA Section =====
        LinearLayout nvidiaSection = new LinearLayout(getContext());
        nvidiaSection.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnGetNvidia = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnGetNvidia.setText(s("Obter Chave (build.nvidia.com)", "Get Key (build.nvidia.com)"));
        btnGetNvidia.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://build.nvidia.com/"));
            startActivity(intent);
        });
        nvidiaSection.addView(btnGetNvidia);

        final EditText inputNvidiaKey = new EditText(getContext());
        inputNvidiaKey.setText(agente.getNvidiaKey());
        inputNvidiaKey.setHint("NVIDIA API Key");

        TextView labelNvidiaKey = new TextView(getContext());
        labelNvidiaKey.setText("NVIDIA API Key:");
        nvidiaSection.addView(labelNvidiaKey);
        nvidiaSection.addView(inputNvidiaKey);

        TextView labelNvidiaModel = new TextView(getContext());
        labelNvidiaModel.setText(s("\nModelo NVIDIA:", "\nNVIDIA Model:"));
        nvidiaSection.addView(labelNvidiaModel);

        final EditText inputNvidiaModel = new EditText(getContext());
        inputNvidiaModel.setText(agente.getNvidiaModel());
        inputNvidiaModel.setHint("meta/llama-3.1-70b-instruct");

        android.widget.HorizontalScrollView hScrollNvidia = new android.widget.HorizontalScrollView(getContext());
        hScrollNvidia.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScrollNvidia.setHorizontalScrollBarEnabled(false);
        com.google.android.material.chip.ChipGroup nvidiaChips = new com.google.android.material.chip.ChipGroup(getContext());
        nvidiaChips.setSingleSelection(false);
        for (String m : SdbAgenteSk.NVIDIA_MODELS) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
            chip.setText(m);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> inputNvidiaModel.setText(m));
            nvidiaChips.addView(chip);
        }
        hScrollNvidia.addView(nvidiaChips);
        nvidiaSection.addView(hScrollNvidia);
        MaterialButton btnLoadNvidia = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnLoadNvidia.setText(s("Carregar modelos NVIDIA", "Load NVIDIA models"));
        btnLoadNvidia.setOnClickListener(v -> {
            btnLoadNvidia.setEnabled(false);
            btnLoadNvidia.setText(s("Carregando modelos...", "Loading models..."));
            agente.fetchNvidiaModels(true, new SdbAgenteSk.ModelsListener() {
                @Override public void onModels(java.util.List<SdbAgenteSk.ProviderModel> models) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        nvidiaChips.removeAllViews();
                        for (SdbAgenteSk.ProviderModel model : models) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
                            chip.setText(model.name);
                            chip.setCheckable(false);
                            chip.setOnClickListener(item -> inputNvidiaModel.setText(model.id));
                            nvidiaChips.addView(chip);
                        }
                        btnLoadNvidia.setEnabled(true);
                        btnLoadNvidia.setText(models.size() + s(" modelos", " models"));
                    });
                }
                @Override public void onError(String error) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        btnLoadNvidia.setEnabled(true);
                        btnLoadNvidia.setText(s("Tentar carregar novamente", "Retry loading"));
                        SketchwareUtil.toastError(error);
                    });
                }
            });
        });
        nvidiaSection.addView(btnLoadNvidia);
        nvidiaSection.addView(inputNvidiaModel);
        container.addView(nvidiaSection);

        // ===== DeepSeek Section =====
        LinearLayout deepSeekSection = new LinearLayout(getContext());
        deepSeekSection.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnGetDeepSeek = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnGetDeepSeek.setText(s("Obter Chave (platform.deepseek.com)", "Get Key (platform.deepseek.com)"));
        btnGetDeepSeek.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://platform.deepseek.com/api_keys"))));
        deepSeekSection.addView(btnGetDeepSeek);

        final EditText inputDeepSeekKey = new EditText(getContext());
        inputDeepSeekKey.setText(agente.getDeepSeekKey());
        inputDeepSeekKey.setHint("DeepSeek API Key");
        deepSeekSection.addView(inputDeepSeekKey);

        final EditText inputDeepSeekModel = new EditText(getContext());
        inputDeepSeekModel.setText(agente.getDeepSeekModel());
        inputDeepSeekModel.setHint("deepseek-v4-flash");
        deepSeekSection.addView(inputDeepSeekModel);

        com.google.android.material.chip.ChipGroup deepSeekChips = new com.google.android.material.chip.ChipGroup(getContext());
        for (String m : SdbAgenteSk.DEEPSEEK_MODELS) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
            chip.setText(m);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> inputDeepSeekModel.setText(m));
            deepSeekChips.addView(chip);
        }
        deepSeekSection.addView(deepSeekChips);
        container.addView(deepSeekSection);

        // ===== Toggle visibility =====
        String currentProvider = agente.getProvider();
        boolean isOpenRouter = SdbAgenteSk.PROVIDER_OPENROUTER.equals(currentProvider);
        boolean isClaude = SdbAgenteSk.PROVIDER_CLAUDE.equals(currentProvider);
        boolean isOpenAI = SdbAgenteSk.PROVIDER_OPENAI.equals(currentProvider);
        boolean isNvidia = SdbAgenteSk.PROVIDER_NVIDIA.equals(currentProvider);
        boolean isDeepSeek = SdbAgenteSk.PROVIDER_DEEPSEEK.equals(currentProvider);
        chipGemini.setChecked(!isOpenRouter && !isClaude && !isOpenAI && !isNvidia && !isDeepSeek);
        chipOpenRouter.setChecked(isOpenRouter);
        chipClaude.setChecked(isClaude);
        chipOpenAI.setChecked(isOpenAI);
        chipNvidia.setChecked(isNvidia);
        chipDeepSeek.setChecked(isDeepSeek);
        geminiSection.setVisibility(!isOpenRouter && !isClaude && !isOpenAI && !isNvidia && !isDeepSeek ? View.VISIBLE : View.GONE);
        openRouterSection.setVisibility(isOpenRouter ? View.VISIBLE : View.GONE);
        claudeSection.setVisibility(isClaude ? View.VISIBLE : View.GONE);
        openAISection.setVisibility(isOpenAI ? View.VISIBLE : View.GONE);
        nvidiaSection.setVisibility(isNvidia ? View.VISIBLE : View.GONE);
        deepSeekSection.setVisibility(isDeepSeek ? View.VISIBLE : View.GONE);

        providerChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean orSelected = checkedIds.contains(chipOpenRouter.getId());
            boolean clSelected = checkedIds.contains(chipClaude.getId());
            boolean aiSelected = checkedIds.contains(chipOpenAI.getId());
            boolean nvSelected = checkedIds.contains(chipNvidia.getId());
            boolean dsSelected = checkedIds.contains(chipDeepSeek.getId());
            geminiSection.setVisibility(!orSelected && !clSelected && !aiSelected && !nvSelected && !dsSelected ? View.VISIBLE : View.GONE);
            openRouterSection.setVisibility(orSelected ? View.VISIBLE : View.GONE);
            claudeSection.setVisibility(clSelected ? View.VISIBLE : View.GONE);
            openAISection.setVisibility(aiSelected ? View.VISIBLE : View.GONE);
            nvidiaSection.setVisibility(nvSelected ? View.VISIBLE : View.GONE);
            deepSeekSection.setVisibility(dsSelected ? View.VISIBLE : View.GONE);
        });

        new MaterialAlertDialogBuilder(getContext())
            .setTitle(s("Configuração do GC-AI", "GC-AI Settings"))
            .setView(scrollView)
            .setPositiveButton(s("Salvar", "Save"), (d, w) -> {
                if (chipClaude.isChecked()) {
                    agente.setProvider(SdbAgenteSk.PROVIDER_CLAUDE);
                    agente.setClaudeKey(inputClaudeKey.getText().toString().trim());
                    agente.setClaudeModel(inputClaudeModel.getText().toString().trim());
                    SketchwareUtil.toast("Claude: " + inputClaudeModel.getText().toString().trim());
                } else if (chipOpenAI.isChecked()) {
                    agente.setProvider(SdbAgenteSk.PROVIDER_OPENAI);
                    agente.setOpenAIKey(inputOpenAIKey.getText().toString().trim());
                    agente.setOpenAIModel(inputOpenAIModel.getText().toString().trim());
                    SketchwareUtil.toast("OpenAI: " + inputOpenAIModel.getText().toString().trim());
                } else if (chipNvidia.isChecked()) {
                    agente.setProvider(SdbAgenteSk.PROVIDER_NVIDIA);
                    agente.setNvidiaKey(inputNvidiaKey.getText().toString().trim());
                    agente.setNvidiaModel(inputNvidiaModel.getText().toString().trim());
                    SketchwareUtil.toast("NVIDIA: " + inputNvidiaModel.getText().toString().trim());
                } else if (chipDeepSeek.isChecked()) {
                    agente.setProvider(SdbAgenteSk.PROVIDER_DEEPSEEK);
                    agente.setDeepSeekKey(inputDeepSeekKey.getText().toString().trim());
                    agente.setDeepSeekModel(inputDeepSeekModel.getText().toString().trim());
                    SketchwareUtil.toast("DeepSeek: " + inputDeepSeekModel.getText().toString().trim());
                } else if (chipOpenRouter.isChecked()) {
                    agente.setProvider(SdbAgenteSk.PROVIDER_OPENROUTER);
                    agente.setOpenRouterKey(inputORKey.getText().toString().trim());
                    agente.setOpenRouterModel(inputORModel.getText().toString().trim());
                    SketchwareUtil.toast("OpenRouter: " + inputORModel.getText().toString().trim());
                } else {
                    agente.setProvider(SdbAgenteSk.PROVIDER_GEMINI);
                    agente.setApiKey(inputKey.getText().toString().trim());
                    agente.setChatModel(inputGeminiModel.getText().toString().trim());
                    SketchwareUtil.toast("Gemini: " + inputGeminiModel.getText().toString().trim());
                }
            })
            .setNeutralButton(s("❤️ Apoiar Desenvolvedor", "❤️ Support Developer"), (d, w) -> {
                showInterstitial();
            })
            .setNegativeButton(s("Cancelar", "Cancel"), null)
            .show();
    }

    private void showInterstitial() {
        if (!UnityAds.isInitialized()) {
            SketchwareUtil.toastError(s("Anúncios ainda não inicializados.", "Ads not yet initialized."));
            return;
        }
        
        SketchwareUtil.toast(s("Carregando anúncio de apoio...", "Loading support ad..."));
        UnityAds.load("Interstitial_Android", new IUnityAdsLoadListener() {
            @Override public void onUnityAdsAdLoaded(String placementId) {
                UnityAds.show(getActivity(), placementId, new com.unity3d.ads.UnityAdsShowOptions(), new IUnityAdsShowListener() {
                    @Override public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                        SketchwareUtil.toastError(s("Erro ao exibir: ", "Error displaying: ") + message);
                        showMrecDialog(); 
                    }
                    @Override public void onUnityAdsShowStart(String placementId) {}
                    @Override public void onUnityAdsShowClick(String placementId) {}
                    @Override public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                        SketchwareUtil.toast(s("Obrigado pelo apoio! ❤️", "Thanks for the support! ❤️"));
                    }
                });
            }
            @Override public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                android.util.Log.e("UnityAds", "Interstitial failed: " + message);
                showMrecDialog(); 
            }
        });
    }

    private void showMrecDialog() {
        SketchwareUtil.toast(s("Obrigado pela intenção de apoio! ❤️", "Thanks for the support intent! ❤️"));
    }

    /** Returns pt string when language is PT, en string otherwise. */
    private String s(String pt, String en) {
        return "pt".equals(agente != null ? agente.getLanguage() : "pt") ? pt : en;
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {
        private final List<SdbAgenteActivity.ChatMessage> messages;
        public ChatAdapter(List<SdbAgenteActivity.ChatMessage> messages) { this.messages = messages; }

        @Override public int getItemViewType(int position) {
            SdbAgenteActivity.ChatMessage msg = messages.get(position);
            if (msg.isAd) return 2;
            return msg.isUser ? 1 : 0;
        }

        @NonNull @Override 
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v;
            if (viewType == 2) {
                v = new LinearLayout(parent.getContext());
                v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ((LinearLayout)v).setOrientation(LinearLayout.VERTICAL);
                ((LinearLayout)v).setGravity(android.view.Gravity.CENTER);
                v.setPadding(0, 16, 0, 16);
            } else if (viewType == 1) {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sdb_agente_chat_item_user, parent, false);
            } else {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sdb_agente_chat_item_ai, parent, false);
            }
            return new ChatViewHolder(v, viewType);
        }

        @Override public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            SdbAgenteActivity.ChatMessage msg = messages.get(position);
            if (msg.isAd) {
                if (((LinearLayout)holder.itemView).getChildCount() == 0) {
                    BannerView ad = new BannerView(getActivity(), "Banner_Android", new UnityBannerSize(300, 250));
                    ad.setListener(new BannerView.IListener() {
                        @Override public void onBannerLoaded(BannerView b) {}
                        @Override public void onBannerShown(BannerView b) {}
                        @Override public void onBannerClick(BannerView b) {}
                        @Override public void onBannerFailedToLoad(BannerView b, BannerErrorInfo e) {
                            holder.itemView.setVisibility(View.GONE);
                        }
                        @Override public void onBannerLeftApplication(BannerView b) {}
                    });
                    ((LinearLayout)holder.itemView).addView(ad);
                    ad.load();
                }
                return;
            }
            
            io.noties.markwon.Markwon.create(holder.itemView.getContext()).setMarkdown(holder.text, msg.text);
            
            if (msg.base64Image != null && !msg.base64Image.isEmpty()) {
                holder.image.setVisibility(View.VISIBLE);
                byte[] decodedString = android.util.Base64.decode(msg.base64Image, android.util.Base64.DEFAULT);
                Glide.with(holder.itemView.getContext())
                        .asBitmap()
                        .load(decodedString)
                        .into(holder.image);
            } else {
                holder.image.setVisibility(View.GONE);
            }

            if (holder.actionsContainer != null) {
                holder.actionsContainer.removeAllViews();
                if (msg.actions != null && !msg.actions.isEmpty()) {
                    holder.actionsContainer.setVisibility(View.VISIBLE);
                    for (SdbAgenteActivity.ChatAction action : msg.actions) {
                        com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(
                            holder.itemView.getContext(), null, pro.sketchware.R.attr.borderlessButtonStyle);
                        btn.setText(action.text);
                        btn.setAllCaps(false);
                        btn.setOnClickListener(v -> {
                            if (action.runnable != null) action.runnable.run();
                        });
                        
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.setMarginEnd((int)(8 * holder.itemView.getContext().getResources().getDisplayMetrics().density));
                        holder.actionsContainer.addView(btn, lp);
                    }
                } else {
                    holder.actionsContainer.setVisibility(View.GONE);
                }
            }
        }
        @Override public int getItemCount() { return messages.size(); }
    }

    private static class ChatViewHolder extends RecyclerView.ViewHolder {
        public TextView text;
        public android.widget.ImageView image;
        public android.widget.LinearLayout actionsContainer;
        public ChatViewHolder(View v, int type) { 
            super(v); 
            if (type == 2) return;
            text = v.findViewById(pro.sketchware.R.id.chat_text);
            image = v.findViewById(pro.sketchware.R.id.chat_image);
            actionsContainer = v.findViewById(pro.sketchware.R.id.chat_actions_container);
            if (text != null) {
                text.setOnLongClickListener(view -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                            v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("GC-AI", text.getText());
                    clipboard.setPrimaryClip(clip);
                    pro.sketchware.utility.SketchwareUtil.toast("Texto copiado para a área de transferência");
                    return true;
                });
            }
        }
    }

    private static java.util.List<String> extractJsonBlocks(String text) {
        java.util.List<String> blocks = new java.util.ArrayList<>();
        int countBraces = 0;
        int countBrackets = 0;
        int startBraces = -1;
        int startBrackets = -1;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Handle Braces {}
            if (c == '{') {
                if (countBraces == 0) startBraces = i;
                countBraces++;
            } else if (c == '}') {
                countBraces--;
                if (countBraces == 0 && startBraces != -1) {
                    String block = text.substring(startBraces, i + 1);
                    if (isValidJson(block)) {
                        blocks.add(block);
                    }
                    startBraces = -1;
                }
            }
            
            // Handle Brackets []
            if (c == '[') {
                if (countBrackets == 0) startBrackets = i;
                countBrackets++;
            } else if (c == ']') {
                countBrackets--;
                if (countBrackets == 0 && startBrackets != -1) {
                    String block = text.substring(startBrackets, i + 1);
                    if (block.contains("{") && isValidJson(block)) {
                        blocks.add(block);
                    }
                    startBrackets = -1;
                }
            }
        }
        return blocks;
    }

    private static boolean isValidJson(String test) {
        try {
            new org.json.JSONObject(test);
            return true;
        } catch (org.json.JSONException ex) {
            try {
                new org.json.JSONArray(test);
                return true;
            } catch (org.json.JSONException ex1) {
                return false;
            }
        }
    }

    private String formatXml(String xml) {
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

    private void handleSaveAction() {
        android.app.Activity activity = getActivity();
        if (activity == null) return;
        
        try {
            if (activity instanceof com.besome.sketch.design.DesignActivity) {
                ((com.besome.sketch.design.DesignActivity) activity).saveProject();
                addMessage(new SdbAgenteActivity.ChatMessage(s("💾 Projeto salvo!", "💾 Project saved!"), false), false);
            } else if (activity.getClass().getSimpleName().equals("ViewCodeEditorActivity") ||
                       activity.getClass().getSimpleName().equals("CodeViewerActivity")) {
                try {
                    java.lang.reflect.Method saveMethod = activity.getClass().getDeclaredMethod("save");
                    saveMethod.setAccessible(true);
                    saveMethod.invoke(activity);
                    addMessage(new SdbAgenteActivity.ChatMessage(s("💾 Salvo!", "💾 Saved!"), false), false);
                } catch (Exception e) {
                    SketchwareUtil.toastError(s("Erro ao salvar: ", "Error saving: ") + e.getMessage());
                }
            } else if (activity.getClass().getSimpleName().equals("LogicEditorActivity")) {
                try {
                    java.lang.reflect.Method saveAction = activity.getClass().getDeclaredMethod("saveProject");
                    saveAction.setAccessible(true);
                    saveAction.invoke(activity);
                    addMessage(new SdbAgenteActivity.ChatMessage(s("💾 Lógica salva!", "💾 Logic saved!"), false), false);
                } catch (Exception e) {
                    SketchwareUtil.toast(s("Lógica salva (feche para persistir)", "Logic saved (close to persist)"));
                }
            } else {
                persistAgentProject();
            }
        } catch (Exception e) {
            SketchwareUtil.toastError(s("Erro ao salvar: ", "Error saving: ") + e.getMessage());
        }
    }

    private void persistAgentProject() {
        try {
            SdbProjectIntegrityGuard.saveProjectData(sc_id);
            jC.b(sc_id).j();
            jC.d(sc_id).y();
            android.content.Context context = getContext();
            if (context != null) {
                android.content.Intent refresh = new android.content.Intent(
                        SdbEditEngine.ACTION_REFRESH_PROJECT);
                refresh.putExtra("sc_id", sc_id);
                if (contextXmlName != null) refresh.putExtra("xml_name", contextXmlName);
                context.sendBroadcast(refresh);
            }
            addMessage(new SdbAgenteActivity.ChatMessage(
                    s("Projeto salvo e sincronizado.", "Project saved and synchronized."),
                    false), false);
            SketchwareUtil.toast(s("Projeto salvo!", "Project saved!"));
        } catch (Exception error) {
            SketchwareUtil.toastError(s("Erro ao salvar: ", "Error saving: ")
                    + (error.getMessage() == null ? error.toString() : error.getMessage()));
        }
    }

    private void handlePreviewAction() {
        android.app.Activity activity = getActivity();
        if (activity == null || contextXmlName == null) return;
        
        try {
            // First try reflection to call the native method if it exists (e.g. in ViewCodeEditorActivity)
            try {
                java.lang.reflect.Method previewMethod = activity.getClass().getDeclaredMethod("toLayoutPreview");
                previewMethod.setAccessible(true);
                previewMethod.invoke(activity);
                return; // Managed by activity's own logic and current editor state
            } catch (Exception e) {
                // Method not found or failed, fall back to manual intent generation if in Designer
            }

            String baseXml = contextXmlName.endsWith(".xml") ? contextXmlName : contextXmlName + ".xml";
            ArrayList<ViewBean> beans = jC.a(sc_id).d(baseXml);
            ProjectFileBean pfb = jC.b(sc_id).b(baseXml);
            ViewBean fb = jC.a(sc_id).h(baseXml);
            
            String currentXml = "";
            if (beans != null && pfb != null) {
                jq buildConfig = new jq();
                buildConfig.sc_id = sc_id;
                buildConfig.g = true; // Use AppCompat
                Ox xmlGenerator = new Ox(buildConfig, pfb);
                xmlGenerator.a(beans, fb);
                currentXml = xmlGenerator.b();
            }
            
            if (currentXml.isEmpty()) {
                SketchwareUtil.toast(s("Não foi possível gerar o XML para o preview.", "Could not generate XML for preview."));
                return;
            }

            Intent intent = new Intent(activity.getApplicationContext(), LayoutPreviewActivity.class);
            // Replicate toLayoutPreview logic: put original extras and then the XML
            if (activity.getIntent() != null) {
                intent.putExtras(activity.getIntent());
            }
            intent.putExtra("xml", currentXml);
            startActivity(intent);
        } catch (Exception e) {
            SketchwareUtil.toastError(s("Erro ao abrir preview: ", "Error opening preview: ") + e.getMessage());
        }
    }

    private void handleUndoAction() {
        if (sc_id == null) return;

        try {
            if (SdbSnapshotManager.undo(sc_id)) {
                SketchwareUtil.toast(s("Mudanças desfeitas com sucesso!", "Changes undone successfully!"));
                addMessage(new SdbAgenteActivity.ChatMessage(s("⏪ **Mudanças desfeitas!** O estado original foi restaurado.", "⏪ **Changes undone!** Original state restored."), false), true);
                if (editListener != null) editListener.onEditApplied();
            } else {
                SketchwareUtil.toast(s("Não há mais o que desfazer.", "Nothing more to undo."));
            }
        } catch (Exception e) {
            SketchwareUtil.toastError(s("Erro ao desfazer: ", "Error undoing: ") + e.getMessage());
        }
    }
}
