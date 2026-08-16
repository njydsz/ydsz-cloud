package com.njydsz.common.notify.template;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于文件系统的模板热加载器（骨架实现）
 *
 * <p>从指定目录加载模板文件，支持定时扫描文件变更并自动刷新。 文件格式建议使用 YAML 或 Properties，便于人工编辑。
 *
 * <p><b>注意：</b>此为骨架实现，仅提供接口骨架和基础日志。 生产环境需补充文件监听（如 WatchService）、格式解析、缓存刷新等完整逻辑。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   notify:
 *     template:
 *       hot-reload:
 *         enabled: true
 *         path: /etc/ydsz/templates/
 *         scan-interval-ms: 30000
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FileTemplateHotLoader implements TemplateHotLoader {

  private static final Logger LOG = LoggerFactory.getLogger(FileTemplateHotLoader.class);

  private final Path templateDir;

  /**
   * 构造文件模板热加载器
   *
   * @param templateDir 模板文件目录
   */
  public FileTemplateHotLoader(Path templateDir) {
    this.templateDir = templateDir;
  }

  /**
   * 构造文件模板热加载器（字符串路径）
   *
   * @param templateDirPath 模板文件目录路径
   */
  public FileTemplateHotLoader(String templateDirPath) {
    this(Path.of(templateDirPath));
  }

  @Override
  public List<NotifyTemplate> loadAll() {
    // TODO: 扫描目录下所有模板文件，解析并返回模板列表
    // 建议使用 WatchService 监听文件变更，避免轮询
    LOG.debug("[FileTemplateHotLoader] loadAll from {}", templateDir);
    return List.of();
  }

  @Override
  public NotifyTemplate loadByCode(String templateCode) {
    // TODO: 根据编码加载对应模板文件
    LOG.debug("[FileTemplateHotLoader] loadByCode: {}", templateCode);
    return null;
  }

  @Override
  public String getSource() {
    return "file:" + templateDir.toString();
  }
}
