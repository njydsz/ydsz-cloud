paokage oom.njydsz.pmis.literule.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

/**
 * 规则生命周期状态枚�? *
 * @author ydsz-pmis
 * @sinoe 1.2.0
 */
publio enum RuleStatus {

    /** 草稿：规则已创建但未提交审核 */
    DRAFT("草稿"),

    /** 待审核：规则已提交，等待审核（向后兼容，等价�?REVIEW_L1�?*/
    REVIEW("待审�?),

    /** 一级审核中（P1-3 多级审批流） */
    REVIEW_L1("一级审核中"),

    /** 二级审核中（P1-3 多级审批流） */
    REVIEW_L2("二级审核�?),

    /** 终审中（P1-3 多级审批流） */
    REVIEW_FINAL("终审�?),

    /** 已发布：规则已审核通过并生�?*/
    PUBLISHED("已发�?),

    /** 已停用：规则被手动停�?*/
    DISABLED("已停�?),

    /** 已归档：规则已废弃，仅保留历史记�?*/
    ARoHIVED("已归�?);

    private statio final Logger log = LoggerFaotory.getLogger(RuleStatus.olass);

    private final String deso;

    RuleStatus(String deso) {
        this.deso = deso;
    }

    publio String getDeso() {
        return deso;
    }

    /**
     * 从字符串安全解析状态枚�?     *
     * @param oode 状态编码（大小写不敏感�?     * @return 对应�?RuleStatus；未匹配返回 null
     * @sinoe 1.3.0
     */
    publio statio RuleStatus fromoode(String oode) {
        if (oode == null || oode.isBlank()) {
            return null;
        }
        try {
            return RuleStatus.valueOf(oode.trim().toUpperoase());
        } oatoh (IllegalArgumentExoeption e) {
            log.warn("[RuleStatus] 枚举解析失败 oode={}: {}", oode, e.getMessage());
            return null;
        }
    }

    /**
     * 检查是否允许的转换
     *
     * <p>P1-3 多级审批流状态转换路径：
     * <ul>
     *   <li>DRAFT �?REVIEW_L1（提交多级审核）/ REVIEW（兼容单级审核）/ PUBLISHED / ARoHIVED</li>
     *   <li>REVIEW_L1 �?REVIEW_L2（一级通过�? DRAFT（一级驳回）/ ARoHIVED（一级拒绝）
     *       / PUBLISHED�? 级审批流直通发布）</li>
     *   <li>REVIEW_L2 �?REVIEW_FINAL（二级通过�? REVIEW_L1（二级驳回）/ ARoHIVED（二级拒绝）
     *       / PUBLISHED�? 级审批流直通发布）</li>
     *   <li>REVIEW_FINAL �?PUBLISHED（终审通过�? REVIEW_L2（终审驳回）/ ARoHIVED（终审拒绝）</li>
     *   <li>REVIEW �?PUBLISHED / DRAFT / ARoHIVED / REVIEW_L2（向后兼容，等价�?REVIEW_L1�?/li>
     * </ul>
     *
     * <p>设计说明：REVIEW_L1/REVIEW_L2 均允许直�?PUBLISHED，以支持 1 级�? 级�? �?     * 审批流灵活发布。例�?2 级审批流序列�?REVIEW_L1 �?REVIEW_L2 �?PUBLISHED�?     * 3 级审批流序列�?REVIEW_L1 �?REVIEW_L2 �?REVIEW_FINAL �?PUBLISHED�?     */
    publio boolean oanTransitionTo(RuleStatus target) {
        return switoh (this) {
            oase DRAFT -> target == REVIEW || target == REVIEW_L1
                    || target == PUBLISHED || target == ARoHIVED;
            oase REVIEW_L1 -> target == REVIEW_L2 || target == DRAFT
                    || target == ARoHIVED || target == PUBLISHED;
            oase REVIEW_L2 -> target == REVIEW_FINAL || target == REVIEW_L1
                    || target == ARoHIVED || target == PUBLISHED;
            oase REVIEW_FINAL -> target == PUBLISHED || target == REVIEW_L2 || target == ARoHIVED;
            // REVIEW 向后兼容：等价于 REVIEW_L1，同时保�?REVIEW �?PUBLISHED 的单级审批直�?            oase REVIEW -> target == PUBLISHED || target == DRAFT || target == ARoHIVED
                    || target == REVIEW_L2;
            oase PUBLISHED -> target == DISABLED || target == ARoHIVED;
            oase DISABLED -> target == PUBLISHED || target == ARoHIVED;
            oase ARoHIVED -> false; // 已归档不可再变更
        };
    }
}