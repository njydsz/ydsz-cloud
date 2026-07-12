package com.njydsz.pmis.common.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 基础实体类
 *
 * <p>继承自 {@link BaseAuditEntity}，包含完整的审计字段、乐观锁版本号和逻辑删除标识。
 * 这是系统中最常用的实体基类，适用于大多数业务实体。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>开闭原则：对扩展开放，对修改关闭</li>
 *   <li>单一职责：每个字段有且只有一个职责</li>
 *   <li>依赖倒置：业务代码依赖抽象基类，不依赖具体实现</li>
 * </ul>
 *
 * <p><b>核心特性：</b>
 * <table>
 *   <tr><th>特性</th><th>字段</th><th>说明</th></tr>
 *   <tr><td>审计字段</td><td>createdBy/createdAt/updatedBy/updatedAt</td><td>追踪数据变更</td></tr>
 *   <tr><td>乐观锁</td><td>revision</td><td>并发控制，防止更新冲突</td></tr>
 *   <tr><td>逻辑删除</td><td>deleted</td><td>软删除，数据可恢复</td></tr>
 *   <tr><td>状态标识</td><td>status</td><td>业务状态启用/禁用</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class User extends BaseEntity<Long> {
 *
 *     private String username;
 *
 *     private String email;
 *
 *     private String phone;
 *
 *     private String status;
 * }
 * }</pre>
 *
 * <p><b>数据库表结构：</b>
 * <pre>{@code
 * CREATE TABLE sys_user (
 *     id BIGINT PRIMARY KEY COMMENT '主键ID',
 *     username VARCHAR(50) COMMENT '用户名',
 *     email VARCHAR(100) COMMENT '邮箱',
 *     phone VARCHAR(20) COMMENT '手机号',
 *     status INT DEFAULT 0 COMMENT '状态',
 *     created_by VARCHAR(64) COMMENT '创建人',
 *     created_at DATETIME COMMENT '创建时间',
 *     updated_by VARCHAR(64) COMMENT '更新人',
 *     updated_at DATETIME COMMENT '更新时间',
 *     revision INT DEFAULT 0 COMMENT '乐观锁版本',
 *     deleted INT DEFAULT 0 COMMENT '逻辑删除'
 * );
 * }</pre>
 *
 * @param <T> 主键ID类型，支持 Long、String、UUID 等
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseAuditEntity
 * @see BaseIdEntity
 * @see RootEntity
 *
 * <p><b>⚠ 重构规划：</b>当前继承链 {@code RootEntity → BaseIdEntity → BaseAuditEntity → BaseEntity} 共 4 层，增加理解成本。
 * 长期建议：扁平化为 2-3 层，或用 {@code @Embedded AuditInfo} 组合替代继承。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseEntity<T extends Serializable> extends BaseAuditEntity<T> implements RootEntity<T> {

    private static final long serialVersionUID = 1L;

    /**
     * 乐观锁版本号
     *
     * <p>用于并发控制，原理：
     * <ul>
     *   <li>每次更新时自动递增（+1）</li>
     *   <li>更新 SQL 包含 WHERE 条件：revision = oldRevision</li>
     *   <li>若影响行数为0，说明版本已变化，抛出乐观锁异常</li>
     * </ul>
     *
     * <p><b>配置方式：</b>
     * <pre>
     * // 方式1：字段注解（推荐）
     * &#64;Version
     * private Integer revision;
     *
     * // 方式2：配置方式（参考 ydsz-pmis-common-jdbc 模块）
     * remi:
     *   sql-intercept:
     *     optimistic-lock:
     *       enable: true
     *       revision-column: revision
     * </pre>
     *
     * @see Version
     */
    @Builder.Default
    private Integer revision = 0;

    /**
     * 逻辑删除标识
     *
     * <p>用于实现软删除，原理：
     * <ul>
     *   <li>删除操作变为 UPDATE 设置 deleted = 1</li>
     *   <li>查询操作自动添加 WHERE deleted = 0 条件</li>
     *   <li>数据可恢复，适合重要业务数据</li>
     * </ul>
     *
     * <p><b>配置方式：</b>
     * <pre>
     * // 方式1：字段注解（推荐）
     * &#64;TableLogic
     * private Integer deleted;
     *
     * // 方式2：配置方式（参考 ydsz-pmis-common-jdbc 模块）
     * remi:
     *   sql-intercept:
     *     logical-delete:
     *       enable: true
     *       deleted-column: deleted
     * </pre>
     *
     * @see TableLogic
     */
    @JsonIgnore
    private Integer deleted;

    /**
     * 状态标识
     *
     * <p>用于标识实体的业务状态：
     * <ul>
     *   <li>0 - 禁用/停用</li>
     *   <li>1 - 正常/启用</li>
     *   <li>其他值可根据业务需求自定义</li>
     * </ul>
     *
     * <p><b>字段映射：</b> status -> status
     */
    private Integer status;
}