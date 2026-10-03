package com.fms.parentalcontrol.core.firebase.di

import com.fms.parentalcontrol.core.firebase.auth.AuthRepository
import com.fms.parentalcontrol.core.firebase.auth.FirebaseAuthRepository
import com.fms.parentalcontrol.core.firebase.child.ChildRepository
import com.fms.parentalcontrol.core.firebase.child.FirebaseChildRepository
import com.fms.parentalcontrol.core.firebase.profile.FirebaseProfileRepository
import com.fms.parentalcontrol.core.firebase.profile.ProfileRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
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
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore

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
}
