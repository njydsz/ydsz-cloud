package com.njydsz.generator.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.enums.TemplateFileTypeEnum;
import com.njydsz.generator.repository.GenTemplateGroupRepository;
import com.njydsz.generator.repository.GenTemplateRepository;

/**
 * 模板数据初始化器。
 *
 * <p>应用启动时自动将 classpath:/templates/{groupName}/* 下的 .vm 文件
 * 同步到数据库 gen_template 表中（仅当分组无模板时触发，不覆盖人工修改内容）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Component
@RequiredArgsConstructor
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
    log.info("[TemplateDataInitializer] 检查模板数据是否需要初始化...");
    List<GenTemplateGroup> groups = groupRepository.findAllByOrderBySortOrderAsc();
    for (GenTemplateGroup group : groups) {
      syncGroupTemplates(group);
    }
  }

  private void syncGroupTemplates(GenTemplateGroup group) {
    long existingCount = templateRepository.countByGroupId(group.getId());
    if (existingCount > 0) {
      log.info("[TemplateDataInitializer] 分组 {} 已有 {} 条模板，跳过初始化",
          group.getName(), existingCount);
      return;
    }
    try {
      String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
          + "/templates/" + group.getName() + "/**/*" + TEMPLATE_SUFFIX;
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources(pattern);

      for (Resource resource : resources) {
        String fileName = extractFileName(resource, group.getName());
        TemplateFileTypeEnum fileType = fileName.startsWith(FRONTEND_PATH_PREFIX)
            ? TemplateFileTypeEnum.FRONTEND : TemplateFileTypeEnum.BACKEND;

        String content = readContent(resource);
        GenTemplate template = GenTemplate.builder()
            .groupId(group.getId())
            .fileName(fileName)
            .description("系统内置模板: " + fileName)
            .content(content)
            .folder(false)
            .parentPath(extractParentPath(fileName))
            .version(1)
            .hash(md5(content))
            .active(true)
            .fileType(fileType.getCode())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        templateRepository.save(template);
      }
      log.info("[TemplateDataInitializer] 分组 {} 同步 {} 条模板成功",
          group.getName(), resources.length);
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
    } catch (Exception e) {
      // fallback to filename only
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
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }
}
