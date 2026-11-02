package app.pengwa.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsTest {
    @Test fun `missing or unsafe configuration disables reporting`() {
        for (key in listOf("", "A-DEV-test", "A-XX-test", "A-EU-", "A-EU-test\n")) {
            assertFalse(AnalyticsConfig(key).valid, key)
        }
        assertTrue(AnalyticsConfig("A-EU-test").valid)
        assertTrue(AnalyticsConfig("A-US-test").valid)
        assertFalse(AnalyticsConfig("A-SH-test").valid)
        for (host in listOf("http://example.com", "https://user@example.com", "https://example.com/path", "https://example.com?secret=1", "https://example.com#x", "not a url")) {
            assertFalse(AnalyticsConfig("A-SH-test", host).valid, host)
        }
        assertTrue(AnalyticsConfig("A-SH-test", "https://stats.example.com/").valid)
        assertFalse(AnalyticsConfig("A-US-test", "https://stats.example.com").valid)
    }

    @Test fun `opt in sends only future events and opt out stops submission`() {
        var consent = false
        val sent = mutableListOf<AnalyticsEvent>()
        val analytics = Analytics(true, { consent }, sent::add)
        analytics.track(AnalyticsEvent.HomeViewed)
        assertTrue(sent.isEmpty())
        consent = true
        analytics.track(AnalyticsEvent.ExchangeStarted)
        consent = false
        analytics.track(AnalyticsEvent.ExchangeSucceeded)
        assertEquals(listOf(AnalyticsEvent.ExchangeStarted), sent)
        consent = true
        assertEquals(1, sent.size) // Previously dropped events are never replayed.
    }

    @Test fun `unconfigured builds never invoke SDK even with saved consent`() {
        var calls = 0
        Analytics(false, { true }) { calls++ }.track(AnalyticsEvent.AppStarted)
        assertEquals(0, calls)
    }

    @Test fun `SDK failures cannot break application actions`() {
        Analytics(true, { true }) { throw IllegalStateException("unavailable SDK") }
            .track(AnalyticsEvent.ExchangeFailed)
    }

    @Test fun `properties contain only documented categories`() {
        val allowed = mapOf(
            "screen" to setOf("home", "nearby", "contacts", "settings"),
            "source" to setOf("nfc", "qr"),
            "direction" to setOf("outgoing"),
            "result" to setOf("success", "failure", "cancelled", "invalid", "self", "peer"),
            "card" to setOf("own", "contact"),
            "setting" to setOf("background_broadcast", "ongoing_notification"),
            "value" to setOf("on", "off"),
            "decision" to setOf("accept", "decline", "expired"),
            "mechanism" to setOf("manual", "nfc_auto", "cooldown", "capacity", "timeout"),
        )
        AnalyticsEvent.entries.forEach { event ->
            event.properties.forEach { (key, value) -> assertTrue(value in allowed.getValue(key)) }
        }
    }
}
