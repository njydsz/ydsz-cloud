package com.njydsz.generator.service;

import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.enums.TemplateFileTypeEnum;
import com.njydsz.generator.repository.GenTemplateGroupRepository;
import com.njydsz.generator.repository.GenTemplateRepository;
import com.njydsz.generator.vo.TemplateZipVO;
import com.njydsz.common.util.security.DigestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 模板导入导出领域服务。
 *
 * <p>支持将指定分组的全部模板导出为 zip 包，或从 zip 文件导入到指定分组。
 * 导出文件格式：{@code templates/{filename}.vm} + {@code manifest.json}。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateImportExportService {

  private final GenTemplateRepository templateRepository;
  private final GenTemplateGroupRepository groupRepository;

  /** 清单文件路径。 */
  private static final String MANIFEST_ENTRY = "manifest.json";
  /** 模板目录前缀。 */
  private static final String TEMPLATE_PREFIX = "templates/";

  /**
   * 导出分组全部模板为 zip 字节。
   *
   * @param groupId 分组 ID
   * @return zip 字节数据
   */
  public TemplateZipVO exportZip(Long groupId) {
    GenTemplateGroup group = groupRepository.findById(groupId)
        .orElseThrow(() -> new IllegalArgumentException("分组不存在: " + groupId));
    List<GenTemplate> templates = templateRepository.findByGroupIdOrderByFileNameAsc(groupId);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

      // 清单文件
      String manifest = buildManifest(group, templates);
      zos.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
      zos.write(manifest.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // 模板文件
      for (GenTemplate tpl : templates) {
        String entryName = TEMPLATE_PREFIX + tpl.getFileName();
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(tpl.getContent().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
      zos.finish();

      String fileName = "templates-" + group.getName() + "-"
          + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".zip";
      return TemplateZipVO.builder()
          .data(baos.toByteArray())
          .fileName(fileName)
          .groupName(group.getName())
          .exportTime(LocalDateTime.now())
          .templateCount(templates.size())
          .build();
    } catch (Exception e) {
      log.error("导出模板失败 groupId={} err={}", groupId, e.getMessage(), e);
      throw new RuntimeException("导出模板失败: " + e.getMessage(), e);
    }
  }

  /**
   * 从 zip 字节导入模板到目标分组。
   *
   * @param targetGroupId 目标分组 ID
   * @param zipData       zip 文件字节
   * @param overwrite     是否覆盖已有模板
   * @return 导入模板数量
   */
  @Transactional(rollbackFor = Exception.class)
  public int importZip(Long targetGroupId, byte[] zipData, boolean overwrite) {
    groupRepository.findById(targetGroupId)
        .orElseThrow(() -> new IllegalArgumentException("目标分组不存在: " + targetGroupId));

    List<GenTemplate> toSave = new ArrayList<>(templates.size());
    try (ZipInputStream zis = new ZipInputStream(
        new ByteArrayInputStream(zipData), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory() || entry.getName().equals(MANIFEST_ENTRY)) {
          zis.closeEntry();
          continue;
        }
        String fileName = entry.getName();
        if (fileName.startsWith(TEMPLATE_PREFIX)) {
          fileName = fileName.substring(TEMPLATE_PREFIX.length());
        }
        String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);

        // 检查是否存在
        boolean exists = templateRepository
            .findByGroupIdAndFileName(targetGroupId, fileName).isPresent();
        if (exists && !overwrite) {
          log.info("跳过已有模板 {}", fileName);
          zis.closeEntry();
          continue;
        }

        GenTemplate template = GenTemplate.builder()
            .groupId(targetGroupId)
            .fileName(fileName)
            .description("导入模板: " + fileName)
            .content(content)
            .folder(false)
            .parentPath(extractParentPath(fileName))
            .version(1)
            .hash(md5(content))
            .active(true)
            .fileType(fileName.startsWith("vue/")
                ? TemplateFileTypeEnum.FRONTEND.getCode()
                : TemplateFileTypeEnum.BACKEND.getCode())
            .build();
        toSave.add(template);
        zis.closeEntry();
      }
    } catch (Exception e) {
      log.error("导入模板失败 err={}", e.getMessage(), e);
      throw new RuntimeException("导入模板失败: " + e.getMessage(), e);
    }

    templateRepository.batchSave(toSave);
    log.info("导入模板完成 groupId={} count={}", targetGroupId, toSave.size());
    return toSave.size();
  }

  private String buildManifest(GenTemplateGroup group, List<GenTemplate> templates) {
    StringBuilder sb = new StringBuilder(512);
    sb.append("{\n");
    sb.append("  \"groupName\": \"").append(group.getName()).append("\",\n");
    sb.append("  \"description\": \"").append(
        group.getDescription() == null ? "" : group.getDescription()).append("\",\n");
    sb.append("  \"exportTime\": \"").append(LocalDateTime.now()).append("\",\n");
    sb.append("  \"templateCount\": ").append(templates.size()).append(",\n");
    sb.append("  \"templates\": [\n");
    for (int i = 0; i < templates.size(); i++) {
      GenTemplate t = templates.get(i);
      sb.append("    {\"fileName\": \"").append(t.getFileName())
          .append("\", \"hash\": \"").append(t.getHash()).append("\"}");
      if (i < templates.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n}");
    return sb.toString();
  }

  private String extractParentPath(String fileName) {
    int idx = fileName.lastIndexOf('/');
    return idx > 0 ? fileName.substring(0, idx + 1) : "";
  }

  private String md5(String content) {
    return DigestUtils.md5Hex(content);
  }
}
