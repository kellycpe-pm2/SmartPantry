# Member C implementation and report alignment — 27 November 2026

This note identifies the report sections to update after phone testing. It does not replace testing evidence or claim that untested functions passed.

## Functional and business rules

| Area | Final implemented rule to describe |
|---|---|
| Quantity | Shopping quantities must be greater than zero and at most 999. g/kg/mL/L allow up to three decimal places; pcs/bag/carton require whole numbers. Zero is a recommendation to remove an item, not a saved purchase quantity. |
| Price | Price is optional and represents RM per selected unit. Retain the existing estimate. No live commercial price service or new local price catalogue was added. |
| Original plan | planned_quantity records the complete need. purchase_quantity records the amount currently on the list. Applying a review changes purchase_quantity only. An intentional manual edit establishes a revised plan. |
| Matching | Match the ingredient ID; convert compatible mass/volume units and normalise piece aliases. Do not add same-name, different-ID stock automatically. Do not guess a bag or carton size. |
| Valid stock | Exclude expired and nonpositive stock. Reserve compatible stock across duplicate shopping rows to prevent counting it twice. |
| Advice | Display plan, available pantry quantity and suggested remaining list amount. Excess stock is relative to planned demand, not a fixed arbitrary threshold. |
| Expiry | Use Malaysian calendar dates. Today/tomorrow is urgent; stock expiring within three days receives a use-first reminder. Batch details remain available. Reduction depends on being able to use the stock before expiry. |
| Keep | Remember the decision for the account's current local list/stock/date snapshot. Recheck after relevant quantities, units, batches, list items or dates change. Do not permanently suppress with a boolean. |
| Review entry | Hide the large suggestion card when no new suggestions exist; keep a compact Purchase Review entry. Loading, unavailable stock and a saved offline snapshot are separate states. |
| Transfer | The source must be Purchased. Validate actual quantity/unit, nonfuture purchase date, expiry on/after purchase, and Pantry/Fridge/Freezer location. Use the existing unique source-shopping-item index for retry safety. A batch photo does not change the canonical ingredient ID. |
| Community | Open on the public community feed. Keep one quick filter row. Show pantry recommendations in a horizontal section with See all. Favourites and My recipes are accessed through the collection menu. |
| Recommendation source | Evaluate the signed-in user's private recipes and all published community recipes with valid ingredient requirements. Another user's unpublished recipe is excluded. |
| Recommendation order | Fully covered recipes using stock expiring within three days come first, followed by other fully covered recipes and partial matches. Compare quantity coverage separately from expiry; do not sum grams and counts into a single score. |
| Classification | Cuisine, dish type and dietary preference are separate optional fields. Main ingredient filtering uses Recipe_Ingredient. Explicit historical cuisine and true vegan flags are preserved; other missing classifications remain unspecified. |
| Author actions | Only the author can edit a post or remove it. Removing a post keeps the original recipe. Deleting the original recipe is a different confirmed action and removes its linked post/interactions through existing foreign keys. |
| Text inputs | Limit characters and consecutive/total line breaks. Recipe descriptions allow up to 5,000 characters and eight line breaks; steps up to 500 characters and two line breaks; captions up to 500 characters with limited paragraphs. |
| Feedback | Display the cause, effect and next action. Keep raw exceptions/URLs in logs. A failed fetch is not an empty-result state. |

## Database dictionary / ERD changes

- Add Recipe.cuisine (nullable text), Recipe.dish_type (nullable text), Recipe.dietary_preference (nullable text).
- Retain Recipe.recipeCategory for compatibility with teammate code; do not present it as all classification dimensions combined.
- Record the existing uniqueness rules: one post per recipe; one rating/favourite per user and post; unique save request and unique pantry transfer per account/source item.
- Keep Ingredient_User.location_storage, source_shopping_item_id, purchase_date, photo_path and note in the dictionary.
- Correct shopping quantity descriptions that currently prohibit all decimals or allow storing purchase_quantity=0.
- Document the indexes added for owner/ingredient/expiry, recipe ingredients and log dates.

## Implementation architecture

- Compose screens use ViewModels and repositories; owner-specific local snapshots support shopping and dashboard access.
- community_v5 wraps selected existing community operations and supplies grouped classification, feed filtering, Home summaries and the temporary log reader.
- community_for_you performs deterministic quantity/expiry matching. It does not call an AI model or consume Groq quota.
- shopping_v5 wraps the existing transactional transfer to attach storage location and keep photo changes separate from ingredient identity.
- Existing community_api and shopping_api remain installed. Existing Ask AI Kotlin and Edge Function sources/keys are not replaced.
- RLS and SQL privileges enforce owner-only inventory and author-only mutation, while allowing defined public catalogue/recipe reads. Explain the distinction between enabling RLS and supplying correct policies.

## Navigation and interaction

- Five-destination floating navigation overlays the page without reserving an opaque full-width footer. Lists have bottom content padding.
- A separate lower-right + is contextual: Home offers recipe/ingredient/pantry creation, Pantry offers ingredient/pantry creation, Recipes creates a recipe.
- Home order: weekly waste card, compact pantry totals, expiring batches, shopping shortcut, genuine community discovery.
- Home batch links include ingredient and batch IDs; the detail page identifies the selected batch.
- Retain teammate swipe-to-remove confirmation, including the batch count and scope.

## Log module boundary

The teammate confirmed her log implementation has not been pushed. The new records route is a temporary **read-only real-data page**, following the described design: dates/period, Consumed and Wasted summaries, recent entries. It reads Consumed from food_log and Wasted from waste_log to avoid duplicate waste counting. Mass, volume and count totals remain separate. A maximum of 300 recent entries is displayed, but summary totals cover the complete selected period. This is not a replacement claim for the teammate's create/edit/delete or chart functions. Replace this route when her source is available.

## Validation evidence

Component compilation, rule checks and a disposable PostgreSQL fixture are supporting developer checks. Full Gradle assembly, phone navigation, real network conditions and two-account RLS checks must be recorded after installing this package. Do not count these as passed before testing. Update Chapter 2 rules, Chapter 3 dictionary/ERD, Implementation Shopping and Community sections, and the test cases together.
