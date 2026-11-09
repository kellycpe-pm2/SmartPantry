package com.example.smartpantry.Screen.Profile

import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.model.UserProfile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class ProfileUpdate(
    @SerialName("display_name") val displayName: String,
    @SerialName("phone_no") val phoneNo: String,
    val gender: String?,
    @SerialName("date_of_birth") val dateOfBirth: String?
)

object ProfileRepository {
    private const val TABLE = "user_profiles"
    // Store the auth UUID separately — NOT in UserProfile, used only for DB ops
    private var currentAuthId: String? = null

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        // Cache the UUID after login
        currentAuthId = supabase.auth.currentUserOrNull()?.id
    }

    suspend fun signUp(name: String, phone: String, email: String, password: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("display_name", name)
                put("phone_no", phone)
            }
        }
        currentAuthId = supabase.auth.currentUserOrNull()?.id
    }

    suspend fun signOut() {
        currentAuthId = null
        supabase.auth.signOut()
    }

    suspend fun changeEmail(newEmail: String) {
        supabase.auth.updateUser { email = newEmail }
    }

    suspend fun changePassword(newPassword: String) {
        supabase.auth.updateUser { password = newPassword }
    }

    suspend fun loadMyProfile(): UserProfile {
        supabase.auth.awaitInitialization()

        // Get fresh UUID every time + cache it
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not signed in — please log in")
        currentAuthId = userId

        return supabase.from(TABLE)
            .select { filter { eq("id", userId) } }
            .decodeSingle<UserProfile>()
    }

    suspend fun updateMyProfile(profile: UserProfile) {
        val userId = currentAuthId
            ?: throw IllegalStateException("Not signed in")

        supabase.from(TABLE).update(
            ProfileUpdate(
                displayName = profile.displayName,
                phoneNo = profile.phoneNo,
                gender = profile.gender,
                dateOfBirth = profile.dateOfBirth
            )
        ) {
            filter { eq("id", userId) }
        }
    }
}
