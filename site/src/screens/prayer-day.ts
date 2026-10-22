// Prayer day's live content, from PrayerDayActivity.render/renderMinute/renderPicker and QiblaView.
import { Coordinates, Qibla } from 'adhan';
import type { Ctx, Model } from '../lib/ctx';
import { methodOf } from '../lib/data';
import { DHUHR, SUNRISE, clock, duration, gregorian, hijri, secondsUntil } from '../lib/prayer';
import type { Text } from '../lib/text';
import { archModel, prayerNames } from './home';

const BELLS = 'afnon-bells', FACING = 'afnon-facing';

/** Each prayer's adhan bell (Prefs.bell): on by default except Sunrise, which has no adhan. */
export function loadBells(): boolean[] {
  const def = [0, 1, 2, 3, 4, 5].map((i) => i !== SUNRISE);
  try {
    if (typeof localStorage === 'undefined') return def;
    const saved = JSON.parse(localStorage.getItem(BELLS) ?? 'null');
    return Array.isArray(saved) && saved.length === 6 ? saved.map(Boolean) : def;
  } catch { return def; }
}
export function saveBells(b: boolean[]) {
  try { localStorage.setItem(BELLS, JSON.stringify(b)); } catch { /* this visit only */ }
}
/** The way the viewer faces the TV, degrees from north; -1 not set. */
export function loadFacing(): number {
  try {
    if (typeof localStorage === 'undefined') return -1;
    const v = parseInt(localStorage.getItem(FACING) ?? '-1', 10);
    return v >= 0 && v < 360 ? v : -1;
  } catch { return -1; }
}
export function saveFacing(v: number) {
  try { localStorage.setItem(FACING, String(v)); } catch { /* this visit only */ }
}

// Text.java methods with logic, used only here.
const qiblaDirection = (t: Text, bearing: number, city: string) => t.octant(bearing) + (t.ar ? ' · من ' : ' · from ') + city;
const facingLine = (t: Text, facing: number) => (t.ar ? 'تجلس مواجهًا: ' : 'You face: ') + t.bearingLabel(facing);
/** Qibla bearing from the city (prayer.ts's `new Qibla()` returns an object, not the number). */
export const qiblaOf = (c: Ctx) => Qibla(new Coordinates(c.day.city.lat, c.day.city.lng));
export const relativeOf = (qibla: number, facing: number) => (((qibla - facing) % 360) + 360) % 360;

/** The dial's needle and (in room mode) its north marker, as SVG transforms. */
export function dialAttrs(bearing: number, north: number | null) {
  return {
    needle: { transform: `rotate(${bearing.toFixed(1)} 180 180)` },
    northMark: north === null ? { visibility: 'hidden', transform: 'rotate(0 180 180)' } : { visibility: 'visible', transform: `rotate(${north.toFixed(1)} 180 180)` },
  };
}

export function prayerDayModel(c: Ctx): Model {
  const { t, day, now, h24, prefs } = c;
  const names = prayerNames(c);
  const bells = loadBells();
  const facing = loadFacing();
  const qibla = qiblaOf(c);

  // The arch: Home's countdown, with the method and Asr school below (no Ramadan wording here).
  const arch = archModel(c);
  arch.html.archLabel = day.friday && day.nextIndex === DHUHR ? t.s('jumuahLabel') : t.s('nextPrayer');
  arch.html.archName = names[day.nextIndex];
  arch.html.archSub = t.methodName(methodOf(prefs));
  arch.html.archSub2 = t.school(prefs.hanafi);

  const html: Record<string, string> = {
    ...arch.html,
    dates: hijri(t, day) + t.sep() + gregorian(t, now) + t.sep() + day.city[t.lang],
    deg: t.d(Math.round(qibla) + '°'),
    roomLabel: t.roomButton(facing >= 0),
  };
  const attr: Record<string, Record<string, string>> = { ...arch.attr };

  day.times.forEach((time, i) => {
    const state = i === day.nowIndex ? 'now' : i !== day.nextIndex && time <= now ? 'past' : 'up';
    html['n' + i] = names[i];
    html['s' + i] = state === 'now' ? t.s('rowNow') : state === 'past' ? t.passed(i === SUNRISE) : t.in(duration(secondsUntil(time, now), false));
    html['t' + i] = clock(t, time, h24, 0.55);
    attr['r' + i] = { 'data-state': state, 'data-bell': String(bells[i]), 'aria-label': `${names[i]} · ${t.bell(bells[i])}` };
  });

  if (facing < 0) {
    // Room direction unknown: north-up.
    html.northLabel = t.s('north');
    html.direction = qiblaDirection(t, qibla, day.city[t.lang]);
    html.qiblaHint = t.s('qiblaHint');
    Object.assign(attr, dialAttrs(qibla, null));
  } else {
    // The top of the dial is the TV, so the needle points the way to turn from the sofa.
    const rel = relativeOf(qibla, facing);
    html.northLabel = t.s('tvMarker');
    html.direction = t.qiblaRelative(rel);
    html.qiblaHint = facingLine(t, facing);
    Object.assign(attr, dialAttrs(rel, (360 - facing) % 360));
  }
  attr.room = { 'data-hints': JSON.stringify([t.hintOk(html.roomLabel), t.s('hintTimes')]) };
  return { html, attr };
}

/** The picker's value, where-line and preview dial for a facing. */
export function pickerModel(c: Ctx, facing: number): Model {
  const rel = relativeOf(qiblaOf(c), facing);
  const d = dialAttrs(rel, (360 - facing) % 360);
  return {
    html: { pickerValue: c.t.bearingLabel(facing), pickerWhere: c.t.qiblaRelative(rel) },
    attr: { pickerNeedle: d.needle, pickerNorth: d.northMark },
  };
}
