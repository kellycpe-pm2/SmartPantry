-- SmartPantry Community, based on integration b92a5c2. Run ONCE in Supabase SQL Editor.
-- Additive migration: existing personal recipes, ingredients and inventory are retained.
BEGIN;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS cooking_steps jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public."Recipe" ADD COLUMN IF NOT EXISTS community_request_id uuid;
CREATE UNIQUE INDEX IF NOT EXISTS recipe_community_request ON public."Recipe" ("userId",community_request_id) WHERE community_request_id IS NOT NULL;
CREATE TABLE IF NOT EXISTS public.recipe_post (
 recipe_post_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 recipe_post_caption text CHECK (char_length(recipe_post_caption)<=500),
 recipe_post_status text NOT NULL DEFAULT 'published' CHECK(recipe_post_status IN ('published','unpublished')),
 recipe_post_created_at timestamptz NOT NULL DEFAULT now(),
 recipe_post_updated_at timestamptz NOT NULL DEFAULT now(),
 recipe_id bigint NOT NULL UNIQUE REFERENCES public."Recipe"("recipeId") ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS public.recipe_rating (
 recipe_rating_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 recipe_rating_value smallint NOT NULL CHECK(recipe_rating_value BETWEEN 1 AND 5),
 recipe_rating_created_at timestamptz NOT NULL DEFAULT now(),
 recipe_rating_updated_at timestamptz NOT NULL DEFAULT now(),
 post_id bigint NOT NULL REFERENCES public.recipe_post ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE,
 UNIQUE(post_id,user_id)
);
CREATE TABLE IF NOT EXISTS public.recipe_favourite (
 recipe_favourite_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 recipe_favourite_created_at timestamptz NOT NULL DEFAULT now(),
 post_id bigint NOT NULL REFERENCES public.recipe_post ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE,
 UNIQUE(post_id,user_id)
);
CREATE TABLE IF NOT EXISTS public.recipe_comment (
 recipe_comment_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 recipe_comment_text text NOT NULL CHECK(char_length(btrim(recipe_comment_text)) BETWEEN 1 AND 1000),
 recipe_comment_created_at timestamptz NOT NULL DEFAULT now(),
 recipe_comment_updated_at timestamptz NOT NULL DEFAULT now(),
 post_id bigint NOT NULL REFERENCES public.recipe_post ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE,
 request_id uuid,
 UNIQUE(user_id,request_id)
);
ALTER TABLE public.recipe_comment ADD COLUMN IF NOT EXISTS request_id uuid;
CREATE UNIQUE INDEX IF NOT EXISTS community_comment_request ON public.recipe_comment(user_id,request_id);
CREATE TABLE IF NOT EXISTS public.comment_like (
 comment_like_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 comment_like_created_at timestamptz NOT NULL DEFAULT now(),
 comment_id bigint NOT NULL REFERENCES public.recipe_comment ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE,
 UNIQUE(comment_id,user_id)
);
CREATE TABLE IF NOT EXISTS public.community_request (
 user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE,
 request_id uuid NOT NULL,
 result jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,request_id)
);
CREATE INDEX IF NOT EXISTS community_feed_date ON public.recipe_post(recipe_post_status,recipe_post_created_at DESC);
CREATE INDEX IF NOT EXISTS community_comment_post ON public.recipe_comment(post_id,recipe_comment_created_at);
-- Access goes through checked RPCs. Never grant clients direct mutation rights.
ALTER TABLE public.recipe_post ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recipe_rating ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recipe_favourite ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recipe_comment ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comment_like ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_request ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.recipe_post,public.recipe_rating,public.recipe_favourite,public.recipe_comment,public.comment_like,public.community_request FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.community_unit(p text) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path=public,pg_temp AS $$
 SELECT CASE lower(btrim(coalesce(p,''))) WHEN 'g' THEN 'g' WHEN 'kg' THEN 'kg' WHEN 'ml' THEN 'mL' WHEN 'l' THEN 'L'
 WHEN 'piece' THEN 'pcs' WHEN 'pieces' THEN 'pcs' WHEN 'pc' THEN 'pcs' WHEN 'pcs' THEN 'pcs'
 WHEN 'bag' THEN 'bag' WHEN 'bags' THEN 'bag' WHEN 'carton' THEN 'carton' WHEN 'cartons' THEN 'carton'
 WHEN 'bunch' THEN 'bunch' ELSE lower(btrim(coalesce(p,''))) END
