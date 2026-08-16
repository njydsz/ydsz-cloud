package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自建工作流引擎 - 审批附件 DTO
 *
 * <p>P1-6 (GAP-51): 审批时由前端提交的附件信息，序列化为 JSON 传入后端。
 * 字段与 {@link com.njydsz.workflow.domain.entity.FlowAttachment} 对齐，
 * 仅保留业务可见字段，不暴露内部版本号/审计字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAttachmentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 原始文件名 */
    @NotBlank(message = "{validation.workflow.msg_3c1a8b22}")
    private String fileName;

    /** 文件扩展名（jpg/pdf/docx...，可空时由 fileName 推断） */
    private String fileExt;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 存储 key（OSS / COS / MinIO 对象 key，或本地相对路径） */
    @NotBlank(message = "{validation.workflow.msg_3c1a8b23}")
    private String storageKey;

    /** 存储类型: OSS / MINIO / LOCAL（默认 OSS） */
    private String storageType;

    /** 临时下载地址（可选，由前端在文件上传后填入） */
    private String downloadUrl;

    /** 文件 MD5（去重/校验） */
    private String md5;
}
