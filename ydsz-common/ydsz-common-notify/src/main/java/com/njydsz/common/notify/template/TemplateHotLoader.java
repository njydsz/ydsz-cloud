package com.njydsz.common.notify.template;

import java.util.List;

/**
 * 模板热加载器
 *
 * <p>支持从外部源（文件系统、Redis、数据库）动态加载模板，实现模板热更新而无需重启服务。 实现类需保证线程安全，支持并发读取和原子替换。
 *
 * <p>此为骨架接口，提供 {@link FileTemplateHotLoader} 文件实现示例。 生产环境可替换为基于 Redis 或数据库的实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface TemplateHotLoader {

  /**
   * 加载所有可用模板
   *
   * @return 模板列表
   */
  List<NotifyTemplate> loadAll();

  /**
   * 加载指定编码的模板
   *
   * @param templateCode 模板编码
   * @return 模板，不存在返回 null
   */
  NotifyTemplate loadByCode(String templateCode);

  /**
   * 获取模板源描述（用于日志和监控）
   *
   * @return 模板源描述
   */
  String getSource();
}
