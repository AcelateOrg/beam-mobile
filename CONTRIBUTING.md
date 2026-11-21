# Contributing

Thanks for helping improve Beam Mobile. Pull requests and issues are welcome.

## Before you start

- Open an issue first for larger changes, so we can agree on the approach.
- By submitting a pull request you agree to the [CLA](CLA.md). Code under
  copyleft licenses (GPL and similar) can't be accepted.
- Security issues: see [SECURITY.md](SECURITY.md), not public issues.
- VPN, profile and subscription logic lives in
  [beam-bridge](https://github.com/AcelateOrg/beam-bridge); send such changes there.

## Setup

Clone the sibling repositories into one folder: `beam-mobile`, `beam-bridge`,
`xray-core-beam`, `beam-tun`. Build steps are in the [README](README.md).

## Process

1. Fork, create a branch, keep the change focused.
2. Make sure `./gradlew assembleDebug` builds.
3. Check UI changes on a real device with `./gradlew assemblePreview`
   (debug builds are much slower than what users get).
4. Open a pull request: what changed and why, how you tested it, and
   screenshots for UI changes.

## Code style

- Kotlin official style, Jetpack Compose, Material 3 Expressive components.
- Keep business logic in beam-bridge; the app only shows state and calls the bridge.
- Never commit signing keys or `keystore.properties`.
