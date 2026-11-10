package com.example.smartpantry.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("display_name") val displayName: String,
    val email: String,
    @SerialName("phone_no") val phoneNo: String,
    val role: String,
    @SerialName("created_at") val memberSince: String,
    @SerialName("household_size") val householdSize: Int? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null
)