package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件 ACL 权限 DTO
 *
 * <p>用于文件 ACL 的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "文件 ACL 权限数据传输对象")
public class FileAclDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
  private String id;

  @Schema(description = "文件节点ID")
  private String fileNodeId;

  @Schema(description = "授权对象类型：user / role / group / tenant")
  private String granteeType;

  @Schema(description = "授权对象ID")
  private String granteeId;

  @Schema(description = "权限位掩码（read=1, write=2, delete=4, share=8, download=16）")
  private Integer permissionMask;

  @Schema(description = "是否继承自父目录")
  private Boolean inherited;

  @Schema(description = "是否为所有者")
  private Boolean owner;
}
