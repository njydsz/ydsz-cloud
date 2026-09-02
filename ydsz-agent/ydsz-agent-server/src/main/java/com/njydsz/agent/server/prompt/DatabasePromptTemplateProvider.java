package com.njydsz.agent.server.prompt;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.PromptTemplateProvider;


/**
 * 基于数据库的 Prompt 模板提供者实现
 *
 * <p>通过 {@link PromptManagementService} 从数据库加载模板内容，利用其内置的内存缓存加速热点读取。 模板不存在时返回 {@code null}，由调用方决定降级策略。
 *
 * <p>注册为 Spring Bean，由 {@code AgentFactory} 注入各执行器。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class DatabasePromptTemplateProvider implements PromptTemplateProvider {

  private final PromptManagementService promptManagementService;

  public DatabasePromptTemplateProvider(PromptManagementService promptManagementService) {
    this.promptManagementService = promptManagementService;
  }

  @Override
  public String load(String templateCode) {
    if (templateCode == null || templateCode.isBlank()) {
      return null;
    }
    try {
      PromptManagementService.PromptTemplate template = promptManagementService.get(templateCode);
      if (template == null) {
        log.debug("[PromptProvider] 模板不存在: code={}", templateCode);
        return null;
      }
      log.debug("[PromptProvider] 加载模板: code={}, version={}", templateCode, template.version());
      return template.content();
    } catch (Exception e) {
      log.warn("[PromptProvider] 加载模板失败: code={}, error={}", templateCode, e.getMessage());
      return null;
    }
  }
}
