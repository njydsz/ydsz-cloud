package com.njydsz.pmis.common.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.pmis.common.json.annotation.JsonField;
import com.njydsz.pmis.common.json.annotation.JsonFormat;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 视图对象基类
 *
 * <p>用于前端展示的数据对象基类，包含通用的审计字段和状态信息。
 * 逻辑删除标识通过 {@code @JsonField(ignore = true)} 对前端透明。
 *
 * <p><b>通用字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>String</td><td>主键ID</td></tr>
 *   <tr><td>createdAt</td><td>LocalDateTime</td><td>创建时间</td></tr>
 *   <tr><td>updatedAt</td><td>LocalDateTime</td><td>更新时间</td></tr>
 *   <tr><td>createdBy</td><td>String</td><td>创建人ID</td></tr>
 *   <tr><td>createdByName</td><td>String</td><td>创建人姓名</td></tr>
 *   <tr><td>updatedBy</td><td>String</td><td>更新人ID</td></tr>
 *   <tr><td>updatedByName</td><td>String</td><td>更新人姓名</td></tr>
 *   <tr><td>status</td><td>Integer</td><td>状态标识</td></tr>
 *   <tr><td>statusName</td><td>String</td><td>状态名称</td></tr>
 *   <tr><td>remark</td><td>String</td><td>备注信息</td></tr>
 *   <tr><td>version</td><td>Integer</td><td>乐观锁版本</td></tr>
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
     * <p>JSON 序列化时格式化为 "yyyy-MM-dd HH:mm:ss"。
     */
    @JsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     *
     * <p>JSON 序列化时格式化为 "yyyy-MM-dd HH:mm:ss"。
     */
    @JsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    private String createdBy;

    /**
     * 创建人姓名
     */
    private String createdByName;

    /**
     * 更新人ID
     */
    private String updatedBy;

    /**
     * 更新人姓名
     */
    private String updatedByName;

    /**
     * 状态标识
     *
     * <p>用于标识记录的业务状态：
     * <ul>
     *   <li>0 - 禁用/停用</li>
     *   <li>1 - 正常/启用</li>
     *   <li>其他 - 业务自定义状态</li>
     * </ul>
     */
    private Integer status;

    /**
     * 状态名称
     *
     * <p>状态的可读名称，如"启用"、"禁用"等。
     */
    private String statusName;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 乐观锁版本
     *
     * <p>与实体中 {@code revision} 字段对应，用于并发控制。
     * 保留 {@code version} 命名以兼容前端 API 契约。
     */
    private Integer version;

    /**
     * 逻辑删除标识（对前端透明）
     *
     * <p>JSON 序列化时忽略此字段，不返回给前端。
     */
    @JsonField(ignore = true)
    private Integer deleted;

}
