-- Repair for the actual function CSV exported 2026-11-28 11:00.
-- Preserves 15-table schema, merged interactions, existing data, permissions and business logic.
-- Idempotent preflight accepts the exported source or this repaired source only.
BEGIN;
DO $preflight$
DECLARE oid_value oid;source_hash text;
BEGIN
 IF to_regclass('public."User"') IS NULL OR to_regclass('public.community_interaction') IS NULL THEN RAISE EXCEPTION 'Expected User and community_interaction tables. Nothing applied.'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='User' AND column_name='user_id') THEN RAISE EXCEPTION 'User.user_id is missing. Nothing applied.'; END IF;
  oid_value:=to_regprocedure('public.is_admin()');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: is_admin(). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('64ba1cb3c9bfba3e4f193286fe7ec37d','7258fa2a547f55c5f81802ab4e6b7375') THEN RAISE EXCEPTION 'Function changed since your exported CSV: is_admin(). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.claim_recipe_ai_budget(uuid)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: claim_recipe_ai_budget(uuid). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('b406d9ea1a3123ff791ee31e452c7853','d765e1505acadc643f04c680996c7328') THEN RAISE EXCEPTION 'Function changed since your exported CSV: claim_recipe_ai_budget(uuid). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_api(text,jsonb)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_api(text,jsonb). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('f9839a0892dad4f780a8a166fa582095','54febd4367a70393a37ac67b79b72967') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_api(text,jsonb). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_card(bigint,uuid)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_card(bigint,uuid). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('6afb567b4e13ffe55540e192635ae509','5a8602a8eb217f93628a7c376d72a6e4') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_card(bigint,uuid). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_design_v1(text,jsonb)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_design_v1(text,jsonb). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('cdd27351087f28446be719edaac0fd06','0f1095da260f15c0b64e9af56917f8a3') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_design_v1(text,jsonb). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_for_you(text,integer)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_for_you(text,integer). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('46e9df0db7c6ccd4fc5d0808da5c1fbe','bd9cf136756e1aec3998c69c7beb2e18') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_for_you(text,integer). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_for_you_v6(text,integer)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_for_you_v6(text,integer). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('8282b15ad6581e412e1664d4663fcb84','8fa5b682be5f8cb773d83f53c03f6908') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_for_you_v6(text,integer). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_recipe_shopping(text,jsonb)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_recipe_shopping(text,jsonb). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('b7021ff4e1dd35a626eaa262c4cebb7a','906b29a10ee2b8ec69b5809a704573a6') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_recipe_shopping(text,jsonb). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_v5(text,jsonb)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_v5(text,jsonb). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('d6c73ee24c3978f55d1cb98d1eec66f0','bfcc4665125a4041779cb5d507ec493c') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_v5(text,jsonb). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.community_v6(text,jsonb)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: community_v6(text,jsonb). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('2c2d5bb5d4cc4502c884cceefbc3c3d9','6190cbc95d66128af372492a72d93ccc') THEN RAISE EXCEPTION 'Function changed since your exported CSV: community_v6(text,jsonb). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.handle_new_user()');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: handle_new_user(). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('60e9675b7e1166106f264f7950119b62','e19cb1f3ba8313be404a027cb034db0c') THEN RAISE EXCEPTION 'Function changed since your exported CSV: handle_new_user(). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.notifications_api(text,jsonb)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: notifications_api(text,jsonb). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('121b4a925abc0055f4006820fce436f9','bc312f41a233a1b0968e19fdfc70c18f') THEN RAISE EXCEPTION 'Function changed since your exported CSV: notifications_api(text,jsonb). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.notify_community_activity()');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: notify_community_activity(). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('6edb3e5fde681902a9009bc3da652269','a5ba7f74dca3d51f0cc3502db7dc8b9a') THEN RAISE EXCEPTION 'Function changed since your exported CSV: notify_community_activity(). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.shopping_api(text,jsonb)');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: shopping_api(text,jsonb). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('b355b4d202acc776cb55720bb93c14a7','3c09bc4449b3b45f9da0f42a983e7baf') THEN RAISE EXCEPTION 'Function changed since your exported CSV: shopping_api(text,jsonb). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.sync_email()');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: sync_email(). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('4da475d06bc25177c0df6fc49497b81f','8597f265e9b2dddc49bb69969ae34d83') THEN RAISE EXCEPTION 'Function changed since your exported CSV: sync_email(). Nothing applied. Export its current definition before retrying.'; END IF;
  oid_value:=to_regprocedure('public.sync_user_email()');
  IF oid_value IS NULL THEN RAISE EXCEPTION 'Missing function: sync_user_email(). Nothing applied.'; END IF;
  SELECT md5(replace(replace(prosrc,E'\r\n',E'\n'),E'\r',E'\n')) INTO source_hash FROM pg_proc WHERE oid=oid_value;
  IF source_hash NOT IN ('25d26c5d7efe9e9a533f8bb704823452','8597f265e9b2dddc49bb69969ae34d83') THEN RAISE EXCEPTION 'Function changed since your exported CSV: sync_user_email(). Nothing applied. Export its current definition before retrying.'; END IF;
END $preflight$;
CREATE OR REPLACE FUNCTION public.is_admin()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select exists (
    select 1 from public."User"
    where user_id = auth.uid() and role = 'admin' and is_active
  );
$function$
;

