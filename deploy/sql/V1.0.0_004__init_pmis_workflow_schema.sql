-- =====================================================
-- PMIS 工作流基础模块清理 DDL（Flowable 表已下线）
-- 版本: V1.0.0_004
-- 描述: 完全移除 Flowable 引擎相关的业务关联表 / 表单定义表 / 节点配置表
--       业务流程关联信息已统一收敛到自研 pmis_flow_instance / pmis_flow_task
--       流程表单/节点配置已收敛到自研 pmis_flow_definition / pmis_flow_node / pmis_flow_skip
-- 历史: V1.0.0_004 旧版本曾创建 pmis_workflow_business / pmis_workflow_form / pmis_workflow_node_config
--       现已废弃，本次迁移仅 DROP（不重建），以保证幂等
-- =====================================================

-- 清理：业务流程实例关联表（功能已被 pmis_flow_instance 替代）
DROP TABLE IF EXISTS pmis_workflow_business;

-- 清理：流程表单定义表（功能已通过 pmis_flow_definition.form_path 替代）
DROP TABLE IF EXISTS pmis_workflow_form;

-- 清理：流程节点配置表（功能已通过 pmis_flow_node.permission_flag / ext 替代）
DROP TABLE IF EXISTS pmis_workflow_node_config;
