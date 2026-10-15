package az.kilid.app.security

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Təmiz kriptoqrafik funksiyalar (Android-dən asılı deyil, JVM-də test olunur). */
object PinCrypto {
    const val DEFAULT_ROUNDS = 150_000

    fun derive(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /** 1–4 cəhd: gecikmə yox. 5: 30 san, 6: 1 dəq, 7: 5 dəq, 8+: 15 dəq. */
    fun delayMillis(failedAttempts: Int): Long? = when {
        failedAttempts < 5 -> null
        failedAttempts == 5 -> 30_000L
        failedAttempts == 6 -> 60_000L
        failedAttempts == 7 -> 300_000L
        else -> 900_000L
    }
}
