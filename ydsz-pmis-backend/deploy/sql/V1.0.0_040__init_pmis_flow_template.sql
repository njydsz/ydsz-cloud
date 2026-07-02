-- ============================================================
-- V1.0.0_040: 初始化流程模板市场表
-- 
-- 创建 pmis_flow_template 表，预置 15 套行业审批流程模板，
-- 覆盖人事、财务、行政、项目四大业务领域。
-- 每个模板包含简化 BPMN 2.0 XML，与项目 BpmnXmlParser 兼容。
-- ============================================================

-- ==============================
-- 1. 建表
-- ==============================
CREATE TABLE pmis_flow_template (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL UNIQUE COMMENT '模板编码',
    template_name VARCHAR(200) NOT NULL COMMENT '模板名称',
    category      VARCHAR(50) DEFAULT 'GENERAL' COMMENT '分类：HR/FINANCE/ADMIN/PROJECT',
    description   VARCHAR(500) COMMENT '模板描述',
    icon          VARCHAR(200) COMMENT '图标路径',
    bpmn_xml      LONGTEXT COMMENT 'BPMN 2.0 XML 流程定义',
    form_path     VARCHAR(200) COMMENT '默认表单路径',
    use_count     INT DEFAULT 0 COMMENT '使用次数',
    sort_order    INT DEFAULT 0 COMMENT '排序权重',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板市场';

-- ==============================
-- 2. 预置模板数据
-- ==============================

-- ============ 人事类 (HR) ============

-- 2.1 请假审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'hr_leave_approval', '请假审批', 'HR',
    '适用于员工事假、病假、年假等各类请假审批流程，支持按请假天数分级审批',
    '/icons/template/leave.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="hr_leave_approval" name="请假审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_leave" name="提交请假申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <exclusiveGateway id="gt3days" name="是否超过3天"/>
    <userTask id="hr_approve" name="HR审批" flowable:assignee="role:hr"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_leave"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_leave" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_gt3" sourceRef="dept_approve" targetRef="gt3days"/>
    <sequenceFlow id="flow_gt3_yes" sourceRef="gt3days" targetRef="hr_approve">
      <conditionExpression xsi:type="tFormalExpression">${leaveDays > 3}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_gt3_no" sourceRef="gt3days" targetRef="end">
      <conditionExpression xsi:type="tFormalExpression">${leaveDays <= 3}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_hr_gm" sourceRef="hr_approve" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_end" sourceRef="gm_approve" targetRef="end"/>
  </process>
</definitions>',
    '/forms/leave.html', 10
);

-- 2.2 加班审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'hr_overtime_approval', '加班审批', 'HR',
    '适用于员工工作日加班、休息日加班、节假日加班审批，支持加班时长与调休联动',
    '/icons/template/overtime.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="hr_overtime_approval" name="加班审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_overtime" name="提交加班申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="hr_record" name="HR备案" flowable:assignee="role:hr"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_overtime"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_overtime" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_hr" sourceRef="dept_approve" targetRef="hr_record"/>
    <sequenceFlow id="flow_hr_end" sourceRef="hr_record" targetRef="end"/>
  </process>
</definitions>',
    '/forms/overtime.html', 20
);

-- 2.3 出差审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'hr_business_trip', '出差审批', 'HR',
    '适用于员工因公出差申请审批，包含出差事由、行程安排、费用预估等',
    '/icons/template/trip.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="hr_business_trip" name="出差审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_trip" name="提交出差申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门审批" flowable:assignee="role:dept_manager"/>
    <exclusiveGateway id="cross_province" name="是否跨省"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <userTask id="hr_record" name="HR备案" flowable:assignee="role:hr"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_trip"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_trip" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_gate" sourceRef="dept_approve" targetRef="cross_province"/>
    <sequenceFlow id="flow_cross_yes" sourceRef="cross_province" targetRef="gm_approve">
      <conditionExpression xsi:type="tFormalExpression">${crossProvince == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_cross_no" sourceRef="cross_province" targetRef="hr_record">
      <conditionExpression xsi:type="tFormalExpression">${crossProvince == false}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_gm_hr" sourceRef="gm_approve" targetRef="hr_record"/>
    <sequenceFlow id="flow_hr_end" sourceRef="hr_record" targetRef="end"/>
  </process>
</definitions>',
    '/forms/trip.html', 30
);

