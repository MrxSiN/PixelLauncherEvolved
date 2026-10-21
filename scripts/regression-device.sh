#!/usr/bin/env bash
# Failure and lifecycle regression cases for Pixel Launcher Evolved (and the PME
# parts it relies on), run against a connected rooted phone. Nothing here is in
# the module: it only drives the phone through adb and reads what it reports.
#
# usage: ADB=/path/to/adb scripts/regression-device.sh
#
# Only cases that leave the phone as it was run here. The rest need a spare device
# (they change the layout, crash the launcher or switch Modes) and are recorded as
# NOT RUN with the reason, so a result never claims more than was tried.
# Never toggles Wi-Fi: adb may be running over it.
#
# Writes docs/regression/<build>.txt: one line per case, PASS / FAIL / SKIP / NOT RUN.
# No pipefail: `grep -q` ends a pipe early, which pipefail would report as the case failing.
set -u
export MSYS_NO_PATHCONV=1

ADB=${ADB:-adb}
LAUNCHER=com.google.android.apps.nexuslauncher
MODULE=io.github.mrxsin.pixellauncherevolved
RECOVERY=$MODULE/my.github.MrxSiN.pixellauncherevolved.recovery.RecoveryActivity
COPIES=/data/data/$LAUNCHER/no_backup/ple_layout

sh_() { $ADB shell "$@" | tr -d '\r'; }
su_() { $ADB shell "su --mount-master -c '$*'" | tr -d '\r'; }

BUILD=$(sh_ getprop ro.build.id)
LAUNCHER_VERSION=$(sh_ dumpsys package $LAUNCHER | grep -m1 versionName | cut -d= -f2)
MODULE_VERSION=$(sh_ dumpsys package $MODULE | grep -m1 versionName | cut -d= -f2)
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/docs/regression/$BUILD.txt"
mkdir -p "$(dirname "$OUT")"
RESULTS=()

record() { RESULTS+=("$(printf '%-28s %-8s %s' "$1" "$2" "$3")"); echo "$1: $2 $3"; }

top() { sh_ dumpsys activity activities | grep -m1 topResumedActivity; }

# ---- Process restart: the launcher comes back, every feature installs, nothing crashes.
case_restart() {
    $ADB logcat -c
    sh_ am force-stop $LAUNCHER
    sh_ am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
    local installed=0
    for _ in $(seq 1 20); do
        sleep 1
        installed=$($ADB logcat -d | grep -c "PixelLauncherEvolved: Installed ")
        [ "$installed" -gt 0 ] && $ADB logcat -d | grep -q "LoaderTask: step 1" && break
    done
    sleep 3
    local failed crashed
    failed=$($ADB logcat -d | grep -c "PixelLauncherEvolved: Feature .* failed to install")
    crashed=$($ADB logcat -d -b crash | grep -c "$LAUNCHER")
    if [ "$installed" -gt 0 ] && [ "$failed" -eq 0 ] && [ "$crashed" -eq 0 ]; then
        record process_restart PASS "$installed features installed"
    else
        record process_restart FAIL "installed=$installed failed=$failed crashes=$crashed"
    fi
}

# ---- A usable home: the launcher is in front with a workspace and a dock or search bar.
case_home() {
    sh_ input keyevent HOME
    # Just after a restart the dump can come back before the workspace is laid out.
    local ui=""
    for _ in $(seq 1 10); do
        sleep 1
        sh_ uiautomator dump /sdcard/ple_ui.xml >/dev/null
        ui=$(sh_ cat /sdcard/ple_ui.xml)
        grep -q "$LAUNCHER:id/workspace" <<<"$ui" && break
    done
    sh_ rm -f /sdcard/ple_ui.xml
    if top | grep -q "$LAUNCHER" && grep -q "$LAUNCHER:id/workspace" <<<"$ui" && grep -q "$LAUNCHER:id/hotseat" <<<"$ui"; then
        record usable_home PASS "workspace and hotseat drawn"
    else
        record usable_home FAIL "launcher not in front or no workspace"
    fi
}

