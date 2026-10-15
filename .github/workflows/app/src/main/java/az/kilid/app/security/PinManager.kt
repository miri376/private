package az.kilid.app.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class PinResult {
    object Success : PinResult()
    data class Wrong(val remainingBeforeDelay: Int) : PinResult()
    data class LockedOut(val untilMillis: Long) : PinResult()
}

/** PIN-i açıq mətn kimi saxlamır: salt + PBKDF2 hash, nəticə isə Android Keystore açarı ilə şifrələnir. */
object KeystoreBox {
    private const val ALIAS = "kilid_secure_box"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): ByteArray? = try {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.doFinal(body)
    } catch (e: Exception) {
        null
    }

    fun delete() {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }
}

class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("kilid_secure", Context.MODE_PRIVATE)

    private class Record(
        val salt: ByteArray,
        val hash: ByteArray,
        val rounds: Int,
        val failed: Int,
        val lockedUntil: Long
    )

    private fun load(): Record? {
        val encoded = prefs.getString("record", null) ?: return null
        val bytes = KeystoreBox.decrypt(encoded) ?: return null
        return try {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            Record(
                salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP),
                hash = Base64.decode(o.getString("hash"), Base64.NO_WRAP),
                rounds = o.getInt("rounds"),
                failed = o.getInt("failed"),
                lockedUntil = o.getLong("lockedUntil")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun save(r: Record) {
        val json = JSONObject().apply {
            put("salt", Base64.encodeToString(r.salt, Base64.NO_WRAP))
            put("hash", Base64.encodeToString(r.hash, Base64.NO_WRAP))
            put("rounds", r.rounds)
            put("failed", r.failed)
            put("lockedUntil", r.lockedUntil)
        }
        prefs.edit().putString("record", KeystoreBox.encrypt(json.toString().toByteArray(Charsets.UTF_8))).apply()
    }

    fun hasPin(): Boolean = load() != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val rounds = PinCrypto.DEFAULT_ROUNDS
        save(Record(salt, PinCrypto.derive(pin, salt, rounds), rounds, 0, 0L))
    }

    fun currentLockout(now: Long = System.currentTimeMillis()): Long? =
        load()?.lockedUntil?.takeIf { it > now }

    fun verify(pin: String, now: Long = System.currentTimeMillis()): PinResult {
        val r = load() ?: return PinResult.Wrong(0)
        if (r.lockedUntil > now) return PinResult.LockedOut(r.lockedUntil)

        val candidate = PinCrypto.derive(pin, r.salt, r.rounds)
        if (PinCrypto.constantTimeEquals(candidate, r.hash)) {
            save(Record(r.salt, r.hash, r.rounds, 0, 0L))
            return PinResult.Success
        }

        val failed = r.failed + 1
        val delay = PinCrypto.delayMillis(failed)
        val until = if (delay != null) now + delay else 0L
        save(Record(r.salt, r.hash, r.rounds, failed, until))
        return if (delay != null) PinResult.LockedOut(until) else PinResult.Wrong(maxOf(0, 5 - failed))
    }

    fun wipe() {
        prefs.edit().clear().apply()
        KeystoreBox.delete()
    }
}
