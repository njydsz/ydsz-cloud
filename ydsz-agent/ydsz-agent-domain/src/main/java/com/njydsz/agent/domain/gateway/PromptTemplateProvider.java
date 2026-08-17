package com.njydsz.agent.domain.gateway;

/**
 * Prompt 模板提供者接口（端口）
 *
 * <p>领域层定义的 Prompt 模板加载端口，解耦执行器与具体存储实现。 执行器通过此接口获取模板内容，无需感知模板来自数据库、配置文件还是远程服务。
 *
 * <p>实现类位于 server 层（{@code DatabasePromptTemplateProvider}），通过 {@code @Component} 注册后由 Spring 注入执行器。
 *
 * <h3>设计意图</h3>
 *
 * <ul>
 *   <li>避免执行器直接依赖 {@code PromptManagementService}（server 层服务），防止循环依赖
 *   <li>支持未来切换存储介质（如 Nacos 配置中心、Redis）时无需修改执行器代码
 *   <li>提供统一的降级策略：模板不存在时返回 {@code null}，执行器回退到默认 Prompt
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface PromptTemplateProvider {

  /**
   * 根据模板编码获取模板内容
   *
   * @param templateCode 模板编码（如 "DEFAULT_SYSTEM"、"REACT_SYSTEM"）
   * @return 模板内容字符串；模板不存在时返回 {@code null}
   */
  String load(String templateCode);

  /**
   * 根据模板编码获取模板内容，不存在时返回默认值
   *
   * @param templateCode 模板编码
   * @param defaultValue 模板不存在时的回退值
   * @return 模板内容字符串；模板不存在时返回 {@code defaultValue}
   */
  default String loadOrDefault(String templateCode, String defaultValue) {
    String content = load(templateCode);
    return content != null ? content : defaultValue;
  }
}
