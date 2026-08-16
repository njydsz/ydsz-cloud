package com.njydsz.nextwiki.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.domain.query.PageQuery;

/**
 * 网盘知识库 API DTO 集合
 *
 * @author ydsz-team
 * @since 1.0.0
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
        @NotBlank(message = "目录名称不能为空")
        @Size(max = 255, message = "目录名称不能超过255个字符")
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
        @NotBlank(message = "目标父目录ID不能为空")
        private String targetParentId;
    }

    /**
     * 批量移动请求
     */
    @Data
    @Schema(description = "批量移动请求")
    public static class BatchMoveRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "待移动节点ID列表")
        @NotNull(message = "节点ID列表不能为空")
        @Size(min = 1, message = "至少选择一个节点")
        private List<String> nodeIds;

        @Schema(description = "目标父目录ID")
        @NotBlank(message = "目标父目录ID不能为空")
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
        @NotBlank(message = "新名称不能为空")
        @Size(max = 255, message = "名称不能超过255个字符")
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
        @NotBlank(message = "文件节点ID不能为空")
        private String fileNodeId;

        @Schema(description = "分享类型: view / download / edit")
        @NotBlank(message = "分享类型不能为空")
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
     * 搜索请求。
     *
     * <p>继承 {@link PageQuery} 获得分页参数（pageNum/pageSize）、排序、
     * 深度分页风险评估等能力，新增搜索 keyword、scope 以及高级筛选字段。
     *
     * <h4>高级筛选能力</h4>
     * <ul>
     *   <li>文件类型筛选：按后缀名过滤（如 pdf、docx、xlsx），支持多选</li>
     *   <li>时间范围筛选：按更新时间范围过滤（startDate / endDate）</li>
     *   <li>大小范围筛选：按文件大小范围过滤（minSize / maxSize，单位字节）</li>
     *   <li>标签筛选：按标签名称过滤，满足任一即返回（OR 关系）</li>
     * </ul>
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @Schema(description = "搜索请求")
    public static class SearchRequest extends PageQuery {

        private static final long serialVersionUID = 1L;

        @Schema(description = "关键词")
        @NotBlank(message = "关键词不能为空")
        private String keyword;

        @Schema(description = "搜索范围: all / filename / content / tag")
        private String scope;

        @Schema(description = "文件类型筛选（后缀名列表，如 pdf / docx / xlsx），为空表示不过滤")
        private List<String> fileTypes;

        @Schema(description = "更新时间起始（ISO 日期时间格式，包含），为空表示不限制")
        private String startDate;

        @Schema(description = "更新时间截止（ISO 日期时间格式，包含），为空表示不限制")
        private String endDate;

        @Schema(description = "文件大小下限（字节），为空表示不限制")
        private Long minSize;

        @Schema(description = "文件大小上限（字节），为空表示不限制")
        private Long maxSize;

        @Schema(description = "标签名称列表（OR 关系，满足任一即返回），为空表示不过滤")
        private List<String> tags;
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
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 100, message = "标签名称不能超过100个字符")
        private String name;

        @Schema(description = "标签颜色（如 #1890ff）")
        private String color;
    }
}
