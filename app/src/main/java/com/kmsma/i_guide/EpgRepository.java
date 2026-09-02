package com.kmsma.i_guide;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for the channel line-up and its EPG, shared by every guide
 * screen. Loaded once per process; screens read from it synchronously and register a
 * listener to be told when a refresh lands.
 */
public final class EpgRepository {

    /** Ignore anything older than this when deciding what is "on now". */
    private static final long STALE_AFTER_MS = 30L * 60L * 1000L;

    public interface Listener {
        @MainThread
        void onEpgUpdated();

        @MainThread
        void onEpgFailed(Exception e);
    }

    private static EpgRepository instance;

    private final TunarrApiClient apiClient = new TunarrApiClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private List<Channel> channels = Collections.emptyList();
    private Map<String, List<Program>> programsByChannelId = Collections.emptyMap();

    private boolean channelsLoaded;
    private boolean epgLoaded;
    private boolean loading;

    private EpgRepository() {
    }

    public static synchronized EpgRepository get() {
        if (instance == null) {
            instance = new EpgRepository();
        }
        return instance;
    }

    // ---- Loading -----------------------------------------------------------

    /** Fetches the channel list and then the XMLTV feed. Safe to call repeatedly. */
    public void load() {
        if (loading) {
            return;
        }
        loading = true;
        apiClient.fetchChannels(new TunarrApiClient.ChannelListCallback() {
            @Override
            public void onSuccess(List<Channel> fetched) {
                main.post(() -> {
                    channels = fetched != null ? fetched : Collections.<Channel>emptyList();
                    channelsLoaded = true;
                    notifyUpdated();
                    loadEpg();
                });
            }

            @Override
            public void onFailure(Exception e) {
                main.post(() -> {
                    loading = false;
                    notifyFailed(e);
                });
            }
        });
    }

    private void loadEpg() {
        apiClient.fetchXmltv(new TunarrApiClient.XmltvCallback() {
            @Override
            public void onSuccess(XmltvParser.Result result) {
                final Map<String, List<Program>> mapped = mapToChannels(result);
                main.post(() -> {
                    programsByChannelId = mapped;
                    epgLoaded = true;
                    loading = false;
                    notifyUpdated();
                });
            }

            @Override
            public void onFailure(Exception e) {
                main.post(() -> {
                    loading = false;
                    notifyFailed(e);
                });
            }
        });
    }

    /**
     * Ties Tunarr's API channels to their XMLTV entries. The two id spaces are
     * unrelated, so this falls back through the aliases the feed publishes: the raw
     * id, the channel number, the name, and the "7 Adult Swim" number-and-name form.
     */
    private Map<String, List<Program>> mapToChannels(XmltvParser.Result result) {
        Map<String, List<Program>> out = new HashMap<>();
        Map<String, String> aliases = result.xmltvIdByAlias;

        for (Channel channel : channels) {
            String name = channel.getName() != null ? channel.getName() : "";
            String number = String.valueOf(channel.getNumber());

            String xmltvId = lookup(aliases, channel.getId());
            if (xmltvId == null) {
                xmltvId = lookup(aliases, number + " " + name);
            }
            if (xmltvId == null) {
                xmltvId = lookup(aliases, name);
            }
            if (xmltvId == null) {
                xmltvId = lookup(aliases, number);
            }

            List<Program> programs = xmltvId != null
                    ? result.programsByXmltvId.get(xmltvId)
                    : null;
            out.put(channel.getId(),
                    programs != null ? programs : Collections.<Program>emptyList());
        }
        return out;
    }

    @Nullable
    private static String lookup(Map<String, String> aliases, @Nullable String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return aliases.get(key.toLowerCase(Locale.US));
    }

    // ---- Listeners ---------------------------------------------------------

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    private void notifyUpdated() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onEpgUpdated();
        }
    }

    private void notifyFailed(Exception e) {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onEpgFailed(e);
        }
    }

    // ---- Reads -------------------------------------------------------------

    public boolean hasChannels() {
        return !channels.isEmpty();
    }

    public boolean isChannelsLoaded() {
        return channelsLoaded;
    }

    public boolean isEpgLoaded() {
        return epgLoaded;
    }

    @NonNull
    public List<Channel> getChannels() {
        return channels;
    }

    @Nullable
    public Channel channelById(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Channel c : channels) {
            if (id.equals(c.getId())) {
                return c;
            }
        }
        return null;
    }

    public int indexOfChannel(@Nullable String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < channels.size(); i++) {
            if (id.equals(channels.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    public List<Program> programsFor(@Nullable Channel channel) {
        if (channel == null) {
            return Collections.emptyList();
        }
        List<Program> list = programsByChannelId.get(channel.getId());
        return list != null ? list : Collections.emptyList();
    }

    /** The programme airing on {@code channel} at {@code timeMs}, if the EPG has one. */
    @Nullable
    public Program programAt(@Nullable Channel channel, long timeMs) {
        for (Program p : programsFor(channel)) {
            if (p.isLiveAt(timeMs)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    public Program currentProgram(@Nullable Channel channel) {
        return programAt(channel, System.currentTimeMillis());
    }

    /** The programme that follows whatever is on now, used by the Mini Guide. */
    @Nullable
    public Program nextProgram(@Nullable Channel channel) {
        long now = System.currentTimeMillis();
        for (Program p : programsFor(channel)) {
            if (p.getStartMs() > now) {
                return p;
            }
        }
        return null;
    }

    /**
     * Builds the cell run for one channel across a time window, inserting filler cells
     * wherever the EPG has a hole. Always returns at least one cell.
     */
    @NonNull
    public List<Program> programsInWindow(@Nullable Channel channel,
                                          long windowStart, long windowEnd,
                                          String fillerTitle) {
        List<Program> out = new ArrayList<>();
        long cursor = windowStart;

        for (Program p : programsFor(channel)) {
            if (!p.overlaps(windowStart, windowEnd)) {
                continue;
            }
            if (p.getStartMs() > cursor) {
                out.add(Program.filler(channelIdOf(channel), cursor,
                        Math.min(p.getStartMs(), windowEnd), fillerTitle));
            }
            out.add(p);
            cursor = Math.max(cursor, Math.min(p.getStopMs(), windowEnd));
            if (cursor >= windowEnd) {
                break;
            }
        }
        if (cursor < windowEnd) {
            out.add(Program.filler(channelIdOf(channel), cursor, windowEnd, fillerTitle));
        }
        return out;
    }

    private static String channelIdOf(@Nullable Channel channel) {
        return channel != null ? channel.getId() : "";
    }

    /**
     * The category a channel reads as, taken from whatever it is showing around now.
     * Used to colour-code rows when an individual programme has no category of its own.
     */
    @NonNull
    public ProgramCategory categoryOf(@Nullable Channel channel) {
        long now = System.currentTimeMillis();
        Program current = programAt(channel, now);
        if (current != null) {
            return current.getCategory();
        }
        for (Program p : programsFor(channel)) {
            if (p.getStopMs() > now - STALE_AFTER_MS) {
                return p.getCategory();
            }
        }
        return ProgramCategory.REGULAR;
    }
}
