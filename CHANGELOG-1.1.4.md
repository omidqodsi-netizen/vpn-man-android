# VPN Man v1.1.4

- Restored the known-good v1.1.0 Android TUN transport behavior.
- Restored blocking VpnService TUN fd on Android 10+.
- Restored IPv6 route and IPv6 DNS through the VPN.
- Restored MTU 1500 in Android VpnService and Xray TUN inbound.
- Removed the 250 ms synthetic startup delay/generation path that was not present in the working build.
- Kept v1.1.2/v1.1.3 server grouping, free-source handling, UI fixes and parser compatibility improvements.
