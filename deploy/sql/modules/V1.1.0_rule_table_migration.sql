-- ============================================================
-- PMIS V1.1.0 规则表归口迁移 — project → literule
-- 将 project 模块中 8 张 pmis_rule_* 表的归属权迁移到 literule 模块
-- ============================================================
-- 以下表 originally defined in V1.0.0_project.sql，现归口到 literule 模块管理：
--
-- 1. pmis_rule_execution_trace  — 规则执行链路追踪
-- 2. pmis_rule_decision_table   — 决策表
-- 3. pmis_rule_canary_bucket    — 灰度桶
-- 4. pmis_rule_scorecard        — 评分卡
-- 5. pmis_rule_decision_tree    — 决策树
-- 6. pmis_rule_script           — 脚本规则
-- 7. pmis_rule_ab_policy        — A/B 测试策略
-- 8. pmis_rule_ab_rollback      — A/B 回滚记录
--
-- 迁移策略：
--   - 表结构不变（避免数据迁移风险）
--   - 更新 COMMENT 标记归属模块为 literule
--   - 后续版本将 DDL 从 V1.0.0_project.sql 迁移到 V1.0.0_literule.sql
--   - Java 实体/Mapper/Service 从 project/literule/ 迁移到 literule 模块
-- ============================================================

-- 更新表注释，标记归属模块
COMMENT ON TABLE pmis_rule_execution_trace IS 'P1-11: 规则执行链路追踪表 [OWNER: literule 模块, V1.1.0 从 project 迁移]';
COMMENT ON TABLE pmis_rule_decision_table IS '决策表支持 [OWNER: literule 模块, V1.1.0 从 project 迁移]';
COMMENT ON TABLE pmis_rule_canary_bucket IS '灰度桶 [OWNER: literule 模块, V1.1.0 从 project 迁移]';
COMMENT ON TABLE pmis_rule_scorecard IS '评分卡 [OWNER: literule 模块, V1.1.0 从 project 迁移]';
COMMENT ON TABLE pmis_rule_decision_tree IS '决策树 [OWNER: literule 模块, V1.1.0 从 project 迁移]';
COMMENT ON TABLE pmis_rule_script IS '脚本规则 [OWNER: literule 模块, V1.1.0 从 project 迁移]';
COMMENT ON TABLE pmis_rule_ab_policy IS 'A/B 测试策略 [OWNER: literule 模块, V1.1.0 从 project 迁移]';
COMMENT ON TABLE pmis_rule_ab_rollback IS 'A/B 回滚记录 [OWNER: literule 模块, V1.1.0 从 project 迁移]';

-- 验证：确认所有 pmis_rule_* 表现在归 literule 管理
-- 期望结果：literule 模块管理全部 17 张 pmis_rule_* 表
SELECT 'rule_tables_count' AS check_name, COUNT(*) AS count
FROM information_schema.tables
WHERE table_name LIKE 'pmis_rule_%' AND table_schema = 'public';
