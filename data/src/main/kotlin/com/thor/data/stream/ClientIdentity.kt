package com.thor.data.stream

import android.content.Context
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * THOR's certificate, as a streaming host knows it.
 *
 * GameStream pairing binds a host's trust to a **client certificate**, not to a
 * password: pairing exchanges and verifies it once, and every request afterwards
 * presents it over TLS. That makes this file the credential — losing it means
 * every host has to be paired again, and regenerating it on each launch would
 * mean pairing that never sticks.
 *
 * Generated once, on first use, and kept in the app's private storage. Not in
 * settings, deliberately: a private key does not belong in a document that is
 * backed up, restored onto other devices and read by a serialiser.
 */
@Singleton
class ClientIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private val certFile: File get() = File(directory, CERT_FILE)
    private val keyFile: File get() = File(directory, KEY_FILE)

    @Volatile
    private var cached: Identity? = null

    /** The certificate and key, generating them on first use. */
    @Synchronized
    fun get(): Identity {
        cached?.let { return it }

        val loaded = load() ?: generate()
        cached = loaded
        return loaded
    }

    private fun load(): Identity? = runCatching {
        if (!certFile.exists() || !keyFile.exists()) return null

        val certificate = certFile.inputStream().use { stream ->
            CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
        }
        val key = java.security.KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))

        Identity(certificate, key)
    }.onFailure {
        ThorLog.w(TAG, "Stored identity unreadable; a new one will be made", it)
    }.getOrNull()

    /**
     * A self-signed certificate, in the shape GameStream hosts expect.
     *
     * RSA-2048 and SHA-256, with a twenty-year life: this is not a public
     * identity that anyone revokes, it is a stable name for one device, and a
     * certificate that expires would silently unpair every host on its
     * anniversary.
     */
    private fun generate(): Identity {
        ThorLog.i(TAG, "Generating a client certificate")

        val keys: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(KEY_BITS, SecureRandom())
        }.generateKeyPair()

        val now = System.currentTimeMillis()
        val name = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, COMMON_NAME).build()

        val holder = JcaX509v3CertificateBuilder(
            /* issuer = */ name,
            /* serial = */ BigInteger.valueOf(now),
            /* notBefore = */ Date(now - BACKDATE_MS),
            /* notAfter = */ Date(now + LIFETIME_MS),
            /* subject = */ name,
            /* publicKey = */ keys.public,
        ).build(JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.private))

        val certificate = JcaX509CertificateConverter().getCertificate(holder)

        runCatching {
            certFile.writeBytes(certificate.encoded)
            keyFile.writeBytes(keys.private.encoded)
        }.onFailure {
            // Not fatal for this run — pairing will work — but it will not last,
            // and silently re-pairing forever is worse than a line in the log.
            ThorLog.w(TAG, "Could not store the client identity; pairing will not persist", it)
        }

        return Identity(certificate, keys.private)
    }

    /** The certificate and the key that signs with it. */
    data class Identity(val certificate: X509Certificate, val privateKey: PrivateKey) {

        /**
         * PEM, which is what the protocol carries — hex-encoded, but PEM inside.
         *
         * The host stores this verbatim and compares it later, so the encoding
         * has to match what every other client sends rather than merely being
         * valid.
         */
        val certificatePem: String
            get() = buildString {
                append("-----BEGIN CERTIFICATE-----\n")
                append(
                    android.util.Base64.encodeToString(
                        certificate.encoded,
                        android.util.Base64.NO_WRAP,
                    ).chunked(PEM_LINE).joinToString("\n"),
                )
                append("\n-----END CERTIFICATE-----\n")
            }
    }

    private companion object {
        const val TAG = "Stream"
        const val DIRECTORY = "stream"
        const val CERT_FILE = "client.crt"
        const val KEY_FILE = "client.key"

        const val KEY_BITS = 2048
        const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        const val COMMON_NAME = "NVIDIA GameStream Client"
        const val PEM_LINE = 64

        /** Twenty years: this names a device, and an expiry would unpair it. */
        const val LIFETIME_MS = 20L * 365 * 24 * 60 * 60 * 1000

        /** A day, so a host whose clock runs behind does not reject it outright. */
        const val BACKDATE_MS = 24L * 60 * 60 * 1000
    }
}
