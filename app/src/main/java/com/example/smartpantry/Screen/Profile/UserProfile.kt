package com.example.smartpantry.Screen.Profile

data class UserProfile(
    //user and admin
    val displayName: String,
    val email: String,
    val phoneNo: String,
    val role: String,
    val memberSince: String,

    //user only (null for admin)
    val gender: String? = null,
    val dateOfBirth: String? = null

)