package com.njydsz.system.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 字典项 VO（视图对象）
 *
 * <p>对应 {@code ydsz_sys_dict_item} 表的展示视图，是「字典中心」列表 / 详情接口的响应载体，
 * 也是前端下拉框、单选框、级联选择器、Tag 组件的<b>核心数据载体</b>。
 *
 * <p>使用 {@link com.njydsz.common.domain.tree.TreeBuilder#buildSimple} 构建树形结构，无需继承 {@code TreeNode} 基类，
 * 保持 VO 类的纯粹性（无框架耦合），同时通过 {@code level}/{@code path} 回调自动填充层级元数据。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code typeCode} — 所属字典类型编码，与 {@link DictTypeVO#typeCode} 关联
 *   <li>{@code itemCode} — 字典项编码（业务存储值，{@code <el-option :value="...">}）
 *   <li>{@code itemValue} — 字典项展示值（前端展示文本）
 *   <li>{@code parentId} — 父级 ID（{@code 0} = 根），支持「省 / 市 / 区县」三级级联
 *   <li>{@code sortOrder} — 同类型内排序号（升序），直接作为 TreeBuilder 排序字段
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
 * <p><b>注意：</b>本类为视图对象，不包含输入校验逻辑。输入校验由 {@link
 * com.njydsz.system.domain.dto.DictItemDTO} 负责。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0 由继承 {@code ydsz-common-domain} 的 {@code TreeNode} 基类改为纯 POJO，使用 {@code TreeBuilder.buildSimple()}
 *               构建树形结构，消除业务 VO 与框架基类的耦合
 * @see com.njydsz.system.infra.entity.DictItem 字典项实体
 * @see DictTypeVO 字典类型 VO
 * @see com.njydsz.common.domain.tree.TreeBuilder 通用树构建器
 */
@Data
public class DictItemVO {

  /** 字典项唯一标识 */
  private String id;

  /** 父级 ID（{@code 0} = 根），支持「省 / 市 / 区县」三级级联 */
  private String parentId;

  /** 子节点列表（树形构建时由 TreeBuilder 自动填充） */
  private List<DictItemVO> children;

  /** 层级深度（根节点=1，由 TreeBuilder 自动填充） */
  private Integer level;

  /** 节点路径（如 "/1/2/5/"，由 TreeBuilder 自动填充） */
  private String path;

  private String typeCode;

  private String itemCode;

  private String itemValue;

  /** 排序号（同类型内升序），直接作为 TreeBuilder 排序字段 */
  private Integer sortOrder;

  private String description;

  private String extJson;

  private String status;
}
