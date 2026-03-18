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

    private String sc_id;
    private SdbAgenteSk agente;
    private RecyclerView chatRecycler;
    private ChatAdapter adapter;
    private TextInputEditText inputText;
    private com.google.android.material.progressindicator.LinearProgressIndicator progressIndicator;
    private List<SdbAgenteActivity.ChatMessage> messages = new ArrayList<>();
    private String currentBase64Image = null;
    private String currentMimeType = null;
    private String currentChatFile;
    private View layoutImagePreview;
    private android.widget.ImageView imgPreview;
    private com.google.android.material.checkbox.MaterialCheckBox cbAutoApply;
    private com.google.android.material.textfield.TextInputLayout inputLayout;
    private ActivityResultLauncher<String> imagePickerLauncher;
    
    private boolean isCodeEditorMode = false;
    private String originalCode = null;
    private OnCodeApplyListener codeApplyListener = null;
    
    private String contextName = null;
    private String contextXmlName = null;
    private String systemInstructionAddon = null;
    private OnApplyListener applyListener = null;
    private OnAgenteEditListener editListener = null;
    private com.google.android.material.chip.ChipGroup chipGroupIntent;
    private BannerView bannerView = null;
    private boolean adFallbackTried = false;

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        chipGroupIntent = view.findViewById(R.id.chip_group_intent);

        adFallbackTried = false;
        view.post(() -> {
            if (UnityAds.isInitialized()) {
                chipGroupIntent.setOnCheckedChangeListener((group, checkedId) -> {
                    if (checkedId == R.id.intent_support) {
                        showInterstitial();
                        group.check(R.id.intent_auto);
                    }
                });
                loadBanner(view);
            }
        });

        // Hide full screen elements if any or adjust for sheet
        view.findViewById(R.id.btn_back).setVisibility(View.GONE); // No back needed in sheet
        
        view.findViewById(R.id.btn_history).setOnClickListener(v -> showHistoryDialog());

        TextView tvTitle = view.findViewById(R.id.tv_title);
        if (tvTitle != null) {
            tvTitle.setSelected(true); // For marquee if needed
        }

        chatRecycler = view.findViewById(R.id.chat_recycler);
        chatRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatAdapter(messages);
        chatRecycler.setAdapter(adapter);

        cbAutoApply = view.findViewById(R.id.cb_auto_apply);

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

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                processImageData(uri);
            }
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
        imgPreview = view.findViewById(R.id.img_preview);
        progressIndicator = view.findViewById(R.id.progress_indicator);
        
        view.findViewById(R.id.btn_send).setOnClickListener(v -> sendMessage());
        view.findViewById(R.id.btn_attach).setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
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
            ((com.google.android.material.button.MaterialButton) v).setText(isPt ? "EN" : "PT");
            SketchwareUtil.toast(isPt ? "Language: English" : "Idioma: Português");
            refreshUiLanguage(view);
        });
        // Set initial lang label
        String lang = agente.getLanguage();
        ((com.google.android.material.button.MaterialButton) view.findViewById(R.id.btn_lang))
            .setText("pt".equals(lang) ? "PT" : "EN");
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
        String title = pt ? "Manual de Uso — SDBCodFlow" : "User Guide — SDBCodFlow";
        String content = pt ?
            "## O que é o SDBCodFlow?\n" +
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
            "**Código Java:** `inject_code` · `add_import`\n" +
            "**MoreBlocks:** `add_moreblock` · `update_moreblock` · `delete_moreblock`\n" +
            "**Drawables:** `add_drawable` · `delete_drawable`\n" +
            "**Blocos de Paleta:** `add_custom_block` · `update_custom_block` · `delete_custom_block` · `delete_palette`\n" +
            "**Design/Widgets:** `edit_layout_xml` · `add_widget` · `update_widget` · `remove_widget`"
            :
            "## What is SDBCodFlow?\n" +
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
            "**Java Code:** `inject_code` · `add_import`\n" +
            "**MoreBlocks:** `add_moreblock` · `update_moreblock` · `delete_moreblock`\n" +
            "**Drawables:** `add_drawable` · `delete_drawable`\n" +
            "**Palette Blocks:** `add_custom_block` · `update_custom_block` · `delete_custom_block` · `delete_palette`\n" +
            "**Design/Widgets:** `edit_layout_xml` · `add_widget` · `update_widget` · `remove_widget`";

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

        view.findViewById(R.id.btn_history).setOnLongClickListener(v -> {
            showApiKeyDialog();
            return true;
        });
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

        // Checkbox
        if (cbAutoApply != null) cbAutoApply.setText(pt ? "Auto-Aplicar Mudanças" : "Auto-Apply Changes");

        // Input hint
        if (inputLayout != null) inputLayout.setHint(pt ? "Sua mensagem..." : "Your message...");

        // Title
        TextView tvTitle = view.findViewById(R.id.tv_title);
        if (tvTitle != null) {
            if (contextName != null) {
                tvTitle.setText("AgenteAI: " + contextName);
            } else {
                tvTitle.setText("AgenteAI: " + (pt ? "Projeto " : "Project ") + sc_id);
            }
        }
    }

    public void setThinking(boolean thinking) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            View sendBtn = getView() != null ? getView().findViewById(R.id.btn_send) : null;
            if (sendBtn != null) {
                sendBtn.setEnabled(!thinking);
                inputText.setEnabled(!thinking);
            }
            if (progressIndicator != null) {
                progressIndicator.setVisibility(thinking ? View.VISIBLE : View.GONE);
            }
        });
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
                loadMessages(fileNames[which]);
            })
            .setPositiveButton(s("Configurar API", "API Settings"), (d, w) -> showApiKeyDialog())
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
        String text = inputText.getText().toString().trim();
        if (text.isEmpty() && currentBase64Image == null) return;

        SdbAgenteActivity.ChatMessage userMsg = new SdbAgenteActivity.ChatMessage(text, true);
        userMsg.base64Image = currentBase64Image;
        userMsg.mimeType = currentMimeType;
        
        addMessage(userMsg, true);
        inputText.setText("");

        final SdbAgenteActivity.ChatMessage thinkingMsg = new SdbAgenteActivity.ChatMessage(s("Aguardando resposta do SDBCodFlow...", "Waiting for SDBCodFlow response..."), false);
        messages.add(thinkingMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        chatRecycler.scrollToPosition(messages.size() - 1);

        // Pass contextName so SdbProjectContext shows full event/block detail only for the
        // current screen — other screens get compact summaries to reduce token usage.
        String contextInfo = SdbProjectContext.getFullProjectContext(sc_id, contextName);
        SdbAgenteSk.ResponseListener listener = new SdbAgenteSk.ResponseListener() {
            @Override
            public void onResponse(String response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    int index = messages.indexOf(thinkingMsg);
                    if (index != -1) {
                        messages.remove(index);
                        adapter.notifyItemRemoved(index);
                    }
                    String rawResponse = response;
                    java.util.List<String> jsonBlocks = extractJsonBlocks(rawResponse);
                    
                    if (!jsonBlocks.isEmpty()) {
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
                            addMessage(new SdbAgenteActivity.ChatMessage(s("⚙️ **Processando alterações...**", "⚙️ **Processing changes...**"), false), true);
                            setThinking(true);
                            
                            new android.os.Handler().postDelayed(() -> {
                                boolean autoApply = cbAutoApply != null && cbAutoApply.isChecked();
                                
                                if (autoApply) {
                                    applyResponseJson(finalJson);
                                } else {
                                    // Manual Apply Mode
                                    SdbAgenteActivity.ChatMessage manualMsg = new SdbAgenteActivity.ChatMessage(s("⚙️ **Alterações prontas.**", "⚙️ **Changes ready.**"), false);

                                    // Add Preview Action
                                    manualMsg.addAction("preview_code", s("Visualizar Código", "Preview Code"), () -> {
                                        showCodePreviewDialog(finalJson);
                                    });

                                    // Add Apply Action
                                    manualMsg.addAction("apply_edits", s("Aplicar Mudanças", "Apply Changes"), () -> {
                                        applyResponseJson(finalJson);
                                    });
                                    
                                    addMessage(manualMsg, true);
                                }
                                setThinking(false);
                            }, 500);
                        } catch (Exception e) {
                            addMessage(new SdbAgenteActivity.ChatMessage(response, false), true);
                            setThinking(false);
                        }
                    } else {
                        SdbAgenteActivity.ChatMessage msg = new SdbAgenteActivity.ChatMessage(response, false);
                        if (applyListener != null) {
                            msg.addAction("apply_context", s("🚀 Aplicar ao Contexto", "🚀 Apply to Context"), () -> {
                                applyResponseJson(response);
                            });
                        }
                        addMessage(msg, true);
                        setThinking(false);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    int index = messages.indexOf(thinkingMsg);
                    if (index != -1) {
                        messages.remove(index);
                        adapter.notifyItemRemoved(index);
                    }
                    SketchwareUtil.toastError(error);
                    addMessage(new SdbAgenteActivity.ChatMessage(s("Desculpe, ocorreu um erro: ", "Sorry, an error occurred: ") + error, false), true);
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
                || m.text.startsWith("Waiting for SDBCodFlow"));

        String contextPrefix = (contextName != null) ? "CONTEXTO ATUAL: " + contextName + "\n" : "";
        String xmlPrefix = (contextXmlName != null) ? "TELA ATUAL (XML): " + contextXmlName + "\n" : "";
        String addon = (systemInstructionAddon != null) ? systemInstructionAddon + "\n" : "";
        
        String designFocus = "";
        if (isCodeEditorMode || (contextName != null && contextName.toLowerCase().contains("xml"))) {
            designFocus = "### REGRAS DE CODIFICAÇÃO (ESTRITAS):\n"
                + "- **SALVAMENTO MANUAL**: Se o prompt indicar que o usuário editou o código manualmente, sua ÚNICA prioridade é sincronizar esse código com o projeto usando `inject_code` ou `edit_layout_xml`.\n"
                + "- **PENSAMENTO LÓGICO ANTES DE AGIR**: Em Android, arquivos de Layout (.xml em /layout/) e arquivos de Drawable (.xml em /drawable/) são arquivos de TIPOS DIFERENTES. Você nunca deve misturá-los.\n"
                + "- **PRECISÃO DO COMANDO `edit_layout_xml`**: Este comando edita a TELA. Ele aceita apenas Widgets (LinearLayout, Button, etc.).\n"
                + "- **PRECISÃO DO COMANDO `add_drawable`**: Este comando cria um RECURSO. Ele aceita apenas definições de desenho (shape, selector, layer-list).\n"
                + "- **JAVA/LOGICA**: Ao lidar com Java no Code Editor, use `inject_code` ou `add_direct_code`. Se o arquivo Java estiver vazio, crie a lógica básica pedida.\n"
                + "- **PROIBIÇÃO TOTAL**: É um erro técnico fatal inserir código de desenho (tags como `<shape>`, `<selector>`, etc.) dentro do `xml_content` de um comando `edit_layout_xml`.\n"
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
            if (checkedId == R.id.intent_inject) {
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

        String sourceOfTruth;
        if (contextName == null) {
            // Modo projeto completo — listar telas reais para a IA usar
            StringBuilder screenList = new StringBuilder();
            try {
                java.util.ArrayList<com.besome.sketch.beans.ProjectFileBean> files = a.a.a.jC.b(sc_id).b();
                if (files != null) {
                    for (com.besome.sketch.beans.ProjectFileBean f : files) {
                        screenList.append("  - java_name: \"").append(f.getJavaName())
                            .append("\" | xmlName: \"").append(f.getXmlName().replace(".xml","")).append("\"\n");
                    }
                }
            } catch (Exception ignored) {}
            sourceOfTruth = "### MODO PROJETO COMPLETO (multi-tela):\n"
                + "- Você tem acesso a TODAS as telas do projeto.\n"
                + "- **TELAS DISPONÍVEIS** (use estes nomes EXATOS nas operações):\n"
                + (screenList.length() > 0 ? screenList.toString() : "  (ver contexto abaixo)\n")
                + "- **OBRIGATÓRIO**: Sempre inclua `\"java_name\"` e `\"xmlName\"` explicitamente em CADA operação.\n"
                + "- **PROIBIDO**: `add_direct_code` (requer evento aberto). Use SEMPRE `inject_code` com `java_name` + `event_name` para lógica Java.\n"
                + "- **LAYOUT**: Use `edit_layout_xml` ou `add_widget` com `\"xmlName\": \"nomeDaTela\"` explícito (sem .xml).\n"
                + "- **MOREBLOCK**: Use `add_moreblock` com `\"data\": { \"java_name\": \"NomeDaTela\", \"name\": ..., \"spec\": ..., \"code\": ... }`.\n\n";
        } else {
            sourceOfTruth = "### FONTE DA VERDADE (INQUESTIONÁVEL):\n"
                + "- **CLASSE JAVA/ATIVIDADE**: `" + contextName + "`\n"
                + "- **LAYOUT XML**: `" + (contextXmlName != null ? contextXmlName : "Desconhecido") + "`\n"
                + "Use EXATAMENTE estes nomes em qualquer operação JSON. Não tente adivinhar ou sugerir outros nomes.\n\n";
        }

        String instruction = sourceOfTruth + contextPrefix + addon + intentInstruction + designFocus + imageInstruction + "Você é o Agente SDBCodFlow do Sketchware Pro.\n"
            + "Modifique o projeto retornando um JSON estrito EM ADIÇÃO à sua resposta em texto amigável.\n\n"
            + "### GUIA DE OPERAÇÕES JSON:\n"
            + "1. **Injetar Lógica Local** `{ \"op\": \"add_direct_code\", \"data\": { \"code\": \"// java;\" } }` (Injeta no evento aberto)\n"
            + "2. **Injeção Global** `{ \"op\": \"inject_code\", \"data\": { \"attributes\": { \"java_name\": \"Home\", \"event_name\": \"onCreate\", \"code\": \"// java\" } } }` (Injeta em qualquer evento/tela)\n"
            + "3. **Adicionar Imports Java** `{ \"op\": \"add_import\", \"data\": { \"java_name\": \"Home\", \"code\": \"import java.util.List;\\nimport java.util.ArrayList;\" } }` (Adiciona imports sem sobrescrever os existentes — use SEMPRE que injetar código que precise de imports)\n"
            + "4. **CRIAR MOREBLOCK**: `{ \"op\": \"add_moreblock\", \"data\": { \"name\": \"meuMbr\", \"spec\": \"meuMbr %s.b\", \"code\": \"// java\" } }` (cria novo; spec: nome + params tipo %s.s=string %d.d=número %b.b=bool)\n"
            + "   **EDITAR MOREBLOCK existente**: `{ \"op\": \"update_moreblock\", \"data\": { \"name\": \"meuMbr\", \"spec\": \"meuMbr %s.s\", \"code\": \"// novo código java\" } }` (atualiza spec E corpo do moreblock existente; se não existir, cria)\n"
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
            + "   - **EDITAR TELA COMPLETA**: `{ \"op\": \"edit_layout_xml\", \"xmlName\": \"main\", \"data\": { \"xml_content\": \"<LinearLayout...>...</LinearLayout>\" } }`\n"
            + "   - **Adicionar widget**: `{ \"op\": \"add_widget\", \"xmlName\": \"main\", \"data\": { \"widget_id\": \"b1\", \"widget_type\": 3, \"parent_id\": \"root\", \"attributes\": { \"text\": \"Ok\" } } }` (Tipos: 0:Linear, 1:Relative, 3:Button, 4:TextView, 8:ImageView...)\n"
            + "   - **Editar widget**: `{ \"op\": \"update_widget\", \"xmlName\": \"main\", \"data\": { \"widget_id\": \"b1\", \"attributes\": { \"android:text\": \"Novo texto\", \"android:background\": \"@drawable/bg_card\" } } }`\n"
            + "   - **Remover widget**: `{ \"op\": \"remove_widget\", \"xmlName\": \"main\", \"data\": { \"widget_id\": \"b1\" } }`\n\n"
            + "### REGRAS DE DESIGN PROFISSIONAL (OBRIGATÓRIO):\n"
            + "- **ImageView**: SEMPRE inclua `android:scaleType=\"fitCenter\"` e `android:adjustViewBounds=\"true\"` em todo ImageView.\n"
            + "- **Drawables como fundos**: Prefira drawables (shapes/gradients) como `android:background=\"@drawable/nome\"` ao invés de cores brutas para design profissional.\n"
            + "- **Bordas arredondadas**: Use `<shape><corners android:radius=\"Xdp\"/></shape>` via `add_drawable` + aplique com `android:background`.\n"
            + "- **Gradiente**: `<gradient android:startColor=\"#X\" android:endColor=\"#Y\" android:angle=\"270\"/>` dentro de `<shape>`.\n"
            + "- **Sombra/Elevação**: Use `android:elevation=\"4dp\"` e `android:stateListAnimator=\"@null\"` em views que precisem de sombra Material.\n"
            + "- **Paleta Material Design**: Prefira cores Material (ex: #6200EE primary, #03DAC6 accent, #FFFFFF surface, #000000 on-surface).\n"
            + "- **Espaçamento**: Use `android:padding` (8dp, 16dp, 24dp) e `android:layout_margin` consistentes para hierarquia visual clara.\n"
            + "- **Imports**: Sempre que criar lógica Java que usa classes externas, inclua as operações `add_import` correspondentes no mesmo array de operações.\n";
        
        // Provide current XML layout for better AI context if in design focus
        if (contextXmlName != null) {
            try {
                String baseXml = contextXmlName.replace(".xml", "");
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
            agente.askWithImage(combinedPrompt, contextInfo, currentBase64Image, currentMimeType, listener);
            currentBase64Image = null;
            currentMimeType = null;
            layoutImagePreview.setVisibility(View.GONE);
        } else {
            agente.askWithHistory(text, contextInfo + "\n\n" + instruction + "\n" + xmlPrefix, historyForApi, listener);
        }
    }

    private void applyResponseJson(String json) {
        // Capture Snapshot before any changes
        ArrayList<String> affectedXmls = new ArrayList<>();
        if (contextXmlName != null) affectedXmls.add(contextXmlName);
        SdbSnapshotManager.takeSnapshot(sc_id, affectedXmls);

        boolean logicInjected = false;
        if (applyListener != null) {
            // Priority: Logic Editor Injection
            applyListener.onApply(json);
            logicInjected = true;
        }

        boolean codeInjected = false;

        // Handle Code Editor Synchronization
        if (codeApplyListener != null) {
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
        
        // Also apply through general engine if applicable (widgets, drawables, etc.)
        boolean engineInjected = SdbEditEngine.applyEdits(sc_id, json, contextXmlName, contextName);
        
        if (logicInjected || engineInjected || codeInjected) {
            SdbAgenteActivity.ChatMessage successMsg = new SdbAgenteActivity.ChatMessage(s("✅ **Edições aplicadas!**", "✅ **Edits applied!**"), false);

            // Add Global Save Action
            successMsg.addAction("save_project", s("Salvar Mudanças", "Save Changes"), () -> handleSaveAction());

            // Add Layout Preview Action only in XML code editor context
            if (isCodeEditorMode) {
                successMsg.addAction("layout_preview", "Layout Preview", () -> handlePreviewAction());
            }

            // Add Undo Action
            if (SdbSnapshotManager.canUndo()) {
                successMsg.addAction("undo_ia", s("Desfazer IA", "Undo AI"), () -> handleUndoAction());
            }

            addMessage(successMsg, true);
            if (editListener != null) editListener.onEditApplied();
        } else {
             addMessage(new SdbAgenteActivity.ChatMessage(s("❌ **Falha ao aplicar as edições.**\nVerifique se o JSON é válido.", "❌ **Failed to apply edits.**\nCheck if the JSON is valid."), false), true);
        }
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
                android.content.ClipData clip = android.content.ClipData.newPlainText("SDBCodFlow Preview", logText.getText());
                clipboard.setPrimaryClip(clip);
                SketchwareUtil.toast(s("Copiado!", "Copied!"));
            })
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
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText inputKey = new EditText(getContext());
        inputKey.setText(agente.getApiKey());
        inputKey.setHint("API Key (ex: AIza...)");
        
        MaterialButton btnGetLink = new MaterialButton(getContext(), null, R.attr.borderlessButtonStyle);
        btnGetLink.setText(s("Obter Chave Grátis (AI Studio)", "Get Free Key (AI Studio)"));
        btnGetLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey"));
            startActivity(intent);
        });
        container.addView(btnGetLink);
        
        final EditText inputModel = new EditText(getContext());
        inputModel.setText(agente.getChatModel());
        inputModel.setHint("Modelo (ex: gemini-2.0-flash)");

        TextView labelKey = new TextView(getContext());
        labelKey.setText("Google Gemini API Key:");
        container.addView(labelKey);
        container.addView(inputKey);

        TextView labelModel = new TextView(getContext());
        labelModel.setText(s("\nEscolha ou Digite o Modelo:", "\nChoose or Type the Model:"));
        container.addView(labelModel);

        // Add HorizontalScrollView with model chips
        android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(getContext());
        hScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScroll.setHorizontalScrollBarEnabled(false);
        
        com.google.android.material.chip.ChipGroup chips = new com.google.android.material.chip.ChipGroup(getContext());
        chips.setSingleSelection(true);
        chips.setSelectionRequired(false);
        
        String[] eliteModels = {
            "gemini-3-flash-preview", 
            "gemini-3.1-flash-lite-preview", 
            "gemini-3.1-pro-preview", 
            "gemini-2.5-flash", 
            "gemini-2.5-pro"
        };
        
        for (String m : eliteModels) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
            chip.setText(m);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> inputModel.setText(m));
            chips.addView(chip);
        }
        
        hScroll.addView(chips);
        container.addView(hScroll);
        container.addView(inputModel);

        new MaterialAlertDialogBuilder(getContext())
            .setTitle(s("Configuração do SDBCodFlow", "SDBCodFlow Settings"))
            .setView(container)
            .setPositiveButton(s("Salvar", "Save"), (d, w) -> {
                String key = inputKey.getText().toString().trim();
                String model = inputModel.getText().toString().trim();

                agente.setApiKey(key);
                agente.setChatModel(model);
                SketchwareUtil.toast(s("Configurado!", "Configured!"));
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
                    android.content.ClipData clip = android.content.ClipData.newPlainText("SDBCodFlow", text.getText());
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
                SketchwareUtil.toast(s("Salvamento não disponível nesta tela.", "Save not available on this screen."));
            }
        } catch (Exception e) {
            SketchwareUtil.toastError(s("Erro ao salvar: ", "Error saving: ") + e.getMessage());
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

            String baseXml = contextXmlName.replace(".xml", "");
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
