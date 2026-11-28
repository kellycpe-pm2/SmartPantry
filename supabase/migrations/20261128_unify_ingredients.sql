-- Unify ingredient definitions; transactional, repeatable, and no CASCADE.
-- Run after the User.user_id repair. Stop on unexpected required fields/dependencies.
BEGIN;
LOCK TABLE public.ingredient,public.shopping_list_items IN ACCESS EXCLUSIVE MODE;
ALTER TABLE public.ingredient ADD COLUMN IF NOT EXISTS legacy_custom_ingredient_id bigint;
CREATE UNIQUE INDEX IF NOT EXISTS ingredient_legacy_custom_id_uq ON public.ingredient(legacy_custom_ingredient_id) WHERE legacy_custom_ingredient_id IS NOT NULL;
DO $migrate$
DECLARE x record; target bigint; missing text;
BEGIN
 IF to_regclass('public.custom_ingredients') IS NOT NULL THEN
  LOCK TABLE public.custom_ingredients IN ACCESS EXCLUSIVE MODE;
  SELECT string_agg(column_name,', ') INTO missing FROM information_schema.columns
  WHERE table_schema='public' AND table_name='ingredient' AND is_nullable='NO' AND column_default IS NULL
  AND is_identity='NO' AND column_name NOT IN ('ingredientId','ingredientName','userId','is_publish','image','create_at');
  IF missing IS NOT NULL THEN RAISE EXCEPTION 'Ingredient requires additional real data: %. Nothing migrated.',missing;END IF;
  IF EXISTS(SELECT 1 FROM public.custom_ingredients c LEFT JOIN public."User" u ON u.user_id=c.user_id WHERE u.user_id IS NULL OR c.name IS NULL OR btrim(c.name)='' OR char_length(c.name)>50) THEN RAISE EXCEPTION 'A legacy ingredient has an invalid owner/name. Correct it before migration.';END IF;
  -- One original row becomes one private definition, even for identical names.
  FOR x IN SELECT * FROM public.custom_ingredients ORDER BY id LOOP
   SELECT "ingredientId" INTO target FROM public.ingredient WHERE legacy_custom_ingredient_id=x.id;
   IF target IS NULL THEN
    INSERT INTO public.ingredient("ingredientName",image,"userId",is_publish,create_at,legacy_custom_ingredient_id)
    VALUES(x.name,x.image_path,x.user_id,false,(extract(epoch FROM x.created_at)*1000)::bigint,x.id) RETURNING "ingredientId" INTO target;
   ELSE
    IF NOT EXISTS(SELECT 1 FROM public.ingredient WHERE "ingredientId"=target AND "userId"=x.user_id AND "ingredientName"=x.name AND image IS NOT DISTINCT FROM x.image_path AND is_publish IS FALSE) THEN RAISE EXCEPTION 'Existing legacy mapping conflicts for %.',x.id;END IF;
   END IF;
  END LOOP;
  IF EXISTS(SELECT 1 FROM public.shopping_list_items s LEFT JOIN public.custom_ingredients c ON c.id=s.custom_ingredient_id WHERE s.custom_ingredient_id IS NOT NULL AND (c.id IS NULL OR c.user_id IS DISTINCT FROM s.user_id)) THEN RAISE EXCEPTION 'Legacy shopping item has a missing/other-account ingredient. Nothing migrated.';END IF;
  UPDATE public.shopping_list_items s SET ingredient_id=i."ingredientId"
  FROM public.ingredient i WHERE s.custom_ingredient_id=i.legacy_custom_ingredient_id AND s.user_id=i."userId";
 END IF;
 IF EXISTS(SELECT 1 FROM public.shopping_list_items s LEFT JOIN public.ingredient i ON i."ingredientId"=s.ingredient_id WHERE i."ingredientId" IS NULL) THEN RAISE EXCEPTION 'Shopping list still contains an unresolved ingredient. Nothing migrated.';END IF;
END $migrate$;
ALTER TABLE public.shopping_list_items DROP COLUMN IF EXISTS custom_ingredient_id;
ALTER TABLE public.shopping_list_items ALTER COLUMN ingredient_id SET NOT NULL;
DO $fk$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.shopping_list_items'::regclass AND contype='f' AND confrelid='public.ingredient'::regclass AND conkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid='public.shopping_list_items'::regclass AND attname='ingredient_id')]::smallint[]) THEN
 ALTER TABLE public.shopping_list_items ADD CONSTRAINT shopping_ingredient_definition_fk FOREIGN KEY(ingredient_id) REFERENCES public.ingredient("ingredientId");
 END IF;
