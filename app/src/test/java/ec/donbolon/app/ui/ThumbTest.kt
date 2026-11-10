package ec.donbolon.app.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Keystore isn't on the JVM, so a reversible fake stands in for Vault.open. */
class ThumbTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val seal = { b: ByteArray -> byteArrayOf(12) + b.map { (it.toInt() xor 0x5A).toByte() } }
    private val open = { b: ByteArray ->
        require(b[0] == 12.toByte()) { "not sealed" }
        b.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    @Test fun aSealedThumbOpens() {
        val t = Images.unseal(seal(jpeg), open)!!
        assertArrayEquals(jpeg, t.jpeg)
        assertFalse(t.legacy)
    }

    @Test fun aPlainJpegFromOlderVersionsIsReadAndMarkedForSealing() {
        val t = Images.unseal(jpeg, open)!!
        assertArrayEquals(jpeg, t.jpeg)
        assertTrue(t.legacy)
    }

    @Test fun garbageIsNothing() {
        assertNull(Images.unseal(byteArrayOf(1, 2, 3, 4), open))
    }
}
