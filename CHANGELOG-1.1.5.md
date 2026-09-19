# VPN Man v1.1.5

Stability rollback for the VPN traffic path.

- Restored the exact known-good v1.1.0 MainActivity, VpnService TUN path and XrayConfigFactory behavior.
- Removed background Android Xray proxy-health cores from the normal refresh path; only lightweight TCP latency tests run in the UI.
- Kept the newer panel/API parsing, manual/free server separation, ads and UI improvements.
- Free servers stay visible; server-side/client historical health data can still be displayed without spawning temporary Xray cores.
- Version bumped to 1.1.5 / versionCode 9.
- Workflow now names artifacts correctly and builds signed Release when persistent signing secrets exist, otherwise a clearly-labeled Debug test APK.
