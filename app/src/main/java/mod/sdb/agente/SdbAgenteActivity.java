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
        findViewById(R.id.chip_optimize).setOnClickListener(v -> inputText.setText("⚡ Otimizar: " + inputText.getText().toString()));
        findViewById(R.id.chip_fix).setOnClickListener(v -> inputText.setText("🛠️ Corrigir: " + inputText.getText().toString()));
        findViewById(R.id.chip_explain).setOnClickListener(v -> inputText.setText("📖 Explicar: " + inputText.getText().toString()));
        findViewById(R.id.chip_new).setOnClickListener(v -> {
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
        final ChatMessage thinkingMsg = new ChatMessage("Aguardando resposta do SDBCodFlow...", false);
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
                            if (SdbEditEngine.applyEdits(response, null)) {
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
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);

        final EditText inputKey = new EditText(this);
        inputKey.setText(agente.getApiKey());
        inputKey.setHint("API Key (ex: AIza...)");
        
        com.google.android.material.button.MaterialButton btnGetLink = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
        btnGetLink.setText("Obter Chave Grátis (AI Studio)");
        btnGetLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey"));
            startActivity(intent);
        });
        container.addView(btnGetLink);
        
        final EditText inputModel = new EditText(this);
        inputModel.setText(agente.getChatModel());
        inputModel.setHint("Modelo (ex: gemini-2.0-flash)");

        TextView labelKey = new TextView(this);
        labelKey.setText("Google Gemini API Key:");
        container.addView(labelKey);
        container.addView(inputKey);

        TextView labelModel = new TextView(this);
        labelModel.setText("\nEscolha ou Digite o Modelo:");
        container.addView(labelModel);
        
        // Elite Models requested by user
        String[] models = {
            "gemini-3-flash-preview",
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-pro-preview",
            "gemini-2.5-flash",
            "gemini-2.5-pro"
        };
        LinearLayout modelButtons = new LinearLayout(this);
        modelButtons.setOrientation(LinearLayout.VERTICAL);
        
        for (String m : models) {
            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this, null, R.attr.borderlessButtonStyle);
            btn.setText(m);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> inputModel.setText(m));
            modelButtons.addView(btn);
        }
        
        container.addView(modelButtons);
        container.addView(inputModel);

        com.google.android.material.button.MaterialButton btnTest = new com.google.android.material.button.MaterialButton(this);
        btnTest.setText("Testar Conexão");
        btnTest.setOnClickListener(v -> {
            String key = inputKey.getText().toString().trim();
            String model = inputModel.getText().toString().trim();
            if (key.isEmpty()) {
                SketchwareUtil.toast("Insira a chave primeiro");
                return;
            }
            agente.setApiKey(key);
            agente.setChatModel(model);
            SketchwareUtil.toast("Testando...");
            agente.testConnection(new SdbAgenteSk.ResponseListener() {
                @Override public void onResponse(String r) { SketchwareUtil.toast("Sucesso: " + r); }
                @Override public void onError(String e) { SketchwareUtil.toast("Erro: " + e); }
            });
        });
        container.addView(btnTest);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Configuração do SDBCodFlow")
            .setMessage("Configure sua chave e escolha a inteligência do Agente.")
            .setView(container)
            .setPositiveButton("Salvar", (d, w) -> {
                String key = inputKey.getText().toString().trim();
                String model = inputModel.getText().toString().trim();
                if (model.isEmpty()) model = "gemini-1.5-flash";
                
                agente.setApiKey(key);
                agente.setChatModel(model);
                SketchwareUtil.toast("SDBCodFlow configurado para " + model);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    public static class ChatMessage {
        public String text;
        public boolean isUser;
        public String base64Image;
        public String mimeType;
        public String actionType;
        public String actionText;
        public transient Runnable actionRunnable;
        
        public ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }

        public ChatMessage setAction(String type, String text, Runnable action) {
            this.actionType = type;
            this.actionText = text;
            this.actionRunnable = action;
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

            if (holder.actionBtn != null) {
                if (msg.actionType != null && msg.actionText != null && msg.actionRunnable != null) {
                    holder.actionBtn.setVisibility(View.VISIBLE);
                    holder.actionBtn.setText(msg.actionText);
                    holder.actionBtn.setOnClickListener(v -> msg.actionRunnable.run());
                } else {
                    holder.actionBtn.setVisibility(View.GONE);
                    holder.actionBtn.setOnClickListener(null);
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
