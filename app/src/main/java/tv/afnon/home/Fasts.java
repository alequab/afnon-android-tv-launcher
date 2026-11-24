package tv.afnon.home;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import java.util.Calendar;
import java.util.Date;

/**
 * The voluntary fasts Afnon reminds of (Settings → Reminders; the words are in {@link Text#fastTitle}). A fast's
 * reminder comes after each of the five prayers before its suhoor: Fajr, Dhuhr and Asr of the day before, then
 * Maghrib and Isha of its evening, when the Hijri day (which begins at Maghrib) is already the fast's. So the second
 * White Day's reminders start after Fajr of the first. Monday and Thursday are quiet: once, after Maghrib. No
 * reminder on days fasting is forbidden, in Ramadan, or for the day of doubt.
 */
final class Fasts {
    private Fasts() {}

    static final int WHITE_DAYS = 0, DHUL_HIJJAH = 1, ARAFAH = 2, TASUA = 3, ASHURA = 4, SHAWWAL = 5, MONDAY = 6, THURSDAY = 7;
    /** Switches in Settings, one per kind; Monday and Thursday share the last. */
    static final int SWITCHES = 7;
    /** All on but Monday and Thursday, which would come twice a week. */
    static final int DEFAULT_BITS = 0b0111111;
    /** After each prayer's time, so the call and the prayer come first. */
    static final long AFTER_PRAYER_MS = 10 * 60_000;
    /** How long the banner stays: news, not a call; Monday and Thursday are the quiet ones. */
    static final long BANNER_MS = 20_000, QUIET_BANNER_MS = 8_000;
    /** Opened again from Home's chip: until Back, or this long. */
    static final long PINNED_MS = 120_000;
    private static final int[] PRAYERS = {PrayerDay.FAJR, PrayerDay.DHUHR, PrayerDay.ASR, PrayerDay.MAGHRIB, PrayerDay.ISHA};

    /** Each kind's {Hijri day, Hijri month}, for the banner's test hook. */
    static final int[][] SAMPLE = {
        {13, IslamicCalendar.RAJAB}, {3, IslamicCalendar.DHU_AL_HIJJAH}, {9, IslamicCalendar.DHU_AL_HIJJAH},
        {9, IslamicCalendar.MUHARRAM}, {10, IslamicCalendar.MUHARRAM}, {2, IslamicCalendar.SHAWWAL},
        {20, IslamicCalendar.SAFAR}, {23, IslamicCalendar.SAFAR},
    };

    /** A coming fast, as its banner, Home's chip and the screensaver show it. */
    static final class Due {
        final int kind, hijriDay, hijriMonth, weekday;
        /**
         * A Monday or Thursday too (and not itself a Monday or Thursday fast): one fast with both intentions. Shown
         * whatever the Monday and Thursday switch says.
         */
        final boolean alsoWeekly;
        /** The fast's Fajr, the end of its suhoor. */
        final Date suhoor;
        /** False from midnight until its Fajr: the fast is today. */
        final boolean tomorrow;
        /** The fast's Hijri date ("1448-5-13"): Home's chip is gold-dotted until opened for it. */
        final String key;
        /** Once per prayer: the date and the prayer ("eve" for the quiet ones). */
        final String slotKey;

        Due(int kind, PrayerDay fast, boolean tomorrow, String slot) {
            this(kind, fast.hijriYear, fast.hijriMonth, fast.hijriDay, fast.weekday, fast.times[PrayerDay.FAJR], tomorrow, slot);
        }

        Due(int kind, int hijriYear, int hijriMonth, int hijriDay, int weekday, Date suhoor, boolean tomorrow, String slot) {
            this.kind = kind;
            this.hijriDay = hijriDay;
            this.hijriMonth = hijriMonth;
            this.weekday = weekday;
            alsoWeekly = !quiet(kind) && (weekday == Calendar.MONDAY || weekday == Calendar.THURSDAY);
            this.suhoor = suhoor;
            this.tomorrow = tomorrow;
            key = hijriYear + "-" + hijriMonth + "-" + hijriDay;
            slotKey = key + ":" + slot;
        }
    }

    /** The banner due now, for the latest prayer whose time is 10 minutes past; null if none or switched off. */
    static Due next(Context c, PrayerDay day, Date now) {
        int slot = slot(day.times, now);
        if (slot < 0) return null;
        return due(new PrayerDay(c, tomorrowNoon(now)), slot, Prefs.fastBits(c));
    }

    /** {@code fast}: the fast day's, at noon. {@code slot}: the prayer just passed (a PrayerDay index). */
    static Due due(PrayerDay fast, int slot, int bits) {
        int kind = kind(fast.hijriMonth, fast.hijriDay, fast.weekday, bits);
        if (kind < 0) return null;
        if (quiet(kind)) return slot >= PrayerDay.MAGHRIB ? new Due(kind, fast, true, "eve") : null;
        return new Due(kind, fast, true, String.valueOf(slot));
    }

