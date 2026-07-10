-- ====================================================================
-- 鍗椾含浜戦《 PMIS 鏁版嵁搴撳垵濮嬪寲 - 妯″潡寮曠敤鑴氭湰
-- Version: V1.0.0
-- Target: PostgreSQL 18
-- Description: 鏈剼鏈寜妯″潡椤哄簭寮曠敤鍚勫瓙妯″潡 SQL 鏂囦欢
--   绛変环浜庣洿鎺ユ墽琛?deploy/sql/V1.0.0.sql
-- Usage:
--   psql "host=... user=... dbname=... password=..." -v ON_ERROR_STOP=1 -f V1.0.0_all.sql
-- ====================================================================

SET client_min_messages = WARNING;
SET search_path = public, pg_catalog;
BEGIN;

-- ==== System.Collections.Hashtable[common] ====
\i V1.0.0_common.sql

-- ==== System.Collections.Hashtable[system] ====
\i V1.0.0_system.sql

-- ==== System.Collections.Hashtable[userinfo] ====
\i V1.0.0_userinfo.sql

-- ==== System.Collections.Hashtable[project] ====
\i V1.0.0_project.sql

-- ==== System.Collections.Hashtable[cronjob] ====
\i V1.0.0_cronjob.sql

-- ==== System.Collections.Hashtable[workflow] ====
\i V1.0.0_workflow.sql

-- ==== System.Collections.Hashtable[literule] ====
\i V1.0.0_literule.sql

-- ====================================================================
-- All DDL has been applied. Commit the transaction.
-- ====================================================================
COMMIT;