END $fk$;

CREATE OR REPLACE FUNCTION public.shopping_private_ingredient(uid uuid,n text,u text,img text)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $fn$
DECLARE result bigint;
BEGIN
 IF uid IS DISTINCT FROM auth.uid() OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 n:=btrim(n);u:=public.community_unit(u);img:=nullif(img,'');
 IF n IS NULL OR char_length(n) NOT BETWEEN 1 AND 50 OR n~E'[\r\n]' THEN RAISE EXCEPTION 'Ingredient name must have 1 to 50 characters on one line.'; END IF;
 IF u IS NULL OR u NOT IN ('g','kg','mL','L','pcs','bag','carton') THEN RAISE EXCEPTION 'Choose a supported unit.';END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(uid::text||':ingredient:'||lower(n),0));
 SELECT "ingredientId" INTO result FROM public.ingredient
 WHERE "userId"=uid AND is_publish IS FALSE AND lower(btrim("ingredientName"))=lower(n)
 AND public.community_unit(unit)=u AND (img IS NULL OR image=img) ORDER BY "ingredientId" LIMIT 1;
 IF result IS NULL THEN
 INSERT INTO public.ingredient("ingredientName",unit,image,"userId",is_publish,create_at)
 VALUES(n,u,img,uid,false,(extract(epoch FROM now())*1000)::bigint) RETURNING "ingredientId" INTO result;
 END IF;
 RETURN result;
END $fn$;

