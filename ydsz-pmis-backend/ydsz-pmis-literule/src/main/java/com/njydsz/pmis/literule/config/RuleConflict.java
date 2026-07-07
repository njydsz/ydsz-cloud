package com.njydsz.pmis.literule.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 规则冲突检测结果
 *
 * <p>由 {@link RuleConflictDetector} 在规则保存前检测，描述新规则与现有规则的潜在冲突。
 *
 * <p>冲突级别：
 * <ul>
 *   <li>{@link Level#WARN}：潜在冲突，默认仅记录日志不阻塞保存</li>
 *   <li>{@link Level#ERROR}：确定性冲突，默认阻塞保存（可通过配置关闭）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleConflict implements Serializable {

    private static final String serialVersionUID = "1";

    /** 冲突级别 */
    public enum Level {
        /** 警告：潜在冲突，不阻塞保存 */
        WARN,
        /** 错误：确定性冲突，默认阻塞保存 */
        ERROR
    }

    /** 冲突类型 */
    public enum Type {
        /** 条件表达式完全相同（可能重复定义） */
        IDENTICAL_CONDITION,
        /** 条件相同但严重度不同（语义冲突） */
        CONTRADICTORY_SEVERITY,
        /** 同类别下名称相同但逻辑不同（命名冲突） */
        NAME_COLLISION,
        /**
         * 条件范围重叠（1.5.0 起）
         *
         * <p>两条规则的条件在相同变量上存在范围交集，
         * 可能导致同一事实同时命中多条规则（除非属于同一互斥组）。
         */
        CONDITION_OVERLAP
    }

    /** 冲突类型 */
    private Type type;

    /** 冲突级别 */
    private Level level;

    /** 新规则编码 */
    private String newRuleCode;

    /** 冲突的已有规则编码 */
    private String conflictingRuleCode;

    /** 冲突描述 */
    private String description;
}
