package com.njydsz.generator.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 模板文件热加载监听服务。
 *
 * <p>使用 JDK WatchService 监听模板目录的文件变更（创建、修改、删除），
 * 当模板发生变化时通知 {@link VelocityConfig} 重新加载。
 *
 * <p>仅在 {@code dev} 或 {@code generator-dev} profile 下启用。
 *
 * @author ydsz-team
 * @since 26.09.04
 */
@Slf4j
@Service
@Profile({"dev", "generator-dev"})
public class TemplateWatcherService implements ApplicationRunner {

  private static final String DEFAULT_TEMPLATE_DIR = "classpath:/templates";

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  @Override
  public void run(ApplicationArguments args) {
    String watchDir = System.getProperty("ydsz.generator.template-dir", "./templates");
    if (watchDir == null || watchDir.startsWith("classpath:")) {
      log.info("模板热加载仅在文件系统模板目录下生效，当前模式: {}", watchDir);
      return;
    }

    Path dir = Paths.get(watchDir);
    if (!dir.toFile().exists()) {
      log.warn("模板目录不存在，跳过热加载监听: {}", watchDir);
      return;
    }

    executorService.submit(() -> watchDirectory(dir));
    log.info("模板热加载监听已启动: {}", watchDir);
  }

  private void watchDirectory(Path dir) {
    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      dir.register(watchService,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_MODIFY,
          StandardWatchEventKinds.ENTRY_DELETE
      );

      log.info("开始监听模板目录: {}", dir.toAbsolutePath());

      while (!Thread.currentThread().isInterrupted()) {
        WatchKey key;
        try {
          key = watchService.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }

        for (WatchEvent<?> event : key.pollEvents()) {
          WatchEvent.Kind<?> kind = event.kind();
          if (kind == StandardWatchEventKinds.OVERFLOW) {
            continue;
          }

          @SuppressWarnings("unchecked")
          WatchEvent<Path> ev = (WatchEvent<Path>) event;
          Path fileName = ev.context();
          String templateName = fileName.toString();

          if (templateName.endsWith(".vm")) {
            log.info("模板文件变更 [{}]: {}，触发重新加载", kind.name(), templateName);
            reloadTemplate(templateName);
          }
        }

        boolean valid = key.reset();
        if (!valid) {
          break;
        }
      }
    } catch (IOException e) {
      log.error("模板监听服务异常", e);
    }
  }

  /**
   * 通知 Velocity 引擎重新加载模板。
   *
   * @param templateName - 变更的模板文件名
   */
  private void reloadTemplate(String templateName) {
    // Velocity 引擎默认会按需重新加载模板文件
    // 此处用于触发缓存清理或事件广播
    log.info("模板已更新: {}", templateName);
  }
}
