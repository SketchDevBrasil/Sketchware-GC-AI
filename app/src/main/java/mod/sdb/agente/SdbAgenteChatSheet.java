package mod.sdb.agente;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
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
    private ActivityResultLauncher<String> imagePickerLauncher;
    
    private boolean isCodeEditorMode = false;
    private String originalCode = null;
    private OnCodeApplyListener codeApplyListener = null;
    
    private String contextName = null;
    private String contextXmlName = null;
    private OnApplyListener applyListener = null;
    private OnAgenteEditListener editListener = null;

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

    public static SdbAgenteChatSheet newInstance(String sc_id, String contextName, OnApplyListener listener) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        args.putString("context_name", contextName);
        fragment.setArguments(args);
        fragment.applyListener = listener;
        return fragment;
    }

    public static SdbAgenteChatSheet newInstance(String sc_id, String contextName, String xmlName, OnAgenteEditListener listener) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        args.putString("context_name", contextName);
        args.putString("context_xml_name", xmlName);
        fragment.setArguments(args);
        fragment.editListener = listener;
        return fragment;
    }

    public static SdbAgenteChatSheet newInstanceForCode(String sc_id, String contextName, String originalCode, OnCodeApplyListener listener) {
        SdbAgenteChatSheet fragment = new SdbAgenteChatSheet();
        Bundle args = new Bundle();
        args.putString("sc_id", sc_id);
        args.putString("context_name", contextName);
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
            if (getArguments().containsKey("original_code")) {
                originalCode = getArguments().getString("original_code");
                isCodeEditorMode = true;
            }
        }
        agente = new SdbAgenteSk(getContext(), sc_id);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sdb_agente_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hide full screen elements if any or adjust for sheet
        view.findViewById(R.id.btn_back).setVisibility(View.GONE); // No back needed in sheet
        view.findViewById(R.id.btn_history).setOnClickListener(v -> showHistoryDialog());

        TextView tvTitle = view.findViewById(R.id.tv_title);
        if (tvTitle != null) {
            if (contextName != null) {
                tvTitle.setText("AgenteAI: " + contextName);
            } else {
                tvTitle.setText("AgenteAI: Projeto " + sc_id);
            }
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

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                processImageData(uri);
            }
        });
    }

    private void inputLoader(View view) {
        inputText = view.findViewById(R.id.input_text);
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
    }

    private void setupChips(View view) {
        view.findViewById(R.id.chip_optimize).setOnClickListener(v -> inputText.setText("⚡ Otimizar: " + inputText.getText().toString()));
        view.findViewById(R.id.chip_fix).setOnClickListener(v -> inputText.setText("🛠️ Corrigir: " + inputText.getText().toString()));
        view.findViewById(R.id.chip_explain).setOnClickListener(v -> inputText.setText("📖 Explicar: " + inputText.getText().toString()));
        view.findViewById(R.id.chip_new).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(getContext())
                .setTitle("Nova Conversa")
                .setMessage("Deseja iniciar um novo chat? O histórico atual será salvo.")
                .setPositiveButton("Sim", (d, w) -> startNewChat())
                .setNegativeButton("Não", null)
                .show();
        });
        
        // Settings/Config chip or button replacement
        view.findViewById(R.id.btn_history).setOnLongClickListener(v -> {
            showApiKeyDialog();
            return true;
        });

        // Add Apply chip if listener exists
        if (applyListener != null) {
            com.google.android.material.chip.Chip applyChip = new com.google.android.material.chip.Chip(getContext());
            applyChip.setText("🚀 Aplicar ao Contexto");
            applyChip.setOnClickListener(v -> {
                String text = inputText.getText().toString().trim();
                if (!text.isEmpty()) {
                    setThinking(true);
                    applyListener.onApply(text);
                } else {
                    SketchwareUtil.toast("Digite uma instrução primeiro");
                }
            });
            ((com.google.android.material.chip.ChipGroup) view.findViewById(R.id.chip_group_actions)).addView(applyChip, 0);
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
            SketchwareUtil.toastError("Erro ao processar imagem: " + e.getMessage());
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
        addMessage(new SdbAgenteActivity.ChatMessage("Nova conversa iniciada. Como posso ajudar com seu projeto " + sc_id + "?", false), true);
    }

    private void showHistoryDialog() {
        String dirPath = pro.sketchware.utility.FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/";
        java.io.File dir = new java.io.File(dirPath);
        java.io.File[] files = dir.listFiles((d, name) -> name.startsWith("chat_") && name.endsWith(".json"));
        
        if (files == null || files.length == 0) {
            SketchwareUtil.toast("Nenhum histórico encontrado.");
            return;
        }

        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        String[] fileNames = new String[files.length];
        String[] displayNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
            displayNames[i] = "Conversa " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(files[i].lastModified()));
        }

        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Histórico de Conversas")
            .setItems(displayNames, (dialog, which) -> {
                loadMessages(fileNames[which]);
            })
            .setPositiveButton("Configurar API", (d, w) -> showApiKeyDialog())
            .setNeutralButton("Apagar Todas", (d, w) -> {
                for (java.io.File file : files) {
                    file.delete();
                }
                startNewChat();
                SketchwareUtil.toast("Histórico apagado!");
            })
            .setNegativeButton("Fechar", null)
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

        final SdbAgenteActivity.ChatMessage thinkingMsg = new SdbAgenteActivity.ChatMessage("Aguardando resposta do SDBCodFlow...", false);
        messages.add(thinkingMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        chatRecycler.scrollToPosition(messages.size() - 1);

        String contextInfo = SdbProjectContext.getFullProjectContext(sc_id);
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
                    String trimmedResponse = response.trim();
                    // Detect JSON edits - check for mandatory structure (scId AND (edits OR operations))
                    int jsonStart = trimmedResponse.indexOf("{");
                    int jsonEnd = trimmedResponse.lastIndexOf("}") + 1;
                    
                    boolean hasJson = jsonStart != -1 && jsonEnd > jsonStart;
                    String potentialJson = hasJson ? trimmedResponse.substring(jsonStart, jsonEnd) : "";
                    
                    boolean isJsonEdit = hasJson && potentialJson.contains("\"scId\"") && 
                                        (potentialJson.contains("\"edits\"") || potentialJson.contains("\"operations\""));

                    if (isJsonEdit) {
                        try {
                            String jsonOnly = potentialJson;
                            // Clean up conversational artifacts
                            String textPart = trimmedResponse.replace(jsonOnly, "").replace("```json", "").replace("```", "").trim();

                            if (!textPart.isEmpty()) {
                                addMessage(new SdbAgenteActivity.ChatMessage(textPart, false), true);
                            }

                            addMessage(new SdbAgenteActivity.ChatMessage("⚙️ **Processando alterações...**", false), true);
                            setThinking(true);
                            
                            new android.os.Handler().postDelayed(() -> {
                                boolean autoApply = cbAutoApply != null && cbAutoApply.isChecked();
                                
                                if (autoApply) {
                                    if (SdbEditEngine.applyEdits(jsonOnly, contextXmlName)) {
                                        SdbAgenteActivity.ChatMessage successMsg = new SdbAgenteActivity.ChatMessage("✅ **Edições aplicadas com sucesso!**", false);
                                        
                                        // Add Save button if we are in DesignActivity
                                        if (getActivity() instanceof com.besome.sketch.design.DesignActivity) {
                                            successMsg.setAction("save_project", "Salvar Projeto", () -> {
                                                ((com.besome.sketch.design.DesignActivity) getActivity()).saveProject();
                                                addMessage(new SdbAgenteActivity.ChatMessage("💾 Projeto salvo com sucesso!", false), false);
                                            });
                                        }
                                        
                                        addMessage(successMsg, true);
                                        SketchwareUtil.toast("Edições aplicadas!");
                                        if (editListener != null) {
                                            editListener.onEditApplied();
                                        }
                                    } else {
                                        addMessage(new SdbAgenteActivity.ChatMessage("❌ **Falha ao aplicar as edições.**\nJSON recebido era inválido ou vazio:\n```json\n" + jsonOnly + "\n```", false), true);
                                    }
                                } else {
                                    // Manual Apply Mode
                                    SdbAgenteActivity.ChatMessage manualMsg = new SdbAgenteActivity.ChatMessage("⚙️ **Alterações prontas.**\nClique no botão abaixo para aplicar no projeto.", false);
                                    manualMsg.setAction("apply_edits", "Aplicar Mudanças", () -> {
                                        if (SdbEditEngine.applyEdits(jsonOnly, contextXmlName)) {
                                            SketchwareUtil.toast("Edições aplicadas manualmente!");
                                            if (editListener != null) {
                                                editListener.onEditApplied();
                                            }
                                        } else {
                                            SketchwareUtil.toastError("Erro ao aplicar edições");
                                        }
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
                        addMessage(new SdbAgenteActivity.ChatMessage(response, false), true);
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
                    addMessage(new SdbAgenteActivity.ChatMessage("Desculpe, ocorreu um erro: " + error, false), true);
                });
            }
        };

        java.util.List<SdbAgenteActivity.ChatMessage> historyForApi = new java.util.ArrayList<>(messages);
        if (!historyForApi.isEmpty()) historyForApi.remove(historyForApi.size() - 1); // Remove thinkingMsg
        if (!historyForApi.isEmpty()) historyForApi.remove(historyForApi.size() - 1); // Remove current userMsg

        if (currentBase64Image != null) {
            agente.askWithImage(text.isEmpty() ? "Analise esta imagem" : text, contextInfo, currentBase64Image, currentMimeType, listener);
            currentBase64Image = null;
            currentMimeType = null;
            layoutImagePreview.setVisibility(View.GONE);
        } else if (isCodeEditorMode) {
            String instruction = "Você é um Agente de Refatoração de Código para o arquivo: " + contextName + ".\n"
                + "Responda APENAS com o código completo atualizado. SEM explicações, SEM markdown (```).\n"
                + "CÓDIGO ATUAL:\n" + originalCode;
            agente.askWithHistory(text, instruction, historyForApi, new SdbAgenteSk.ResponseListener() {
                @Override
                public void onResponse(String response) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        int index = messages.indexOf(thinkingMsg);
                        if (index != -1) {
                            messages.remove(index);
                            adapter.notifyItemRemoved(index);
                        }
                        
                        String finalCode = response.trim();
                        // Strip markdown if AI ignored instructions
                        if (finalCode.startsWith("```")) {
                            int start = finalCode.indexOf("\n") + 1;
                            int end = finalCode.lastIndexOf("```");
                            if (end > start) finalCode = finalCode.substring(start, end);
                        }

                        if (codeApplyListener != null) {
                            codeApplyListener.onCodeApply(finalCode);
                        }
                        addMessage(new SdbAgenteActivity.ChatMessage("✅ **Código atualizado no editor!**", false), true);
                    });
                }

                @Override
                public void onError(String error) {
                    listener.onError(error);
                }
            });
        } else {
            String contextPrefix = (contextName != null) ? "CONTEXTO ATUAL: " + contextName + "\n" : "";
            String xmlPrefix = (contextXmlName != null) ? "TELA ATUAL (XML): " + contextXmlName + "\n" : "";
            
            String instruction = contextPrefix + xmlPrefix + "Você é um Agente Inteligente para Sketchware Pro.\n"
                + "Você TEM PODER para modificar o projeto diretamente enviando o JSON correto.\n"
                + "1. Se o usuário quiser MUDAR a lógica ou interface, forneça o JSON de edições. NUNCA diga que não pode.\n"
                + "2. Para interface, use 'operations': 'add_widget', 'update_widget', 'remove_widget', 'add_drawable', 'add_custom_block'.\n"
                + "EXEMPLOS DE OPERAÇÕES:\n"
                + "{\n"
                + "  \"scId\": \"" + sc_id + "\",\n"
                + "  \"operations\": [\n"
                + "    { \"op\": \"add_custom_block\", \"data\": { \"palette_name\": \"UI Design\", \"palette_color\": \"#FF0000\", \"blocks\": [ { \"name\": \"bg_color\", \"type\": \" \", \"typeName\": \"\", \"spec\": \"set background color %s\", \"code\": \"view.setBackgroundColor(Color.parseColor(%1$s));\" } ] } },\n"
                + "    { \"op\": \"add_drawable\", \"data\": { \"drawable_name\": \"sdb_btn_bg\", \"xml_content\": \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\"?>\\n<shape xmlns:android=\\\"http://schemas.android.com/apk/res/android\\\">\\n  <corners android:radius=\\\"8dp\\\"/>\\n  <solid android:color=\\\"#FF0000\\\"/>\\n</shape>\" } },\n"
                + "    { \"op\": \"add_widget\", \"xmlName\": \"" + (contextXmlName != null ? contextXmlName : "main") + "\", \"data\": { \"widget_id\": \"btn1\", \"parent_id\": \"linear1\", \"widget_type\": 3, \"attributes\": {\"android:text\": \"Olá\", \"android:background\": \"@drawable/sdb_btn_bg\"} } },\n"
                + "    { \"op\": \"update_widget\", \"xmlName\": \"" + (contextXmlName != null ? contextXmlName : "main") + "\", \"data\": { \"widget_id\": \"btn1\", \"attributes\": {\"android:text\": \"Mundo\"} } },\n"
                + "    { \"op\": \"remove_widget\", \"xmlName\": \"" + (contextXmlName != null ? contextXmlName : "main") + "\", \"data\": { \"widget_id\": \"textview1\" } }\n"
                + "  ]\n"
                + "}\n"
                + "MAPPING DE TIPOS (widget_type):\n"
                + "0:LinearLayout(H), 1:RelativeLayout, 3:Button, 4:TextView, 5:EditText, 6:ImageView.\n"
                + "CUSTOM BLOCK SPECS: %s=string, %d=number, %b=boolean, %m.view=view\n"
                + "IMPORTANTE: Responda primeiro com uma breve explicação e depois o JSON. Use o contexto abaixo:";
            setThinking(true);
            agente.askWithHistory(text, contextInfo + "\n\n" + instruction, historyForApi, listener);
        }
    }

    public void setOnAgenteEditListener(OnAgenteEditListener listener) {
        this.editListener = listener;
    }

    private void addMessage(SdbAgenteActivity.ChatMessage msg, boolean save) {
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        chatRecycler.scrollToPosition(messages.size() - 1);
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
        btnGetLink.setText("Obter Chave Grátis (AI Studio)");
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
        labelModel.setText("\nEscolha ou Digite o Modelo:");
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
            .setTitle("Configuração do SDBCodFlow")
            .setView(container)
            .setPositiveButton("Salvar", (d, w) -> {
                String key = inputKey.getText().toString().trim();
                String model = inputModel.getText().toString().trim();
                agente.setApiKey(key);
                agente.setChatModel(model);
                SketchwareUtil.toast("Configurado!");
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {
        private final List<SdbAgenteActivity.ChatMessage> messages;
        public ChatAdapter(List<SdbAgenteActivity.ChatMessage> messages) { this.messages = messages; }

        @Override public int getItemViewType(int position) {
            return messages.get(position).isUser ? 1 : 0;
        }

        @NonNull @Override 
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v;
            if (viewType == 1) {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sdb_agente_chat_item_user, parent, false);
            } else {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.sdb_agente_chat_item_ai, parent, false);
            }
            return new ChatViewHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            SdbAgenteActivity.ChatMessage msg = messages.get(position);
            io.noties.markwon.Markwon.create(holder.itemView.getContext()).setMarkdown(holder.text, msg.text);
            
            if (msg.base64Image != null && !msg.base64Image.isEmpty()) {
                holder.image.setVisibility(View.VISIBLE);
                byte[] decodedString = android.util.Base64.decode(msg.base64Image, android.util.Base64.DEFAULT);
                Glide.with(holder.itemView.getContext()).asBitmap().load(decodedString).into(holder.image);
            } else {
                holder.image.setVisibility(View.GONE);
            }

            if (holder.actionBtn != null) {
                if (msg.actionText != null && !msg.actionText.isEmpty() && msg.actionRunnable != null) {
                    holder.actionBtn.setVisibility(View.VISIBLE);
                    holder.actionBtn.setText(msg.actionText);
                    holder.actionBtn.setOnClickListener(v -> msg.actionRunnable.run());
                } else {
                    holder.actionBtn.setVisibility(View.GONE);
                }
            }
        }
        @Override public int getItemCount() { return messages.size(); }
    }

    private static class ChatViewHolder extends RecyclerView.ViewHolder {
        public TextView text;
        public android.widget.ImageView image;
        public com.google.android.material.button.MaterialButton actionBtn;
        public ChatViewHolder(View v) { 
            super(v); 
            text = v.findViewById(R.id.chat_text);
            image = v.findViewById(R.id.chat_image);
            actionBtn = v.findViewById(R.id.chat_action_btn);
        }
    }
}
