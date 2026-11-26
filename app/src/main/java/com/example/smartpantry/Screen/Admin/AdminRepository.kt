package com.example.smartpantry.Screen.Admin

import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
// IMPORTANT: add the same import for `supabase` that ProfileRepository.kt uses.

@Serializable
data class AdminUserRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val email: String = "",
    @SerialName("user_code") val userCode: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("total_count") val totalCount: Long = 0
)

@Serializable
data class AdminUserDetail(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val email: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("household_size") val householdSize: Int? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_active") val isActive: Boolean
)

object AdminRepository {
    const val PAGE_SIZE = 20

    suspend fun searchUsers(query: String, status: String, page: Int): List<AdminUserRow> =
        withContext(Dispatchers.IO) {
            supabase.postgrest.rpc("admin_search_users", buildJsonObject {
                put("q", query)
                put("status_filter", status)
                put("lim", PAGE_SIZE)
                put("page_offset", page * PAGE_SIZE)
            }).decodeList<AdminUserRow>()
        }

    suspend fun getUser(userId: String): AdminUserDetail =
        withContext(Dispatchers.IO) {
            supabase.postgrest.rpc("admin_get_user", buildJsonObject { put("target", userId) })
                .decodeList<AdminUserDetail>().first()
        }

    suspend fun setUserActive(userId: String, active: Boolean) {
        withContext(Dispatchers.IO) {
            supabase.postgrest.rpc("admin_set_user_active", buildJsonObject {
                put("target", userId)
                put("active", active)
            })
        }
    }
}