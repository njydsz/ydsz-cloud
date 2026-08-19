package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 用户收藏夹视图对象
 *
 * <p>返回给前端的收藏节点信息（含节点元数据 + 收藏排序号）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@Builder
@Schema(description = "用户收藏夹视图对象")
public class UserFavoriteVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "收藏记录ID")
  private String favoriteId;

  @Schema(description = "节点ID")
  private String nodeId;

  @Schema(description = "节点名称")
  private String name;

  @Schema(description = "节点类型: folder / file")
  private String nodeType;

  @Schema(description = "文件后缀")
  private String suffix;

  @Schema(description = "文件大小（字节）")
  private Long size;

  @Schema(description = "目录路径")
  private String path;

  @Schema(description = "缩略图存储键")
  private String thumbnailKey;

  @Schema(description = "排序序号")
  private Integer sortOrder;

  @Schema(description = "是否星标")
  private Boolean starred;

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  @Schema(description = "收藏时间")
  private LocalDateTime favoritedAt;
}
