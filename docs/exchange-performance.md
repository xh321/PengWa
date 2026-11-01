# Exchange performance versions

Each branch builds on the previous version; compare adjacent APKs on the same two physical phones.
No Bluetooth throughput or NFC end-to-end result has been measured on physical phones yet.

## V1: phase timings

`adb logcat -s PengWaExchange:D` records monotonic elapsed and stage times, payload bytes and actual MTU.
Each trace has a local attempt number, never a name, UUID, Bluetooth address or image payload.
Discovery traces run from successful NFC/QR identification through target match, received card and completed SQLite write.
Separate BLE traces cover connection, MTU, services, upload, acceptance, download and final ACK.
They intentionally exclude camera startup / NFC field acquisition and first image rendering; `stored` is not a frame-render measurement.
Timings are diagnostic logcat output, with no UI changes.

### Device validation matrix

Use test profiles with 0 / 1 / 3 / maximum-sized images, both directions, NFC and QR.
Test cold discovery and already-discovered peers; add unrelated advertisers and a failing advertiser.
Collect at least 20 successful runs per case and compare P50 / P95 plus failures and actual MTU.
Check accept, decline, pending expiry, disconnect during upload/download, and retry.
For protocol upgrades test new/new, new/old and old/new in both initiator roles.
Verify cards and images survive process restart. Never call transfer success a durable two-device transaction.

UI screenshots: not applicable to V1; logs and internal callbacks only.

## V2: high priority during active data transfer

Request `CONNECTION_PRIORITY_HIGH` for exchange setup, upload and download; return to balanced while waiting for consent and after ACK / cleanup.
Requests are best effort and never bypass consent or affect preview-only connections. Request acceptance is not proof that the controller changed its connection interval.
Validate on battery-powered physical phones; compare V1/V2 with the same MTU and cards. UI screenshots: not applicable; transport parameters only.
