package com.nexusmount.app.security

import android.util.Base64
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.random.Random

/**
 * TOTP (RFC 6238) básico para 2FA.
 * Secretos en Base32 simplificado (hex interno).
 */
object TotpHelper {

    fun generateSecret(): String {
        val bytes = Random.Default.nextBytes(20)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
            .trimEnd('=')
    }

    fun currentCode(secretBase64: String, timeStepSec: Long = 30L): String {
        val key = Base64.decode(padBase64(secretBase64), Base64.URL_SAFE)
        val counter = System.currentTimeMillis() / 1000L / timeStepSec
        return generateHotp(key, counter).toString().padStart(6, '0').takeLast(6)
    }

    fun verify(secretBase64: String, code: String, window: Int = 1): Boolean {
        val key = Base64.decode(padBase64(secretBase64), Base64.URL_SAFE)
        val counter = System.currentTimeMillis() / 1000L / 30L
        for (i in -window..window) {
            val expected = generateHotp(key, counter + i).toString().padStart(6, '0').takeLast(6)
            if (expected == code.trim()) return true
        }
        return false
    }

    private fun generateHotp(key: ByteArray, counter: Long): Int {
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = (hash.last() and 0x0f).toInt()
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return binary % 1_000_000
    }

    private fun padBase64(s: String): String {
        val p = (4 - s.length % 4) % 4
        return s + "=".repeat(p)
    }
}
