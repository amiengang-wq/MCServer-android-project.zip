MC Server (Android 4.4.2+, ARM 32-bit) - serwer Minecraft 1.12.2 + wbudowana Java 8

BUDOWA: patrz poradnik (GitHub Actions) albo: bash get-jre.sh, potem Android Studio -> Build APK.
APK ma ok. 41 MB - w srodku jest JRE 8 (OpenJDK 8 pod Androida API 19, z Google61/PojavLauncher-android4,
licencja GPLv2+Classpath Exception, pliki LICENSE w tym folderze).

UZYCIE:
 1. Zainstaluj APK, uruchom, kliknij "Instaluj":
    - rozpakuje wbudowane JRE (ok. 100 MB w pamieci wewnetrznej, 1-3 min),
    - pobierze server.jar 1.12.2 z Mojang (sprawdza SHA-1) - albo wezmie server.jar z /sdcard/MCServer/.
 2. "Start". IP serwera widac u gory ekranu, port 25565.
 3. RAM: pole "RAM (MB)" - na starych telefonach zostaw 256-384.
Potrzebujesz min. ok. 250 MB wolnego miejsca w pamieci wewnetrznej.
