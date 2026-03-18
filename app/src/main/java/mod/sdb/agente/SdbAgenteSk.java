package mod.sdb.agente;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class SdbAgenteSk {
    private static final String PREF_NAME = "SDB_CODFLOW_CONFIG";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "chat_model";
    private static final String KEY_CODE_EDITOR_ACCESS = "code_editor_access";
    private static final String KEY_LANGUAGE = "chat_language";
    private static final String DEFAULT_MODEL = "";
    
    private final Context context;
    private final String sc_id;

    public SdbAgenteSk(Context context, String sc_id) {
        this.context = context;
        this.sc_id = sc_id;
    }

    public void setApiKey(String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public String getApiKey() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setChatModel(String model) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODEL, model).apply();
    }

    public String getChatModel() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL);
        if (model.equals("gemini-1.5-flash")) {
            return "";
        }
        if (model.startsWith("models/")) {
            model = model.substring(7);
        }
        return model;
    }

    public void setLanguage(String lang) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
               .edit().putString(KEY_LANGUAGE, lang).apply();
    }

    public String getLanguage() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                      .getString(KEY_LANGUAGE, "pt");
    }

    public static String getLanguage(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                  .getString(KEY_LANGUAGE, "pt");
    }

    public void testConnection(ResponseListener listener) {
        ask("Olá, isto é um teste de conexão. Responda apenas 'OK'.", "Teste de Conexão", listener);
    }

    public interface ResponseListener {
        void onResponse(String response);
        void onError(String error);
    }

    public void incrementCodeEditorAccess() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int current = prefs.getInt(KEY_CODE_EDITOR_ACCESS, 0);
        prefs.edit().putInt(KEY_CODE_EDITOR_ACCESS, current + 1).apply();
    }

    public int getCodeEditorAccess() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_CODE_EDITOR_ACCESS, 0);
    }

    public void ask(String question, String contextInfo, ResponseListener listener) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            listener.onError("API Key não configurada.");
            return;
        }

        new Thread(() -> {
            if (!tryApiCall("v1beta", question, contextInfo, null, null, null, listener)) {
                listener.onError("Modelo não encontrado (404). Certifique-se que o nome do modelo está correto para a sua região/chave.");
            }
        }).start();
    }

    public void askWithHistory(String question, String contextInfo, java.util.List<SdbAgenteActivity.ChatMessage> history, ResponseListener listener) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            listener.onError("API Key não configurada.");
            return;
        }

        new Thread(() -> {
            if (!tryApiCall("v1beta", question, contextInfo, null, null, history, listener)) {
                listener.onError("Modelo não encontrado (404). Certifique-se que o nome do modelo está correto para a sua região/chave.");
            }
        }).start();
    }

    public void askWithImage(String question, String contextInfo, String base64Image, String mimeType, ResponseListener listener) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            listener.onError("API Key não configurada.");
            return;
        }

        new Thread(() -> {
            if (!tryApiCall("v1beta", question, contextInfo, base64Image, mimeType, null, listener)) {
                listener.onError("Erro ao enviar imagem: Modelo não encontrado (404).");
            }
        }).start();
    }

    public void directEdit(String originalContent, String instruction, String type, ResponseListener listener) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            listener.onError("API Key não configurada.");
            return;
        }

        new Thread(() -> {
            if (!tryApiCall("v1beta", originalContent, "EDIÇÃO: " + instruction, null, null, null, listener)) {
                listener.onError("Erro na edição direta: Modelo não encontrado (404).");
            }
        }).start();
    }

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

            // Add history if available
            String lastRole = null;
            JSONObject lastContentObj = null;
            JSONArray lastPartsArray = null;

            if (history != null && !history.isEmpty()) {
                for (SdbAgenteActivity.ChatMessage msg : history) {
                    if (msg.text != null && !msg.text.trim().isEmpty() && !msg.text.startsWith("⚙️") && !msg.text.startsWith("Aguardando resposta")) {
                        String currentRole = msg.isUser ? "user" : "model";
                        
                        if (currentRole.equals(lastRole) && lastContentObj != null) {
                            // Append to previous role to avoid Gemini 400 Error (roles must alternate)
                            JSONObject hTextPart = new JSONObject();
                            hTextPart.put("text", "\n" + msg.text);
                            lastPartsArray.put(hTextPart);
                        } else {
                            // New role block
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
                return false; // Indica que devemos tentar a outra versão
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
}
