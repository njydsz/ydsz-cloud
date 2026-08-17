package com.njydsz.workflow.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.workflow.infra.entity.FlowAttachmentDO;

/**
 * 自建工作流引擎 - 审批附件 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_attachment</code>（P1-6 GAP-51），存储审批节点上传的附件。
 *
 * <p>附件走文件存储服务，DB 仅保存元数据（文件 ID/名称/大小/上传人），由 {@code IFileStorageProvider} 负责上传/下载。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_instance_id — 流程实例维度查询索引
 *   <li>idx_task_id — 任务维度查询索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.infra.entity.FlowAttachmentDO 审批附件实体
 * @see com.njydsz.workflow.server.service.FlowAttachmentService 审批附件 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowAttachmentMapper extends BaseMapper<FlowAttachmentDO> {

  /**
   * 查询某任务关联的未删除附件
   *
   * @param taskId 任务 ID
   * @return 附件列表
   */
  @Select(
      "SELECT * FROM ydsz_flow_attachment WHERE task_id = #{taskId} AND deleted = 0 ORDER BY created_at ASC")
  List<FlowAttachmentDO> selectByTask(@Param("taskId") String taskId);

  /**
   * 查询某实例关联的未删除附件
   *
   * @param instanceId 实例 ID
   * @return 附件列表
   */
  @Select(
      "SELECT * FROM ydsz_flow_attachment WHERE instance_id = #{instanceId} AND deleted = 0 ORDER BY created_at ASC")
  List<FlowAttachmentDO> selectByInstance(@Param("instanceId") String instanceId);
}
