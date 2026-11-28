package com.example.smartpantry.Screen.Profile

import com.example.smartpantry.storage.remote.supabase
import com.example.smartpantry.model.User
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class ProfileUpdate(
    @SerialName("display_name") val displayName: String,
    @SerialName("phone_no") val phoneNo: String,
    @SerialName("household_size") val householdSize: Int,
    @SerialName("date_of_birth") val dateOfBirth: String?
)

object ProfileRepository {
    private const val TABLE = "User"
    private var currentAuthId: String? = null

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        // Cache the UUID after login
        currentAuthId = supabase.auth.currentUserOrNull()?.id
    }

    suspend fun signUp(email: String, password: String) = withContext(Dispatchers.IO) {
        val info = supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // With "Confirm email" on, Supabase hides existing emails: no error, but no identities
        if (info != null && info.identities.isNullOrEmpty()) error("already registered")
    }

    suspend fun completeProfile(name: String, phone: String, householdSize: Int, dateOfBirth: String?) = withContext(Dispatchers.IO) {
        val id = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")
        supabase.from(TABLE).update(
            ProfileUpdate(name, phone, householdSize, dateOfBirth)
        ) { filter { eq("user_id", id) } }
    }

    suspend fun verifySignUpCode(email: String, code: String) = withContext(Dispatchers.IO) {
        supabase.auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email = email, token = code)
        currentAuthId = supabase.auth.currentUserOrNull()?.id
    }
    suspend fun resendSignUpCode(email: String) = withContext(Dispatchers.IO) {
        supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    suspend fun emailExists(email: String): Boolean = withContext(Dispatchers.IO) {
        supabase.postgrest.rpc("email_exists", buildJsonObject { put("check_email", email) })
            .decodeAs<Boolean>()
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        currentAuthId = null
        supabase.auth.signOut()
    }

    suspend fun changeEmail(newEmail: String) = withContext(Dispatchers.IO) {
        supabase.auth.updateUser { email = newEmail }
    }

    suspend fun changePassword(newPassword: String) = withContext(Dispatchers.IO) {
        supabase.auth.updateUser { password = newPassword }
    }

    suspend fun sendResetCode(email: String) = withContext(Dispatchers.IO) {
        supabase.auth.resetPasswordForEmail(email)
    }

    suspend fun verifyResetCode(email: String, code: String) = withContext(Dispatchers.IO) {
        supabase.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
        currentAuthId = supabase.auth.currentUserOrNull()?.id
    }

    suspend fun loadMyProfile(): User = withContext(Dispatchers.IO) {
        supabase.auth.awaitInitialization()

        // Get fresh UUID every time + cache it
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not signed in — please log in")
        currentAuthId = userId

        supabase.from(TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeSingle<User>()
    }

    suspend fun updateMyProfile(profile: User) = withContext(Dispatchers.IO) {
        val userId = currentAuthId
            ?: throw IllegalStateException("Not signed in")

        supabase.from(TABLE).update(
            ProfileUpdate(
                displayName = profile.displayName,
                phoneNo = profile.phoneNo,
                householdSize = profile.householdSize,
                dateOfBirth = profile.dateOfBirth
            )
        ) {
            filter { eq("user_id", userId) }
        }
    }
}
