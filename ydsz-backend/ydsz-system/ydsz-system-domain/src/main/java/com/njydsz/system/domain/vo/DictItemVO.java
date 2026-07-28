package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典项 VO
 *
 * <p>对应 {@code ydsz_dict_item} 表的展示视图，是「字典中心」列表 / 详情接口的返回值类型，
 * 也是前端下拉框、单选框、级联选择器、Tag 组件的<b>核心数据载体</b>。
 *
 * <p>由 {@link com.njydsz.system.domain.converter.SystemConverter} 从
 * {@link com.njydsz.system.domain.entity.DictItem} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@code typeCode} — 所属字典类型编码，与 {@link DictTypeVO#typeCode} 关联</li>
 *   <li>{@code itemCode} — 字典项编码（业务存储值，{@code <el-option :value="...">}）</li>
 *   <li>{@code itemValue} — 字典项展示值（前端展示文本）</li>
 *   <li>{@code parentId} — 父级 ID（{@code 0} = 根），支持「省 / 市 / 区县」三级级联</li>
 *   <li>{@code extJson} — 扩展属性 JSON（如地区码、颜色值、图标等），前端按需解析</li>
 *   <li>{@code sortOrder} — 同类型内排序号（升序）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>字典中心列表 / 详情</li>
 *   <li>前端通用下拉框 / 单选框 / 多选框 / 级联选择 / 树形字典渲染</li>
 *   <li>业务表单中的「枚举字段」数据源</li>
 * </ul>
 *
 * <p><b>缓存策略：</b>读取时按 {@code ydsz:dict:item:{typeCode}} 整体缓存至 Redis；
 * 列表查询支持本地缓存（Caffeine），TTL 5min。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.DictItem 字典项实体
 * @see com.njydsz.system.domain.dto.DictItemDTO 字典项 DTO
 * @see DictTypeVO 字典类型 VO
 */
@Data
@Schema(description = "字典项视图对象")
public class DictItemVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "所属字典类型编码")
    private String typeCode;

    @Schema(description = "字典项编码")
    private String itemCode;

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