-- 2.4 离职审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'hr_resignation_approval', '离职审批', 'HR',
    '适用于员工主动离职或协商离职的全流程审批，包含工作交接、资产归还、薪资结算等环节',
    '/icons/template/resign.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="hr_resignation_approval" name="离职审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_resign" name="提交离职申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="hr_interview" name="HR面谈" flowable:assignee="role:hr"/>
    <userTask id="handover" name="工作交接确认" flowable:assignee="role:dept_manager"/>
    <userTask id="asset_check" name="资产归还确认" flowable:assignee="role:admin"/>
    <userTask id="finance_settle" name="薪资结算" flowable:assignee="role:finance"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_resign"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_resign" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_hr" sourceRef="dept_approve" targetRef="hr_interview"/>
    <sequenceFlow id="flow_hr_handover" sourceRef="hr_interview" targetRef="handover"/>
    <sequenceFlow id="flow_handover_asset" sourceRef="handover" targetRef="asset_check"/>
    <sequenceFlow id="flow_asset_finance" sourceRef="asset_check" targetRef="finance_settle"/>
    <sequenceFlow id="flow_finance_gm" sourceRef="finance_settle" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_end" sourceRef="gm_approve" targetRef="end"/>
  </process>
</definitions>',
    '/forms/resignation.html', 40
);

-- 2.5 转正审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'hr_regularization', '转正审批', 'HR',
    '适用于试用期员工转正审批，包含试用期考核评价、导师评价、转正薪资核定等',
    '/icons/template/regular.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="hr_regularization" name="转正审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_regular" name="提交转正申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="mentor_evaluate" name="导师评价" flowable:assignee="user:${mentorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="hr_evaluate" name="HR评估" flowable:assignee="role:hr"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_regular"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_regular" targetRef="mentor_evaluate"/>
    <sequenceFlow id="flow_mentor_dept" sourceRef="mentor_evaluate" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_hr" sourceRef="dept_approve" targetRef="hr_evaluate"/>
    <sequenceFlow id="flow_hr_gm" sourceRef="hr_evaluate" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_end" sourceRef="gm_approve" targetRef="end"/>
  </process>
</definitions>',
    '/forms/regularization.html', 50
);

-- ============ 财务类 (FINANCE) ============

-- 2.6 报销审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'finance_reimbursement', '报销审批', 'FINANCE',
    '适用于员工日常费用报销审批，按金额分级审批，支持发票验真与预算核减',
    '/icons/template/reimburse.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="finance_reimbursement" name="报销审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_reimburse" name="提交报销申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <exclusiveGateway id="amount_check" name="金额判断"/>
    <userTask id="finance_audit" name="财务审核" flowable:assignee="role:finance"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <userTask id="cashier_pay" name="出纳付款" flowable:assignee="role:cashier"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_reimburse"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_reimburse" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_gate" sourceRef="dept_approve" targetRef="amount_check"/>
    <sequenceFlow id="flow_lt5k" sourceRef="amount_check" targetRef="finance_audit">
      <conditionExpression xsi:type="tFormalExpression">${amount <= 5000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_gte5k" sourceRef="amount_check" targetRef="gm_approve">
      <conditionExpression xsi:type="tFormalExpression">${amount > 5000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_finance_cashier" sourceRef="finance_audit" targetRef="cashier_pay"/>
    <sequenceFlow id="flow_gm_finance" sourceRef="gm_approve" targetRef="finance_audit"/>
    <sequenceFlow id="flow_cashier_end" sourceRef="cashier_pay" targetRef="end"/>
  </process>
</definitions>',
    '/forms/reimbursement.html', 60
);

-- 2.7 借款审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'finance_loan_approval', '借款审批', 'FINANCE',
    '适用于员工因公借款审批，包含借款事由、金额、还款计划，支持按金额分级审批',
    '/icons/template/loan.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="finance_loan_approval" name="借款审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_loan" name="提交借款申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="finance_audit" name="财务审核" flowable:assignee="role:finance"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <userTask id="cashier_disburse" name="出纳放款" flowable:assignee="role:cashier"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_loan"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_loan" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_finance" sourceRef="dept_approve" targetRef="finance_audit"/>
    <sequenceFlow id="flow_finance_gm" sourceRef="finance_audit" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_cashier" sourceRef="gm_approve" targetRef="cashier_disburse"/>
    <sequenceFlow id="flow_cashier_end" sourceRef="cashier_disburse" targetRef="end"/>
  </process>
</definitions>',
    '/forms/loan.html', 70
);

