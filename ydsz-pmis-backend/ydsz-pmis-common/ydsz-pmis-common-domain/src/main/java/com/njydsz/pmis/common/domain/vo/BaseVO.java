package com.njydsz.pmis.common.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.pmis.common.json.annotation.JsonFormat;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 视图对象基类
 *
 * <p>字段命名与 {@link com.njydsz.pmis.common.domain.entity.BaseAuditEntity} 保持一致：
 * <ul>
 *   <li>createdAt：创建时间，对应数据。created_at</li>
 *   <li>updatedAt：更新时间，对应数据。updated_at</li>
 * </ul>
 *
 * <p><b>通用字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>String</td><td>主键ID</td></tr>
 *   <tr><td>createdAt</td><td>LocalDateTime</td><td>创建时间</td></tr>
 *   <tr><td>updatedAt</td><td>LocalDateTime</td><td>更新时间</td></tr>
 *   <tr><td>createdBy</td><td>String</td><td>创建人ID</td></tr>
 *   <tr><td>createdByName</td><td>String</td><td>创建人姓。</td></tr>
 *   <tr><td>updatedBy</td><td>String</td><td>更新人ID</td></tr>
 *   <tr><td>updatedByName</td><td>String</td><td>更新人姓。</td></tr>
 *   <tr><td>status</td><td>Integer</td><td>状态标识</td></tr>
 *   <tr><td>statusName</td><td>String</td><td>状态名。</td></tr>
 *   <tr><td>remark</td><td>String</td><td>备注信息</td></tr>
 * </table>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class BaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     *
     * <p>实体的唯一标识，通常由雪花算法生成。
     */
    private String id;

    /**
     * 创建时间
     *
     * <p>与 {@link com.njydsz.pmis.common.domain.entity.BaseAuditEntity#getCreatedAt()} 命名对齐。
     * JSON 序列化时格式化为 "yyyy-MM-dd HH:mm:ss"。
     */
    @JsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     *
     * <p>与 {@link com.njydsz.pmis.common.domain.entity.BaseAuditEntity#getUpdatedAt()} 命名对齐。
     * JSON 序列化时格式化为 "yyyy-MM-dd HH:mm:ss"。
     */
    @JsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     *
     * <p>创建该记录的用户ID，用于追溯数据来源。
     */
    private String createdBy;

    /**
     * 创建人姓。
     *
     * <p>创建该记录的用户姓名，提供更友好的显示信息。
     * 。createdBy 配合使用，避免前端二次查询用户信息。
     */
    private String createdByName;

    /**
     * 更新人ID
     *
     * <p>最后更新该记录的用户ID。
     */
    private String updatedBy;

    /**
     * 更新人姓。
     *
     * <p>最后更新该记录的用户姓名。
     * 。updatedBy 配合使用。
     */
    private String updatedByName;

    /**
     * 状态标识
     *
     * <p>用于标识记录的业务状态：
     * <ul>
     *   <li>0 - 禁用/停用</li>
     *   <li>1 - 正常/启用</li>
     *   <li>其他。- 业务自定义状态</li>
     * </ul>
     */
    private Integer status;

    /**
     * 状态名。
     *
     * <p>状态的可读名称，如"启用"。禁用"等。
     * 。status 配合使用，避免前端维护状态映射。
     */
    private String statusName;

    /**
     * 备注信息
     *
     * <p>用于存储额外的说明信息。
     */
    private String remark;

    /**
     * 版本。
     *
     * <p>乐观锁版本号，用于并发控制。
     * 与实体中。revision 字段对应。
     */
    private Integer version;

    /**
     * 是否删除
     *
     * <p>逻辑删除标识。
     * <ul>
     *   <li>0 - 未删。</li>
     *   <li>1 - 已删。</li>
     * </ul>
     */
    private Integer deleted;
}
