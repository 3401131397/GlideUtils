package com.catpaw.mangareader.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.catpaw.mangareader.model.CategoryItem;
import com.catpaw.mangareader.model.MangaItem;
import com.catpaw.mangareader.model.TagItem;

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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JMApiClient {

    private static final String TAG = "JMApiClient";
    private static final String BASE_URL = "https://www.cdngwc.cc";
    private static final String TOKEN_SECRET = "0WyJFBix1e6c7TqSg7XgDliOvQy3lASQ";
    private static final String KEY_SECRET = "nNbsEgIn8uIItm9d";
    private static final String IV_SECRET = "dygIVvYyTtpzc8wm";
    private static final String VERSION = "1.1.2";

    private static JMApiClient instance;
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
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static synchronized JMApiClient getInstance() {
        if (instance == null) {
            instance = new JMApiClient();
        }
        return instance;
    }

    private String generateToken(String timestamp) {
        String input = timestamp + TOKEN_SECRET;
        return md5(input);
    }

    private byte[] generateKey(String timestamp) {
        String input = timestamp + KEY_SECRET + VERSION;
        String hexKey = md5(input);
        return hexStringToBytes(hexKey);
    }

    private byte[] generateIv(String timestamp) {
        String input = timestamp + IV_SECRET + VERSION;
        String hexIv = md5(input);
        return hexIv.substring(0, 16).getBytes(StandardCharsets.UTF_8);
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
            Log.e(TAG, "MD5 algorithm not found", e);
            return "";
        }
    }

    private byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String decrypt(String ciphertext, String timestamp) {
        try {
            byte[] keyBytes = generateKey(timestamp);
            byte[] ivBytes = generateIv(timestamp);
            byte[] encryptedBytes = Base64.decode(ciphertext, Base64.DEFAULT);

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decrypt failed", e);
            return null;
        }
    }

    private void sendRequest(String path, String params,
            ApiSuccessCallback<String> onSuccess, ApiFailureCallback onFailure) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String token = generateToken(timestamp);

        String url = BASE_URL + path + "?token=" + token + "&t=" + timestamp;
        if (params != null && !params.isEmpty()) {
            url = url + "&" + params;
        }

        Log.d(TAG, "Request URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "okhttp/3.12.1")
                .addHeader("tokenparam", timestamp + "," + VERSION)
                .addHeader("token", token)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error", e);
                mainHandler.post(() -> onFailure.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseBody);
                        int code = jsonObject.optInt("code", -1);
                        if (code == 200) {
                            String encryptedData = jsonObject.optString("data", "");
                            String decrypted = decrypt(encryptedData, timestamp);
                            if (decrypted != null) {
                                mainHandler.post(() -> onSuccess.onSuccess(decrypted));
                            } else {
                                mainHandler.post(() -> onFailure.onFailure("Decryption failed"));
                            }
                        } else {
                            mainHandler.post(() -> onFailure.onFailure("API error code: " + code));
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parse error", e);
                        mainHandler.post(() -> onFailure.onFailure("JSON parse error: " + e.getMessage()));
                    } catch (IOException e) {
                        Log.e(TAG, "IO error", e);
                        mainHandler.post(() -> onFailure.onFailure("IO error: " + e.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> onFailure.onFailure("HTTP error: " + response.code()));
                }
                response.close();
            }
        });
    }

    private String buildKeyValue(String... pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) sb.append("&");
            try {
                sb.append(URLEncoder.encode(pairs[i], "UTF-8"))
                        .append("=")
                        .append(URLEncoder.encode(pairs[i + 1], "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                sb.append(pairs[i]).append("=").append(pairs[i + 1]);
            }
        }
        return sb.toString();
    }

    public void getCategories(ApiCallback<List<CategoryItem>> callback) {
        sendRequest("/categories", "", response -> {
            try {
                List<CategoryItem> categoryItems = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(response);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    CategoryItem item = new CategoryItem();
                    item.setCategoryId(obj.optString("categoryId", String.valueOf(i)));
                    item.setTitle(obj.optString("title", obj.optString("name", "Category " + i)));
                    categoryItems.add(item);
                }
                callback.onSuccess(categoryItems);
            } catch (JSONException e) {
                callback.onFailure("Parse categories failed: " + e.getMessage());
            }
        }, error -> callback.onFailure(error));
    }

    public void getCategoryFilter(String filterType, String category, int page,
            ApiCallback<List<MangaItem>> callback) {
        String params = buildKeyValue(
                "o", filterType != null ? filterType : "mr",
                "c", category != null ? category : "0",
                "page", String.valueOf(page));
        sendRequest("/categories/filter", params, response -> {
            try {
                List<MangaItem> mangaItems = parseMangaList(response);
                callback.onSuccess(mangaItems);
            } catch (JSONException e) {
                callback.onFailure("Parse filter failed: " + e.getMessage());
            }
        }, error -> callback.onFailure(error));
    }

    public void getSearch(String query, String orderBy, int page,
            ApiCallback<List<MangaItem>> callback) {
        String params = buildKeyValue(
                "search_query", query,
                "o", orderBy != null ? orderBy : "mr",
                "page", String.valueOf(page));
        sendRequest("/search", params, response -> {
            try {
                List<MangaItem> mangaItems = parseMangaList(response);
                callback.onSuccess(mangaItems);
            } catch (JSONException e) {
                callback.onFailure("Parse search failed: " + e.getMessage());
            }
        }, error -> callback.onFailure(error));
    }

    public void getAlbumDetail(String albumId,
            ApiCallback<String> callback) {
        sendRequest("/album/" + albumId, "", response -> {
            callback.onSuccess(response);
        }, error -> callback.onFailure(error));
    }

    public void getChapterDetail(String chapterId,
            ApiCallback<String> callback) {
        sendRequest("/chapter/" + chapterId, "", response -> {
            callback.onSuccess(response);
        }, error -> callback.onFailure(error));
    }

    private List<MangaItem> parseMangaList(String jsonStr) throws JSONException {
        List<MangaItem> mangaItems = new ArrayList<>();
        JSONArray jsonArray = new JSONArray(jsonStr);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            MangaItem item = new MangaItem();
            item.setAlbumId(obj.optString("albumId", String.valueOf(i)));
            item.setTitle(obj.optString("title", ""));
            item.setCoverUrl(obj.optString("coverUrl", obj.optString("cover", "")));
            item.setTags(obj.optString("tags", ""));
            item.setViews(obj.optString("views", ""));
            item.setCategory(obj.optString("category", ""));
            item.setSummary(obj.optString("summary", ""));
            mangaItems.add(item);
        }
        return mangaItems;
    }
}
