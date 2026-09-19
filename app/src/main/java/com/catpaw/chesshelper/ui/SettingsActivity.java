package com.catpaw.chesshelper.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.catpaw.chesshelper.R;
import com.catpaw.chesshelper.config.AppConfig;

/**
 * 设置界面
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // AI提供者切换提示
            ListPreference aiProvider = findPreference("ai_provider");
            if (aiProvider != null) {
                aiProvider.setOnPreferenceChangeListener((preference, newValue) -> {
                    updateSummaryForProvider(newValue.toString());
                    return true;
                });
            }

            // 引擎深度
            SeekBarPreference engineDepth = findPreference("engine_depth");
            if (engineDepth != null) {
                engineDepth.setUpdatesContinuously(true);
            }

            // API Key
            EditTextPreference apiKeyPref = findPreference("api_key");
            if (apiKeyPref != null) {
                apiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String key = (String) newValue;
                    if (key != null && !key.isEmpty()) {
                        apiKeyPref.setSummary("已配置 (长度: " + key.length() + ")");
                    } else {
                        apiKeyPref.setSummary("未配置");
                    }
                    return true;
                });
                // 初始化摘要
                String currentKey = apiKeyPref.getText();
                if (currentKey != null && !currentKey.isEmpty()) {
                    apiKeyPref.setSummary("已配置 (长度: " + currentKey.length() + ")");
                } else {
                    apiKeyPref.setSummary("未配置");
                }
            }
        }

        private void updateSummaryForProvider(String provider) {
            ListPreference pref = findPreference("ai_provider");
            if (pref != null) {
                switch (provider) {
                    case "LOCAL":
                        pref.setSummary("使用本地Alpha-Beta引擎");
                        break;
                    case "OPENAI":
                        pref.setSummary("使用OpenAI GPT-4 / GPT-4o");
                        break;
                    case "CUSTOM_API":
                        pref.setSummary("使用自定义API（兼容OpenAI接口）");
                        break;
                }
            }
        }
    }
}
