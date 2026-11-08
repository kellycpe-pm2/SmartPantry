package com.example.smartpantry.data.remote

import com.example.smartpantry.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseProvider {

    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

        require(url.startsWith("https://")) {
            "Set SUPABASE_URL in local.properties."
        }

        require(key.isNotBlank()) {
            "Set SUPABASE_PUBLISHABLE_KEY in local.properties."
        }

        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}