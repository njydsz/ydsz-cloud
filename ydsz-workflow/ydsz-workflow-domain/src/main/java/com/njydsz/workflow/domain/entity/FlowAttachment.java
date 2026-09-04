package com.njydsz.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 自建工作流引擎 - 审批附件实体
 *
 * <p>对应数据库表 {@code ydsz_flow_attachment}，P1-6 (GAP-51): 审批时提交的附件（图片/文档/视频等）统一落库，支持查询与下载。
 *
 * <p><b>核心使用场景：</b>
 *
 * <ul>
 *   <li>发起审批时上传的申请材料（合同 PDF、发票图片等）
 *   <li>审批过程中补充的材料（{@code bizType=TASK}）
 *   <li>评论附件（{@code bizType=COMMENT}）
 * </ul>
 *
 * <p><b>存储策略：</b>
 *
 * <ul>
 *   <li>{@code storageType=OSS}：阿里云 OSS
 *   <li>{@code storageType=MINIO}：自建 MinIO 对象存储
 *   <li>{@code storageType=LOCAL}：本地文件系统（仅 dev 环境）
 * </ul>
 *
 * <p><b>MD5 去重：</b>{@code md5} 字段在上传时计算，落库前查询是否已存在， 实现秒传能力（文件已存在时直接复用 {@code storageKey}）。
 *
 * <p><b>P1-7 重构：</b>继承 {@link MpBaseEntity}，统一审计字段（{@code
 * createdBy/createdAt/updatedBy/updatedAt/deleted}） 与乐观锁（{@code revision}）由父类管理，消除字段重复声明。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：实例附件清单
 *   <li>普通索引 {@code idx_task}（{@code task_id}）：任务附件清单
 *   <li>普通索引 {@code idx_md5}（{@code md5}）：文件秒传去重
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowAttachmentService 附件服务
 * @see com.njydsz.common.file.storage.IFileStorage 文件存储抽象
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_attachment")
public class FlowAttachment extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 关联流程实例 ID */
  private String instanceId;

  /** 关联任务 ID（实例级附件可为空） */
  private String taskId;

  /** 关联节点编码 */
  private String nodeCode;

  /** 附件业务类型：{@code TASK}（任务级）/ {@code INSTANCE}（实例级）/ {@code COMMENT}（评论） */
  private String bizType;

  /** 原始文件名（含扩展名） */
  private String fileName;

  /** 文件扩展名（{@code jpg/pdf/docx...}，小写不带点） */
  private String fileExt;

  /** 字节大小 */
  private Long fileSize;

  /** MIME 类型（如 {@code image/jpeg} / {@code application/pdf}） */
  private String contentType;

  /** 存储 key（OSS/COS/MinIO 对象 key 或本地相对路径） */
  private String storageKey;

  /** 存储类型：{@code OSS} / {@code MINIO} / {@code LOCAL} */
  private String storageType;

  /** 上传人 ID */
  private String uploaderId;

  /** 上传人姓名（冗余） */
  private String uploaderName;

  /** 临时下载地址（可选，由前端调用签名接口刷新，避免长 URL 泄露） */
  private String downloadUrl;

  /** 文件 MD5（去重/校验） */
  private String md5;

  /** 链路追踪 ID */
  private String providerTraceId;
}
