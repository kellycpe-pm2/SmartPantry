package com.example.smartpantry.storage.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

val supabase = createSupabaseClient(
    supabaseUrl = "https://sauxeqatfysygmkicpdf.supabase.co",
    supabaseKey = "sb_publishable_taZTQlTpfFPGxyHvqv6xzA_r_f5kxid"
) {
    install(Postgrest)
}