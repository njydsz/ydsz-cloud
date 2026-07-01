-- =====================================================
-- PMIS 批次11 DDL：资源池 + 人员标签 + 资源分配 + Bench 闲置
-- 版本: V1.0.0_014
-- 描述: 资源池(pmis_resource_pool)、人员标签(pmis_employee_tag)、
--       资源分配(pmis_resource_assignment)、Bench 闲置(pmis_bench_record)
-- =====================================================

-- =====================================================
-- 1. 资源池主表 pmis_resource_pool
-- =====================================================
DROP TABLE IF EXISTS pmis_resource_pool;
CREATE TABLE pmis_resource_pool (
    id                  BIGSERIAL PRIMARY KEY,
    pool_code           VARCHAR(64)  NOT NULL,
    pool_name           VARCHAR(256) NOT NULL,
    pool_type           VARCHAR(32)  NOT NULL,                 -- HQ/DIVISION/RESERVE
    department_id       BIGINT,                                -- 事业部/部门
    department_name     VARCHAR(256),
    level_range         VARCHAR(32),                           -- L1-L3 / L4-L12 / L13+
    headcount           INTEGER      NOT NULL DEFAULT 0,
    billable_target     INTEGER      NOT NULL DEFAULT 0,
    description         TEXT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_prp_code UNIQUE (pool_code, deleted)
);
COMMENT ON TABLE  pmis_resource_pool IS '资源池表: 3 级资源池（HQ 总部 / DIVISION 事业部 / RESERVE 储备）,PoolType.inferByLevel 按职级自动分配';
COMMENT ON COLUMN pmis_resource_pool.pool_code IS '资源池编码: 业务唯一,如 POOL-HQ-GLOBAL';
COMMENT ON COLUMN pmis_resource_pool.pool_name IS '资源池名称';
COMMENT ON COLUMN pmis_resource_pool.pool_type IS '资源池类型: HQ 总部 / DIVISION 事业部 / RESERVE 储备,按职级自动映射';
COMMENT ON COLUMN pmis_resource_pool.department_id IS '所属部门 ID: 池归属的事业部/部门';
COMMENT ON COLUMN pmis_resource_pool.department_name IS '所属部门名称（冗余）';
COMMENT ON COLUMN pmis_resource_pool.level_range IS '职级范围: L1-L3 / L4-L12 / L13+';
COMMENT ON COLUMN pmis_resource_pool.headcount IS '当前人数';
COMMENT ON COLUMN pmis_resource_pool.billable_target IS '计费人头目标: 期望投入计费项目的人数';
COMMENT ON COLUMN pmis_resource_pool.description IS '资源池描述';
COMMENT ON COLUMN pmis_resource_pool.status IS '状态: ACTIVE 启用 / INACTIVE 停用';
COMMENT ON COLUMN pmis_resource_pool.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_resource_pool.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_resource_pool.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_prp_type_status ON pmis_resource_pool(pool_type, status);
CREATE INDEX idx_prp_dept ON pmis_resource_pool(department_id);

-- =====================================================
-- 2. 人员标签表 pmis_employee_tag
-- =====================================================
DROP TABLE IF EXISTS pmis_employee_tag;
CREATE TABLE pmis_employee_tag (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT       NOT NULL,
    tag_type            VARCHAR(32)  NOT NULL,                 -- SKILL/INDUSTRY/DOMAIN/CERT
    tag_code            VARCHAR(64)  NOT NULL,
    tag_name            VARCHAR(256) NOT NULL,
    proficiency         INTEGER      NOT NULL DEFAULT 3,       -- 1-5
    years_exp           INTEGER      NOT NULL DEFAULT 0,
    remark              TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pet_emp_tag UNIQUE (employee_id, tag_code, deleted)
);
COMMENT ON TABLE  pmis_employee_tag IS '人员标签表: 员工的技能/行业/领域/资质标签,支撑资源推荐智能体匹配';
COMMENT ON COLUMN pmis_employee_tag.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_employee_tag.tag_type IS '标签类型: SKILL 技能 / INDUSTRY 行业 / DOMAIN 领域 / CERT 资质';
COMMENT ON COLUMN pmis_employee_tag.tag_code IS '标签编码: 业务唯一,如 JAVA / BANKING';
COMMENT ON COLUMN pmis_employee_tag.tag_name IS '标签名称';
COMMENT ON COLUMN pmis_employee_tag.proficiency IS '熟练度: 1-5 星';
COMMENT ON COLUMN pmis_employee_tag.years_exp IS '相关年限(年)';
COMMENT ON COLUMN pmis_employee_tag.remark IS '备注';
COMMENT ON COLUMN pmis_employee_tag.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_employee_tag.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_employee_tag.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pet_emp ON pmis_employee_tag(employee_id);
CREATE INDEX idx_pet_type ON pmis_employee_tag(tag_type, tag_code);

