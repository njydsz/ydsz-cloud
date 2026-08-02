package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.common.json.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 分享链接 VO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "分享链接信息")
public class ShareLinkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分享ID")
    private String id;

    @Schema(description = "分享码")
    private String shareCode;

    @Schema(description = "提取码")
    private String extractCode;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "分享类型")
    private String shareType;

    @Schema(description = "过期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Schema(description = "最大访问次数")
    private Integer maxAccessCount;

    @Schema(description = "已访问次数")
    private Integer accessCount;

    @Schema(description = "分享状态")
    private String status;

    @Schema(description = "是否需要密码")
    private Boolean hasPassword;

    @Schema(description = "分享URL")
    private String shareUrl;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
