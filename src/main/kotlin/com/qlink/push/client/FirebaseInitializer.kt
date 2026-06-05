package com.qlink.push.client

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

private const val FCM_CONFIG_MISSING_MESSAGE = "FCM service account JSON is not configured."

class FcmConfigurationException(
    message: String = FCM_CONFIG_MISSING_MESSAGE,
) : IllegalStateException(message)

class FirebaseInitializer(
    private val serviceAccountJson: String?,
    private val firebaseAppProvider: FirebaseAppProvider = DefaultFirebaseAppProvider(),
) {
    fun initializeIfConfigured() {
        val credentialJson = serviceAccountJson?.takeIf(String::isNotBlank) ?: return
        if (firebaseAppProvider.hasInitializedApp()) {
            return
        }

        firebaseAppProvider.initialize(credentialJson)
    }

    fun requireInitialized() {
        if (!firebaseAppProvider.hasInitializedApp()) {
            throw FcmConfigurationException()
        }
    }
}

interface FirebaseAppProvider {
    fun hasInitializedApp(): Boolean

    fun initialize(serviceAccountJson: String)
}

private class DefaultFirebaseAppProvider : FirebaseAppProvider {
    override fun hasInitializedApp(): Boolean = FirebaseApp.getApps().isNotEmpty()

    override fun initialize(serviceAccountJson: String) {
        val options =
            FirebaseOptions
                .builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountJson.byteInputStream()))
                .build()

        FirebaseApp.initializeApp(options)
    }
}
