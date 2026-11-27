-- Run after the existing Final-Repair migration. Adds video link / optional author step timers and an API wrapper.
-- Does not change Ingredient AI, private recipe ownership, inventory or existing function bodies.
BEGIN;
DO $$ BEGIN
 IF to_regprocedure('public.community_v6(text,jsonb)') IS NULL THEN
  RAISE EXCEPTION 'Run the existing community backend setup first. No changes applied.';
 END IF;
END $$;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS cooking_video_url text;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS step_timers jsonb DEFAULT '[]'::jsonb;
CREATE OR REPLACE FUNCTION public.community_design_v1(p_action text,p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid(); result jsonb; rid bigint; video text:=btrim(coalesce(p_payload->>'videoUrl','')); timers jsonb:=coalesce(p_payload->'stepTimers','[]'::jsonb);
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN
  RAISE EXCEPTION 'Please sign in with an active account.';
 END IF;
 IF p_action='save_recipe' AND (length(video)>2048 OR (video<>'' AND
   (video !~ '^https://[A-Za-z0-9][A-Za-z0-9.-]*(:443)?([/?#][^[:space:]]*)?$' OR video ~ '^https://[^/?#]*@'))) THEN
  RAISE EXCEPTION 'Use a complete HTTPS cooking-video link without login details.';
 END IF;
 IF p_action='save_recipe' THEN
  IF jsonb_typeof(timers)<>'array' THEN RAISE EXCEPTION 'Step timers must be a list.'; END IF;
  IF jsonb_array_length(timers)>30 OR (jsonb_array_length(timers)>0 AND jsonb_array_length(timers)<>jsonb_array_length(p_payload->'steps')) THEN
   RAISE EXCEPTION 'Each step must have one optional timer.';
  END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(timers) x WHERE x<>'null'::jsonb AND
    (jsonb_typeof(x)<>'number' OR x::text !~ '^[0-9]{1,5}$')) THEN
   RAISE EXCEPTION 'Set step timers to whole seconds, or turn them off.';
  END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(timers) x WHERE x<>'null'::jsonb AND (x::text::integer<1 OR x::text::integer>86400)) THEN
   RAISE EXCEPTION 'Step timers must be between 1 second and 24 hours.';
  END IF;
 END IF;
 result:=public.community_v6(p_action,p_payload);
 IF p_action='save_recipe' THEN
  rid:=(result->>'recipeId')::bigint;
  IF NOT EXISTS(SELECT 1 FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid) THEN
   RAISE EXCEPTION 'This recipe does not belong to your account.';
  END IF;
  UPDATE public."Recipe" SET cooking_video_url=nullif(video,''),step_timers=timers WHERE "recipeId"=rid AND "userId"=uid;
 ELSIF p_action='draft' THEN
  SELECT cooking_video_url INTO video FROM public."Recipe" WHERE "recipeId"=(p_payload->>'recipeId')::bigint AND "userId"=uid;
  result:=result||jsonb_build_object('videoUrl',coalesce(video,''),'stepTimers',coalesce((SELECT step_timers FROM public."Recipe" WHERE "recipeId"=(p_payload->>'recipeId')::bigint AND "userId"=uid),'[]'::jsonb));
 ELSIF p_action='dashboard' THEN
  result:=result||jsonb_build_object('weekTotal',(
   SELECT count(*) FROM public.food_log WHERE user_id=uid AND record_type IN ('Consumed','Wasted')
    AND record_date BETWEEN date_trunc('week',now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date AND (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date
  ));
  result:=result||jsonb_build_object('pantry',coalesce((
   SELECT jsonb_agg(x.row||jsonb_build_object('purchaseDate',b.purchase_date) ORDER BY x.n)
   FROM jsonb_array_elements(result->'pantry') WITH ORDINALITY x(row,n)
   LEFT JOIN public."Ingredient_User" b ON b."IngredientUser_Id"=(x.row->>'id')::bigint AND b.user_id=uid
  ),'[]'::jsonb));
 ELSIF p_action='detail' THEN
  rid:=(result->'post'->>'recipeId')::bigint;
  SELECT cooking_video_url INTO video FROM public."Recipe" WHERE "recipeId"=rid;
  result:=result||jsonb_build_object('videoUrl',coalesce(video,''),'stepTimers',coalesce((SELECT step_timers FROM public."Recipe" WHERE "recipeId"=rid),'[]'::jsonb));
 END IF;
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.community_design_v1(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_design_v1(text,jsonb) TO authenticated;
-- Persist the two narrowly scoped owner policies that were tested on the user's project.
DROP POLICY IF EXISTS recipe_creator_insert_private ON public.ingredient;
CREATE POLICY recipe_creator_insert_private ON public.ingredient FOR INSERT TO authenticated
 WITH CHECK("userId"=(SELECT auth.uid()) AND is_publish IS NOT TRUE);
DROP POLICY IF EXISTS recipe_creator_read_own ON public.ingredient;
CREATE POLICY recipe_creator_read_own ON public.ingredient FOR SELECT TO authenticated USING("userId"=(SELECT auth.uid()));
NOTIFY pgrst,'reload schema';
COMMIT;
