#!/usr/bin/env python3
"""End-to-end generator for docs/V1.0.0.sql.

Steps:
1. Read all V*.sql files from deploy/sql/ in order.
2. Append the SUPPLEMENT block for code-discovered tables.
3. Re-apply column-level Chinese comments to supplement tables.
4. Validate the final file:
   - no COMMENT ON VIEW / TABLE / COLUMN appears before its CREATE
   - all Chinese, no garbled characters
5. Write to docs/V1.0.0.sql with UTF-8 encoding.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(r'd:\Code\ydsz\ydsz-pmis')
SRC_DIR = ROOT / 'deploy' / 'sql'
OUT_FILE = ROOT / 'docs' / 'V1.0.0.sql'
SOURCE_015 = SRC_DIR / 'V1.0.0_015__init_pmis_cockpit_views.sql'

# ---- supplement block (code-discovered tables) ----
SUPPLEMENT = r'''-- ----------------------------------------------------------------
-- pmis_flow_template -- P3-1: process template marketplace
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_template (
    id              BIGSERIAL       PRIMARY KEY,
    template_code   VARCHAR(128)    NOT NULL,
    template_name   VARCHAR(256)    NOT NULL,
    category        VARCHAR(64),
    description     VARCHAR(512),
    icon            VARCHAR(256),
    bpmn_xml        TEXT,
    form_path       VARCHAR(256),
    use_count       INTEGER         NOT NULL DEFAULT 0,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_flow_template_code
    ON pmis_flow_template (template_code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_flow_template_category
    ON pmis_flow_template (category, sort_order) WHERE deleted = 0;

COMMENT ON TABLE  pmis_flow_template IS 'P3-1: 流程模板市场表, 预置常用流程模板供一键导入';
COMMENT ON COLUMN pmis_flow_template.id IS '主键 ID';
COMMENT ON COLUMN pmis_flow_template.template_code IS '模板编码 (唯一)';
COMMENT ON COLUMN pmis_flow_template.template_name IS '模板名称';
COMMENT ON COLUMN pmis_flow_template.category IS '分类 (HR/FINANCE/ADMIN/PROJECT/GENERAL)';
COMMENT ON COLUMN pmis_flow_template.description IS '模板描述';
COMMENT ON COLUMN pmis_flow_template.icon IS '图标 URL';
COMMENT ON COLUMN pmis_flow_template.bpmn_xml IS 'BPMN 2.0 XML 流程定义';
COMMENT ON COLUMN pmis_flow_template.form_path IS '关联表单路径';
COMMENT ON COLUMN pmis_flow_template.use_count IS '使用次数';
COMMENT ON COLUMN pmis_flow_template.sort_order IS '排序值, 升序';
COMMENT ON COLUMN pmis_flow_template.created_at IS '创建时间';
COMMENT ON COLUMN pmis_flow_template.updated_at IS '更新时间';
COMMENT ON COLUMN pmis_flow_template.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_flow_auto_trigger -- P3-2: process auto-trigger
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_auto_trigger (
    id                   BIGSERIAL       PRIMARY KEY,
    source_flow_code     VARCHAR(64)     NOT NULL,
    target_flow_code     VARCHAR(64)     NOT NULL,
    condition_expression VARCHAR(1024),
    description          VARCHAR(512),
    enabled              INTEGER         NOT NULL DEFAULT 1,
    sort_order           INTEGER         NOT NULL DEFAULT 0,
    created_by           BIGINT,
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_flow_auto_trigger_src
    ON pmis_flow_auto_trigger (source_flow_code, enabled) WHERE deleted = 0;

COMMENT ON TABLE  pmis_flow_auto_trigger IS 'P3-2: 流程完成时自动触发下游流程的规则表';
COMMENT ON COLUMN pmis_flow_auto_trigger.id IS '主键 ID';
COMMENT ON COLUMN pmis_flow_auto_trigger.source_flow_code IS '源流程编码 (触发方)';
COMMENT ON COLUMN pmis_flow_auto_trigger.target_flow_code IS '目标流程编码 (被触发方)';
COMMENT ON COLUMN pmis_flow_auto_trigger.condition_expression IS 'Aviator 条件表达式;为空则无条件触发';
COMMENT ON COLUMN pmis_flow_auto_trigger.description IS '触发规则说明';
COMMENT ON COLUMN pmis_flow_auto_trigger.enabled IS '是否启用 1=启用 0=禁用';
COMMENT ON COLUMN pmis_flow_auto_trigger.sort_order IS '触发顺序';
COMMENT ON COLUMN pmis_flow_auto_trigger.created_by IS '创建人';
COMMENT ON COLUMN pmis_flow_auto_trigger.created_at IS '创建时间';
COMMENT ON COLUMN pmis_flow_auto_trigger.updated_by IS '更新人';
COMMENT ON COLUMN pmis_flow_auto_trigger.updated_at IS '更新时间';
COMMENT ON COLUMN pmis_flow_auto_trigger.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_flow_notify_channel -- P3-3: notification channel config
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_notify_channel (
    id            BIGSERIAL       PRIMARY KEY,
    tenant_id     BIGINT          NOT NULL DEFAULT 1,
    channel_type  VARCHAR(32)     NOT NULL,
    channel_name  VARCHAR(128)    NOT NULL,
    config        TEXT,
    enabled       SMALLINT        NOT NULL DEFAULT 1,
    created_by    BIGINT,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_flow_notify_channel_type
    ON pmis_flow_notify_channel (channel_type, enabled) WHERE deleted = 0;

COMMENT ON TABLE  pmis_flow_notify_channel IS 'P3-3: 工作流通知通道配置表 (IN_APP/EMAIL/SMS/WEBHOOK/DINGTALK/WECHAT)';
COMMENT ON COLUMN pmis_flow_notify_channel.id IS '主键 ID';
COMMENT ON COLUMN pmis_flow_notify_channel.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_flow_notify_channel.channel_type IS '通道类型 (IN_APP/EMAIL/SMS/WEBHOOK/DINGTALK/WECHAT)';
COMMENT ON COLUMN pmis_flow_notify_channel.channel_name IS '通道名称';
COMMENT ON COLUMN pmis_flow_notify_channel.config IS '配置 JSON (Webhook URL, 短信模板编码等)';
COMMENT ON COLUMN pmis_flow_notify_channel.enabled IS '是否启用 1=启用 0=禁用';
COMMENT ON COLUMN pmis_flow_notify_channel.created_by IS '创建人';
COMMENT ON COLUMN pmis_flow_notify_channel.created_at IS '创建时间';
COMMENT ON COLUMN pmis_flow_notify_channel.updated_by IS '更新人';
COMMENT ON COLUMN pmis_flow_notify_channel.updated_at IS '更新时间';
COMMENT ON COLUMN pmis_flow_notify_channel.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_flow_task_comment -- P1-3: task comment thread
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_task_comment (
    id           BIGSERIAL       PRIMARY KEY,
    tenant_id    BIGINT          NOT NULL DEFAULT 1,
    instance_id  BIGINT          NOT NULL,
    task_id      BIGINT          NOT NULL,
    node_code    VARCHAR(64),
    user_id      BIGINT          NOT NULL,
    user_name    VARCHAR(128),
    content      TEXT,
    type         VARCHAR(16)     NOT NULL DEFAULT 'COMMENT',
    parent_id    BIGINT,
    created_by   BIGINT,
    created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT,
    updated_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_flow_task_comment_task
    ON pmis_flow_task_comment (task_id, created_at) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_flow_task_comment_parent
    ON pmis_flow_task_comment (parent_id) WHERE deleted = 0;

COMMENT ON TABLE  pmis_flow_task_comment IS 'P1-3: 工作流任务评论表 (楼中楼, 通过 parent_id 形成嵌套回复)';
COMMENT ON COLUMN pmis_flow_task_comment.id IS '主键 ID';
COMMENT ON COLUMN pmis_flow_task_comment.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_flow_task_comment.instance_id IS '流程实例 ID';
COMMENT ON COLUMN pmis_flow_task_comment.task_id IS '任务 ID';
COMMENT ON COLUMN pmis_flow_task_comment.node_code IS '节点编码';
COMMENT ON COLUMN pmis_flow_task_comment.user_id IS '评论人 ID';
COMMENT ON COLUMN pmis_flow_task_comment.user_name IS '评论人姓名 (冗余)';
COMMENT ON COLUMN pmis_flow_task_comment.content IS '评论内容';
COMMENT ON COLUMN pmis_flow_task_comment.type IS '评论类型: COMMENT/QUESTION/REPLY';
COMMENT ON COLUMN pmis_flow_task_comment.parent_id IS '父评论 ID (楼中楼, 0=根评论)';
COMMENT ON COLUMN pmis_flow_task_comment.created_by IS '创建人';
COMMENT ON COLUMN pmis_flow_task_comment.created_at IS '创建时间';
COMMENT ON COLUMN pmis_flow_task_comment.updated_by IS '更新人';
COMMENT ON COLUMN pmis_flow_task_comment.updated_at IS '更新时间';
COMMENT ON COLUMN pmis_flow_task_comment.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_rule_chain_graph -- P0-1: rule chain visual canvas
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_chain_graph (
    id              BIGSERIAL       PRIMARY KEY,
    rule_code       VARCHAR(128)    NOT NULL,
    name            VARCHAR(256),
    description     VARCHAR(512),
    scenario        VARCHAR(64),
    tenant_id       BIGINT          NOT NULL DEFAULT 1,
    graph_version   INTEGER         NOT NULL DEFAULT 1,
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',
    content_json    TEXT,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_chain_graph_rule
    ON pmis_rule_chain_graph (rule_code) WHERE deleted = 0;

COMMENT ON TABLE  pmis_rule_chain_graph IS 'P0-1: 规则链可视化画布 JSON 存储表';
COMMENT ON COLUMN pmis_rule_chain_graph.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_chain_graph.rule_code IS '关联规则编码';
COMMENT ON COLUMN pmis_rule_chain_graph.name IS '画布名称';
COMMENT ON COLUMN pmis_rule_chain_graph.description IS '画布描述';
COMMENT ON COLUMN pmis_rule_chain_graph.scenario IS '业务场景';
COMMENT ON COLUMN pmis_rule_chain_graph.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_chain_graph.graph_version IS '画布版本号';
COMMENT ON COLUMN pmis_rule_chain_graph.status IS '画布状态: DRAFT/PUBLISHED/ARCHIVED';
COMMENT ON COLUMN pmis_rule_chain_graph.content_json IS '画布节点/连线 JSON';
COMMENT ON COLUMN pmis_rule_chain_graph.created_by IS '创建人';
COMMENT ON COLUMN pmis_rule_chain_graph.created_at IS '创建时间';
COMMENT ON COLUMN pmis_rule_chain_graph.updated_by IS '更新人';
COMMENT ON COLUMN pmis_rule_chain_graph.updated_at IS '更新时间';
COMMENT ON COLUMN pmis_rule_chain_graph.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_rule_dependency -- P1-8: rule dependency
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_dependency (
    id                       BIGSERIAL       PRIMARY KEY,
    rule_code                VARCHAR(128)    NOT NULL,
    depends_on_rule_code     VARCHAR(128)    NOT NULL,
    dependency_type          VARCHAR(16)     NOT NULL DEFAULT 'EXECUTE',
    cascade_on_disable       SMALLINT        NOT NULL DEFAULT 0,
    description              VARCHAR(512),
    tenant_id                BIGINT          NOT NULL DEFAULT 1,
    created_by               VARCHAR(64),
    created_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rule_dependency_rule
    ON pmis_rule_dependency (rule_code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_rule_dependency_depends
    ON pmis_rule_dependency (depends_on_rule_code, cascade_on_disable) WHERE deleted = 0;

COMMENT ON TABLE  pmis_rule_dependency IS 'P1-8: 规则间依赖关系表 (EXECUTE/READ_RESULT/SOFT)';
COMMENT ON COLUMN pmis_rule_dependency.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_dependency.rule_code IS '规则编码';
COMMENT ON COLUMN pmis_rule_dependency.depends_on_rule_code IS '被依赖的规则编码';
COMMENT ON COLUMN pmis_rule_dependency.dependency_type IS '依赖类型: EXECUTE/READ_RESULT/SOFT';
COMMENT ON COLUMN pmis_rule_dependency.cascade_on_disable IS '上游禁用时是否级联禁用 1=是 0=否';
COMMENT ON COLUMN pmis_rule_dependency.description IS '依赖说明';
COMMENT ON COLUMN pmis_rule_dependency.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_dependency.created_by IS '创建人';
COMMENT ON COLUMN pmis_rule_dependency.created_at IS '创建时间';
COMMENT ON COLUMN pmis_rule_dependency.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_rule_ab_policy -- P1-10: AB test auto-rollback policy
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_ab_policy (
    id                      BIGSERIAL       PRIMARY KEY,
    rule_code               VARCHAR(128)    NOT NULL,
    auto_rollback_enabled   SMALLINT        NOT NULL DEFAULT 1,
    rollback_action         VARCHAR(16)     NOT NULL DEFAULT 'AUTO',
    error_rate_threshold    NUMERIC(5,4)    NOT NULL DEFAULT 0.0500,
    min_sample_size         INTEGER         NOT NULL DEFAULT 100,
    check_window_minutes    INTEGER         NOT NULL DEFAULT 5,
    notify_channels         VARCHAR(128),
    description             VARCHAR(512),
    last_evaluated_at       TIMESTAMP,
    last_rollback_at        TIMESTAMP,
    created_by              VARCHAR(64),
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64),
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_ab_policy_rule
    ON pmis_rule_ab_policy (rule_code) WHERE deleted = 0;

COMMENT ON TABLE  pmis_rule_ab_policy IS 'P1-10: AB Test 自动回滚策略表';
COMMENT ON COLUMN pmis_rule_ab_policy.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_ab_policy.rule_code IS '规则编码';
COMMENT ON COLUMN pmis_rule_ab_policy.auto_rollback_enabled IS '是否启用自动回滚 1=是 0=否';
COMMENT ON COLUMN pmis_rule_ab_policy.rollback_action IS '回滚动作: AUTO 自动回滚/NOTIFY 仅通知负责人';
COMMENT ON COLUMN pmis_rule_ab_policy.error_rate_threshold IS '触发回滚的错误率阈值 (0~1)';
COMMENT ON COLUMN pmis_rule_ab_policy.min_sample_size IS '最小评估样本数';
COMMENT ON COLUMN pmis_rule_ab_policy.check_window_minutes IS '评估窗口 (分钟)';
COMMENT ON COLUMN pmis_rule_ab_policy.notify_channels IS '通知通道 (逗号分隔, 引用 pmis_flow_notify_channel.id)';
COMMENT ON COLUMN pmis_rule_ab_policy.description IS '策略描述';
COMMENT ON COLUMN pmis_rule_ab_policy.last_evaluated_at IS '最近一次评估时间';
COMMENT ON COLUMN pmis_rule_ab_policy.last_rollback_at IS '最近一次回滚时间';
COMMENT ON COLUMN pmis_rule_ab_policy.created_by IS '创建人';
COMMENT ON COLUMN pmis_rule_ab_policy.created_at IS '创建时间';
COMMENT ON COLUMN pmis_rule_ab_policy.updated_by IS '更新人';
COMMENT ON COLUMN pmis_rule_ab_policy.updated_at IS '更新时间';
COMMENT ON COLUMN pmis_rule_ab_policy.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_rule_ab_rollback -- P1-10: AB test rollback history
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_ab_rollback (
    id                BIGSERIAL       PRIMARY KEY,
    rule_code         VARCHAR(128)    NOT NULL,
    trigger_reason    VARCHAR(32)     NOT NULL,
    error_rate        NUMERIC(5,4),
    sample_size       BIGINT,
    from_canary       SMALLINT        NOT NULL DEFAULT 0,
    operator          VARCHAR(64),
    notify_status     VARCHAR(32),
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rule_ab_rollback_rule
    ON pmis_rule_ab_rollback (rule_code, created_at DESC) WHERE deleted = 0;

COMMENT ON TABLE  pmis_rule_ab_rollback IS 'P1-10: AB Test 回滚历史表';
COMMENT ON COLUMN pmis_rule_ab_rollback.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_ab_rollback.rule_code IS '规则编码';
COMMENT ON COLUMN pmis_rule_ab_rollback.trigger_reason IS '触发原因: ERROR_RATE/MANUAL/OWNER_REQUEST';
COMMENT ON COLUMN pmis_rule_ab_rollback.error_rate IS '回滚时的错误率';
COMMENT ON COLUMN pmis_rule_ab_rollback.sample_size IS '评估样本数';
COMMENT ON COLUMN pmis_rule_ab_rollback.from_canary IS '是否从灰度版本回滚 1=是 0=否';
COMMENT ON COLUMN pmis_rule_ab_rollback.operator IS '操作人 (SYSTEM=自动)';
COMMENT ON COLUMN pmis_rule_ab_rollback.notify_status IS '通知发送状态: PENDING/SUCCESS/FAILED';
COMMENT ON COLUMN pmis_rule_ab_rollback.created_at IS '回滚时间';
COMMENT ON COLUMN pmis_rule_ab_rollback.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_rule_pack -- P2-14: rule pack marketplace
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_pack (
    id              BIGSERIAL       PRIMARY KEY,
    pack_code       VARCHAR(128)    NOT NULL,
    pack_version    VARCHAR(32)     NOT NULL,
    pack_name       VARCHAR(256)    NOT NULL,
    industry        VARCHAR(64),
    tags            VARCHAR(512),
    rule_codes      TEXT,
    description     VARCHAR(512),
    author          VARCHAR(128),
    download_count  BIGINT          NOT NULL DEFAULT 0,
    rating          NUMERIC(3,2)    NOT NULL DEFAULT 0,
    enabled         SMALLINT        NOT NULL DEFAULT 1,
    official        SMALLINT        NOT NULL DEFAULT 0,
    created_by      VARCHAR(64),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_pack_code_version
    ON pmis_rule_pack (pack_code, pack_version) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_rule_pack_industry
    ON pmis_rule_pack (industry, enabled) WHERE deleted = 0;

COMMENT ON TABLE  pmis_rule_pack IS 'P2-14: 规则集市场表 (按行业/场景打包)';
COMMENT ON COLUMN pmis_rule_pack.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_pack.pack_code IS '规则集编码';
COMMENT ON COLUMN pmis_rule_pack.pack_version IS '规则集版本号 (语义化)';
COMMENT ON COLUMN pmis_rule_pack.pack_name IS '规则集名称';
COMMENT ON COLUMN pmis_rule_pack.industry IS '适用行业';
COMMENT ON COLUMN pmis_rule_pack.tags IS '标签, 逗号分隔';
COMMENT ON COLUMN pmis_rule_pack.rule_codes IS '包含的规则编码列表 (逗号分隔)';
COMMENT ON COLUMN pmis_rule_pack.description IS '描述';
COMMENT ON COLUMN pmis_rule_pack.author IS '作者';
COMMENT ON COLUMN pmis_rule_pack.download_count IS '下载次数';
COMMENT ON COLUMN pmis_rule_pack.rating IS '评分 (0~5)';
COMMENT ON COLUMN pmis_rule_pack.enabled IS '是否上架 1=是 0=否';
COMMENT ON COLUMN pmis_rule_pack.official IS '是否官方 1=是 0=否';
COMMENT ON COLUMN pmis_rule_pack.created_by IS '创建人';
COMMENT ON COLUMN pmis_rule_pack.created_at IS '创建时间';
COMMENT ON COLUMN pmis_rule_pack.updated_by IS '更新人';
COMMENT ON COLUMN pmis_rule_pack.updated_at IS '更新时间';
COMMENT ON COLUMN pmis_rule_pack.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_rule_pack_install -- P2-14: rule pack install history
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_pack_install (
    id              BIGSERIAL       PRIMARY KEY,
    pack_code       VARCHAR(128)    NOT NULL,
    pack_version    VARCHAR(32)     NOT NULL,
    tenant_id       BIGINT          NOT NULL DEFAULT 1,
    installed_by    VARCHAR(64),
    installed_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS',
    error_message   TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rule_pack_install_tenant
    ON pmis_rule_pack_install (tenant_id, pack_code, installed_at DESC) WHERE deleted = 0;

COMMENT ON TABLE  pmis_rule_pack_install IS 'P2-14: 规则集安装历史表 (按租户)';
COMMENT ON COLUMN pmis_rule_pack_install.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_pack_install.pack_code IS '规则集编码';
COMMENT ON COLUMN pmis_rule_pack_install.pack_version IS '规则集版本号';
COMMENT ON COLUMN pmis_rule_pack_install.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_pack_install.installed_by IS '安装操作人';
COMMENT ON COLUMN pmis_rule_pack_install.installed_at IS '安装时间';
COMMENT ON COLUMN pmis_rule_pack_install.status IS '安装状态: SUCCESS/FAILED/ROLLBACK';
COMMENT ON COLUMN pmis_rule_pack_install.error_message IS '失败时的错误信息';
COMMENT ON COLUMN pmis_rule_pack_install.created_at IS '记录创建时间';
COMMENT ON COLUMN pmis_rule_pack_install.deleted IS '逻辑删除 0=未删 1=已删';
'''

# ---- generate ----
sql_files = sorted(SRC_DIR.glob('V*.sql'))
print(f'Found {len(sql_files)} source files')

header_lines = [
    '-- ====================================================================',
    '-- Nanjing Yunding PMIS database initialization script (single-file merge)',
    '-- Version: V1.0.0',
    '-- Target: PostgreSQL 18',
    '-- Description: This file is generated by merging all V1.0.0_xxx Flyway',
    '--   scripts under deploy/sql/ in version order. It is equivalent to',
    '--   running the Flyway migrations end-to-end, suitable for one-shot',
    '--   initialization of fresh environments.',
    '--   For online upgrades keep using Flyway + deploy/sql/V*__*.sql.',
    '-- Generated at: 2026-07-04',
    f'-- Files merged: {len(sql_files)}',
    '-- ====================================================================',
    '',
]

with OUT_FILE.open('w', encoding='utf-8', newline='') as out:
    out.write('\n'.join(header_lines))

    # ---- pre-pass: collect forward-referenced tables ----
    # Some Flyway scripts (notably V1.0.0_052__index_tuning) reference
    # tables that haven't been CREATEd yet. They are valid for online
    # upgrades once the corresponding table migration lands, but will
    # fail on a fresh one-shot init. We detect these "forward refs" and
    # comment out the offending DDL lines in the merged file.
    forward_ref_tables = set()
    table_defs_seen = set()
    for f in sql_files:
        for line in f.read_text(encoding='utf-8').splitlines():
            stripped = line.strip()
            if stripped.startswith('--'):
                continue
            m = re.match(
                r'CREATE\s+(?:OR\s+REPLACE\s+)?(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(',
                line, re.IGNORECASE,
            )
            if m:
                table_defs_seen.add(m.group(1))
    for f in sql_files:
        # Two-pass: identify DDL statement heads and their following
        # ON/COLUMN/ANALYZE clauses across multiple lines.
        lines = f.read_text(encoding='utf-8').splitlines()
        i = 0
        while i < len(lines):
            line = lines[i]
            stripped = line.strip()
            if stripped.startswith('--'):
                i += 1
                continue
            tail_refs = []
            m = re.match(r'CREATE\s+(?:UNIQUE\s+)?INDEX\b', line, re.IGNORECASE)
            if m:
                # ON <table> may be on the same line OR in subsequent
                # non-comment, non-empty lines.
                for j in range(i, min(i + 5, len(lines))):
                    m2 = re.search(r'\bON\s+(\w+)\b', lines[j], re.IGNORECASE)
                    if m2:
                        tail_refs.append(m2.group(1))
                        break
            else:
                m = re.match(r'ALTER\s+TABLE\s+(?:ONLY\s+)?(?:IF\s+EXISTS\s+)?(\w+)\b', line, re.IGNORECASE)
                if m:
                    tail_refs.append(m.group(1))
                else:
                    m = re.match(r'COMMENT\s+ON\s+COLUMN\s+(\w+)\.\w+\b', line, re.IGNORECASE)
                    if m:
                        tail_refs.append(m.group(1))
                    else:
                        m = re.match(r'COMMENT\s+ON\s+TABLE\s+(\w+)\b', line, re.IGNORECASE)
                        if m:
                            tail_refs.append(m.group(1))
                        else:
                            m = re.match(r'ANALYZE\s+(\w+)\b', line, re.IGNORECASE)
                            if m:
                                tail_refs.append(m.group(1))
            for t in tail_refs:
                if t in ('public', 'pg_catalog', 'information_schema'):
                    continue
                if t not in table_defs_seen:
                    forward_ref_tables.add(t)
            i += 1

    if forward_ref_tables:
        out.write('-- ====================================================================\n')
        out.write('-- [GENERATOR NOTE] Forward references detected and skipped:\n')
        for t in sorted(forward_ref_tables):
            out.write(f'--   - {t}  (CREATE TABLE not in V1.0.0_001..V1.0.0_059)\n')
        out.write('--   The following source files reference these tables (index, comment,\n')
        out.write('--   analyze) but the tables are not defined anywhere in the source. They\n')
        out.write('--   are commented out in the merged file. Online upgrades via Flyway\n')
        out.write('--   will need a follow-up migration that creates these tables first.\n')
        out.write('-- ====================================================================\n')
        out.write('\n')

    def is_forward_ref(line: str) -> bool:
        """Check whether `line` itself is a DDL that references a forward-ref table.
        Returns True if this is the line containing the table reference
        (e.g. "    ON pmis_xxx (col);" or "ALTER TABLE pmis_xxx ..."
        or "COMMENT ON COLUMN pmis_xxx.col ..." or "ANALYZE pmis_xxx;").
        """
        if not forward_ref_tables or line.strip().startswith('--'):
            return False
        for t in forward_ref_tables:
            if re.search(
                rf'\b(ON|TABLE|COLUMN|ANALYZE)\s+{re.escape(t)}\b',
                line, re.IGNORECASE,
            ) and not re.match(
                rf'^\s*CREATE\s+(?:OR\s+REPLACE\s+)?(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?{re.escape(t)}\s*\(',
                line, re.IGNORECASE,
            ):
                return True
        return False

    for f in sql_files:
        out.write('\n')
        out.write('-- ====================================================================\n')
        out.write(f'-- >>>>>>>>>> START OF {f.name}\n')
        out.write('-- ====================================================================\n')
        out.write('\n')
        # First pass: collect lines, mark the index of each line that
        # needs to be commented out. A DDL that spans multiple lines
        # (e.g. CREATE INDEX / ALTER TABLE) requires the previous line
        # (the statement head) to also be marked.
        raw_lines = f.read_text(encoding='utf-8').splitlines(keepends=True)
        to_skip = set()
        for i, raw in enumerate(raw_lines):
            if is_forward_ref(raw):
                to_skip.add(i)
                # If the previous line is a continuation head
                # (e.g. CREATE INDEX ...), mark it too.
                if i > 0:
                    prev = raw_lines[i - 1]
                    if (
                        not prev.strip().startswith('--')
                        and not prev.rstrip().endswith(';')
                    ):
                        to_skip.add(i - 1)
        for i, raw in enumerate(raw_lines):
            if i in to_skip and not raw.strip().startswith('--'):
                out.write('-- [SKIPPED-FWD-REF] ' + raw)
            else:
                out.write(raw)
        out.write('\n')
        out.write('-- ====================================================================\n')
        out.write(f'-- >>>>>>>>>> END OF {f.name}\n')
        out.write('-- ====================================================================\n')

    out.write('\n')
    out.write('-- ====================================================================\n')
    out.write('-- >>>>>>>>>> SUPPLEMENT: code-discovered tables (no Flyway migration yet)\n')
    out.write('--   The following tables are referenced by MyBatis-Plus entities /\n')
    out.write('--   mappers in ydsz-pmis-backend, but no Flyway migration has been\n')
    out.write('--   created yet. They are appended here for completeness so the\n')
    out.write('--   single-file initialization can be used on a fresh database.\n')
    out.write('--   Once a Flyway migration is published for each of them, this\n')
    out.write('--   block can be removed.\n')
    out.write('-- ====================================================================\n')
    out.write('\n')
    out.write(SUPPLEMENT)
    out.write('-- ====================================================================\n')
    out.write('-- >>>>>>>>>> END OF SUPPLEMENT\n')
    out.write('-- ====================================================================\n')

# ---- validation BEFORE comment additions ----

# ---- add table+column comments for 3 tables whose Flyway migration lacks COMMENT ----
text = OUT_FILE.read_text(encoding='utf-8')
ADDITIONS = {
    'pmis_rule_test_case': [
        "COMMENT ON TABLE pmis_rule_test_case IS 'P1-9: 规则测试用例表,用于规则评估的回归测试';",
        "COMMENT ON COLUMN pmis_rule_test_case.id IS '主键 ID';",
        "COMMENT ON COLUMN pmis_rule_test_case.name IS '测试用例名称';",
        "COMMENT ON COLUMN pmis_rule_test_case.rule_code IS '关联规则编码 (可选, null 表示通用测试用例)';",
        "COMMENT ON COLUMN pmis_rule_test_case.facts_data IS '事实数据 JSON (输入参数)';",
        "COMMENT ON COLUMN pmis_rule_test_case.expected_triggered IS '预期触发的规则编码列表 JSON';",
        "COMMENT ON COLUMN pmis_rule_test_case.description IS '用例描述';",
        "COMMENT ON COLUMN pmis_rule_test_case.created_at IS '创建时间';",
        "COMMENT ON COLUMN pmis_rule_test_case.updated_at IS '更新时间';",
    ],
    'pmis_rule_execution_trace': [
        "COMMENT ON TABLE pmis_rule_execution_trace IS 'P1-11: 规则执行链路追踪表,一次评估一条记录';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.id IS '主键 ID';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.trace_id IS '追踪 ID (同一批次评估共享)';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.rule_code IS '规则编码';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.rule_name IS '规则名称';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.scenario IS '业务场景';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.triggered IS '是否触发';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.severity IS '严重度 (RED/YELLOW/GREEN/INFO)';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.condition_result IS '条件表达式求值结果描述';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.elapsed_ms IS '执行耗时 (毫秒)';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.facts_snapshot IS '事实数据快照 JSON';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.result_snapshot IS '结果快照 JSON';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.error_message IS '错误信息';",
        "COMMENT ON COLUMN pmis_rule_execution_trace.created_at IS '创建时间';",
    ],
    'pmis_rule_decision_table': [
        "COMMENT ON TABLE pmis_rule_decision_table IS 'P1-12: 决策表 (DMN 简化版),条件/动作/行均以 JSON 存储';",
        "COMMENT ON COLUMN pmis_rule_decision_table.id IS '主键 ID';",
        "COMMENT ON COLUMN pmis_rule_decision_table.table_code IS '决策表编码 (唯一)';",
        "COMMENT ON COLUMN pmis_rule_decision_table.table_name IS '决策表名称';",
        "COMMENT ON COLUMN pmis_rule_decision_table.description IS '描述';",
        "COMMENT ON COLUMN pmis_rule_decision_table.category IS '类别';",
        "COMMENT ON COLUMN pmis_rule_decision_table.condition_columns IS '条件列定义 JSON: [{name,label,type}]';",
        "COMMENT ON COLUMN pmis_rule_decision_table.action_columns IS '动作列定义 JSON: [{name,label,type}]';",
        "COMMENT ON COLUMN pmis_rule_decision_table.rows IS '决策行 JSON: [{conditions,actions}]';",
        "COMMENT ON COLUMN pmis_rule_decision_table.default_actions IS '默认动作 (未匹配行时使用) JSON';",
        "COMMENT ON COLUMN pmis_rule_decision_table.hit_policy IS '命中策略: UNIQUE/FIRST/PRIORITY/COLLECT/ANY,默认 FIRST';",
        "COMMENT ON COLUMN pmis_rule_decision_table.enabled IS '是否启用';",
        "COMMENT ON COLUMN pmis_rule_decision_table.priority IS '优先级';",
        "COMMENT ON COLUMN pmis_rule_decision_table.version IS '版本号';",
        "COMMENT ON COLUMN pmis_rule_decision_table.created_by IS '创建人';",
        "COMMENT ON COLUMN pmis_rule_decision_table.created_at IS '创建时间';",
        "COMMENT ON COLUMN pmis_rule_decision_table.updated_by IS '更新人';",
        "COMMENT ON COLUMN pmis_rule_decision_table.updated_at IS '更新时间';",
    ],
}

lines = text.splitlines(keepends=True)
target_ends = {}
open_table = None
depth = 0
for i, line in enumerate(lines):
    if open_table is None:
        m = re.match(r'^\s*CREATE\s+(?:OR\s+REPLACE\s+)?(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\S+)\s*\(', line)
        if m:
            open_table = m.group(1)
            depth = 1
    else:
        clean = re.sub(r"'(?:[^']|'')*'", "''", line)
        opens = clean.count('(')
        closes = clean.count(')')
        depth += opens
        depth -= closes
        if depth <= 0 and (opens + closes) > 0:
            target_ends[open_table] = i
            open_table = None
            depth = 0

needed = {t: idx for t, idx in target_ends.items() if t in ADDITIONS}
sorted_inserts = sorted(needed.items(), key=lambda kv: -kv[1])
for table, idx in sorted_inserts:
    insert_block = ['\n'] + [line + '\n' for line in ADDITIONS[table]]
    lines[idx + 1:idx + 1] = insert_block

OUT_FILE.write_text(''.join(lines), encoding='utf-8')

# ---- validation pass ----
text = OUT_FILE.read_text(encoding='utf-8')
errors = []

# 1. No COMMENT ON VIEW/TABLE/COLUMN before its CREATE
re_view = re.compile(r"^COMMENT ON VIEW\s+(\S+)\s+IS\s+'", re.MULTILINE)
re_create_view = re.compile(r"^CREATE (?:OR REPLACE )?VIEW\s+(\S+)\s+(?:AS|\$)", re.MULTILINE)
re_tbl = re.compile(r"^COMMENT ON TABLE\s+(\S+)\s+IS\s+'", re.MULTILINE)
re_create_tbl = re.compile(r"^CREATE (?:OR REPLACE )?TABLE\s+(?:IF NOT EXISTS\s+)?(\S+)\s*\(", re.MULTILINE)

# Build sets of (name, line_no) for each create
creates_view = {m.group(1): m.start() for m in re_create_view.finditer(text)}
creates_tbl = {m.group(1): m.start() for m in re_create_tbl.finditer(text)}

for m in re_view.finditer(text):
    name = m.group(1)
    if name in creates_view and m.start() < creates_view[name]:
        errors.append(f'  COMMENT ON VIEW {name} before CREATE VIEW (offset {m.start()})')

# 2. No garbled chars
garbled = ['閫昏緫', '鍒犻櫎', '鏈', '宸插垹', '闆嗙紪', '閿', '榛樿']
for g in garbled:
    if g in text:
        errors.append(f'  garbled char {g!r} found')

# 3. All COMMENT ON bodies contain CJK (or are pure enum literals)
def is_pure_english(s: str) -> bool:
    if not s:
        return False
    return all(ord(c) < 128 for c in s)

re_col_body = re.compile(r"^COMMENT ON COLUMN\s+\S+\.\S+\s+IS\s+'([^']*)'", re.MULTILINE)
re_tbl_body = re.compile(r"^COMMENT ON TABLE\s+\S+\s+IS\s+'([^']*)'", re.MULTILINE)
eng_cols = sum(1 for m in re_col_body.finditer(text) if is_pure_english(m.group(1)))
eng_tbls = sum(1 for m in re_tbl_body.finditer(text) if is_pure_english(m.group(1)))

# 4. No index name collisions across all CREATE INDEX statements
import collections
re_idx = re.compile(
    r"^\s*CREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s+ON\s+(\w+(?:\.\w+)?)\b",
    re.IGNORECASE | re.MULTILINE,
)
by_name = collections.defaultdict(list)
for m in re_idx.finditer(text):
    # Skip SKIPPED-FWD-REF lines (auto-commented by generator)
    line_start = text.rfind('\n', 0, m.start()) + 1
    line = text[line_start: text.find('\n', m.start())]
    if '[SKIPPED-FWD-REF]' in line:
        continue
    name = m.group(2)
    table = m.group(3)
    by_name[name].append(table)
for name, tables in by_name.items():
    distinct = set(tables)
    if len(distinct) > 1:
        errors.append(f'  index name collision: {name!r} used by {sorted(distinct)}')

# 5. No schema-qualified DDL references (e.g. pmis_finance.pmis_finance_invoice)
#    would fail with "schema does not exist" in a single-public-schema DB.
import re as _re
known_tables = set()
for m in _re.finditer(
    r'CREATE\s+(?:OR\s+REPLACE\s+)?(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(',
    text, _re.IGNORECASE,
):
    known_tables.add(m.group(1))

for label, pat in [
    ('ALTER TABLE', _re.compile(r'ALTER\s+TABLE\s+(?:ONLY\s+)?(?:IF\s+EXISTS\s+)?(\w+(?:\.\w+)+)', _re.IGNORECASE)),
    ('CREATE TABLE', _re.compile(r'CREATE\s+(?:OR\s+REPLACE\s+)?(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+(?:\.\w+)+)', _re.IGNORECASE)),
    ('CREATE INDEX ON', _re.compile(r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?\w+\s+ON\s+(\w+(?:\.\w+)+)', _re.IGNORECASE)),
    ('CREATE VIEW', _re.compile(r'CREATE\s+(?:OR\s+REPLACE\s+)?VIEW\s+(\w+(?:\.\w+)+)', _re.IGNORECASE)),
    ('DROP', _re.compile(r'DROP\s+(?:TABLE|INDEX|VIEW)\s+(?:IF\s+EXISTS\s+)?(\w+(?:\.\w+)+)', _re.IGNORECASE)),
]:
    for m in pat.finditer(text):
        ref = m.group(1)
        schema = ref.split('.')[0]
        if schema in ('public', 'pg_catalog', 'information_schema'):
            continue
        errors.append(f'  schema-qualified DDL ({label}): {ref!r}')

# 6. No ALTER TABLE / CREATE INDEX ON / COMMENT ON COLUMN references an
#    unknown table. Such a reference will fail at execution time with
#    "relation <name> does not exist".
# Build a richer set: include the SUPPLEMENT tables too.
supplement_text = SUPPLEMENT
supp_tables = set()
for m in _re.finditer(
    r'CREATE\s+(?:OR\s+REPLACE\s+)?(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(',
    supplement_text, _re.IGNORECASE,
):
    supp_tables.add(m.group(1))
all_known_tables = known_tables | supp_tables

# ALTER TABLE
for m in _re.finditer(r'ALTER\s+TABLE\s+(?:ONLY\s+)?(?:IF\s+EXISTS\s+)?(\w+)\b', text, _re.IGNORECASE):
    name = m.group(1)
    if '.' in name:  # schema-qualified already checked above
        continue
    line_start = text.rfind('\n', 0, m.start()) + 1
    line = text[line_start: text.find('\n', m.start())]
    if '[SKIPPED-FWD-REF]' in line:
        continue
    if name not in all_known_tables:
        errors.append(f'  ALTER TABLE unknown table: {name!r}')

# CREATE [UNIQUE] INDEX ... ON <table>
for m in _re.finditer(
    r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?\w+\s+ON\s+(\w+)\b',
    text, _re.IGNORECASE,
):
    name = m.group(1)
    if '.' in name:
        continue
    line_start = text.rfind('\n', 0, m.start()) + 1
    line = text[line_start: text.find('\n', m.start())]
    if '[SKIPPED-FWD-REF]' in line:
        continue
    if name not in all_known_tables:
        errors.append(f'  CREATE INDEX ON unknown table: {name!r}')

# COMMENT ON COLUMN <table>.<col>
for m in _re.finditer(r'COMMENT\s+ON\s+COLUMN\s+(\w+)\.([\w]+)\b', text, _re.IGNORECASE):
    name = m.group(1)
    line_start = text.rfind('\n', 0, m.start()) + 1
    line = text[line_start: text.find('\n', m.start())]
    if '[SKIPPED-FWD-REF]' in line:
        continue
    if name not in all_known_tables:
        errors.append(f'  COMMENT ON COLUMN unknown table: {name!r}')

# COMMENT ON TABLE <name>
for m in _re.finditer(r'COMMENT\s+ON\s+TABLE\s+(\w+)\b', text, _re.IGNORECASE):
    name = m.group(1)
    if '.' in name:
        continue
    line_start = text.rfind('\n', 0, m.start()) + 1
    line = text[line_start: text.find('\n', m.start())]
    if '[SKIPPED-FWD-REF]' in line:
        continue
    if name not in all_known_tables:
        errors.append(f'  COMMENT ON TABLE unknown table: {name!r}')

# Stats
total_col = len(re_col_body.findall(text))
total_tbl = len(re_tbl_body.findall(text))
print(f'  COMMENT ON TABLE: {total_tbl}')
print(f'  COMMENT ON COLUMN: {total_col}')
print(f'  Pure-English bodies: TABLE {eng_tbls}, COLUMN {eng_cols}')
print(f'  Index collisions: {sum(1 for n,l in by_name.items() if len(set(l))>1)}')
print(f'  File size: {OUT_FILE.stat().st_size:,} bytes')

if errors:
    print('VALIDATION FAILED:')
    for e in errors:
        print(e)
    sys.exit(1)
else:
    print('VALIDATION PASSED')
