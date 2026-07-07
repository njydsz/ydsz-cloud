package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自建工作流引擎 - 审批附件实体
 *
 * <p>P1-6 (GAP-51): 审批时提交的附件（图片/文档/视频等）统一落库，支持查询与下载。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_flow_attachment")
public class FlowAttachmentDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 关联流程实例 ID */
    private String instanceId;

    /** 关联任务 ID（实例级附件可为空） */
    private String taskId;

    /** 关联节点编码 */
    private String nodeCode;

    /** 附件业务类型: TASK / INSTANCE / COMMENT */
    private String bizType;

    /** 原始文件名 */
    private String fileName;

    /** 文件扩展名（jpg/pdf...） */
    private String fileExt;

    /** 字节大小 */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 存储 key（OSS/COS/MinIO 对象 key 或本地相对路径） */
    private String storageKey;

    /** 存储类型: OSS / MINIO / LOCAL */
    private String storageType;

    /** 上传人 ID */
    private String uploaderId;

    /** 上传人姓名 */
    private String uploaderName;

    /** 临时下载地址（可选，前端可直接展示） */
    private String downloadUrl;

    /** 文件 MD5（去重/校验） */
    private String md5;

    /** 逻辑删除: 0=未删 1=已删 */
    @TableLogic
    private Integer deleted;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 修改人 */
    private String updatedBy;

    /** 修改时间 */
    private LocalDateTime updatedAt;

    /** 链路追踪 ID */
    private String providerTraceId;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
