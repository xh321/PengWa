package app.pengwa.exchange

import kotlin.random.Random

/** Randomized foreground windows let two phones discover complementary NFC roles. */
class AutomaticNfcSession(
    private val state: ExchangeModeState,
    private val canDisplay: Boolean,
    private val random: Random = Random.Default,
) {
    private val owner = state.beginAutomaticNfc(
        if (canDisplay && random.nextBoolean()) ExchangeMode.NfcDisplay else ExchangeMode.NfcRead,
    )

    // Deliberately independent between devices, including devices opened at the same time.
    fun nextDelayMillis(): Long = random.nextLong(1_500L, 3_001L)

    fun advance(): Boolean = canDisplay && state.advanceAutomaticNfc(owner)

    /** A disposed session must not turn off a newer page's NFC or Bluetooth session. */
    fun close() = state.endAutomaticNfc(owner)
}
