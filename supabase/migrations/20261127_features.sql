-- Run first. Additive schema/API upgrade; does not change AI functions, keys or tables with user data.
BEGIN;
DO $$ BEGIN
 IF to_regprocedure('public.community_api(text,jsonb)') IS NULL OR to_regprocedure('public.community_card(bigint,uuid)') IS NULL THEN
 RAISE EXCEPTION 'Existing community backend is required. No changes were applied.'; END IF;
END $$;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS cuisine text;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS dish_type text;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS dietary_preference text;
CREATE INDEX IF NOT EXISTS pantry_owner_ingredient_expiry ON public."Ingredient_User"(user_id,"ingredientId","expiredDate") WHERE quantity>0;
CREATE INDEX IF NOT EXISTS recipe_ingredient_lookup ON public."Recipe_Ingredient"("recipeId","ingredientId");
CREATE INDEX IF NOT EXISTS food_owner_date ON public.food_log(user_id,record_date DESC);
-- Only copy explicit historical information. False is_vegan does NOT imply vegetarian or non-vegetarian.
UPDATE public."Recipe" SET cuisine="recipeCategory" WHERE cuisine IS NULL AND "recipeCategory" IN ('Malaysian','Chinese','Asian','Western','Other');
UPDATE public."Recipe" SET dietary_preference='Vegan' WHERE dietary_preference IS NULL AND is_vegan=true;

