-- Read-only readiness checks after the first two migrations. No data mutation.
SELECT f.name, to_regprocedure(f.signature) IS NOT NULL AS installed
FROM (VALUES
 ('Community feed/classification/home/log API','public.community_v5(text,jsonb)'),
 ('Pantry recommendations','public.community_for_you(text,integer)'),
 ('Pantry transfer wrapper','public.shopping_v5(text,jsonb)'),
 ('Recipe read guard','public.app_recipe_readable(bigint)'),
 ('Recipe ownership guard','public.app_recipe_owned(bigint)'),
 ('Ingredient visibility guard','public.app_ingredient_readable(bigint)')
) AS f(name,signature);

SELECT tablename,policyname,roles,cmd,qual,with_check
FROM pg_policies WHERE schemaname='public'
AND tablename IN ('Ingredient_User','Recipe','Recipe_Ingredient','ingredient')
ORDER BY tablename,policyname;

SELECT t.tablename,
 has_table_privilege('anon',format('public.%I',t.tablename),'SELECT') AS anonymous_read,
 has_table_privilege('authenticated',format('public.%I',t.tablename),'TRUNCATE') AS client_truncate
FROM pg_tables t WHERE t.schemaname='public' ORDER BY t.tablename;
-- anonymous_read and client_truncate should be false.
-- These are structural checks. Also test normal signed-in users A/B on the phone:
-- each sees only their inventory and private recipes, public recipes stay readable,
-- and B cannot edit/remove A's records.
