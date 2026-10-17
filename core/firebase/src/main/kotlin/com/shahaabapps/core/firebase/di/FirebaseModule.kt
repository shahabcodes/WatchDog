package com.shahaabapps.core.firebase.di

import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.auth.FirebaseAuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.child.FirebaseChildRepository
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.device.FirebaseDeviceRepository
import com.shahaabapps.core.firebase.profile.FirebaseProfileRepository
import com.shahaabapps.core.firebase.profile.ProfileRepository
import com.shahaabapps.core.firebase.geofence.FirebaseGeofenceRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.messaging.FirebaseMessagingTokenRepository
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.core.firebase.focus.FirebaseFocusRepository
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.screenshot.FirebaseScreenshotRepository
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import com.shahaabapps.core.firebase.protection.FirebasePendingAppsRepository
import com.shahaabapps.core.firebase.protection.FirebaseProtectionRepository
import com.shahaabapps.core.firebase.protection.PendingAppsRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.firebase.request.FirebaseRequestRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.rules.FirebaseAppRulesRepository
import com.shahaabapps.core.firebase.usage.FirebaseUsageRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore.apply {
        // Explicitly enable on-disk persistence with a larger cache (256 MB) so
        // location writes made while offline are reliably queued on disk and
        // flushed when the connection returns — even across app kills / reboots.
        // Guarded: settings can only be applied before the client is first used.
        runCatching {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(256L * 1024 * 1024)
                        .build(),
                )
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = Firebase.storage
}

@Module
@InstallIn(SingletonComponent::class)
interface FirebaseBindsModule {

    @Binds
    @Singleton
    fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    fun bindProfileRepository(impl: FirebaseProfileRepository): ProfileRepository

    @Binds
    @Singleton
    fun bindChildRepository(impl: FirebaseChildRepository): ChildRepository

    @Binds
    @Singleton
    fun bindAppRulesRepository(impl: FirebaseAppRulesRepository): AppRulesRepository

    @Binds
    @Singleton
    fun bindGeofenceRepository(impl: FirebaseGeofenceRepository): GeofenceRepository

    @Binds
    @Singleton
    fun bindRequestRepository(impl: FirebaseRequestRepository): RequestRepository

    @Binds
    @Singleton
    fun bindUsageRepository(impl: FirebaseUsageRepository): UsageRepository

    @Binds
    @Singleton
    fun bindDeviceRepository(impl: FirebaseDeviceRepository): DeviceRepository

    @Binds
    @Singleton
    fun bindMessagingTokenRepository(impl: FirebaseMessagingTokenRepository): MessagingTokenRepository

    @Binds
    @Singleton
    fun bindProtectionRepository(impl: FirebaseProtectionRepository): ProtectionRepository

    @Binds
    @Singleton
    fun bindPendingAppsRepository(impl: FirebasePendingAppsRepository): PendingAppsRepository

    @Binds
    @Singleton
    fun bindFocusRepository(impl: FirebaseFocusRepository): FocusRepository

    @Binds
    @Singleton
    fun bindScreenshotRepository(impl: FirebaseScreenshotRepository): ScreenshotRepository

    @Binds
    @Singleton
    fun bindTrackingRepository(impl: com.shahaabapps.core.firebase.tracking.FirebaseTrackingRepository): com.shahaabapps.core.firebase.tracking.TrackingRepository

    @Binds
    @Singleton
    fun bindE2eeKeyRepository(impl: com.shahaabapps.core.firebase.e2ee.FirebaseE2eeKeyRepository): com.shahaabapps.core.firebase.e2ee.E2eeKeyRepository

    @Binds
    @Singleton
    fun bindInstallGuardRepository(impl: com.shahaabapps.core.firebase.installguard.FirebaseInstallGuardRepository): com.shahaabapps.core.firebase.installguard.InstallGuardRepository

    @Binds
    @Singleton
    fun bindViewerAccessRepository(impl: com.shahaabapps.core.firebase.viewer.FirebaseViewerAccessRepository): com.shahaabapps.core.firebase.viewer.ViewerAccessRepository
}