-- 2.8 付款审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'finance_payment_approval', '付款审批', 'FINANCE',
    '适用于对公付款审批，包含采购付款、服务费付款、预付款等，支持合同关联与预算核验',
    '/icons/template/payment.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="finance_payment_approval" name="付款审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_payment" name="提交付款申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="finance_audit" name="财务审核" flowable:assignee="role:finance"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <userTask id="cashier_execute" name="出纳执行付款" flowable:assignee="role:cashier"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_payment"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_payment" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_finance" sourceRef="dept_approve" targetRef="finance_audit"/>
    <sequenceFlow id="flow_finance_gm" sourceRef="finance_audit" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_cashier" sourceRef="gm_approve" targetRef="cashier_execute"/>
    <sequenceFlow id="flow_cashier_end" sourceRef="cashier_execute" targetRef="end"/>
  </process>
</definitions>',
    '/forms/payment.html', 80
);

-- 2.9 预算审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'finance_budget_approval', '预算审批', 'FINANCE',
    '适用于部门年度预算、项目预算、追加预算的编制与审批，支持多级预算审批与执行跟踪',
    '/icons/template/budget.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="finance_budget_approval" name="预算审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_budget" name="提交预算编制" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="finance_audit" name="财务审核" flowable:assignee="role:finance"/>
    <userTask id="cfo_approve" name="CFO审批" flowable:assignee="role:cfo"/>
    <userTask id="ceo_approve" name="CEO审批" flowable:assignee="role:ceo"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_budget"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_budget" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_finance" sourceRef="dept_approve" targetRef="finance_audit"/>
    <sequenceFlow id="flow_finance_cfo" sourceRef="finance_audit" targetRef="cfo_approve"/>
    <sequenceFlow id="flow_cfo_ceo" sourceRef="cfo_approve" targetRef="ceo_approve"/>
    <sequenceFlow id="flow_ceo_end" sourceRef="ceo_approve" targetRef="end"/>
  </process>
</definitions>',
    '/forms/budget.html', 90
);

-- ============ 行政类 (ADMIN) ============

-- 2.10 用章审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'admin_seal_approval', '用章审批', 'ADMIN',
    '适用于公司公章、合同章、财务章等各类印章使用申请审批，按印章类型分级审批',
    '/icons/template/seal.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="admin_seal_approval" name="用章审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_seal" name="提交用章申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <exclusiveGateway id="seal_type" name="印章类型"/>
    <userTask id="legal_review" name="法务审核" flowable:assignee="role:legal"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <userTask id="admin_seal" name="行政盖章" flowable:assignee="role:admin"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_seal"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_seal" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_gate" sourceRef="dept_approve" targetRef="seal_type"/>
    <sequenceFlow id="flow_official" sourceRef="seal_type" targetRef="legal_review">
      <conditionExpression xsi:type="tFormalExpression">${sealType == "official"}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_other" sourceRef="seal_type" targetRef="admin_seal">
      <conditionExpression xsi:type="tFormalExpression">${sealType != "official"}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_legal_gm" sourceRef="legal_review" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_admin" sourceRef="gm_approve" targetRef="admin_seal"/>
    <sequenceFlow id="flow_admin_end" sourceRef="admin_seal" targetRef="end"/>
  </process>
