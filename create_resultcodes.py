#!/usr/bin/env python3
"""Create 7 ResultCode enum files for business modules."""
import os

base = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

template = '''package {package}.enums;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;

/**
 * {module_cn}模块异常码枚举。
 *
 * <p>实现 {{@link ExceptionCode}} 接口，通过 {{@link ExceptionCodeRegistry}} 全局注册，
 * 支持 i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 * <ul>
{ranges}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public enum {className} implements ExceptionCode {{

{entries}

    /** 错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    {className}(String code, String key) {{
        this(code, key, 400);
    }}

    {className}(String code, String key, int httpStatus) {{
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }}

    static {{
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for ({className} c : values()) {{
            registryMap.put(c.getCode(), c);
        }}
        ExceptionCodeRegistry.register(registryMap);
    }}
}}
'''

files = []

# 1. ProjectResultCode
files.append({
    'path': os.path.join(base, r'ydsz-project\ydsz-project-domain\src\main\java\com\njydsz\project\domain\enums\ProjectResultCode.java'),
    'package': 'com.njydsz.project.domain',
    'className': 'ProjectResultCode',
    'module_cn': '项目管理',
    'ranges': ' *   <li>B40001-B40099 项目立项</li>\n *   <li>B40101-B40199 商机</li>\n *   <li>B40201-B40299 合同</li>\n *   <li>B41001-B41099 成本/采购/费用</li>\n *   <li>B41101-B41199 收入/开票/回款</li>\n *   <li>B42001-B42099 执行/WBS/工时</li>\n *   <li>B43001-B43099 EVM/费率</li>\n *   <li>B44001-B44099 满意度/质保/运维',
    'entries': '''    // ==================== B40001-B40099 项目立项 ====================
    PROJECT_NOT_FOUND("B40001", "project.not.found", 404),
    PROJECT_CODE_DUPLICATE("B40002", "project.code.duplicate"),
    PROJECT_STATUS_INVALID("B40003", "project.status.invalid"),

    // ==================== B40101-B40199 商机 ====================
    OPPORTUNITY_NOT_FOUND("B40101", "project.opportunity.not.found", 404),
    OPPORTUNITY_STATUS_INVALID("B40102", "project.opportunity.status.invalid"),

    // ==================== B40201-B40299 合同 ====================
    CONTRACT_NOT_FOUND("B40201", "project.contract.not.found", 404),
    CONTRACT_AMOUNT_EXCEED("B40202", "project.contract.amount.exceed"),
    CONTRACT_CODE_DUPLICATE("B40203", "project.contract.code.duplicate"),

    // ==================== B41001-B41099 成本/采购/费用 ====================
    COST_OVERFLOW("B41001", "project.cost.overflow"),
    COST_NOT_FOUND("B41002", "project.cost.not.found", 404),
    EXPENSE_NOT_FOUND("B41003", "project.expense.not.found", 404),
    PURCHASE_NOT_FOUND("B41004", "project.purchase.not.found", 404),

    // ==================== B41101-B41199 收入/开票/回款 ====================
    INVOICE_NOT_FOUND("B41101", "project.invoice.not.found", 404),
    PAYMENT_NOT_FOUND("B41102", "project.payment.not.found", 404),
    REVENUE_NOT_FOUND("B41103", "project.revenue.not.found", 404),

    // ==================== B42001-B42099 执行/WBS/工时 ====================
    WBS_TASK_NOT_FOUND("B42001", "project.wbs.task.not.found", 404),
    TIME_ENTRY_DUPLICATE("B42002", "project.time.entry.duplicate"),
    TIME_ENTRY_LOCKED("B42003", "project.time.entry.locked", 423),

    // ==================== B42101-B42199 交付/风险/结项 ====================
    DELIVERY_ITEM_NOT_FOUND("B42101", "project.delivery.item.not.found", 404),
    GATE_REVIEW_NOT_FOUND("B42102", "project.gate.review.not.found", 404),
    RISK_NOT_FOUND("B42103", "project.risk.not.found", 404),

    // ==================== B43001-B43099 EVM/费率 ====================
    EVM_MEASURE_NOT_FOUND("B43001", "project.evm.measure.not.found", 404),
    PROFIT_NEGATIVE("B43002", "project.profit.negative"),
    RATE_CARD_NOT_FOUND("B43003", "project.rate.card.not.found", 404),
    RATE_INTERNAL_NOT_FOUND("B43004", "project.rate.internal.not.found", 404),

    // ==================== B44001-B44099 满意度/质保/运维 ====================
    SATISFACTION_NOT_FOUND("B44001", "project.satisfaction.not.found", 404),
    WARRANTY_NOT_FOUND("B44002", "project.warranty.not.found", 404),
    OPS_TICKET_NOT_FOUND("B44003", "project.ops.ticket.not.found", 404)''',
})

