package com.njydsz.pmis.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 可版本化实体基类（P2-1 架构优化）。
 *
 * <p>在 {@link BaseDO} 基础上增加乐观锁版本号字段，供需要版本管理的实体继承。
 * 替代各模块各自定义版本表（RuleVersionHistoryDO / DictVersionDO / MsgTemplateVersionDO /
 * JobDagVersionDO / AgentVersionDO）中重复的版本号字段。
 *
 * <p>使用方式：
 * <pre>{@code
 * @TableName("pmis_rule_def")
 * public class RuleDefDO extends VersionableDO {
 *     // 只需定义业务字段，version / versionLabel / versionComment 由父类管理
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionableDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 乐观锁版本号（MyBatis-Plus @Version 自动递增） */
    @Version
    private Integer lockVersion;

    /** 语义化版本标签（如 v1.0.0），用于展示和版本历史 */
    @TableField("version_label")
    private String versionLabel;

    /** 版本备注（变更说明） */
    @TableField("version_comment")
    private String versionComment;
}
