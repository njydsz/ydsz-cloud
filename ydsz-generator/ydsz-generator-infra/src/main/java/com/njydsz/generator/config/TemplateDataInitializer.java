package com.njydsz.generator.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.njydsz.common.tenant.SystemTenantContextRunner;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.enums.TemplateFileTypeEnum;
import com.njydsz.generator.repository.GenTemplateGroupRepository;
import com.njydsz.generator.repository.GenTemplateRepository;

/**
 * 模板数据初始化器。
 *
 * <p>应用启动时（{@code template-init} profile）把 classpath 下 templates/{groupName} 目录里的 .vm
 * 同步到数据库 gen_template 表中：**缺哪个补哪个**，已存在的文件名一律保留（不覆盖人工修改）。
 * 原实现"分组只要有一条模板就整组跳过"，导致新增/修复的内置模板永远进不了存量环境。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("template-init")
public class TemplateDataInitializer {

  /** 模板文件后缀。 */
  private static final String TEMPLATE_SUFFIX = ".vm";

  /** 前端模板路径前缀。 */
  private static final String FRONTEND_PATH_PREFIX = "vue/";

  private final GenTemplateGroupRepository groupRepository;
  private final GenTemplateRepository templateRepository;

  /**
   * 应用启动后执行模板数据同步。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    SystemTenantContextRunner.run(() -> {
      log.info("[TemplateDataInitializer] 检查模板数据是否需要初始化...");
      List<GenTemplateGroup> groups = groupRepository.findAllByOrderBysortAsc();
      for (GenTemplateGroup group : groups) {
        syncGroupTemplates(group);
      }
    });
  }

  private void syncGroupTemplates(GenTemplateGroup group) {
    // 逐文件补齐（而不是"分组有任意模板就整组跳过"）：
    // 整组跳过会让新增的内置模板（如 controllerMockTest.vm）在存量环境里永远装不进去，
    // 也让模板修复只对全新安装生效。已存在的文件名一律保留，不覆盖人工修改内容。
    Set<String> existingFileNames =
        templateRepository.findByGroupIdOrderByFileNameAsc(group.getId()).stream()
            .map(GenTemplate::getFileName)
            .collect(Collectors.toSet());
    try {
      String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
          + "/templates/" + group.getName() + "/**/*" + TEMPLATE_SUFFIX;
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources(pattern);

      int added = 0;
      for (Resource resource : resources) {
        String fileName = extractFileName(resource, group.getName());
        if (existingFileNames.contains(fileName)) {
          continue;
        }
        TemplateFileTypeEnum fileType = fileName.startsWith(FRONTEND_PATH_PREFIX)
            ? TemplateFileTypeEnum.FRONTEND : TemplateFileTypeEnum.BACKEND;

        String content = readContent(resource);
        GenTemplate template = GenTemplate.builder()
            .groupId(group.getId())
            .fileName(fileName)
            .description("系统内置模板: " + fileName)
            .content(content)
            .isFolder(false)
            .parentPath(extractParentPath(fileName))
            .version(1)
            .hash(md5(content))
            .isActive(true)
            .fileType(fileType.getCode())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        templateRepository.save(template);
        added++;
      }
      log.info("[TemplateDataInitializer] 分组 {} 新增 {} 条内置模板（已存在 {} 条保留不覆盖）",
          group.getName(), added, existingFileNames.size());
    } catch (Exception e) {
      log.error("[TemplateDataInitializer] 分组 {} 模板同步失败: {}",
          group.getName(), e.getMessage(), e);
    }
  }

  private String extractFileName(Resource resource, String groupName) {
    String uri = resource.getFilename();
    if (uri == null) {
      return "unknown" + TEMPLATE_SUFFIX;
    }
    try {
      String fullPath = resource.getURI().toString();
      int idx = fullPath.indexOf("/templates/" + groupName + "/");
      if (idx > 0) {
        return fullPath.substring(idx + ("/templates/" + groupName + "/").length());
      }
    } catch (final IOException e) {
      log.warn("URI 解析异常，保留原始文件名: {}, 原因: {}", uri, e.getMessage());
    }
    return uri;
  }

  private String extractParentPath(String fileName) {
    int lastSlash = fileName.lastIndexOf('/');
    return lastSlash > 0 ? fileName.substring(0, lastSlash + 1) : "";
  }

  private String readContent(Resource resource) throws IOException {
    try (InputStream is = resource.getInputStream()) {
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String md5(String content) {
    return DigestUtils.md5Hex(content);
  }
}
