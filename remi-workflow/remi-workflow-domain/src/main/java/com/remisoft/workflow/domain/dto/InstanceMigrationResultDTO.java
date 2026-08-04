package com.remisoft.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 流程实例迁移结果 DTO
 *
 * <p>封装迁移执行的统计信息与逐实例明细，供前端展示迁移报告。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class InstanceMigrationResultDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 符合迁移条件的实例总数 */
    private int totalInstances;

    /** 成功迁移实例数 */
    private int migratedCount;

    /** 跳过实例数（如节点不兼容且无映射） */
    private int skippedCount;

    /** 失败实例数（迁移过程中异常） */
    private int failedCount;

    /** 逐实例迁移明细 */
    private List<MigrationDetail> details;

    /** 实际生效的节点映射（旧节点编码 -> 新节点编码） */
    private Map<String, String> nodeMappingApplied;

    /**
     * 单个实例的迁移明细
     *
     * @author remi-team
     * @since 1.0.0
     */
    @Data
    public static class MigrationDetail implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 实例 ID */
        private String instanceId;

        /** 实例标题 */
        private String instanceTitle;

        /** 迁移前节点编码 */
        private String oldNodeCode;

        /** 迁移后节点编码 */
        private String newNodeCode;

        /** 迁移状态：MIGRATED / SKIPPED / FAILED */
        private String status;

        /** 状态说明 / 跳过或失败原因 */
        private String reason;
    }
}
