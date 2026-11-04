# AFK Console (Android)
Compose app that connects Minecraft accounts (Java Edition) via MCProtocolLib and runs: timed chat/commands, on-join commands, auto-reply, auto-reconnect, live console, multiple accounts. No movement/anti-AFK packets are sent.

Build: push to GitHub -> Actions -> "Build APK" -> download artifact. Or open in Android Studio.
Notes: logs in with an offline-mode username only (no Microsoft login yet), so it works on offline-mode servers. UNTESTED: not compiled yet; MCProtocolLib signatures may need small fixes.
