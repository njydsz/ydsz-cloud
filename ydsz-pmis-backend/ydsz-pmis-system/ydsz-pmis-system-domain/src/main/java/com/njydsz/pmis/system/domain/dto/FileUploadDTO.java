package com.njydsz.pmis.system.domain.dto.file;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 文件上传 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class FileUploadDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 指定 Bucket（可选，默认用 default-bucket） */
    private String bucket;

    /** 描述 */
    private String description;

    /** 上传人 ID */
    private String uploaderId;

    /** 上传人姓名 */
    private String uploaderName;
}
