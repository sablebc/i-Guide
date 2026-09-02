package com.kmsma.i_guide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reads series/movie metadata from Jellyfin so the Program Information screen can show
 * a library summary alongside the EPG data.
 *
 * <p>No credentials are configured yet, so requests go out unauthenticated and are
 * expected to fail on a locked-down server. Every call site treats a failure as "no
 * Jellyfin data" and hides the line rather than surfacing an error. Call
 * {@link #setAccessToken(String)} once a token is available.
 */
public class JellyfinClient {

    private static final String BASE_URL = "http://192.168.2.100:8096";

    private static String accessToken;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    /** Sets the {@code X-Emby-Token} sent with subsequent requests. */
    public static void setAccessToken(@Nullable String token) {
        accessToken = token;
    }

    /** Library counts for one title, as shown under the program synopsis. */
    public static class LibraryInfo {
        public final String name;
        public final int seasonCount;
        public final int episodeCount;
        public final int unwatchedCount;

        LibraryInfo(String name, int seasonCount, int episodeCount, int unwatchedCount) {
            this.name = name;
            this.seasonCount = seasonCount;
            this.episodeCount = episodeCount;
            this.unwatchedCount = unwatchedCount;
        }

        /** {@code 12 seasons — 264 episodes — 23 unwatched}, omitting empty parts. */
        @NonNull
        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (seasonCount > 0) {
                sb.append(seasonCount).append(seasonCount == 1 ? " season" : " seasons");
            }
            if (episodeCount > 0) {
                if (sb.length() > 0) {
                    sb.append(" — ");
                }
                sb.append(episodeCount).append(episodeCount == 1 ? " episode" : " episodes");
            }
            if (unwatchedCount > 0) {
                if (sb.length() > 0) {
                    sb.append(" — ");
                }
                sb.append(unwatchedCount).append(" unwatched");
            }
            return sb.toString();
        }
    }

    public interface LibraryInfoCallback {
        void onResult(@Nullable LibraryInfo info);
    }

    /**
     * Looks up a title by name and reports its library counts, or {@code null} when
     * Jellyfin is unreachable, unauthenticated, or has no match.
     */
    public void fetchLibraryInfo(@Nullable String title, @NonNull LibraryInfoCallback callback) {
        if (title == null || title.trim().isEmpty()) {
            callback.onResult(null);
            return;
        }
        String url;
        try {
            url = BASE_URL + "/Items"
                    + "?searchTerm=" + URLEncoder.encode(title.trim(), "UTF-8")
                    + "&IncludeItemTypes=Series,Movie"
                    + "&Recursive=true"
                    + "&Fields=ChildCount,RecursiveItemCount,UserData"
                    + "&Limit=1";
        } catch (IOException e) {
            callback.onResult(null);
            return;
        }

        Request.Builder builder = new Request.Builder().url(url).get();
        if (accessToken != null && !accessToken.isEmpty()) {
            builder.header("X-Emby-Token", accessToken);
        }

        httpClient.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        callback.onResult(null);
                        return;
                    }
                    ItemsResponse parsed = gson.fromJson(resp.body().string(), ItemsResponse.class);
                    if (parsed == null || parsed.items == null || parsed.items.isEmpty()) {
                        callback.onResult(null);
                        return;
                    }
                    Item item = parsed.items.get(0);
                    int unwatched = item.userData != null ? item.userData.unplayedItemCount : 0;
                    callback.onResult(new LibraryInfo(item.name, item.childCount,
                            item.recursiveItemCount, unwatched));
                } catch (Exception e) {
                    callback.onResult(null);
                }
            }
        });
    }

    /** Primary-image URL for a Jellyfin item, sized for the guide. */
    @NonNull
    public static String imageUrl(@NonNull String itemId, int maxHeightPx) {
        return String.format(Locale.US, "%s/Items/%s/Images/Primary?maxHeight=%d",
                BASE_URL, itemId, maxHeightPx);
    }

    // ---- Wire format -------------------------------------------------------

    private static class ItemsResponse {
        @SerializedName("Items")
        List<Item> items;
    }

    private static class Item {
        @SerializedName("Name")
        String name;

        @SerializedName("ChildCount")
        int childCount;

        @SerializedName("RecursiveItemCount")
        int recursiveItemCount;

        @SerializedName("UserData")
        UserData userData;
    }

    private static class UserData {
        @SerializedName("UnplayedItemCount")
        int unplayedItemCount;
    }
}