CREATE OR REPLACE FUNCTION public.shopping_api(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid(); req uuid; sid bigint; ing bigint; bid bigint; q numeric; planned numeric; price numeric; un text; n text; photo text; pd date; ed date; old public.shopping_list_items%ROWTYPE;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Sign in with an active account.'; END IF;
 IF p_payload ? 'owner_id' AND (p_payload->>'owner_id') IS DISTINCT FROM uid::text THEN RAISE EXCEPTION 'Please sign in again.'; END IF;
 IF p_action='catalog' THEN RETURN jsonb_build_object('rows',coalesce((SELECT jsonb_agg(to_jsonb(i) ORDER BY i."ingredientName") FROM public.ingredient i WHERE i."userId"::text=uid::text OR i."userId" IS NULL OR i.is_publish),'[]'::jsonb)); END IF;
 IF p_action='stored' THEN RETURN jsonb_build_object('ids',coalesce((SELECT jsonb_agg(source_shopping_item_id) FROM public."Ingredient_User" WHERE user_id=uid AND source_shopping_item_id IS NOT NULL),'[]'::jsonb)); END IF;
 sid:=nullif(p_payload->>'id','0')::bigint;
 IF sid IS NOT NULL THEN
  SELECT * INTO old FROM public.shopping_list_items WHERE id=sid AND user_id=uid FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Shopping item no longer exists.'; END IF;
  SELECT "IngredientUser_Id" INTO bid FROM public."Ingredient_User" WHERE user_id=uid AND source_shopping_item_id=sid;
  IF bid IS NOT NULL THEN
   IF p_action='transfer' THEN RETURN jsonb_build_object('batchId',bid,'stored',true); END IF;
   RAISE EXCEPTION 'Already stored. Remove the list entry if you no longer need it; the pantry batch is kept.';
  END IF;
 END IF;
 IF p_action='save' THEN
  req:=nullif(p_payload->>'request_id','')::uuid;
  IF sid IS NULL THEN
   IF req IS NULL THEN RAISE EXCEPTION 'Missing save request ID.'; END IF;
   PERFORM pg_advisory_xact_lock(hashtextextended(uid::text||req::text,0));
   SELECT id INTO sid FROM public.shopping_list_items WHERE user_id=uid AND shopping_request_id=req;
   IF FOUND THEN RETURN (SELECT to_jsonb(s)||jsonb_build_object('ingredient_name',(SELECT "ingredientName" FROM public.ingredient WHERE "ingredientId"=s.ingredient_id)) FROM public.shopping_list_items s WHERE id=sid AND user_id=uid); END IF;
  END IF;
  q:=(p_payload->>'purchase_quantity')::numeric;planned:=(p_payload->>'planned_quantity')::numeric;un:=public.community_unit(p_payload->>'unit');price:=(p_payload->>'unit_price')::numeric;photo:=p_payload->>'photo_path';
  IF q IS NULL OR planned IS NULL OR q<=0 OR q>999 OR planned<=0 OR planned>999 OR (un IN ('pcs','bag','carton') AND (q<>trunc(q) OR planned<>trunc(planned))) OR (un IN ('g','kg','mL','L') AND (q<>round(q,3) OR planned<>round(planned,3))) OR un IS NULL OR un NOT IN ('g','kg','mL','L','pcs','bag','carton') THEN RAISE EXCEPTION 'Use a quantity above 0 and up to 999. Counts must be whole numbers; weights and volumes allow up to 3 decimal places.'; END IF;
  IF price IS NOT NULL AND (price<0 OR price>9999999999.99 OR price<>round(price,2)) THEN RAISE EXCEPTION 'Unit price must be non-negative with up to two decimal places.'; END IF;
  ing:=nullif(p_payload->>'ingredient_id','0')::bigint;
  IF ing IS NULL THEN
   n:=p_payload->>'name';
   ing:=public.shopping_private_ingredient(uid,n,un,photo);
  ELSIF NOT EXISTS(SELECT 1 FROM public.ingredient WHERE "ingredientId"=ing AND ("userId"::text=uid::text OR "userId" IS NULL OR is_publish)) THEN RAISE EXCEPTION 'Ingredient is unavailable.';
  END IF;
  IF sid IS NULL THEN
   INSERT INTO public.shopping_list_items(user_id,ingredient_id,planned_quantity,purchase_quantity,unit,is_purchased,unit_price,photo_path,review_handled,shopping_request_id)
   VALUES(uid,ing,planned,q,un,coalesce((p_payload->>'is_purchased')::boolean,false),price,photo,coalesce((p_payload->>'review_handled')::boolean,false),req) RETURNING id INTO sid;
  ELSE
   UPDATE public.shopping_list_items SET ingredient_id=ing,planned_quantity=planned,purchase_quantity=q,unit=un,is_purchased=coalesce((p_payload->>'is_purchased')::boolean,false),unit_price=price,photo_path=photo,review_handled=coalesce((p_payload->>'review_handled')::boolean,false) WHERE id=sid AND user_id=uid;
  END IF;
  RETURN (SELECT to_jsonb(s)||jsonb_build_object('ingredient_name',(SELECT "ingredientName" FROM public.ingredient WHERE "ingredientId"=s.ingredient_id)) FROM public.shopping_list_items s WHERE id=sid AND user_id=uid);
 ELSIF p_action='transfer' THEN
  IF sid IS NULL OR NOT old.is_purchased THEN RAISE EXCEPTION 'Mark this item Purchased before adding it to the pantry.'; END IF;
  q:=(p_payload->>'quantity')::numeric;un:=public.community_unit(p_payload->>'unit');pd:=(p_payload->>'purchase_date')::date;ed:=(p_payload->>'expiry_date')::date;n:=btrim(p_payload->>'name');photo:=p_payload->>'photo_path';
  IF q IS NULL OR q<=0 OR q>999 OR (un IN ('pcs','bag','carton') AND q<>trunc(q)) OR (un IN ('g','kg','mL','L') AND q<>round(q,3)) OR un IS NULL OR un NOT IN ('g','kg','mL','L','pcs','bag','carton') THEN RAISE EXCEPTION 'Use a quantity above 0 and up to 999. Counts must be whole numbers; weights and volumes allow up to 3 decimal places.'; END IF;
  IF un IS NULL THEN RAISE EXCEPTION 'Choose a supported unit.'; END IF;
  IF pd IS NULL OR ed IS NULL OR pd>(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date OR ed<pd THEN RAISE EXCEPTION 'Check purchase and expiry dates.'; END IF;
  IF n IS NULL OR char_length(n) NOT BETWEEN 1 AND 50 OR char_length(coalesce(p_payload->>'note',''))>150 THEN RAISE EXCEPTION 'Check ingredient name and note.'; END IF;
  ing:=old.ingredient_id;
  IF ing IS NULL OR NOT EXISTS(SELECT 1 FROM public.ingredient WHERE "ingredientId"=ing AND lower(btrim("ingredientName"))=lower(n) AND (photo IS NULL OR coalesce(image,'')=photo)) THEN ing:=public.shopping_private_ingredient(uid,n,un,photo); END IF;
  INSERT INTO public."Ingredient_User"(user_id,"ingredientId",quantity,quantity_unit,"expiredDate",source_shopping_item_id,purchase_date,note,batch_name,photo_path)
  VALUES(uid,ing,q,un,(extract(epoch FROM(ed::timestamp AT TIME ZONE 'Asia/Kuala_Lumpur'))*1000)::bigint,sid,pd,nullif(btrim(p_payload->>'note'),''),n,photo) RETURNING "IngredientUser_Id" INTO bid;
  RETURN jsonb_build_object('batchId',bid,'stored',true);
 END IF;
 RAISE EXCEPTION 'Unknown shopping action.';
END $function$
;
-- New client entry point. The map is account-scoped and remains available to old offline caches.
CREATE OR REPLACE FUNCTION public.shopping_ingredients_v2(p_action text,p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $api$
DECLARE uid uuid:=auth.uid();
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.';END IF;
 IF p_payload?'owner_id' AND p_payload->>'owner_id' IS DISTINCT FROM uid::text THEN RAISE EXCEPTION 'Please reopen after switching accounts.';END IF;
 IF p_action='legacy_map' THEN RETURN jsonb_build_object('map',coalesce((SELECT jsonb_object_agg(legacy_custom_ingredient_id::text,"ingredientId") FROM public.ingredient WHERE "userId"=uid AND legacy_custom_ingredient_id IS NOT NULL),'{}'::jsonb));END IF;
 IF p_action='personal_catalog' THEN RETURN jsonb_build_object('rows',coalesce((SELECT jsonb_agg(to_jsonb(i) ORDER BY i."ingredientName",i."ingredientId") FROM public.ingredient i WHERE i."userId"=uid),'[]'::jsonb));END IF;
 IF p_action='shared_catalog' THEN RETURN jsonb_build_object('rows',coalesce((SELECT jsonb_agg(to_jsonb(i) ORDER BY i."ingredientName",i."ingredientId") FROM public.ingredient i WHERE i."userId" IS NULL OR i.is_publish IS TRUE),'[]'::jsonb));END IF;
 RETURN public.shopping_api(p_action,p_payload);
END $api$;
ALTER TABLE public.ingredient ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ingredient_definition_anon_guard ON public.ingredient;
CREATE POLICY ingredient_definition_anon_guard ON public.ingredient AS RESTRICTIVE FOR ALL TO anon USING(false) WITH CHECK(false);
DROP POLICY IF EXISTS ingredient_definition_read_guard ON public.ingredient;
CREATE POLICY ingredient_definition_read_guard ON public.ingredient AS RESTRICTIVE FOR SELECT TO authenticated USING(public.app_ingredient_readable("ingredientId"));
DROP POLICY IF EXISTS ingredient_definition_insert_guard ON public.ingredient;
CREATE POLICY ingredient_definition_insert_guard ON public.ingredient AS RESTRICTIVE FOR INSERT TO authenticated WITH CHECK(("userId"=auth.uid() AND is_publish IS FALSE) OR public.is_admin());
DROP POLICY IF EXISTS ingredient_definition_update_guard ON public.ingredient;
CREATE POLICY ingredient_definition_update_guard ON public.ingredient AS RESTRICTIVE FOR UPDATE TO authenticated USING("userId"=auth.uid() OR public.is_admin()) WITH CHECK("userId"=auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS ingredient_definition_delete_guard ON public.ingredient;
CREATE POLICY ingredient_definition_delete_guard ON public.ingredient AS RESTRICTIVE FOR DELETE TO authenticated USING("userId"=auth.uid() OR public.is_admin());
REVOKE ALL ON FUNCTION public.shopping_ingredients_v2(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.shopping_ingredients_v2(text,jsonb) TO authenticated;
REVOKE ALL ON FUNCTION public.shopping_private_ingredient(uuid,text,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.shopping_private_ingredient(uuid,text,text,text) TO authenticated;
DO $deps$
DECLARE refs text;
BEGIN
 SELECT string_agg(p.proname,', ') INTO refs FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prokind='f' AND (p.prosrc~'\mcustom_ingredients\M' OR p.prosrc~'\mcustom_ingredient_id\M');
 IF refs IS NOT NULL THEN RAISE EXCEPTION 'Unconverted server functions: %. Entire migration rolled back.',refs;END IF;
END $deps$;
DROP TABLE IF EXISTS public.custom_ingredients;
COMMIT;
