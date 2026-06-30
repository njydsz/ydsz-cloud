-- =====================================================
-- PMIS 批次11 DDL：资源池 + 人员标签 + 资源分配 + Bench 闲置
-- 版本: V1.0.0_014
-- 描述: 资源池(pmis_resource_pool)、人员标签(pmis_employee_tag)、
--       资源分配(pmis_resource_assignment)、Bench 闲置(pmis_bench_record)
-- Schema: pmis
-- =====================================================

-- =====================================================
-- 1. 资源池主表 pmis_resource_pool
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_resource_pool;
CREATE TABLE pmis.pmis_resource_pool (
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
COMMENT ON TABLE pmis.pmis_resource_pool IS '资源池（总部/事业部/备用三级）';
CREATE INDEX idx_prp_type_status ON pmis.pmis_resource_pool(pool_type, status);
CREATE INDEX idx_prp_dept ON pmis.pmis_resource_pool(department_id);

-- =====================================================
-- 2. 人员标签表 pmis_employee_tag
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_employee_tag;
CREATE TABLE pmis.pmis_employee_tag (
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
COMMENT ON TABLE pmis.pmis_employee_tag IS '人员标签（技能/行业/领域/资质）';
CREATE INDEX idx_pet_emp ON pmis.pmis_employee_tag(employee_id);
CREATE INDEX idx_pet_type ON pmis.pmis_employee_tag(tag_type, tag_code);

-- =====================================================
-- 3. 资源分配主表 pmis_resource_assignment
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_resource_assignment;
CREATE TABLE pmis.pmis_resource_assignment (
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
COMMENT ON TABLE pmis.pmis_resource_assignment IS '资源分配记录（预占/入场/调岗/离场）';
CREATE INDEX idx_pra_emp ON pmis.pmis_resource_assignment(employee_id);
CREATE INDEX idx_pra_initiation ON pmis.pmis_resource_assignment(initiation_id);
CREATE INDEX idx_pra_status ON pmis.pmis_resource_assignment(status);
CREATE INDEX idx_pra_pool ON pmis.pmis_resource_assignment(pool_id, status);

-- =====================================================
-- 4. Bench 闲置记录表 pmis_bench_record
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_bench_record;
CREATE TABLE pmis.pmis_bench_record (
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
COMMENT ON TABLE pmis.pmis_bench_record IS 'Bench 闲置记录（自动入池/出池/成本量化）';
CREATE INDEX idx_pbr_emp ON pmis.pmis_bench_record(employee_id);
CREATE INDEX idx_pbr_status ON pmis.pmis_bench_record(status, bench_date);
CREATE INDEX idx_pbr_pool ON pmis.pmis_bench_record(pool_id, status);
CREATE INDEX idx_pbr_date ON pmis.pmis_bench_record(bench_date, exit_date);

-- =====================================================
-- 5. 初始化三级资源池（HQ/DIVISION/RESERVE）
-- =====================================================
INSERT INTO pmis.pmis_resource_pool
    (pool_code, pool_name, pool_type, department_id, department_name, level_range, headcount, billable_target, status, tenant_id, provider_trace_id)
VALUES
    ('POOL-HQ-GLOBAL',        '总部高级资源池',   'HQ',       1, '总部',  'L13+', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-CONSULTING',   '咨询事业部池',    'DIVISION', 2, '咨询事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-IMPL',         '实施事业部池',    'DIVISION', 3, '实施事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-RESERVE-TRAINING', '储备培训池',      'RESERVE',  1, '总部',  'L1-L3', 0, 0, 'ACTIVE', 1, 'init');
