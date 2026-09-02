package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件 ACL 权限 VO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "文件 ACL 权限信息")
public class FileAclVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "ACL记录ID")
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

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;
}
