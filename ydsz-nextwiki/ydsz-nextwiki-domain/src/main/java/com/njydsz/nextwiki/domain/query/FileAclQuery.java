package com.njydsz.nextwiki.domain.query;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件 ACL 查询 Query
 *
 * <p>用于文件 ACL 权限的查询，作为 Repository 接口查询方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "文件 ACL 查询参数")
public class FileAclQuery implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "文件节点ID")
  private String fileNodeId;

  @Schema(description = "授权对象类型：user / role / group / tenant")
  private String granteeType;

  @Schema(description = "授权对象ID")
  private String granteeId;

  @Schema(description = "当前用户ID")
  private String userId;

  @Schema(description = "用户所属角色ID列表")
  private List<String> roleIds;
}
