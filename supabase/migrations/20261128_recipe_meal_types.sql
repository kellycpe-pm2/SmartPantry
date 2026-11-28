-- New additive meal metadata and APIs. Requires the successful User.user_id repair.
-- No existing function is renamed or replaced; no old recipe is auto-classified.
BEGIN;
DO $guard$ BEGIN
 IF to_regprocedure('public.community_design_v1(text,jsonb)') IS NULL
 OR to_regprocedure('public.community_card(bigint,uuid)') IS NULL THEN RAISE EXCEPTION 'Run the current database setup first. No changes applied.'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='User' AND column_name='user_id') THEN RAISE EXCEPTION 'Expected User.user_id. No changes applied.'; END IF;
 IF EXISTS(SELECT 1 FROM pg_proc WHERE oid=to_regprocedure('public.community_design_v1(text,jsonb)') AND prosrc LIKE '%public."User" WHERE id=%') THEN RAISE EXCEPTION 'Run Database-Link-Repair before this migration. No changes applied.'; END IF;
END $guard$;
CREATE OR REPLACE FUNCTION public.recipe_valid_meals(values_list text[]) RETURNS boolean
LANGUAGE sql IMMUTABLE SET search_path=public,pg_temp AS $$
 SELECT values_list IS NOT NULL AND cardinality(values_list)<=4
 AND array_position(values_list,NULL) IS NULL
 AND values_list <@ ARRAY['Breakfast','Lunch','Dinner','Snack / Tea time']::text[]
 AND cardinality(values_list)=(SELECT count(DISTINCT item) FROM unnest(values_list) item)
$$;
CREATE OR REPLACE FUNCTION public.recipe_meal_slot(hour_value integer) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path=public,pg_temp AS $$
 SELECT CASE WHEN hour_value BETWEEN 5 AND 10 THEN 'Breakfast'
 WHEN hour_value BETWEEN 11 AND 14 THEN 'Lunch'
 WHEN hour_value BETWEEN 15 AND 17 THEN 'Snack / Tea time'
 WHEN hour_value BETWEEN 18 AND 21 THEN 'Dinner' ELSE NULL END