CREATE OR REPLACE FUNCTION public.claim_recipe_ai_budget(owner uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE d date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date; global_count integer; user_count integer;
BEGIN
 IF owner IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=owner AND is_active) THEN RETURN false; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('recipe_ai_daily_budget',0));
 SELECT used INTO global_count FROM public.recipe_ai_budget WHERE day=d AND scope='global';
 SELECT used INTO user_count FROM public.recipe_ai_budget WHERE day=d AND scope=owner::text;
 IF coalesce(global_count,0)>=60 OR coalesce(user_count,0)>=10 THEN RETURN false; END IF;
 INSERT INTO public.recipe_ai_budget(day,scope,used) VALUES(d,'global',1),(d,owner::text,1)
 ON CONFLICT(day,scope) DO UPDATE SET used=recipe_ai_budget.used+1;
 DELETE FROM public.recipe_ai_budget WHERE day<d-7;
 RETURN true;
END $function$
;

CREATE OR REPLACE FUNCTION public.community_api(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
 uid uuid:=auth.uid(); pid bigint; rid bigint; cid bigint; req uuid; title text; txt text; cat text; img text;
 result jsonb; line jsonb; lines jsonb; steps jsonb; step_value jsonb; ing bigint; q numeric; un text;
 need numeric; stock numeric; shopping numeric; existing_id bigint; existing_unit text; delta numeric;
 n integer:=0; page integer; val integer;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 IF p_action='catalog' THEN
  RETURN jsonb_build_object('ingredients',coalesce((SELECT jsonb_agg(jsonb_build_object('id',"ingredientId",'name',"ingredientName",'unit',public.community_unit(unit),'image',coalesce(image,'')) ORDER BY "ingredientName") FROM public.ingredient i WHERE coalesce((to_jsonb(i)->>'is_publish')::boolean,true) OR to_jsonb(i)->>'userId' IS NULL OR to_jsonb(i)->>'userId'=uid::text),'[]'::jsonb),
  'recipes',coalesce((SELECT jsonb_agg(jsonb_build_object('id',r."recipeId",'title',r."recipeName",'image',coalesce(r.recipe_image,''),'category',coalesce(r."recipeCategory",''),'postId',coalesce(p.recipe_post_id,0),'published',coalesce(p.recipe_post_status='published',false)) ORDER BY r."recipeId" DESC)
  FROM public."Recipe" r LEFT JOIN public.recipe_post p ON p.recipe_id=r."recipeId" WHERE r."userId"=uid),'[]'::jsonb));
 END IF;
 IF p_action='feed' THEN
  page:=greatest(0,least(coalesce((p_payload->>'page')::int,0),10000));
  RETURN jsonb_build_object('posts',coalesce((SELECT jsonb_agg(public.community_card(x.recipe_post_id,uid) ORDER BY x.recipe_post_created_at DESC,x.recipe_post_id DESC) FROM
   (SELECT p.* FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
    WHERE p.recipe_post_status='published'
    AND (coalesce(p_payload->>'search','')='' OR strpos(lower(r."recipeName"),lower(btrim(p_payload->>'search')))>0)
    AND (coalesce(p_payload->>'category','All')='All' OR (p_payload->>'category'='Vegan' AND r.is_vegan) OR r."recipeCategory"=p_payload->>'category')
    AND (coalesce(p_payload->>'mode','feed')<>'saved' OR EXISTS(SELECT 1 FROM public.community_favourite_rows() f WHERE f.post_id=p.recipe_post_id AND f.user_id=uid))
    ORDER BY p.recipe_post_created_at DESC,p.recipe_post_id DESC LIMIT 30 OFFSET page*30) x),'[]'::jsonb));
 END IF;
 IF p_action IN ('draft','save_recipe') THEN
  rid:=nullif(p_payload->>'recipeId','0')::bigint;
  IF rid IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid) THEN RAISE EXCEPTION 'You can only edit your own recipe.'; END IF;
  IF p_action='draft' THEN
   RETURN (SELECT jsonb_build_object('recipeId',r."recipeId",'title',r."recipeName",'category',coalesce(r."recipeCategory",'Malaysian'),'description',coalesce(r.description,''),'image',coalesce(r.recipe_image,''),'vegan',coalesce(r.is_vegan,false),'steps',r.cooking_steps,
    'ingredients',coalesce((SELECT jsonb_agg(jsonb_build_object('id',ri."ingredientId",'name',i."ingredientName",'quantity',ri.quantity,'unit',public.community_unit(ri.unit),'image',coalesce(i.image,'')) ORDER BY ri.recipe_ingredient_id) FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=rid),'[]'::jsonb)) FROM public."Recipe" r WHERE r."recipeId"=rid);
  END IF;
  req:=(p_payload->>'requestId')::uuid;
  IF req IS NULL THEN RAISE EXCEPTION 'Missing save request ID.'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(uid::text||req::text,0));
  SELECT cr.result INTO result FROM public.community_request cr WHERE cr.user_id=uid AND cr.request_id=req;
  IF FOUND THEN RETURN result; END IF;
  title:=btrim(p_payload->>'title'); txt:=nullif(public.community_clean_text(p_payload->>'description'),''); cat:=btrim(p_payload->>'category'); img:=nullif(p_payload->>'image','');
  lines:=p_payload->'ingredients'; steps:=p_payload->'steps';
  IF title IS NULL OR char_length(title) NOT BETWEEN 1 AND 150 OR char_length(coalesce(txt,''))>5000 OR cat IS NULL OR char_length(cat) NOT BETWEEN 1 AND 500 THEN RAISE EXCEPTION 'Check the recipe title, category and description.'; END IF;
  IF jsonb_typeof(lines) IS DISTINCT FROM 'array' OR jsonb_array_length(lines) NOT BETWEEN 1 AND 50 THEN RAISE EXCEPTION 'Add 1 to 50 ingredients.'; END IF;
  IF jsonb_typeof(steps) IS DISTINCT FROM 'array' OR jsonb_array_length(steps) NOT BETWEEN 1 AND 30 THEN RAISE EXCEPTION 'Add 1 to 30 cooking steps.'; END IF;
  FOR step_value IN SELECT value FROM jsonb_array_elements(steps) LOOP
   IF jsonb_typeof(step_value)<>'string' OR char_length(btrim(step_value#>>'{}')) NOT BETWEEN 1 AND 500 THEN RAISE EXCEPTION 'Each step needs 1 to 500 characters.'; END IF;
  END LOOP;
  IF (SELECT count(DISTINCT value->>'id') FROM jsonb_array_elements(lines))<>jsonb_array_length(lines) THEN RAISE EXCEPTION 'Combine repeated ingredients into one line.'; END IF;
  FOR line IN SELECT value FROM jsonb_array_elements(lines) LOOP
   ing:=(line->>'id')::bigint; q:=(line->>'quantity')::numeric; un:=public.community_unit(line->>'unit');
   IF NOT EXISTS(SELECT 1 FROM public.ingredient i WHERE "ingredientId"=ing AND (coalesce((to_jsonb(i)->>'is_publish')::boolean,true) OR to_jsonb(i)->>'userId' IS NULL OR to_jsonb(i)->>'userId'=uid::text)) OR q IS NULL OR q<=0 OR q>1000000 OR un NOT IN ('g','kg','mL','L','pcs','bag','carton','bunch') OR (un IN ('pcs','bag','carton','bunch') AND q<>trunc(q)) THEN RAISE EXCEPTION 'Check ingredient quantities and units.'; END IF;
  END LOOP;
  IF rid IS NULL THEN
   INSERT INTO public."Recipe"("recipeName","recipeCategory",description,recipe_image,is_vegan,"userId",create_at,cooking_steps,community_request_id)
   VALUES(title,cat,txt,img,coalesce((p_payload->>'vegan')::boolean,false),uid,(extract(epoch FROM now())*1000)::bigint,steps,req) RETURNING "recipeId" INTO rid;
  ELSE
   PERFORM 1 FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid FOR UPDATE;
   UPDATE public."Recipe" SET "recipeName"=title,"recipeCategory"=cat,description=txt,recipe_image=img,is_vegan=coalesce((p_payload->>'vegan')::boolean,false),cooking_steps=steps WHERE "recipeId"=rid AND "userId"=uid;
   DELETE FROM public."Recipe_Ingredient" WHERE "recipeId"=rid;
  END IF;
  INSERT INTO public."Recipe_Ingredient"("recipeId","ingredientId",quantity,unit) SELECT rid,(value->>'id')::bigint,(value->>'quantity')::numeric,public.community_unit(value->>'unit') FROM jsonb_array_elements(lines);
  result:=jsonb_build_object('recipeId',rid);
  INSERT INTO public.community_request(user_id,request_id,result) VALUES(uid,req,result);
  RETURN result;
 END IF;
 IF p_action='publish' THEN
  rid:=(p_payload->>'recipeId')::bigint; txt:=nullif(public.community_clean_text(p_payload->>'caption'),'');
  PERFORM 1 FROM public."Recipe" WHERE "recipeId"=rid AND "userId"=uid FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Save your own recipe before publishing.'; END IF;
  IF char_length(coalesce(txt,''))>500 THEN RAISE EXCEPTION 'Caption must be 500 characters or fewer.'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public."Recipe_Ingredient" WHERE "recipeId"=rid) OR (SELECT jsonb_array_length(cooking_steps) FROM public."Recipe" WHERE "recipeId"=rid)=0 THEN RAISE EXCEPTION 'Add ingredients and cooking steps in Edit recipe before publishing.'; END IF;
  IF EXISTS(SELECT 1 FROM public."Recipe_Ingredient" WHERE "recipeId"=rid AND (quantity IS NULL OR quantity<=0 OR quantity>1000000 OR public.community_unit(unit) NOT IN ('g','kg','mL','L','pcs','bag','carton','bunch'))) THEN RAISE EXCEPTION 'Review ingredient quantities and units before publishing.'; END IF;
  INSERT INTO public.recipe_post(recipe_id,user_id,recipe_post_caption) VALUES(rid,uid,txt)
  ON CONFLICT(recipe_id) DO UPDATE SET recipe_post_status='published',recipe_post_caption=excluded.recipe_post_caption,recipe_post_updated_at=now()
  WHERE recipe_post.user_id=uid RETURNING recipe_post_id INTO pid;
  RETURN jsonb_build_object('postId',pid);
 END IF;
 pid:=(p_payload->>'postId')::bigint;
 IF p_action IN ('caption','unpublish') THEN
  PERFORM 1 FROM public.recipe_post WHERE recipe_post_id=pid AND user_id=uid FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Only the author can change this post.'; END IF;
  IF p_action='unpublish' THEN UPDATE public.recipe_post SET recipe_post_status='unpublished',recipe_post_updated_at=now() WHERE recipe_post_id=pid;
  ELSE
   txt:=nullif(public.community_clean_text(p_payload->>'caption'),''); IF char_length(coalesce(txt,''))>500 THEN RAISE EXCEPTION 'Caption is too long.'; END IF;
   UPDATE public.recipe_post SET recipe_post_caption=txt,recipe_post_updated_at=now() WHERE recipe_post_id=pid;
  END IF;
  RETURN jsonb_build_object('ok',true);
 END IF;
 -- Lock the post: no new interactions may race with an unpublish transaction.
 PERFORM 1 FROM public.recipe_post WHERE recipe_post_id=pid AND recipe_post_status='published' FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'This community post is no longer available.'; END IF;
 IF p_action='detail' THEN
  SELECT recipe_id INTO rid FROM public.recipe_post WHERE recipe_post_id=pid;
  RETURN jsonb_build_object('post',public.community_card(pid,uid),'steps',(SELECT cooking_steps FROM public."Recipe" WHERE "recipeId"=rid),
  'myRating',coalesce((SELECT recipe_rating_value FROM public.community_rating_rows() WHERE post_id=pid AND user_id=uid),0),
  'ingredients',coalesce((SELECT jsonb_agg(jsonb_build_object('id',i."ingredientId",'name',i."ingredientName",'image',coalesce(i.image,''),'quantity',ri.quantity,'unit',public.community_unit(ri.unit),'stock',public.community_stock(uid,i."ingredientId",ri.unit),
   'comparable',NOT EXISTS(SELECT 1 FROM public."Ingredient_User" b WHERE b.user_id=uid AND b."ingredientId"=i."ingredientId" AND b.quantity>0 AND public.community_convert(b.quantity,b.quantity_unit,ri.unit) IS NULL),
   'inList',coalesce((SELECT sum(public.community_convert(s.purchase_quantity,s.unit,ri.unit)) FROM public.shopping_list_items s WHERE s.user_id=uid AND s.ingredient_id=i."ingredientId" AND NOT s.is_purchased),0)))
    FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=rid),'[]'::jsonb),
  'comments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',c.recipe_comment_id,'text',c.recipe_comment_text,'owner',c.user_id,'author',coalesce(u.display_name,'Member'),'avatar',coalesce(u.profile_image_url,''),'createdAt',c.recipe_comment_created_at,'updatedAt',c.recipe_comment_updated_at,
   'likes',(SELECT count(*) FROM public.community_like_rows() l WHERE l.comment_id=c.recipe_comment_id),'liked',EXISTS(SELECT 1 FROM public.community_like_rows() l WHERE l.comment_id=c.recipe_comment_id AND l.user_id=uid)) ORDER BY c.recipe_comment_created_at,c.recipe_comment_id)
    FROM public.recipe_comment c LEFT JOIN public."User" u ON u.user_id=c.user_id WHERE c.post_id=pid),'[]'::jsonb));
 END IF;
 IF p_action IN ('rating','favourite') THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('community_interaction:'||uid::text||':'||pid::text,0));
 END IF;
 IF p_action='rating' THEN
  val:=(p_payload->>'value')::int;
  IF val IS NULL OR val NOT BETWEEN 1 AND 5 THEN RAISE EXCEPTION 'Select 1 to 5 stars.'; END IF;
  INSERT INTO public.community_interaction(post_id,user_id,rating_value,rating_created_at,rating_updated_at)
  VALUES(pid,uid,val,now(),now()) ON CONFLICT(post_id,user_id) DO UPDATE
  SET rating_value=excluded.rating_value,
      rating_created_at=coalesce(community_interaction.rating_created_at,excluded.rating_created_at),
      rating_updated_at=now();
 ELSIF p_action='favourite' THEN
  IF coalesce((p_payload->>'enabled')::boolean,false) THEN INSERT INTO public.community_interaction(post_id,user_id,is_favourite,favourited_at)
  VALUES(pid,uid,true,now()) ON CONFLICT(post_id,user_id) DO UPDATE
  SET is_favourite=true,favourited_at=CASE WHEN community_interaction.is_favourite
      THEN community_interaction.favourited_at ELSE excluded.favourited_at END;
  ELSE DELETE FROM public.community_interaction WHERE post_id=pid AND user_id=uid AND rating_value IS NULL;
   UPDATE public.community_interaction SET is_favourite=false,favourited_at=NULL
   WHERE post_id=pid AND user_id=uid AND rating_value IS NOT NULL; END IF;
 ELSIF p_action='comment' THEN
  txt:=public.community_clean_text(p_payload->>'text'); cid:=nullif(p_payload->>'commentId','0')::bigint;
  IF txt IS NULL OR char_length(txt) NOT BETWEEN 1 AND 1000 THEN RAISE EXCEPTION 'Comment must have 1 to 1000 characters.'; END IF;
  IF cid IS NULL THEN
   req:=(p_payload->>'requestId')::uuid; IF req IS NULL THEN RAISE EXCEPTION 'Missing comment request ID.'; END IF;
   INSERT INTO public.recipe_comment(post_id,user_id,recipe_comment_text,request_id) VALUES(pid,uid,txt,req) ON CONFLICT(user_id,request_id) DO NOTHING;
  ELSE
   UPDATE public.recipe_comment SET recipe_comment_text=txt,recipe_comment_updated_at=now() WHERE recipe_comment_id=cid AND post_id=pid AND user_id=uid;
   IF NOT FOUND THEN RAISE EXCEPTION 'Only the comment author can edit it.'; END IF;
  END IF;
 ELSIF p_action='delete_comment' THEN
  DELETE FROM public.recipe_comment WHERE recipe_comment_id=(p_payload->>'commentId')::bigint AND post_id=pid AND user_id=uid;
  IF NOT FOUND THEN RAISE EXCEPTION 'Only the comment author can remove it.'; END IF;
 ELSIF p_action='like' THEN
  cid:=(p_payload->>'commentId')::bigint;
  IF NOT EXISTS(SELECT 1 FROM public.recipe_comment WHERE recipe_comment_id=cid AND post_id=pid) THEN RAISE EXCEPTION 'Comment no longer exists.'; END IF;
  IF coalesce((p_payload->>'enabled')::boolean,false) THEN INSERT INTO public.community_interaction(comment_id,user_id,is_liked,liked_at)
  VALUES(cid,uid,true,now()) ON CONFLICT(comment_id,user_id) DO NOTHING;
  ELSE DELETE FROM public.community_interaction WHERE comment_id=cid AND user_id=uid; END IF;
 ELSIF p_action='add_missing' THEN
  req:=(p_payload->>'requestId')::uuid; IF req IS NULL THEN RAISE EXCEPTION 'Missing shopping request ID.'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended('shopping:'||uid::text,0));
  SELECT cr.result INTO result FROM public.community_request cr WHERE cr.user_id=uid AND cr.request_id=req;
  IF FOUND THEN RETURN result; END IF;
  SELECT recipe_id INTO rid FROM public.recipe_post WHERE recipe_post_id=pid;
  FOR line IN SELECT jsonb_build_object('id',"ingredientId",'quantity',sum(quantity),'unit',public.community_unit(unit)) FROM public."Recipe_Ingredient" WHERE "recipeId"=rid GROUP BY "ingredientId",public.community_unit(unit) LOOP
   ing:=(line->>'id')::bigint; un:=line->>'unit'; q:=(line->>'quantity')::numeric;
   IF un NOT IN ('g','kg','mL','L','pcs','bag','carton') THEN RAISE EXCEPTION 'A recipe uses %. Choose a supported shopping unit manually.',un; END IF;
   IF EXISTS(SELECT 1 FROM public."Ingredient_User" b WHERE b.user_id=uid AND b."ingredientId"=ing AND b.quantity>0 AND public.community_convert(b.quantity,b.quantity_unit,un) IS NULL) OR EXISTS(SELECT 1 FROM public.shopping_list_items s WHERE s.user_id=uid AND s.ingredient_id=ing AND NOT s.is_purchased AND public.community_convert(s.purchase_quantity,s.unit,un) IS NULL) THEN RAISE EXCEPTION 'Some units cannot be compared. Review these ingredients manually.'; END IF;
   stock:=public.community_stock(uid,ing,un);
   SELECT coalesce(sum(public.community_convert(s.purchase_quantity,s.unit,un)),0) INTO shopping FROM public.shopping_list_items s WHERE s.user_id=uid AND s.ingredient_id=ing AND NOT s.is_purchased;
   SELECT s.id,s.unit INTO existing_id,existing_unit FROM public.shopping_list_items s WHERE s.user_id=uid AND s.ingredient_id=ing AND NOT s.is_purchased AND public.community_convert(1,s.unit,un) IS NOT NULL ORDER BY s.id LIMIT 1 FOR UPDATE;
   -- Keep original recipe demand separate from the amount still to buy.
   -- Otherwise Purchase Review would subtract the same pantry stock twice.
   IF existing_id IS NOT NULL THEN
    delta:=public.community_convert(q,un,existing_unit);
    IF delta>999 OR delta<>round(delta,3) OR (existing_unit IN ('pcs','bag','carton') AND delta<>trunc(delta)) THEN RAISE EXCEPTION 'The combined quantity exceeds shopping limits. Edit the existing shopping item manually.'; END IF;
    UPDATE public.shopping_list_items SET planned_quantity=greatest(planned_quantity,delta) WHERE id=existing_id AND user_id=uid;
   END IF;
   need:=greatest(q-stock-shopping,0); IF need<=0 THEN CONTINUE; END IF;
   IF un IN ('pcs','bag','carton') THEN need:=ceil(need); ELSE need:=ceil(need*1000)/1000; END IF;
   IF q<=0 OR q>999 OR need>999 OR (un IN ('pcs','bag','carton') AND q<>trunc(q)) OR (un IN ('g','kg','mL','L') AND q<>round(q,3)) THEN RAISE EXCEPTION 'The required quantity exceeds shopping limits. Choose another unit or split the quantity manually.'; END IF;
   IF existing_id IS NULL THEN
    INSERT INTO public.shopping_list_items(user_id,ingredient_id,planned_quantity,purchase_quantity,unit,is_purchased) VALUES(uid,ing,q,need,un,false);
   ELSE
    delta:=public.community_convert(need,un,existing_unit);
    IF delta<>round(delta,3) OR (existing_unit IN ('pcs','bag','carton') AND delta<>trunc(delta)) OR (SELECT purchase_quantity+delta>999 FROM public.shopping_list_items WHERE id=existing_id) THEN RAISE EXCEPTION 'The combined quantity exceeds shopping limits. Edit it manually.'; END IF;
    UPDATE public.shopping_list_items SET purchase_quantity=purchase_quantity+delta,review_handled=false WHERE id=existing_id AND user_id=uid;
   END IF;
   n:=n+1;
  END LOOP;
  result:=jsonb_build_object('added',n); INSERT INTO public.community_request(user_id,request_id,result) VALUES(uid,req,result); RETURN result;
 ELSE RAISE EXCEPTION 'Unknown community action.';
 END IF;
 RETURN jsonb_build_object('ok',true);