    /**
     * Home's chip and the screensaver: the banner due now, else, from midnight until Fajr, the fast that is today.
     * Null if none.
     */
    static Due upcoming(Context c, PrayerDay day, Date now) {
        Due d = next(c, day, now);
        if (d != null) return d;
        // After midnight, before Fajr: the Hijri date is already the fast's.
        if (now.before(day.times[PrayerDay.FAJR]) && now.before(day.times[PrayerDay.SUNRISE])) {
            int kind = kind(day.hijriMonth, day.hijriDay, day.weekday, Prefs.fastBits(c));
            if (kind >= 0) return new Due(kind, day, false, "today");
        }
        return null;
    }

    /**
     * The latest of the five prayers whose time was {@link #AFTER_PRAYER_MS} ago or more today, or -1 (before
     * Fajr). After Isha, {@code times[FAJR]} is tomorrow's, so Isha's slot lasts until midnight.
     */
    static int slot(Date[] times, Date now) {
        for (int i = PRAYERS.length - 1; i >= 0; i--) {
            int p = PRAYERS[i];
            if (times[p].getTime() + AFTER_PRAYER_MS <= now.getTime()) return p;
        }
        return -1;
    }

    /** Noon of the calendar day after now's: the fast's day, whose Hijri date is the one it is fasted on. */
    static Date tomorrowNoon(Date now) {
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** The fast on this Hijri date and weekday (Calendar.MONDAY…) among the switched-on bits; -1 if none. */
    static int kind(int month, int day, int weekday, int bits) {
        if (month == IslamicCalendar.RAMADAN) return -1;
        // Eid al-Fitr, Eid al-Adha and the Days of Tashriq: fasting is forbidden.
        if (month == IslamicCalendar.SHAWWAL && day == 1) return -1;
        if (month == IslamicCalendar.DHU_AL_HIJJAH && day >= 10 && day <= 13) return -1;
        // The day of doubt: the moon may make this Ramadan.
        if (month == IslamicCalendar.SHABAN && day >= 29) return -1;
        if (month == IslamicCalendar.DHU_AL_HIJJAH && day == 9 && on(bits, ARAFAH)) return ARAFAH;
        if (month == IslamicCalendar.MUHARRAM && day == 10 && on(bits, ASHURA)) return ASHURA;
        // Fasting the 9th with Ashura is the Sunnah, so the Ashura switch brings Tasu'a's evening too.
        if (month == IslamicCalendar.MUHARRAM && day == 9 && (on(bits, TASUA) || on(bits, ASHURA))) return TASUA;
        if (month == IslamicCalendar.DHU_AL_HIJJAH && day <= 8 && on(bits, DHUL_HIJJAH)) return DHUL_HIJJAH;
        // Once, the evening before the first day they can be fasted, not every night of the month.
        if (month == IslamicCalendar.SHAWWAL && day == 2 && on(bits, SHAWWAL)) return SHAWWAL;
        if (day >= 13 && day <= 15 && on(bits, WHITE_DAYS)) return WHITE_DAYS;
        if (weekday == Calendar.MONDAY && on(bits, MONDAY)) return MONDAY;
        if (weekday == Calendar.THURSDAY && on(bits, MONDAY)) return THURSDAY;
        return -1;
    }

    /** Monday and Thursday: a smaller banner, shorter, with no extra lines. */
    static boolean quiet(int kind) {
        return kind == MONDAY || kind == THURSDAY;
    }

    /** The row of dots under the title: {how many, which is tomorrow (-1 none)}, or null. */
    static int[] dots(int kind, int hijriDay) {
        switch (kind) {
            case WHITE_DAYS: return new int[] {3, hijriDay - 13};
            case DHUL_HIJJAH: return new int[] {9, hijriDay - 1};
            case ARAFAH: return new int[] {9, 8};
            case TASUA: return new int[] {2, 0};
            case ASHURA: return new int[] {2, 1};
            case SHAWWAL: return new int[] {6, -1};
            default: return null;
        }
    }

    static int icon(int kind) {
        switch (kind) {
            case WHITE_DAYS: return Icon.FULL_MOON;
            case DHUL_HIJJAH: return Icon.KAABA;
            case ARAFAH: return Icon.MOUNTAIN;
            case MONDAY:
            case THURSDAY: return Icon.CALENDAR;
            default: return Icon.MOON;
        }
    }

    private static boolean on(int bits, int kind) {
        return (bits & (1 << kind)) != 0;
    }
}
