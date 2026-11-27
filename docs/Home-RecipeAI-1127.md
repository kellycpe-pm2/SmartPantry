# Home / Community refinement and explicit Recipe AI

Base: tested 39fb4a5, same source tree as remote Member C b068040. The Restore-Team-Details package is NOT used. This patch does not change teammate Pantry/Ingredient pages, Ingredient AI, existing notifications or database schema. Only the Home profile callback is added to navigation.

## Home and recipe UI

Restore Malaysian-time greeting and clickable initials avatar using the actual display name; existing horizontal logo asset retained. Light mint gradient hero, same vegetable asset aligned to the bottom right with its aspect ratio preserved. Entry-based weekly waste rate retains the existing backend definition; unknown/unloaded data is not shown as a fabricated zero. Thin pantry count strip. Use these first is a visible vertical list, sorted by expiry. Discover is vertical. Both section headings use the SAME HomeSectionTitle component and 24dp chevron. Floating bottom navigation remains unchanged.

Recommendation cards use a shared intrinsic row height instead of fixed 260dp cards and a forced two-line title; title/info spacing remains compact. My recipes gains All / Published / Private filtering combined with search. Personal details keep sharing; private exports never invent a public app link. Add ingredient to recipe opens the existing editor's picker and saves to the original recipe ID.

## Recipe draft paths

AI draft always invokes recipe-draft, including familiar names such as Nasi Goreng. Templates are explicit and never call Groq or silently replace a failed AI result. Community post copying is not used as an invisible substitute for AI.

User enters dish name, 1–12 servings and optional dietary preference. Generated content is an editable draft, never an automatic save/publication or inventory mutation. Replacing a non-empty form requires confirmation. When editing a saved recipe, generation preserves its recipe ID and visibility until the user explicitly saves. Cancellation closes the HTTP connection; if Groq already began processing, the provider/project attempt may still count.

Known ingredient IDs AND names are validated against the authenticated user's catalogue. Missing essential ingredients remain visible in an unresolved list; match each to an existing ingredient or explicitly remove it with confirmation and review the steps. Save/publish is blocked while unresolved rows remain. The new backend refuses partial drafts for legacy clients, preventing silent ingredient loss.

## Backend and keys

recipe-draft reads only RECIPE_GROQ_API_KEY. It never falls back to GROQ_API_KEY. Default model is openai/gpt-oss-20b; optional RECIPE_GROQ_MODEL changes only this function. Teammate ingredient-assistant code and settings remain untouched. JWT legacy gateway verification can be off for this function because getUser validates the actual session and the catalogue RPC validates an active account.

Uses existing community_api(catalog), claim_recipe_ai_budget and community_request. No new tables or fields. The service role is used only for private request-cache and budget operations after user authentication. Existing budget typically allows 10 requests/user/day, 60 globally/day in Malaysia time; those are project controls, not Groq plan limits. Actual Groq account limits still apply. Failed provider attempts can consume a project-budget attempt. No automatic retries or artificial waiting countdown.

Successful same-ID retries return the account-scoped cached draft without another provider call or budget claim. Concurrent duplicate IDs receive a processing message. Different input cannot reuse the same ID. Failed reservations are cleaned up where possible; app retry explicitly starts a new request. No shared/global recipe cache exposes private content.

## Observability and testing

Every non-OPTIONS request writes structured received/response stages. Successful generation also logs catalogue_ready/model_requested/model_response. No token, API key, dish name or ingredient catalogue is logged. Client Logcat tag RecipeAI records request ID and HTTP status. An empty exported Logs array alone is not proof of no invocation; check Invocations and time filters too.

Backend tests mock Groq/Supabase and validate request/auth/error/cache/ingredient rules; they do not prove the live key, model access, RLS configuration, latency or quota. Kotlin/Compose API compilation uses some unchanged teammate stubs and older local API jars; the installer performs the real Windows Gradle build. No pixel-perfect Android screenshot or live Groq test has been claimed.