$$;
REVOKE ALL ON FUNCTION public.recipe_valid_meals(text[]),public.recipe_meal_slot(integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.recipe_valid_meals(text[]),public.recipe_meal_slot(integer) TO authenticated,service_role;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS meal_types text[] NOT NULL DEFAULT '{}'::text[];
DO $constraint$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public."Recipe"'::regclass AND conname='recipe_meal_types_valid') THEN
 ALTER TABLE public."Recipe" ADD CONSTRAINT recipe_meal_types_valid CHECK(public.recipe_valid_meals(meal_types));
 END IF;
END $constraint$;
CREATE INDEX IF NOT EXISTS recipe_meal_types_lookup ON public."Recipe" USING gin(meal_types);
CREATE OR REPLACE FUNCTION public.community_meals_v1(p_action text,p_payload jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid();result jsonb;rid bigint;meals text[]:='{}';
 c text:=nullif(btrim(p_payload->>'cuisine'),'');d text:=nullif(btrim(p_payload->>'dishType'),'');diet text:=nullif(btrim(p_payload->>'dietary'),'');n integer;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 IF p_payload ? 'mealTypes' THEN
  IF jsonb_typeof(p_payload->'mealTypes') IS DISTINCT FROM 'array' THEN RAISE EXCEPTION 'Choose meal types from the available options.'; END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(p_payload->'mealTypes') x WHERE jsonb_typeof(x) IS DISTINCT FROM 'string') THEN RAISE EXCEPTION 'Choose meal types from the available options.'; END IF;
  SELECT coalesce(array_agg(value),'{}'::text[]) INTO meals FROM jsonb_array_elements_text(p_payload->'mealTypes');
  IF NOT public.recipe_valid_meals(meals) THEN RAISE EXCEPTION 'Choose supported meal types without duplicates.'; END IF;
 END IF;
 IF p_action='feed' THEN
  n:=greatest(0,least(coalesce((p_payload->>'page')::integer,0),10000));
  SELECT jsonb_build_object('posts',coalesce(jsonb_agg(
    public.community_card(x.recipe_post_id,uid)||jsonb_build_object('cuisine',x.cuisine,'dishType',x.dish_type,'dietary',x.dietary_preference,'mealTypes',to_jsonb(x.meal_types))
    ORDER BY x.recipe_post_created_at DESC,x.recipe_post_id DESC),'[]'::jsonb)) INTO result
  FROM (SELECT p.*,r.cuisine,r.dish_type,r.dietary_preference,r.meal_types FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
   WHERE p.recipe_post_status='published'
    AND (strpos(lower(r."recipeName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0 OR EXISTS(SELECT 1 FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=r."recipeId" AND strpos(lower(i."ingredientName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0))
    AND (coalesce(p_payload->>'mode','feed')<>'saved' OR EXISTS(SELECT 1 FROM public.community_interaction f WHERE f.post_id=p.recipe_post_id AND f.user_id=uid AND f.is_favourite))
    AND (c IS NULL OR r.cuisine=c OR (c='Malaysian' AND r.cuisine IN ('Malay','Chinese','Indian')) OR (r.cuisine IS NULL AND r."recipeCategory"=c))
    AND (d IS NULL OR r.dish_type=d)
    AND (diet IS NULL OR r.dietary_preference=diet OR (diet='Vegan' AND r.is_vegan=true))
    AND (coalesce((p_payload->>'ingredientId')::bigint,0)=0 OR EXISTS(SELECT 1 FROM public."Recipe_Ingredient" x WHERE x."recipeId"=r."recipeId" AND x."ingredientId"=(p_payload->>'ingredientId')::bigint))
    AND (cardinality(meals)=0 OR r.meal_types && meals)
   ORDER BY p.recipe_post_created_at DESC,p.recipe_post_id DESC LIMIT 30 OFFSET n*30) x;
  RETURN result;
 END IF;
 result:=public.community_design_v1(p_action,p_payload);
 IF p_action='save_recipe' THEN
  rid:=(result->>'recipeId')::bigint;
  IF p_payload ? 'mealTypes' THEN UPDATE public."Recipe" SET meal_types=meals WHERE "recipeId"=rid AND "userId"=uid; END IF;
 ELSIF p_action='draft' THEN
  rid:=(p_payload->>'recipeId')::bigint;
  result:=result||jsonb_build_object('mealTypes',coalesce((SELECT to_jsonb(meal_types) FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid),'[]'::jsonb));
 ELSIF p_action='catalog' THEN
  result:=result||jsonb_build_object('recipes',coalesce((SELECT jsonb_agg(x.row||jsonb_build_object('mealTypes',to_jsonb(r.meal_types)) ORDER BY x.n)
   FROM jsonb_array_elements(result->'recipes') WITH ORDINALITY x(row,n) JOIN public."Recipe" r ON r."recipeId"=(x.row->>'id')::bigint AND r."userId"=uid),'[]'::jsonb));
 ELSIF p_action='detail' THEN
  rid:=(result->'post'->>'recipeId')::bigint;
  result:=result||jsonb_build_object('mealTypes',coalesce((SELECT to_jsonb(meal_types) FROM public."Recipe" WHERE "recipeId"=rid),'[]'::jsonb));
  result:=jsonb_set(result,'{post}',result->'post'||jsonb_build_object('mealTypes',result->'mealTypes'));
 END IF;
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.community_meals_v1(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_meals_v1(text,jsonb) TO authenticated;
CREATE OR REPLACE FUNCTION public.community_meals_for_you(p_search text DEFAULT ''::text, p_page integer DEFAULT 0, p_meal_types text[] DEFAULT '{}'::text[], p_prefer_current boolean DEFAULT false)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid(); result jsonb; today date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date; preferred text:=CASE WHEN p_prefer_current THEN public.recipe_meal_slot(extract(hour FROM now() AT TIME ZONE 'Asia/Kuala_Lumpur')::integer) ELSE NULL END;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 IF NOT public.recipe_valid_meals(p_meal_types) THEN RAISE EXCEPTION 'Choose supported meal types without duplicates.'; END IF;
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
   AND (cardinality(p_meal_types)=0 OR r.meal_types && p_meal_types)
   AND (strpos(lower(r."recipeName"),lower(left(btrim(coalesce(p_search,'')),80)))>0 OR EXISTS(SELECT 1 FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=r."recipeId" AND strpos(lower(i."ingredientName"),lower(left(btrim(coalesce(p_search,'')),80)))>0))
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
    min(soon_date) soon_date,count(*) FILTER(WHERE soon_date IS NOT NULL) soon_count,
    coalesce(jsonb_agg(i."ingredientName" ORDER BY i."ingredientName") FILTER(WHERE soon_date IS NOT NULL),'[]'::jsonb) soon_names,avg(coverage) coverage,
    coalesce(jsonb_agg(i."ingredientName") FILTER(WHERE NOT x.covered),'[]'::jsonb) missing_names
   FROM per_ingredient x LEFT JOIN public.ingredient i ON i."ingredientId"=x.ingredient_id
   GROUP BY "recipeId" HAVING bool_or(partly)
 ), groups AS (
   SELECT c.*,s.required,s.covered,s.all_available,s.soon_date,s.soon_count,s.soon_names,s.missing_names,
   row_number() OVER(PARTITION BY s.soon_date IS NOT NULL ORDER BY s.soon_date NULLS LAST,coalesce(preferred=ANY(c.meal_types),false) DESC,s.soon_count DESC,s.covered::numeric/nullif(s.required,0) DESC,(s.required-s.covered),c."recipeId" DESC) group_position
   FROM candidates c JOIN scores s USING("recipeId")
 ), ranked AS (
   SELECT g.*,row_number() OVER(ORDER BY CASE WHEN group_position<=5 THEN CASE WHEN soon_date IS NOT NULL THEN 0 ELSE 1 END ELSE 2 END,
    CASE WHEN group_position<=5 THEN group_position END,soon_date NULLS LAST,group_position,"recipeId" DESC) position FROM groups g
 ), page AS (
   SELECT * FROM ranked ORDER BY position LIMIT 31 OFFSET greatest(0,least(coalesce(p_page,0),10000))*30
 ) SELECT jsonb_build_object('pantryEmpty',NOT EXISTS(SELECT 1 FROM stock),'hasMore',(SELECT count(*)>30 FROM page),
 'posts',coalesce((SELECT jsonb_agg(
   (CASE WHEN p.recipe_post_id IS NOT NULL THEN public.community_card(p.recipe_post_id,uid)
    ELSE jsonb_build_object('id',-p."recipeId",'recipeId',p."recipeId",'title',p."recipeName",'image',coalesce(p.recipe_image,''),
      'owner',uid,'author','You','published',false,'rating',0,'ratingCount',0,'favourite',false) END)
   ||jsonb_build_object('forYou',true,'required',p.required,'covered',p.covered,'missing',p.required-p.covered,
     'mealTypes',to_jsonb(p.meal_types),'preferredMeal',preferred,'soonCount',p.soon_count,'soonNames',p.soon_names,'allAvailable',p.all_available,'useSoon',p.soon_date IS NOT NULL,'soonDate',p.soon_date,'missingNames',p.missing_names)
   ORDER BY p.position) FROM page p WHERE p.position<=greatest(0,least(coalesce(p_page,0),10000))*30+30),'[]'::jsonb)) INTO result;
 RETURN result;
END $function$
;

REVOKE ALL ON FUNCTION public.community_meals_for_you(text,integer,text[],boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_meals_for_you(text,integer,text[],boolean) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
SELECT 'Meal types ready. Existing recipe classifications remain unchanged.' AS result;
