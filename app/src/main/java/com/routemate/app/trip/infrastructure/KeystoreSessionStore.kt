package com.routemate.app.trip.infrastructure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.routemate.app.trip.domain.*
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreSessionStore(context: Context, serverScope: String) : SessionStore {
    // Credentials must never be reused when a developer changes the backend origin.
    private val scope = MessageDigest.getInstance("SHA-256").digest(serverScope.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private val preferences = context.getSharedPreferences("routemate_session_$scope", Context.MODE_PRIVATE)
    private val alias = "routemate_session_key_$scope"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun load(): String? {
        val stored = preferences.getString("credential", null) ?: return null
        try {
            val parts = stored.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { throw TripRegistrationException(TripFailure.UNAUTHORIZED) }
    }

    override fun save(token: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encrypted = Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            check(preferences.edit().putString("credential", "$iv:$encrypted").commit())
        } catch (_: Exception) { throw TripRegistrationException(TripFailure.UNAUTHORIZED) }
    }
}
