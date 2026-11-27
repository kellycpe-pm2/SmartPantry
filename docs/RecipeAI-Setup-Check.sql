-- READ ONLY. Only needed if AI reports incomplete backend setup.
SELECT to_regprocedure('public.community_api(text,jsonb)') IS NOT NULL AS catalogue_ready,
       to_regprocedure('public.claim_recipe_ai_budget(uuid)') IS NOT NULL AS budget_function_ready,
       to_regclass('public.community_request') IS NOT NULL AS request_cache_ready;
SELECT has_table_privilege('service_role','public.community_request','SELECT') AS cache_select,
       has_table_privilege('service_role','public.community_request','INSERT') AS cache_insert,
       has_table_privilege('service_role','public.community_request','UPDATE') AS cache_update,
       has_table_privilege('service_role','public.community_request','DELETE') AS cache_delete,
       has_function_privilege('service_role','public.claim_recipe_ai_budget(uuid)','EXECUTE') AS budget_execute;