-- =====================================================
-- 3. 资源分配主表 pmis_resource_assignment
-- =====================================================
DROP TABLE IF EXISTS pmis_resource_assignment;
CREATE TABLE pmis_resource_assignment (
    id                    BIGSERIAL PRIMARY KEY,
    assignment_code       VARCHAR(64)  NOT NULL,
    employee_id           BIGINT       NOT NULL,
    employee_name         VARCHAR(64),
    level_code            VARCHAR(16),
    pool_id               BIGINT,
    pool_type             VARCHAR(32),                         -- 冗余池类型
    initiation_id         BIGINT,                              -- 关联项目
    initiation_name       VARCHAR(256),
    opportunity_id        BIGINT,                              -- 关联商机
    status                VARCHAR(32)  NOT NULL DEFAULT 'RESERVED',
    allocation            NUMERIC(5,4) NOT NULL DEFAULT 1.0,   -- 0-1
    planned_start_date    DATE,
    planned_end_date      DATE,
    actual_start_date     DATE,
    actual_end_date       DATE,
    billable              SMALLINT     NOT NULL DEFAULT 1,
    daily_hours           NUMERIC(5,2) NOT NULL DEFAULT 8.0,
    tenant_id             BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pra_code UNIQUE (assignment_code, deleted)
);
COMMENT ON TABLE  pmis_resource_assignment IS '资源分配表: 资源预占/入场/调岗/离场的全过程,act() 入口按 action 参数映射 AssignmentStatus';
COMMENT ON COLUMN pmis_resource_assignment.assignment_code IS '分配单号: 业务唯一,如 RA-2026-001';
COMMENT ON COLUMN pmis_resource_assignment.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_resource_assignment.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_resource_assignment.level_code IS '职级: L1-L18';
COMMENT ON COLUMN pmis_resource_assignment.pool_id IS '所属资源池 ID';
COMMENT ON COLUMN pmis_resource_assignment.pool_type IS '资源池类型: HQ/DIVISION/RESERVE（冗余,便于查询）';
COMMENT ON COLUMN pmis_resource_assignment.initiation_id IS '所属立项 ID: 分配到的项目';
COMMENT ON COLUMN pmis_resource_assignment.initiation_name IS '立项名称（冗余）';
COMMENT ON COLUMN pmis_resource_assignment.opportunity_id IS '所属商机 ID: 商机阶段预占时填写';
COMMENT ON COLUMN pmis_resource_assignment.status IS '分配状态: RESERVED 已预占 / IN_PROGRESS 进行中 / TRANSFERRED 已调岗 / RELEASED 已释放 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_resource_assignment.allocation IS '占用比例: 0-1,例如 0.5=50% 投入';
COMMENT ON COLUMN pmis_resource_assignment.planned_start_date IS '计划开始日期';
COMMENT ON COLUMN pmis_resource_assignment.planned_end_date IS '计划结束日期';
COMMENT ON COLUMN pmis_resource_assignment.actual_start_date IS '实际开始日期';
COMMENT ON COLUMN pmis_resource_assignment.actual_end_date IS '实际结束日期';
COMMENT ON COLUMN pmis_resource_assignment.billable IS '是否计费: 0=非计费,1=计费';
COMMENT ON COLUMN pmis_resource_assignment.daily_hours IS '日均工时(小时): 默认 8';
COMMENT ON COLUMN pmis_resource_assignment.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_resource_assignment.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_resource_assignment.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pra_emp ON pmis_resource_assignment(employee_id);
CREATE INDEX idx_pra_initiation ON pmis_resource_assignment(initiation_id);
CREATE INDEX idx_pra_status ON pmis_resource_assignment(status);
CREATE INDEX idx_pra_pool ON pmis_resource_assignment(pool_id, status);