# 2. SystemResultCode
files.append({
    'path': os.path.join(base, r'ydsz-system\ydsz-system-domain\src\main\java\com\njydsz\system\domain\enums\SystemResultCode.java'),
    'package': 'com.njydsz.system.domain',
    'className': 'SystemResultCode',
    'module_cn': '系统管理',
    'ranges': ' *   <li>B90001-B90099 系统配置</li>\n *   <li>B91001-B91099 字典类型/字典项</li>\n *   <li>B92001-B92099 系统变量</li>\n *   <li>B93001-B93099 应用信息',
    'entries': '''    // ==================== B90001-B90099 系统配置 ====================
    CONFIG_NOT_FOUND("B90001", "system.config.not.found", 404),
    CONFIG_KEY_DUPLICATE("B90002", "system.config.key.duplicate"),
    CONFIG_GROUP_INVALID("B90003", "system.config.group.invalid"),

    // ==================== B91001-B91099 字典 ====================
    DICT_TYPE_NOT_FOUND("B91001", "system.dict.type.not.found", 404),
    DICT_TYPE_CODE_DUPLICATE("B91002", "system.dict.type.code.duplicate"),
    DICT_ITEM_NOT_FOUND("B91003", "system.dict.item.not.found", 404),
    DICT_ITEM_CODE_DUPLICATE("B91004", "system.dict.item.code.duplicate"),
    DICT_VERSION_NOT_FOUND("B91005", "system.dict.version.not.found", 404),

    // ==================== B92001-B92099 系统变量 ====================
    VARIABLE_NOT_FOUND("B92001", "system.variable.not.found", 404),
    VARIABLE_KEY_DUPLICATE("B92002", "system.variable.key.duplicate"),

    // ==================== B93001-B93099 应用信息 ====================
    APP_INFO_NOT_FOUND("B93001", "system.app.info.not.found", 404),
    APP_KEY_DUPLICATE("B93002", "system.app.key.duplicate")''',
})

