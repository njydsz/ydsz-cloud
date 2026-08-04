package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典类型创建/更新 DTO
 *
 * <p>对应 {@code ydsz_dict_type} 表的写入参数。字典类型是「业务维度」的分类容器，
 * 如 {@code order_status}（订单状态）、{@code industry_type}（行业类型）、
 * {@code region_code}（行政区划）等。
 *
 * <p>一个字典类型下挂载多个字典项（{@link DictItemDTO}），构成<b>类型 → 项</b>两级结构，
 * 是前端下拉框、单选框、级联选择器的<b>唯一数据源</b>。
 *
 * <p><b>字段约束：</b>
 * <ul>
 *   <li>{@code typeCode} — 字典类型编码，租户内唯一，最长 64 字符，
 *       推荐使用「业务域_语义名」下划线命名（如 {@code workflow_node_type}）</li>
 *   <li>{@code typeName} — 字典类型名称（前端展示用），最长 128 字符</li>
 *   <li>{@code status} — 启用状态：{@code ENABLED / DISABLED}</li>
 * </ul>
 *
 * <p><b>唯一约束：</b>（{@code tenantId}, {@code typeCode}）二联唯一。
 *
 * <p><b>缓存策略：</b>字典类型通过 {@code ydsz:dict:type:{typeCode}} 缓存至 Redis；
 * 字典类型 + 字典项的「完整字典」通过 {@code ydsz:dict:full:{typeCode}} 整体缓存，
 * 避免每次下拉框渲染触发 N 次 SQL。写入时 {@code @CacheEvict} 主动失效。
 *
 * <p><b>设计原则：</b>字典类型<b>不级联</b>字典项的删除，删除类型前需先清空其下字典项。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DictItemDTO 字典项 DTO
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "字典类型创建/更新 DTO")
public class DictTypeDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 64, message = "字典类型编码长度不能超过64")
    @Schema(description = "字典类型编码")
    private String typeCode;

    @NotBlank(message = "字典类型名称不能为空")
    @Size(max = 128, message = "字典类型名称长度不能超过128")
    @Schema(description = "字典类型名称")
    private String typeName;

    @Schema(description = "字典类型业务说明")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
