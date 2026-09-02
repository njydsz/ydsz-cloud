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
 *   <li>{@code sortOrder} — 排序号
 *   <li>{@code description} — 字典项业务说明
 *   <li>{@code extJson} — 扩展属性 JSON
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class DictItemDTO {

  private String id;

  private String parentId;

  @NotBlank(message = "字典类型编码不能为空")
  @Size(max = 64, message = "字典类型编码长度不能超过64")
  @Xss(message = "字典类型编码包含非法内容")
  private String typeCode;

  @NotBlank(message = "字典项编码不能为空")
  @Size(max = 64, message = "字典项编码长度不能超过64")
  @Xss(message = "字典项编码包含非法内容")
  private String itemCode;

  @NotBlank(message = "字典项展示值不能为空")
  @Size(max = 255, message = "字典项展示值长度不能超过255")
  @Xss(message = "字典项展示值包含非法内容")
  private String itemValue;

  private Integer sortOrder;

  @Xss(message = "字典项业务说明包含非法内容")
  private String description;

  @Xss(message = "扩展属性包含非法内容")
  private String extJson;

  private String status;
}
