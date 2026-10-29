#!/usr/bin/env bash
# Run when the connected tests fail, while the emulator is still up, so the job log itself says
# what had window focus and what crashed: the logcat artifact can't be read by every reviewer.
set -u
echo "::group::Window focus when the tests failed"
# adb can hang once the system process has died, so each call gets a short limit.
timeout 30 adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' || echo "(no answer from dumpsys window)"
timeout 30 adb shell dumpsys activity activities | grep -E 'mResumedActivity|mFocusedActivity' || echo "(no answer from dumpsys activity)"
echo "::endgroup::"
echo "::group::Crashes and ANRs in the emulator log"
grep -E -A 12 'FATAL EXCEPTION|ANR in |WATCHDOG' emulator-logcat.txt | head -n 400
grep -E 'Force finishing activity|has died' emulator-logcat.txt | tail -n 40
echo "::endgroup::"
exit 1
