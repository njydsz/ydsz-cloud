package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 字典项 DTO
 *
 * <p>用于字典项的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code id} — 主键 ID（更新时必填）
 *   <li>{@code typeCode} — 所属字典类型编码
 *   <li>{@code itemCode} — 字典项编码
 *   <li>{@code itemValue} — 字典项展示值
 *   <li>{@code parentId} — 父级 ID
 *   <li>{@code sort} — 排序号
 *   <li>{@code description} — 字典项业务说明
 *   <li>{@code extJson} — 扩展属性 JSON
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class DictItemDTO {

  private String id;

  private String parentId;

  @NotBlank(message = "{system.dto.dictItem.typeCode.required}")
  @Size(max = 64, message = "{system.dto.dictItem.typeCode.max}")
  @Xss(message = "{system.dto.dictItem.typeCode.xss}")
  private String typeCode;

  @NotBlank(message = "{system.dto.dictItem.itemCode.required}")
  @Size(max = 64, message = "{system.dto.dictItem.itemCode.max}")
  @Xss(message = "{system.dto.dictItem.itemCode.xss}")
  private String itemCode;

  @NotBlank(message = "{system.dto.dictItem.itemValue.required}")
  @Size(max = 255, message = "{system.dto.dictItem.itemValue.max}")
  @Xss(message = "{system.dto.dictItem.itemValue.xss}")
  private String itemValue;

  private Integer sort;

  @Xss(message = "{system.dto.dictItem.description.xss}")
  private String description;

  @Xss(message = "{system.dto.dictItem.extJson.xss}")
  private String extJson;

  private String status;
}
