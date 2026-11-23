# Tasks: Share by tap

**Input**: [spec.md](spec.md), [plan.md](plan.md). **Tests**: included (constitution V).

## Phase 1: Protocol core (`:core:share`)

- [ ] S001 Module `:core:share` (Kotlin JVM, BouncyCastle, kotlinx-serialization, JUnit 5)
- [ ] S002 `Identity` (Ed25519 + X25519), sign/verify helpers
- [ ] S003 `NoiseXX` handshake with initiator commitment, `CipherState` transport, tamper tests
- [ ] S004 `SafetyTiles` (4 of 16 from the handshake hash)
- [ ] S005 `Hlc` hybrid logical clock
- [ ] S006 `FieldRecord`, `RosterEntry` (signed), wire messages
- [ ] S007 `ShareStore` interface and an in-memory fake
- [ ] S008 `ShareEngine`: diff local rows to records, summaries, missing records, merge, conflicts
- [ ] S009 `TapProtocol`: reader and card state machines (pair, confirm, invite, sync), all or nothing
- [ ] S010 `ApduFraming` with chaining; reader/card loopback test
- [ ] S011 Tests: two- and three-phone gossip, conflicts, delete/resurrect, removal, broken tap

## Phase 2: Storage (`:core:data`)

- [ ] S012 Room v5 tables `shared_list`, `share_member`, `share_peer`, `share_field`; migration 4→5
- [ ] S013 `RoomShareStore` implementing `ShareStore` over the task tables

## Phase 3: Android (`:app`)

- [ ] S014 `KeystoreIdentityStore`
- [ ] S015 `ShareCardService` (HostApduService, AID `F0444E54415053`) and `apduservice.xml`
- [ ] S016 `TapReader` (reader mode, IsoDep, 2 s timeout)
- [ ] S017 Screens: list menu item, share by tap, check tiles, invite, tap to sync result, sharing
  page section, lists tile glyph; app bar "tap to sync"
- [ ] S018 Switching away from "this phone only" leaves shared lists (FR-812)
- [ ] S019 Screenshot tests, light and dark

## Phase 4: Before release

- [ ] S020 Measure tap timing and throughput on two real phones; set the snapshot size limit