$$;
CREATE OR REPLACE FUNCTION public.community_convert(q numeric,src text,dst text) RETURNS numeric
LANGUAGE sql IMMUTABLE SET search_path=public,pg_temp AS $$
 SELECT CASE WHEN public.community_unit(src)=public.community_unit(dst) THEN q
 WHEN public.community_unit(src)='kg' AND public.community_unit(dst)='g' THEN q*1000
 WHEN public.community_unit(src)='g' AND public.community_unit(dst)='kg' THEN q/1000
 WHEN public.community_unit(src)='L' AND public.community_unit(dst)='mL' THEN q*1000
 WHEN public.community_unit(src)='mL' AND public.community_unit(dst)='L' THEN q/1000 ELSE NULL END
$$;
CREATE OR REPLACE FUNCTION public.community_stock(owner uuid, ing bigint, unit_name text) RETURNS numeric
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
 SELECT coalesce(sum(public.community_convert(i.quantity,i.quantity_unit,unit_name)),0)
 FROM public."Ingredient_User" i WHERE i.user_id=owner AND i."ingredientId"=ing
 AND i.quantity>0 AND (to_timestamp(i."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date >= (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date
$$;
REVOKE ALL ON FUNCTION public.community_stock(uuid,bigint,text) FROM PUBLIC;

CREATE OR REPLACE FUNCTION public.community_card(pid bigint, viewer uuid) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
 SELECT jsonb_build_object('id',p.recipe_post_id,'recipeId',r."recipeId",'title',r."recipeName",'category',coalesce(r."recipeCategory",''),
 'description',coalesce(r.description,''),'image',coalesce(r.recipe_image,''),'caption',coalesce(p.recipe_post_caption,''),
 'owner',p.user_id,'author',coalesce(u.display_name,'SmartPantry member'),'avatar',coalesce(u.profile_image_url,''),
 'published',p.recipe_post_status='published','createdAt',p.recipe_post_created_at,
 'rating',coalesce((SELECT round(avg(recipe_rating_value),1) FROM public.recipe_rating WHERE post_id=pid),0),
 'ratingCount',(SELECT count(*) FROM public.recipe_rating WHERE post_id=pid),
 'commentCount',(SELECT count(*) FROM public.recipe_comment WHERE post_id=pid),
 'favourite',EXISTS(SELECT 1 FROM public.recipe_favourite WHERE post_id=pid AND user_id=viewer))
 FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id LEFT JOIN public."User" u ON u.id=p.user_id WHERE p.recipe_post_id=pid
$$;
REVOKE ALL ON FUNCTION public.community_card(bigint,uuid) FROM PUBLIC;

CREATE OR REPLACE FUNCTION public.community_clean_text(value text) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path=public,pg_temp AS $$
 SELECT btrim(regexp_replace(
 replace(replace(coalesce(value,''),chr(13)||chr(10),chr(10)),chr(13),chr(10)),
 chr(10)||'[[:blank:]]*'||chr(10)||'(?:[[:blank:]]*'||chr(10)||')+',
 chr(10)||chr(10),'g'),chr(9)||chr(10)||chr(13)||' ')
$$;
CREATE OR REPLACE FUNCTION public.community_api(p_action text,p_payload jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE
 uid uuid:=auth.uid(); pid bigint; rid bigint; cid bigint; req uuid; title text; txt text; cat text; img text;
 result jsonb; line jsonb; lines jsonb; steps jsonb; step_value jsonb; ing bigint; q numeric; un text;
 need numeric; stock numeric; shopping numeric; existing_id bigint; existing_unit text; delta numeric;
 n integer:=0; page integer; val integer;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.'; END IF;
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
    AND (coalesce(p_payload->>'mode','feed')<>'saved' OR EXISTS(SELECT 1 FROM public.recipe_favourite f WHERE f.post_id=p.recipe_post_id AND f.user_id=uid))
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
  'myRating',coalesce((SELECT recipe_rating_value FROM public.recipe_rating WHERE post_id=pid AND user_id=uid),0),
  'ingredients',coalesce((SELECT jsonb_agg(jsonb_build_object('id',i."ingredientId",'name',i."ingredientName",'image',coalesce(i.image,''),'quantity',ri.quantity,'unit',public.community_unit(ri.unit),'stock',public.community_stock(uid,i."ingredientId",ri.unit),
   'comparable',NOT EXISTS(SELECT 1 FROM public."Ingredient_User" b WHERE b.user_id=uid AND b."ingredientId"=i."ingredientId" AND b.quantity>0 AND public.community_convert(b.quantity,b.quantity_unit,ri.unit) IS NULL),
   'inList',coalesce((SELECT sum(public.community_convert(s.purchase_quantity,s.unit,ri.unit)) FROM public.shopping_list_items s WHERE s.user_id=uid AND s.ingredient_id=i."ingredientId" AND NOT s.is_purchased),0)))
    FROM public."Recipe_Ingredient" ri JOIN public.ingredient i ON i."ingredientId"=ri."ingredientId" WHERE ri."recipeId"=rid),'[]'::jsonb),
  'comments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',c.recipe_comment_id,'text',c.recipe_comment_text,'owner',c.user_id,'author',coalesce(u.display_name,'Member'),'avatar',coalesce(u.profile_image_url,''),'createdAt',c.recipe_comment_created_at,'updatedAt',c.recipe_comment_updated_at,
   'likes',(SELECT count(*) FROM public.comment_like l WHERE l.comment_id=c.recipe_comment_id),'liked',EXISTS(SELECT 1 FROM public.comment_like l WHERE l.comment_id=c.recipe_comment_id AND l.user_id=uid)) ORDER BY c.recipe_comment_created_at,c.recipe_comment_id)
    FROM public.recipe_comment c LEFT JOIN public."User" u ON u.id=c.user_id WHERE c.post_id=pid),'[]'::jsonb));
 END IF;
 IF p_action='rating' THEN
  val:=(p_payload->>'value')::int;
  IF val IS NULL OR val NOT BETWEEN 1 AND 5 THEN RAISE EXCEPTION 'Select 1 to 5 stars.'; END IF;
  INSERT INTO public.recipe_rating(post_id,user_id,recipe_rating_value) VALUES(pid,uid,val) ON CONFLICT(post_id,user_id) DO UPDATE SET recipe_rating_value=excluded.recipe_rating_value,recipe_rating_updated_at=now();
 ELSIF p_action='favourite' THEN
  IF coalesce((p_payload->>'enabled')::boolean,false) THEN INSERT INTO public.recipe_favourite(post_id,user_id) VALUES(pid,uid) ON CONFLICT DO NOTHING;
  ELSE DELETE FROM public.recipe_favourite WHERE post_id=pid AND user_id=uid; END IF;
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
  IF coalesce((p_payload->>'enabled')::boolean,false) THEN INSERT INTO public.comment_like(comment_id,user_id) VALUES(cid,uid) ON CONFLICT DO NOTHING;
  ELSE DELETE FROM public.comment_like WHERE comment_id=cid AND user_id=uid; END IF;
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
    UPDATE public.shopping_list_items SET planned_quantity=greatest(planned_quantity,public.community_convert(q,un,existing_unit)) WHERE id=existing_id AND user_id=uid;
   END IF;
   need:=greatest(q-stock-shopping,0); IF need<=0 THEN CONTINUE; END IF;
   IF q NOT BETWEEN 1 AND 999 OR q<>trunc(q) OR need>999 OR need<>trunc(need) THEN RAISE EXCEPTION 'This recipe needs a shopping quantity outside 1 to 999 whole units. Add it manually using another unit or split the quantity.'; END IF;
   IF un IN ('pcs','bag','carton') THEN need:=ceil(need); END IF;
   IF existing_id IS NULL THEN
    INSERT INTO public.shopping_list_items(user_id,ingredient_id,planned_quantity,purchase_quantity,unit,is_purchased) VALUES(uid,ing,q,need,un,false);
   ELSE
    delta:=public.community_convert(need,un,existing_unit);
    UPDATE public.shopping_list_items SET purchase_quantity=purchase_quantity+delta WHERE id=existing_id AND user_id=uid;
   END IF;
   n:=n+1;
  END LOOP;
  result:=jsonb_build_object('added',n); INSERT INTO public.community_request(user_id,request_id,result) VALUES(uid,req,result); RETURN result;
 ELSE RAISE EXCEPTION 'Unknown community action.';
 END IF;
 RETURN jsonb_build_object('ok',true);
END $$;
REVOKE ALL ON FUNCTION public.community_api(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.community_api(text,jsonb) TO authenticated;

-- Dedicated image bucket; UID folder prevents overwriting other members' photographs.
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
VALUES('community-images','community-images',true,5242880,ARRAY['image/jpeg','image/png','image/webp']) ON CONFLICT(id) DO NOTHING;
DROP POLICY IF EXISTS community_image_insert ON storage.objects;
CREATE POLICY community_image_insert ON storage.objects FOR INSERT TO authenticated WITH CHECK(bucket_id='community-images' AND (storage.foldername(name))[1]=auth.uid()::text);
DROP POLICY IF EXISTS community_image_delete ON storage.objects;
CREATE POLICY community_image_delete ON storage.objects FOR DELETE TO authenticated USING(bucket_id='community-images' AND (storage.foldername(name))[1]=auth.uid()::text);
COMMIT;
NOTIFY pgrst,'reload schema';