# 3. WorkflowResultCode
files.append({
    'path': os.path.join(base, r'ydsz-workflow\ydsz-workflow-domain\src\main\java\com\njydsz\workflow\domain\enums\WorkflowResultCode.java'),
    'package': 'com.njydsz.workflow.domain',
    'className': 'WorkflowResultCode',
    'module_cn': '工作流',
    'ranges': ' *   <li>B70001-B70099 流程模板/定义</li>\n *   <li>B71001-B71099 流程实例</li>\n *   <li>B72001-B72099 任务</li>\n *   <li>B73001-B73099 委托授权</li>\n *   <li>B74001-B74099 分类/评论/附件</li>\n *   <li>B75001-B75099 SLA/催办',
    'entries': '''    // ==================== B70001-B70099 流程模板/定义 ====================
    TEMPLATE_NOT_FOUND("B70001", "workflow.template.not.found", 404),
    TEMPLATE_CODE_DUPLICATE("B70002", "workflow.template.code.duplicate"),
    TEMPLATE_DEPLOYED_CANNOT_DELETE("B70003", "workflow.template.deployed.cannot.delete"),
    DEFINITION_NOT_FOUND("B70004", "workflow.definition.not.found", 404),
    BPMN_PARSE_ERROR("B70005", "workflow.bpmn.parse.error"),

    // ==================== B71001-B71099 流程实例 ====================
    INSTANCE_NOT_FOUND("B71001", "workflow.instance.not.found", 404),
    INSTANCE_STATUS_INVALID("B71002", "workflow.instance.status.invalid"),
    INSTANCE_ALREADY_FINISHED("B71003", "workflow.instance.already.finished"),

    // ==================== B72001-B72099 任务 ====================
    TASK_NOT_FOUND("B72001", "workflow.task.not.found", 404),
    TASK_NO_PERMISSION("B72002", "workflow.task.no.permission", 403),
    TASK_ALREADY_HANDLED("B72003", "workflow.task.already.handled"),
    TASK_APPROVER_DUPLICATE("B72004", "workflow.task.approver.duplicate"),

    // ==================== B73001-B73099 委托授权 ====================
    DELEGATE_AUTH_NOT_FOUND("B73001", "workflow.delegate.auth.not.found", 404),
    DELEGATE_AUTH_EXPIRED("B73002", "workflow.delegate.auth.expired"),

    // ==================== B74001-B74099 分类/评论/附件 ====================
    CATEGORY_NOT_FOUND("B74001", "workflow.category.not.found", 404),
    CATEGORY_CODE_DUPLICATE("B74002", "workflow.category.code.duplicate"),
    COMMENT_NOT_FOUND("B74003", "workflow.comment.not.found", 404),
    ATTACHMENT_NOT_FOUND("B74004", "workflow.attachment.not.found", 404),

    // ==================== B75001-B75099 SLA/催办 ====================
    SLA_NOT_FOUND("B75001", "workflow.sla.not.found", 404),
    SLA_OVERDUE("B75002", "workflow.sla.overdue"),
    URGE_TOO_FREQUENT("B75003", "workflow.urge.too.frequent", 429)''',
})

# 4. MessageResultCode
files.append({
    'path': os.path.join(base, r'ydsz-message\ydsz-message-domain\src\main\java\com\njydsz\message\domain\enums\MessageResultCode.java'),
    'package': 'com.njydsz.message.domain',
    'className': 'MessageResultCode',
    'module_cn': '消息中心',
    'ranges': ' *   <li>B91001-B91099 模板</li>\n *   <li>B91101-B91199 通知/消息日志</li>\n *   <li>B91201-B91299 渠道/路由</li>\n *   <li>B91301-B91399 批量/灰度</li>\n *   <li>B91401-B91499 退订/偏好/反馈',
    'entries': '''    // ==================== B91001-B91099 模板 ====================
    TEMPLATE_NOT_FOUND("B91001", "message.template.not.found", 404),
    TEMPLATE_CODE_DUPLICATE("B91002", "message.template.code.duplicate"),
    TEMPLATE_AUDIT_PENDING("B91003", "message.template.audit.pending"),
    TEMPLATE_AUDIT_REJECTED("B91004", "message.template.audit.rejected"),
    TEMPLATE_VARIABLE_MISSING("B91005", "message.template.variable.missing"),

    // ==================== B91101-B91199 通知/消息日志 ====================
    NOTIFICATION_NOT_FOUND("B91101", "message.notification.not.found", 404),
    MESSAGE_LOG_NOT_FOUND("B91102", "message.log.not.found", 404),
    MESSAGE_SEND_FAILED("B91103", "message.send.failed", 500),
    MESSAGE_RECALL_FAILED("B91104", "message.recall.failed"),

    // ==================== B91201-B91299 渠道/路由 ====================
    CHANNEL_NOT_CONFIGURED("B91201", "message.channel.not.configured"),
    CHANNEL_SEND_FAILED("B91202", "message.channel.send.failed", 500),
    ROUTE_RULE_NOT_FOUND("B91203", "message.route.rule.not.found", 404),
    CHANNEL_BLOCKED("B91204", "message.channel.blocked"),

    // ==================== B91301-B91399 批量/灰度 ====================
    BATCH_NOT_FOUND("B91301", "message.batch.not.found", 404),
    BATCH_ALREADY_RUNNING("B91302", "message.batch.already.running"),
    CANARY_NOT_FOUND("B91303", "message.canary.not.found", 404),

    // ==================== B91401-B91499 退订/偏好/反馈 ====================
    UNSUBSCRIBE_TOKEN_INVALID("B91401", "message.unsubscribe.token.invalid"),
    PREFERENCE_NOT_FOUND("B91402", "message.preference.not.found", 404),
    FEEDBACK_NOT_FOUND("B91403", "message.feedback.not.found", 404)''',
})

