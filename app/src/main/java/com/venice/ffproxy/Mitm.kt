package com.venice.ffproxy

import android.util.Base64
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.littleshoot.proxy.MitmManager
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession

object CaStore {
    private val PASS = "ffproxy".toCharArray()
    private val CA_NAME = X500Name("CN=FFProxy Root CA,O=Venice,C=BR")

    private var caKey: PrivateKey? = null
    private var caCert: X509Certificate? = null
    private var leafCache = ConcurrentHashMap<String, SslContext>()
    private var clientCtx: SslContext? = null

    fun init(filesDir: File) {
        val dir = File(filesDir, "ca").apply { mkdirs() }
        val store = File(dir, "ca.p12")

        if (store.exists()) {
            runCatching {
                val ks = KeyStore.getInstance("PKCS12")
                store.inputStream().use { ks.load(it, PASS) }
                val alias = ks.aliases().toList().first()
                caKey = ks.getKey(alias, PASS) as PrivateKey
                caCert = ks.getCertificate(alias) as X509Certificate
            }
        }

        if (caKey == null || caCert == null) {
            generateCa()
            runCatching {
                val ks = KeyStore.getInstance("PKCS12")
                ks.load(null, null)
                ks.setKeyEntry("ca", caKey, PASS, arrayOf(caCert))
                store.outputStream().use { ks.store(it, PASS) }
            }
        }
        leafCache = ConcurrentHashMap()
    }

    fun certPem(): String {
        val b64 = Base64.encodeToString(caCert!!.encoded, Base64.NO_WRAP)
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n"
    }

    fun serverContext(host: String): SslContext {
        val key = host.ifBlank { "localhost" }
        return leafCache.getOrPut(key) {
            val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val kp = kpg.generateKeyPair()
            val cert = signLeaf(key, kp.public)
            SslContextBuilder.forServer(kp.private, cert)
                .sslProvider(SslProvider.JDK)
                .build()
        }
    }

    fun clientContext(): SslContext = clientCtx ?: SslContextBuilder.forClient()
        .sslProvider(SslProvider.JDK)
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build().also { clientCtx = it }

    private fun generateCa() {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val kp = kpg.generateKeyPair()
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            CA_NAME, BigInteger.valueOf(now),
            Date(now - 86400000L), Date(now + 10L * 365 * 86400000L),
            CA_NAME, kp.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        caCert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        caKey = kp.private
    }

    private fun signLeaf(host: String, pub: PublicKey): X509Certificate {
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=" + host.replace(",", "_"))
        val builder = JcaX509v3CertificateBuilder(
            CA_NAME, BigInteger.valueOf(now),
            Date(now - 86400000L), Date(now + 3L * 365 * 86400000L),
            subject, pub
        )
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage, false,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        val isIp = host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
        val san = if (isIp) {
            GeneralNames(GeneralName(GeneralName.iPAddress, host))
        } else {
            GeneralNames(GeneralName(GeneralName.dNSName, host))
        }
        builder.addExtension(Extension.subjectAlternativeName, false, san)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}

class MitmManagerImpl : MitmManager {

    override fun serverSslEngine(peerHost: String, peerPort: Int): SSLEngine {
        val engine = CaStore.clientContext()
            .newEngine(UnpooledByteBufAllocator.DEFAULT, peerHost, peerPort)
        engine.useClientMode = true
        return engine
    }

    override fun serverSslEngine(): SSLEngine = serverSslEngine("localhost", 443)

    override fun clientSslEngineFor(
        httpRequest: HttpRequest,
        serverSslSession: SSLSession
    ): SSLEngine {
        val host = serverSslSession.peerHost?.takeIf { it.isNotBlank() } ?: "localhost"
        val engine = CaStore.serverContext(host)
            .newEngine(UnpooledByteBufAllocator.DEFAULT, host, 443)
        engine.useClientMode = false
        return engine
    }
}