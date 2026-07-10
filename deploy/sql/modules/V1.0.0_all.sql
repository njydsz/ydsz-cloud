-- ====================================================================
-- PMIS DB Init - Module Reference Script
-- Version: V1.0.0 | Target: PostgreSQL 18
-- Usage: psql -v ON_ERROR_STOP=1 -f modules/V1.0.0_all.sql
-- ====================================================================

SET client_min_messages = WARNING;
SET search_path = public, pg_catalog;
BEGIN;

-- ==== Common Base (Dict/Ext/Tx/Trigger) ====
\i V1.0.0_common.sql

-- ==== System Mgmt (Config/File/Audit/Export) ====
\i V1.0.0_system.sql

-- ==== User Info (Auth/User/Org/Perm/Resource/HR) ====
\i V1.0.0_userinfo.sql

-- ==== Project Mgmt (Opp/Init/Contract/Exec/Fin/Close/AfterSales) ====
\i V1.0.0_project.sql

-- ==== Cron Job (Job/DAG/Schedule/Alert/Log/Quota) ====
\i V1.0.0_cronjob.sql

-- ==== Message Center (Notif/Template/Receipt/Batch/Canary) ====
\i V1.0.0_message.sql

-- ==== Workflow Engine (Def/Instance/Delegate/Notify/DMN/Integ) ====
\i V1.0.0_workflow.sql

-- ==== AI Agent (Agent/Orch/Knowledge/Tool/HitL) ====
\i V1.0.0_agent.sql

-- ==== Rule Engine (Rule/Decision/Scorecard/ABTest/Var) ====
\i V1.0.0_literule.sql

COMMIT;