# 5. CronjobResultCode
files.append({
    'path': os.path.join(base, r'ydsz-cronjob\ydsz-cronjob-domain\src\main\java\com\njydsz\cronjob\domain\enums\CronjobResultCode.java'),
    'package': 'com.njydsz.cronjob.domain',
    'className': 'CronjobResultCode',
    'module_cn': '定时任务调度',
    'ranges': ' *   <li>B92001-B92099 任务</li>\n *   <li>B92101-B92199 DAG</li>\n *   <li>B92201-B92299 任务历史/版本</li>\n *   <li>B92301-B92399 告警规则/Webhook',
    'entries': '''    // ==================== B92001-B92099 任务 ====================
    JOB_NOT_FOUND("B92001", "cronjob.job.not.found", 404),
    JOB_CODE_DUPLICATE("B92002", "cronjob.job.code.duplicate"),
    JOB_ALREADY_RUNNING("B92003", "cronjob.job.already.running"),
    JOB_HANDLER_NOT_FOUND("B92004", "cronjob.job.handler.not.found"),
    JOB_CRON_INVALID("B92005", "cronjob.job.cron.invalid"),

    // ==================== B92101-B92199 DAG ====================
    DAG_NOT_FOUND("B92101", "cronjob.dag.not.found", 404),
    DAG_CYCLE_DETECTED("B92102", "cronjob.dag.cycle.detected"),
    DAG_INSTANCE_NOT_FOUND("B92103", "cronjob.dag.instance.not.found", 404),
    DAG_NODE_NOT_FOUND("B92104", "cronjob.dag.node.not.found", 404),

    // ==================== B92201-B92299 任务历史/版本 ====================
    JOB_HISTORY_NOT_FOUND("B92201", "cronjob.job.history.not.found", 404),
    JOB_VERSION_NOT_FOUND("B92202", "cronjob.job.version.not.found", 404),
    JOB_LOG_NOT_FOUND("B92203", "cronjob.job.log.not.found", 404),

    // ==================== B92301-B92399 告警规则/Webhook ====================
    ALERT_RULE_NOT_FOUND("B92301", "cronjob.alert.rule.not.found", 404),
    WEBHOOK_NOT_FOUND("B92302", "cronjob.webhook.not.found", 404),
    CONNECTOR_NOT_FOUND("B92303", "cronjob.connector.not.found", 404)''',
})

# 6. LiteruleResultCode
files.append({
    'path': os.path.join(base, r'ydsz-literule\ydsz-literule-domain\src\main\java\com\njydsz\literule\domain\enums\LiteruleResultCode.java'),
    'package': 'com.njydsz.literule.domain',
    'className': 'LiteruleResultCode',
    'module_cn': '轻量规则引擎',
    'ranges': ' *   <li>B93001-B93099 规则定义</li>\n *   <li>B93101-B93199 规则包/版本</li>\n *   <li>B93201-B93299 规则链/决策表</li>\n *   <li>B93301-B93399 测试用例/DSL',
    'entries': '''    // ==================== B93001-B93099 规则定义 ====================
    RULE_NOT_FOUND("B93001", "literule.rule.not.found", 404),
    RULE_CODE_DUPLICATE("B93002", "literule.rule.code.duplicate"),
    RULE_EXPRESSION_INVALID("B93003", "literule.rule.expression.invalid"),
    RULE_STATUS_INVALID("B93004", "literule.rule.status.invalid"),

    // ==================== B93101-B93199 规则包/版本 ====================
    RULE_PACK_NOT_FOUND("B93101", "literule.rule.pack.not.found", 404),
    RULE_VERSION_NOT_FOUND("B93102", "literule.rule.version.not.found", 404),
    RULE_PACK_ALREADY_INSTALLED("B93103", "literule.rule.pack.already.installed"),

    // ==================== B93201-B93299 规则链/决策表 ====================
    RULE_CHAIN_NOT_FOUND("B93201", "literule.rule.chain.not.found", 404),
    DECISION_TABLE_NOT_FOUND("B93202", "literule.decision.table.not.found", 404),
    AB_POLICY_NOT_FOUND("B93203", "literule.ab.policy.not.found", 404),

    // ==================== B93301-B93399 测试用例/DSL ====================
    TEST_CASE_NOT_FOUND("B93301", "literule.test.case.not.found", 404),
    DSL_PARSE_ERROR("B93302", "literule.dsl.parse.error"),
    VARIABLE_DEF_NOT_FOUND("B93303", "literule.variable.def.not.found", 404)''',
})

