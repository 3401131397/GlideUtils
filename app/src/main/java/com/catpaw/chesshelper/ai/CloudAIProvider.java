package com.catpaw.chesshelper.ai;

import android.util.Log;

import com.catpaw.chesshelper.config.AppConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 云端AI提供者
 * 支持OpenAI API及兼容接口格式的自定义API
 */
public class CloudAIProvider {

    private static final String TAG = "CloudAI";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AppConfig mConfig;
    private final OkHttpClient mHttpClient;

    public CloudAIProvider(AppConfig config) {
        mConfig = config;
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String getBestMove(String fen, boolean isRedTurn) throws Exception {
        AppConfig.AIProvider provider = mConfig.getAIProvider();

        switch (provider) {
            case OPENAI:
            case CUSTOM_API:
                return queryOpenAICompatible(fen, isRedTurn);
            default:
                throw new IllegalStateException("不支持的AI提供商: " + provider);
        }
    }

    public void getBestMoveAsync(String fen, boolean isRedTurn, AIResultCallback callback) {
        new Thread(() -> {
            try {
                String move = getBestMove(fen, isRedTurn);
                callback.onSuccess(move);
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    private String queryOpenAICompatible(String fen, boolean isRedTurn) throws Exception {
        String apiKey = mConfig.getApiKey();
        String model = mConfig.getModelName();
        String apiUrl = mConfig.getApiUrl();

        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new IllegalStateException("请先在设置中配置API地址");
        }

        JsonObject requestBody = buildRequest(fen, isRedTurn, model);

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON));

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        try (Response response = mHttpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API调用失败: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "API响应: " + responseBody);
            return parseResponse(responseBody);
        }
    }

    private JsonObject buildRequest(String fen, boolean isRedTurn, String model) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model != null && !model.isEmpty() ? model : "gpt-4o");

        JsonArray messages = new JsonArray();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", buildPrompt(fen, isRedTurn));
        messages.add(userMsg);

        root.add("messages", messages);
        root.addProperty("temperature", 0.1);
        root.addProperty("max_tokens", 100);

        return root;
    }

    private String buildPrompt(String fen, boolean isRedTurn) {
        String turn = isRedTurn ? "红方" : "黑方";
        return "你是一个中国象棋特级大师。分析以下象棋局面并给出最佳着法。\n" +
                "FEN: " + fen + "\n" +
                "轮到: " + turn + "走棋\n" +
                "请直接回答UCCI格式的最佳着法（如：a0a1 或 h2h3）" +
                "只输出走法，不要解释、不要标点符号、不要多余文字。";
    }

    private String parseResponse(String responseBody) throws Exception {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");

        if (choices == null || choices.size() == 0) {
            throw new Exception("API返回choices为空");
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        String content = message.get("content").getAsString().trim();

        return extractMove(content);
    }

    private String extractMove(String response) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([a-i][0-9])([a-i][0-9])");
        java.util.regex.Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1) + matcher.group(2);
        }

        String[] parts = response.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : response.trim();
    }

    public interface AIResultCallback {
        void onSuccess(String bestMove);
        void onError(Exception e);
    }
}
