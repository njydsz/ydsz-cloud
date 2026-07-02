-- ============================================================
-- V1.0.0_041: 初始化流程自动触发规则表
-- 
-- 创建 pmis_flow_auto_trigger 表，用于配置流程完成后的自动触发规则。
-- 当一个流程实例完成时，系统检查 sourceFlowCode 对应的所有启用规则，
-- 评估 conditionExpression 条件（Aviator 语法），满足条件则自动启动
-- targetFlowCode 对应的目标流程。
-- ============================================================

-- ==============================
-- 1. 建表
-- ==============================
CREATE TABLE pmis_flow_auto_trigger (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_flow_code    VARCHAR(100) NOT NULL COMMENT '源流程编码（触发方）',
    target_flow_code    VARCHAR(100) NOT NULL COMMENT '目标流程编码（被触发方）',
    condition_expression VARCHAR(500) COMMENT '条件表达式（Aviator 语法，为空则无条件触发）',
    description         VARCHAR(200) COMMENT '规则描述',
    enabled             TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    sort_order          INT DEFAULT 0 COMMENT '排序权重',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
    created_by          BIGINT COMMENT '创建人 ID',
    updated_by          BIGINT COMMENT '更新人 ID',
    INDEX idx_source_flow_code (source_flow_code),
    INDEX idx_target_flow_code (target_flow_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程自动触发规则';