# ---- The Modes dump still has the layout ZenModes reads: a build that changes it shows here first.
case_modes_format() {
    local dump
    dump=$(su_ "dumpsys notification --zen | sed -n \"/Zen Mode:/,/mUser=/p\"")
    local missing=()
    for marker in "mConfigs\[u=0\]" "automaticRules=" "manualRule=" "deletedRules=" "mUser="; do
        grep -q "$marker" <<<"$dump" || missing+=("$marker")
    done
    if [ ${#missing[@]} -eq 0 ]; then
        record modes_dump_format PASS "$(grep -c 'ZenRule\[' <<<"$dump") rules, $(wc -c <<<"$dump") bytes"
    else
        record modes_dump_format FAIL "missing ${missing[*]}"
    fi
}

# ---- Layout copies, when there are any, are whole: database and manifest agree.
case_copies() {
    local listing
    listing=$(su_ "ls $COPIES 2>/dev/null")
    if [ -z "$listing" ]; then
        record layout_copies SKIP "no copies kept (layout never depended on the module)"
        return
    fi
    local bad=()
    for slot in before latest; do
        grep -q "^$slot.properties$" <<<"$listing" || continue
        grep -q "^$slot.db$" <<<"$listing" || bad+=("$slot.db missing")
        su_ "grep -q ^items= $COPIES/$slot.properties && grep -q ^db= $COPIES/$slot.properties" >/dev/null || bad+=("$slot manifest incomplete")
    done
    if [ ${#bad[@]} -eq 0 ]; then record layout_copies PASS "$(tr '\n' ' ' <<<"$listing")"; else record layout_copies FAIL "${bad[*]}"; fi
}

# ---- Recovery opens and reads the copies while the launcher cannot answer (frozen, not killed).
case_recovery() {
    local pid
    pid=$(sh_ pidof $LAUNCHER)
    [ -n "$pid" ] && su_ "kill -STOP $pid"
    sh_ am start -a android.intent.action.APPLICATION_PREFERENCES -p $MODULE >/dev/null
    sleep 5
    sh_ uiautomator dump /sdcard/ple_ui.xml >/dev/null
    local ui
    ui=$(sh_ cat /sdcard/ple_ui.xml)
    sh_ rm -f /sdcard/ple_ui.xml
    [ -n "$pid" ] && su_ "kill -CONT $pid"
    sh_ input keyevent BACK
    if grep -q "Layout recovery" <<<"$ui" && ! grep -q "can only be read with root" <<<"$ui" && ! grep -q "Looking for copies" <<<"$ui"; then
        record recovery_without_home PASS "opened from app settings with the launcher frozen"
    else
        record recovery_without_home FAIL "screen missing, no root, or copies not read"
    fi
}

# ---- PME: every system_server feature reports ok, and every Mode it says it switched on is on.
case_pme() {
    local health
    health=$(sh_ settings get secure mode_evolved_diagnostics_system)
    if [ "$health" = "null" ] || [ -z "$health" ]; then
        record pme_health SKIP "Pixel Modes Evolved not active"
        return
    fi
    if grep -q '"ok":false' <<<"$health"; then
        record pme_health FAIL "$(grep -o '"feature":"[^"]*","ok":false' <<<"$health" | tr '\n' ' ')"
    else
        record pme_health PASS "$(grep -o '"ok":true' <<<"$health" | wc -l) features ok"
    fi
    local owned off=()
    owned=$(sh_ settings get secure mode_evolved_owned)
    if [ "$owned" != "null" ] && [ -n "$owned" ]; then
        local dump
        dump=$(su_ "dumpsys notification --zen | sed -n \"/Zen Mode:/,/mUser=/p\"")
        for id in ${owned//,/ }; do
            grep -q "ZenRule\[id=$id,state=STATE_TRUE" <<<"$dump" || off+=("$id")
        done
    fi
    # A schedule can switch off a Mode this module switched on; that leaves a stale record, not a fault.
    if [ ${#off[@]} -eq 0 ]; then record pme_owned_modes PASS "${owned/null/none}"; else record pme_owned_modes SKIP "recorded but off now: ${off[*]}"; fi
}

case_restart
case_home
case_modes_format
case_copies
case_recovery
case_pme

record layout_change_interrupt "NOT RUN" "kills the launcher mid-migration; spare device only"
record launcher_crash_loop "NOT RUN" "needs a build that crashes at start; spare device only"
record module_disabled "NOT RUN" "Pixel Launcher deletes module-dependent items; spare device only"
record mode_overlap "NOT RUN" "switches the owner's Modes"
record stock_schedule_bounds "NOT RUN" "needs a test Mode with a schedule and a trigger adb can flip"
record manual_override "NOT RUN" "switches the owner's Modes"
record work_profile "NOT RUN" "no work profile on this phone"

{
    echo "# Regression: $(date -u +%Y-%m-%dT%H:%MZ)"
    echo "# build $BUILD, Pixel Launcher $LAUNCHER_VERSION, module $MODULE_VERSION"
    printf '%s\n' "${RESULTS[@]}"
} > "$OUT"
echo "Wrote $OUT"
