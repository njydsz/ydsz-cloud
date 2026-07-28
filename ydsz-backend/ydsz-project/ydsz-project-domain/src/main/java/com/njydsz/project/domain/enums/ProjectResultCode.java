package com.njydsz.project.domain.enums;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;

/**
 * 项目管理模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，通过 {@link ExceptionCodeRegistry} 全局注册，
 * 支持 i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 * <ul>
 *   <li>B40001-B40099 项目立项</li>
 *   <li>B40101-B40199 商机</li>
 *   <li>B40201-B40299 合同</li>
 *   <li>B41001-B41099 成本/采购/费用</li>
 *   <li>B41101-B41199 收入/开票/回款</li>
 *   <li>B42001-B42099 执行/WBS/工时</li>
 *   <li>B43001-B43099 EVM/费率</li>
 *   <li>B44001-B44099 满意度/质保/运维
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public enum ProjectResultCode implements ExceptionCode {

    // ==================== B40001-B40099 项目立项 ====================
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
    OPS_TICKET_NOT_FOUND("B44003", "project.ops.ticket.not.found", 404)

    /** 错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    ProjectResultCode(String code, String key) {
        this(code, key, 400);
    }

    ProjectResultCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    static {
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (ProjectResultCode c : values()) {
            registryMap.put(c.getCode(), c);
        }
        ExceptionCodeRegistry.register(registryMap);
    }
}
