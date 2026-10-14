package com.shahaabapps.shield.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideShieldDatabase(@ApplicationContext context: Context): ShieldDatabase =
        Room.databaseBuilder(context, ShieldDatabase::class.java, "shield.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePendingTrailDao(db: ShieldDatabase): PendingTrailDao = db.pendingTrailDao()
}
