#!/usr/bin/env bash
# "Boot completed" can come before user 0 is unlocked and /sdcard is mounted, and tests
# that touch SharedPreferences or /sdcard then fail.
set -uo pipefail
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
state() {
  if [ "$sdk" -lt 24 ]; then
    # No file-based encryption before API 24, so no locked state to wait out. /sdcard alone
    # is ready seconds after boot, while the system is still starting apps, so wait for the
    # boot animation to end and an activity (the launcher) to be on screen.
    ce=true; user="State: RUNNING_UNLOCKED"
    booted=$(adb shell getprop sys.boot_completed | tr -d '\r')
    anim=$(adb shell getprop init.svc.bootanim | tr -d '\r')
    if [ "$booted" = "1" ] && [ "$anim" = "stopped" ] && adb shell dumpsys activity activities | grep mResumedActivity >/dev/null; then
      resumed=yes
    else
      resumed="no (boot_completed=$booted, bootanim=$anim)"
    fi
  else
    ce=$(adb shell getprop sys.user.0.ce_available | tr -d '\r')
    user=$(adb shell dumpsys user | grep -m1 -oE 'State: [A-Z_]+' | tr -d '\r')
    resumed=yes
  fi
  if adb shell ls /sdcard/ >/dev/null 2>&1; then sdcard=ok; else sdcard=unavailable; fi
}
for i in $(seq 150); do
  state
  if [ "$ce" = "true" ] && [ "$user" = "State: RUNNING_UNLOCKED" ] && [ "$sdcard" = "ok" ] && [ "$resumed" = "yes" ]; then
    echo "Emulator ready after $((i * 2))s ($user, ce_available=$ce, sdcard=$sdcard, resumed=$resumed)"
    adb shell wm dismiss-keyguard || true
    # Another app crashing on the emulator shows a dialog that takes window focus from the
    # app under test, and Espresso then fails with RootViewWithoutFocusException.
    adb shell settings put global hide_error_dialogs 1
    exit 0
  fi
  if [ $((i % 5)) -eq 1 ]; then echo "Waiting for emulator: $user, ce_available=$ce, sdcard=$sdcard, resumed=$resumed"; fi
  sleep 2
done
echo "Emulator not ready after 5 minutes: $user, ce_available=$ce, sdcard=$sdcard, resumed=$resumed" >&2
exit 1
