package tv.afnon.home;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import java.util.Date;

/**
 * The voluntary fasts Afnon reminds of, the evening before (Settings → Reminders; the words are in
 * {@link Text#fastTitle}). A Hijri day begins at Maghrib, so from then {@link PrayerDay} already holds the date and
 * weekday of the fast. No reminder on days fasting is forbidden, in Ramadan, or for the day of doubt.
 */
final class Fasts {
    private Fasts() {}

    static final int WHITE_DAYS = 0, DHUL_HIJJAH = 1, ARAFAH = 2, TASUA = 3, ASHURA = 4, SHAWWAL = 5, MONDAY = 6, THURSDAY = 7;
    /** Switches in Settings, one per kind; Monday and Thursday share the last. */
    static final int SWITCHES = 7;
    /** All on but Monday and Thursday, which would come twice a week. */
    static final int DEFAULT_BITS = 0b0111111;
    /** After Maghrib, so the call and the prayer come first. */
    private static final long AFTER_MAGHRIB_MS = 10 * 60_000;
    /** How long the banner stays: evening news, not a call; Monday and Thursday are the quiet ones. */
    static final long BANNER_MS = 20_000, QUIET_BANNER_MS = 8_000;

    /** Each kind's {Hijri day, Hijri month}, for the banner's test hook. */
    static final int[][] SAMPLE = {
        {13, IslamicCalendar.RAJAB}, {3, IslamicCalendar.DHU_AL_HIJJAH}, {9, IslamicCalendar.DHU_AL_HIJJAH},
        {9, IslamicCalendar.MUHARRAM}, {10, IslamicCalendar.MUHARRAM}, {2, IslamicCalendar.SHAWWAL},
        {20, IslamicCalendar.SAFAR}, {23, IslamicCalendar.SAFAR},
    };

    /** The fast whose evening this is and which is switched on; -1 if none. */
    static int due(Context c, PrayerDay day, Date now) {
        if (now.getTime() < day.times[PrayerDay.MAGHRIB].getTime() + AFTER_MAGHRIB_MS) return -1;
        return kind(day.hijriMonth, day.hijriDay, day.weekday, Prefs.fastBits(c));
    }

    /**
     * For Home's date line: the fast from its evening until its Fajr, so a banner missed while away still shows.
     * {kind, 1 if it is tomorrow (before midnight) or 0 if today (after midnight)}, or null if none.
     */
    static int[] upcoming(Context c, PrayerDay day, Date now) {
        int kind = due(c, day, now);
        if (kind >= 0) return new int[] {kind, 1};
        // After midnight, before Fajr: the Hijri date is already the fast's.
        if (now.before(day.times[PrayerDay.FAJR]) && now.before(day.times[PrayerDay.SUNRISE])) {
            kind = kind(day.hijriMonth, day.hijriDay, day.weekday, Prefs.fastBits(c));
            if (kind >= 0) return new int[] {kind, 0};
        }
        return null;
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
        if (weekday == java.util.Calendar.MONDAY && on(bits, MONDAY)) return MONDAY;
        if (weekday == java.util.Calendar.THURSDAY && on(bits, MONDAY)) return THURSDAY;
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