CREATE OR REPLACE FUNCTION public.community_for_you(p_search text DEFAULT '',p_page integer DEFAULT 0)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid(); result jsonb; today date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 WITH stock AS MATERIALIZED (
   SELECT "ingredientId" ingredient_id,quantity,quantity_unit,
     (to_timestamp("expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date expires
   FROM public."Ingredient_User" WHERE user_id=uid AND quantity>0
   AND quantity::text NOT IN ('NaN','Infinity','-Infinity') AND "ingredientId" IS NOT NULL
   AND (to_timestamp("expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date>=today
 ), candidates AS (
   SELECT r.*,p.recipe_post_id,p.recipe_post_created_at
   FROM public."Recipe" r LEFT JOIN public.recipe_post p ON p.recipe_id=r."recipeId" AND p.recipe_post_status='published'
   WHERE (r."userId"=uid OR p.recipe_post_id IS NOT NULL)
   AND strpos(lower(r."recipeName"),lower(left(btrim(coalesce(p_search,'')),80)))>0
   AND EXISTS(SELECT 1 FROM public."Recipe_Ingredient" x WHERE x."recipeId"=r."recipeId")
   AND NOT EXISTS(SELECT 1 FROM public."Recipe_Ingredient" x WHERE x."recipeId"=r."recipeId" AND
      (x.quantity IS NULL OR x.quantity<=0 OR x.quantity::text IN ('NaN','Infinity','-Infinity') OR
       public.community_unit(x.unit) NOT IN ('g','kg','mL','L','pcs','bag','carton','bunch')))
 ), demand AS (
   SELECT c."recipeId",x."ingredientId" ingredient_id,
    CASE public.community_unit(x.unit) WHEN 'kg' THEN 'g' WHEN 'L' THEN 'mL' ELSE public.community_unit(x.unit) END unit_name,
    sum(CASE WHEN public.community_unit(x.unit) IN ('kg','L') THEN x.quantity*1000 ELSE x.quantity END) needed
   FROM candidates c JOIN public."Recipe_Ingredient" x ON x."recipeId"=c."recipeId"
   GROUP BY c."recipeId",x."ingredientId",CASE public.community_unit(x.unit) WHEN 'kg' THEN 'g' WHEN 'L' THEN 'mL' ELSE public.community_unit(x.unit) END
 ), compared AS (
   SELECT d.*,coalesce(x.available,0) available,x.soon_date
   FROM demand d LEFT JOIN LATERAL (
    SELECT sum(public.community_convert(s.quantity,s.quantity_unit,d.unit_name)) available,
      min(s.expires) FILTER(WHERE s.expires<=today+3 AND public.community_convert(s.quantity,s.quantity_unit,d.unit_name)>0) soon_date
    FROM stock s WHERE s.ingredient_id=d.ingredient_id
   ) x ON true
 ), per_ingredient AS (
   SELECT "recipeId",ingredient_id,bool_and(available>=needed) covered,bool_or(available>0) partly,
     min(soon_date) soon_date,avg(least(1::numeric,available/needed)) coverage
   FROM compared GROUP BY "recipeId",ingredient_id
 ), scores AS (
   SELECT "recipeId",count(*) required,count(*) FILTER(WHERE covered) covered,bool_and(covered) all_available,
    min(soon_date) soon_date,avg(coverage) coverage,
    coalesce(jsonb_agg(i."ingredientName") FILTER(WHERE NOT x.covered),'[]'::jsonb) missing_names
   FROM per_ingredient x LEFT JOIN public.ingredient i ON i."ingredientId"=x.ingredient_id
   GROUP BY "recipeId" HAVING bool_or(partly)
 ), ranked AS (
   SELECT c.*,s.required,s.covered,s.all_available,s.soon_date,s.missing_names,
   row_number() OVER(ORDER BY s.all_available DESC,(s.all_available AND s.soon_date IS NOT NULL) DESC,
     s.coverage DESC,s.soon_date NULLS LAST,c."recipeId" DESC) position
   FROM candidates c JOIN scores s USING("recipeId")
 ), page AS (
   SELECT * FROM ranked ORDER BY position LIMIT 31 OFFSET greatest(0,least(coalesce(p_page,0),10000))*30
 ) SELECT jsonb_build_object('pantryEmpty',NOT EXISTS(SELECT 1 FROM stock),'hasMore',(SELECT count(*)>30 FROM page),
 'posts',coalesce((SELECT jsonb_agg(
   (CASE WHEN p.recipe_post_id IS NOT NULL THEN public.community_card(p.recipe_post_id,uid)
    ELSE jsonb_build_object('id',-p."recipeId",'recipeId',p."recipeId",'title',p."recipeName",'image',coalesce(p.recipe_image,''),
      'owner',uid,'author','You','published',false,'rating',0,'ratingCount',0,'favourite',false) END)
   ||jsonb_build_object('forYou',true,'required',p.required,'covered',p.covered,'missing',p.required-p.covered,
     'allAvailable',p.all_available,'useSoon',p.soon_date IS NOT NULL,'soonDate',p.soon_date,'missingNames',p.missing_names)
   ORDER BY p.position) FROM page p WHERE p.position<=greatest(0,least(coalesce(p_page,0),10000))*30+30),'[]'::jsonb)) INTO result;
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.community_for_you(text,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_for_you(text,integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.community_v5(p_action text,p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid(); result jsonb; rid bigint; row_recipe public."Recipe"%ROWTYPE; n integer;
 c text:=nullif(btrim(p_payload->>'cuisine'),''); d text:=nullif(btrim(p_payload->>'dishType'),''); diet text:=nullif(btrim(p_payload->>'dietary'),'');
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 IF p_action='feed' THEN
  n:=greatest(0,least(coalesce((p_payload->>'page')::integer,0),10000));
  SELECT jsonb_build_object('posts',coalesce(jsonb_agg(public.community_card(x.recipe_post_id,uid)||jsonb_build_object('cuisine',x.cuisine,'dishType',x.dish_type,'dietary',x.dietary_preference) ORDER BY x.recipe_post_created_at DESC,x.recipe_post_id DESC),'[]'::jsonb)) INTO result
  FROM (SELECT p.*,r.cuisine,r.dish_type,r.dietary_preference FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
   WHERE p.recipe_post_status='published'
    AND strpos(lower(r."recipeName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0
    AND (coalesce(p_payload->>'mode','feed')<>'saved' OR EXISTS(SELECT 1 FROM public.recipe_favourite f WHERE f.post_id=p.recipe_post_id AND f.user_id=uid))
    AND (c IS NULL OR r.cuisine=c OR (r.cuisine IS NULL AND r."recipeCategory"=c))
    AND (d IS NULL OR r.dish_type=d)
    AND (diet IS NULL OR r.dietary_preference=diet OR (diet='Vegan' AND r.is_vegan=true))
    AND (coalesce((p_payload->>'ingredientId')::bigint,0)=0 OR EXISTS(SELECT 1 FROM public."Recipe_Ingredient" x WHERE x."recipeId"=r."recipeId" AND x."ingredientId"=(p_payload->>'ingredientId')::bigint))
   ORDER BY p.recipe_post_created_at DESC,p.recipe_post_id DESC LIMIT 30 OFFSET n*30) x;
  RETURN result;
 ELSIF p_action='save_recipe' THEN
  IF c IS NOT NULL AND c NOT IN ('Malaysian','Chinese','Asian','Western','Other') THEN RAISE EXCEPTION 'Choose a supported cuisine.'; END IF;
  IF d IS NOT NULL AND d NOT IN ('Rice dishes','Noodles','Light meals','Desserts','Other') THEN RAISE EXCEPTION 'Choose a supported dish type.'; END IF;
  IF diet IS NOT NULL AND diet NOT IN ('Vegan','Vegetarian','Unspecified') THEN RAISE EXCEPTION 'Choose a supported dietary preference.'; END IF;
  IF diet='Vegan' AND NOT coalesce((p_payload->>'vegan')::boolean,false) THEN RAISE EXCEPTION 'Vegan classification must agree with the vegan setting.'; END IF;
  result:=public.community_api(p_action,p_payload);rid:=(result->>'recipeId')::bigint;
  UPDATE public."Recipe" SET cuisine=c,dish_type=d,dietary_preference=CASE WHEN is_vegan=true THEN 'Vegan' ELSE diet END WHERE "recipeId"=rid AND "userId"=uid;
  RETURN result;
 ELSIF p_action='delete_recipe' THEN
  rid:=(p_payload->>'recipeId')::bigint;
  IF NOT EXISTS(SELECT 1 FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid) THEN RAISE EXCEPTION 'This recipe is unavailable or does not belong to your account. Refresh your recipes.'; END IF;
  DELETE FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid;
  RETURN jsonb_build_object('deleted',true);
 ELSIF p_action='draft' THEN
  result:=public.community_api(p_action,p_payload);rid:=(p_payload->>'recipeId')::bigint;
  SELECT * INTO row_recipe FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid;
  RETURN result||jsonb_build_object('cuisine',row_recipe.cuisine,'dishType',row_recipe.dish_type,'dietary',row_recipe.dietary_preference);
 ELSIF p_action='dashboard' THEN
  RETURN jsonb_build_object(
   'pantry',coalesce((SELECT jsonb_agg(to_jsonb(x)) FROM (SELECT b."IngredientUser_Id" id,b."ingredientId" "ingredientId",i."ingredientName" name,i.image,b.quantity,b.quantity_unit unit,
    (to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date expiry
    FROM public."Ingredient_User" b LEFT JOIN public.ingredient i ON i."ingredientId"=b."ingredientId"
    WHERE b.user_id=uid AND b.quantity>0 ORDER BY b."expiredDate",b."IngredientUser_Id") x),'[]'::jsonb),
   'waste',coalesce((SELECT jsonb_agg(to_jsonb(x)) FROM (SELECT food_log_id id,food_name name,food_quantity quantity,unit,record_date date,food_category category,food_log_notes note
    FROM public.food_log WHERE user_id=uid AND record_type='Wasted' ORDER BY record_date DESC,food_log_id DESC LIMIT 300) x),'[]'::jsonb),
   'weekCount',(SELECT count(*) FROM public.food_log WHERE user_id=uid AND record_type='Wasted' AND record_date BETWEEN date_trunc('week',now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date AND (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date),
   'weekWaste',coalesce((SELECT jsonb_agg(to_jsonb(x)) FROM (
    SELECT CASE public.community_unit(unit) WHEN 'kg' THEN 'g' WHEN 'L' THEN 'mL' ELSE public.community_unit(unit) END unit,
      sum(CASE WHEN public.community_unit(unit) IN ('kg','L') THEN food_quantity*1000 ELSE food_quantity END) quantity
    FROM public.food_log WHERE user_id=uid AND record_type='Wasted' AND food_quantity::text NOT IN ('NaN','Infinity','-Infinity')
      AND record_date BETWEEN date_trunc('week',now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date AND (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date
    GROUP BY CASE public.community_unit(unit) WHEN 'kg' THEN 'g' WHEN 'L' THEN 'mL' ELSE public.community_unit(unit) END
   )x),'[]'::jsonb),
   'toBuy',(SELECT count(*) FROM public.shopping_list_items WHERE user_id=uid AND NOT is_purchased),
   'purchased',(SELECT count(*) FROM public.shopping_list_items WHERE user_id=uid AND is_purchased));
 ELSIF p_action='logs' THEN
  IF (p_payload->>'start') IS NULL OR (p_payload->>'end') IS NULL OR (p_payload->>'start')::date>(p_payload->>'end')::date OR (p_payload->>'end')::date>(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date THEN RAISE EXCEPTION 'Choose a start date on or before the end date, and no future dates.'; END IF;
  WITH entries AS MATERIALIZED (
   SELECT food_log_id::text id,record_type type,food_name name,food_quantity quantity,unit,
    record_date date,food_log_notes note,food_category category,ingredient_user_id "ingredientUserId",
    is_auto "isAuto",wasted_cost "wastedCost"
   FROM public.food_log WHERE user_id=uid AND record_type IN ('Consumed','Wasted')
    AND record_date BETWEEN (p_payload->>'start')::date AND (p_payload->>'end')::date
  ), amounts AS (
   SELECT type,CASE public.community_unit(unit) WHEN 'kg' THEN 'g' WHEN 'L' THEN 'mL' ELSE public.community_unit(unit) END unit,
    sum(CASE WHEN public.community_unit(unit) IN ('kg','L') THEN quantity*1000 ELSE quantity END) quantity
   FROM entries WHERE quantity::text NOT IN ('NaN','Infinity','-Infinity')
   GROUP BY type,CASE public.community_unit(unit) WHEN 'kg' THEN 'g' WHEN 'L' THEN 'mL' ELSE public.community_unit(unit) END
  ) SELECT jsonb_build_object('entries',coalesce((SELECT jsonb_agg(to_jsonb(x) ORDER BY x.date DESC,x.id DESC) FROM (SELECT * FROM entries ORDER BY date DESC,id DESC LIMIT 300)x),'[]'::jsonb),
    'amounts',coalesce((SELECT jsonb_agg(to_jsonb(x)) FROM amounts x),'[]'::jsonb),
    'consumedCount',(SELECT count(*) FROM entries WHERE type='Consumed'),'wastedCount',(SELECT count(*) FROM entries WHERE type='Wasted'),
    'limited',(SELECT count(*)>300 FROM entries)) INTO result;
  RETURN result;
 END IF;
 RAISE EXCEPTION 'This operation is unavailable. Refresh the app and try again.';
END $$;
REVOKE ALL ON FUNCTION public.community_v5(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_v5(text,jsonb) TO authenticated;
CREATE OR REPLACE FUNCTION public.shopping_v5(p_action text,p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid();r jsonb;location_name text:=p_payload->>'location';
BEGIN
 IF uid IS NULL OR p_action<>'transfer' THEN RAISE EXCEPTION 'Please sign in again and reopen the pantry transfer.'; END IF;
 IF location_name IS NULL OR location_name NOT IN ('Pantry','Fridge','Freezer') THEN RAISE EXCEPTION 'Select Pantry, Fridge or Freezer before adding stock.'; END IF;
 -- A batch photo is not a different ingredient identity. Keep catalogue IDs stable for matching.
 r:=public.shopping_api(p_action,p_payload-'photo_path');
 UPDATE public."Ingredient_User" SET location_storage=location_name,photo_path=p_payload->>'photo_path' WHERE user_id=uid AND "IngredientUser_Id"=(r->>'batchId')::bigint;
 RETURN r;
END $$;
REVOKE ALL ON FUNCTION public.shopping_v5(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.shopping_v5(text,jsonb) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
