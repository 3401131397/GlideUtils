package com.catpaw.mangareader.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.catpaw.mangareader.model.CategoryItem;
import com.catpaw.mangareader.model.MangaItem;
import com.catpaw.mangareader.model.TagItem;

public class JMApiClient {

    private static final String BASE_URL = "https://www.cdnggc.cc";
    private static final String TOKEN_SECRET = "0WyJFBix1e6c7TqSg7XgDliOvQy3lASQ";
    private static final String KEY_SECRET = "nNbsEgIn8uIItm9d";
    private static final String IV_SECRET = "dygIVvYyTtpzc8wm";
    private static final String VERSION = "1.1.2";

    private static JMApiClient instance;
    private final Handler mainHandler;

    private JMApiClient() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized JMApiClient getInstance() {
        if (instance == null) {
            instance = new JMApiClient();
        }
        return instance;
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(String error);
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
            e.printStackTrace();
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
            e.printStackTrace();
            return null;
        }
    }

    private void sendRequest(String path, String params, ApiCallback<String> callback) {
        new Thread(() -> {
            try {
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                String token = generateToken(timestamp);

                String urlWithParams = BASE_URL + path + "?token=" + token + "&t=" + timestamp + "&" + params;

                URL url = new URL(urlWithParams);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "okhttp/4.9.3");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    try {
                        JSONObject jsonObject = new JSONObject(response.toString());
                        int code = jsonObject.getInt("code");
                        if (code == 200) {
                            String encryptedData = jsonObject.getString("data");
                            String decrypted = decrypt(encryptedData, timestamp);
                            if (decrypted != null) {
                                mainHandler.post(() -> callback.onSuccess(decrypted));
                            } else {
                                mainHandler.post(() -> callback.onFailure("Decryption failed"));
                            }
                        } else {
                            mainHandler.post(() -> callback.onFailure("API error code: " + code));
                        }
                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onFailure("JSON parse error: " + e.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> callback.onFailure("HTTP error: " + responseCode));
                }
                connection.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }
        }).start();
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
        sendRequest("/manga/filter/build", "", response -> {
            try {
                List<CategoryItem> categoryItems = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(response);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    CategoryItem item = new CategoryItem();
                    item.setCategoryId(obj.optString("categoryId", ""));
                    item.setTitle(obj.optString("title", ""));
                    categoryItems.add(item);
                }
                callback.onSuccess(categoryItems);
            } catch (JSONException e) {
                callback.onFailure("Parse categories failed: " + e.getMessage());
            }
        });
    }

    public void getSearchQueries(String categoryFilter, ApiCallback<List<String>> callback) {
        String params = buildKeyValue("filter", categoryFilter);
        sendRequest("/manga/search/params", params, response -> {
            try {
                List<String> queries = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(response);
                for (int i = 0; i < jsonArray.length(); i++) {
                    queries.add(jsonArray.getString(i));
                }
                callback.onSuccess(queries);
            } catch (JSONException e) {
                callback.onFailure("Parse search queries failed: " + e.getMessage());
            }
        });
    }

    public void getSearch(String query, ApiCallback<List<MangaItem>> callback) {
        String params = buildKeyValue("query", query);
        sendRequest("/manga/search", params, response -> {
            try {
                List<MangaItem> mangaItems = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(response);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    MangaItem item = new MangaItem();
                    item.setAlbumId(obj.optString("albumId", ""));
                    item.setTitle(obj.optString("title", ""));
                    item.setCoverUrl(obj.optString("coverUrl", ""));
                    item.setTags(obj.optString("tags", ""));
                    item.setViews(obj.optString("views", ""));
                    item.setCategory(obj.optString("category", ""));
                    item.setSummary(obj.optString("summary", ""));
                    mangaItems.add(item);
                }
                callback.onSuccess(mangaItems);
            } catch (JSONException e) {
                callback.onFailure("Parse search results failed: " + e.getMessage());
            }
        });
    }

    public void getTagGroups(ApiCallback<List<TagItem>> callback) {
        sendRequest("/manga/tag/groups", "", response -> {
            try {
                List<TagItem> tagItems = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(response);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    TagItem item = new TagItem();
                    item.setTagName(obj.optString("tagName", ""));
                    item.setTagGroup(obj.optString("tagGroup", ""));
                    tagItems.add(item);
                }
                callback.onSuccess(tagItems);
            } catch (JSONException e) {
                callback.onFailure("Parse tag groups failed: " + e.getMessage());
            }
        });
    }

    public void getFilter(String filter, ApiCallback<List<Object>> callback) {
        String params = buildKeyValue("filter", filter);
        sendRequest("/manga/filter", params, response -> {
            try {
                List<Object> results = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(response);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    if (obj.has("tagName")) {
                        TagItem tag = new TagItem();
                        tag.setTagName(obj.optString("tagName", ""));
                        tag.setTagGroup(obj.optString("tagGroup", ""));
                        results.add(tag);
                    } else {
                        MangaItem item = new MangaItem();
                        item.setAlbumId(obj.optString("albumId", ""));
                        item.setTitle(obj.optString("title", ""));
                        item.setCoverUrl(obj.optString("coverUrl", ""));
                        item.setTags(obj.optString("tags", ""));
                        item.setViews(obj.optString("views", ""));
                        item.setCategory(obj.optString("category", ""));
                        item.setSummary(obj.optString("summary", ""));
                        results.add(item);
                    }
                }
                callback.onSuccess(results);
            } catch (JSONException e) {
                callback.onFailure("Parse filter failed: " + e.getMessage());
            }
        });
    }
}