END $function$
;

CREATE OR REPLACE FUNCTION public.community_card(pid bigint, viewer uuid)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
 SELECT jsonb_build_object('id',p.recipe_post_id,'recipeId',r."recipeId",'title',r."recipeName",'category',coalesce(r."recipeCategory",''),
 'description',coalesce(r.description,''),'image',coalesce(r.recipe_image,''),'caption',coalesce(p.recipe_post_caption,''),
 'owner',p.user_id,'author',coalesce(u.display_name,'SmartPantry member'),'avatar',coalesce(u.profile_image_url,''),
 'published',p.recipe_post_status='published','createdAt',p.recipe_post_created_at,
 'rating',coalesce((SELECT round(avg(recipe_rating_value),1) FROM public.community_rating_rows() WHERE post_id=pid),0),
 'ratingCount',(SELECT count(*) FROM public.community_rating_rows() WHERE post_id=pid),
 'commentCount',(SELECT count(*) FROM public.recipe_comment WHERE post_id=pid),
 'favourite',EXISTS(SELECT 1 FROM public.community_favourite_rows() WHERE post_id=pid AND user_id=viewer))
 FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id LEFT JOIN public."User" u ON u.user_id=p.user_id WHERE p.recipe_post_id=pid
$function$
;

CREATE OR REPLACE FUNCTION public.community_design_v1(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid(); result jsonb; rid bigint; video text:=btrim(coalesce(p_payload->>'videoUrl','')); timers jsonb:=coalesce(p_payload->'stepTimers','[]'::jsonb);
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN
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
END $function$
;

CREATE OR REPLACE FUNCTION public.community_for_you(p_search text DEFAULT ''::text, p_page integer DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid(); result jsonb; today date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
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
END $function$
;

CREATE OR REPLACE FUNCTION public.community_for_you_v6(p_search text DEFAULT ''::text, p_page integer DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid(); result jsonb; today date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
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
   row_number() OVER(PARTITION BY s.soon_date IS NOT NULL ORDER BY s.soon_date NULLS LAST,s.soon_count DESC,s.covered::numeric/nullif(s.required,0) DESC,(s.required-s.covered),c."recipeId" DESC) group_position
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
     'soonCount',p.soon_count,'soonNames',p.soon_names,'allAvailable',p.all_available,'useSoon',p.soon_date IS NOT NULL,'soonDate',p.soon_date,'missingNames',p.missing_names)
   ORDER BY p.position) FROM page p WHERE p.position<=greatest(0,least(coalesce(p_page,0),10000))*30+30),'[]'::jsonb)) INTO result;
 RETURN result;
