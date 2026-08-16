package com.njydsz.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 流程分类实体
 *
 * <p>对应数据库表 {@code ydsz_flow_category}，P1-6: 对标钉钉/飞书审批的"流程分类管理"能力， 支持按业务线/部门对流程进行分组归类。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>设计器左侧按分类导航（如「项目类」「合同类」「人事类」「财务类」）
 *   <li>发起审批时按分类筛选流程模板
 *   <li>管理员按分类批量管理流程定义（发布/下架）
 * </ul>
 *
 * <p><b>树形结构：</b>{@code parentId} 支持多级嵌套（{@code NULL} 表示顶级分类）， 单次查询全表后在内存中构建树，避免 N+1。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_category_code}（{@code category_code}）：分类编码唯一
 *   <li>普通索引 {@code idx_parent}（{@code parent_id}）：子分类查询
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDefinition 流程定义（{@code category} 字段引用本表）
 * @see com.njydsz.workflow.server.service.FlowCategoryService 分类服务
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_category")
public class FlowCategory extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 分类编码（唯一，业务语义，建议 snake_case） */
  private String categoryCode;

  /** 分类名称（前端展示） */
  private String categoryName;

  /** 父分类 ID（支持多级树形结构，顶级为 {@code NULL}） */
  private String parentId;

  /** 排序号（越小越靠前） */
  private Integer sortNum;

  /** 图标（前端展示用，如 Element Plus icon 名称） */
  private String icon;

  /** 备注（说明分类的业务用途） */
  private String remark;
}
