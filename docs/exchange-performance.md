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
For protocol upgrades test matching versions; mismatched versions must not exchange cards.
Verify cards and images survive process restart. Never call transfer success a durable two-device transaction.

UI screenshots: not applicable to V1; logs and internal callbacks only.

## V2: high priority during active data transfer

Request `CONNECTION_PRIORITY_HIGH` for exchange setup, upload and download; return to balanced while waiting for consent and after ACK / cleanup.
Requests are best effort and never bypass consent or affect preview-only connections. Request acceptance is not proof that the controller changed its connection interval.
Validate on battery-powered physical phones; compare V1/V2 with the same MTU and cards. UI screenshots: not applicable; transport parameters only.

## V3: binary image wire encoding (breaking protocol)

Service UUID ends in `bac3` and discovery marker is D3. No old protocol negotiation or fallback; both phones must use the same version. Old service / D2 / legacy card QR codes are not accepted.
Only identity preview, status and CARD characteristics are served; legacy identity exchange is removed.
Network cards require PWC3: same field order as local PWC2 but each avatar/block image is `[int32 length][raw JPEG/PNG bytes]` instead of Base64 UTF.
All lengths are checked before allocation; 12 KiB/image, 96 KiB/wire and the existing 96 KiB local serialization limit still apply.
Local persisted cards remain readable; this is storage preservation, not network compatibility.
For image bytes only this removes approximately 25% of the old wire payload, without lossy recompression.
QR pixels change with the new discovery marker. Emulator screenshots show the running QR fixture before/after; no physical NFC/BLE performance result is claimed.

## V4: consent before image upload (breaking protocol)

D4 / service suffix `bac4`; V3/V4 cannot exchange. REQUEST characteristic is mandatory and accepts framed basic identity only (18–49 bytes).
After the small request, wait for consent; only Accepted sessions may upload a full PWC3 card.
The full card must match the accepted UUID, updates the same session without another prompt, and must complete before the server allows card download.
Only final ACK records a card. Decline, expiry, disconnect and identity substitution cannot complete a card.
This improves time-to-prompt and avoids sending images before consent; it does not hide image transfer time.

## V5: direct advertisement matching and preview connection reuse

Breaking D5 discovery: `[D5][UUID 16][process-session locator int64]`, 25 bytes. Service suffix `bac5`.
An 8-byte random locator is generated once per process, shared by QR, NFC and BLE scan-response service data. It is not a stable ID or authentication credential.
The 128-bit service-data AD entry fits the 31-byte legacy scan response (26 bytes including AD headers); the main advertisement still carries the service UUID.
Controller matches locator as soon as a scan callback arrives, bypassing the UI's 300 ms publish delay and unrelated preview reads.
Exchange requests interrupt unrelated active previews. If matching follows a preview read, a single main-loop turn allows upgrading that same GATT connection into an exchange, preserving MTU/services.
Every targeted connection verifies the identity preview before sending its request; the completed card is still checked against the expected UUID.
The locator rotates on process restart; QR must be rescanned after a peer process restart. No old-version fallback.

## V6: windowed notification download

Breaking D6 / service suffix `bac6`; STREAM notify characteristic and CCCD are mandatory. No read/cursor fallback.
After an accepted full upload, client subscribes, then writes `[offset=0,total=-2]` to start download.
Server sends at most eight `[offset,total,payload]` frames, never larger than `min(MTU-3,512)`, waiting for `onNotificationSent` before each next frame.
Client grants each next window with `[next-offset,-2]`. Server rejects unexpected/duplicate credits and never advances an outstanding Android send on a credit alone.
Client serializes control writes even when notifications arrive before the GATT write callback.
All bytes must assemble in order with constant length, decode as PWC3 and match expected UUID before final `[-1,0]` ACK.
Server requires a validated accepted incoming card and every outgoing byte offered before accepting final ACK; failed sends / disconnects do not save an incomplete card.
At large MTU, this replaces two read/write operations per download frame with one credit write per eight notifications. Notification send pacing still depends on Android and hardware.
No retransmission/resume is attempted after corruption or disconnect: abort and retry the complete exchange, never declare success with missing images.
