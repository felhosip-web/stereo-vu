# Stereo VU 2x20 Overlay - Bluetooth Stereo

## Hogyan lesz APK Android Studio nélkül?

1. Csinálj egy GitHub repo-t (pl. `stereo-vu`)
2. Töltsd fel ennek a mappának a tartalmát
3. Menj a GitHub-on: Actions fül -> Build APK -> Run
4. 2-3 perc után az Artifacts alatt letöltheted az `app-debug.apk`-t
5. Telefonon telepíted (Ismeretlen forrás engedélyezése)

## Mit csinál?
- Lebegő ablak YouTube felett
- 2x20 LED, 17 zöld + 3 piros
- Peak hold 0.9s + lassú visszaesés
- Belső hang Playback Capture -> Bluetooth fülessel is megy sztereóban
- True Peak mérési mód: egyedi 4×-es túlmintavételezett interpolációs csúcsérték-becslés (polyphase FIR szűrő inter-sample túllövések detektálására; a szűrőegyütthatók eltérnek az ITU-R BS.1770-5 2. mellékletének referencia-szűrőjétől)