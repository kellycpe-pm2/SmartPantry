-- Read-only: verify new objects, owner-scoped API grants, settings and trigger installation.
SELECT proname,prosecdef FROM pg_proc JOIN pg_namespace n ON n.oid=pronamespace WHERE n.nspname='public' AND proname IN ('community_v6','community_for_you_v6','notifications_api','notify_community_activity');
SELECT tablename,rowsecurity FROM pg_tables WHERE schemaname='public' AND tablename IN ('app_notification','app_notification_settings');
SELECT grantee,table_name,privilege_type FROM information_schema.role_table_grants WHERE table_schema='public' AND table_name IN ('app_notification','app_notification_settings') AND grantee IN ('anon','authenticated');
SELECT trigger_name,event_object_table FROM information_schema.triggers WHERE trigger_schema='public' AND trigger_name IN ('community_comment_notification','community_like_notification');
SELECT has_function_privilege('anon','public.notifications_api(text,jsonb)','execute') AS anonymous_must_be_false,has_function_privilege('authenticated','public.notifications_api(text,jsonb)','execute') AS signed_in_must_be_true;
-- Run app verification with two real accounts: comments/likes to author, never self, no cross-account pantry.
