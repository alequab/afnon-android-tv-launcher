---
name: Afnon
description: An Android TV home screen built around the prayer day, in lapis and gold.
colors:
  night-lapis: "#0C1B33"
  deep-lapis: "#0A1730"
  lapis-surface: "#12264A"
  lapis-raised: "#1A3360"
  lamp-gold: "#D8B46A"
  dawn-gold: "#F0D79C"
  plaster: "#EFE7D6"
  mist: "#9FB0CC"
  dim-mist: "#6F7F9C"
  soft-line: "rgba(239,231,214,0.25)"
  gold-line: "rgba(216,180,106,0.6)"
  lattice-line: "rgba(216,180,106,0.07)"
  plaster-label: "#5A4A22"
  plaster-secondary: "#3D4A63"
  screensaver-black: "#000000"
  screensaver-text: "#A69F90"
  screensaver-gold: "#B8A578"
typography:
  display:
    fontFamily: "Amiri, serif"
    fontSize: "150px"
    fontWeight: 700
    lineHeight: 1
  display-en:
    fontFamily: "Marcellus, serif"
    fontSize: "150px"
    fontWeight: 400
    lineHeight: 1
  headline:
    fontFamily: "Amiri, serif"
    fontSize: "88px"
    fontWeight: 700
    lineHeight: 1
  title:
    fontFamily: "Amiri, serif"
    fontSize: "52px"
    fontWeight: 700
    lineHeight: 1.15
  sacred:
    fontFamily: "Amiri, serif"
    fontSize: "32px"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: "0em"
  body:
    fontFamily: "Alexandria, sans-serif"
    fontSize: "26px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Alexandria, sans-serif"
    fontSize: "22px"
    fontWeight: 400
    lineHeight: 1.5
  caption:
    fontFamily: "Alexandria, sans-serif"
    fontSize: "20px"
    fontWeight: 400
    lineHeight: 1.5
rounded:
  badge: "8px"
  cell: "14px"
  row: "16px"
  tile: "18px"
  panel: "24px"
  pill: "999px"
spacing:
  "1": "8px"
  "2": "12px"
  "3": "16px"
  "4": "24px"
  "5": "32px"
  "6": "48px"
  "7": "64px"
  "8": "96px"
  safe-inline: "96px"
  safe-block: "56px"
components:
  tile:
    backgroundColor: "{colors.lapis-surface}"
    textColor: "{colors.plaster}"
    rounded: "{rounded.tile}"
    width: "248px"
    height: "150px"
  tile-focused:
    backgroundColor: "{colors.lamp-gold}"
    textColor: "{colors.night-lapis}"
    rounded: "{rounded.tile}"
  header-pill:
    backgroundColor: "{colors.lapis-surface}"
    textColor: "{colors.plaster}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    height: "72px"
    padding: "0 28px"
  chip:
    backgroundColor: "{colors.lapis-surface}"
    textColor: "{colors.plaster}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 26px 0 22px"
  chip-unread:
    backgroundColor: "{colors.lapis-surface}"
    textColor: "{colors.lamp-gold}"
    rounded: "{rounded.pill}"
  prayer-cell:
    backgroundColor: "{colors.lapis-surface}"
    textColor: "{colors.plaster}"
    rounded: "{rounded.cell}"
    height: "146px"
  prayer-cell-current:
    backgroundColor: "{colors.plaster}"
    textColor: "{colors.night-lapis}"
    rounded: "{rounded.cell}"
  banner:
    backgroundColor: "{colors.night-lapis}"
    textColor: "{colors.plaster}"
    rounded: "{rounded.panel}"
    width: "640px"
    padding: "24px 28px 0"
  reminder-card:
    backgroundColor: "{colors.plaster}"
    textColor: "{colors.night-lapis}"
    rounded: "{rounded.panel}"
    width: "680px"
    padding: "32px 36px"
---

<!-- Tokens: afnon-mihrab/tokens.json (night mode: tokens.night.json) is the source; Tokens.java, colors.xml and the
site's tokens.css are generated from it (cd site && npm run tokens). This file describes them; it never overrides them. -->

# Design System: Afnon

## Overview

**Creative North Star: "The Lit Mihrab"**

Afnon is a prayer niche lit in the evening. The screen is night lapis; one lamp of gold marks what matters now: the
next prayer, its countdown, the focused choice. Everything else sits back in mist and plaster until the remote reaches
it. The arch on the start side is the room's one architectural gesture: it holds Qur'an 4:103 in its crown and the next
prayer below, and nothing ever covers it.

