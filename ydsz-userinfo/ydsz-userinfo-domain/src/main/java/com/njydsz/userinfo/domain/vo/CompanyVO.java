package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 公司视图对象。
 *
 * <p>表示组织架构中的公司/子公司节点。公司采用树形结构（通过 parentId 自关联），
 * 支持集团-子公司多层嵌套。不包含 deleted、createdBy 等内部维护字段。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code companyCode} — 公司编码（全局唯一，业务侧引用）</li>
 *   <li>{@code parentId} — 上一级公司 ID（顶级公司为 null）</li>
 *   <li>{@code contactPerson} — 公司联系人</li>
 *   <li>{@code status} — 状态（ENABLE-启用、DISABLE-禁用）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class CompanyVO {

  /** 公司唯一标识 */
  private String id;

  /** 公司名称 */
  private String companyName;

  /** 公司编码，全局唯一 */
  private String companyCode;

  /** 父公司 ID */
  private String parentId;

  /** 联系人 */
  private String contactPerson;

  /** 联系电话 */
  private String contactPhone;

  /** 地址 */
  private String address;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;
}
