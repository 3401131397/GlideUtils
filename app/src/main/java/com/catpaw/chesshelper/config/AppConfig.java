package com.catpaw.chesshelper.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;

import androidx.preference.PreferenceManager;

/**
 * 应用配置管理
 * 通过SharedPreferences管理所有配置项
 */
public class AppConfig {

    public enum AIProvider {
        LOCAL,       // 本地引擎
        OPENAI,      // OpenAI API
        CUSTOM_API   // 自定义API（兼容OpenAI接口格式）
    }

    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_AUTO_RECOGNITION = "auto_recognition";
    private static final String KEY_AI_PROVIDER = "ai_provider";
    private static final String KEY_ENGINE_DEPTH = "engine_depth";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_RECOGNITION_INTERVAL = "recognition_interval";
    private static final String KEY_MANUAL_BOARD_RECT = "manual_board_rect";

    private static AppConfig sInstance;
    private final SharedPreferences mPrefs;

    private AppConfig(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static synchronized AppConfig getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppConfig(context);
        }
        return sInstance;
    }

    // ==================== 开关配置 ====================

    public boolean isServiceEnabled() {
        return mPrefs.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    public void setServiceEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }

    public boolean isAutoRecognitionEnabled() {
        return mPrefs.getBoolean(KEY_AUTO_RECOGNITION, true);
    }

    public void setAutoRecognitionEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_AUTO_RECOGNITION, enabled).apply();
    }

    // ==================== AI配置 ====================

    public AIProvider getAIProvider() {
        String provider = mPrefs.getString(KEY_AI_PROVIDER, "LOCAL");
        try {
            return AIProvider.valueOf(provider);
        } catch (Exception e) {
            return AIProvider.LOCAL;
        }
    }

    public void setAIProvider(AIProvider provider) {
        mPrefs.edit().putString(KEY_AI_PROVIDER, provider.name()).apply();
    }

    public int getEngineDepth() {
        return mPrefs.getInt(KEY_ENGINE_DEPTH, 4);
    }

    public void setEngineDepth(int depth) {
        mPrefs.edit().putInt(KEY_ENGINE_DEPTH, depth).apply();
    }

    // ==================== API配置 ====================

    public String getApiKey() {
        return mPrefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String key) {
        mPrefs.edit().putString(KEY_API_KEY, key).apply();
    }

    public String getApiUrl() {
        return mPrefs.getString(KEY_API_URL, "https://api.openai.com/v1/chat/completions");
    }

    public void setApiUrl(String url) {
        mPrefs.edit().putString(KEY_API_URL, url).apply();
    }

    public String getModelName() {
        return mPrefs.getString(KEY_MODEL_NAME, "gpt-4o");
    }

    public void setModelName(String model) {
        mPrefs.edit().putString(KEY_MODEL_NAME, model).apply();
    }

    // ==================== 识别配置 ====================

    public int getRecognitionInterval() {
        return mPrefs.getInt(KEY_RECOGNITION_INTERVAL, 2000);
    }

    public void setRecognitionInterval(int interval) {
        mPrefs.edit().putInt(KEY_RECOGNITION_INTERVAL, interval).apply();
    }

    public boolean useManualBoardRect() {
        return mPrefs.getBoolean(KEY_MANUAL_BOARD_RECT, false);
    }

    public Rect getManualBoardRect() {
        String rectStr = mPrefs.getString("board_rect_str", null);
        if (rectStr == null) return null;
        try {
            String[] parts = rectStr.split(",");
            return new Rect(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (Exception e) {
            return null;
        }
    }

    public void setManualBoardRect(Rect rect) {
        String rectStr = rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
        mPrefs.edit().putString("board_rect_str", rectStr).apply();
        mPrefs.edit().putBoolean(KEY_MANUAL_BOARD_RECT, true).apply();
    }
}
