package com.desk.dashboard

import com.desk.dashboard.board.GridLayout
import com.desk.dashboard.board.TileSpec
import com.desk.dashboard.remote.Protocol
import com.desk.dashboard.remote.RemoteHost
import com.desk.dashboard.remote.ScreenInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProtocolTest {
    private val screen = ScreenInfo(4, 3, 400f, 300f)
    private fun t(id: String, x: Int, y: Int, w: Int = 1, h: Int = 1) = TileSpec(id, "clock", x, y, w, h)

    @Test fun proofDependsOnCodeAndCertificate() {
        val fp = "ab".repeat(32)
        assertEquals(Protocol.proof("123456", fp), Protocol.proof("123456", fp))
        assertNotEquals(Protocol.proof("123456", fp), Protocol.proof("123457", fp))
        // A stand-in's certificate gives a different proof, so the tablet rejects a relayed pairing.
        assertNotEquals(Protocol.proof("123456", fp), Protocol.proof("123456", "cd".repeat(32)))
    }

    @Test fun versionsTalkWhenEachMeetsTheOthersMinimum() {
        assertTrue(Protocol.compatible(Protocol.PROTOCOL, Protocol.MIN_PROTOCOL))
        // A newer build that still accepts ours.
        assertTrue(Protocol.compatible(Protocol.PROTOCOL + 1, Protocol.PROTOCOL))
        // A newer build that has dropped ours.
        assertFalse(Protocol.compatible(Protocol.PROTOCOL + 2, Protocol.PROTOCOL + 1))
        // Older than we still speak.
        assertFalse(Protocol.compatible(Protocol.MIN_PROTOCOL - 1, 0))
    }

    @Test fun layoutsFromAPhoneAreChecked() {
        assertTrue(RemoteHost.valid(GridLayout(4, 3, listOf(t("a", 0, 0, 2, 2), t("b", 2, 0))), screen))
        assertFalse("overlap", RemoteHost.valid(GridLayout(4, 3, listOf(t("a", 0, 0, 2, 2), t("b", 1, 1))), screen))
        assertFalse("off the grid", RemoteHost.valid(GridLayout(4, 3, listOf(t("a", 3, 0, 2, 1))), screen))
        assertFalse("duplicate id", RemoteHost.valid(GridLayout(4, 3, listOf(t("a", 0, 0), t("a", 1, 0))), screen))
        assertFalse("other grid size", RemoteHost.valid(GridLayout(5, 3, emptyList()), screen))
    }

    @Test fun messagesKeepNestedObjects() {
        val m = Protocol.obj("list" to listOf(Protocol.obj("id" to "x")), "names" to listOf("a", "b"), "skip" to null)
        assertTrue((m["list"] as JsonArray)[0] is JsonObject)
        assertEquals(listOf("a", "b"), with(Protocol) { m.strings("names") })
        assertFalse("skip" in m)
    }
}