# 7. AgentResultCode
files.append({
    'path': os.path.join(base, r'ydsz-agent\ydsz-agent-domain\src\main\java\com\njydsz\agent\domain\enums\AgentResultCode.java'),
    'package': 'com.njydsz.agent.domain',
    'className': 'AgentResultCode',
    'module_cn': 'AI 智能体',
    'ranges': ' *   <li>B94001-B94099 Agent 定义/执行</li>\n *   <li>B94101-B94199 对话/记忆</li>\n *   <li>B94201-B94299 LLM 调用</li>\n *   <li>B94301-B94399 RAG/工具/Prompt',
    'entries': '''    // ==================== B94001-B94099 Agent 定义/执行 ====================
    AGENT_NOT_FOUND("B94001", "agent.not.found", 404),
    AGENT_CODE_DUPLICATE("B94002", "agent.code.duplicate"),
    AGENT_TYPE_NOT_SUPPORTED("B94003", "agent.type.not.supported"),
    AGENT_EXECUTION_FAILED("B94004", "agent.execution.failed", 500),
    AGENT_DAG_CYCLE_DETECTED("B94005", "agent.dag.cycle.detected"),

    // ==================== B94101-B94199 对话/记忆 ====================
    CONVERSATION_NOT_FOUND("B94101", "agent.conversation.not.found", 404),
    MEMORY_OVERFLOW("B94102", "agent.memory.overflow"),

    // ==================== B94201-B94299 LLM 调用 ====================
    LLM_CALL_FAILED("B94201", "agent.llm.call.failed", 502),
    LLM_RESPONSE_INVALID("B94202", "agent.llm.response.invalid"),
    LLM_TOKEN_EXCEEDED("B94203", "agent.llm.token.exceeded"),
    LLM_PROVIDER_NOT_CONFIGURED("B94204", "agent.llm.provider.not.configured"),

    // ==================== B94301-B94399 RAG/工具/Prompt ====================
    RAG_RETRIEVAL_FAILED("B94301", "agent.rag.retrieval.failed", 500),
    TOOL_NOT_FOUND("B94302", "agent.tool.not.found", 404),
    TOOL_EXECUTION_FAILED("B94303", "agent.tool.execution.failed", 500),
    PROMPT_TEMPLATE_NOT_FOUND("B94304", "agent.prompt.template.not.found", 404),
    PROMPT_TEMPLATE_DUPLICATE("B94305", "agent.prompt.template.duplicate"),
    GUARDRAIL_REJECTED("B94306", "agent.guardrail.rejected", 403)''',
})

for f in files:
    content = template.format(
        package=f['package'],
        className=f['className'],
        module_cn=f['module_cn'],
        ranges=f['ranges'],
        entries=f['entries']
    )
    dir_path = os.path.dirname(f['path'])
    os.makedirs(dir_path, exist_ok=True)
    with open(f['path'], 'w', encoding='utf-8') as fh:
        fh.write(content)
    print(f'Created: {f["path"]}')

print(f'\nDone: {len(files)} ResultCode enum files created')
