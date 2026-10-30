package tv.afnon.home;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.Date;

/**
 * Settings → Display → Open Afnon when the TV turns on (on by default). The TCL goes back to the last app when it
 * leaves standby; this brings Home, with the next prayer, to the front instead, as cleanyourtv's "Open on wake"
 * add-on does for an app. Run by the remote buttons service, which is always on and may open screens: SCREEN_ON
 * only reaches receivers registered at run time, so no new service. It waits a moment for the TV to restore its
 * app, then presses Home; nothing if the screen went off again meanwhile, not twice within a few seconds, and never
 * over the call to prayer.
 */
final class HomeOnWake extends BroadcastReceiver {
    private static final String TAG = "AfnonHomeOnWake";
    /** The TV puts its last app back first; Home after it, so it stays in front. */
    private static final long WAIT_MS = 2500;
    private static final long AGAIN_MS = 5000;

    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long last;

    private final Runnable home = this::home;

    HomeOnWake(AccessibilityService service) {
        this.service = service;
    }

    void start() {
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        service.registerReceiver(this, f);
    }

    void stop() {
        handler.removeCallbacks(home);
        try {
            service.unregisterReceiver(this);
        } catch (IllegalArgumentException ignored) {
            // Never registered.
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        handler.removeCallbacks(home);
        if (!Intent.ACTION_SCREEN_ON.equals(i.getAction())) return;
        if (SystemClock.uptimeMillis() - last < AGAIN_MS || !Prefs.homeOnWake(service)) return;
        handler.postDelayed(home, WAIT_MS);
    }

    private void home() {
        PowerManager pm = service.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return;
        // The call to prayer may be what turned the screen on, or be about to open: leave it the screen.
        Date now = new Date();
        if (Reminders.dueCall(service, new PrayerDay(service, now), now) >= 0) return;
        last = SystemClock.uptimeMillis();
        boolean done = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        Log.i(TAG, done ? "screen on: Home" : "screen on: Home refused");
    }
}
