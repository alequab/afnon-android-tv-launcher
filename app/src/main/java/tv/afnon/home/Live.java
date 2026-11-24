package tv.afnon.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local only, not for upstream (its rules have no video and no network but the radio): the live video from
 * Makkah and Madinah for Home's panel. The source is Aloula (aloula.sba.sa), the Saudi Broadcasting Authority's
 * own platform: Makkah is its Quran channel (id 7, قناة القرآن الكريم), Madinah its Sunnah channel (id 6, قناة
 * السنة النبوية). H.264 and AAC over HLS, no DRM.
 *
 * <p>Each start asks Aloula's player API for a fresh address (its token is good for seconds, so it is never
 * kept), reads the renditions its master playlist offers, takes the saved quality (Prefs.liveQuality, 480 at
 * first, else the nearest below) and plays it with MediaPlayer on the panel's surface. The requests run on one
 * short-lived thread. Another quality plays on the same player from the rendition list already read.
 *
 * <p>Reconnecting: an error, the stream ending, a connection that doesn't play within {@link #CONNECT_MS} or a
 * picture stuck buffering for {@link #STALL_MS} starts again from a fresh master (the token may have run out), after
 * 2, 5 and 10 s; only when all three fail does it say so and stop. Playing again resets the count. Playing takes
 * the audio focus and stops the radio; losing the focus (the radio, the adhan, another app) stops it. Stopping
 * releases everything.
 */
final class Live {
    static final int MAKKAH = 0, MADINAH = 1;
    static final int OFF = 0, CONNECTING = 1, PLAYING = 2, FAILED = 3;

    /** Aloula's channel ids, in the order of MAKKAH, MADINAH. */
    private static final int[] CHANNELS = {7, 6};
    private static final String API = "https://aloula.faulio.com/api/v1.1/channels/";
    private static final String TAG = "AfnonLive";
    /** Waits before each new try, in order; then it fails. */
    private static final long[] RETRY_MS = {2000, 5000, 10000};
    /** A connection that hasn't started playing by then is tried again. */
    private static final long CONNECT_MS = 20_000;
    /** Buffering this long while playing counts as a dropped connection. */
    private static final long STALL_MS = 15_000;
    private static final Pattern BANDWIDTH = Pattern.compile("(?:^|[:,])BANDWIDTH=(\\d+)");
    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=\\d+x(\\d+)");
    /**
     * Aloula's CDN ties each rendition's token to the User-Agent that fetched the master playlist (another one
     * gets 403), so Afnon's fetches and MediaPlayer's own send the same: MediaPlayer's usual one.
     */
    private static final String USER_AGENT = "stagefright/1.2 (Linux;Android " + Build.VERSION.RELEASE + ")";

    /** One rendition of the stream: its picture height (0 if not given), bits per second and media playlist. */
    static final class Rendition {
        final int height;
        final long bandwidth;
        final String url;

        Rendition(int height, long bandwidth, String url) {
            this.height = height;
            this.bandwidth = bandwidth;
            this.url = url;
        }
    }

    interface Listener {
        void onLive(int state);
    }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Context app;
    private MediaPlayer player;
    private SurfaceHolder surface;
    private AudioFocusRequest focus;
    /** Bumped by every start, switch and stop, so a fetch or reset that comes back late is dropped. */
    private int generation;
    private int state = OFF, place = -1, channel;
    /** The renditions of this start's master playlist, by height, and the one playing. */
    private List<Rendition> renditions = Collections.emptyList();
    private int height;
    /** A quality switch is loading. */
    private boolean switching;
    /** Tries made since it last played (0 on a first connection). */
    private int retries;
    /** The connecting or buffering check now waiting; one at a time. */
    private Runnable watchdog;

    Live(Listener l) {
        listener = l;
    }

    int state() {
        return state;
    }

    /** MAKKAH or MADINAH while connecting, playing or failed; -1 when off. */
    int place() {
        return place;
    }

    /** What the master playlist offers (empty until it is read), lowest first. */
    List<Rendition> renditions() {
        return renditions;
    }

    /** The height of the rendition playing or loading. */
    int height() {
        return height;
    }

    /** Connecting again after a dropped connection (the panel says «جارٍ إعادة الاتصال…»). */
    boolean retrying() {
        return state == CONNECTING && retries > 0;
    }

    /** Stops whatever plays (the radio too) and starts the chosen one; the panel stays through a switch. */
    void start(Context c, int which) {
        release();
        app = c.getApplicationContext();
        place = which;
        channel = CHANNELS[which];
        renditions = Collections.emptyList();
        height = 0;
        switching = false;
        retries = 0;
        if (Radio.on()) Radio.stop();
        AudioManager am = app.getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener(this::onFocus, main)
            .build();
        if (am != null) am.requestAudioFocus(focus);
        set(CONNECTING);
        fetch(Prefs.liveQuality(app));
    }

    /** Stops and releases everything; the listener hears OFF. */
    void stop() {
        generation++;
        place = -1;
        release();
        if (state != OFF) set(OFF);
    }

    /**
     * Another quality (a picture height), saved for next time. While it plays, the same player moves to that
     * rendition of the list already read.
     */
    void setQuality(Context c, int h) {
        Prefs.setLiveQuality(c, h);
        if (player == null || renditions.isEmpty()) return;
        Rendition r = choose(renditions, h);
        if (r.height == height) return;
        height = r.height;
        switching = true;
        set(CONNECTING);
        load(r.url);
        watch(CONNECT_MS, "quality switch didn't play");
    }

    /** The panel's surface came (or came back after a rebuild). */
    void setSurface(SurfaceHolder h) {
        surface = h;
        if (player != null) player.setDisplay(h);
    }

    /** The panel's surface is going: the player lets go of it first. */
    void surfaceGone(SurfaceHolder h) {
        if (surface != h) return;
        surface = null;
        if (player != null) player.setDisplay(null);
    }

    // ---------------------------------------------------------------- playing

    /** Reads the master playlist off the main thread, then plays the rendition for quality. */
    private void fetch(int quality) {
        int gen = ++generation;
        int ch = channel;
        new Thread(() -> {
            List<Rendition> found = null;
            try {
                found = renditions(ch);
            } catch (Exception e) {
                Log.w(TAG, "channel " + ch + ": " + e);
            }
            List<Rendition> list = found;
            main.post(() -> {
                if (gen != generation) return;
                if (list == null || list.isEmpty()) {
                    retryOrFail("no playlist");
                    return;
                }
                renditions = list;
                Rendition r = choose(list, quality);
                height = r.height;
                Log.i(TAG, "channel " + ch + ": " + (r.height > 0 ? r.height + "p" : "its only rendition"));
                load(r.url);
                watch(CONNECT_MS, "didn't start playing");
            });
        }, "live-fetch").start();
    }

    /**
     * Plays url: a new player at first; after that the same one, reset off the main thread (reset() waits for a
     * stream that is still connecting, as Radio's does) and loaded again.
     */
    private void load(String url) {
        if (player == null) {
            player = newPlayer();
            prepare(player, url);
            return;
        }
        MediaPlayer p = player;
        int gen = ++generation;
        new Thread(() -> {
            try {
                p.reset();
            } catch (RuntimeException ignored) {
                // Released meanwhile; the check below drops it.
            }
            main.post(() -> { if (gen == generation && p == player) prepare(p, url); });
        }, "live-switch").start();
    }

    private MediaPlayer newPlayer() {
        MediaPlayer mp = new MediaPlayer();
        mp.setOnPreparedListener(p -> {
            if (p != player) return;
            p.start();
            switching = false;
            retries = 0;
            unwatch();
            set(PLAYING);
        });
        mp.setOnErrorListener((p, what, extra) -> {
            if (p == player) retryOrFail("MediaPlayer error " + what + "/" + extra + (switching ? " after a quality switch" : ""));
            return true;
        });
        // A live stream never completes; if it does, the connection dropped.
        mp.setOnCompletionListener(p -> {
            if (p == player) retryOrFail("stream ended");
        });
        // Stuck buffering while playing (the picture frozen): after a while, connect again.
        mp.setOnInfoListener((p, what, extra) -> {
            if (p != player) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START && state == PLAYING) watch(STALL_MS, "stuck buffering");
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END && state == PLAYING) unwatch();
            return false;
        });
        return mp;
    }

    private void prepare(MediaPlayer p, String url) {
        try {
            p.setAudioAttributes(attributes());
            if (surface != null) p.setDisplay(surface);
            p.setDataSource(app, Uri.parse(url), Collections.singletonMap("User-Agent", USER_AGENT));
            p.prepareAsync();
        } catch (Exception e) {
            retryOrFail("setDataSource: " + e);
        }
    }

    /**
     * The connection dropped or never came: the player goes, and after the next wait in {@link #RETRY_MS} a fresh
     * master is fetched for the same place and quality (its token may have run out). Past the last wait it fails.
     * The audio focus is kept meanwhile, so nothing else starts playing in the gap.
     */
    private void retryOrFail(String why) {
        unwatch();
        if (place < 0) return;
        if (retries >= RETRY_MS.length) {
            Log.w(TAG, why + ": giving up after " + retries + " tries");
            fail();
            return;
        }
        long wait = RETRY_MS[retries++];
        Log.w(TAG, why + ": trying again in " + wait / 1000 + " s (" + retries + "/" + RETRY_MS.length + ")");
        int gen = ++generation;
        releasePlayer();
        switching = false;
        set(CONNECTING);
        int quality = height > 0 ? height : Prefs.liveQuality(app);
        main.postDelayed(() -> { if (gen == generation && place >= 0) fetch(quality); }, wait);
    }

    /** After {@code ms}, unless something else happened first, it counts as a dropped connection. */
    private void watch(long ms, String why) {
        unwatch();
        int gen = generation;
        watchdog = () -> {
            watchdog = null;
            if (gen == generation && player != null) retryOrFail(why);
        };
        main.postDelayed(watchdog, ms);
    }

    private void unwatch() {
        if (watchdog != null) main.removeCallbacks(watchdog);
        watchdog = null;
    }

    /** Couldn't play, even after the retries: everything is released, and the panel says so (OK tries again). */
    private void fail() {
        unwatch();
        generation++;
        release();
        set(FAILED);
    }

    private void release() {
        unwatch();
        releasePlayer();
        if (focus != null && app != null) {
            AudioManager am = app.getSystemService(AudioManager.class);
            if (am != null) am.abandonAudioFocusRequest(focus);
        }
        focus = null;
    }

    /** The player only (the audio focus stays): for a retry, and as part of release. */
    private void releasePlayer() {
        if (player != null) {
            MediaPlayer p = player;
            player = null;
            // Let go of the surface now, so a new player (the next start) can take it.
            try {
                p.setDisplay(null);
            } catch (RuntimeException ignored) {
                // Its surface is already gone.
            }
            // reset() waits for a stream that is still connecting, so it runs off the main thread (as Radio's).
            new Thread(() -> {
                try {
                    p.reset();
                    p.release();
                } catch (RuntimeException ignored) {
                    // Already gone.
                }
            }, "live-release").start();
        }
    }

    /** The radio, the call to prayer or another app took the sound: one audio at a time, so it stops. */
    private void onFocus(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop();
    }

    private void set(int s) {
        state = s;
        listener.onLive(s);
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build();
    }

    /** The rendition of that height, or the nearest below it, or the lowest there is. */
    static Rendition choose(List<Rendition> list, int quality) {
        Rendition pick = list.get(0);
        for (Rendition r : list) if (r.height <= quality && r.height > pick.height) pick = r;
        return pick;
    }

    // ---------------------------------------------------------------- the renditions (off the main thread)

    /** Aloula's fresh master playlist and the renditions it lists, lowest first. */
    private static List<Rendition> renditions(int channel) throws Exception {
        String master = new JSONObject(get(API + channel + "/player")).getJSONObject("streams").getString("hls");
        String[] lines = get(master).split("\n");
        List<Rendition> out = new ArrayList<>();
        for (int i = 0; i + 1 < lines.length; i++) {
            String info = lines[i].trim();
            String uri = lines[i + 1].trim();
            if (!info.startsWith("#EXT-X-STREAM-INF:") || uri.isEmpty() || uri.startsWith("#")) continue;
            Matcher r = RESOLUTION.matcher(info);
            Matcher b = BANDWIDTH.matcher(info);
            int h = r.find() ? Integer.parseInt(r.group(1)) : 0;
            long bandwidth = b.find() ? Long.parseLong(b.group(1)) : 0;
            out.add(new Rendition(h, bandwidth, new URL(new URL(master), uri).toString()));
        }
        if (out.isEmpty()) return Collections.singletonList(new Rendition(0, 0, master));   // a media playlist
        Collections.sort(out, (x, y) -> Integer.compare(x.height, y.height));
        // Aloula's renditions are six-hour DVR windows: playlists of ~3600 segments (400 KB) that MediaPlayer reads
        // again every few seconds. The same renditions' live lists have the last few, far lighter on this TV; one
        // is tried, and if it is missing the DVR ones stay.
        String dvr = "/chunks_dvr.m3u8", live = "/chunks.m3u8";
        String first = out.get(0).url;
        if (first.contains(dvr)) {
            try {
                if (get(first.replace(dvr, live)).startsWith("#EXTM3U")) {
                    List<Rendition> lists = new ArrayList<>();
                    for (Rendition x : out) lists.add(new Rendition(x.height, x.bandwidth, x.url.replace(dvr, live)));
                    return lists;
                }
            } catch (IOException e) {
                Log.i(TAG, "no live lists, using the DVR ones: " + e);
            }
        }
        return out;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > 1_000_000) throw new IOException("too long");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
