BEGIN;

ALTER TABLE public.app_notification
 ADD COLUMN IF NOT EXISTS cleared_at timestamptz,
 ADD COLUMN IF NOT EXISTS recipe_target bigint;
ALTER TABLE public.app_notification_settings
 ADD COLUMN IF NOT EXISTS favourites boolean NOT NULL DEFAULT true,
 ADD COLUMN IF NOT EXISTS ratings boolean NOT NULL DEFAULT true,
 ADD COLUMN IF NOT EXISTS timers boolean NOT NULL DEFAULT true;

CREATE OR REPLACE FUNCTION public.notifications_api(p_action text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE uid uuid:=auth.uid();settings public.app_notification_settings%ROWTYPE;today date:=(now() AT TIME ZONE 'Asia/Kuala_Lumpur')::date;result jsonb;
BEGIN
 IF uid IS NULL OR NOT EXISTS(SELECT 1 FROM public."User" WHERE user_id=uid AND is_active) THEN RAISE EXCEPTION 'Please sign in with an active account.';END IF;
 IF p_payload ? 'owner_id' AND p_payload->>'owner_id' IS DISTINCT FROM uid::text THEN
  RAISE EXCEPTION 'Account changed.';
 END IF;
 INSERT INTO public.app_notification_settings(user_id)VALUES(uid)ON CONFLICT DO NOTHING;
 SELECT * INTO settings FROM public.app_notification_settings WHERE user_id=uid;
 IF p_action='settings' THEN RETURN to_jsonb(settings)-'user_id'; END IF;
 IF p_action='save_settings' THEN
  UPDATE public.app_notification_settings SET comments=coalesce((p_payload->>'comments')::boolean,comments),likes=coalesce((p_payload->>'likes')::boolean,likes),
   favourites=coalesce((p_payload->>'favourites')::boolean,favourites), ratings=coalesce((p_payload->>'ratings')::boolean,ratings),
   timers=coalesce((p_payload->>'timers')::boolean,timers),
   pantry=coalesce((p_payload->>'pantry')::boolean,pantry),sync=coalesce((p_payload->>'sync')::boolean,sync),push=coalesce((p_payload->>'push')::boolean,push),
   reminder_days=coalesce((p_payload->>'reminder_days')::integer,reminder_days),quiet=coalesce((p_payload->>'quiet')::boolean,quiet),
   quiet_start=coalesce((p_payload->>'quiet_start')::integer,quiet_start),quiet_end=coalesce((p_payload->>'quiet_end')::integer,quiet_end) WHERE user_id=uid RETURNING * INTO settings;
  RETURN to_jsonb(settings)-'user_id';
 ELSIF p_action IN ('read', 'clear') THEN
  UPDATE public.app_notification
  SET read_at=coalesce(read_at,now()),
      cleared_at=CASE WHEN p_action='clear' THEN coalesce(cleared_at,now()) ELSE cleared_at END
  WHERE user_id=uid AND
   (event_key=p_payload->>'event_key' OR id=(p_payload->>'id')::bigint);
 ELSIF p_action='apply_decisions' THEN
  UPDATE public.app_notification AS n
   SET read_at=coalesce(n.read_at,now()),
       cleared_at=CASE WHEN d.value='clear' THEN coalesce(n.cleared_at,now()) ELSE n.cleared_at END
   FROM jsonb_each_text(p_payload->'decisions') AS d
   WHERE n.user_id=uid AND n.event_key=d.key AND d.value IN ('read','clear');
 ELSIF p_action='read_all' THEN
  UPDATE public.app_notification SET read_at=coalesce(read_at,now()) WHERE user_id=uid AND cleared_at IS NULL;
 ELSIF p_action='timer_complete' THEN
  IF coalesce(p_payload->>'event_key','') !~ '^timer:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
   RAISE EXCEPTION 'Invalid timer event.';
  END IF;
  INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,recipe_target,created_at)
  VALUES(uid,'System','timer',left(coalesce(p_payload->>'title','Cooking timer finished'),160),
   left(coalesce(p_payload->>'body',''),160),p_payload->>'event_key',
   (p_payload->>'recipe_target')::bigint,
   least(now(),coalesce((p_payload->>'created_at')::timestamptz,now())))
  ON CONFLICT (user_id,event_key) DO NOTHING;
 ELSIF p_action NOT IN ('list','refresh','sync_issue') THEN
  RAISE EXCEPTION 'Unknown notification action. Reopen notifications.';
 END IF;
 INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,batch_id)
 SELECT uid,'Pantry','expiry',coalesce(i."ingredientName",'Ingredient')||CASE WHEN d.expiry<today THEN ' has expired' WHEN d.expiry=today THEN ' expires today' WHEN d.expiry=today+1 THEN ' expires tomorrow' ELSE ' expires in '||(d.expiry-today)||' days' END,
 b.quantity||' '||coalesce(b.quantity_unit,'')||' · '||coalesce(nullif(b.location_storage,''),'Pantry'),
 k.event_key,b."IngredientUser_Id"
 FROM public."Ingredient_User" b LEFT JOIN public.ingredient i ON i."ingredientId"=b."ingredientId"
 CROSS JOIN LATERAL(SELECT (to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date expiry)d
 CROSS JOIN LATERAL(SELECT 'batch:'||b."IngredientUser_Id"||':'||b."expiredDate"||':' AS prefix) key_parts
 CROSS JOIN LATERAL(SELECT key_parts.prefix||CASE WHEN EXISTS (
   SELECT 1 FROM public.app_notification existing
   WHERE existing.user_id=uid AND existing.event_key=key_parts.prefix||'expired'
 ) THEN 'expired' ELSE 'soon' END AS event_key) k
 WHERE settings.pantry AND b.user_id=uid AND b.quantity>0 AND d.expiry<=today+settings.reminder_days
 ON CONFLICT(user_id,event_key)DO UPDATE SET title=excluded.title,body=excluded.body;
 SELECT jsonb_build_object('settings',to_jsonb(settings)-'user_id','items',coalesce(jsonb_agg(to_jsonb(x) ORDER BY x.created_at DESC,x.id DESC),'[]'::jsonb),
 'unread',coalesce(sum(CASE WHEN x.read_at IS NULL THEN 1 ELSE 0 END),0)) INTO result FROM (
 SELECT n.*,u.profile_image_url avatar,r.recipe_image image,b."ingredientId" ingredient_id,(to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date expiry
 FROM public.app_notification n LEFT JOIN public."User" u ON u.user_id=n.actor_id LEFT JOIN public.recipe_post p ON p.recipe_post_id=n.post_id LEFT JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
 LEFT JOIN public."Ingredient_User" b ON b."IngredientUser_Id"=n.batch_id
 WHERE n.user_id=uid AND n.cleared_at IS NULL AND (
  (n.kind='comment' AND settings.comments AND p.recipe_post_status='published') OR
  (n.kind='like' AND settings.likes AND p.recipe_post_status='published') OR
  (n.kind='favourite' AND settings.favourites AND p.recipe_post_status='published') OR
  (n.kind='rating' AND settings.ratings AND p.recipe_post_status='published') OR
  (n.kind='timer' AND settings.timers) OR
  (n.kind='expiry' AND settings.pantry AND b.user_id=uid AND b.quantity>0
   AND (to_timestamp(b."expiredDate"/1000.0) AT TIME ZONE 'Asia/Kuala_Lumpur')::date<=today+settings.reminder_days
   AND (n.event_key='batch:'||b."IngredientUser_Id"||':'||b."expiredDate"||':expired'
        OR (n.event_key='batch:'||b."IngredientUser_Id"||':'||b."expiredDate"||':soon'
            AND NOT EXISTS (SELECT 1 FROM public.app_notification existing WHERE existing.user_id=uid
              AND existing.event_key='batch:'||b."IngredientUser_Id"||':'||b."expiredDate"||':expired')))))
 ORDER BY n.created_at DESC,n.id DESC LIMIT 200)x;
 RETURN result;
END $function$;

CREATE OR REPLACE FUNCTION public.notify_community_activity()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE recipient uuid;pid bigint;cid bigint;actor_name text;dish text;kind_name text;body_text text;
BEGIN
 IF TG_TABLE_NAME='recipe_comment' THEN
  pid:=NEW.post_id;cid:=NEW.recipe_comment_id;kind_name:='comment';body_text:=left(public.community_clean_text(NEW.recipe_comment_text),90);
  SELECT p.user_id,r."recipeName" INTO recipient,dish FROM public.recipe_post p JOIN public."Recipe" r ON r."recipeId"=p.recipe_id WHERE p.recipe_post_id=pid AND p.recipe_post_status='published';
 ELSIF NEW.comment_id IS NOT NULL THEN
  IF NOT NEW.is_liked THEN RETURN NEW; END IF;
  IF TG_OP='UPDATE' THEN
   IF OLD.is_liked THEN RETURN NEW; END IF;
  END IF;
  cid:=NEW.comment_id;kind_name:='like';
  SELECT c.user_id,c.post_id,left(public.community_clean_text(c.recipe_comment_text),90),r."recipeName" INTO recipient,pid,body_text,dish FROM public.recipe_comment c JOIN public.recipe_post p ON p.recipe_post_id=c.post_id JOIN public."Recipe" r ON r."recipeId"=p.recipe_id WHERE c.recipe_comment_id=cid AND p.recipe_post_status='published';
 ELSE
  pid:=NEW.post_id;
  SELECT p.user_id,r."recipeName" INTO recipient,dish FROM public.recipe_post p
   JOIN public."Recipe" r ON r."recipeId"=p.recipe_id
   WHERE p.recipe_post_id=pid AND p.recipe_post_status='published';
  IF recipient IS NULL OR recipient=NEW.user_id THEN RETURN NEW; END IF;
  SELECT display_name INTO actor_name FROM public."User" WHERE user_id=NEW.user_id;
  IF NEW.is_favourite AND (TG_OP='INSERT' OR OLD.is_favourite IS DISTINCT FROM true) THEN
   INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,post_id,actor_id)
   VALUES(recipient,'Interactions','favourite',coalesce(actor_name,'A member')||' saved your recipe',dish,
          'favourite:'||pid||':'||NEW.user_id,pid,NEW.user_id)
   ON CONFLICT(user_id,event_key) DO NOTHING;
  END IF;
  IF NEW.rating_value IS NOT NULL AND (TG_OP='INSERT' OR NEW.rating_value IS DISTINCT FROM OLD.rating_value) THEN
   INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,post_id,actor_id)
   VALUES(recipient,'Interactions','rating',coalesce(actor_name,'A member')||' rated your recipe',
          dish||' · '||NEW.rating_value||'/5','rating:'||pid||':'||NEW.user_id,pid,NEW.user_id)
   ON CONFLICT(user_id,event_key) DO UPDATE SET body=excluded.body,title=excluded.title;
  END IF;
  RETURN NEW;
 END IF;
 IF recipient IS NULL OR recipient=NEW.user_id THEN RETURN NEW; END IF;
 SELECT display_name INTO actor_name FROM public."User" WHERE user_id=NEW.user_id;
 INSERT INTO public.app_notification(user_id,category,kind,title,body,event_key,post_id,comment_id,actor_id)
 VALUES(recipient,'Interactions',kind_name,coalesce(actor_name,'A member')||CASE WHEN kind_name='comment' THEN ' commented on your ' ELSE ' liked your comment on ' END||dish,body_text,
 kind_name||':'||cid||':'||NEW.user_id,pid,cid,NEW.user_id) ON CONFLICT(user_id,event_key) DO NOTHING;
 RETURN NEW;
END $function$;

DROP TRIGGER IF EXISTS community_interaction_like_notification ON public.community_interaction;
DROP TRIGGER IF EXISTS community_interaction_notification ON public.community_interaction;
CREATE TRIGGER community_interaction_notification
 AFTER INSERT OR UPDATE OF is_liked, is_favourite, rating_value ON public.community_interaction
 FOR EACH ROW EXECUTE FUNCTION public.notify_community_activity();

REVOKE ALL ON FUNCTION public.notify_community_activity() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.notifications_api(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.notifications_api(text,jsonb) TO authenticated;

COMMIT;
