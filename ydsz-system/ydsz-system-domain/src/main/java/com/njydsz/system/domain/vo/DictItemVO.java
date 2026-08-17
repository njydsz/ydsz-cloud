package com.njydsz.system.domain.vo;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;
import com.njydsz.system.domain.common.TreeNode;

/**
 * 字典项 VO（兼 DTO）
 *
 * <p>对应 {@code ydsz_dict_item} 表的展示视图和写入参数，是「字典中心」列表 / 详情 / 创建 / 更新接口的通用载体，
 * 也是前端下拉框、单选框、级联选择器、Tag 组件的<b>核心数据载体</b>。
 *
 * <p>由 {@link com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.DictItem} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code typeCode} — 所属字典类型编码，与 {@link DictTypeVO#typeCode} 关联
 *   <li>{@code itemCode} — 字典项编码（业务存储值，{@code <el-option :value="...">}）
 *   <li>{@code itemValue} — 字典项展示值（前端展示文本）
 *   <li>{@code parentId} — 父级 ID（{@code 0} = 根），支持「省 / 市 / 区县」三级级联
 *   <li>{@code extJson} — 扩展属性 JSON（如地区码、颜色值、图标等），前端按需解析
 *   <li>{@code sortOrder} — 同类型内排序号（升序）
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>字典中心列表 / 详情
 *   <li>前端通用下拉框 / 单选框 / 多选框 / 级联选择 / 树形字典渲染
 *   <li>业务表单中的「枚举字段」数据源
 * </ul>
 *
 * <p><b>缓存策略：</b>读取时按 {@code ydsz:dict:item:{typeCode}} 整体缓存至 Redis； 列表查询支持本地缓存（Caffeine），TTL 5min。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.DictItem 字典项实体
 * @see DictTypeVO 字典类型 VO
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "字典项视图对象")
public class DictItemVO implements TreeNode<String> {

  @Schema(description = "主键 ID（更新时必填）")
  private String id;

  @NotBlank(message = "字典类型编码不能为空")
  @Size(max = 64, message = "字典类型编码长度不能超过64")
  @Xss(message = "字典类型编码包含非法内容")
  @Schema(description = "所属字典类型编码")
  private String typeCode;

  @NotBlank(message = "字典项编码不能为空")
  @Size(max = 64, message = "字典项编码长度不能超过64")
  @Xss(message = "字典项编码包含非法内容")
  @Schema(description = "字典项编码")
  private String itemCode;

  @NotBlank(message = "字典项展示值不能为空")
  @Size(max = 255, message = "字典项展示值长度不能超过255")
  @Xss(message = "字典项展示值包含非法内容")
  @Schema(description = "字典项展示值")
  private String itemValue;

  @Schema(description = "排序号")
  private Integer sortOrder;

  @Schema(description = "父级字典项 ID（0=根）")
  private String parentId;

  @Xss(message = "字典项业务说明包含非法内容")
  @Schema(description = "字典项业务说明")
  private String description;

  @Xss(message = "扩展属性包含非法内容")
  @Schema(description = "扩展属性 JSON")
  private String extJson;

  @Schema(description = "启用状态: ENABLED/DISABLED")
  private String status;

  /** 子节点列表（树形结构） */
  @Schema(description = "子节点列表")
  private List<DictItemVO> children;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getParentId() {
    return parentId;
  }

  @Override
  public List<DictItemVO> getChildren() {
    return children;
  }

  @Override
  public void setChildren(List<? extends TreeNode<String>> children) {
    // 安全类型转换：DictItemVO 的 children 字段类型是 List<DictItemVO>
    if (children == null) {
      this.children = null;
    } else {
      this.children = children.stream()
          .map(DictItemVO.class::cast)
          .collect(Collectors.toList());
    }
  }
}
