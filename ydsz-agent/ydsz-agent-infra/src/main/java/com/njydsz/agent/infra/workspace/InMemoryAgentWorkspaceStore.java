package com.njydsz.agent.infra.workspace;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.workspace.AgentWorkspace;
import com.njydsz.agent.domain.workspace.AgentWorkspaceStore;

import lombok.extern.slf4j.Slf4j;

/**
 * 内存工作区存储 — 开发环境默认实现（无外部依赖）。
 *
 * <p>使用 ConcurrentHashMap 存储工作区，重启后数据丢失。
 * 标注 {@code matchIfMissing = true} 确保无配置时自动启用。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@Component
@ConditionalOnMissingBean(AgentWorkspaceStore.class)
@ConditionalOnProperty(
    prefix = "ydsz.agent.workspace",
    name = "backend",
    havingValue = "memory",
    matchIfMissing = true)
public class InMemoryAgentWorkspaceStore implements AgentWorkspaceStore {

  /** 工作区内存存储 */
  private final ConcurrentHashMap<String, AgentWorkspace> store = new ConcurrentHashMap<>(16);

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  @Override
  public AgentWorkspace load(String workspaceId) {
    return store.get(workspaceId);
  }

  @Override
  public void save(AgentWorkspace workspace) {
    store.put(workspace.getWorkspaceId(), workspace);
    log.debug("[Workspace-Memory] 保存工作区: id={}, version={}",
        workspace.getWorkspaceId(), workspace.getVersion());
  }

  @Override
  public void delete(String workspaceId) {
    store.remove(workspaceId);
  }

  @Override
  public boolean exists(String workspaceId) {
    return store.containsKey(workspaceId);
  }

  @Override
  public String getBackendType() {
    return "memory";
  }
}
