-- Apply after the earlier 1127 features + permissions. Additive; no AI changes.
BEGIN;
DO $$ BEGIN
 IF to_regprocedure('public.community_v5(text,jsonb)') IS NULL THEN RAISE EXCEPTION 'Run the previous 1127 features and permissions SQL first. Nothing was applied.'; END IF;
END $$;
CREATE OR REPLACE FUNCTION public.community_for_you_v6(p_search text DEFAULT '',p_page integer DEFAULT 0)
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
END $$;
REVOKE ALL ON FUNCTION public.community_for_you_v6(text,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_for_you_v6(text,integer) TO authenticated;


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
  RETURN result||jsonb_build_object('wasteRate',coalesce((SELECT round(100.0*sum(wasted)/nullif(sum(total),0),1) FROM (
   SELECT count(*) wasted,count(*) total FROM public.waste_log WHERE user_id=uid AND record_type='Wasted' AND record_date BETWEEN date_trunc('week',now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date AND (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date
   UNION ALL SELECT 0,count(*) FROM public.food_log WHERE user_id=uid AND record_type='Consumed' AND record_date BETWEEN date_trunc('week',now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date AND (now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date
  ) counts),0));
 END IF;
 RETURN public.community_v5(p_action,p_payload);
END $$;
REVOKE ALL ON FUNCTION public.community_v6(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.community_v6(text,jsonb) TO authenticated;

CREATE TABLE IF NOT EXISTS public.app_notification_settings (
 user_id uuid PRIMARY KEY REFERENCES public."User"(id) ON DELETE CASCADE,
 comments boolean NOT NULL DEFAULT true,likes boolean NOT NULL DEFAULT true,pantry boolean NOT NULL DEFAULT true,sync boolean NOT NULL DEFAULT true,
 push boolean NOT NULL DEFAULT false,reminder_days integer NOT NULL DEFAULT 3 CHECK(reminder_days IN (1,2,3,7)),
 quiet boolean NOT NULL DEFAULT false,quiet_start integer NOT NULL DEFAULT 22 CHECK(quiet_start BETWEEN 0 AND 23),quiet_end integer NOT NULL DEFAULT 8 CHECK(quiet_end BETWEEN 0 AND 23)
);
CREATE TABLE IF NOT EXISTS public.app_notification (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,user_id uuid NOT NULL REFERENCES public."User"(id) ON DELETE CASCADE,
 category text NOT NULL CHECK(category IN ('Interactions','Pantry','System')),kind text NOT NULL,
 title text NOT NULL,body text NOT NULL DEFAULT '',event_key text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),read_at timestamptz,
 post_id bigint REFERENCES public.recipe_post(recipe_post_id) ON DELETE CASCADE,
 comment_id bigint REFERENCES public.recipe_comment(recipe_comment_id) ON DELETE CASCADE,
 batch_id bigint REFERENCES public."Ingredient_User"("IngredientUser_Id") ON DELETE CASCADE,
 actor_id uuid REFERENCES public."User"(id) ON DELETE SET NULL,
 UNIQUE(user_id,event_key)
);
ALTER TABLE public.app_notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_notification_settings ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.app_notification,public.app_notification_settings FROM PUBLIC,anon,authenticated;
CREATE INDEX IF NOT EXISTS app_notification_owner_date ON public.app_notification(user_id,created_at DESC,id DESC);

CREATE OR REPLACE FUNCTION public.notify_community_activity() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
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
 SELECT display_name INTO actor_name FROM public."User" WHERE id=NEW.user_id;
 INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,post_id,comment_id,actor_id)
 VALUES(recipient,'Interactions',kind_name,coalesce(actor_name,'A member')||CASE WHEN kind_name='comment' THEN ' commented on your ' ELSE ' liked your comment on ' END||dish,body_text,
 kind_name||':'||cid||':'||NEW.user_id,pid,cid,NEW.user_id) ON CONFLICT(user_id,event_key) DO NOTHING;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS community_comment_notification ON public.recipe_comment;
CREATE TRIGGER community_comment_notification AFTER INSERT ON public.recipe_comment FOR EACH ROW EXECUTE FUNCTION public.notify_community_activity();
DROP TRIGGER IF EXISTS community_like_notification ON public.comment_like;
CREATE TRIGGER community_like_notification AFTER INSERT ON public.comment_like FOR EACH ROW EXECUTE FUNCTION public.notify_community_activity();
REVOKE ALL ON FUNCTION public.notify_community_activity() FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.notifications_api(p_action text,p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE uid uuid:=auth.uid();settings public.app_notification_settings%ROWTYPE;today date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date;result jsonb;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.';END IF;
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
 FROM public.app_notification n LEFT JOIN public."User" u ON u.id=n.actor_id LEFT JOIN public.recipe_post p ON p.recipe_post_id=n.post_id LEFT JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
 LEFT JOIN public."Ingredient_User" b ON b."IngredientUser_Id"=n.batch_id
 WHERE n.user_id=uid AND ((n.kind='comment' AND settings.comments) OR(n.kind='like' AND settings.likes) OR(n.category='Pantry' AND settings.pantry) OR(n.category='System' AND settings.sync))
 ORDER BY n.created_at DESC,n.id DESC LIMIT 200)x;
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.notifications_api(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.notifications_api(text,jsonb) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
