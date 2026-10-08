-- =============================================================================
-- V26.10.10: 为核心业务表添加 FK 外键约束
-- 策略：仅选择写入频率低、关联度高的表，使用 NOT VALID + VALIDATE CONSTRAINT
--        分两步避免长时间锁表。高写入表（msg_log, flow_audit_log）不做处理。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 第 0 步：孤立数据检查（执行 FK 前先确认数据一致性）
-- 以下查询返回 0 行才能安全添加对应 FK。
-- -----------------------------------------------------------------------------

-- 检查1: flow_instance 中 definition_id 在 flow_definition 中不存在
-- SELECT COUNT(*) AS orphan_count FROM "ydsz_flow_instance" fi
--     WHERE fi."definition_id" IS NOT NULL
--       AND NOT EXISTS (SELECT 1 FROM "ydsz_flow_definition" fd WHERE fd."id" = fi."definition_id");

-- 检查2: flow_run_task 中 instance_id 在 flow_instance 中不存在
-- SELECT COUNT(*) AS orphan_count FROM "ydsz_flow_run_task" frt
--     WHERE NOT EXISTS (SELECT 1 FROM "ydsz_flow_instance" fi WHERE fi."id" = frt."instance_id");

-- 检查3: flow_node 中 definition_id 在 flow_definition 中不存在
-- SELECT COUNT(*) AS orphan_count FROM "ydsz_flow_node" fn
--     WHERE NOT EXISTS (SELECT 1 FROM "ydsz_flow_definition" fd WHERE fd."id" = fn."definition_id");

-- 检查4: flow_his_instance 中 definition_id 在 flow_definition 中不存在
-- SELECT COUNT(*) AS orphan_count FROM "ydsz_flow_his_instance" fhi
--     WHERE NOT EXISTS (SELECT 1 FROM "ydsz_flow_definition" fd WHERE fd."id" = fhi."definition_id");

-- 检查5: flow_his_task 中 instance_id 在 flow_his_instance 中不存在
-- SELECT COUNT(*) AS orphan_count FROM "ydsz_flow_his_task" fht
--     WHERE NOT EXISTS (SELECT 1 FROM "ydsz_flow_his_instance" fhi WHERE fhi."id" = fht."instance_id");

-- 检查6: job_history 中 job_id 在 job_main 中不存在
-- SELECT COUNT(*) AS orphan_count FROM "ydsz_job_history" jh
--     WHERE NOT EXISTS (SELECT 1 FROM "ydsz_job_main" jm WHERE jm."id" = jh."job_id");


-- -----------------------------------------------------------------------------
-- 第 1 步：添加 FK 约束（NOT VALID 模式，不立即验证已有数据，不阻塞写入）
-- -----------------------------------------------------------------------------

-- 1. ydzsz_flow_instance.definition_id -> ydsz_flow_definition(id)
ALTER TABLE "ydsz_flow_instance"
    ADD CONSTRAINT "fk_flow_instance_definition_id"
    FOREIGN KEY ("definition_id") REFERENCES "ydsz_flow_definition" ("id")
    NOT VALID;

-- 2. ydzsz_flow_run_task.instance_id -> ydsz_flow_instance(id)
ALTER TABLE "ydsz_flow_run_task"
    ADD CONSTRAINT "fk_flow_run_task_instance_id"
    FOREIGN KEY ("instance_id") REFERENCES "ydsz_flow_instance" ("id")
    NOT VALID;

-- 3. ydzsz_flow_node.definition_id -> ydsz_flow_definition(id)
ALTER TABLE "ydsz_flow_node"
    ADD CONSTRAINT "fk_flow_node_definition_id"
    FOREIGN KEY ("definition_id") REFERENCES "ydsz_flow_definition" ("id")
    NOT VALID;

-- 4. ydzsz_flow_his_instance.definition_id -> ydsz_flow_definition(id)
ALTER TABLE "ydsz_flow_his_instance"
    ADD CONSTRAINT "fk_flow_his_instance_definition_id"
    FOREIGN KEY ("definition_id") REFERENCES "ydsz_flow_definition" ("id")
    NOT VALID;

-- 5. ydzsz_flow_his_task.instance_id -> ydsz_flow_his_instance(id)
ALTER TABLE "ydsz_flow_his_task"
    ADD CONSTRAINT "fk_flow_his_task_instance_id"
    FOREIGN KEY ("instance_id") REFERENCES "ydsz_flow_his_instance" ("id")
    NOT VALID;

-- 6. ydsz_job_history.job_id -> ydsz_job_main(id)
ALTER TABLE "ydsz_job_history"
    ADD CONSTRAINT "fk_job_history_job_id"
    FOREIGN KEY ("job_id") REFERENCES "ydsz_job_main" ("id")
    NOT VALID;


-- -----------------------------------------------------------------------------
-- 第 2 步：验证已有数据满足 FK 约束（VALIDATE CONSTRAINT，仅需 SHARE UPDATE EXCLUSIVE 锁）
-- -----------------------------------------------------------------------------

ALTER TABLE "ydsz_flow_instance" VALIDATE CONSTRAINT "fk_flow_instance_definition_id";
ALTER TABLE "ydsz_flow_run_task" VALIDATE CONSTRAINT "fk_flow_run_task_instance_id";
ALTER TABLE "ydsz_flow_node" VALIDATE CONSTRAINT "fk_flow_node_definition_id";
ALTER TABLE "ydsz_flow_his_instance" VALIDATE CONSTRAINT "fk_flow_his_instance_definition_id";
ALTER TABLE "ydsz_flow_his_task" VALIDATE CONSTRAINT "fk_flow_his_task_instance_id";
ALTER TABLE "ydsz_job_history" VALIDATE CONSTRAINT "fk_job_history_job_id";


-- -----------------------------------------------------------------------------
-- 说明：
--   - NOT VALID 创建约束时不对已有行做全表扫描，瞬间完成，不阻塞 DML。
--   - VALIDATE CONSTRAINT 仅获取 SHARE UPDATE EXCLUSIVE 锁（与 DML 兼容），
--     仅阻塞 DDL 和 VACUUM FULL。
--   - 新建约束后将自动拒绝未来插入/更新产生的孤立数据。
--   - 高写入表（flow_audit_log 按月分区追加、msg_log 高频写入）未添加 FK。
--   - msg_log.template_code 无法建立 FK：msg_template 的 PK 是 id 而非 template_code
--     （唯一键为 (template_code, tenant_id) 组合），故跳过。
-- =============================================================================