</definitions>',
    '/forms/seal.html', 100
);

-- 2.11 采购审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'admin_purchase_approval', '采购审批', 'ADMIN',
    '适用于办公用品、设备、服务等采购申请审批，按金额分级审批，支持供应商比价',
    '/icons/template/purchase.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="admin_purchase_approval" name="采购审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_purchase" name="提交采购申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <exclusiveGateway id="amount_level" name="金额分级"/>
    <userTask id="admin_approve" name="行政审批" flowable:assignee="role:admin"/>
    <userTask id="finance_approve" name="财务审批" flowable:assignee="role:finance"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_purchase"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_purchase" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_gate" sourceRef="dept_approve" targetRef="amount_level"/>
    <sequenceFlow id="flow_lt10k" sourceRef="amount_level" targetRef="admin_approve">
      <conditionExpression xsi:type="tFormalExpression">${amount <= 10000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_gte10k" sourceRef="amount_level" targetRef="finance_approve">
      <conditionExpression xsi:type="tFormalExpression">${amount > 10000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_admin_end" sourceRef="admin_approve" targetRef="end"/>
    <sequenceFlow id="flow_finance_gm" sourceRef="finance_approve" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_end" sourceRef="gm_approve" targetRef="end"/>
  </process>
</definitions>',
    '/forms/purchase.html', 110
);

-- 2.12 资产领用审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'admin_asset_borrow', '资产领用审批', 'ADMIN',
    '适用于公司固定资产（电脑、显示器、打印机等）领用申请，支持资产入库与归还管理',
    '/icons/template/asset.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="admin_asset_borrow" name="资产领用审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_borrow" name="提交领用申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="admin_verify" name="行政核实库存" flowable:assignee="role:admin"/>
    <userTask id="asset_assign" name="资产管理员分配" flowable:assignee="role:asset_manager"/>
    <userTask id="receiver_sign" name="领用人签收" flowable:assignee="user:${initiatorId}"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_borrow"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_borrow" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_admin" sourceRef="dept_approve" targetRef="admin_verify"/>
    <sequenceFlow id="flow_admin_asset" sourceRef="admin_verify" targetRef="asset_assign"/>
    <sequenceFlow id="flow_asset_sign" sourceRef="asset_assign" targetRef="receiver_sign"/>
    <sequenceFlow id="flow_sign_end" sourceRef="receiver_sign" targetRef="end"/>
  </process>
</definitions>',
    '/forms/asset.html', 120
);

-- ============ 项目类 (PROJECT) ============

-- 2.13 立项审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'project_initiation', '立项审批', 'PROJECT',
    '适用于新项目立项审批流程，包含项目可行性分析、资源评估、预算核定等',
    '/icons/template/project.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="project_initiation" name="立项审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_project" name="提交立项申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_approve" name="部门负责人审批" flowable:assignee="role:dept_manager"/>
    <userTask id="pmo_review" name="PMO审核" flowable:assignee="role:pmo"/>
    <userTask id="finance_review" name="财务评估" flowable:assignee="role:finance"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_project"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_project" targetRef="dept_approve"/>
    <sequenceFlow id="flow_dept_pmo" sourceRef="dept_approve" targetRef="pmo_review"/>
    <sequenceFlow id="flow_pmo_finance" sourceRef="pmo_review" targetRef="finance_review"/>
    <sequenceFlow id="flow_finance_gm" sourceRef="finance_review" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_end" sourceRef="gm_approve" targetRef="end"/>
  </process>
</definitions>',
    '/forms/project_initiation.html', 130
);

