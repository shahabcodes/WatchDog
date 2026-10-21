package com.shahaabapps.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Firebase App Check: every request to Firebase (database, storage, sign-in,
 * Cloud Functions) carries a Google Play Integrity attestation that it comes from
 * the genuine, correctly signed Watch Dog / Shield app. Until enforcement is
 * switched on in the Firebase console, missing or invalid tokens are only
 * counted, never rejected. Call once at app start.
 */
object AppCheckSetup {
    fun install(context: Context) {
        runCatching {
            FirebaseApp.initializeApp(context)
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
