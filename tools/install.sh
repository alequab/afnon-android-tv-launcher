#!/usr/bin/env bash
# Builds Afnon, installs it over the running copy and brings it straight back as the home screen. Also sets
# what Afnon needs from the system: HOME, the remote buttons service, the screensaver, TCL's auto-start
# grants, two permissions the radio uses and location for the Wi-Fi name.
set -e
. "$(dirname "$0")/env.sh"
[ -n "$(ls "$ROOT/app/src/main/assets/adhan/"*.mp3 2>/dev/null)" ] || bash "$ROOT/tools/fetch-adhans.sh"
[ -n "$(ls "$ROOT/app/src/main/assets/announce/"*.mp3 2>/dev/null)" ] || bash "$ROOT/tools/fetch-announce.sh" || true
(cd "$ROOT" && ./gradlew :app:assembleDebug -q)
adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" | tail -1
# The setup steps, shared with the phone installer: tools/setup-steps.txt (required|en|ar|command, then ?|check).
# A step whose check already passes is left alone; after its command the check must pass too.
step() {
  [ -n "$cmd" ] || return 0
  if [ -n "$check" ] && adb shell "$check" </dev/null >/dev/null 2>&1; then echo "  ok   $en (already)"; return 0; fi
  if adb shell "$cmd" </dev/null >/dev/null 2>&1 && { [ -z "$check" ] || adb shell "$check" </dev/null >/dev/null 2>&1; }; then
    echo "  ok   $en"
  elif [ "$required" = 1 ]; then echo "  FAIL $en" >&2; return 1
  else echo "  skip $en"; fi
}
required=; en=; cmd=; check=
while IFS= read -r line; do
  case "$line" in
    '#'*|'') ;;
    '?|'*) check=${line#\?|} ;;
    *) step || exit 1; check=; IFS='|' read -r required en ar cmd <<< "$line" ;;
  esac
done < "$ROOT/tools/setup-steps.txt"
step || exit 1
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "Afnon installed and on screen."
