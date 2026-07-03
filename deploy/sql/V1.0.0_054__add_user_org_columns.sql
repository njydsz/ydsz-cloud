-- ============================================================
-- V1.0.0_054  P2-2 候选人/变量独立表 — 用户表组织架构字段补全
-- ============================================================
-- 说明：为 pmis_user_account 表补充 dept_id / leader_id / position_code 字段，
--   使工作流引擎的 FlowAssigneeResolver 能够展开：
--     - dept:xxx → 部门下所有成员（按 dept_id 查询）
--     - leader:xxx → 直属上级（按 leader_id 查询）
--     - multi_leader:N → 连续 N 级主管（循环查 leader_id）
--     - position:xxx → 岗位人员（按 position_code 查询）
--
-- 兼容性：所有新增字段允许 NULL，不影响存量数据。
-- ============================================================

-- 1. 用户表加 dept_id（部门 ID）
ALTER TABLE pmis_user_account ADD COLUMN IF NOT EXISTS dept_id BIGINT;
COMMENT ON COLUMN pmis_user_account.dept_id IS '所属部门 ID（关联 pmis_department.id）';

-- 2. 用户表加 leader_id（直属上级用户 ID）
ALTER TABLE pmis_user_account ADD COLUMN IF NOT EXISTS leader_id BIGINT;
COMMENT ON COLUMN pmis_user_account.leader_id IS '直属上级用户 ID（关联 pmis_user_account.id，用于审批人 leader: 展开）';

-- 3. 用户表加 position_code（岗位编码）
ALTER TABLE pmis_user_account ADD COLUMN IF NOT EXISTS position_code VARCHAR(64);
COMMENT ON COLUMN pmis_user_account.position_code IS '岗位编码（如 PM/DEV/QA/SA，用于审批人 position: 展开）';

-- 索引：按部门查询用户（dept: 展开主查询）
CREATE INDEX IF NOT EXISTS idx_pua_dept_id
    ON pmis_user_account(dept_id)
    WHERE deleted = 0 AND dept_id IS NOT NULL;

-- 索引：按岗位查询用户（position: 展开主查询）
CREATE INDEX IF NOT EXISTS idx_pua_position_code
    ON pmis_user_account(position_code)
    WHERE deleted = 0 AND position_code IS NOT NULL;

-- 索引：按上级查询用户（反向查询"谁是某人的下属"，用于多级会签场景）
CREATE INDEX IF NOT EXISTS idx_pua_leader_id
    ON pmis_user_account(leader_id)
    WHERE deleted = 0 AND leader_id IS NOT NULL;

-- ============================================================
-- 说明：本脚本仅补全用户表字段，候选人/变量独立表的完整方案还包括：
--   1. pmis_flow_candidate 表（候选人快照，避免运行时多次查 DB）
--   2. pmis_flow_variable 表（流程变量独立存储，支持复杂类型与索引查询）
-- 当前阶段仅落地字段补全，使 leader:/dept:/position: 能展开；
-- 候选人快照表与变量独立表留待后续迭代。
-- ============================================================