The mood is calm, reverent and quiet. It is a screen watched from a sofa three metres away, by a family, through the
day, so nothing shouts, blinks or competes; only the prayer time rises. Density is low and fixed: one 1920×1080 canvas,
driven by a D-pad, with every focus move spelled out per screen. The sacred text is set as text, in Amiri, exactly,
never as decoration.

It must never become kitsch religious decor: no glitter, no clip-art domes or minarets, no green gradients, no
calligraphy used as wallpaper. The only ornament is the faint star lattice behind Home and the arch's own lines.

**Key Characteristics:**
- Night lapis ground, one gold accent, plaster for what is current.
- An arch that always shows the next prayer, never covered or focusable at its crown.
- Arabic first (RTL), English second; Western or Eastern digits independent of language.
- Flat: depth from lapis steps and rings, never shadows or blur.
- Transform and opacity only, and the TV may have animations off.

## Colors

A narrow night palette: four lapis steps, two golds, plaster and mist; the gold is rare on purpose.

### Primary
- **Lamp Gold** (#D8B46A): the next prayer, the arch's outline, sacred text, the fill of the focused tile or button,
  the gold pill of a banner. 8.9:1 on Night Lapis.
- **Dawn Gold** (#F0D79C): the approaching prayer's glow (the arch's border in the last 15 minutes), a pressed control,
  and Arafah's brighter banner border.

### Neutral
- **Night Lapis** (#0C1B33): the ground of every screen and of the banners over other apps.
- **Deep Lapis** (#0A1730): below the ground, the Settings sidebar.
- **Lapis Surface** (#12264A): the arch's fill, tiles, prayer cells, pills, panels.
- **Lapis Raised** (#1A3360): badges, stepper buttons, a switch's off track.
- **Plaster** (#EFE7D6): primary text, the focus ring, the current prayer's cell and the reminder card. 13.9:1 on
  Night Lapis.
- **Mist** (#9FB0CC): secondary text, past prayers, sources under a quotation. 7.7:1 on Night Lapis.
- **Dim Mist** (#6F7F9C): disabled text only.
- **Soft Line** (plaster at 25%): resting outlines of pills and chips; **Gold Line** (gold at 60%): the arch foot
  button's outline and hairlines in banners; **Lattice Line** (gold at 7%): the star lattice behind Home.
- **Screensaver** (#000000 ground, #A69F90 text, #B8A578 for the next prayer): a black room with dimmed words, so
  nothing burns in.

Night mode (Settings → Display, or the moon button) swaps the lapis for near-black and halves the gold and text
(tokens.night.json); colours are read when a view is built, so they are never copied into constants.

### Named Rules
**The One Lamp Rule.** Gold marks what matters now: the next prayer, the focused control, the sacred text. If a
second thing on the screen glows gold without being one of these, it is wrong.

**The Plaster Is Now Rule.** A plaster fill means "current": the prayer whose time it is, the chosen segment, the
reminder card. It is never used for decoration.

## Typography

**Display Font:** Amiri 700 (Arabic) and Marcellus (English), with serif fallback
**Body Font:** Alexandria 300–600, with sans-serif fallback
**Sacred Text:** Amiri 400, always in Lamp Gold

**Character:** Amiri gives the prayer names and the countdown the weight of a manuscript; Alexandria carries everything
that is interface, in both scripts and both digit sets. With Western digits in Arabic, the large numbers are set in
Marcellus and their ص/م in Amiri.

### Hierarchy
- **Display** (Amiri 700 / Marcellus, 150px, 120px with hours, line-height 1): the arch's countdown; the clock is 96px.
- **Headline** (Amiri 700, 88px, 1): the next prayer's name in the arch.
- **Title** (Amiri 700 / Marcellus, 52–64px, 1.15): screen titles, the call screen, a banner's title (34–44px).
- **Sacred** (Amiri 400, 32px, 1.55): Qur'an and hadith, exact, with surah:ayah or the collection under them.
- **Body** (Alexandria, 26–30px, 1.5): dates, prayer cells, settings rows.
- **Label** (Alexandria, 22–24px): pills, chips, hints, sources.
- **Caption** (Alexandria, 20px): the smallest text on any screen.

### Named Rules
**The Twenty Pixel Floor.** Nothing on screen is smaller than 20px at 1080p (about 13px on a 720p panel); it is read
from a sofa.

**The Unspaced Arabic Rule.** Arabic is never letter-spaced; English caps labels alone take 0.2em.

**The Exact Text Rule.** Qur'an and hadith are quoted word for word (checked in the Shamela library), with their
source; a paraphrase is never set in the sacred style.

## Layout

A fixed 1920×1080 canvas in two parts: the arch (600×900) at the inline start, 96px in, and the courtyard (1064px
wide, from 760px) beside it. The courtyard reads top to bottom: clock and the two dates; the header pills (Radio,
Wi-Fi, Inputs, Night, Settings, and Live on the `tcl-live` build) at its end; the six prayer cells (146px high, 12px apart); Favourites (four
248×150 tiles, 24px apart); More (five tiles and All apps, 20px apart); the hint line at the bottom. In Arabic the whole
layout mirrors, and every Left and Right on the remote swaps with it.

Spacing steps are 8, 12, 16, 24, 32, 48, 64 and 96; the safe area is 96px inline and 56px top and bottom. The design is
drawn at 1080p and scales down whole on a 720p panel.

### Named Rules
**The Courtyard Order Rule.** Information that qualifies something sits beside it: the coming fast's chip on the date's
line, a release's chip under Settings. Nothing floats free of what it is about.

## Elevation & Depth

Flat by design: no shadows, no blur, no glass. Depth comes from the lapis steps (ground, surface, raised) and from
rings: a resting outline in Soft Line, the focused element filled with Lamp Gold inside a 4px plaster ring 6px away,
scaled to 1.06 (rows 1.04, Settings rows 1.02). Overlays dim what is behind with a solid scrim, never a blur.

### Named Rules
**The Solid Ring Rule.** The only thing that lifts an element is focus: a gold fill, a plaster ring and a small scale.
Nothing is lifted by a shadow.

## Shapes

Soft and steady. Corners grow with the element: badges 8px, prayer cells 14px, rows 16px, tiles 18px, panels, cards
and banners 24px, pills and chips fully round. The arch is the one non-rectangular form: a pointed arch with an inner
line, its crown kept free for the verse. Outlines are 2px at rest (3px for the next prayer and the arch).

## Components

### Header pills and round buttons
- **Shape:** fully round, 72px high (round buttons 72×72).
- **Rest:** Lapis Surface fill, 2px Soft Line outline, Plaster words and icon. The radio pill's outline turns Gold Line
  while it plays.
- **Focus:** Lamp Gold fill, Night Lapis words, plaster ring.

### Chips
- **Style:** fully round, 48px high, Lapis Surface fill, 24px Alexandria words beside a 30px icon.
- **Unread:** a 3px Lamp Gold outline and gold words until opened; **read:** the pills' Soft Line outline and plaster
  words. Never a gold fill at rest: that is focus.

### Tiles
- **Corner Style:** 18px. **Background:** Lapis Surface; the app's own icon or banner inside.
- **Focus:** Lamp Gold fill with a plaster ring, scaled 1.06; held OK arranges favourites.

### Prayer cells
- **Style:** 14px corners, Lapis Surface; past ones at 60%; the next prayer outlined 3px in Lamp Gold; the current one
  filled Plaster with Night Lapis words. Information only, never focusable.

### Banners
- **Style:** Night Lapis, 24px corners, a 2px Lamp Gold border (3px Dawn Gold for Arafah), 640px wide at the top end of
  the screen, over any app. An icon, a small label above the title, the title in Amiri or Marcellus, details below.
- **Behaviour:** a gold bar empties while it waits to hide (8–20s); never over the call to prayer or the screensaver;
  never takes focus. Opened again from a chip it has no bar and stays until OK, Back or Home.

### Reminder card
- **Style:** a Plaster card (24px corners, 6px lapis border) at the bottom end of Home, Night Lapis words; it takes
  focus with two buttons (Got it, Today's prayers) until one is pressed or Back.

### The Arch (signature)
The arch is always there: its crown holds Qur'an 4:103 in Amiri, then the next prayer, its countdown and its adhan time.
At rest its border is 3px Lamp Gold; in the last 15 minutes it is 6px Dawn Gold; focused, 5px Plaster. OK on its foot
button opens Today's prayers and the Qibla.

## Do's and Don'ts

### Do:
- **Do** take every colour, size and timing from tokens.json (night mode from tokens.night.json).
- **Do** keep text at 20px or more at 1080p, and check both 1080p and the 720p TCL.
- **Do** check every screen in Arabic (RTL) and English (LTR), with Western and Eastern digits.
- **Do** quote Qur'an and hadith exactly, in Amiri 400 gold, with the source under it.
- **Do** spell out the D-pad focus for each screen, with Left and Right swapped in Arabic.

### Don't:
- **Don't** add kitsch religious decor: glitter, clip-art domes or minarets, green gradients, calligraphy as wallpaper.
- **Don't** use shadows, blur or glass; depth is lapis steps and the focus ring.
- **Don't** fill anything gold at rest: a gold fill means focus.
- **Don't** letter-space Arabic.
- **Don't** animate anything but transform and opacity, or rely on an animation the TV may have turned off.
- **Don't** cover the arch's crown, or make it focusable.
