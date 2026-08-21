package mod.sdb.agente;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class SdbAgenteSk {
    private static final String PREF_NAME = "SDB_CODFLOW_CONFIG";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "chat_model";
    private static final String KEY_CODE_EDITOR_ACCESS = "code_editor_access";
    private static final String KEY_LANGUAGE = "chat_language";
    private static final String KEY_PROVIDER = "ai_provider";
    private static final String KEY_OPENROUTER_KEY = "openrouter_api_key";
    private static final String KEY_OPENROUTER_MODEL = "openrouter_model";
    private static final String KEY_CLAUDE_KEY = "claude_api_key";
    private static final String KEY_CLAUDE_MODEL = "claude_model";
    private static final String KEY_OPENAI_KEY = "openai_api_key";
    private static final String KEY_OPENAI_MODEL = "openai_model";
    private static final String KEY_NVIDIA_KEY = "nvidia_api_key";
    private static final String KEY_NVIDIA_MODEL = "nvidia_model";
    private static final String KEY_DEEPSEEK_KEY = "deepseek_api_key";
    private static final String KEY_DEEPSEEK_MODEL = "deepseek_model";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_CLAUDE = "claude";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_NVIDIA = "nvidia";
    public static final String PROVIDER_DEEPSEEK = "deepseek";

    private final Context context;
    private final String sc_id;

    public SdbAgenteSk(Context context, String sc_id) {
        this.context = context;
        this.sc_id = sc_id;
    }

    // ===== Provider =====

    public void setProvider(String provider) {
        getPrefs().edit().putString(KEY_PROVIDER, provider).apply();
    }

    public String getProvider() {
        return getPrefs().getString(KEY_PROVIDER, PROVIDER_GEMINI);
    }

    // ===== Gemini =====

    public void setApiKey(String apiKey) {
        getPrefs().edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public String getApiKey() {
        return getPrefs().getString(KEY_API_KEY, "");
    }

    public void setChatModel(String model) {
        getPrefs().edit().putString(KEY_MODEL, normalizeGeminiModel(model)).apply();
    }

    public String getChatModel() {
        String model = getPrefs().getString(KEY_MODEL, DEFAULT_MODEL);
        String normalized = normalizeGeminiModel(model);
        if (!normalized.equals(model)) {
            getPrefs().edit().putString(KEY_MODEL, normalized).apply();
        }
        return normalized;
    }

    private static String normalizeGeminiModel(String model) {
        if (model == null || model.trim().isEmpty()) return DEFAULT_MODEL;
        String normalized = model.trim();
        if (normalized.startsWith("models/")) {
            normalized = normalized.substring(7);
        }
        if ("gemini-1.5-flash".equals(normalized)) {
            return DEFAULT_MODEL;
        }
        String lower = normalized.toLowerCase();
        if (lower.startsWith("gemini-3.1-flash-lite")
                && (lower.contains("preview") || lower.contains("experimental"))) {
            return "gemini-3.1-flash-lite";
        }
        return normalized;
    }

    // ===== OpenRouter =====

    public void setOpenRouterKey(String key) {
        getPrefs().edit().putString(KEY_OPENROUTER_KEY, key).apply();
    }

    public String getOpenRouterKey() {
        return getPrefs().getString(KEY_OPENROUTER_KEY, "");
    }

    public void setOpenRouterModel(String model) {
        getPrefs().edit().putString(KEY_OPENROUTER_MODEL, model).apply();
    }

    public String getOpenRouterModel() {
        return getPrefs().getString(KEY_OPENROUTER_MODEL, "google/gemini-2.5-flash:free");
    }

    // ===== Claude (Anthropic) =====

    public void setClaudeKey(String key) {
        getPrefs().edit().putString(KEY_CLAUDE_KEY, key).apply();
    }

    public String getClaudeKey() {
        return getPrefs().getString(KEY_CLAUDE_KEY, "");
    }

    public void setClaudeModel(String model) {
        getPrefs().edit().putString(KEY_CLAUDE_MODEL, model).apply();
    }

    public String getClaudeModel() {
        return getPrefs().getString(KEY_CLAUDE_MODEL, "claude-sonnet-4-20250514");
    }

    // ===== OpenAI / GPT =====

    public void setOpenAIKey(String key) {
        getPrefs().edit().putString(KEY_OPENAI_KEY, key).apply();
    }

    public String getOpenAIKey() {
        return getPrefs().getString(KEY_OPENAI_KEY, "");
    }

    public void setOpenAIModel(String model) {
        getPrefs().edit().putString(KEY_OPENAI_MODEL, model).apply();
    }

    public String getOpenAIModel() {
        return getPrefs().getString(KEY_OPENAI_MODEL, "gpt-4o-mini");
    }

    // ===== NVIDIA NIM =====

    public void setNvidiaKey(String key) {
        getPrefs().edit().putString(KEY_NVIDIA_KEY, key).apply();
    }

    public String getNvidiaKey() {
        return getPrefs().getString(KEY_NVIDIA_KEY, "");
    }

    public void setNvidiaModel(String model) {
        getPrefs().edit().putString(KEY_NVIDIA_MODEL, model).apply();
    }

    public String getNvidiaModel() {
        String value = getPrefs().getString(KEY_NVIDIA_MODEL, "meta/llama-3.1-70b-instruct");
        if (value == null || value.trim().isEmpty() || value.startsWith("openai/")) {
            value = "meta/llama-3.1-70b-instruct";
            getPrefs().edit().putString(KEY_NVIDIA_MODEL, value).apply();
        }
        return value;
    }

    // ===== DeepSeek =====

    public void setDeepSeekKey(String key) {
        getPrefs().edit().putString(KEY_DEEPSEEK_KEY, key).apply();
    }

    public String getDeepSeekKey() {
        return getPrefs().getString(KEY_DEEPSEEK_KEY, "");
    }

    public void setDeepSeekModel(String model) {
        getPrefs().edit().putString(KEY_DEEPSEEK_MODEL, model).apply();
    }

    public String getDeepSeekModel() {
        return getPrefs().getString(KEY_DEEPSEEK_MODEL, "deepseek-v4-flash");
    }

    // ===== Language =====

    public void setLanguage(String lang) {
        getPrefs().edit().putString(KEY_LANGUAGE, lang).apply();
    }

    public String getLanguage() {
        return getPrefs().getString(KEY_LANGUAGE, "pt");
    }

    public static String getLanguage(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                  .getString(KEY_LANGUAGE, "pt");
    }

    // ===== Code Editor Access =====

    public void incrementCodeEditorAccess() {
        int current = getPrefs().getInt(KEY_CODE_EDITOR_ACCESS, 0);
        getPrefs().edit().putInt(KEY_CODE_EDITOR_ACCESS, current + 1).apply();
    }

    public int getCodeEditorAccess() {
        return getPrefs().getInt(KEY_CODE_EDITOR_ACCESS, 0);
    }

    // ===== Connection Test =====

    public void testConnection(ResponseListener listener) {
        ask("Olá, isto é um teste de conexão. Responda apenas 'OK'.", "Teste de Conexão", listener);
    }

    public interface ResponseListener {
        void onResponse(String response);
        void onError(String error);
    }

    // ===== API Calls =====

    public void ask(String question, String contextInfo, ResponseListener listener) {
        String provider = getProvider();
        if (PROVIDER_CLAUDE.equals(provider)) {
            String key = getClaudeKey();
            if (key.isEmpty()) { listener.onError("Claude API Key não configurada."); return; }
            new Thread(() -> callClaude(question, contextInfo, null, null, null, listener)).start();
        } else if (PROVIDER_OPENAI.equals(provider)) {
            String key = getOpenAIKey();
            if (key.isEmpty()) { listener.onError("OpenAI API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("OpenAI", "https://api.openai.com/v1/chat/completions",
                    "Bearer " + key, null, getOpenAIModel(), question, contextInfo, null, null, null, listener)).start();
        } else if (PROVIDER_NVIDIA.equals(provider)) {
            String key = getNvidiaKey();
            if (key.isEmpty()) { listener.onError("NVIDIA API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("NVIDIA", "https://integrate.api.nvidia.com/v1/chat/completions",
                    "Bearer " + key, null, getNvidiaModel(), question, contextInfo, null, null, null, listener)).start();
        } else if (PROVIDER_DEEPSEEK.equals(provider)) {
            String key = getDeepSeekKey();
            if (key.isEmpty()) { listener.onError("DeepSeek API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("DeepSeek", "https://api.deepseek.com/chat/completions",
                    "Bearer " + key, null, getDeepSeekModel(), question, contextInfo, null, null, null, listener)).start();
        } else if (PROVIDER_OPENROUTER.equals(provider)) {
            String key = getOpenRouterKey();
            if (key.isEmpty()) { listener.onError("OpenRouter API Key não configurada."); return; }
            new Thread(() -> callOpenRouter(question, contextInfo, null, null, null, listener)).start();
        } else {
            String apiKey = getApiKey();
            if (apiKey.isEmpty()) { listener.onError("API Key não configurada."); return; }
            new Thread(() -> {
                if (!tryApiCall("v1beta", question, contextInfo, null, null, null, listener)) {
                    listener.onError("Modelo não encontrado (404). Certifique-se que o nome do modelo está correto.");
                }
            }).start();
        }
    }

    public void askWithHistory(String question, String contextInfo, java.util.List<SdbAgenteActivity.ChatMessage> history, ResponseListener listener) {
        String provider = getProvider();
        if (PROVIDER_CLAUDE.equals(provider)) {
            String key = getClaudeKey();
            if (key.isEmpty()) { listener.onError("Claude API Key não configurada."); return; }
            new Thread(() -> callClaude(question, contextInfo, null, null, history, listener)).start();
        } else if (PROVIDER_OPENAI.equals(provider)) {
            String key = getOpenAIKey();
            if (key.isEmpty()) { listener.onError("OpenAI API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("OpenAI", "https://api.openai.com/v1/chat/completions",
                    "Bearer " + key, null, getOpenAIModel(), question, contextInfo, null, null, history, listener)).start();
        } else if (PROVIDER_NVIDIA.equals(provider)) {
            String key = getNvidiaKey();
            if (key.isEmpty()) { listener.onError("NVIDIA API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("NVIDIA", "https://integrate.api.nvidia.com/v1/chat/completions",
                    "Bearer " + key, null, getNvidiaModel(), question, contextInfo, null, null, history, listener)).start();
        } else if (PROVIDER_DEEPSEEK.equals(provider)) {
            String key = getDeepSeekKey();
            if (key.isEmpty()) { listener.onError("DeepSeek API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("DeepSeek", "https://api.deepseek.com/chat/completions",
                    "Bearer " + key, null, getDeepSeekModel(), question, contextInfo, null, null, history, listener)).start();
        } else if (PROVIDER_OPENROUTER.equals(provider)) {
            String key = getOpenRouterKey();
            if (key.isEmpty()) { listener.onError("OpenRouter API Key não configurada."); return; }
            new Thread(() -> callOpenRouter(question, contextInfo, null, null, history, listener)).start();
        } else {
            String apiKey = getApiKey();
            if (apiKey.isEmpty()) { listener.onError("API Key não configurada."); return; }
            new Thread(() -> {
                if (!tryApiCall("v1beta", question, contextInfo, null, null, history, listener)) {
                    listener.onError("Modelo não encontrado (404).");
                }
            }).start();
        }
    }

    public void askWithImage(String question, String contextInfo, String base64Image, String mimeType, ResponseListener listener) {
        String provider = getProvider();
        if (PROVIDER_CLAUDE.equals(provider)) {
            String key = getClaudeKey();
            if (key.isEmpty()) { listener.onError("Claude API Key não configurada."); return; }
            new Thread(() -> callClaude(question, contextInfo, base64Image, mimeType, null, listener)).start();
        } else if (PROVIDER_OPENAI.equals(provider)) {
            String key = getOpenAIKey();
            if (key.isEmpty()) { listener.onError("OpenAI API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("OpenAI", "https://api.openai.com/v1/chat/completions",
                    "Bearer " + key, null, getOpenAIModel(), question, contextInfo, base64Image, mimeType, null, listener)).start();
        } else if (PROVIDER_NVIDIA.equals(provider)) {
            String key = getNvidiaKey();
            if (key.isEmpty()) { listener.onError("NVIDIA API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("NVIDIA", "https://integrate.api.nvidia.com/v1/chat/completions",
                    "Bearer " + key, null, getNvidiaModel(), question, contextInfo, base64Image, mimeType, null, listener)).start();
        } else if (PROVIDER_DEEPSEEK.equals(provider)) {
            listener.onError("DeepSeek nao aceita imagens neste modo. Use Gemini, OpenAI, Claude ou OpenRouter.");
        } else if (PROVIDER_OPENROUTER.equals(provider)) {
            String key = getOpenRouterKey();
            if (key.isEmpty()) { listener.onError("OpenRouter API Key não configurada."); return; }
            new Thread(() -> callOpenRouter(question, contextInfo, base64Image, mimeType, null, listener)).start();
        } else {
            String apiKey = getApiKey();
            if (apiKey.isEmpty()) { listener.onError("API Key não configurada."); return; }
            new Thread(() -> {
                if (!tryApiCall("v1beta", question, contextInfo, base64Image, mimeType, null, listener)) {
                    listener.onError("Erro ao enviar imagem: Modelo não encontrado (404).");
                }
            }).start();
        }
    }

    public void directEdit(String originalContent, String instruction, String type, ResponseListener listener) {
        String provider = getProvider();
        if (PROVIDER_DEEPSEEK.equals(provider)) {
            String key = getDeepSeekKey();
            if (key.isEmpty()) { listener.onError("DeepSeek API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("DeepSeek", "https://api.deepseek.com/chat/completions",
                    "Bearer " + key, null, getDeepSeekModel(), originalContent, "EDICAO: " + instruction, null, null, null, listener)).start();
        } else if (PROVIDER_CLAUDE.equals(provider)) {
            String key = getClaudeKey();
            if (key.isEmpty()) { listener.onError("Claude API Key não configurada."); return; }
            new Thread(() -> callClaude(originalContent, "EDIÇÃO: " + instruction, null, null, null, listener)).start();
        } else if (PROVIDER_OPENAI.equals(provider)) {
            String key = getOpenAIKey();
            if (key.isEmpty()) { listener.onError("OpenAI API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("OpenAI", "https://api.openai.com/v1/chat/completions",
                    "Bearer " + key, null, getOpenAIModel(), originalContent, "EDIÇÃO: " + instruction, null, null, null, listener)).start();
        } else if (PROVIDER_NVIDIA.equals(provider)) {
            String key = getNvidiaKey();
            if (key.isEmpty()) { listener.onError("NVIDIA API Key nao configurada."); return; }
            new Thread(() -> callOpenAICompatible("NVIDIA", "https://integrate.api.nvidia.com/v1/chat/completions",
                    "Bearer " + key, null, getNvidiaModel(), originalContent, "EDIÇÃO: " + instruction, null, null, null, listener)).start();
        } else if (PROVIDER_OPENROUTER.equals(provider)) {
            String key = getOpenRouterKey();
            if (key.isEmpty()) { listener.onError("OpenRouter API Key não configurada."); return; }
            new Thread(() -> callOpenRouter(originalContent, "EDIÇÃO: " + instruction, null, null, null, listener)).start();
        } else {
            String apiKey = getApiKey();
            if (apiKey.isEmpty()) { listener.onError("API Key não configurada."); return; }
            new Thread(() -> {
                if (!tryApiCall("v1beta", originalContent, "EDIÇÃO: " + instruction, null, null, null, listener)) {
                    listener.onError("Erro na edição direta: Modelo não encontrado (404).");
                }
            }).start();
        }
    }

    // ===== Claude (Anthropic) API =====

    private void callClaude(String text, String context, String base64, String mime,
                            java.util.List<SdbAgenteActivity.ChatMessage> history, ResponseListener listener) {
        try {
            String apiKey = getClaudeKey();
            String model = getClaudeModel();

            URL url = new URL("https://api.anthropic.com/v1/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", model);
            jsonBody.put("max_tokens", 8192);
            jsonBody.put("system", context);

            JSONArray messagesArray = new JSONArray();

            // History
            if (history != null && !history.isEmpty()) {
                for (SdbAgenteActivity.ChatMessage msg : history) {
                    if (msg.text != null && !msg.text.trim().isEmpty()
                            && !msg.text.startsWith("⚙️")
                            && !msg.text.startsWith("Aguardando resposta")) {
                        JSONObject histMsg = new JSONObject();
                        histMsg.put("role", msg.isUser ? "user" : "assistant");
                        histMsg.put("content", msg.text);
                        messagesArray.put(histMsg);
                    }
                }
            }

            // Current user message
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");

            if (base64 != null && mime != null) {
                // Multimodal: image + text
                JSONArray contentArray = new JSONArray();

                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image");
                JSONObject source = new JSONObject();
                source.put("type", "base64");
                source.put("media_type", mime);
                source.put("data", base64);
                imagePart.put("source", source);
                contentArray.put(imagePart);

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", "Analise esta imagem. Pergunta: " + text);
                contentArray.put(textPart);

                userMsg.put("content", contentArray);
            } else {
                userMsg.put("content", text);
            }
            messagesArray.put(userMsg);

            jsonBody.put("messages", messagesArray);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.toString().getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                    String responseStr = s.hasNext() ? s.next() : "";
                    JSONObject responseJson = new JSONObject(responseStr);
                    JSONArray content = responseJson.getJSONArray("content");
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject block = content.getJSONObject(i);
                        if ("text".equals(block.getString("type"))) {
                            if (result.length() > 0) result.append("\n");
                            result.append(block.getString("text"));
                        }
                    }
                    listener.onResponse(result.toString());
                }
            } else {
                try (Scanner s = new Scanner(conn.getErrorStream()).useDelimiter("\\A")) {
                    String errorStr = s.hasNext() ? s.next() : "Desconhecido";
                    if (responseCode == 401) {
                        listener.onError("Claude: API Key inválida (401). Verifique sua chave em console.anthropic.com");
                    } else if (responseCode == 403) {
                        listener.onError("Claude: Acesso negado (403). Verifique as permissões da sua API Key.");
                    } else if (responseCode == 429) {
                        listener.onError("Claude: Limite de requisições atingido (429). Aguarde um momento.");
                    } else if (responseCode == 529) {
                        listener.onError("Claude: API sobrecarregada (529). Tente novamente em alguns segundos.");
                    } else {
                        listener.onError("Claude ERRO_" + responseCode + ": " + errorStr);
                    }
                }
            }
        } catch (Exception e) {
            listener.onError("Erro de Conexão Claude: " + e.getMessage());
        }
    }

    // ===== OpenRouter API =====

    private void callOpenRouter(String text, String context, String base64, String mime,
                                java.util.List<SdbAgenteActivity.ChatMessage> history, ResponseListener listener) {
        callOpenAICompatible("OpenRouter", "https://openrouter.ai/api/v1/chat/completions",
                "Bearer " + getOpenRouterKey(),
                new String[]{"HTTP-Referer", "https://sketchware.pro", "X-Title", "GC-AI"},
                getOpenRouterModel(), text, context, base64, mime, history, listener);
    }

    // ===== OpenAI-compatible APIs (OpenRouter, OpenAI, NVIDIA) =====

    private void callOpenAICompatible(String providerLabel, String endpoint, String authHeader, String[] extraHeaders,
                                      String model, String text, String context, String base64, String mime,
                                      java.util.List<SdbAgenteActivity.ChatMessage> history, ResponseListener listener) {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", authHeader);
            if (extraHeaders != null) {
                for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
                    conn.setRequestProperty(extraHeaders[i], extraHeaders[i + 1]);
                }
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", model);
            jsonBody.put("max_tokens", 8192);

            JSONArray messagesArray = new JSONArray();

            // System message
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", context);
            messagesArray.put(systemMsg);

            // History
            if (history != null && !history.isEmpty()) {
                for (SdbAgenteActivity.ChatMessage msg : history) {
                    if (msg.text != null && !msg.text.trim().isEmpty()
                            && !msg.text.startsWith("⚙️")
                            && !msg.text.startsWith("Aguardando resposta")) {
                        JSONObject histMsg = new JSONObject();
                        histMsg.put("role", msg.isUser ? "user" : "assistant");
                        histMsg.put("content", msg.text);
                        messagesArray.put(histMsg);
                    }
                }
            }

            // Current user message
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");

            if (base64 != null && mime != null) {
                // Multimodal: image + text
                JSONArray contentArray = new JSONArray();

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", "Analise esta imagem. Pergunta: " + text);
                contentArray.put(textPart);

                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                imageUrl.put("url", "data:" + mime + ";base64," + base64);
                imagePart.put("image_url", imageUrl);
                contentArray.put(imagePart);

                userMsg.put("content", contentArray);
            } else {
                userMsg.put("content", text);
            }
            messagesArray.put(userMsg);

            jsonBody.put("messages", messagesArray);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.toString().getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                    String responseStr = s.hasNext() ? s.next() : "";
                    JSONObject responseJson = new JSONObject(responseStr);
                    String aiText = responseJson.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .optString("content", "");
                    listener.onResponse(aiText);
                }
            } else {
                try (Scanner s = new Scanner(conn.getErrorStream()).useDelimiter("\\A")) {
                    String errorStr = s.hasNext() ? s.next() : "Desconhecido";
                    if (responseCode == 401) {
                        listener.onError(providerLabel + ": API Key invalida (401).");
                    } else if (responseCode == 402) {
                        listener.onError(providerLabel + ": Creditos insuficientes (402).");
                    } else if (responseCode == 429) {
                        listener.onError(providerLabel + ": Limite de requisicoes atingido (429). Aguarde um momento.");
                    } else {
                        listener.onError(providerLabel + " ERRO_" + responseCode + ": " + errorStr);
                    }
                }
            }
        } catch (Exception e) {
            listener.onError("Erro de Conexao " + providerLabel + ": " + e.getMessage());
        }
    }

    // ===== Gemini API =====

    private boolean tryApiCall(String version, String text, String context, String base64, String mime, java.util.List<SdbAgenteActivity.ChatMessage> history, ResponseListener listener) {
        try {
            String apiKey = getApiKey();
            String model = getChatModel();
            URL url = new URL("https://generativelanguage.googleapis.com/" + version + "/models/" + model + ":generateContent?key=" + apiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            JSONObject jsonBody = new JSONObject();
            JSONArray contentsArray = new JSONArray();

            JSONObject systemInstruction = new JSONObject();
            JSONObject systemParts = new JSONObject();
            systemParts.put("text", context);
            systemInstruction.put("parts", new JSONArray().put(systemParts));
            jsonBody.put("system_instruction", systemInstruction);

            String lastRole = null;
            JSONObject lastContentObj = null;
            JSONArray lastPartsArray = null;

            if (history != null && !history.isEmpty()) {
                for (SdbAgenteActivity.ChatMessage msg : history) {
                    if (msg.text != null && !msg.text.trim().isEmpty() && !msg.text.startsWith("⚙️") && !msg.text.startsWith("Aguardando resposta")) {
                        String currentRole = msg.isUser ? "user" : "model";

                        if (currentRole.equals(lastRole) && lastContentObj != null) {
                            JSONObject hTextPart = new JSONObject();
                            hTextPart.put("text", "\n" + msg.text);
                            lastPartsArray.put(hTextPart);
                        } else {
                            JSONObject hContentObj = new JSONObject();
                            hContentObj.put("role", currentRole);
                            JSONObject hTextPart = new JSONObject();
                            hTextPart.put("text", msg.text);
                            JSONArray hPartsArray = new JSONArray();
                            hPartsArray.put(hTextPart);
                            hContentObj.put("parts", hPartsArray);
                            contentsArray.put(hContentObj);

                            lastContentObj = hContentObj;
                            lastPartsArray = hPartsArray;
                            lastRole = currentRole;
                        }
                    }
                }
            }

            JSONObject contentObj = new JSONObject();
            JSONArray partsArray = new JSONArray();

            JSONObject textPart = new JSONObject();
            if (base64 != null) {
                textPart.put("text", "Analise esta imagem. Pergunta: " + text);
            } else {
                textPart.put("text", text);
            }
            partsArray.put(textPart);

            JSONObject imagePart = null;
            if (base64 != null) {
                imagePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", mime);
                inlineData.put("data", base64);
                imagePart.put("inline_data", inlineData);
                partsArray.put(imagePart);
            }

            if ("user".equals(lastRole) && lastContentObj != null) {
                lastPartsArray.put(textPart);
                if (imagePart != null) {
                    lastPartsArray.put(imagePart);
                }
            } else {
                contentObj.put("role", "user");
                contentObj.put("parts", partsArray);
                contentsArray.put(contentObj);
            }
            jsonBody.put("contents", contentsArray);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.toString().getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                    String responseStr = s.hasNext() ? s.next() : "";
                    JSONObject responseJson = new JSONObject(responseStr);
                    String aiText = responseJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");
                    listener.onResponse(aiText);
                    return true;
                }
            } else if (responseCode == 404) {
                return false;
            } else {
                try (Scanner s = new Scanner(conn.getErrorStream()).useDelimiter("\\A")) {
                    String errorStr = s.hasNext() ? s.next() : "Desconhecido";
                    if (responseCode == 429) {
                        listener.onError("COTA_EXCEDIDA: Limite de uso atingido (Cota 0). " +
                                "Verifique seu plano no AI Studio ou troque o modelo.");
                    } else if (responseCode == 404) {
                        listener.onError("MODELO_NAO_ENCONTRADO: O modelo '" + model + "' não foi encontrado no endpoint " + version + ".");
                    } else {
                        listener.onError("ERRO_API_" + responseCode + ": " + errorStr);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            listener.onError("Erro de Conexão: " + e.getMessage());
            return true;
        }
    }

    // ===== Dynamic model catalogs =====

    public static class ProviderModel {
        public final String id;
        public final String name;
        public final boolean isFree;

        ProviderModel(String id, String name, boolean isFree) {
            this.id = id;
            this.name = name;
            this.isFree = isFree;
        }
    }

    public interface ModelsListener {
        void onModels(List<ProviderModel> models);
        void onError(String error);
    }

    public void fetchOpenRouterModels(boolean freeOnly, ModelsListener listener) {
        fetchModels("https://openrouter.ai/api/v1/models", getOpenRouterKey(), freeOnly, false, listener);
    }

    public void fetchNvidiaModels(boolean freeOnly, ModelsListener listener) {
        fetchModels("https://integrate.api.nvidia.com/v1/models", getNvidiaKey(), freeOnly, true, listener);
    }

    private void fetchModels(String endpoint, String apiKey, boolean freeOnly, boolean nvidia,
                             ModelsListener listener) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                }
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                int code = conn.getResponseCode();
                if (code != 200) {
                    listener.onError("Erro " + code + " ao carregar modelos.");
                    return;
                }
                String body;
                try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    body = scanner.hasNext() ? scanner.next() : "";
                }
                JSONArray data = new JSONObject(body).optJSONArray("data");
                if (data == null) {
                    listener.onError("O provedor retornou um catálogo inválido.");
                    return;
                }
                List<ProviderModel> models = new ArrayList<>();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "").trim();
                    if (id.isEmpty()) continue;
                    String name = item.optString("name", id);
                    JSONObject pricing = item.optJSONObject("pricing");
                    boolean free = pricing != null
                            && "0".equals(pricing.optString("prompt"))
                            && "0".equals(pricing.optString("completion"));
                    if (nvidia && pricing == null) free = isLikelyNvidiaChatModel(id, name);
                    if (!freeOnly || free) models.add(new ProviderModel(id, name, free));
                }
                Collections.sort(models, Comparator.comparing(model -> model.name.toLowerCase(Locale.ROOT)));
                listener.onModels(models);
            } catch (Exception e) {
                listener.onError("Falha ao carregar modelos: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private boolean isLikelyNvidiaChatModel(String id, String name) {
        String value = (id + " " + name).toLowerCase(Locale.ROOT);
        if (value.contains("embed") || value.contains("rerank") || value.contains("image")
                || value.contains("audio") || value.contains("speech")) return false;
        return value.contains("llama") || value.contains("nemotron") || value.contains("mistral")
                || value.contains("qwen") || value.contains("gemma") || value.contains("deepseek");
    }

    // ===== Offline model fallbacks =====

    public static final String[] OPENROUTER_FREE_MODELS = {
        "google/gemini-2.5-flash:free",
        "google/gemini-2.5-pro-preview:free",
        "deepseek/deepseek-chat-v3:free",
        "deepseek/deepseek-r1:free",
        "meta-llama/llama-4-maverick:free",
        "meta-llama/llama-4-scout:free",
        "qwen/qwen3-235b-a22b:free",
        "qwen/qwen3-32b:free",
        "microsoft/mai-ds-r1:free",
        "mistralai/devstral-small:free",
        "moonshotai/kimi-vl-a3b-thinking:free"
    };

    public static final String[] CLAUDE_MODELS = {
        "claude-opus-4-20250514",
        "claude-sonnet-4-20250514",
        "claude-haiku-4-20250414",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022"
    };

    public static final String[] OPENAI_MODELS = {
        "gpt-5.1",
        "gpt-5",
        "gpt-5-mini",
        "gpt-5-nano",
        "gpt-4.1"
    };

    public static final String[] NVIDIA_MODELS = {
        "meta/llama-3.1-70b-instruct",
        "meta/llama-3.1-405b-instruct",
        "meta/llama-3.3-70b-instruct",
        "nvidia/llama-3.1-nemotron-70b-instruct",
        "mistralai/mistral-large-2-instruct",
        "qwen/qwen2.5-coder-32b-instruct",
        "deepseek-ai/deepseek-r1",
        "google/gemma-2-27b-it"
    };

    public static final String[] DEEPSEEK_MODELS = {
        "deepseek-v4-flash",
        "deepseek-v4-pro",
        "deepseek-chat",
        "deepseek-reasoner"
    };

    public static final String[] GEMINI_MODELS = {
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-3.1-pro-preview",
        "gemini-flash-latest",
        "gemini-pro-latest"
    };

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