END $function$
;

CREATE OR REPLACE FUNCTION public.community_recipe_shopping(action text, payload jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
 uid uuid:=auth.uid(); rid bigint; pid bigint:=nullif(payload->>'postId','0')::bigint;
 req uuid; result jsonb; line record; target record; chosen_unit text;
 planned numeric; buy numeric; added integer:=0;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN
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
END $function$
;

CREATE OR REPLACE FUNCTION public.community_v5(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid(); result jsonb; rid bigint; row_recipe public."Recipe"%ROWTYPE; n integer;
 c text:=nullif(btrim(p_payload->>'cuisine'),''); d text:=nullif(btrim(p_payload->>'dishType'),''); diet text:=nullif(btrim(p_payload->>'dietary'),'');
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 IF p_action='feed' THEN
  n:=greatest(0,least(coalesce((p_payload->>'page')::integer,0),10000));
  SELECT jsonb_build_object('posts',coalesce(jsonb_agg(public.community_card(x.recipe_post_id,uid)||jsonb_build_object('cuisine',x.cuisine,'dishType',x.dish_type,'dietary',x.dietary_preference) ORDER BY x.recipe_post_created_at DESC,x.recipe_post_id DESC),'[]'::jsonb)) INTO result
  FROM (SELECT p.*,r.cuisine,r.dish_type,r.dietary_preference FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
   WHERE p.recipe_post_status='published'
    AND strpos(lower(r."recipeName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0
    AND (coalesce(p_payload->>'mode','feed')<>'saved' OR EXISTS(SELECT 1 FROM public.community_favourite_rows() f WHERE f.post_id=p.recipe_post_id AND f.user_id=uid))
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
END $function$
;

CREATE OR REPLACE FUNCTION public.community_v6(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid();result jsonb;rid bigint;row_recipe public."Recipe"%ROWTYPE;n integer;post_id bigint;
 c text:=nullif(btrim(p_payload->>'cuisine'),'');d text:=nullif(btrim(p_payload->>'dishType'),'');diet text:=nullif(btrim(p_payload->>'dietary'),'');
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
 IF p_action='feed' THEN
  n:=greatest(0,least(coalesce((p_payload->>'page')::integer,0),10000));
  SELECT jsonb_build_object('posts',coalesce(jsonb_agg(public.community_card(x.recipe_post_id,uid)||jsonb_build_object('cuisine',x.cuisine,'dishType',x.dish_type,'dietary',x.dietary_preference) ORDER BY x.recipe_post_created_at DESC,x.recipe_post_id DESC),'[]'::jsonb)) INTO result
  FROM (SELECT p.*,r.cuisine,r.dish_type,r.dietary_preference FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
   WHERE p.recipe_post_status='published'
    AND (strpos(lower(r."recipeName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0 OR EXISTS(SELECT 1 FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=r."recipeId" AND strpos(lower(i."ingredientName"),lower(left(btrim(coalesce(p_payload->>'search','')),80)))>0))
    AND (coalesce(p_payload->>'mode','feed')<>'saved' OR EXISTS(SELECT 1 FROM public.community_favourite_rows() f WHERE f.post_id=p.recipe_post_id AND f.user_id=uid))
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
   'description',r.description,'rating',coalesce((SELECT avg(recipe_rating_value) FROM public.community_rating_rows() rr WHERE rr.post_id=(x.row->>'postId')::bigint),0),
   'ratingCount',(SELECT count(*) FROM public.community_rating_rows() rr WHERE rr.post_id=(x.row->>'postId')::bigint),'createdAt',to_timestamp(r.create_at/1000.0),
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
END $function$
;

CREATE OR REPLACE FUNCTION public.handle_new_user()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  prefix text := split_part(new.email, '@', 1);
begin
  insert into public."User" (user_id, email, display_name, phone_no, household_size, date_of_birth)
  values (
    new.id,
    new.email,
    coalesce(
      new.raw_user_meta_data ->> 'display_name',
      left(case when length(prefix) < 3 then prefix || '___' else prefix end, 20)
    ),
    coalesce(new.raw_user_meta_data ->> 'phone_no', ''),
    coalesce(nullif(new.raw_user_meta_data ->> 'household_size', '')::int, 1),
    nullif(new.raw_user_meta_data ->> 'date_of_birth', '')::date
  );
  return new;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.notifications_api(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid();settings public.app_notification_settings%ROWTYPE;today date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date;result jsonb;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.';END IF;
 INSERT INTO public.app_notification_settings(user_id)VALUES(uid)ON CONFLICT DO NOTHING;
 SELECT * INTO settings FROM public.app_notification_settings WHERE user_id=uid;
 IF p_action='settings' THEN RETURN to_jsonb(settings)-'user_id'; END IF;
 IF p_action='save_settings' THEN
  UPDATE public.app_notification_settings SET comments=coalesce((p_payload->>'comments')::boolean,comments),likes=coalesce((p_payload->>'likes')::boolean,likes),
   pantry=coalesce((p_payload->>'pantry')::boolean,pantry),sync=coalesce((p_payload->>'sync')::boolean,sync),push=coalesce((p_payload->>'push')::boolean,push),
   reminder_days=coalesce((p_payload->>'reminder_days')::integer,reminder_days),quiet=coalesce((p_payload->>'quiet')::boolean,quiet),
   quiet_start=coalesce((p_payload->>'quiet_start')::integer,quiet_start),quiet_end=coalesce((p_payload->>'quiet_end')::integer,quiet_end) WHERE user_id=uid RETURNING * INTO settings;
  RETURN to_jsonb(settings)-'user_id';
 ELSIF p_action='read' THEN UPDATE public.app_notification SET read_at=coalesce(read_at,now()) WHERE user_id=uid AND id=(p_payload->>'id')::bigint;
 ELSIF p_action='read_all' THEN UPDATE public.app_notification SET read_at=coalesce(read_at,now()) WHERE user_id=uid;
 ELSIF p_action='sync_issue' THEN
  INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key)VALUES(uid,'System','sync','Shopping list could not sync','Your changes are saved on this device. Open your shopping list and retry when connected.','sync:'||today)ON CONFLICT DO NOTHING;
 ELSIF p_action NOT IN ('list','refresh') THEN RAISE EXCEPTION 'Unknown notification action. Reopen notifications.';
 END IF;
 -- Remove reminders that no longer represent the current batch/expiry, including consumed stock.
 DELETE FROM public.app_notification n WHERE n.user_id=uid AND n.category='Pantry' AND NOT EXISTS(
  SELECT 1 FROM public."Ingredient_User" b WHERE b."IngredientUser_Id"=n.batch_id AND b.user_id=uid AND b.quantity>0 AND
  n.event_key='batch:'||b."IngredientUser_Id"||':'||b."expiredDate"||':'||CASE WHEN (to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date<today THEN 'expired' ELSE 'soon' END);
 INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,batch_id)
 SELECT uid,'Pantry','expiry',coalesce(i."ingredientName",'Ingredient')||CASE WHEN d.expiry<today THEN ' has expired' WHEN d.expiry=today THEN ' expires today' WHEN d.expiry=today+1 THEN ' expires tomorrow' ELSE ' expires in '||(d.expiry-today)||' days' END,
 b.quantity||' '||coalesce(b.quantity_unit,'')||' · '||coalesce(nullif(b.location_storage,''),'Pantry'),
 'batch:'||b."IngredientUser_Id"||':'||b."expiredDate"||':'||CASE WHEN d.expiry<today THEN 'expired' ELSE 'soon' END,b."IngredientUser_Id"
 FROM public."Ingredient_User" b LEFT JOIN public.ingredient i ON i."ingredientId"=b."ingredientId"
 CROSS JOIN LATERAL(SELECT (to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date expiry)d
 WHERE b.user_id=uid AND b.quantity>0 AND d.expiry<=today+settings.reminder_days
 ON CONFLICT(user_id,event_key)DO UPDATE SET title=excluded.title,body=excluded.body;
 SELECT jsonb_build_object('settings',to_jsonb(settings)-'user_id','items',coalesce(jsonb_agg(to_jsonb(x) ORDER BY x.created_at DESC,x.id DESC),'[]'::jsonb),
 'unread',coalesce(sum(CASE WHEN x.read_at IS NULL THEN 1 ELSE 0 END),0)) INTO result FROM (
 SELECT n.*,u.profile_image_url avatar,r.recipe_image image,b."ingredientId" ingredient_id,(to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date expiry
 FROM public.app_notification n LEFT JOIN public."User" u ON u.user_id=n.actor_id LEFT JOIN public.recipe_post p ON p.recipe_post_id=n.post_id LEFT JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
 LEFT JOIN public."Ingredient_User" b ON b."IngredientUser_Id"=n.batch_id
 WHERE n.user_id=uid AND ((n.kind='comment' AND settings.comments) OR(n.kind='like' AND settings.likes) OR(n.category='Pantry' AND settings.pantry) OR(n.category='System' AND settings.sync))
 ORDER BY n.created_at DESC,n.id DESC LIMIT 200)x;
 RETURN result;
END $function$
;

CREATE OR REPLACE FUNCTION public.notify_community_activity()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE recipient uuid;pid bigint;cid bigint;actor_name text;dish text;kind_name text;body_text text;
BEGIN
 IF TG_TABLE_NAME='recipe_comment' THEN
  pid:=NEW.post_id;cid:=NEW.recipe_comment_id;kind_name:='comment';body_text:=left(public.community_clean_text(NEW.recipe_comment_text),180);
  SELECT p.user_id,r."recipeName" INTO recipient,dish FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id WHERE p.recipe_post_id=pid AND p.recipe_post_status='published';
 ELSE
  cid:=NEW.comment_id;kind_name:='like';
  SELECT c.user_id,c.post_id,left(c.recipe_comment_text,180),r."recipeName" INTO recipient,pid,body_text,dish FROM public.recipe_comment c JOIN public.recipe_post p ON p.recipe_post_id=c.post_id JOIN public."Recipe" r ON r."recipeId"=p.recipe_id WHERE c.recipe_comment_id=cid AND p.recipe_post_status='published';
 END IF;
 IF recipient IS NULL OR recipient=NEW.user_id THEN RETURN NEW; END IF;
 SELECT display_name INTO actor_name FROM public."User" WHERE user_id=NEW.user_id;
 INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,post_id,comment_id,actor_id)
 VALUES(recipient,'Interactions',kind_name,coalesce(actor_name,'A member')||CASE WHEN kind_name='comment' THEN ' commented on your ' ELSE ' liked your comment on ' END||dish,body_text,
 kind_name||':'||cid||':'||NEW.user_id,pid,cid,NEW.user_id) ON CONFLICT(user_id,event_key) DO NOTHING;
 RETURN NEW;
END $function$
;

CREATE OR REPLACE FUNCTION public.shopping_api(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid(); req uuid; sid bigint; ing bigint; custom bigint; bid bigint; q numeric; planned numeric; price numeric; un text; n text; photo text; pd date; ed date; old public.shopping_list_items%ROWTYPE;
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

CREATE OR REPLACE FUNCTION public.sync_email()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  update public."User" set email = new.email where user_id = new.id;
  return new;
end; $function$
;

CREATE OR REPLACE FUNCTION public.sync_user_email()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  update public."User" set email = new.email where user_id = new.id;
  return new;
end; $function$
;
-- Recreate only a missing standard activity notification hook. Existing active hooks are retained.
DO $hooks$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='public.recipe_comment'::regclass AND tgfoid='public.notify_community_activity()'::regprocedure AND NOT tgisinternal AND tgenabled<>'D') THEN
  CREATE TRIGGER community_comment_notification_user_id_fix AFTER INSERT ON public.recipe_comment FOR EACH ROW EXECUTE FUNCTION public.notify_community_activity();
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='public.community_interaction'::regclass AND tgfoid='public.notify_community_activity()'::regprocedure AND NOT tgisinternal AND tgenabled<>'D') THEN
  CREATE TRIGGER community_like_notification_user_id_fix AFTER INSERT OR UPDATE OF is_liked ON public.community_interaction FOR EACH ROW WHEN (NEW.comment_id IS NOT NULL AND NEW.is_liked) EXECUTE FUNCTION public.notify_community_activity();
 END IF;
END $hooks$;
NOTIFY pgrst,'reload schema';
COMMIT;
SELECT 'User.user_id references repaired. Existing community_interaction data retained.' AS result;
