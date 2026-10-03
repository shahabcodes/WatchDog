package com.shahaabapps.core.firebase.profile

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the current user's [UserProfile], including avatar upload.
 */
interface ProfileRepository {

    /** Live profile for the given user id. Emits null until a profile exists. */
    fun observeProfile(userId: String): Flow<UserProfile?>

    suspend fun saveProfile(profile: UserProfile): AppResult<Unit>

    /**
     * Uploads avatar bytes to Storage and returns the download URL.
     * Does not mutate the profile document itself.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray): AppResult<String>
}
