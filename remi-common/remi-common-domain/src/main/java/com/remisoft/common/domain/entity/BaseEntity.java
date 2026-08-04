package com.remisoft.common.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.remisoft.common.json.annotation.JsonFormat;
import com.remisoft.common.json.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 领域基础实体（扁平化、纯领域，不依赖 MyBatis-Plus）。
 *
 * <p>系统统一的业务实体基类，包含主键、审计字段、乐观锁版本、逻辑删除、状态、租户标识等全部通用列。
 * 业务实体直接继承此类即可获得完整的通用字段，无需多层继承。
 *
 * <p><b>纯领域设计：</b>本类不携带任何 MyBatis-Plus 注解，持久化行为全部由 common-jdbc 的
 * SQL 拦截器在 SQL 层完成，实体本身保持领域纯净：
 * <ul>
 *   <li>审计字段填充（createdBy/createdAt/updatedBy/updatedAt）：由 {@code CombinedFieldFillInterceptor} 处理</li>
 *   <li>乐观锁（revision）：由 {@code OptimisticLockInterceptor} 处理</li>
 *   <li>逻辑删除（deleted）：由 {@code LogicalDeleteInterceptor} 处理</li>
 *   <li>列名映射：依赖 MP 全局 {@code map-underscore-to-camel-case}</li>
 *   <li>主键生成：依赖 MP 全局 {@code id-type=ASSIGN_ID}（雪花算法）</li>
 * </ul>
 *
 * <p><b>字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>列名</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，雪花算法生成</td></tr>
 *   <tr><td>createdBy</td><td>created_by</td><td>创建人ID，INSERT 自动填充</td></tr>
 *   <tr><td>createdAt</td><td>created_at</td><td>创建时间，INSERT 自动填充</td></tr>
 *   <tr><td>updatedBy</td><td>updated_by</td><td>更新人ID，INSERT/UPDATE 自动填充</td></tr>
 *   <tr><td>updatedAt</td><td>updated_at</td><td>更新时间，INSERT/UPDATE 自动填充</td></tr>
 *   <tr><td>revision</td><td>revision</td><td>乐观锁版本号，每次更新 +1</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标识（0=未删除，1=已删除）</td></tr>
 *   <tr><td>status</td><td>status</td><td>业务状态标识，子类可覆盖为枚举</td></tr>
 *   <tr><td>tenantId</td><td>tenant_id</td><td>租户ID，多租户隔离</td></tr>
 * </table>
 *
 * @param <T> 主键ID类型，支持 Long、String、UUID 等
 *
 * @author remi-team
 * @since 1.0.0
 * @since 1.4.0 扁平化：合并 BaseIdEntity/BaseAuditEntity，移除 domainEvents，纯领域无 MP 注解
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseEntity<T extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法，由 MP 全局 id-type=ASSIGN_ID 生成） */
    private T id;

    /** 创建人ID（INSERT 时由 CombinedFieldFillInterceptor 自动填充） */
    private String createdBy;

    /** 创建时间（INSERT 时由 CombinedFieldFillInterceptor 自动填充） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新人ID（INSERT/UPDATE 时由 CombinedFieldFillInterceptor 自动填充） */
    private String updatedBy;

    /** 更新时间（INSERT/UPDATE 时由 CombinedFieldFillInterceptor 自动填充） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号
     * <p>每次更新时自动递增（+1），由 {@code OptimisticLockInterceptor} 处理。
     */
    @Builder.Default
    private Integer revision = 0;

    /**
     * 逻辑删除标识
     * <p>0=未删除，1=已删除。删除操作转为 UPDATE，查询自动追加 WHERE deleted=0，
     * 由 {@code LogicalDeleteInterceptor} 处理。
     */
    @JsonIgnore
    private Integer deleted;

    /**
     * 业务状态标识
     * <p>子类可按需覆盖为具体业务状态枚举值，默认值为空。
     */
    private String status;

    /**
     * 租户ID
     * <p>多租户隔离字段，由租户拦截器自动注入 WHERE 条件和 INSERT 填充。
     * 单租户模式下默认值 "1"。对外 API 不暴露。
     */
    @JsonIgnore
    private String tenantId;
}
