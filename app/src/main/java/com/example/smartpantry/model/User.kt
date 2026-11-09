package com.example.smartpantry.model

import kotlinx.serialization.Serializable

data class User(
    val userId: Long? = null,
    val userName: String
)