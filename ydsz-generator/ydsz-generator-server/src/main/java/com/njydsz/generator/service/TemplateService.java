package com.njydsz.generator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.repository.GenTemplateRepository;
import com.njydsz.generator.vo.DiffLineVO;
import com.njydsz.generator.vo.TemplateValidateVO;

/**
 * 模板领域服务。
 *
 * <p>管理模板 CRUD，提供按分组查询、内容更新等能力。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

  /** 模板内容空白占位符长度。 */
  private static final int TEMPLATE_PLACEHOLDER_LENGTH = 64;

  private final GenTemplateRepository templateRepository;
  private final VelocityEngine velocityEngine;

  /**
   * 按分组 ID 查询全部模板。
   *
   * @param groupId 分组 ID
   * @return 模板列表
   */
  public List<GenTemplate> listByGroup(Long groupId) {
    return templateRepository.findByGroupIdOrderByFileNameAsc(groupId);
  }

  /**
   * 查询分组全部模板文件名（不含内容，用于文件浏览器）。
   *
   * @param groupId 分组 ID
   * @return 文件名列表
   */
  public List<String> listFileNames(Long groupId) {
    return templateRepository.findByGroupIdOrderByFileNameAsc(groupId).stream()
        .map(GenTemplate::getFileName)
        .collect(Collectors.toList());
  }

  /**
   * 根据 ID 查询模板。
   *
   * @param id 模板 ID
   * @return 模板实体
   */
  public GenTemplate getById(Long id) {
    return templateRepository.findById(id).orElse(null);
  }

  /**
   * 根据文件名查询模板。
   *
   * @param groupId  分组 ID
   * @param fileName 文件名
   * @return 模板实体
   */
  public GenTemplate getByFileName(Long groupId, String fileName) {
    return templateRepository.findByGroupIdAndFileName(groupId, fileName).orElse(null);
  }

  /**
   * 创建模板。
   *
   * @param template 模板实体
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenTemplate create(GenTemplate template) {
    template.setId(null);
    template.setVersion(1);
    if (template.getIsActive() == null) {
      template.setIsActive(true);
    }
    return templateRepository.save(template);
  }

  /**
   * 更新模板内容（版本号自动递增）。
   *
   * @param template 模板实体（ID + content）
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenTemplate updateContent(GenTemplate template) {
    GenTemplate existing = templateRepository.findById(template.getId())
        .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + template.getId()));
    existing.setContent(template.getContent());
    existing.setDescription(template.getDescription());
    if (template.getVersion() != null) {
      existing.setVersion(template.getVersion() + 1);
    } else {
      existing.setVersion((existing.getVersion() == null ? 1 : existing.getVersion()) + 1);
    }
    log.info("更新模板 id={} file={} version={}", existing.getId(), existing.getFileName(),
        existing.getVersion());
    return templateRepository.save(existing);
  }

  /**
   * 删除模板。
   *
   * @param id 模板 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteById(Long id) {
    templateRepository.deleteById(id);
    log.info("删除模板 id={}", id);
  }

  /**
   * 删除分组全部模板。
   *
   * @param groupId 分组 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteByGroup(Long groupId) {
    templateRepository.deleteByGroupId(groupId);
    log.info("删除模板全部分组 groupId={}", groupId);
  }

  /**
   * 按标题搜索模板。
   *
   * @param groupId 分组 ID
   * @param keyword 搜索关键词
   * @return 匹配模板列表
   */
  public List<GenTemplate> search(Long groupId, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return listByGroup(groupId);
    }
    return templateRepository.findByGroupIdOrderByFileNameAsc(groupId).stream()
        .filter(t -> t.getFileName().contains(keyword)
            || (t.getDescription() != null && t.getDescription().contains(keyword)))
        .collect(Collectors.toList());
  }

  /**
   * 统计分组模板数量。
   *
   * @param groupId 分组 ID
   * @return 数量
   */
  public long countByGroup(Long groupId) {
    return templateRepository.countByGroupId(groupId);
  }

  // ════════════════════════════════════════════════════════════
  // 模板编辑器增强：语法校验 + 版本 Diff
  // ════════════════════════════════════════════════════════════

  /**
   * 校验 Velocity 模板语法是否正确。
   *
   * <p>通过尝试将模板内容作为 Velocity 模板解析（#parse），
   * 若抛出 {@link ParseErrorException} 则返回错误行号和消息。
   * 使用空上下文进行纯语法校验，不实际渲染输出。
   *
   * @param content Velocity 模板内容
   * @return 校验结果 VO
   */
  public TemplateValidateVO validateTemplate(String content) {
    if (content == null || content.isBlank()) {
      return TemplateValidateVO.builder()
          .isValid(false)
          .errorMessage("模板内容不能为空")
          .errorLine(null)
          .build();
    }
    try {
      VelocityContext ctx = new VelocityContext();
      Velocity.evaluate(ctx, new java.io.StringWriter(),
          "validateCheck", content);
      return TemplateValidateVO.builder()
          .isValid(true)
          .errorMessage("")
          .errorLine(null)
          .build();
    } catch (ParseErrorException e) {
      return TemplateValidateVO.builder()
          .isValid(false)
          .errorMessage(e.getMessage())
          .errorLine(e.getLineNumber())
          .build();
    } catch (ResourceNotFoundException e) {
      return TemplateValidateVO.builder()
          .isValid(false)
          .errorMessage(e.getMessage())
          .errorLine(null)
          .build();
    } catch (Exception e) {
      return TemplateValidateVO.builder()
          .isValid(false)
          .errorMessage(e.getMessage())
          .errorLine(null)
          .build();
    }
  }

  /**
   * 对比两个版本模板内容，生成逐行 diff 结果。
   *
   * <p>采用简单 LCS（最长公共子序列）行级 diff 算法，
   * 标注每一行的变更类型（UNCHANGED/ADDED/REMOVED）。
   * 用于前端展示模板历史版本差异。
   *
   * @param oldContent 旧版本模板内容
   * @param newContent 新版本模板内容
   * @return Diff 行列表
   */
  public List<DiffLineVO> diffTemplate(String oldContent, String newContent) {
    String[] oldLines = oldContent == null ? new String[0] : oldContent.split("\n", -1);
    String[] newLines = newContent == null ? new String[0] : newContent.split("\n", -1);
    int oldLen = oldLines.length;
    int newLen = newLines.length;

    // 动态规划求 LCS 长度表
    int[][] lcs = new int[oldLen + 1][newLen + 1];
    for (int i = oldLen - 1; i >= 0; i--) {
      for (int j = newLen - 1; j >= 0; j--) {
        if (oldLines[i].equals(newLines[j])) {
          lcs[i][j] = lcs[i + 1][j + 1] + 1;
        } else {
          lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
        }
      }
    }

    // 回溯生成 diff
    List<DiffLineVO> diffLines = new ArrayList<>(Math.max(oldLen, newLen));
    int i = 0;
    int j = 0;
    while (i < oldLen && j < newLen) {
      if (oldLines[i].equals(newLines[j])) {
        diffLines.add(DiffLineVO.builder()
            .lineNumber(j + 1)
            .oldContent(oldLines[i])
            .newContent(newLines[j])
            .changeType("UNCHANGED")
            .build());
        i++;
        j++;
      } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
        diffLines.add(DiffLineVO.builder()
            .lineNumber(i + 1)
            .oldContent(oldLines[i])
            .newContent(null)
            .changeType("REMOVED")
            .build());
        i++;
      } else {
        diffLines.add(DiffLineVO.builder()
            .lineNumber(j + 1)
            .oldContent(null)
            .newContent(newLines[j])
            .changeType("ADDED")
            .build());
        j++;
      }
    }
    while (i < oldLen) {
      diffLines.add(DiffLineVO.builder()
          .lineNumber(i + 1)
          .oldContent(oldLines[i])
          .newContent(null)
          .changeType("REMOVED")
          .build());
      i++;
    }
    while (j < newLen) {
      diffLines.add(DiffLineVO.builder()
          .lineNumber(j + 1)
          .oldContent(null)
          .newContent(newLines[j])
          .changeType("ADDED")
          .build());
      j++;
    }
    return diffLines;
  }
}
