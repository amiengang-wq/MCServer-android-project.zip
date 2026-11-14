#!/bin/bash
# Pobiera JRE 8 (OpenJDK 8 pod Android 4.4, ARM32) z releasu Google61/PojavLauncher-android4,
# odchudza je i zapisuje jako app/src/main/assets/jre8-arm.tar.gz
set -e
cd "$(dirname "$0")"
W=$(mktemp -d)
curl -fL -o "$W/full.apk" https://github.com/Google61/PojavLauncher-android4/releases/download/ics_old/app-debug.apk
unzip -q "$W/full.apk" assets/components/jre/bin-arm.tar.xz -d "$W"
mkdir "$W/jre"
tar xJf "$W/assets/components/jre/bin-arm.tar.xz" -C "$W/jre"
cd "$W/jre"
rm -rf man lib/images lib/fonts lib/jfr lib/jfr.jar lib/applet lib/jvm.hprof.txt
rm -f lib/aarch32/client/libjsig.so; cp lib/aarch32/libjsig.so lib/aarch32/client/libjsig.so
cd - >/dev/null
mkdir -p app/src/main/assets
tar --owner=0 --group=0 -C "$W/jre" -cf - . | gzip -9 > app/src/main/assets/jre8-arm.tar.gz
ls -la app/src/main/assets/jre8-arm.tar.gz
rm -rf "$W"
