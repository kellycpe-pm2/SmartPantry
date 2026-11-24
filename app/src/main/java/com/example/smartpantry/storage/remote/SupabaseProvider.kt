package com.example.smartpantry.storage.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

const val SUPABASE_URL = "https://sauxeqatfysygmkicpdf.supabase.co"
const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_taZTQlTpfFPGxyHvqv6xzA_r_f5kxid"

val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_PUBLISHABLE_KEY
) {
    // Columns the Kotlin class doesn't have (e.g. is_active) are ignored instead of crashing
    defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
    install(Auth)      // login, sign up, session (saved on the phone automatically)
    install(Postgrest)
    install(Storage)
}
