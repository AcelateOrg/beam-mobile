# Beam Mobile

Android client for the **BEAM** VPN: Kotlin, Jetpack Compose, Material 3 Expressive.

The app is a thin shell. Profiles, subscriptions, routing, the network core
and statistics all live in the shared core
[`beam-bridge`](https://github.com/AcelateOrg/beam-bridge), the same one the
desktop client uses. It reaches the app as a gomobile AAR built from
`beam-bridge/mobile`.

## Features

- VLESS / VMess / Trojan / Shadowsocks / Hysteria2, subscriptions and `.beam` profiles
- Import by link, file or QR code
- Server list with latency, node editor
- Split tunneling by app
- Traffic statistics per profile, server and app; live logs
- Quick Settings tile, tunnel watchdog that reconnects after network changes

## How it works

```
Compose UI (ui/)  ──►  VpnController / BeamVpnService (vpn/)  ──►  beam-bridge.aar
                                   │                                    │
                         Android VpnService (TUN fd)  ──────────►  beam-core + TUN stack
```

`BeamVpnService` opens the Android VPN interface and hands its file descriptor
to beam-bridge, which runs the core in-process. The UI talks to the bridge
through small JSON calls (`vpn/BridgeJson.kt`, `vpn/BridgeRuntime.kt`).

## Layout

```
app/src/main/java/com/beamvpn/app/
  ui/         screens: home, profiles, servers, settings, onboarding; theme
  vpn/        VPN service, controller, bridge calls, routing, statistics
  settings/   local settings store
scripts/      build-bridge.ps1: builds app/libs/beam-bridge.aar
```

## Build

Needs JDK 17+ (Android Studio's bundled JBR works), the Android SDK and,
for the bridge, Go, [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)
and an Android NDK. `beam-bridge`, `xray-core-beam` and `beam-tun` must sit
next to this folder.

1. Bridge AAR: `./scripts/build-bridge.ps1` writes `app/libs/beam-bridge.aar`.
2. App:
   - `./gradlew assembleDebug` for development;
   - `./gradlew assemblePreview` for testing speed: a release-optimized build
     signed with the debug key (debug builds are noticeably slower);
   - `./gradlew assembleRelease` for a release build; it is signed only when
     `keystore.properties` is present.

APKs are split per ABI and land in `app/build/outputs/apk/`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: [SECURITY.md](SECURITY.md).

## License

Beam Mobile is source-available under the Beam Source License 1.0
([LICENSE](LICENSE), Russian translation in [LICENSE.ru.md](LICENSE.ru.md));
the English text is legally binding. "Beam" and "Acelate" are trademarks, see
[TRADEMARKS.md](TRADEMARKS.md). Contributions are accepted under
[CLA.md](CLA.md). beam-core and beam-tun are MPL-2.0.
