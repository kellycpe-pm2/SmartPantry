-- Run after Community setup. Additive: retains inventory rows, IDs and teammates' tables.
BEGIN;
ALTER TABLE public.shopping_list_items ADD COLUMN IF NOT EXISTS shopping_request_id uuid;
CREATE UNIQUE INDEX IF NOT EXISTS shopping_save_once ON public.shopping_list_items(user_id,shopping_request_id) WHERE shopping_request_id IS NOT NULL;
ALTER TABLE public.shopping_list_items ADD COLUMN IF NOT EXISTS unit_price numeric(12,2);
ALTER TABLE public.shopping_list_items ADD COLUMN IF NOT EXISTS photo_path text;
ALTER TABLE public.shopping_list_items ADD COLUMN IF NOT EXISTS review_handled boolean NOT NULL DEFAULT false;
ALTER TABLE public."Ingredient_User" ADD COLUMN IF NOT EXISTS source_shopping_item_id bigint REFERENCES public.shopping_list_items(id) ON DELETE SET NULL;
ALTER TABLE public."Ingredient_User" ADD COLUMN IF NOT EXISTS purchase_date date;
ALTER TABLE public."Ingredient_User" ADD COLUMN IF NOT EXISTS note text;
ALTER TABLE public."Ingredient_User" ADD COLUMN IF NOT EXISTS batch_name text;
ALTER TABLE public."Ingredient_User" ADD COLUMN IF NOT EXISTS photo_path text;
CREATE UNIQUE INDEX IF NOT EXISTS shopping_transfer_once ON public."Ingredient_User"(user_id,source_shopping_item_id) WHERE source_shopping_item_id IS NOT NULL;
CREATE OR REPLACE FUNCTION public.shopping_private_ingredient(uid uuid,n text,u text,img text) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE result bigint;
BEGIN
 IF uid IS DISTINCT FROM auth.uid() THEN RAISE EXCEPTION 'Not authorised.'; END IF;
 n:=btrim(n); IF n IS NULL OR char_length(n) NOT BETWEEN 1 AND 50 OR n~E'[\r\n]' THEN RAISE EXCEPTION 'Ingredient name must have 1 to 50 characters on one line.'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(uid::text||':ingredient:'||lower(n),0));
 SELECT "ingredientId" INTO result FROM public.ingredient WHERE "userId"::text=uid::text AND lower(btrim("ingredientName"))=lower(n) ORDER BY "ingredientId" LIMIT 1;
 IF result IS NULL THEN
  INSERT INTO public.ingredient("ingredientName",unit,image,is_publish,"userId",create_at) VALUES(n,u,nullif(img,''),false,uid,(extract(epoch FROM now())*1000)::bigint) RETURNING "ingredientId" INTO result;
 END IF;
 IF img IS NOT NULL THEN UPDATE public.ingredient SET image=nullif(img,'') WHERE "ingredientId"=result AND "userId"::text=uid::text; END IF;
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.shopping_private_ingredient(uuid,text,text,text) FROM PUBLIC;
CREATE OR REPLACE FUNCTION public.shopping_api(p_action text,p_payload jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid(); req uuid; sid bigint; ing bigint; custom bigint; bid bigint; q numeric; planned numeric; price numeric; un text; n text; photo text; pd date; ed date; old public.shopping_list_items%ROWTYPE;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN RAISE EXCEPTION 'Sign in with an active account.'; END IF;
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
  IF q IS NULL OR planned IS NULL OR q NOT BETWEEN 1 AND 999 OR planned NOT BETWEEN 1 AND 999 OR q<>trunc(q) OR planned<>trunc(planned) OR un NOT IN ('g','kg','mL','L','pcs','bag','carton') THEN RAISE EXCEPTION 'Use a whole quantity from 1 to 999 and a supported unit.'; END IF;
  IF price IS NOT NULL AND (price<0 OR price>9999999999.99 OR price<>round(price,2)) THEN RAISE EXCEPTION 'Unit price must be non-negative with up to two decimal places.'; END IF;
  ing:=nullif(p_payload->>'ingredient_id','0')::bigint;custom:=nullif(p_payload->>'custom_ingredient_id','0')::bigint;
  IF ing IS NULL THEN
   n:=p_payload->>'name';
   IF custom IS NOT NULL THEN SELECT name INTO n FROM public.custom_ingredients WHERE id=custom AND user_id=uid;IF NOT FOUND THEN RAISE EXCEPTION 'Custom ingredient is not yours.'; END IF; END IF;
   ing:=public.shopping_private_ingredient(uid,n,un,photo);
  ELSIF NOT EXISTS(SELECT 1 FROM public.ingredient WHERE "ingredientId"=ing AND ("userId"::text=uid::text OR "userId" IS NULL OR is_publish)) THEN RAISE EXCEPTION 'Ingredient is unavailable.';
  END IF;
  IF sid IS NULL THEN
   INSERT INTO public.shopping_list_items(user_id,ingredient_id,custom_ingredient_id,planned_quantity,purchase_quantity,unit,is_purchased,unit_price,photo_path,review_handled,shopping_request_id)
   VALUES(uid,ing,NULL,planned,q,un,coalesce((p_payload->>'is_purchased')::boolean,false),price,photo,coalesce((p_payload->>'review_handled')::boolean,false),req) RETURNING id INTO sid;
  ELSE
   UPDATE public.shopping_list_items SET ingredient_id=ing,custom_ingredient_id=NULL,planned_quantity=planned,purchase_quantity=q,unit=un,is_purchased=coalesce((p_payload->>'is_purchased')::boolean,false),unit_price=price,photo_path=photo,review_handled=coalesce((p_payload->>'review_handled')::boolean,false) WHERE id=sid AND user_id=uid;
  END IF;
  RETURN (SELECT to_jsonb(s)||jsonb_build_object('ingredient_name',(SELECT "ingredientName" FROM public.ingredient WHERE "ingredientId"=s.ingredient_id)) FROM public.shopping_list_items s WHERE id=sid AND user_id=uid);
 ELSIF p_action='transfer' THEN
  IF sid IS NULL OR NOT old.is_purchased THEN RAISE EXCEPTION 'Mark this item Purchased before adding it to the pantry.'; END IF;
  q:=(p_payload->>'quantity')::numeric;un:=public.community_unit(p_payload->>'unit');pd:=(p_payload->>'purchase_date')::date;ed:=(p_payload->>'expiry_date')::date;n:=btrim(p_payload->>'name');photo:=p_payload->>'photo_path';
  IF q IS NULL OR q NOT BETWEEN 1 AND 999 OR q<>trunc(q) OR un NOT IN ('g','kg','mL','L','pcs','bag','carton') THEN RAISE EXCEPTION 'Use a whole quantity from 1 to 999 and a supported unit.'; END IF;
  IF pd IS NULL OR ed IS NULL OR pd>(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date OR ed<pd THEN RAISE EXCEPTION 'Check purchase and expiry dates.'; END IF;
  IF n IS NULL OR char_length(n) NOT BETWEEN 1 AND 50 OR char_length(coalesce(p_payload->>'note',''))>150 THEN RAISE EXCEPTION 'Check ingredient name and note.'; END IF;
  ing:=old.ingredient_id;
  IF ing IS NULL OR NOT EXISTS(SELECT 1 FROM public.ingredient WHERE "ingredientId"=ing AND lower(btrim("ingredientName"))=lower(n) AND (photo IS NULL OR coalesce(image,'')=photo)) THEN ing:=public.shopping_private_ingredient(uid,n,un,photo); END IF;
  INSERT INTO public."Ingredient_User"(user_id,"ingredientId",quantity,quantity_unit,"expiredDate",source_shopping_item_id,purchase_date,note,batch_name,photo_path)
  VALUES(uid,ing,q,un,(extract(epoch FROM(ed::timestamp AT TIME ZONE 'Asia/Kuala_Lumpur'))*1000)::bigint,sid,pd,nullif(btrim(p_payload->>'note'),''),n,photo) RETURNING "IngredientUser_Id" INTO bid;
  RETURN jsonb_build_object('batchId',bid,'stored',true);
 END IF;
 RAISE EXCEPTION 'Unknown shopping action.';
END $$;
REVOKE ALL ON FUNCTION public.shopping_api(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.shopping_api(text,jsonb) TO authenticated;
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types) VALUES('shopping-images','shopping-images',true,5242880,ARRAY['image/jpeg']) ON CONFLICT(id) DO NOTHING;
DROP POLICY IF EXISTS shopping_image_insert ON storage.objects;
CREATE POLICY shopping_image_insert ON storage.objects FOR INSERT TO authenticated WITH CHECK(bucket_id='shopping-images' AND (storage.foldername(name))[1]=auth.uid()::text);
COMMIT;
NOTIFY pgrst,'reload schema';
