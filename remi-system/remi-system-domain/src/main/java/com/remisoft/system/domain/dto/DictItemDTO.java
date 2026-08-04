package com.remisoft.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典项创建/更新 DTO
 *
 * <p>对应 {@code remi_dict_item} 表的写入参数。字典项是「字典类型」下的具体条目，
 * 通过 {@code typeCode} 外键关联 {@link DictTypeDTO}，形成「类型 → 项」两级结构。
 *
 * <p><b>字段约束：</b>
 * <ul>
 *   <li>{@code typeCode} — 所属字典类型编码，最长 64 字符</li>
 *   <li>{@code itemCode} — 字典项编码，类型内唯一，最长 64 字符</li>
 *   <li>{@code itemValue} — 字典项展示值（前端下拉框 / 单选框显示文本），最长 255 字符</li>
 *   <li>{@code sortOrder} — 同类型内排序号（升序）</li>
 *   <li>{@code parentId} — 父级字典项 ID（{@code 0} 表示根），支持树形结构（如「省 / 市 / 区县」）</li>
 *   <li>{@code extJson} — 扩展属性 JSON（如地区码、颜色值、图标等）</li>
 *   <li>{@code status} — 启用状态：{@code ENABLED / DISABLED}</li>
 * </ul>
 *
 * <p><b>唯一约束：</b>（{@code tenantId}, {@code typeCode}, {@code itemCode}）三联唯一。
 *
 * <p><b>缓存策略：</b>字典项通过 {@code remi:dict:item:{typeCode}} 缓存至 Redis，
 * 写入时 {@code @CacheEvict} 主动失效。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see DictTypeDTO 字典类型 DTO
 * @see com.remisoft.system.domain.entity.DictItem 字典项实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "字典项创建/更新 DTO")
public class DictItemDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 64, message = "字典类型编码长度不能超过64")
    @Schema(description = "所属字典类型编码")
    private String typeCode;

    @NotBlank(message = "字典项编码不能为空")
    @Size(max = 64, message = "字典项编码长度不能超过64")
    @Schema(description = "字典项编码")
    private String itemCode;

    @NotBlank(message = "字典项展示值不能为空")
    @Size(max = 255, message = "字典项展示值长度不能超过255")
    @Schema(description = "字典项展示值")
    private String itemValue;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "父级字典项 ID（0=根）")
    private String parentId;

    @Schema(description = "字典项业务说明")
    private String description;

    @Schema(description = "扩展属性 JSON")
    private String extJson;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
