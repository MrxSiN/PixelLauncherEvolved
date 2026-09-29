#!/usr/bin/env bash
# Builds a tiny icon pack for device tests of the icon pack feature.
#
# It follows the ADW convention (an activity answering org.adw.launcher.THEMES,
# an appfilter.xml asset, a drawable.xml resource) and maps a few Google apps to
# plain coloured shapes, so a mapped icon is unmistakable on the home screen. The
# calendar is mapped as a dynamic calendar (cal_1 .. cal_31).
#
#   scripts/test-icon-pack.sh [version]      -> build/test-icon-pack.apk
#   adb install -r build/test-icon-pack.apk
#
# Pass a higher version to exercise the pack-update path.
set -euo pipefail
unset MSYS_NO_PATHCONV  # aapt2 is a Windows binary and needs converted paths
ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${1:-1}
SDK=${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}
TOOLS=$(ls -d "$SDK"/build-tools/* | tail -1)
PLATFORM=$(ls -d "$SDK"/platforms/android-* | tail -1)
WORK="$ROOT/build/test-icon-pack"
rm -rf "$WORK"; mkdir -p "$WORK/res/drawable" "$WORK/res/xml" "$WORK/assets"

cat > "$WORK/AndroidManifest.xml" <<EOF
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="my.github.mrxsin.testiconpack" android:versionCode="$VERSION" android:versionName="$VERSION">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
    <application android:label="PLE Test Icons v$VERSION" android:icon="@drawable/pack" android:hasCode="false">
        <activity android:name=".Pack" android:exported="true">
            <intent-filter>
                <action android:name="org.adw.launcher.THEMES"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

shape() { # name colour
    cat > "$WORK/res/drawable/$1.xml" <<EOF
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="$2"/><corners android:radius="12dp"/><size android:width="108dp" android:height="108dp"/>
</shape>
EOF
}
shape pack "#FF6D00"
shape chrome "#D50000"
shape gmail "#00C853"
shape dialer "#2962FF"
shape spare "#AA00FF"
# Version 2 recolours Chrome, so an update is visible.
[ "$VERSION" -ge 2 ] && shape chrome "#FFD600"
for day in $(seq 1 31); do
    hue=$(( (day * 8) % 256 ))
    shape "cal_$day" "$(printf '#%02X40%02X' "$hue" $((255 - hue)))"
done

cat > "$WORK/assets/appfilter.xml" <<'EOF'
<resources>
    <item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="chrome"/>
    <item component="ComponentInfo{com.google.android.gm/com.google.android.gm.ConversationListActivityGmail}" drawable="gmail"/>
    <item component="ComponentInfo{com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity}" drawable="dialer"/>
    <item component="ComponentInfo{com.google.android.gm/com.example.Missing}" drawable="does_not_exist"/>
    <calendar component="ComponentInfo{com.google.android.calendar/com.android.calendar.AllInOneActivity}" prefix="cal_"/>
</resources>
EOF

{
    echo '<resources>'
    for name in chrome gmail dialer spare; do echo "    <item drawable=\"$name\"/>"; done
    echo '</resources>'
} > "$WORK/res/xml/drawable.xml"

"$TOOLS/aapt2" compile --dir "$WORK/res" -o "$WORK/res.zip"
"$TOOLS/aapt2" link -o "$WORK/unsigned.apk" -I "$PLATFORM/android.jar" \
    --manifest "$WORK/AndroidManifest.xml" -A "$WORK/assets" "$WORK/res.zip"
"$TOOLS/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
[ -f "$WORK/../test-icon-pack.jks" ] || "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$WORK/../test-icon-pack.jks" \
    -storepass testpack -keypass testpack -alias pack -keyalg RSA -validity 10000 -dname CN=test >/dev/null 2>&1
"$TOOLS/apksigner.bat" sign --ks "$WORK/../test-icon-pack.jks" --ks-pass pass:testpack \
    --out "$ROOT/build/test-icon-pack.apk" "$WORK/aligned.apk" 2>/dev/null \
  || "$TOOLS/apksigner" sign --ks "$WORK/../test-icon-pack.jks" --ks-pass pass:testpack \
    --out "$ROOT/build/test-icon-pack.apk" "$WORK/aligned.apk"
echo "$ROOT/build/test-icon-pack.apk"
