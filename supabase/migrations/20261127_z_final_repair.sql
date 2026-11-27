-- Apply once to the existing SmartPantry database using the Supabase SQL Editor.
-- Requires the previously deployed Community and Shopping APIs and the existing food_log table.
BEGIN;
CREATE INDEX IF NOT EXISTS food_owner_date ON public.food_log(user_id,record_date DESC);

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

CREATE OR REPLACE FUNCTION public.community_shopping_quantity(q numeric, un text) RETURNS boolean
LANGUAGE sql IMMUTABLE SET search_path=public,pg_temp AS $$
 SELECT q>0 AND q<=999 AND CASE
  WHEN un IN ('g','kg','mL','L') THEN q=round(q,3)
  WHEN un IN ('pcs','bag','carton') THEN q=trunc(q)
  ELSE false END
$$;

-- Both the details response and the shopping transaction use these same quantities.
CREATE OR REPLACE FUNCTION public.community_recipe_needs(owner_id uuid, recipe_id bigint)
RETURNS TABLE(id bigint, name text, image text, quantity numeric, unit text, stock numeric,
              "inList" numeric, missing numeric, comparable boolean)
LANGUAGE sql STABLE SET search_path=public,pg_temp AS $$
 WITH raw AS (
  SELECT ri."ingredientId" AS ingredient_id, ri.quantity,
   public.community_unit(ri.unit) AS original_unit
  FROM public."Recipe_Ingredient" ri WHERE ri."recipeId"=recipe_id
 ), grouped AS (
  SELECT ingredient_id, min(original_unit) AS first_unit,
   sum(public.community_convert(quantity,original_unit,
    CASE WHEN original_unit IN ('kg','g') THEN 'g' WHEN original_unit IN ('L','mL') THEN 'mL' ELSE original_unit END)) AS base_quantity,
   CASE WHEN original_unit IN ('kg','g') THEN 'g' WHEN original_unit IN ('L','mL') THEN 'mL' ELSE original_unit END AS base_unit
  FROM raw GROUP BY ingredient_id,4
 ), demand AS (
  SELECT ingredient_id, public.community_convert(base_quantity,base_unit,first_unit) AS quantity, first_unit AS unit
  FROM grouped
 )
 SELECT d.ingredient_id,i."ingredientName"::text,coalesce(i.image,'')::text,d.quantity,d.unit,
  available.stock,cart.quantity,greatest(d.quantity-available.stock-cart.quantity,0),
  d.quantity IS NOT NULL AND d.quantity>0 AND d.quantity::text NOT IN ('NaN','Infinity','-Infinity')
   AND (available.stock>=d.quantity OR (d.unit IN ('g','kg','mL','L','pcs','bag','carton') AND cart.comparable AND NOT EXISTS (
   SELECT 1 FROM public."Ingredient_User" b
   WHERE b.user_id=owner_id AND b."ingredientId"=d.ingredient_id AND b.quantity>0
    AND (to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date >= (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date
    AND public.community_convert(b.quantity,b.quantity_unit,d.unit) IS NULL
  )))
 FROM demand d JOIN public.ingredient i ON i."ingredientId"=d.ingredient_id
 CROSS JOIN LATERAL (SELECT public.community_stock(owner_id,d.ingredient_id,d.unit) AS stock) available
 CROSS JOIN LATERAL (
  SELECT coalesce(sum(public.community_convert(s.purchase_quantity,s.unit,d.unit)),0) AS quantity,
   coalesce(bool_and(public.community_convert(s.purchase_quantity,s.unit,d.unit) IS NOT NULL),true) AS comparable
  FROM public.shopping_list_items s
  WHERE s.user_id=owner_id AND s.ingredient_id=d.ingredient_id AND NOT s.is_purchased
 ) cart
 ORDER BY d.ingredient_id,d.unit
$$;

CREATE OR REPLACE FUNCTION public.community_recipe_shopping(action text, payload jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE
 uid uuid:=auth.uid(); rid bigint; pid bigint:=nullif(payload->>'postId','0')::bigint;
 req uuid; result jsonb; line record; target record; chosen_unit text;
 planned numeric; buy numeric; added integer:=0;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN
  RAISE EXCEPTION 'Please sign in with an active account.';
 END IF;
 IF pid IS NOT NULL THEN
  SELECT p.recipe_id INTO rid FROM public.recipe_post p
   WHERE p.recipe_post_id=pid AND p.recipe_post_status='published' FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'This community post is no longer available.'; END IF;
 ELSE
  SELECT r."recipeId" INTO rid FROM public."Recipe" r
   WHERE r."recipeId"=(payload->>'recipeId')::bigint AND r."userId"=uid;
  IF NOT FOUND THEN RAISE EXCEPTION 'This recipe is unavailable or does not belong to your account.'; END IF;
 END IF;

 IF action IN ('detail','personal_needs') THEN
  result:='{}'::jsonb;
  IF action='detail' THEN
   IF pid IS NULL THEN RAISE EXCEPTION 'This community post is no longer available.'; END IF;
   result:=public.community_api('detail',payload);
  END IF;
  RETURN result||jsonb_build_object('ingredients',coalesce((
   SELECT jsonb_agg(to_jsonb(n) ORDER BY n.id,n.unit) FROM public.community_recipe_needs(uid,rid) n
  ),'[]'::jsonb));
 END IF;
 IF action<>'add_missing' THEN RAISE EXCEPTION 'Unknown community shopping action.'; END IF;
 req:=(payload->>'requestId')::uuid;
 IF req IS NULL THEN RAISE EXCEPTION 'Missing shopping request ID.'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('shopping:'||uid::text,0));
 SELECT cr.result INTO result FROM public.community_request cr WHERE cr.user_id=uid AND cr.request_id=req;
 IF FOUND THEN RETURN result; END IF;
 -- Lock current rows before calculating, so concurrent edits cannot change a row being merged.
 PERFORM 1 FROM public.shopping_list_items s WHERE s.user_id=uid AND NOT s.is_purchased ORDER BY s.id FOR UPDATE;
 FOR line IN SELECT * FROM public.community_recipe_needs(uid,rid) LOOP
  IF NOT line.comparable THEN
   RAISE EXCEPTION 'Cannot compare shopping units for %. Use a compatible unit or add it manually.',line.name;
  END IF;
  IF line.missing<=0 THEN CONTINUE; END IF;

  SELECT s.id,public.community_unit(s.unit) AS unit,s.purchase_quantity,s.planned_quantity INTO target
  FROM public.shopping_list_items s WHERE s.user_id=uid AND s.ingredient_id=line.id AND NOT s.is_purchased
   AND public.community_convert(1,line.unit,s.unit) IS NOT NULL
  ORDER BY s.id LIMIT 1;
  IF FOUND THEN
   chosen_unit:=target.unit;
   planned:=greatest(target.planned_quantity,public.community_convert(line.quantity,line.unit,chosen_unit));
   buy:=target.purchase_quantity + public.community_convert(line.missing,line.unit,chosen_unit);
  ELSE
   chosen_unit:=line.unit;
   planned:=line.quantity;
   buy:=line.missing;
   IF NOT (public.community_shopping_quantity(planned,chosen_unit) AND public.community_shopping_quantity(buy,chosen_unit)) THEN
    chosen_unit:=CASE line.unit WHEN 'g' THEN 'kg' WHEN 'kg' THEN 'g' WHEN 'mL' THEN 'L' WHEN 'L' THEN 'mL' ELSE line.unit END;
    planned:=public.community_convert(line.quantity,line.unit,chosen_unit);
    buy:=public.community_convert(line.missing,line.unit,chosen_unit);
   END IF;
  END IF;
  IF NOT coalesce(public.community_shopping_quantity(planned,chosen_unit) AND public.community_shopping_quantity(buy,chosen_unit),false) THEN
   RAISE EXCEPTION 'The combined quantity exceeds shopping limits for %. Change the shopping unit or split the quantity manually.',line.name;
  END IF;
  IF target.id IS NULL THEN
   INSERT INTO public.shopping_list_items(user_id,ingredient_id,planned_quantity,purchase_quantity,unit,is_purchased)
    VALUES(uid,line.id,planned,buy,chosen_unit,false);
  ELSE
   UPDATE public.shopping_list_items SET planned_quantity=planned,purchase_quantity=buy WHERE id=target.id AND user_id=uid;
  END IF;
  added:=added+1;
 END LOOP;
 result:=jsonb_build_object('added',added);
 INSERT INTO public.community_request(user_id,request_id,result) VALUES(uid,req,result);
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.community_recipe_needs(uuid,bigint),public.community_recipe_shopping(text,jsonb) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.community_v6(p_action text,p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid();result jsonb;rid bigint;row_recipe public."Recipe"%ROWTYPE;n integer;post_id bigint;
 c text:=nullif(btrim(p_payload->>'cuisine'),'');d text:=nullif(btrim(p_payload->>'dishType'),'');diet text:=nullif(btrim(p_payload->>'dietary'),'');
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 IF p_action='feed' THEN
  n:=greatest(0,least(coalesce((p_payload->>'page')::integer,0),10000));
  SELECT jsonb_build_object('posts',coalesce(jsonb_agg(public.community_card(x.recipe_post_id,uid)||jsonb_build_object('cuisine',x.cuisine,'dishType',x.dish_type,'dietary',x.dietary_preference) ORDER BY x.recipe_post_created_at DESC,x.recipe_post_id DESC),'[]'::jsonb)) INTO result
  FROM (SELECT p.*,r.cuisine,r.dish_type,r.dietary_preference FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
   WHERE p.recipe_post_status='published'
    AND (strpos(lower(r."recipeName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0 OR EXISTS(SELECT 1 FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=r."recipeId" AND strpos(lower(i."ingredientName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0))
    AND (coalesce(p_payload->>'mode','feed')<>'saved' OR EXISTS(SELECT 1 FROM public.recipe_favourite f WHERE f.post_id=p.recipe_post_id AND f.user_id=uid))
    AND (c IS NULL OR r.cuisine=c OR (c='Malaysian' AND r.cuisine IN ('Malay','Chinese','Indian')) OR (r.cuisine IS NULL AND r."recipeCategory"=c))
    AND (d IS NULL OR r.dish_type=d)
    AND (diet IS NULL OR r.dietary_preference=diet OR (diet='Vegan' AND r.is_vegan=true))
    AND (coalesce((p_payload->>'ingredientId')::bigint,0)=0 OR EXISTS(SELECT 1 FROM public."Recipe_Ingredient" x WHERE x."recipeId"=r."recipeId" AND x."ingredientId"=(p_payload->>'ingredientId')::bigint))
   ORDER BY p.recipe_post_created_at DESC,p.recipe_post_id DESC LIMIT 30 OFFSET n*30) x;
  RETURN result;

 ELSIF p_action='save_recipe' THEN
  IF c IS NOT NULL AND c NOT IN ('Malaysian','Malay','Chinese','Indian','Asian','Western') THEN RAISE EXCEPTION 'Choose a supported cuisine.'; END IF;
  IF d IS NOT NULL AND d NOT IN ('Rice dishes','Noodles','Light meals','Desserts') THEN RAISE EXCEPTION 'Choose a supported dish type.'; END IF;
  IF diet IS NOT NULL AND diet NOT IN ('Vegan','Vegetarian','Halal','Unspecified') THEN RAISE EXCEPTION 'Choose a supported dietary preference.'; END IF;
  result:=public.community_api('save_recipe',p_payload);rid:=(result->>'recipeId')::bigint;
  UPDATE public."Recipe" SET cuisine=c,dish_type=d,dietary_preference=CASE WHEN is_vegan THEN 'Vegan' ELSE diet END WHERE "recipeId"=rid AND "userId"=uid;
  IF coalesce((p_payload->>'publish')::boolean,false) THEN
    SELECT recipe_post_id INTO post_id FROM public.recipe_post WHERE recipe_id=rid AND user_id=uid;
    IF post_id IS NULL THEN result:=result||public.community_api('publish',jsonb_build_object('recipeId',rid,'caption',''));
    ELSE UPDATE public.recipe_post SET recipe_post_status='published',recipe_post_updated_at=now() WHERE recipe_post_id=post_id; END IF;
  ELSE UPDATE public.recipe_post SET recipe_post_status='unpublished',recipe_post_updated_at=now() WHERE recipe_id=rid AND user_id=uid;
  END IF;
  RETURN result||jsonb_build_object('recipeId',rid);
 ELSIF p_action='catalog' THEN
  result:=public.community_api('catalog',p_payload);
  RETURN result||jsonb_build_object('recipes',coalesce((SELECT jsonb_agg(x.row||jsonb_build_object(
   'description',r.description,'rating',coalesce((SELECT avg(recipe_rating_value) FROM public.recipe_rating rr WHERE rr.post_id=(x.row->>'postId')::bigint),0),
   'ratingCount',(SELECT count(*) FROM public.recipe_rating rr WHERE rr.post_id=(x.row->>'postId')::bigint),'createdAt',to_timestamp(r.create_at/1000.0),
   'ingredientNames',(SELECT coalesce(jsonb_agg(i."ingredientName"),'[]'::jsonb) FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=r."recipeId")
  )) FROM jsonb_array_elements(result->'recipes')x(row) JOIN public."Recipe" r ON r."recipeId"=(x.row->>'id')::bigint WHERE r."userId"=uid),'[]'::jsonb));
 ELSIF p_action='draft' THEN
  result:=public.community_v5('draft',p_payload);
  RETURN result||jsonb_build_object('published',EXISTS(SELECT 1 FROM public.recipe_post WHERE recipe_id=(p_payload->>'recipeId')::bigint AND user_id=uid AND recipe_post_status='published'));
 ELSIF p_action='dashboard' THEN
  result:=public.community_v5('dashboard',p_payload);
  RETURN result||jsonb_build_object('wasteRate',coalesce((
   SELECT round(100.0*count(*) FILTER (WHERE record_type='Wasted')/nullif(count(*),0),1)
   FROM public.food_log WHERE user_id=uid AND record_type IN ('Consumed','Wasted')
    AND record_date BETWEEN date_trunc('week',now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date AND (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date
  ),0));
 ELSIF p_action='shopping_items' THEN
  -- An owned list item retains its ingredient label even after that recipe is hidden.
  RETURN jsonb_build_object('rows',coalesce((
   SELECT jsonb_agg(to_jsonb(s)||jsonb_build_object('ingredient_name',i."ingredientName") ORDER BY s.id)
   FROM public.shopping_list_items s LEFT JOIN public.ingredient i ON i."ingredientId"=s.ingredient_id
   WHERE s.user_id=uid
  ),'[]'::jsonb));
 ELSIF p_action IN ('detail','add_missing','personal_needs') THEN
  RETURN public.community_recipe_shopping(p_action,p_payload);
 END IF;
 RETURN public.community_v5(p_action,p_payload);
END $$;
REVOKE ALL ON FUNCTION public.community_v6(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_v6(text,jsonb) TO authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;
