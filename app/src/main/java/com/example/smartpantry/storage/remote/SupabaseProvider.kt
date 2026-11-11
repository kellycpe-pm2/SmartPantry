package com.example.smartpantry.storage.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

val supabase = createSupabaseClient(
    supabaseUrl = "https://sauxeqatfysygmkicpdf.supabase.co",
    supabaseKey = "sb_publishable_taZTQlTpfFPGxyHvqv6xzA_r_f5kxid"
) {
    // Columns the Kotlin class doesn't have (e.g. is_active) are ignored instead of crashing
    defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
    install(Auth)      // login, sign up, session (saved on the phone automatically)
    install(Postgrest)
    install(Storage)
}