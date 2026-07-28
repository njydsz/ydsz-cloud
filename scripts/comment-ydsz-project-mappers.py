#!/usr/bin/env python3
"""Add/upgrade Javadoc on *Mapper.java files to meet Alibaba standards."""
import pathlib
import re
import textwrap

# 4-tuple: (entity_class_simple_name, table_name_cn, biz_desc, see_refs)
MAPPERS = {
    # ===== ydsz-project =====
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/alert/AlertDispatchMapper.java": {
        "entity": "AlertDispatch",
        "table": "ydsz_alert_dispatch",
        "title": "告警分发 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_alert_dispatch}，存储告警事件的订阅、推送、处理结果。",
            "告警是项目/系统异常的信号通知，按订阅规则推送给责任人（IM/邮件/短信），支持告警确认/转派/关闭等生命周期。",
        ],
        "index": [
            "uk_dispatch_id — 主键索引（雪花算法字符串）",
            "idx_rule_severity — 规则+严重度查询索引",
            "idx_status — 状态过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.alert.AlertDispatch 告警实体",
            "com.njydsz.project.server.service.AlertDispatchService 告警 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/billable/BillableUtilizationSnapshotMapper.java": {
        "entity": "BillableUtilizationSnapshot",
        "table": "ydsz_billable_utilization_snapshot",
        "title": "可计费率快照 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_billable_utilization_snapshot}，存储员工/部门可计费率快照。",
            "按月/季度固化计算结果，避免数据漂移，是衡量团队产能与项目健康度的核心指标。",
        ],
        "index": [
            "uk_snapshot — (员工/部门+周期) 唯一索引",
            "idx_tenant_period — 租户+周期查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot 快照实体",
            "com.njydsz.project.server.service.BillableUtilizationSnapshotService 快照 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/cost/CostAllocationMapper.java": {
        "entity": "CostAllocation",
        "table": "ydsz_cost_allocation",
        "title": "成本分摊 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_cost_allocation}，存储成本分摊规则与执行结果。",
            "成本分摊是把公共成本按规则分摊到具体项目/部门的过程，支持按工时/收入/人数/固定比例等多种分摊方式。",
        ],
        "index": [
            "uk_allocation_key — (项目+科目+周期) 唯一索引",
            "idx_source_target — 源/目标维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.cost.CostAllocation 成本分摊实体",
            "com.njydsz.project.server.service.CostAllocationService 成本分摊 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/cost/CostPurchaseMapper.java": {
        "entity": "CostPurchase",
        "table": "ydsz_cost_purchase",
        "title": "采购成本 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_cost_purchase}，存储项目采购成本登记。",
            "采购成本是项目非工时成本的主要组成（外协/采购/外包等），按订单/合同/验收节点分摊到项目。",
        ],
        "index": [
            "uk_purchase_no — 采购单号唯一索引",
            "idx_project_id — 项目维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.cost.CostPurchase 采购成本实体",
            "com.njydsz.project.server.service.CostPurchaseService 采购成本 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/evm/EvmMeasureMapper.java": {
        "entity": "EvmMeasure",
        "table": "ydsz_evm_measure",
        "title": "EVM 挣值测量 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_evm_measure}，存储 EVM 测量记录。",
            "EVM 通过 PV/EV/AC 三个核心指标计算 SPI/CPI，量化项目是否按时/是否超支。",
        ],
        "index": [
            "uk_project_period — (项目+周期) 唯一索引",
            "idx_measure_at — 测量时间排序索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.evm.EvmMeasure EVM 实体",
            "com.njydsz.project.server.service.EvmMeasureService EVM Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/execution/ExecutionClosureMapper.java": {
        "entity": "ExecutionClosure",
        "table": "ydsz_execution_closure",
        "title": "项目终验 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_execution_closure}，存储项目终验记录。",
            "终验是项目生命周期的收官阶段：交付物验收、文档归档、团队释放、客户签收、满意度触发。",
        ],
        "index": [
            "uk_project_id — 1 个项目只允许 1 个终验",
            "idx_closure_at — 终验时间排序索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionClosure 终验实体",
            "com.njydsz.project.server.service.ExecutionClosureService 终验 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/execution/ExecutionDeliveryItemMapper.java": {
        "entity": "ExecutionDeliveryItem",
        "table": "ydsz_execution_delivery_item",
        "title": "项目交付物 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_execution_delivery_item}，存储项目交付物登记与验收状态。",
            "交付物是项目产出的具体物件（代码/文档/数据/系统/培训等），按交付标准验收。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
            "idx_status — 验收状态过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem 交付物实体",
            "com.njydsz.project.server.service.ExecutionDeliveryItemService 交付物 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/execution/ExecutionDeliveryStandardMapper.java": {
        "entity": "ExecutionDeliveryStandard",
        "table": "ydsz_execution_delivery_standard",
        "title": "交付标准 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_execution_delivery_standard}，存储项目交付标准。",
            "标准定义了项目交付物的质量门槛（代码规范/测试覆盖率/文档完整性/性能指标），是验收环节的硬性指标。",
        ],
        "index": [
            "uk_project_type — (项目类型+标准版本) 唯一索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard 标准实体",
            "com.njydsz.project.server.service.ExecutionDeliveryStandardService 标准 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/execution/ExecutionRiskMapper.java": {
        "entity": "ExecutionRisk",
        "table": "ydsz_execution_risk",
        "title": "项目风险 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_execution_risk}，存储项目风险登记与跟踪。",
            "风险是项目执行中可能影响进度/质量/成本的不确定事件，按识别→评估→应对→跟踪→关闭流程管理。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
            "idx_risk_level — 风险等级过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionRisk 风险实体",
            "com.njydsz.project.server.service.ExecutionRiskService 风险 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/execution/ExecutionTimeEntryMapper.java": {
        "entity": "ExecutionTimeEntry",
        "table": "ydsz_execution_time_entry",
        "title": "工时填报 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_execution_time_entry}，存储员工工时填报记录。",
            "按员工×日期×任务维度填报，是项目成本的主要组成（人力成本占项目成本 60%+）。",
        ],
        "index": [
            "idx_employee_date — 员工+日期查询索引",
            "idx_task_id — 任务维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionTimeEntry 工时实体",
            "com.njydsz.project.server.service.ExecutionTimeEntryService 工时 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/execution/ExecutionWbsTaskMapper.java": {
        "entity": "ExecutionWbsTask",
        "table": "ydsz_execution_wbs_task",
        "title": "项目 WBS 任务 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_execution_wbs_task}，存储项目 WBS 任务分解。",
            "WBS（工作分解结构）将项目交付物逐层拆分为可执行的任务，用于项目计划/进度跟踪/工时填报。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
            "idx_parent_id — 父子任务层级索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionWbsTask WBS 任务实体",
            "com.njydsz.project.server.service.ExecutionWbsTaskService WBS 任务 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/ops/OpsTicketMapper.java": {
        "entity": "OpsTicket",
        "table": "ydsz_ops_ticket",
        "title": "运维工单 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_ops_ticket}，存储运维工单记录。",
            "工单是项目交付后客户对系统/服务的报修/咨询/请求入口，由客服/运维团队按 SLA 响应处理。",
        ],
        "index": [
            "uk_ticket_no — 工单号唯一索引",
            "idx_status_priority — 状态+优先级过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.ops.OpsTicket 工单实体",
            "com.njydsz.project.server.service.OpsTicketService 工单 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectBudgetItemMapper.java": {
        "entity": "ProjectBudgetItem",
        "table": "ydsz_project_budget_item",
        "title": "项目预算项 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_budget_item}，存储项目预算的最小单元。",
            "按科目（工时/采购/差旅/外协/管理费等）维度编制，用于控制项目支出、对比实际成本、利润分析。",
        ],
        "index": [
            "uk_project_subject_version — (项目+科目+版本) 唯一索引",
            "idx_project_id — 项目维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectBudgetItem 预算项实体",
            "com.njydsz.project.server.service.ProjectBudgetItemService 预算项 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectChangeMapper.java": {
        "entity": "ProjectChange",
        "table": "ydsz_project_change",
        "title": "项目变更 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_change}，存储项目变更申请与审批记录。",
            "项目变更是项目执行过程中对项目计划/范围/目标的调整，区别于合同变更（侧重钱/账期）。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
            "idx_change_type — 变更类型过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectChange 项目变更实体",
            "com.njydsz.project.server.service.ProjectChangeService 项目变更 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectContractChangeMapper.java": {
        "entity": "ProjectContractChange",
        "table": "ydsz_project_contract_change",
        "title": "合同变更 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_contract_change}，存储合同变更记录。",
            "合同变更是对原合同条款的修改（工作范围/金额/工期/账期），需经双方书面确认后生效。",
        ],
        "index": [
            "idx_contract_id — 合同维度查询索引",
            "idx_change_type — 变更类型过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectContractChange 合同变更实体",
            "com.njydsz.project.server.service.ProjectContractChangeService 合同变更 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectContractMapper.java": {
        "entity": "ProjectContract",
        "table": "ydsz_project_contract",
        "title": "项目合同 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_contract}，存储项目合同正文。",
            "合同是项目收入侧的核心依据，所有计费/开票/收款节点都以合同金额为基准。",
        ],
        "index": [
            "uk_contract_no — 合同号唯一索引",
            "idx_project_id — 项目维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectContract 合同实体",
            "com.njydsz.project.server.service.ProjectContractService 合同 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectContractSupplementMapper.java": {
        "entity": "ProjectContractSupplement",
        "table": "ydsz_project_contract_supplement",
        "title": "合同补充协议 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_contract_supplement}，存储合同补充协议。",
            "补充协议是对原合同的补充约定（增项/账期/工作范围），是双方新达成的一致。",
        ],
        "index": [
            "idx_contract_id — 合同维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectContractSupplement 补充协议实体",
            "com.njydsz.project.server.service.ProjectContractSupplementService 补充协议 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectContractTemplateMapper.java": {
        "entity": "ProjectContractTemplate",
        "table": "ydsz_project_contract_template",
        "title": "合同模板 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_contract_template}，存储合同模板。",
            "合同模板是合同正文的母版，定义了标准条款（交付/付款/验收/保密/违约等），签约时基于模板填充具体参数。",
        ],
        "index": [
            "uk_template_code — 模板编码唯一索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectContractTemplate 模板实体",
            "com.njydsz.project.server.service.ProjectContractTemplateService 模板 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectCustomerCreditMapper.java": {
        "entity": "ProjectCustomerCredit",
        "table": "ydsz_project_customer_credit",
        "title": "客户信用 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_customer_credit}，存储客户信用档案。",
            "信用档案是合同评审/垫资/赊销的关键依据：信用等级、授信额度、账期、历史回款表现等。",
        ],
        "index": [
            "uk_customer_id — 客户唯一索引（一个客户一份信用档案）",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectCustomerCredit 客户信用实体",
            "com.njydsz.project.server.service.ProjectCustomerCreditService 客户信用 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectExpenseMapper.java": {
        "entity": "ProjectExpense",
        "table": "ydsz_project_expense",
        "title": "项目费用 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_expense}，存储项目费用记录。",
            "费用指项目执行过程中除工时外的所有支出（差旅/采购/外协/招待等），是项目成本的重要组成部分。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
            "idx_expense_type — 费用类型过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectExpense 费用实体",
            "com.njydsz.project.server.service.ProjectExpenseService 费用 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectGateReviewMapper.java": {
        "entity": "ProjectGateReview",
        "table": "ydsz_project_gate_review",
        "title": "项目门审 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_gate_review}，存储项目门审记录。",
            "门审（Stage-Gate）将项目划分为多个阶段（启动/规划/执行/收尾），每个阶段结束设置评审门。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
            "idx_stage — 门审阶段过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectGateReview 门审实体",
            "com.njydsz.project.server.service.ProjectGateReviewService 门审 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectInitiationMapper.java": {
        "entity": "ProjectInitiation",
        "table": "ydsz_project_initiation",
        "title": "项目立项 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_initiation}，存储项目立项记录。",
            "立项是项目运营管理的入口业务，立项完成后才能进入合同/执行/结算等后续阶段。",
        ],
        "index": [
            "uk_project_code — 项目编号唯一索引",
            "idx_pm_id — 项目经理维度查询索引",
            "idx_tenant_status — 租户+状态复合索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectInitiation 立项实体",
            "com.njydsz.project.server.service.ProjectInitiationService 立项 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectInvoiceMapper.java": {
        "entity": "ProjectInvoice",
        "table": "ydsz_project_invoice",
        "title": "项目发票 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_invoice}，存储项目发票记录。",
            "发票是合同收入的合法凭据，开票后客户按票面金额回款，回款核销发票完成收入闭环。",
        ],
        "index": [
            "uk_invoice_no — 发票号唯一索引",
            "idx_contract_id — 合同维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectInvoice 发票实体",
            "com.njydsz.project.server.service.ProjectInvoiceService 发票 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectOpportunityFollowMapper.java": {
        "entity": "ProjectOpportunityFollow",
        "table": "ydsz_project_opportunity_follow",
        "title": "商机跟进记录 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_opportunity_follow}，存储商机的每次跟进记录。",
            "包括销售与客户的沟通/报价/演示/谈判的过程，用于复盘、知识沉淀、领导查阅。",
        ],
        "index": [
            "idx_opportunity_id — 商机维度查询索引",
            "idx_follow_at — 跟进时间排序索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectOpportunityFollow 跟进实体",
            "com.njydsz.project.server.service.ProjectOpportunityFollowService 跟进 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectOpportunityMapper.java": {
        "entity": "ProjectOpportunity",
        "table": "ydsz_project_opportunity",
        "title": "项目商机 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_opportunity}，存储销售阶段商机。",
            "商机是销售阶段的潜在项目，经过跟进/报价/谈判/合同签订后转为正式项目立项。",
        ],
        "index": [
            "uk_opportunity_code — 商机编号唯一索引",
            "idx_stage — 阶段过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectOpportunity 商机实体",
            "com.njydsz.project.server.service.ProjectOpportunityService 商机 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectPaymentMapper.java": {
        "entity": "ProjectPayment",
        "table": "ydsz_project_payment",
        "title": "项目回款 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_payment}，存储客户回款记录。",
            "回款是客户按合同条款实际支付的项目款项，核销后进入利润计算。",
        ],
        "index": [
            "idx_contract_id — 合同维度查询索引",
            "idx_payment_at — 回款时间排序索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectPayment 回款实体",
            "com.njydsz.project.server.service.ProjectPaymentService 回款 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectProfitSimulationMapper.java": {
        "entity": "ProjectProfitSimulation",
        "table": "ydsz_project_profit_simulation",
        "title": "项目利润模拟 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_profit_simulation}，存储利润模拟参数与结果。",
            "模拟是假设条件下的利润预测（报价前评估、预算评审、方案对比），与快照（事实）解耦。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectProfitSimulation 利润模拟实体",
            "com.njydsz.project.server.service.ProjectProfitSimulationService 利润模拟 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectProfitSnapshotMapper.java": {
        "entity": "ProjectProfitSnapshot",
        "table": "ydsz_project_profit_snapshot",
        "title": "项目利润快照 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_profit_snapshot}，存储按项目×月固化的利润快照。",
            "快照数据来源于日对账/工时/费用/收入汇总，用于利润分析、复盘、考核。",
        ],
        "index": [
            "uk_project_month — (项目+月份) 唯一索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectProfitSnapshot 利润快照实体",
            "com.njydsz.project.server.service.ProjectProfitSnapshotService 利润快照 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectReconcileDailyMapper.java": {
        "entity": "ProjectReconcileDaily",
        "table": "ydsz_project_reconcile_daily",
        "title": "项目日对账 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_reconcile_daily}，存储按项目×日对账的财务数据。",
            "日对账是项目财务对账的最小粒度，由调度器每日凌晨生成前一天的对账单。",
        ],
        "index": [
            "uk_project_date — (项目+日期) 唯一索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectReconcileDaily 日对账实体",
            "com.njydsz.project.server.service.ProjectReconcileDailyService 日对账 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/project/ProjectRevenueMapper.java": {
        "entity": "ProjectRevenue",
        "table": "ydsz_project_revenue",
        "title": "项目收入 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_project_revenue}，存储项目收入确认记录。",
            "项目收入来源于合同条款（里程碑/工时/开票），确认后进入利润计算。",
        ],
        "index": [
            "idx_project_id — 项目维度查询索引",
            "idx_revenue_type — 收入类型过滤索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectRevenue 收入实体",
            "com.njydsz.project.server.service.ProjectRevenueService 收入 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/rate/RateCardMapper.java": {
        "entity": "RateCard",
        "table": "ydsz_rate_card",
        "title": "客户计费卡 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rate_card}，存储对客户的计费卡。",
            "按客户×角色维度配置对外计费单价，用于合同报价、工时计费、收入确认。",
        ],
        "index": [
            "uk_customer_role — (客户+角色) 唯一索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.rate.RateCard 计费卡实体",
            "com.njydsz.project.server.service.RateCardService 计费卡 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/rate/RateInternalMapper.java": {
        "entity": "RateInternal",
        "table": "ydsz_rate_internal",
        "title": "内部人员费率 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_rate_internal}，存储内部人员（员工）费率档位。",
            "按角色/职级配置内部人力成本基准，用于利润分摊、EVM 计算、计费成本核算。",
        ],
        "index": [
            "uk_role_level — (角色+职级) 唯一索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.rate.RateInternal 内部费率实体",
            "com.njydsz.project.server.service.RateInternalService 内部费率 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/satisfaction/SatisfactionMapper.java": {
        "entity": "Satisfaction",
        "table": "ydsz_satisfaction",
        "title": "客户满意度 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_satisfaction}，存储客户对项目交付的满意度评价。",
            "项目终验后自动触发客户填写，用于项目复盘、团队绩效考核、客户分级。",
        ],
        "index": [
            "uk_project_id — 1 个项目只允许 1 份满意度",
        ],
        "see": [
            "com.njydsz.project.domain.entity.satisfaction.Satisfaction 满意度实体",
            "com.njydsz.project.server.service.SatisfactionService 满意度 Service",
        ],
    },
    "ydsz-project/ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/warranty/WarrantyMapper.java": {
        "entity": "Warranty",
        "table": "ydsz_warranty",
        "title": "质保金 Mapper",
        "desc": [
            "对应数据表 {@code ydsz_warranty}，存储项目质保金扣留/释放/扣款记录。",
            "质保金是合同结算时按比例扣留的部分，在质保期满后退还或扣款。",
        ],
        "index": [
            "idx_contract_id — 合同维度查询索引",
            "idx_release_at — 释放时间排序索引",
        ],
        "see": [
            "com.njydsz.project.domain.entity.warranty.Warranty 质保金实体",
            "com.njydsz.project.server.service.WarrantyService 质保金 Service",
        ],
    },
}


def render(meta: dict, rel: str) -> str:
    desc = "</p>\n * <p>".join(meta["desc"])
    index_items = "\n".join(f" *   <li>{idx}</li>" for idx in meta["index"])
    see_items = "\n".join(f" * @see {s}" for s in meta["see"])
    # extract package based on path
    parts = rel.replace("\\", "/").split("/")
    # parts: ydsz-project / ydsz-project-infra / src / main / java / com / njydsz / project / infra / mapper / {sub} / {Entity}Mapper.java
    sub = parts[-2]
    cls = parts[-1]
    return f"""package com.njydsz.project.infra.mapper.{sub};

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.project.domain.entity.{infer_entity_pkg(sub)}.{meta['entity']};
import org.apache.ibatis.annotations.Mapper;

/**
 * {meta['title']}
 *
 * <p>对应数据表 <code>{meta['table']}</code>。
 * <p>{desc}
 *
 * <p><b>主要索引：</b>
 * <ul>
{index_items}
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {{@code tenant_id}} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
{see_items}
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface {cls.replace('.java', '')} extends BaseMapper<{meta['entity']}> {{
}}
"""


def infer_entity_pkg(sub: str) -> str:
    return {
        "alert": "alert",
        "billable": "billable",
        "cost": "cost",
        "evm": "evm",
        "execution": "execution",
        "ops": "ops",
        "project": "project",
        "rate": "rate",
        "satisfaction": "satisfaction",
        "warranty": "warranty",
    }.get(sub, "project")


def main():
    base = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend")
    count = 0
    for rel, meta in MAPPERS.items():
        fpath = base / rel
        if not fpath.exists():
            print(f"SKIP (not found): {rel}")
            continue
        fpath.write_text(render(meta, rel), encoding="utf-8")
        print(f"OK: {rel}")
        count += 1
    print(f"\nTotal: {count} files updated")


if __name__ == "__main__":
    main()
