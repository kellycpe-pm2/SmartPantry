# Approved UI and notification implementation

Base: ef32a61, minhui/ui-community-1127. Changes target that branch only; teammate Ask AI, model keys, Edge Functions and Gradle settings remain unchanged.

## Home and shopping

Gradient weekly waste hero: waste rate = wasted entries / (wasted entries + consumed entries) in Malaysia Monday–today, with 0.0% when there are no records. It is explicitly an entry-based rate, never a sum of g, pcs and L. Align the report/teammate statistics with this definition before describing it as a mass-based waste rate.

Thin inventory strip; horizontal Use these first cards show unexpired batches due within three days, quantity and yellow expiry labels on one line. Shopping has a distinct section. Discover contains actual published recipes and consistent 24dp chevrons. Add menu is a non-modal anchored popup so outside scrolling can reach the page.

Review entry remains present: reviewing count / no purchase review / checking / unavailable. Whole card is clickable; no duplicate heading or subtitle. Detail explains the feature and keeps the existing transparent quantity/expiry rules and confirmation. Other items section removed. Offline snapshots remain labelled.

## Community

Community heading shares a row with favourites and personal-recipe actions. Search and quick filters collapse together on downward scroll and reappear upward. Search matches recipe names or ingredient names; advanced filters contain cuisine, dish type, dietary only. Halal is author-declared, not certified or inferred. Other classification is no longer selectable.

Recommendation carousel: first five use soon-to-expire stock, next five ordinary matches, fill shortages from the other group, unique maximum ten. See all retains remaining results. At least one usable matching ingredient is enough, including partial recipes. Expired batches do not contribute. ID matching and convertible units are required. Percentage = fully quantity-covered ingredient kinds / required ingredient kinds. Recommendation cards share a fixed height and explicitly identify expiring ingredients and missing ingredient count.

Post cards put image first, title + rating on one row, short description, then circular author avatar/date. No editing controls in the feed or My recipes. Zero ratings display 0.0 everywhere in the changed community/home flows. Favourites supports Undo on removal.

Detail header: favourite, share and author-only edit on the title row. Ingredient thumbnails; no redundant shopping link or bottom comments button. Description and steps collapse for reading. Caption stays in the database for historical compatibility; description is the primary displayed/editable text, and caption is a fallback only when description is empty.

Create/edit publication switch saves and publishes/hides in one database transaction. Hide keeps feedback and favourites; permanent delete is a separate confirmed edit-page action. The existing draft helper is retained unchanged.

Sharing reuses teammate PNG/PDF/TXT builders. Standard Android chooser handles installed apps. App links use smartpantry://recipe/{postId}, opening a published recipe on devices with SmartPantry installed. They are NOT hosted web/universal links; recipients without the app should receive recipe text, an image or PDF instead.

## Notifications

Bell at the top right of My profile, same title row; existing profile content and bottom navigation retained. All / Interactions / Pantry / System filters; unread indicator and mark all read. Preferences are authenticated per account; Android permission is requested only when enabling device notifications.

Comment and comment-like database triggers create genuine events for the recipient, excluding self-actions. No fabricated history is inserted. Comments open the reviews page and scroll to/highlight the referenced comment. Pantry reminders refer to actual batch IDs; consumed/deleted or rescheduled batches are removed/replaced. Expired batches cannot launch a cooking recommendation action. Sync failures create an account-local reminder, uploaded idempotently when the next connection succeeds. Successful sync is not spammed as a notification.

Read state has an account-local cache and retry queue. SQL RPC validates auth.uid and active account; notification tables have RLS enabled and no direct anon/authenticated table grants. Notifications do not disclose other users' private inventory.

Device delivery is WorkManager polling, normally hourly, affected by Android background scheduling, network availability and quiet hours. This is NOT instant FCM push. The existing worker is replaced to avoid scanning every account's local inventory, startup permission prompts and repeated batch alerts. No exact-alarm promise. Enabling device delivery triggers an immediate check; denying permission does not block in-app notifications.

## Database and verification

Apply previous 1127 features/permissions first if not already installed. Apply 20261127_polish_notifications.sql once (rerunnable) and optional read-only verify SQL. These scripts act on the shared Supabase database even when Git changes are on a personal branch. This package does not run them automatically or deploy any AI function.

Component compilation includes changed navigation, community/home/shopping/profile, notifications UI/repository/worker/MainActivity and existing exporters against local Android/Compose API jars. Some unchanged teammate viewmodels/screens are represented by stubs; a test-only type alias adapts the older local Material API name. This is not a full Android Gradle build or a pixel screenshot test. The user's installer runs a real assembleDebug. SQL is tested on a disposable PostgreSQL fixture, not the live Supabase project. Two-account runtime RLS and phone permission/background behaviour need real-device testing.
