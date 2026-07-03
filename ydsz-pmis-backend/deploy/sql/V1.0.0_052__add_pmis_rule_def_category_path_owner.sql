-- 规则目录树 + 责任人字段（P1-9）
-- category_path：分类路径，用 / 分隔的多级分类，如 "finance/credit/loan"
-- owner：责任人（工号/用户名），用于告警通知、AB Test 回滚通知、巡检派单
-- 兼容老数据：category 字段保留，新增列均可为空；path 在老数据中默认与 category 相同。

ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS category_path VARCHAR(512);

ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS owner VARCHAR(64);

-- 已有数据回填：category_path 默认为 category（如果存在）
UPDATE pmis_rule_def
   SET category_path = category
 WHERE category_path IS NULL
   AND category IS NOT NULL;

-- 一级分类（path 的第一段）索引，便于左侧树按一级分类快速查询
CREATE INDEX IF NOT EXISTS idx_rule_def_category_path
    ON pmis_rule_def (category_path);

-- owner 索引，便于按责任人筛选
CREATE INDEX IF NOT EXISTS idx_rule_def_owner
    ON pmis_rule_def (owner);

COMMENT ON COLUMN pmis_rule_def.category_path IS '分类路径（多级，用 / 分隔），P1-9 规则目录树';
COMMENT ON COLUMN pmis_rule_def.owner         IS '责任人（工号/用户名），P1-9 规则目录树';
