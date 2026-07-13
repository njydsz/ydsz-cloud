package com.njydsz.pmis.nextwiki.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 网盘知识库 API DTO 集合
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class NextwikiDTOs {

    private NextwikiDTOs() {
    }

    /**
     * 创建目录请求
     */
    @Data
    @Schema(description = "创建目录请求")
    public static class CreateFolderRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "父目录ID（根目录传 null 或 \"0\"）")
        private String parentId;

        @Schema(description = "目录名称")
        private String name;
    }

    /**
     * 上传文件请求
     */
    @Data
    @Schema(description = "上传文件请求")
    public static class UploadFileRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "父目录ID")
        private String parentId;

        @Schema(description = "文件重命名（可选）")
        private String rename;

        @Schema(description = "版本备注（可选）")
        private String versionRemark;
    }

    /**
     * 移动文件请求
     */
    @Data
    @Schema(description = "移动文件请求")
    public static class MoveRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "目标父目录ID")
        private String targetParentId;
    }

    /**
     * 重命名请求
     */
    @Data
    @Schema(description = "重命名请求")
    public static class RenameRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "新名称")
        private String newName;
    }

    /**
     * 创建分享请求
     */
    @Data
    @Schema(description = "创建分享请求")
    public static class CreateShareRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "文件节点ID")
        private String fileNodeId;

        @Schema(description = "分享类型: view / download / edit")
        private String shareType;

        @Schema(description = "密码（可选）")
        private String password;

        @Schema(description = "过期时间（可选，null 表示永久）")
        private LocalDateTime expireTime;

        @Schema(description = "最大访问次数（可选，null 表示不限）")
        private Integer maxAccessCount;
    }

    /**
     * 验证分享访问请求
     */
    @Data
    @Schema(description = "验证分享访问请求")
    public static class VerifyShareRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "分享码")
        private String shareCode;

        @Schema(description = "提取码")
        private String extractCode;

        @Schema(description = "密码（如有）")
        private String password;
    }

    /**
     * 授予权限请求
     */
    @Data
    @Schema(description = "授予权限请求")
    public static class GrantPermissionRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "文件节点ID")
        private String fileNodeId;

        @Schema(description = "授权对象类型: user / role / group")
        private String granteeType;

        @Schema(description = "授权对象ID")
        private String granteeId;

        @Schema(description = "权限: read / write / delete / share / download（逗号分隔）")
        private String permissions;
    }

    /**
     * 搜索请求
     */
    @Data
    @Schema(description = "搜索请求")
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "关键词")
        private String keyword;

        @Schema(description = "搜索范围: all / filename / content / tag")
        private String scope;

        @Schema(description = "页码（从 1 开始）")
        private Integer page;

        @Schema(description = "每页大小")
        private Integer pageSize;
    }

    /**
     * 设置配额请求
     */
    @Data
    @Schema(description = "设置配额请求")
    public static class SetQuotaRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "配额维度: user / tenant / project")
        private String scopeType;

        @Schema(description = "维度ID")
        private String scopeId;

        @Schema(description = "配额上限（字节）")
        private Long quotaLimit;

        @Schema(description = "文件数量上限")
        private Integer fileCountLimit;
    }

    /**
     * 绑定标签请求
     */
    @Data
    @Schema(description = "绑定标签请求")
    public static class BindTagRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "文件节点ID")
        private String fileNodeId;

        @Schema(description = "标签ID列表")
        private List<String> tagIds;
    }

    /**
     * 创建标签请求
     */
    @Data
    @Schema(description = "创建标签请求")
    public static class CreateTagRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "标签名称")
        private String name;

        @Schema(description = "标签颜色（如 #1890ff）")
        private String color;
    }
}
