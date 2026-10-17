package com.example.mydeskrobot.data.proxy

import com.example.mydeskrobot.domain.proxy.PairingQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingQrCodecTest {

    @Test
    fun encodeDecode_roundTrip() {
        val payload = PairingQrPayload(
            name = "MyDeskRobot",
            ip = "192.168.1.42",
            port = 8765,
            token = "tok-abc",
            robotId = "robot-1",
        )
        val decoded = PairingQrCodec.decode(PairingQrCodec.encode(payload))
        assertNotNull(decoded)
        assertEquals(payload.ip, decoded!!.ip)
        assertEquals(payload.port, decoded.port)
        assertEquals(payload.token, decoded.token)
        assertEquals(payload.robotId, decoded.robotId)
        assertEquals(payload.name, decoded.name)
    }

    @Test
    fun decode_deepLink() {
        val uri =
            "mydeskrobot://pair?v=1&ip=10.0.0.2&port=8765&token=secret&robotId=r1&name=Bot"
        val decoded = PairingQrCodec.decode(uri)
        assertNotNull(decoded)
        assertEquals("10.0.0.2", decoded!!.ip)
        assertEquals(8765, decoded.port)
        assertEquals("secret", decoded.token)
        assertEquals("r1", decoded.robotId)
    }

    @Test
    fun decode_invalid_returnsNull() {
        assertNull(PairingQrCodec.decode(""))
        assertNull(PairingQrCodec.decode("{not-json"))
        assertNull(PairingQrCodec.decode("""{"ip":"","token":"x","robotId":"y"}"""))
    }
}
