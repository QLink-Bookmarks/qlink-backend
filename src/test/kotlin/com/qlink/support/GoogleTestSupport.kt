@file:Suppress("ktlint:standard:filename")

package com.qlink.support

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

/**
 * 구글 네이티브 id_token 검증 테스트용 키/토큰/JWKS 생성기. 실제 RSA 키쌍으로 토큰을
 * 서명하고, 대응하는 공개키를 구글 certs(JWKS) 응답(모킹된 외부 통신)으로 내려준다.
 */
object GoogleTestKeys {
    const val KEY_ID = "test-google-key"
    const val ISSUER = "https://accounts.google.com"
    const val CLIENT_ID = "qlink-ios.apps.googleusercontent.com"

    private val keyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

    private val otherPrivateKey =
        KeyPairGenerator
            .getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
            .private as RSAPrivateKey

    fun jwks(keyId: String = KEY_ID): String {
        val n = publicKey.modulus.toBase64Url()
        val e = publicKey.publicExponent.toBase64Url()
        return """{"keys":[{"kty":"RSA","kid":"$keyId","use":"sig","alg":"RS256","n":"$n","e":"$e"}]}"""
    }

    fun idToken(
        subject: String,
        audience: String = CLIENT_ID,
        issuer: String = ISSUER,
        keyId: String = KEY_ID,
        expiresAt: Date = Date(System.currentTimeMillis() + 3_600_000),
        signWithWrongKey: Boolean = false,
    ): String =
        JWT
            .create()
            .withKeyId(keyId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withExpiresAt(expiresAt)
            .sign(Algorithm.RSA256(null, if (signWithWrongKey) otherPrivateKey else privateKey))

    private fun BigInteger.toBase64Url(): String {
        val bytes = toByteArray()
        val magnitude = if (bytes.size > 1 && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
        return Base64.getUrlEncoder().withoutPadding().encodeToString(magnitude)
    }
}
