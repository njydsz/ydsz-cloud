package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.tree.TreeNode;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 字典项 VO（兼 DTO）
 *
 * <p>对应 {@code ydsz_dict_item} 表的展示视图和写入参数，是「字典中心」列表 / 详情 / 创建 / 更新接口的通用载体，
 * 也是前端下拉框、单选框、级联选择器、Tag 组件的<b>核心数据载体</b>。
 *
 * <p>由 {@link com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.DictItem} 实体转换而来。
 *
 * <p>继承自 {@link TreeNode}，复用 {@code ydsz-common-domain} 提供的树形结构能力
 * （O(n) 迭代构建、自动 level/path/leaf 填充、自定义排序比较器），无需在业务模块重复实现。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code typeCode} — 所属字典类型编码，与 {@link DictTypeVO#typeCode} 关联
 *   <li>{@code itemCode} — 字典项编码（业务存储值，{@code <el-option :value="...">}）
 *   <li>{@code itemValue} — 字典项展示值（前端展示文本）
 *   <li>{@code parentId} — 父级 ID（{@code 0} = 根），支持「省 / 市 / 区县」三级级联
 *   <li>{@code sortOrder} — 同类型内排序号（升序），通过 {@link #getSort()} 桥接到 TreeBuilder 默认排序器
 *   <li>{@code extJson} — 扩展属性 JSON（如地区码、颜色值、图标等），前端按需解析
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
 * @since 1.6.0 由实现业务模块自建 {@code TreeNode} 接口改为继承 {@code ydsz-common-domain} 的 {@link TreeNode} 基类，
 *               复用 O(n) 迭代构建算法与自动 level/path/leaf 能力
 * @see com.njydsz.system.domain.entity.DictItem 字典项实体
 * @see DictTypeVO 字典类型 VO
 * @see TreeNode 通用树节点基类（ydsz-common-domain）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"children"})
@Schema(description = "字典项视图对象")
public class DictItemVO extends TreeNode<DictItemVO, String> {

  // ===== id / parentId / children 继承自 TreeNode =====

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

  /**
   * 排序号（同类型内升序）。
   *
   * <p>通过 {@link #getSort()} 桥接到 {@link TreeNode#getSort()}，使 {@code TreeBuilder} 默认排序器可直接使用。
   */
  @Schema(description = "排序号")
  private Integer sortOrder;

  @Xss(message = "字典项业务说明包含非法内容")
  @Schema(description = "字典项业务说明")
  private String description;

  @Xss(message = "扩展属性包含非法内容")
  @Schema(description = "扩展属性 JSON")
  private String extJson;

  @Schema(description = "启用状态: ENABLED/DISABLED")
  private String status;

  /**
   * 桥接到业务字段 {@link #sortOrder}，使 {@code TreeBuilder} 默认排序器（基于 {@code getSort()}）可直接使用。
   *
   * <p>排序逻辑：sortOrder 升序，null 值排在最后。
   *
   * @return sortOrder 字段的值
   */
  @Override
  public Integer getSort() {
    return sortOrder;
  }
}
