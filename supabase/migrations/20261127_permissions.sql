-- Run AFTER features.sql. Transactional policies and privilege repair; deletes no data.
BEGIN;
DO $$ DECLARE x record; BEGIN
 IF to_regprocedure('public.community_v5(text,jsonb)') IS NULL THEN RAISE EXCEPTION 'Run 20261127_features.sql first.'; END IF;
 FOR x IN SELECT tablename FROM pg_tables WHERE schemaname='public' LOOP
  EXECUTE format('REVOKE TRUNCATE, REFERENCES, TRIGGER ON TABLE public.%I FROM PUBLIC, anon, authenticated',x.tablename);
  EXECUTE format('REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I FROM anon',x.tablename);
 END LOOP;
 FOR x IN SELECT tablename,policyname FROM pg_policies WHERE schemaname='public' AND tablename IN ('Ingredient_User','Recipe','Recipe_Ingredient','ingredient') LOOP
  EXECUTE format('DROP POLICY %I ON public.%I',x.policyname,x.tablename);
 END LOOP;
END $$;
ALTER TABLE public."Ingredient_User" ENABLE ROW LEVEL SECURITY;
ALTER TABLE public."Recipe" ENABLE ROW LEVEL SECURITY;
ALTER TABLE public."Recipe_Ingredient" ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ingredient ENABLE ROW LEVEL SECURITY;
CREATE OR REPLACE FUNCTION public.app_recipe_readable(rid bigint) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
 SELECT auth.uid() IS NOT NULL AND EXISTS(SELECT 1 FROM public."Recipe" r WHERE r."recipeId"=rid AND
  (r."userId"=auth.uid() OR EXISTS(SELECT 1 FROM public.recipe_post p WHERE p.recipe_id=rid AND p.recipe_post_status='published')))
$$;
CREATE OR REPLACE FUNCTION public.app_recipe_owned(rid bigint) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
 SELECT auth.uid() IS NOT NULL AND EXISTS(SELECT 1 FROM public."Recipe" r WHERE r."recipeId"=rid AND r."userId"=auth.uid())
$$;
CREATE OR REPLACE FUNCTION public.app_ingredient_readable(ing bigint) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
 SELECT auth.uid() IS NOT NULL AND EXISTS(SELECT 1 FROM public.ingredient i WHERE i."ingredientId"=ing AND
  (i."userId" IS NULL OR i."userId"=auth.uid() OR i.is_publish=true OR public.is_admin() OR EXISTS(
   SELECT 1 FROM public."Recipe_Ingredient" x JOIN public.recipe_post p ON p.recipe_id=x."recipeId" WHERE x."ingredientId"=ing AND p.recipe_post_status='published')))
$$;
REVOKE ALL ON FUNCTION public.app_recipe_readable(bigint),public.app_recipe_owned(bigint),public.app_ingredient_readable(bigint) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.app_recipe_readable(bigint),public.app_recipe_owned(bigint),public.app_ingredient_readable(bigint) TO authenticated;
GRANT SELECT,INSERT,UPDATE,DELETE ON public."Ingredient_User",public."Recipe",public."Recipe_Ingredient",public.ingredient TO authenticated;
CREATE POLICY inventory_owner ON public."Ingredient_User" FOR ALL TO authenticated USING(user_id=(SELECT auth.uid())) WITH CHECK(user_id=(SELECT auth.uid()) AND ("ingredientId" IS NULL OR public.app_ingredient_readable("ingredientId")));
CREATE POLICY recipe_read ON public."Recipe" FOR SELECT TO authenticated USING(public.app_recipe_readable("recipeId") OR public.is_admin());
CREATE POLICY recipe_insert ON public."Recipe" FOR INSERT TO authenticated WITH CHECK("userId"=(SELECT auth.uid()));
CREATE POLICY recipe_update ON public."Recipe" FOR UPDATE TO authenticated USING("userId"=(SELECT auth.uid())) WITH CHECK("userId"=(SELECT auth.uid()));
CREATE POLICY recipe_delete ON public."Recipe" FOR DELETE TO authenticated USING("userId"=(SELECT auth.uid()));
CREATE POLICY recipe_ingredient_read ON public."Recipe_Ingredient" FOR SELECT TO authenticated USING(public.app_recipe_readable("recipeId") OR public.is_admin());
CREATE POLICY recipe_ingredient_write ON public."Recipe_Ingredient" FOR ALL TO authenticated USING(public.app_recipe_owned("recipeId")) WITH CHECK(public.app_recipe_owned("recipeId") AND public.app_ingredient_readable("ingredientId"));
CREATE POLICY ingredient_read ON public.ingredient FOR SELECT TO authenticated USING(public.app_ingredient_readable("ingredientId"));
CREATE POLICY ingredient_insert ON public.ingredient FOR INSERT TO authenticated WITH CHECK("userId"=(SELECT auth.uid()) OR public.is_admin());
CREATE POLICY ingredient_update ON public.ingredient FOR UPDATE TO authenticated USING("userId"=(SELECT auth.uid()) OR public.is_admin()) WITH CHECK("userId"=(SELECT auth.uid()) OR public.is_admin());
CREATE POLICY ingredient_delete ON public.ingredient FOR DELETE TO authenticated USING("userId"=(SELECT auth.uid()) OR public.is_admin());
-- Private tables with RLS and no policies are intentionally accessed through validated RPCs.
REVOKE ALL ON FUNCTION public.community_card(bigint,uuid),public.community_stock(uuid,bigint,text),public.shopping_private_ingredient(uuid,text,text,text),public.claim_recipe_ai_budget(uuid) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.community_api(text,jsonb),public.shopping_api(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_api(text,jsonb),public.shopping_api(text,jsonb) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
