package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 规则链画布 DO（P0-1）
 *
 * <p>对应 ydsz_rule_chain_graph 表，存储可视化编排画布的完整 JSON 内容。
 * 一条规则对应一条画布记录，画布版本号独立递增，与规则版本号解耦。
 *
 * <p><b>画布状态流转：</b>
 * <pre>
 *   DRAFT ──▶ PUBLISHED ──▶ ARCHIVED
 *     ▲          │
 *     └──────────┘（重新编辑回到 DRAFT）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_chain_graph")
public class RuleChainGraphDO extends MpBaseEntity<String> {

    /** 画布状态：草稿 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 画布状态：已发布 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 画布状态：已归档 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 关联规则编码（一对一） */
    private String ruleCode;

    /** 画布名称 */
    private String name;

    /** 画布描述 */
    private String description;

    /** 适用场景（与 RuleContext.scenario 对应） */
    private String scenario;

    /** 画布版本号（独立递增） */
    private Integer graphVersion;

    /** 画布状态：DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    /** 画布内容 JSON（包含 nodes/edges/viewport/metadata） */
    private String contentJson;

    // ==================== 领域行为方法 ====================

    /**
     * 判断画布是否为草稿状态。
     *
     * @return true 表示草稿
     */
    public boolean isDraft() {
        return STATUS_DRAFT.equals(status);
    }

    /**
     * 判断画布是否已发布。
     *
     * @return true 表示已发布
     */
    public boolean isPublished() {
        return STATUS_PUBLISHED.equals(status);
    }

    /**
     * 判断画布是否已归档。
     *
     * @return true 表示已归档
     */
    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }

    /**
     * 发布画布（版本号自增）。
     *
     * @throws IllegalStateException 当非草稿状态时
     */
    public void publish() {
        if (!isDraft()) {
            throw new IllegalStateException(
                    String.format("画布[%s]当前状态[%s]不允许发布", ruleCode, status));
        }
        this.status = STATUS_PUBLISHED;
        this.graphVersion = (graphVersion == null) ? 1 : graphVersion + 1;
    }

    /**
     * 归档画布。
     *
     * @throws IllegalStateException 当非已发布状态时
     */
    public void archive() {
        if (!isPublished()) {
            throw new IllegalStateException(
                    String.format("画布[%s]当前状态[%s]不允许归档", ruleCode, status));
        }
        this.status = STATUS_ARCHIVED;
    }

    /**
     * 重新编辑（从已发布回到草稿，创建新版本）。
     *
     * @throws IllegalStateException 当非已发布状态时
     */
    public void revertToDraft() {
        if (!isPublished() && !isArchived()) {
            throw new IllegalStateException(
                    String.format("画布[%s]当前状态[%s]不允许回退", ruleCode, status));
        }
        this.status = STATUS_DRAFT;
    }

    /**
     * 判断画布内容是否为空。
     *
     * @return true 表示内容为空
     */
    public boolean isContentEmpty() {
        return contentJson == null || contentJson.isBlank();
    }
}
