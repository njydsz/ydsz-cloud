package com.njydsz.cronjob.server.core.discovery;

import java.util.List;

import com.njydsz.cronjob.domain.vo.JobNodeVO;

/**
 * 执行器节点发现策略（P1-1）。
 *
 * <p>支持两种实现：
 *
 * <ul>
 *   <li>{@code NACOS}：基于 Nacos 服务发现，复用现有注册能力，替代心跳表
 *   <li>{@code DB}：基于 ydsz_job_node 心跳表（向后兼容）
 * </ul>
 *
 * <p>通过 {@code ydsz.cronjob.node-discovery.type} 配置项切换，默认 {@code nacos}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface NodeDiscoveryStrategy {

  /**
   * 获取所有在线执行器节点。
   *
   * @return 在线节点 VO 列表；无节点时返回空列表
   */
  List<JobNodeVO> getOnlineNodes();

  /**
   * 获取当前节点 ID。
   *
   * @return 当前节点 ID（hostname:port）
   */
  String getLocalNodeId();
}
