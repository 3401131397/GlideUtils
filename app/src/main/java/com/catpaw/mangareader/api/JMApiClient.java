package com.catpaw.mangareader.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.catpaw.mangareader.model.CategoryItem;
import com.catpaw.mangareader.model.MangaItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JMApiClient {

    private static final String TAG = "JMApiClient";
    private static final String BASE_URL = "https://www.cdngwc.cc";
    // 正确常量值 (来源: JMComic-Crawler-Python 开源项目 github.com/bigbanging/JMComic-Crawler-Python)
    private static final String APP_TOKEN_SECRET = "18comicAPP";
    private static final String APP_DATA_SECRET = "185Hcomic3PAPP7R";
    private static final String APP_VERSION = "1.7.0";
    private static final String USER_AGENT = "okhttp/3.12.1";
    private static final String COOKIE = "ipcountry=HK";

    private static volatile JMApiClient instance;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;

    public interface ApiSuccessCallback<T> {
        void onSuccess(T result);
    }

    public interface ApiFailureCallback {
        void onFailure(String error);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    private JMApiClient() {
        mainHandler = new Handler(Looper.getMainLooper());
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static JMApiClient getInstance() {
        if (instance == null) {
            synchronized (JMApiClient.class) {
                if (instance == null) {
                    instance = new JMApiClient();
                }
            }
        }
        return instance;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 not found", e);
            return "";
        }
    }

    private String generateToken(String timestamp) {
        return md5(timestamp + APP_TOKEN_SECRET);
    }

    /**
     * 生成 AES 解密密钥
     * 算法: md5hex(timestamp + APP_DATA_SECRET) 的 ASCII 字节 (32字节 = 256位)
     */
    private byte[] generateKey(String timestamp) {
        return md5(timestamp + APP_DATA_SECRET).getBytes(StandardCharsets.UTF_8);
    }

    private String decrypt(String ciphertext, String timestamp) {
        try {
            byte[] keyBytes = generateKey(timestamp);
            byte[] encrypted = Base64.decode(ciphertext, Base64.DEFAULT);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // JMAPi 使用 AES-ECB 模式 (不是 CBC!)，无 IV
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decrypted = cipher.doFinal(encrypted);

            // 手动去除 PKCS7 padding
            int padLen = decrypted[decrypted.length - 1] & 0xFF;
            if (padLen < 1 || padLen > 16) {
                Log.e(TAG, "Invalid PKCS7 padding: " + padLen);
                return null;
            }
            byte[] result = new byte[decrypted.length - padLen];
            System.arraycopy(decrypted, 0, result, 0, result.length);

            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decrypt error", e);
            return null;
        }
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private void sendRequest(String path, String queryParams,
            ApiSuccessCallback<String> onSuccess, ApiFailureCallback onFailure) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String token = generateToken(timestamp);

        StringBuilder urlBuilder = new StringBuilder(BASE_URL).append(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            urlBuilder.append("?").append(queryParams);
        }

        String url = urlBuilder.toString();
        Log.d(TAG, "→ " + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("tokenparam", timestamp + "," + APP_VERSION)
                .addHeader("token", token)
                .addHeader("cookie", COOKIE)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                mainHandler.post(() -> onFailure.onFailure("Network: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> onFailure.onFailure("HTTP " + response.code()));
                    response.close();
                    return;
                }
                try {
                    String body = response.body().string();
                    Log.d(TAG, "← " + body.substring(0, Math.min(200, body.length())));
                    JSONObject json = new JSONObject(body);
                    if (json.optInt("code", -1) == 200) {
                        String encrypted = json.optString("data", "");
                        String decrypted = decrypt(encrypted, timestamp);
                        if (decrypted != null) {
                            mainHandler.post(() -> onSuccess.onSuccess(decrypted));
                        } else {
                            mainHandler.post(() -> onFailure.onFailure("Decrypt failed"));
                        }
                    } else {
                        mainHandler.post(() -> onFailure.onFailure("API " + json.optInt("code")));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                    mainHandler.post(() -> onFailure.onFailure("Parse: " + e.getMessage()));
                }
                response.close();
            }
        });
    }

    public void getCategories(ApiCallback<List<CategoryItem>> callback) {
        sendRequest("/categories", "", response -> {
            try {
                List<CategoryItem> items = new ArrayList<>();
                JSONArray arr = new JSONArray(response);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    CategoryItem item = new CategoryItem();
                    item.setCategoryId(obj.optString("categoryId", String.valueOf(i)));
                    item.setTitle(obj.optString("title", obj.optString("name", "Cat " + i)));
                    items.add(item);
                }
                callback.onSuccess(items);
            } catch (JSONException e) {
                callback.onFailure("Categories parse: " + e.getMessage());
            }
        }, callback::onFailure);
    }

    public void getCategoryFilter(String order, String category, int page,
            ApiCallback<List<MangaItem>> callback) {
        String params = "o=" + encode(order) + "&c=" + encode(category) + "&page=" + page;
        sendRequest("/categories/filter", params, response -> {
            try {
                callback.onSuccess(parseMangaList(response));
            } catch (JSONException e) {
                callback.onFailure("Filter parse: " + e.getMessage());
            }
        }, callback::onFailure);
    }

    public void getSearch(String query, String order, int page,
            ApiCallback<List<MangaItem>> callback) {
        String params = "search_query=" + encode(query) + "&o=" + encode(order) + "&page=" + page;
        sendRequest("/search", params, response -> {
            try {
                callback.onSuccess(parseMangaList(response));
            } catch (JSONException e) {
                callback.onFailure("Search parse: " + e.getMessage());
            }
        }, callback::onFailure);
    }

    public void getAlbumDetail(String albumId, ApiCallback<String> callback) {
        sendRequest("/album/" + albumId, "", callback::onSuccess, callback::onFailure);
    }

    public void getChapterDetail(String chapterId, ApiCallback<String> callback) {
        sendRequest("/chapter?id=" + encode(chapterId), "", callback::onSuccess, callback::onFailure);
    }

    private List<MangaItem> parseMangaList(String jsonStr) throws JSONException {
        List<MangaItem> items = new ArrayList<>();
        JSONArray arr = new JSONArray(jsonStr);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            MangaItem item = new MangaItem();
            item.setAlbumId(obj.optString("albumId", String.valueOf(i)));
            item.setTitle(obj.optString("title", ""));
            item.setCoverUrl(obj.optString("coverUrl", obj.optString("cover", "")));
            item.setTags(obj.optString("tags", ""));
            item.setViews(obj.optString("views", ""));
            item.setCategory(obj.optString("category", ""));
            item.setSummary(obj.optString("summary", ""));
            items.add(item);
        }
        return items;
    }
}
