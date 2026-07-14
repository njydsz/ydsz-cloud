package com.njydsz.pmis.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.pmis.common.json.annotation.YdszJsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件节点树形 VO
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Builder
@Schema(description = "文件节点树形结构")
public class FileNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点ID")
    private String id;

    @Schema(description = "父节点ID")
    private String parentId;

    @Schema(description = "节点名称")
    private String name;

    @Schema(description = "节点类型: folder / file")
    private String nodeType;

    @Schema(description = "文件扩展名")
    private String suffix;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "层级深度")
    private Integer level;

    @Schema(description = "排序序号")
    private Integer sort;

    @Schema(description = "当前版本号")
    private Integer currentVersion;

    @Schema(description = "是否星标")
    private Boolean starred;

    @Schema(description = "共享状态")
    private String shareStatus;

    @Schema(description = "缩略图URL")
    private String thumbnailUrl;

    @Schema(description = "预览是否就绪")
    private Boolean previewReady;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    @YdszJsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @YdszJsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "子节点列表")
    private List<FileNodeVO> children;

    @Schema(description = "标签列表")
    private List<String> tags;
}
