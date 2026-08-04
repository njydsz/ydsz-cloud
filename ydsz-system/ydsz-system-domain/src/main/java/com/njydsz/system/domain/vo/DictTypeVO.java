package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典类型 VO
 *
 * <p>对应 {@code ydsz_dict_type} 表的展示视图，是「字典中心」列表 / 详情接口的返回值类型。
 * 由 {@link com.njydsz.system.domain.converter.SystemConverter} 从
 * {@link com.njydsz.system.domain.entity.DictType} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@code typeCode} — 字典类型编码，租户内唯一，业务存储 / 接口传输主键</li>
 *   <li>{@code typeName} — 字典类型名称（前端展示文本，如「订单状态」「行业类型」）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>字典中心列表 / 详情 / 编辑回显</li>
 *   <li>前端「字典类型选择器」数据源（管理员配置字典项前的下拉框）</li>
 *   <li>字典项管理页的「按类型筛选」下拉框</li>
 * </ul>
 *
 * <p><b>关联关系：</b>1 个 {@code DictTypeVO} 对应 N 个 {@link DictItemVO}，构成「类型 → 项」两级结构。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 * @see com.njydsz.system.domain.dto.DictTypeDTO 字典类型 DTO
 * @see DictItemVO 字典项 VO
 */
@Data
@Schema(description = "字典类型视图对象")
public class DictTypeVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "字典类型编码")
    private String typeCode;

    @Schema(description = "字典类型名称")
    private String typeName;

    @Schema(description = "字典类型业务说明")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