-- =====================================================
-- 4. Bench 闲置记录表 pmis_bench_record
-- =====================================================
DROP TABLE IF EXISTS pmis_bench_record;
CREATE TABLE pmis_bench_record (
    id                    BIGSERIAL PRIMARY KEY,
    bench_code            VARCHAR(64)  NOT NULL,
    employee_id           BIGINT       NOT NULL,
    employee_name         VARCHAR(64),
    level_code            VARCHAR(16),
    pool_id               BIGINT,
    bench_reason          VARCHAR(32)  NOT NULL DEFAULT 'ENTER', -- ENTER/EXIT
    reason_type            VARCHAR(32),                          -- PROJECT_END/RESERVE/TRAINING/LEAVE
    source_assignment     BIGINT,                               -- 触发本次 Bench 的分配记录
    bench_date            DATE         NOT NULL,
    exit_date             DATE,
    idle_days             INTEGER      NOT NULL DEFAULT 0,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- BenchStatus
    daily_cost            NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_idle_cost       NUMERIC(15,2) NOT NULL DEFAULT 0,
    remark                TEXT,
    tenant_id             BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pbr_code UNIQUE (bench_code, deleted)
);
COMMENT ON TABLE  pmis_bench_record IS 'Bench 闲置记录表: 资源闲置期间自动入池/出池,BenchCostCalculator 计算 idleDays + totalIdleCost';
COMMENT ON COLUMN pmis_bench_record.bench_code IS 'Bench 单号: 业务唯一,如 BENCH-2026-001';
COMMENT ON COLUMN pmis_bench_record.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_bench_record.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_bench_record.level_code IS '职级: L1-L18';
COMMENT ON COLUMN pmis_bench_record.pool_id IS '所属资源池 ID';
COMMENT ON COLUMN pmis_bench_record.bench_reason IS 'Bench 类型: ENTER 入池 / EXIT 出池';
COMMENT ON COLUMN pmis_bench_record.reason_type IS '原因类型: PROJECT_END 项目结束 / RESERVE 储备 / TRAINING 培训 / LEAVE 请假';
COMMENT ON COLUMN pmis_bench_record.source_assignment IS '触发本次 Bench 的分配记录 ID: 引用 pmis_resource_assignment.id(Long)';
COMMENT ON COLUMN pmis_bench_record.bench_date IS '入池日期';
COMMENT ON COLUMN pmis_bench_record.exit_date IS '出池日期: NULL 表示仍在 Bench';
COMMENT ON COLUMN pmis_bench_record.idle_days IS '闲置天数: ChronoUnit.DAYS.between(benchDate, exitDate or now)';
COMMENT ON COLUMN pmis_bench_record.status IS '状态: ACTIVE 闲置中 / CLOSED 已关闭';
COMMENT ON COLUMN pmis_bench_record.daily_cost IS '日均闲置成本(元): 按职级内部费率计算';
COMMENT ON COLUMN pmis_bench_record.total_idle_cost IS '累计闲置成本(元) = daily_cost * idle_days';
COMMENT ON COLUMN pmis_bench_record.remark IS '备注';
COMMENT ON COLUMN pmis_bench_record.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_bench_record.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_bench_record.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pbr_emp ON pmis_bench_record(employee_id);
CREATE INDEX idx_pbr_status ON pmis_bench_record(status, bench_date);
CREATE INDEX idx_pbr_pool ON pmis_bench_record(pool_id, status);
CREATE INDEX idx_pbr_date ON pmis_bench_record(bench_date, exit_date);

-- =====================================================
-- 5. 初始化三级资源池（HQ/DIVISION/RESERVE）
-- =====================================================
INSERT INTO pmis_resource_pool
    (pool_code, pool_name, pool_type, department_id, department_name, level_range, headcount, billable_target, status, tenant_id, provider_trace_id)
VALUES
    ('POOL-HQ-GLOBAL',        '总部高级资源池',   'HQ',       1, '总部',  'L13+', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-CONSULTING',   '咨询事业部池',    'DIVISION', 2, '咨询事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-IMPL',         '实施事业部池',    'DIVISION', 3, '实施事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-RESERVE-TRAINING', '储备培训池',      'RESERVE',  1, '总部',  'L1-L3', 0, 0, 'ACTIVE', 1, 'init');
