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
