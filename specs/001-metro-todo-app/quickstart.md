# Quickstart: build, run and validate

## Prerequisites

- Android Studio (latest stable) with JDK 17, an emulator or phone on Android 8.0+.
- For real sync: the Google and Microsoft client IDs from [plan.md](plan.md#external-setup-you-will-need),
  added to `local.properties`:

```properties
google.webClientId=...apps.googleusercontent.com
msal.clientId=00000000-0000-0000-0000-000000000000
msal.signatureHash=...
```

## Commands

```bash
./gradlew assembleDebug            # build
./gradlew test                     # unit + contract + sync tests
./gradlew verifyRoborazziDebug     # screenshot tests (dark + light)
./gradlew ktlintCheck lint         # style and Android Lint
./gradlew installDebug             # put it on the device
```

## Validation scenarios

| # | Scenario | Expected |
|---|---|---|
| V1 | Debug build, choose "demo account" | Pivot shows two sample lists; all of US1's acceptance scenarios pass in airplane mode |
| V2 | Open the component gallery (debug menu) | Every component in research R3 renders in dark and light; matches `docs/design/metro-mockups.svg` |
| V3 | Sign in to Google with a test account | Existing lists appear; a task added on the phone appears at tasks.google.com within a minute |
| V4 | Complete a task on the web, pull to refresh | Task moves to "completed" on the phone |
| V5 | Edit the same task on web and phone while offline, reconnect | Newest edit wins; the other is in settings > sync log |
| V6 | Sign in to Microsoft with a test account | Lists, steps and importance appear and sync both ways |
| V7 | Settings > account > switch service | Warning dialog; after confirming, no old-provider data remains (check `adb shell run-as ... ls databases` row counts) |
| V8 | Developer options > animator duration scale off | All transitions become instant |
| V9 | `./gradlew :core:sync:test --tests '*Soak*'` | 200-operation soak passes with zero duplicates or losses (SC-004) |
