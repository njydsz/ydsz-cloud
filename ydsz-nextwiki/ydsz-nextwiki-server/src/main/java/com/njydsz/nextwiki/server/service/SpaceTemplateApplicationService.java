package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.SpaceTemplateRepository;
import com.njydsz.nextwiki.domain.vo.SpaceVO;

/**
 * 空间模板应用服务
 *
 * <p><b>S4-P3-02：文档模板体系</b>
 *
 * <p>提供预定义空间结构模板的管理功能：查询可用模板、创建自定义模板、使用模板创建空间。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceTemplateApplicationService {

  private final SpaceTemplateRepository spaceTemplateRepository;
  private final SpaceApplicationService spaceApplicationService;

  /** 默认查询数量限制 */
  private static final int DEFAULT_LIMIT = 20;

  /**
   * 查询可用模板列表。
   *
   * <p>返回系统公开模板 + 当前租户自定义模板。
   *
   * @param category 模板分类（可为 null 表示全部分类）
   * @return 模板DTO列表
   */
  public List<SpaceTemplateDTO> listTemplates(String category) {
    String tenantId = TenantContextHolder.getTenantId();
    return spaceTemplateRepository.findAvailableTemplates(tenantId, category);
  }

  /**
   * 获取模板详情。
   *
   * @param templateId 模板ID
   * @return 模板DTO
   */
  public SpaceTemplateDTO getTemplate(String templateId) {
    return spaceTemplateRepository.findById(templateId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.TEMPLATE_NOT_FOUND)
            .data("templateId", templateId));
  }

  /**
   * 创建自定义模板。
   *
   * @param name 模板名称
   * @param description 模板描述
   * @param category 模板分类
   * @param structureJson 模板结构JSON
   * @param userId 创建者ID
   * @return 新创建的模板DTO
   */
  @Transactional(rollbackFor = Exception.class)
  public SpaceTemplateDTO createTemplate(
      String name, String description, String category, String structureJson, String userId) {
    String tenantId = TenantContextHolder.getTenantId();
    LocalDateTime now = LocalDateTime.now();

    SpaceTemplateDTO dto = SpaceTemplateDTO.builder()
        .name(name)
        .description(description)
        .category(category != null ? category : "general")
        .tenantId(tenantId)
        .isSystem(false)
        .isPublic(false)
        .structureJson(structureJson)
        .sortOrder(0)
        .usageCount(0)
        .createdBy(userId)
        .updatedBy(userId)
        .createdAt(now)
        .updatedAt(now)
        .build();

    spaceTemplateRepository.save(dto);

    log.info("[SpaceTemplateApplicationService] 创建模板: templateId={}, name={}", dto.getId(), name);
    return dto;
  }

  /**
   * 更新自定义模板。
   *
   * @param templateId 模板ID
   * @param name 新名称
   * @param description 新描述
   * @param category 新分类
   * @param structureJson 新结构JSON
   * @param userId 操作人ID
   * @return 更新后的模板DTO
   */
  @Transactional(rollbackFor = Exception.class)
  public SpaceTemplateDTO updateTemplate(
      String templateId, String name, String description, String category, String structureJson, String userId) {
    SpaceTemplateDTO dto = spaceTemplateRepository.findById(templateId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.TEMPLATE_NOT_FOUND)
            .data("templateId", templateId));

    // 系统模板不允许修改
    if (Boolean.TRUE.equals(dto.getIsSystem())) {
      throw BusinessException.of(NextwikiExceptionCode.TEMPLATE_SYSTEM_NOT_EDITABLE);
    }

    if (name != null) {
      dto.setName(name);
    }
    if (description != null) {
      dto.setDescription(description);
    }
    if (category != null) {
      dto.setCategory(category);
    }
    if (structureJson != null) {
      dto.setStructureJson(structureJson);
    }
    dto.setUpdatedBy(userId);
    dto.setUpdatedAt(LocalDateTime.now());

    spaceTemplateRepository.update(dto);

    log.info("[SpaceTemplateApplicationService] 更新模板: templateId={}", templateId);
    return dto;
  }

  /**
   * 删除自定义模板。
   *
   * @param templateId 模板ID
   * @param userId 操作人ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteTemplate(String templateId, String userId) {
    SpaceTemplateDTO dto = spaceTemplateRepository.findById(templateId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.TEMPLATE_NOT_FOUND)
            .data("templateId", templateId));

    // 系统模板不允许删除
    if (Boolean.TRUE.equals(dto.getIsSystem())) {
      throw BusinessException.of(NextwikiExceptionCode.TEMPLATE_SYSTEM_NOT_DELETABLE);
    }

    spaceTemplateRepository.deleteById(templateId);
    log.info("[SpaceTemplateApplicationService] 删除模板: templateId={}", templateId);
  }

  /**
   * 使用模板创建空间。
   *
   * <p>基于预定义模板的结构 JSON 自动创建空间及初始目录树。
   *
   * @param templateId 模板ID
   * @param spaceName 空间名称
   * @param userId 创建者ID
   * @return 新创建的空间视图
   */
  @Transactional(rollbackFor = Exception.class)
  public SpaceVO createSpaceFromTemplate(String templateId, String spaceName, String userId) {
    // 获取模板
    SpaceTemplateDTO template = spaceTemplateRepository.findById(templateId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.TEMPLATE_NOT_FOUND)
            .data("templateId", templateId));

    // 增加模板使用次数
    spaceTemplateRepository.incrementUsageCount(templateId);

    // 使用默认可见性创建空间
    SpaceVO space = spaceApplicationService.createSpace(
        spaceName, template.getDescription(), "private", userId);

    log.info(
        "[SpaceTemplateApplicationService] 使用模板创建空间: templateId={}, spaceId={}, spaceName={}",
        templateId, space.getId(), spaceName);

    // TODO: 2026-09-01 根据 template.getStructureJson() 创建初始目录树，具体逻辑由结构解析服务处理。（@ydsz-team）

    return space;
  }
}
