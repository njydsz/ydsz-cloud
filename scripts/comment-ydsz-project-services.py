#!/usr/bin/env python3
"""Batch update ydsz-project service interfaces with comprehensive Javadoc."""
import pathlib

PROJECT_DIR = pathlib.Path(
    r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-project\ydsz-project-server\src\main\java\com\njydsz\project\server\service"
)

# Description uses triple-quote-safe form (no embedded " in source)
SERVICES = {
    "ProjectCustomerCreditService.java": {
        "entity": "ProjectCustomerCredit",
        "title": "客户信用 Service",
        "desc_lines": [
            "管理客户信用档案（{@code ydsz_project_customer_credit}）的评估、调整、查询。",
            "信用档案是合同评审/垫资/赊销的关键依据：客户的信用等级、授信额度、账期、历史回款表现等，",
            "决定了能否签订大额合同、能否赊销、是否需要预付款。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "信用评估：按历史回款/合同金额/账期计算信用分",
            "授信额度：客户在授信额度内可赊销",
            "信用预警：失信/严重逾期触发预警",
        ],
        "ext": [
            ("信用等级：", "AAA / AA / A / BBB / BB / B / C（数字越低信用越好）"),
            ("评估维度：", "回款及时率 / 平均账期 / 历史逾期次数 / 合同金额规模 / 合作年限"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectCustomerCredit 客户信用实体",
            "ProjectContractService 合同 Service(信用档案是合同评审依据)",
        ],
    },
    "ProjectContractTemplateService.java": {
        "entity": "ProjectContractTemplate",
        "title": "合同模板 Service",
        "desc_lines": [
            "管理合同模板（{@code ydsz_project_contract_template}）的维护与应用。",
            "合同模板是合同正文的母版，定义了标准条款（交付/付款/验收/保密/违约等），",
            "合同签约时基于模板填充具体参数，规避法律风险、提升签约效率。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "模板版本：模板变更保留历史版本",
            "模板应用：合同签约时引用模板生成正文",
        ],
        "ext": [
            ("模板类型：", "通用销售合同 / 外协采购合同 / 框架协议 / NDA / 补充协议"),
            ("模板变量：", "合同金额/项目名称/客户名称/账期等支持 ${var} 占位符"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectContractTemplate 合同模板实体",
            "ProjectContractService 合同 Service(签约时引用模板)",
        ],
    },
    "ProjectContractSupplementService.java": {
        "entity": "ProjectContractSupplement",
        "title": "合同补充协议 Service",
        "desc_lines": [
            "管理合同补充协议（{@code ydsz_project_contract_supplement}）的录入与查询。",
            "补充协议是对原合同的补充约定，用于在合同执行过程中调整部分条款（增项/账期/工作范围），",
            "与变更（{@link ProjectContractChangeService}）的区别：补充协议是双方新达成的一致；变更是对原条款的修改。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "关联主合同：补充协议依附于主合同存在",
            "金额叠加：补充协议金额计入主合同总额",
        ],
        "ext": [
            ("与变更区别：", "补充协议是增量条款，变更是原条款替换"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectContractSupplement 合同补充协议实体",
            "ProjectContractService 合同 Service(主合同关联)",
            "ProjectContractChangeService 合同变更 Service(兄弟概念)",
        ],
    },
    "ProjectContractChangeService.java": {
        "entity": "ProjectContractChange",
        "title": "合同变更 Service",
        "desc_lines": [
            "管理合同变更（{@code ydsz_project_contract_change}）的申请、审批、归档。",
            "合同变更是对原合同条款的修改（工作范围/金额/工期/账期），需经双方书面确认后生效，",
            "属于原条款替换性质。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "变更申请：发起合同变更，关联原合同",
            "变更审批：走 workflow 审批流程",
            "生效后：原合同相应字段被替换",
        ],
        "ext": [
            ("变更类型：", "范围变更 / 金额变更 / 工期变更 / 账期变更"),
            ("与补充协议区别：", "变更是原条款替换，补充协议是增量条款"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectContractChange 合同变更实体",
            "ProjectContractService 合同 Service(被变更的主合同)",
            "ProjectContractSupplementService 合同补充协议 Service(兄弟概念)",
        ],
    },
    "ProjectChangeService.java": {
        "entity": "ProjectChange",
        "title": "项目变更 Service",
        "desc_lines": [
            "管理项目变更（{@code ydsz_project_change}）的申请与审批。",
            "项目变更是项目执行过程中对项目计划/范围/目标的调整，区别于合同变更：",
            "<ul><li>合同变更：双方合同条款的修改</li><li>项目变更：项目内部范围/计划的调整（不一定涉及合同金额变化）</li></ul>",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "变更分类：范围变更 / 计划变更 / 资源变更",
            "变更审批：走 workflow 审批流程",
        ],
        "ext": [
            ("与合同变更区别：", "合同变更侧重钱/账期，项目变更侧重范围/计划/资源"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectChange 项目变更实体",
            "ProjectContractChangeService 合同变更 Service(联动关系)",
            "ProjectInitiationService 立项 Service(变更后项目状态联动)",
        ],
    },
    "ProjectBudgetItemService.java": {
        "entity": "ProjectBudgetItem",
        "title": "项目预算项 Service",
        "desc_lines": [
            "管理项目预算项（{@code ydsz_project_budget_item}）的录入与查询。",
            "预算项是项目预算的最小单元，按科目（工时/采购/差旅/外协/管理费等）维度编制，",
            "用于控制项目支出、对比实际成本、利润分析。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "预算项按科目：工时/采购/差旅/外协/招待/管理费/其他",
            "预算版本：每次预算调整生成新版本",
        ],
        "ext": [
            ("预算项字段：", "科目 / 预算金额 / 已用金额 / 剩余金额 / 责任人"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.project.ProjectBudgetItem 预算项实体",
            "ProjectInitiationService 立项 Service(立项时编制预算)",
            "CostAllocationService 成本分摊 Service(实际成本对比)",
        ],
    },
    "OpsTicketService.java": {
        "entity": "OpsTicket",
        "title": "运维工单 Service",
        "desc_lines": [
            "管理运维工单（{@code ydsz_ops_ticket}）的创建、分派、处理、关闭。",
            "运维工单是项目交付后客户对系统/服务的报修/咨询/请求入口，",
            "由客服/运维团队响应，按 SLA 跟踪处理进度。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "工单分派：按产品线/区域/技能分派给处理人",
            "SLA 跟踪：超时自动升级处理",
            "满意度回访：关闭后触发客户评价",
        ],
        "ext": [
            ("工单类型：", "故障 / 咨询 / 请求 / 投诉 / 建议"),
            ("优先级：", "P0(紧急) / P1(高) / P2(中) / P3(低)"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.ops.OpsTicket 运维工单实体",
            "SatisfactionService 满意度 Service(工单关闭后触发评价)",
        ],
    },
    "ExecutionWbsTaskService.java": {
        "entity": "ExecutionWbsTask",
        "title": "项目 WBS 任务 Service",
        "desc_lines": [
            "管理项目 WBS 任务（{@code ydsz_execution_wbs_task}）的分解、分配、跟踪。",
            "WBS（Work Breakdown Structure）是项目工作分解结构，将项目交付物逐层拆分为可执行的任务，",
            "用于项目计划/进度跟踪/工时填报/成本归集。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "任务分解：父子任务层级（项目→阶段→任务→子任务）",
            "责任人分配：每个任务指定责任人",
            "进度更新：实时更新完成度/状态",
        ],
        "ext": [
            ("任务状态：", "TODO / IN_PROGRESS / BLOCKED / REVIEW / DONE / CANCELLED"),
            ("任务字段：", "计划开始/结束/实际开始/结束/工时预估/实际工时/优先级"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionWbsTask WBS 任务实体",
            "ExecutionTimeEntryService 工时填报 Service(任务关联工时)",
            "ProjectInitiationService 立项 Service(WBS 挂载在项目下)",
        ],
    },
    "ExecutionTimeEntryService.java": {
        "entity": "ExecutionTimeEntry",
        "title": "工时填报 Service",
        "desc_lines": [
            "管理员工工时填报（{@code ydsz_execution_time_entry}）的录入与汇总。",
            "工时是项目成本的主要组成（人力成本占项目成本 60%+），按员工 × 日期 × 任务 维度填报，",
            "用于项目成本核算、计费、利润分析。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "按任务/员工/日期汇总：用于成本归集",
            "按月报工时：自动统计月工时/月计费",
            "审批流：超过 8h/天 或 40h/周 触发审批",
        ],
        "ext": [
            ("计费模式：", "正常 / 加班(1.5x) / 周末(2x) / 节假日(3x)"),
            ("取价规则：", "按任务的 {@link RateCardService} 客户计费 + {@link RateInternalService} 内部费率"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionTimeEntry 工时实体",
            "ExecutionWbsTaskService WBS 任务 Service(工时关联任务)",
            "ProjectProfitSnapshotService 利润快照 Service(工时是利润数据源)",
        ],
    },
    "ExecutionRiskService.java": {
        "entity": "ExecutionRisk",
        "title": "项目风险 Service",
        "desc_lines": [
            "管理项目风险（{@code ydsz_execution_risk}）的识别、跟踪、关闭。",
            "风险是项目执行中可能影响进度/质量/成本的不确定事件，按识别 → 评估 → 应对 → 跟踪 → 关闭流程管理。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "风险登记：识别风险并登记",
            "风险评估：按概率×影响评估风险等级",
            "应对措施：每条风险指定应对人和应对措施",
            "状态跟踪：OPEN / MITIGATING / CLOSED",
        ],
        "ext": [
            ("风险等级：", "HIGH / MEDIUM / LOW"),
            ("风险类型：", "技术 / 资源 / 进度 / 范围 / 质量 / 外部依赖"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionRisk 风险实体",
            "AlertDispatchService 告警分发 Service(高风险触发告警)",
        ],
    },
    "ExecutionDeliveryStandardService.java": {
        "entity": "ExecutionDeliveryStandard",
        "title": "交付标准 Service",
        "desc_lines": [
            "管理项目交付标准（{@code ydsz_execution_delivery_standard}）的维护。",
            "交付标准定义了项目交付物的质量门槛（如代码规范/测试覆盖率/文档完整性/性能指标），",
            "是验收环节的硬性指标，确保项目交付质量。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "按项目类型配置：不同类型项目（开发/集成/咨询）有不同标准",
            "标准版本：标准变更保留历史",
        ],
        "ext": [
            ("标准维度：", "代码规范 / 测试覆盖率 / 文档完整性 / 性能指标 / 安全性"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard 交付标准实体",
            "ExecutionDeliveryItemService 交付物 Service(交付物对照标准)",
        ],
    },
    "ExecutionDeliveryItemService.java": {
        "entity": "ExecutionDeliveryItem",
        "title": "项目交付物 Service",
        "desc_lines": [
            "管理项目交付物（{@code ydsz_execution_delivery_item}）的登记、确认、归档。",
            "交付物是项目产出的具体物件（代码/文档/数据/系统/培训等），按交付标准验收，",
            "验收通过后归档作为客户资产。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "交付物登记：按交付标准登记交付物",
            "客户验收：客户在系统中确认验收",
            "归档：验收通过后归档",
        ],
        "ext": [
            ("交付物类型：", "代码 / 文档 / 数据 / 系统 / 培训 / 其他"),
            ("验收状态：", "PENDING / DELIVERED / ACCEPTED / REJECTED / ARCHIVED"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem 交付物实体",
            "ExecutionDeliveryStandardService 交付标准 Service(对照标准)",
            "ExecutionClosureService 项目终验 Service(终验后归档)",
        ],
    },
    "ExecutionClosureService.java": {
        "entity": "ExecutionClosure",
        "title": "项目终验 Service",
        "desc_lines": [
            "管理项目终验（{@code ydsz_execution_closure}）的申请、评审、归档。",
            "项目终验是项目生命周期的收官阶段：交付物验收、文档归档、团队释放、客户签收、",
            "满意度触发，是项目关闭前的最后一道关卡。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "终验申请：项目经理发起终验申请",
            "交付物核对：所有交付物已验收",
            "文档归档：项目文档归入知识库",
            "满意度触发：终验通过触发客户填写满意度",
        ],
        "ext": [
            ("终验阶段：", "申请 → 资料准备 → 验收会 → 客户签收 → 项目关闭"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.execution.ExecutionClosure 终验实体",
            "ExecutionDeliveryItemService 交付物 Service(终验前确认)",
            "SatisfactionService 满意度 Service(终验后触发)",
            "ProjectInitiationService 立项 Service(终验后项目关闭)",
        ],
    },
    "EvmMeasureService.java": {
        "entity": "EvmMeasure",
        "title": "EVM 挣值测量 Service",
        "desc_lines": [
            "管理 EVM（Earned Value Management，挣值管理）（{@code ydsz_evm_measure}）测量记录。",
            "EVM 是项目管理的金标准度量方法，通过 PV（计划值）/ EV（挣值）/ AC（实际成本）",
            "三个核心指标计算 SPI（进度绩效指数）/ CPI（成本绩效指数），量化项目是否按时/是否超支。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "测量采集：定期（按周/按月）采集项目 EVM 指标",
            "趋势分析：连续测量形成趋势图",
            "异常预警：CPI<0.8 或 SPI<0.8 触发预警",
        ],
        "ext": [
            ("核心指标：", "PV（计划值） / EV（挣值） / AC（实际成本）"),
            ("绩效指数：", "SPI = EV/PV（>1 提前, <1 滞后）/ CPI = EV/AC（>1 节省, <1 超支）"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.evm.EvmMeasure EVM 测量实体",
            "AlertDispatchService 告警分发 Service(EVM 异常触发告警)",
            "ProjectInitiationService 立项 Service(按项目维度汇总)",
        ],
    },
    "CostPurchaseService.java": {
        "entity": "CostPurchase",
        "title": "采购成本 Service",
        "desc_lines": [
            "管理项目采购成本（{@code ydsz_cost_purchase}）的登记、审批、入账。",
            "采购成本是项目非工时成本的主要组成（外协/采购/外包等），按订单/合同/验收节点分摊到项目，",
            "计入项目成本与利润。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "采购订单：关联采购订单/采购合同",
            "验收入库：采购到货验收后入账",
            "分摊到项目：按项目维度分摊",
        ],
        "ext": [
            ("采购类型：", "硬件采购 / 软件采购 / 外协服务 / 外包人力"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.cost.CostPurchase 采购成本实体",
            "ProjectExpenseService 项目费用 Service(费用与采购成本互补)",
            "CostAllocationService 成本分摊 Service(分摊到项目)",
        ],
    },
    "CostAllocationService.java": {
        "entity": "CostAllocation",
        "title": "成本分摊 Service",
        "desc_lines": [
            "管理成本分摊（{@code ydsz_cost_allocation}）规则的配置与执行。",
            "成本分摊是把公共成本按规则分摊到具体项目/部门的过程，常用于：",
            "<ul><li>跨项目使用的人员费用</li><li>跨部门使用的平台/工具费用</li><li>管理费用的二次分摊</li></ul>",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "分摊规则：按比例/按工时/按收入 等多种分摊方式",
            "分摊执行：定时执行分摊并写入项目",
        ],
        "ext": [
            ("分摊方式：", "按工时比例 / 按收入比例 / 按人数比例 / 固定比例"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.cost.CostAllocation 成本分摊实体",
            "ExecutionTimeEntryService 工时 Service(按工时分摊的数据源)",
            "ProjectProfitSnapshotService 利润快照 Service(分摊后计入利润)",
        ],
    },
    "BillableUtilizationSnapshotService.java": {
        "entity": "BillableUtilizationSnapshot",
        "title": "可计费率快照 Service",
        "desc_lines": [
            "管理可计费率快照（{@code ydsz_billable_utilization_snapshot}）的生成与查询。",
            "可计费率 = 计费工时 / 总工时，反映员工/部门时间投入在有收入项目上的比例，",
            "是衡量团队产能与项目健康度的核心指标。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "按员工/部门/项目维度汇总",
            "快照化：按月/季度固化,避免数据漂移",
        ],
        "ext": [
            ("指标定义：", "可计费率 = ∑(计费工时) / ∑(总工时)"),
            ("健康阈值：", "可计费率 ≥ 75% 为健康, < 60% 触发预警"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot 可计费率快照实体",
            "ExecutionTimeEntryService 工时 Service(数据源)",
        ],
    },
    "AlertDispatchService.java": {
        "entity": "AlertDispatch",
        "title": "告警分发 Service",
        "desc_lines": [
            "管理告警分发（{@code ydsz_alert_dispatch}）的订阅、推送、确认。",
            "告警是项目/系统异常的信号通知，按订阅规则推送给责任人（IM/邮件/短信），",
            "支持告警确认/转派/关闭等生命周期管理。",
        ],
        "core": [
            "CRUD：getById / page / save / updateById / removeById",
            "告警订阅：按规则订阅告警（如 EVM CPI<0.8 触发）",
            "告警推送：多通道推送(IM/邮件/短信)",
            "告警处理：确认/转派/关闭",
        ],
        "ext": [
            ("告警级别：", "CRITICAL / MAJOR / MINOR / INFO"),
            ("推送通道：", "企业微信 / 邮件 / 短信 / 系统站内信"),
        ],
        "see": [
            "com.njydsz.project.domain.entity.alert.AlertDispatch 告警实体",
            "EvmMeasureService EVM Service(EVM 异常触发告警)",
            "ExecutionRiskService 风险 Service(高风险触发告警)",
        ],
    },
}


def infer_pkg(entity: str) -> str:
    if entity.startswith("Project"):
        return "project"
    if entity.startswith("Rate"):
        return "rate"
    if entity.startswith("Satisfaction"):
        return "satisfaction"
    if entity.startswith("Warranty"):
        return "warranty"
    if entity.startswith("Execution"):
        return "execution"
    if entity.startswith("Evm"):
        return "evm"
    if entity.startswith("Cost"):
        return "cost"
    if entity.startswith("Billable"):
        return "billable"
    if entity.startswith("Alert"):
        return "alert"
    if entity.startswith("Ops"):
        return "ops"
    return "project"


def render(meta: dict, fname: str) -> str:
    desc = "</p>\n * <p>".join(meta["desc_lines"])
    core_items = "\n".join(f" *   <li><b>{c}</b></li>" for c in meta["core"])
    ext_items = "\n".join(
        f" * <p><b>{k}</b>{v}。" for k, v in meta["ext"]
    )
    see_items = "\n".join(f" * @see {s}" for s in meta["see"])
    cls = fname.replace(".java", "")
    return f"""package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.{infer_pkg(meta['entity'])}.{meta['entity']};
/**
 * {meta['title']}
 *
 * <p>{desc}
 *
 * <p><b>核心职责：</b>
 * <ul>
{core_items}
 * </ul>
 *
{ext_items}
 *
 * <p><b>事务：</b>所有写操作开启 {{@code @Transactional(rollbackFor = Exception.class)}}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
{see_items}
 */
public interface {cls} {{
    {meta['entity']} getById(String id);
    IPage<{meta['entity']}> page(int pageNum, int pageSize);
    boolean save({meta['entity']} entity);
    boolean updateById({meta['entity']} entity);
    boolean removeById(String id);
}}
"""


if __name__ == "__main__":
    for fname, meta in SERVICES.items():
        fpath = PROJECT_DIR / fname
        if not fpath.exists():
            print(f"SKIP (not found): {fname}")
            continue
        fpath.write_text(render(meta, fname), encoding="utf-8")
        print(f"OK: {fname}")
