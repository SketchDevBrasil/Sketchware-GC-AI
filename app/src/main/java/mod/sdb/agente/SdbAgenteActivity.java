package mod.sdb.agente;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import a.a.a.jC;
import a.a.a.lC;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import java.util.HashMap;
import android.net.Uri;
import android.util.Base64;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bumptech.glide.Glide;

public class SdbAgenteActivity extends BaseAppCompatActivity {
    private String sc_id;
    private SdbAgenteSk agente;
    private RecyclerView chatRecycler;
    private ChatAdapter adapter;
    private TextInputEditText inputText;
    private List<ChatMessage> messages = new ArrayList<>();
    private String currentBase64Image = null;
    private String currentMimeType = null;
    private String currentChatFile;
    private View layoutImagePreview;
    private android.widget.ImageView imgPreview;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sdb_agente_layout);

        sc_id = getIntent().getStringExtra("sc_id");
        agente = new SdbAgenteSk(this, sc_id);

        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btn_history).setOnClickListener(v -> showHistoryDialog());

        chatRecycler = findViewById(R.id.chat_recycler);
        chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        chatRecycler.setAdapter(adapter);

        inputLoader();
        setupChips();
        
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

    private void inputLoader() {
        inputText = findViewById(R.id.input_text);
        layoutImagePreview = findViewById(R.id.layout_image_preview);
        imgPreview = findViewById(R.id.img_preview);
        
        findViewById(R.id.btn_send).setOnClickListener(v -> sendMessage());
        findViewById(R.id.btn_attach).setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        findViewById(R.id.btn_remove_image).setOnClickListener(v -> {
            currentBase64Image = null;
            currentMimeType = null;
            layoutImagePreview.setVisibility(View.GONE);
        });
    }

    private void setupChips() {
        findViewById(R.id.btn_new_chat).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Nova Conversa")
                .setMessage("Deseja iniciar um novo chat? O histórico atual será salvo.")
                .setPositiveButton("Sim", (d, w) -> startNewChat())
                .setNegativeButton("Não", null)
                .show();
        });
    }

    private void processImageData(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            // Resize if too large
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
            SketchwareUtil.toast("Imagem anexada!");
        } catch (Exception e) {
            e.printStackTrace();
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
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<ChatMessage>>() {}.getType();
                List<ChatMessage> savedMessages = new com.google.gson.Gson().fromJson(json, type);
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
        addMessage(new ChatMessage("Nova conversa iniciada. Como posso ajudar com seu projeto " + sc_id + "?", false), true);
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
            // Try to get a preview from the first message
            displayNames[i] = "Conversa " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(files[i].lastModified()));
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Histórico de Conversas")
            .setItems(displayNames, (dialog, which) -> {
                loadMessages(fileNames[which]);
            })
            .setNegativeButton("Fechar", null)
            .show();
    }

    private void sendMessage() {
        String text = inputText.getText().toString().trim();
        if (text.isEmpty() && currentBase64Image == null) return;

        ChatMessage userMsg = new ChatMessage(text, true);
        userMsg.base64Image = currentBase64Image;
        userMsg.mimeType = currentMimeType;
        
        addMessage(userMsg, true);
        inputText.setText("");

        // Show "thinking" state
        final ChatMessage thinkingMsg = new ChatMessage("GC-AI esta analisando o projeto...", false);
        messages.add(thinkingMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        chatRecycler.scrollToPosition(messages.size() - 1);

        String contextInfo = gatherProjectContext();
        SdbAgenteSk.ResponseListener listener = new SdbAgenteSk.ResponseListener() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    int index = messages.indexOf(thinkingMsg);
                    if (index != -1) {
                        messages.remove(index);
                        adapter.notifyItemRemoved(index);
                    }
                    addMessage(new ChatMessage(response, false), true);
                    
                    // Try to apply edits if AI returned JSON
                    if (response.trim().startsWith("{") && response.contains("\"scId\"") && response.contains("\"edits\"")) {
                        try {
                            if (SdbEditEngine.applyEdits(sc_id, response, null)) {
                                addMessage(new ChatMessage("✅ **Edições aplicadas com sucesso!**", false), true);
                                SketchwareUtil.toast("Edições aplicadas no projeto!");
                            } else {
                                addMessage(new ChatMessage("⚠️ **Não foi possível aplicar as edições automaticamente.**", false), true);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            addMessage(new ChatMessage("❌ **Erro ao aplicar edições:** " + e.getMessage(), false), true);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    int index = messages.indexOf(thinkingMsg);
                    if (index != -1) {
                        messages.remove(index);
                        adapter.notifyItemRemoved(index);
                    }
                    SketchwareUtil.toastError(error);
                    addMessage(new ChatMessage("Desculpe, ocorreu um erro: " + error, false), true);
                });
            }
        };

        if (currentBase64Image != null) {
            agente.askWithImage(text.isEmpty() ? "Analise esta imagem" : text, contextInfo, currentBase64Image, currentMimeType, listener);
            currentBase64Image = null;
            currentMimeType = null;
            layoutImagePreview.setVisibility(View.GONE);
        } else {
            String systemInstruction = "\n\nIMPORTANTE: Você é um Agente de Edição Direta. Se o usuário pedir mudanças na lógica ou código, responda APENAS com o JSON no formato: {\"scId\":\"" + sc_id + "\", \"edits\":[{\"javaName\":\"ActivityName\", \"eventName\":\"EventName\", \"blocks\":[...]}]} para que eu possa aplicar as mudanças automaticamente. Use o contexto abaixo para entender a estrutura atual.";
            agente.ask(text + systemInstruction, contextInfo, listener);
        }
    }

    private String gatherProjectContext() {
        return SdbProjectContext.getFullProjectContext(sc_id);
    }

    private void addMessage(ChatMessage msg, boolean save) {
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        chatRecycler.scrollToPosition(messages.size() - 1);
        if (save) saveMessages();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 3, 0, "Configurações")
            .setIcon(pro.sketchware.R.drawable.ic_mtrl_deployed_code)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                startNewChat();
                return true;
            case 2:
                showHistoryDialog();
                return true;
            case 3:
                showApiKeyDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showApiKeyDialog() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);
        scrollView.addView(container);

        // ===== Provider Selection =====
        TextView labelProvider = new TextView(this);
        labelProvider.setText("Provedor de IA:");
        labelProvider.setTextSize(16);
        labelProvider.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(labelProvider);

        com.google.android.material.chip.ChipGroup providerChips = new com.google.android.material.chip.ChipGroup(this);
        providerChips.setSingleSelection(true);
        providerChips.setSelectionRequired(true);

        com.google.android.material.chip.Chip chipGemini = new com.google.android.material.chip.Chip(this);
        chipGemini.setText("Google Gemini");
        chipGemini.setCheckable(true);

        com.google.android.material.chip.Chip chipOpenRouter = new com.google.android.material.chip.Chip(this);
        chipOpenRouter.setText("OpenRouter (IAs Grátis)");
        chipOpenRouter.setCheckable(true);

        com.google.android.material.chip.Chip chipClaude = new com.google.android.material.chip.Chip(this);
        chipClaude.setText("Claude (Anthropic)");
        chipClaude.setCheckable(true);

        providerChips.addView(chipGemini);
        providerChips.addView(chipOpenRouter);
        providerChips.addView(chipClaude);
        container.addView(providerChips);

        // ===== Gemini Section =====
        LinearLayout geminiSection = new LinearLayout(this);
        geminiSection.setOrientation(LinearLayout.VERTICAL);

        com.google.android.material.button.MaterialButton btnGetLink = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
        btnGetLink.setText("Obter Chave Grátis (AI Studio)");
        btnGetLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey"));
            startActivity(intent);
        });
        geminiSection.addView(btnGetLink);

        final EditText inputKey = new EditText(this);
        inputKey.setText(agente.getApiKey());
        inputKey.setHint("API Key (ex: AIza...)");

        TextView labelKey = new TextView(this);
        labelKey.setText("Google Gemini API Key:");
        geminiSection.addView(labelKey);
        geminiSection.addView(inputKey);

        TextView labelModel = new TextView(this);
        labelModel.setText("\nModelo Gemini:");
        geminiSection.addView(labelModel);

        final EditText inputGeminiModel = new EditText(this);
        inputGeminiModel.setText(agente.getChatModel());
        inputGeminiModel.setHint("Modelo (ex: gemini-2.5-flash)");

        LinearLayout modelButtons = new LinearLayout(this);
        modelButtons.setOrientation(LinearLayout.VERTICAL);
        for (String m : SdbAgenteSk.GEMINI_MODELS) {
            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
            btn.setText(m);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> inputGeminiModel.setText(m));
            modelButtons.addView(btn);
        }
        geminiSection.addView(modelButtons);
        geminiSection.addView(inputGeminiModel);

        container.addView(geminiSection);

        // ===== OpenRouter Section =====
        LinearLayout openRouterSection = new LinearLayout(this);
        openRouterSection.setOrientation(LinearLayout.VERTICAL);

        com.google.android.material.button.MaterialButton btnGetOR = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
        btnGetOR.setText("Obter Chave (openrouter.ai)");
        btnGetOR.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://openrouter.ai/keys"));
            startActivity(intent);
        });
        openRouterSection.addView(btnGetOR);

        final EditText inputORKey = new EditText(this);
        inputORKey.setText(agente.getOpenRouterKey());
        inputORKey.setHint("OpenRouter Key (ex: sk-or-...)");

        TextView labelORKey = new TextView(this);
        labelORKey.setText("OpenRouter API Key:");
        openRouterSection.addView(labelORKey);
        openRouterSection.addView(inputORKey);

        TextView labelORModel = new TextView(this);
        labelORModel.setText("\nModelos Grátis disponíveis:");
        openRouterSection.addView(labelORModel);

        LinearLayout orModelButtons = new LinearLayout(this);
        orModelButtons.setOrientation(LinearLayout.VERTICAL);

        final EditText inputORModel = new EditText(this);
        inputORModel.setText(agente.getOpenRouterModel());
        inputORModel.setHint("Modelo OpenRouter");

        for (String m : SdbAgenteSk.OPENROUTER_FREE_MODELS) {
            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
            btn.setText(m.replace(":free", " (grátis)"));
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> inputORModel.setText(m));
            orModelButtons.addView(btn);
        }
        openRouterSection.addView(orModelButtons);
        openRouterSection.addView(inputORModel);

        container.addView(openRouterSection);

        // ===== Claude Section =====
        LinearLayout claudeSection = new LinearLayout(this);
        claudeSection.setOrientation(LinearLayout.VERTICAL);

        com.google.android.material.button.MaterialButton btnGetClaude = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
        btnGetClaude.setText("Obter Chave (console.anthropic.com)");
        btnGetClaude.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://console.anthropic.com/settings/keys"));
            startActivity(intent);
        });
        claudeSection.addView(btnGetClaude);

        final EditText inputClaudeKey = new EditText(this);
        inputClaudeKey.setText(agente.getClaudeKey());
        inputClaudeKey.setHint("Claude Key (ex: sk-ant-...)");

        TextView labelClaudeKey = new TextView(this);
        labelClaudeKey.setText("Claude API Key:");
        claudeSection.addView(labelClaudeKey);
        claudeSection.addView(inputClaudeKey);

        TextView labelClaudeModel = new TextView(this);
        labelClaudeModel.setText("\nModelo Claude:");
        claudeSection.addView(labelClaudeModel);

        final EditText inputClaudeModel = new EditText(this);
        inputClaudeModel.setText(agente.getClaudeModel());
        inputClaudeModel.setHint("Modelo (ex: claude-sonnet-4-20250514)");

        LinearLayout claudeModelButtons = new LinearLayout(this);
        claudeModelButtons.setOrientation(LinearLayout.VERTICAL);
        for (String m : SdbAgenteSk.CLAUDE_MODELS) {
            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
            btn.setText(m);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> inputClaudeModel.setText(m));
            claudeModelButtons.addView(btn);
        }
        claudeSection.addView(claudeModelButtons);
        claudeSection.addView(inputClaudeModel);

        container.addView(claudeSection);

        // ===== Toggle visibility =====
        String currentProvider = agente.getProvider();
        boolean isOpenRouter = SdbAgenteSk.PROVIDER_OPENROUTER.equals(currentProvider);
        boolean isClaude = SdbAgenteSk.PROVIDER_CLAUDE.equals(currentProvider);
        chipGemini.setChecked(!isOpenRouter && !isClaude);
        chipOpenRouter.setChecked(isOpenRouter);
        chipClaude.setChecked(isClaude);
        geminiSection.setVisibility(!isOpenRouter && !isClaude ? View.VISIBLE : View.GONE);
        openRouterSection.setVisibility(isOpenRouter ? View.VISIBLE : View.GONE);
        claudeSection.setVisibility(isClaude ? View.VISIBLE : View.GONE);

        providerChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean orSelected = checkedIds.contains(chipOpenRouter.getId());
            boolean clSelected = checkedIds.contains(chipClaude.getId());
            geminiSection.setVisibility(!orSelected && !clSelected ? View.VISIBLE : View.GONE);
            openRouterSection.setVisibility(orSelected ? View.VISIBLE : View.GONE);
            claudeSection.setVisibility(clSelected ? View.VISIBLE : View.GONE);
        });

        // ===== Test Button =====
        com.google.android.material.button.MaterialButton btnTest = new com.google.android.material.button.MaterialButton(this);
        btnTest.setText("Testar Conexão");
        btnTest.setOnClickListener(v -> {
            if (chipClaude.isChecked()) {
                agente.setProvider(SdbAgenteSk.PROVIDER_CLAUDE);
                agente.setClaudeKey(inputClaudeKey.getText().toString().trim());
                agente.setClaudeModel(inputClaudeModel.getText().toString().trim());
            } else if (chipOpenRouter.isChecked()) {
                agente.setProvider(SdbAgenteSk.PROVIDER_OPENROUTER);
                agente.setOpenRouterKey(inputORKey.getText().toString().trim());
                agente.setOpenRouterModel(inputORModel.getText().toString().trim());
            } else {
                agente.setProvider(SdbAgenteSk.PROVIDER_GEMINI);
                agente.setApiKey(inputKey.getText().toString().trim());
                agente.setChatModel(inputGeminiModel.getText().toString().trim());
            }
            SketchwareUtil.toast("Testando...");
            agente.testConnection(new SdbAgenteSk.ResponseListener() {
                @Override public void onResponse(String r) { runOnUiThread(() -> SketchwareUtil.toast("Sucesso: " + r)); }
                @Override public void onError(String e) { runOnUiThread(() -> SketchwareUtil.toast("Erro: " + e)); }
            });
        });
        container.addView(btnTest);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Configuração do GC-AI")
            .setMessage("Configure o provedor de IA e escolha o modelo.")
            .setView(scrollView)
            .setPositiveButton("Salvar", (d, w) -> {
                if (chipClaude.isChecked()) {
                    agente.setProvider(SdbAgenteSk.PROVIDER_CLAUDE);
                    agente.setClaudeKey(inputClaudeKey.getText().toString().trim());
                    agente.setClaudeModel(inputClaudeModel.getText().toString().trim());
                    SketchwareUtil.toast("Claude: " + inputClaudeModel.getText().toString().trim());
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
            .setNegativeButton("Cancelar", null)
            .show();
    }

    public static class ChatAction {
        public String type;
        public String text;
        public transient Runnable runnable;

        public ChatAction(String type, String text, Runnable runnable) {
            this.type = type;
            this.text = text;
            this.runnable = runnable;
        }
    }

    public static class ChatMessage {
        public String text;
        public boolean isUser;
        public boolean isAd = false;
        public String base64Image;
        public String mimeType;
        public List<ChatAction> actions = new ArrayList<>();
        
        public ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }

        public ChatMessage(boolean isAd) {
            this.isAd = isAd;
            this.text = "";
            this.isUser = false;
        }

        public ChatMessage addAction(String type, String text, Runnable action) {
            this.actions.add(new ChatAction(type, text, action));
            return this;
        }
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {
        private final List<ChatMessage> messages;
        public ChatAdapter(List<ChatMessage> messages) { this.messages = messages; }

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
            ChatMessage msg = messages.get(position);
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
                    for (ChatAction action : msg.actions) {
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
        public ChatViewHolder(View v) { 
            super(v); 
            text = v.findViewById(pro.sketchware.R.id.chat_text);
            image = v.findViewById(pro.sketchware.R.id.chat_image);
            actionsContainer = v.findViewById(pro.sketchware.R.id.chat_actions_container);
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
