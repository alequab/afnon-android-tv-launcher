package tv.afnon.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/**
 * Watches the clock from the remote buttons service, which the system keeps running whatever app is in front:
 * the reminder before each prayer (Home's own card, or a banner over other apps) and the call screen when the
 * time begins. The banner is an accessibility overlay, so it needs no overlay permission; it never takes focus
 * or touches and hides after 12 s (design/ApproachVideo.dc.html).
 */
final class PrayerWatch {
    private static final long CHECK_MS = 15_000;
    private static final int BANNER_SECONDS = 12;

    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /**
     * "prayer index:time" of the last reminder, call and spoken announcement, so each happens once. Saved, because
     * the system restarts this service (low memory, TCL's background manager) and a fresh one would repeat them.
     */
    private String reminded, called, announced, ending, fasted;
    /** The reminder minutes the keys above were given with; -1 before the first check. */
    private int remindMinutes = -1;
    private View banner;

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, CHECK_MS);
        }
    };

    PrayerWatch(AccessibilityService service) {
        this.service = service;
        reminded = Prefs.of(service).getString("reminded", "");
        called = Prefs.of(service).getString("called", "");
        announced = Prefs.of(service).getString("announced", "");
        ending = Prefs.of(service).getString("ending", "");
        fasted = Prefs.of(service).getString("fasted", "");
    }

    void start() {
        handler.removeCallbacks(check);
        handler.post(check);
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        hideBanner();
    }

    private void check() {
        PowerManager pm = service.getSystemService(PowerManager.class);
        Date now = new Date();
        boolean asleep = pm != null && !pm.isInteractive();
        // Asleep: nothing to show, unless the radio plays with the screen off (TCL Audio Only); then the call
        // still comes, waking the screen.
        if (asleep && !Radio.on()) return;
        PrayerDay day = new PrayerDay(service, now);
        rearm(day, now);

        int call = Reminders.dueCall(service, day, now);
        if (call >= 0) {
            // While Afnon's screensaver runs it wakes itself for the call; open the screen once it has gone.
            if (!AfnonDream.running) callOnce(call, day.times[call], false);
            return;
        }
        // No call for a prayer whose adhan is off: just say that its time has begun (also with the screen off).
        int quiet = Reminders.dueAnnounce(service, day, now);
        if (quiet >= 0) {
            String key = quiet + ":" + day.times[quiet].getTime();
            if (!key.equals(announced)) {
                announced = key;
                Prefs.of(service).edit().putString("announced", key).apply();
                Announce.now(service, quiet, day.friday);
            }
        }
        if (asleep) return;
        // «أوشك…»: the current prayer's time is ending, as long before its end as the reminder comes before a
        // prayer. Fajr ends at sunrise; Dhuhr, Asr and Maghrib at the next prayer; Isha at Fajr.
        int current = day.nowIndex;
        if (current >= 0 && Prefs.remindVoice(service)) {
            Date end = current == PrayerDay.FAJR ? day.times[PrayerDay.SUNRISE] : day.nextTime;
            long left = Screen.secondsUntil(end, now);
            int minutes = Prefs.remindMinutes(service);
            String key = current + ":" + end.getTime();
            if (minutes > 0 && left > 0 && left <= minutes * 60L && !key.equals(ending)) {
                ending = key;
                Prefs.of(service).edit().putString("ending", key).apply();
                Announce.ending(service, current, day.friday);
            }
        }
        // A prayer's own reminder comes first; the fast's waits for the next check.
        if (!Reminders.dueReminder(service, day, now)) checkFast(day, now);
        if (Reminders.dueReminder(service, day, now)) {
            String key = day.nextIndex + ":" + day.nextTime.getTime();
            if (key.equals(reminded) || AfnonDream.running) return;
            reminded = key;
            Prefs.of(service).edit().putString("reminded", key).apply();
            if (Reminders.home != null) Reminders.home.showReminder();
            else showBanner(day, now);
            if (Prefs.remindVoice(service)) Announce.before(service, day.nextIndex, day.friday);
        }
    }

    /** The evening before a voluntary fast (see {@link Fasts}): the banner, once for that Hijri date. */
    private void checkFast(PrayerDay day, Date now) {
        if (banner != null || AfnonDream.running) return;
        int kind = Fasts.due(service, day, now);
        if (kind < 0) return;
        String key = day.hijriYear + "-" + day.hijriMonth + "-" + day.hijriDay;
        if (key.equals(fasted)) return;
        fasted = key;
        Prefs.of(service).edit().putString("fasted", key).apply();
        showFast(Screen.loadText(service), kind, day.hijriDay, day.hijriMonth, day.suhoorEnd);
    }

    /**
     * The reminder minutes changed (Settings → Reminders): a reminder or «أوشك» given with the old minutes that is not
     * due with the new ones comes again when it is (10 → 5 at 8 minutes left: once more at 5). One that is still due
     * stays given, so it isn't said twice.
     */
    private void rearm(PrayerDay day, Date now) {
        int minutes = Prefs.remindMinutes(service);
        boolean changed = remindMinutes >= 0 && minutes != remindMinutes;
        remindMinutes = minutes;
        if (!changed) return;
        if (!Reminders.dueReminder(service, day, now)) {
            reminded = "";
            Prefs.of(service).edit().putString("reminded", "").apply();
        }
        int current = day.nowIndex;
        Date end = current < 0 ? null : current == PrayerDay.FAJR ? day.times[PrayerDay.SUNRISE] : day.nextTime;
        long left = end == null ? 0 : Screen.secondsUntil(end, now);
        if (minutes <= 0 || left <= 0 || left > minutes * 60L) {
            ending = "";
            Prefs.of(service).edit().putString("ending", "").apply();
        }
    }

    /**
     * The call for this prayer time, unless it has been shown already: from this check, or from {@link Wake}'s
     * alarm with the TV in standby (woke), which shares the "called" key so the two never both open it.
     */
    void callOnce(int prayer, Date time, boolean woke) {
        String key = prayer + ":" + time.getTime();
        if (key.equals(called)) return;
        called = key;
        Prefs.of(service).edit().putString("called", key).apply();
        openCall(prayer, time, woke);
    }

    /** woke: Afnon turned the TV on for it, so the call screen puts it back to standby when it closes. */
    void openCall(int prayer, Date time, boolean woke) {
        hideBanner();
        wakeScreen();
        Intent i = new Intent(service, NowActivity.class)
            .putExtra("prayer", prayer)
            .putExtra("time", time.getTime())
            .putExtra("woke", woke)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            service.startActivity(i);
        } catch (RuntimeException ignored) {
            // Blocked on this firmware; the reminder and Home still show the time.
        }
    }

    /** Turns the screen back on (from TCL Audio Only or sleep) so the call can be seen; a no-op when it is on. */
    private void wakeScreen() {
        PowerManager pm = service.getSystemService(PowerManager.class);
        if (pm == null || pm.isInteractive()) return;
        try {
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "afnon:call");
            wl.acquire(5000);
        } catch (RuntimeException ignored) {
            // No wake lock; the call opens when the screen next comes on.
        }
    }

    /**
     * Back to standby after a call Afnon woke the TV for: Android's lock-screen action, which also turns the screen
     * off (Android 9+). False where there is none; the TV then stays on.
     */
    boolean sleep() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    }

    // ---------------------------------------------------------------- banner

    void showBanner(PrayerDay day, Date now) {
        hideBanner();
        Ui.init(service);
        Text t = Screen.loadText(service);
        // Over other apps too, the banner takes the palette of Settings → Display → Night mode.
        Tokens.use(Prefs.night(service));
        boolean h24 = Prefs.clock24(service);
        String[] names = t.prayerNames();
        if (day.friday) names[PrayerDay.DHUHR] = t.jumuah();
        long minutes = Reminders.minutesLeft(day, now);
        // The same look as the fast banner: the city at the start of the last row, the adhan time as a gold pill.
        Typeface ui = service.getResources().getFont(R.font.alexandria_regular);
        View last = lastRow(ui, t.city(), t.adhanPill(Screen.clock(t, day.nextTime, h24, 1f)), 16);
        showCard(t, Icon.MIHRAB, 56, 72, 44, t.apLabel(),
            t.apTitle(names[day.nextIndex], minutes, Screen.duration(Screen.secondsUntil(day.nextTime, now), false)),
            null, last, Tokens.GOLD_400, 2, BANNER_SECONDS * 1000L);
    }

    /** A banner's last row: quiet words at the start, a gold pill at the end. */
    private LinearLayout lastRow(Typeface ui, CharSequence start, CharSequence pillText, int top) {
        LinearLayout last = new LinearLayout(service);
        last.setGravity(Gravity.CENTER_VERTICAL);
        last.addView(text(ui, 20, Tokens.MIST_400, start), new LinearLayout.LayoutParams(0, -2, 1));
        TextView pill = text(ui, 20, Tokens.GOLD_400, pillText);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Tokens.LAPIS_800);
        pbg.setCornerRadius(Ui.px(999));
        pbg.setStroke(Ui.ipx(1), Tokens.GOLD_400_60);
        pill.setBackground(pbg);
        pill.setPadding(Ui.ipx(16), Ui.ipx(6), Ui.ipx(16), Ui.ipx(6));
        last.addView(pill);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = Ui.ipx(top);
        last.setLayoutParams(llp);
        return last;
    }

    /**
     * A voluntary fast tomorrow (see {@link Fasts}): its own icon, the day among its days as dots, the hadith on its
     * virtue with the source, and the fast's date with suhoor's end as a gold pill. Monday and Thursday are quiet:
     * smaller, shorter, title and date only. {@code hijriDay}/{@code hijriMonth} are the fast's; suhoor is its Fajr.
     */
    void showFast(Text t, int kind, int hijriDay, int hijriMonth, Date suhoor) {
        hideBanner();
        Ui.init(service);
        Tokens.use(Prefs.night(service));
        boolean quiet = Fasts.quiet(kind);
        Typeface ui = service.getResources().getFont(R.font.alexandria_regular);

        // Under the title: the detail line, then the dots (tomorrow's filled, the rest hollow).
        LinearLayout extra = new LinearLayout(service);
        extra.setOrientation(LinearLayout.VERTICAL);
        String detail = t.fastDetail(kind, hijriDay);
        // Its own width: a vertical row's default (match_parent) would squeeze it to the dots' width and cut it off.
        if (detail != null) extra.addView(text(ui, 22, Tokens.MIST_400, detail), new LinearLayout.LayoutParams(-2, -2));
        int[] dots = Fasts.dots(kind, hijriDay);
        if (dots != null) {
            LinearLayout row = new LinearLayout(service);
            for (int i = 0; i < dots[0]; i++) {
                View dot = new View(service);
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                if (i == dots[1]) d.setColor(Tokens.GOLD_400);
                else d.setStroke(Ui.ipx(2), Tokens.GOLD_400_60);
                dot.setBackground(d);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.ipx(12), Ui.ipx(12));
                if (i > 0) dlp.setMarginStart(Ui.ipx(8));
                row.addView(dot, dlp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
            rlp.topMargin = Ui.ipx(detail != null ? 10 : 6);
            extra.addView(row, rlp);
        }

        LinearLayout below = new LinearLayout(service);
        below.setOrientation(LinearLayout.VERTICAL);
        String virtue = quiet ? null : t.fastVirtue(kind);
        if (virtue != null) {
            // The hadith in Amiri, a hairline above it, its source under it.
            View rule = new View(service);
            rule.setBackgroundColor(Tokens.GOLD_400_60);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, Ui.ipx(1));
            hlp.topMargin = Ui.ipx(18);
            below.addView(rule, hlp);
            Typeface quote = service.getResources().getFont(t.ar ? R.font.amiri_regular : R.font.alexandria_light);
            TextView v = text(quote, t.ar ? 26 : 20, Tokens.PLASTER_100, virtue);
            Screen.lineHeightPx(v, Ui.ipx((t.ar ? 26 : 20) * 1.5f));
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
            vlp.topMargin = Ui.ipx(14);
            below.addView(v, vlp);
            TextView src = text(ui, 18, Tokens.MIST_400, t.fastSource(kind));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.ipx(4);
            below.addView(src, slp);
        }
        // Last row: the fast's date at the start, suhoor's end as a pill at the end.
        below.addView(lastRow(ui, t.fastDate(hijriDay, hijriMonth),
            t.suhoorEnds(Screen.clock(t, suhoor, Prefs.clock24(service), 1f).toString()), virtue != null ? 14 : 16));

        // Arafah, the day of the year, has a brighter, thicker border.
        boolean arafah = kind == Fasts.ARAFAH;
        // Smaller in English, where these titles would wrap and cover the arch; the quiet ones smaller still.
        int titleSize = quiet ? 34 : t.ar ? 44 : 38;
        showCard(t, Fasts.icon(kind), quiet ? 52 : 64, quiet ? 52 : 64, titleSize, t.fastLabel(),
            t.fastTitle(kind, hijriDay), extra.getChildCount() > 0 ? extra : null, below,
            arafah ? Tokens.GOLD_200 : Tokens.GOLD_400, arafah ? 3 : 2,
            quiet ? Fasts.QUIET_BANNER_MS : Fasts.BANNER_MS);
    }

    private final Runnable hide = this::hideBanner;

    /**
     * The banner: icon and words in a row (label, title, then {@code sub}, a line of text or views), anything in
     * {@code below} under that row, and a bar emptying over {@code lifeMs}. It slides down a little as it fades in.
     */
    private void showCard(Text t, int iconKind, int iconW, int iconH, int titleSize, CharSequence labelText,
                          CharSequence titleText, Object sub, View below, int border, int borderWidth, long lifeMs) {
        Typeface ui = service.getResources().getFont(R.font.alexandria_regular);
        Typeface display = service.getResources().getFont(t.ar ? R.font.amiri_bold : R.font.marcellus_regular);

        LinearLayout card = new LinearLayout(service);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(t.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        card.setPadding(Ui.ipx(28), Ui.ipx(24), Ui.ipx(28), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.LAPIS_900);
        bg.setCornerRadius(Ui.px(24));
        bg.setStroke(Ui.ipx(borderWidth), border);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setClipToPadding(false);

        LinearLayout row = new LinearLayout(service);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Icon icon = new Icon(service, iconKind, 1.2f);
        icon.setColor(Tokens.GOLD_400);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.ipx(iconW), Ui.ipx(iconH)));
        LinearLayout words = new LinearLayout(service);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(ui, 20, Tokens.MIST_400, labelText);
        if (!t.ar) label.setLetterSpacing(0.2f);
        TextView title = text(display, titleSize, Tokens.PLASTER_100, titleText);
        words.addView(label);
        words.addView(title);
        Screen.lineHeight(title, 1.1f);
        // Room for Amiri's marks above the letters (غدًا, تبدأ), which otherwise touch the label.
        ((LinearLayout.LayoutParams) title.getLayoutParams()).topMargin = Ui.ipx(t.ar ? 8 : 0);
        if (sub != null) {
            View s = sub instanceof View ? (View) sub : text(ui, 22, Tokens.MIST_400, (CharSequence) sub);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.ipx(4);
            words.addView(s, slp);
        }
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1);
        wlp.setMarginStart(Ui.ipx(20));
        row.addView(words, wlp);
        card.addView(row);
        if (below != null) {
            if (below.getLayoutParams() == null) below.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            card.addView(below);
        }

        // The time left before it hides, emptying toward the start side.
        FrameLayout track = new FrameLayout(service);
        track.setBackgroundColor(Tokens.BANNER_TRACK);
        View fill = new View(service);
        fill.setBackgroundColor(Tokens.GOLD_400);
        track.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Ui.ipx(6));
        tlp.topMargin = Ui.ipx(18);
        tlp.setMarginStart(-Ui.ipx(28));
        tlp.setMarginEnd(-Ui.ipx(28));
        card.addView(track, tlp);

        // The window is a little taller than the card, so the card can slide inside it without being cut off.
        int slide = Ui.ipx(16);
        FrameLayout root = new FrameLayout(service);
        root.setPadding(0, slide, 0, 0);
        root.setClipChildren(false);
        root.addView(card, new FrameLayout.LayoutParams(-1, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            Ui.ipx(640), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        // Top end: the left in Arabic, the right in English.
        lp.gravity = Gravity.TOP | (t.ar ? Gravity.LEFT : Gravity.RIGHT);
        lp.x = Ui.ipx(96);
        lp.y = Ui.ipx(56) - slide;
        WindowManager wm = service.getSystemService(WindowManager.class);
        try {
            wm.addView(root, lp);
        } catch (RuntimeException e) {
            return;
        }
        banner = root;
        // Transform and opacity only. With the TV's animations off (TCL), it simply appears.
        card.setAlpha(0f);
        card.setTranslationY(-slide);
        card.animate().alpha(1f).translationY(0f).setDuration(Tokens.MOTION_OVERLAY_FADE_MS).start();
        fill.post(() -> {
            fill.setPivotX(t.ar ? fill.getWidth() : 0);
            fill.animate().scaleX(0f).setDuration(lifeMs).setInterpolator(null).start();
        });
        // A newer banner replaces an older one; the older one's timer must not hide it.
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, lifeMs);
    }

    void hideBanner() {
        if (banner == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(banner);
        } catch (RuntimeException ignored) {
            // Already gone with the service.
        }
        banner = null;
    }

    private TextView text(Typeface tf, float size, int color, CharSequence s) {
        TextView v = new TextView(service);
        v.setTypeface(tf);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(size));
        v.setTextColor(color);
        v.setText(s);
        return v;
    }

    // ---------------------------------------------------------------- testing

    /**
     * For checking on the TV without waiting for a prayer: the banner (with the spoken reminder, if on), the call
     * screen, or the spoken «حان الآن موعد صلاة …» for the next prayer, after a delay to switch to another app
     * first; or standby as after a call that woke the TV. From Home:
     * `am start -n tv.afnon.home/.MainActivity --es test banner|call|announce|standby`; a fast's banner with
     * `--es test fast:N` (a {@link Fasts} kind), or `fast:N:en` for its English; or the real check as if it were
     * that moment, `--es test fast-at:<epoch ms>`.
     */
    void test(String kind, long delayMs) {
        handler.postDelayed(() -> {
            Date now = new Date();
            PrayerDay day = new PrayerDay(service, now);
            if (kind.startsWith("fast-at:")) {
                Date then = new Date(Long.parseLong(kind.substring(8)));
                fasted = "";
                checkFast(new PrayerDay(service, then), then);
            } else if (kind.startsWith("fast:")) {
                String[] p = kind.split(":");
                int fast = Integer.parseInt(p[1]);
                Text t = p.length > 2 ? new Text(false, false) : Screen.loadText(service);
                showFast(t, fast, Fasts.SAMPLE[fast][0], Fasts.SAMPLE[fast][1], day.suhoorEnd);
            } else if ("call".equals(kind)) {
                openCall(day.nextIndex, day.nextTime, false);
            } else if ("standby".equals(kind)) {
                Wake.backToStandby();
            } else if ("announce".equals(kind)) {
                Announce.now(service, day.nextIndex, day.friday);
            } else {
                showBanner(day, now);
                // "banner-silent": the look only, without the spoken reminder (late at night).
                if (Prefs.remindVoice(service) && !"banner-silent".equals(kind)) Announce.before(service, day.nextIndex, day.friday);
            }
        }, delayMs);
    }
}