-- 2.14 合同评审
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'project_contract_review', '合同评审', 'PROJECT',
    '适用于各类合同签订前的多部门评审流程，包含法务审核、财务审核、技术评审等',
    '/icons/template/contract.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="project_contract_review" name="合同评审" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_contract" name="提交合同评审" flowable:assignee="user:${initiatorId}"/>
    <userTask id="dept_review" name="部门审核" flowable:assignee="role:dept_manager"/>
    <parallelGateway id="parallel_review" name="并行评审"/>
    <userTask id="legal_review" name="法务审核" flowable:assignee="role:legal"/>
    <userTask id="finance_review" name="财务审核" flowable:assignee="role:finance"/>
    <userTask id="tech_review" name="技术评审" flowable:assignee="role:tech_lead"/>
    <parallelGateway id="merge_review" name="汇总评审"/>
    <userTask id="gm_sign" name="总经理签批" flowable:assignee="role:general_manager"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_contract"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_contract" targetRef="dept_review"/>
    <sequenceFlow id="flow_dept_parallel" sourceRef="dept_review" targetRef="parallel_review"/>
    <sequenceFlow id="flow_parallel_legal" sourceRef="parallel_review" targetRef="legal_review"/>
    <sequenceFlow id="flow_parallel_finance" sourceRef="parallel_review" targetRef="finance_review"/>
    <sequenceFlow id="flow_parallel_tech" sourceRef="parallel_review" targetRef="tech_review"/>
    <sequenceFlow id="flow_legal_merge" sourceRef="legal_review" targetRef="merge_review"/>
    <sequenceFlow id="flow_finance_merge" sourceRef="finance_review" targetRef="merge_review"/>
    <sequenceFlow id="flow_tech_merge" sourceRef="tech_review" targetRef="merge_review"/>
    <sequenceFlow id="flow_merge_gm" sourceRef="merge_review" targetRef="gm_sign"/>
    <sequenceFlow id="flow_gm_end" sourceRef="gm_sign" targetRef="end"/>
  </process>
</definitions>',
    '/forms/contract_review.html', 140
);

-- 2.15 项目变更审批
INSERT INTO pmis_flow_template (template_code, template_name, category, description, icon, bpmn_xml, form_path, sort_order)
VALUES (
    'project_change_approval', '项目变更审批', 'PROJECT',
    '适用于项目范围、进度、预算、资源等变更申请审批，支持变更影响评估与版本管理',
    '/icons/template/change.svg',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://pmis.ydsz/flow">
  <process id="project_change_approval" name="项目变更审批" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="submit_change" name="提交变更申请" flowable:assignee="user:${initiatorId}"/>
    <userTask id="pm_assess" name="项目经理评估" flowable:assignee="role:pm"/>
    <exclusiveGateway id="change_level" name="变更级别"/>
    <userTask id="pmo_approve" name="PMO审批" flowable:assignee="role:pmo"/>
    <userTask id="stakeholder_approve" name="干系人审批" flowable:assignee="role:stakeholder"/>
    <userTask id="gm_approve" name="总经理审批" flowable:assignee="role:general_manager"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="flow_start" sourceRef="start" targetRef="submit_change"/>
    <sequenceFlow id="flow_submit" sourceRef="submit_change" targetRef="pm_assess"/>
    <sequenceFlow id="flow_pm_gate" sourceRef="pm_assess" targetRef="change_level"/>
    <sequenceFlow id="flow_minor" sourceRef="change_level" targetRef="pmo_approve">
      <conditionExpression xsi:type="tFormalExpression">${changeLevel == "MINOR"}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_major" sourceRef="change_level" targetRef="stakeholder_approve">
      <conditionExpression xsi:type="tFormalExpression">${changeLevel == "MAJOR"}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow_pmo_end" sourceRef="pmo_approve" targetRef="end"/>
    <sequenceFlow id="flow_stakeholder_gm" sourceRef="stakeholder_approve" targetRef="gm_approve"/>
    <sequenceFlow id="flow_gm_end" sourceRef="gm_approve" targetRef="end"/>
  </process>
</definitions>',
    '/forms/project_change.html', 150
);