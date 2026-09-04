package com.njydsz.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 审批常用语实体
 *
 * <p>对应数据库表 {@code ydsz_flow_quick_comment}，P1-2: 对标钉钉/飞书审批的"常用语"能力， 用户可预设常用审批意见，审批时一键填入，提升审批效率。
 *
 * <p><b>核心使用场景：</b>
 *
 * <ul>
 *   <li>审批意见面板展示「我的常用语」快捷选项
 *   <li>点击常用语后自动填入审批意见输入框
 *   <li>按使用频率（{@code useCount}）智能排序，热门常用语靠前
 * </ul>
 *
 * <p><b>数据隔离：</b>常用语按 {@code userId} 隔离，每个用户拥有自己的常用语清单。 {@code isSystem=1} 的记录为系统预置（所有用户可见但不可修改）。
 *
 * <p><b>意见分类（{@code commentType}）：</b>对齐 {@link com.njydsz.workflow.domain.enums.FlowCommentType}，
 * 取值 {@code AGREE} / {@code DISAGREE} / {@code SUGGEST} / {@code INQUIRE}，可空。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_user}（{@code user_id}）：按用户查询
 *   <li>普通索引 {@code idx_sort}（{@code sort_num}）：自定义排序
 *   <li>普通索引 {@code idx_use_count}（{@code use_count}）：按使用频率排序
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowCommentService 评论服务（含常用语能力）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_quick_comment")
public class FlowQuickComment extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID（所属用户，常用语按用户隔离） */
  private String userId;

  /** 常用语内容（审批意见文本，最大长度 500） */
  private String content;

  /** 意见分类：{@code AGREE} / {@code DISAGREE} / {@code SUGGEST} / {@code INQUIRE}（可空） */
  private String commentType;

  /** 排序号（越小越靠前，默认 {@code 0}） */
  private Integer sortNum;

  /** 使用次数（统计用，前端可按使用频率排序） */
  private Integer useCount;

  /** 是否为系统预设：{@code 1}=系统预置（所有用户可见）/ {@code 0}=用户自定义 */
  private Integer isSystem;
}
