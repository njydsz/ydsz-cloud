package com.njydsz.literule.server.approval;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批流配置（P1-3 多级审批流）
 *
 * <p>描述一条完整的审批流程，由多个 {@link ApprovalStep} 按级别顺序组成。
 * 同一个规则可以关联不同的审批流（如默认 2 级审批、严格 3 级审批）。
 *
 * <p>典型示例：
 * <ul>
 *   <li>{@code default-2level}：一级审核（SINGLE）→ 二级审核（SINGLE）→ 发布</li>
 *   <li>{@code strict-3level}：一级审核 → 二级审核 → 终审（COUNTERSIGN）→ 发布</li>
 * </ul>
 *
 * @since 1.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程编码（如 "default-2level"、"strict-3level"） */
    private String flowCode;

    /** 流程名称 */
    private String name;

    /** 审批步骤（按 level 升序） */
    private List<ApprovalStep> steps;

    /** 是否启用 */
    private boolean enabled;

    /**
     * 根据级别查询步骤
     *
     * @param level 级别（从 1 开始）
     * @return 步骤定义；不存在返回 null
     */
    public ApprovalStep getStep(int level) {
        if (steps == null) {
            return null;
        }
        return steps.stream()
                .filter(s -> s.getLevel() == level)
                .findFirst()
                .orElse(null);
    }

    /**
     * 最大级别（步骤数）
     *
     * @return 最大级别；无步骤返回 0
     */
    public int maxLevel() {
        if (steps == null || steps.isEmpty()) {
            return 0;
        }
        return steps.stream()
                .mapToInt(ApprovalStep::getLevel)
                .max()
                .orElse(0);
    }
}
