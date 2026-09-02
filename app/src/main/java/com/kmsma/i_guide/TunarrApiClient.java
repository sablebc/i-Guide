package com.kmsma.i_guide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** OkHttp client for talking to the Tunarr backend over the LAN. */
public class TunarrApiClient {

    private static final String BASE_URL = "http://192.168.2.100:8010";

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public TunarrApiClient() {
        this.httpClient = new OkHttpClient();
    }

    /** Callback for the channel list fetch, invoked on a background thread. */
    public interface ChannelListCallback {
        void onSuccess(List<Channel> channels);

        void onFailure(Exception e);
    }

    public void fetchChannels(@NonNull ChannelListCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/channels")
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        callback.onFailure(new IOException("Unexpected response: " + resp.code()));
                        return;
                    }
                    String body = resp.body().string();
                    Type listType = new TypeToken<List<Channel>>() {}.getType();
                    List<Channel> channels = gson.fromJson(body, listType);
                    callback.onSuccess(channels);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    /** Builds the HLS stream URL for a given channel id. */
    @NonNull
    public static String buildStreamUrl(@Nullable String channelId) {
        return BASE_URL + "/stream/channels/" + channelId + "?streamMode=hls";
    }
}
