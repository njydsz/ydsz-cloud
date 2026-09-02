package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowNodeExt;

/**
 * 跨节点办理人去重服务
 *
 * <p>负责流程实例的<b>跨节点办理人去重</b>逻辑，对标钉钉「同人不重复审批」。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>去重判断</b>：判断节点是否启用跨节点去重（ext.autoDedup = true）</li>
 *   <li><b>去重执行</b>：过滤已在当前实例审批过的用户，避免同一人重复审批</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class FlowCrossNodeDedupService {

  private final FlowRunTaskRepository taskRepository;

  /**
   * 构造函数
   *
   * @param taskRepository 运行时任务仓储
   */
  public FlowCrossNodeDedupService(FlowRunTaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  /**
   * 判断节点是否启用跨节点去重
   *
   * @param node 流程节点
   * @return true 当 ext.autoDedup = true
   */
  public boolean isAutoDedupEnabled(FlowNodeVO node) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return false;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      Object val = ext.get("autoDedup");
      if (val == null) {
        return false;
      }
      if (val instanceof Boolean b) {
        return b;
      }
      return Boolean.parseBoolean(String.valueOf(val));
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 跨节点办理人去重
   *
   * <p>查询实例下已审批过的人员（COMPLETED 状态），从候选列表中排除这些人。
   *
   * @param userIds 候选办理人列表
   * @param instanceId 实例 ID
   * @param node 流程节点
   * @return 去重后的办理人列表
   */
  public List<String> applyCrossNodeDedup(List<String> userIds, String instanceId, FlowNodeVO node) {
    try {
      // 查询实例下已审批过的人员（COMPLETED 状态）
      List<FlowRunTaskVO> done =
          taskRepository.findByInstanceId(instanceId).stream()
              .filter(t -> FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()))
              .toList();
      Set<String> excluded = new HashSet<>(16);
      for (FlowRunTaskVO t : done) {
        if (t.getAssigneeId() != null && !"SYSTEM_AUTO_PASS".equals(t.getAssigneeName())) {
          excluded.add(t.getAssigneeId());
        }
      }
      int beforeSize = userIds.size();
      List<String> deduped = new ArrayList<>(16);
      for (String uid : userIds) {
        if (!excluded.contains(uid)) {
          deduped.add(uid);
        }
      }
      log.info(
          "[Flow] 跨节点办理人去重: instanceId={} node={} before={} after={} excluded={}",
          instanceId,
          node.getNodeCode(),
          beforeSize,
          deduped.size(),
          beforeSize - deduped.size());
      return deduped;
    } catch (Exception e) {
      log.warn(
          "[Flow] 跨节点办理人去重异常，跳过去重: instanceId={} node={} err={}",
          instanceId,
          node.getNodeCode(),
          e.getMessage());
      return userIds;
    }
  }
}
