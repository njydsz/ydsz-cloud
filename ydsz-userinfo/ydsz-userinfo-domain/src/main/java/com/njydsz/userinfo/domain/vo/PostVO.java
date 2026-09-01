package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 岗位视图对象。
 *
 * <p>岗位是组织架构中的职位分类（如产品经理/开发工程师/测试工程师），用于审批人展开与用户标记。
 * 不包含 deleted、createdBy 等内部维护字段。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code postCode} — 岗位编码（全局唯一，如 PM/DEV/QA/SA），支持 position: 审批人展开</li>
 *   <li>{@code postName} — 岗位名称（如"产品经理"、"开发工程师"）</li>
 *   <li>{@code description} — 岗位描述</li>
 *   <li>{@code sortOrder} — 排序序号（越小越靠前）</li>
 *   <li>{@code status} — 状态（ENABLE-启用、DISABLE-禁用）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class PostVO {

  /** 岗位唯一标识 */
  private String id;

  /** 岗位名称 */
  private String postName;

  /** 岗位编码，全局唯一 */
  private String postCode;

  /** 岗位描述 */
  private String description;

  /** 排序序号，越小越靠前 */
  private Integer sortOrder;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;
}
