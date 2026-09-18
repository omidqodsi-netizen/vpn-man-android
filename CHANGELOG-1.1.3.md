# VPN Man Android 1.1.3

- Removed the fatal post-TUN delay verification that caused long CONNECTING states and false "server did not respond" errors.
- Xray TUN now starts with the Android fd directly, port 0, MTU 1400, IPv4-first routing.
- Free-config Xray probes are serialized to avoid native runtime races.
- Personal servers remain visible even when free-server tests fail.
- Protocol is recovered from the config URI when older panel data has a missing/incorrect protocol field.
- APK native libraries are compressed and release shrinking is enabled to reduce download size.
- GitHub Actions now requires a persistent release signing key so future APKs can update over one another.
