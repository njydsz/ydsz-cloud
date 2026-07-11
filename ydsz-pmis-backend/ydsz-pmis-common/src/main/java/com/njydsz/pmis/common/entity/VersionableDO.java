package com.njydsz.pmis.common.entity;

import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 可版本化实体基类（P1-6 架构优化）。
 *
 * <p>在 {@link BaseDO} 基础上增加乐观锁版本号字段，供需要乐观锁控制的实体继承。
 * 替代各模块实体中重复声明的 {@code @Version private Integer version} 字段。
 *
 * <p>字段映射：{@code version} → 数据库列 {@code version}，与现有 DDL 保持一致。
 *
 * <p>使用方式：
 * <pre>{@code
 * @TableName("pmis_flow_definition")
 * public class FlowDefinitionDO extends VersionableDO {
 *     // 只需定义业务字段，审计字段 + 乐观锁由父类统一管理
 * }
 * }</pre>
 *
 * <p><b>注意</b>：本类用于"乐观锁"场景（同一行更新时版本递增），不适用于"版本快照"场景
 * （每次变更插入新行的版本历史表，如 RuleVersionHistoryDO / DictVersionDO）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionableDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 乐观锁版本号（MyBatis-Plus @Version 自动递增，更新时 WHERE version = #{oldVersion}） */
    @Version
    private Integer version;
}
