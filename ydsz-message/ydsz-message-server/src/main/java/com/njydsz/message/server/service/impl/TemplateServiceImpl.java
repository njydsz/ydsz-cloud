package com.njydsz.message.server.service.impl.template;

import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.TemplateAuditDTO;
import com.njydsz.message.domain.dto.TemplateCreateDTO;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.domain.enums.template.TemplateAuditStatusEnum;
import com.njydsz.message.domain.repository.MsgTemplateRepository;
import com.njydsz.message.server.service.template.TemplateService;
import com.njydsz.message.server.template.TemplateEngine;

/**
 * 消息模板服务实现。
 *
 * <p>维护消息模板 ({@code ydsz_msg_template} / {@code ydsz_msg_template_version})：
 *
 * <p>按 (templateCode, channel, locale, tenantId) 唯一；
 *
 * <p>支持审核流转 DRAFT → AUDITING → APPROVED/REJECTED，发布后版本化；
 *
 * <p>变量支持 ${var} 嵌套替换与缺省值回退；locale 加载支持精确 + 默认 zh-CN 回退。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

  /** 消息模板 Repository（CRUD / locale 回退查询） */
  private final MsgTemplateRepository msgTemplateRepository;

  /** 模板引擎（变量渲染） */
  private final TemplateEngine templateEngine;

  /** 搜索索引事件桥接器（可选注入）。 用于在模板创建/更新/删除时异步同步到 ydsz-common-search 统一搜索索引。 */
  private final ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider;

  /**
   * 创建消息模板。
   *
   * <p>按 (templateCode, channel, locale, tenantId) 唯一性校验，冲突抛 DUPLICATE_KEY； 新建模板默认状态 ENABLED、审核状态
   * DRAFT。locale 缺省回退 zh-CN。
   *
   * @param dto 模板创建参数（templateCode/channel 必填）
   * @return 已创建的模板实体
   * @throws com.njydsz.common.exception.custom.SysException 参数为空 / 编码或通道为空 / 模板已存在时
   */
  @Override
  public MsgTemplateVO create(TemplateCreateDTO dto) {
    if (dto == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板参数不能为空")
          .build();
    }
    if (!StringUtils.hasText(dto.getTemplateCode()) || !StringUtils.hasText(dto.getChannel())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板编码与通道不能为空")
          .build();
    }
    String tenantId = TenantContextHolder.getTenantId();
    String locale =
        StringUtils.hasText(dto.getLocale()) ? dto.getLocale() : MessageConstants.DEFAULT_LOCALE;
    // 唯一性校验 (templateCode, channel, locale, tenantId)
    MsgTemplateVO existing =
        msgTemplateRepository.selectOne(
            new LambdaQueryWrapper<MsgTemplateVO>()
                .eq(MsgTemplate::getTemplateCode, dto.getTemplateCode())
                .eq(MsgTemplate::getChannel, dto.getChannel())
                .eq(MsgTemplate::getLocale, locale)
                .eq(MsgTemplate::getTenantId, tenantId)
                .last("LIMIT 1"));
    if (existing != null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板已存在: " + dto.getTemplateCode() + "/" + locale)
          .build();
    }
    MsgTemplateVO entity = new MsgTemplateVO();
    entity.setTemplateCode(dto.getTemplateCode());
    entity.setChannel(dto.getChannel());
    entity.setLocale(locale);
    entity.setVersion(dto.getVersion());
    entity.setCategory(dto.getCategory());
    entity.setSceneCode(dto.getSceneCode());
    entity.setSubject(dto.getSubject());
    entity.setContent(dto.getContent());
    entity.setProvider(dto.getProvider());
    entity.setProviderKey(dto.getProviderKey());
    entity.setSignName(dto.getSignName());
    entity.setStatus("ENABLED");
    entity.setAuditStatus(TemplateAuditStatusEnum.DRAFT.name());
    entity.setDescription(dto.getDescription());
    entity.setTenantId(tenantId);
    msgTemplateRepository.insert(entity);
    syncSearchIndex(entity);
    log.info(
        "[Template] 创建模板: code={} channel={} locale={}",
        dto.getTemplateCode(),
        dto.getChannel(),
        locale);
    return entity;
  }

  /**
   * 更新消息模板（局部字段）。
   *
   * <p>先按 id 校验存在，再对非空字段做局部更新；id 为空抛 BAD_REQUEST。 注意：更新不触发版本化，发布版本化由发布流程处理。
   *
   * @param id 模板 ID
   * @param dto 模板更新参数（非空字段覆盖）
   * @return 更新后的模板实体
   * @throws com.njydsz.common.exception.custom.SysException id 为空 / dto 为空 / 模板不存在时
   */
  @Override
  public MsgTemplateVO update(String id, TemplateCreateDTO dto) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板 ID 不能为空")
          .build();
    }
    MsgTemplateVO entity = getById(id);
    if (dto == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板参数不能为空")
          .build();
    }
    if (StringUtils.hasText(dto.getLocale())) {
      entity.setLocale(dto.getLocale());
    }
    if (StringUtils.hasText(dto.getVersion())) {
      entity.setVersion(dto.getVersion());
    }
    if (StringUtils.hasText(dto.getCategory())) {
      entity.setCategory(dto.getCategory());
    }
    if (dto.getSceneCode() != null) {
      entity.setSceneCode(dto.getSceneCode());
    }
    if (dto.getSubject() != null) {
      entity.setSubject(dto.getSubject());
    }
    if (dto.getContent() != null) {
      entity.setContent(dto.getContent());
    }
    if (dto.getProvider() != null) {
      entity.setProvider(dto.getProvider());
    }
    if (dto.getProviderKey() != null) {
      entity.setProviderKey(dto.getProviderKey());
    }
    if (dto.getSignName() != null) {
      entity.setSignName(dto.getSignName());
    }
    if (dto.getDescription() != null) {
      entity.setDescription(dto.getDescription());
    }
    msgTemplateRepository.updateById(entity);
    syncSearchIndex(entity);
    return entity;
  }

  /**
   * 删除消息模板（物理删除）。
   *
   * @param id 模板 ID（为空抛 BAD_REQUEST）
   * @throws com.njydsz.common.exception.custom.SysException id 为空时
   */
  @Override
  public void delete(String id) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板 ID 不能为空")
          .build();
    }
    msgTemplateRepository.deleteById(id);
    deleteSearchIndex(id);
  }

  /**
   * 按 ID 查询模板。
   *
   * @param id 模板 ID
   * @return 模板实体
   * @throws com.njydsz.common.exception.custom.SysException id 为空 / 模板不存在时
   */
  @Override
  public MsgTemplateVO getById(String id) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板 ID 不能为空")
          .build();
    }
    MsgTemplateVO entity = msgTemplateRepository.selectById(id);
    if (entity == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("模板不存在: " + id)
          .build();
    }
    return entity;
  }

  /**
   * 分页查询模板（支持多条件动态过滤）。
   *
   * <p>按 createdAt 降序；页码/页大小缺失取默认，页大小受 {@code PageConstants} 上限保护。
   *
   * @param query 查询条件（可空，空则查全量分页）
   * @return 模板分页结果
   */
  @Override
  public Page<MsgTemplateVO> page(TemplateQueryDTO query) {
    Page<MsgTemplateVO> page =
        new Page<>(
            query == null ? 1 : query.getPageNum(),
            Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
    LambdaQueryWrapper<MsgTemplateVO> w = new LambdaQueryWrapper<>();
    if (query != null) {
      w.eq(
          StringUtils.hasText(query.getTemplateCode()),
          MsgTemplate::getTemplateCode,
          query.getTemplateCode());
      w.eq(StringUtils.hasText(query.getChannel()), MsgTemplate::getChannel, query.getChannel());
      w.eq(StringUtils.hasText(query.getLocale()), MsgTemplate::getLocale, query.getLocale());
      w.eq(StringUtils.hasText(query.getStatus()), MsgTemplate::getStatus, query.getStatus());
      w.eq(
          StringUtils.hasText(query.getAuditStatus()),
          MsgTemplate::getAuditStatus,
          query.getAuditStatus());
      w.eq(StringUtils.hasText(query.getCategory()), MsgTemplate::getCategory, query.getCategory());
      w.eq(
          StringUtils.hasText(query.getSceneCode()),
          MsgTemplate::getSceneCode,
          query.getSceneCode());
    }
    w.orderByDesc(MsgTemplate::getCreatedAt);
    return msgTemplateRepository.selectPage(page, w);
  }

  /**
   * 按 (templateCode, channel, locale, tenantId) 加载启用模板。
   *
   * <p>先精确 locale 匹配启用模板；未命中且非默认 locale 时回退 zh-CN。 用于发送/渲染时按渠道取模板，tenantId 缺省取当前租户。
   *
   * @param templateCode 模板编码
   * @param channel 通道
   * @param locale 区域（可空，回退默认）
   * @param tenantId 租户 ID（可空，取当前租户）
   * @return 命中模板；无匹配返回 null
   * @throws com.njydsz.common.exception.custom.SysException templateCode 或 channel 为空时
   */
  @Override
  public MsgTemplateVO loadByCodeAndChannel(
      String templateCode, String channel, String locale, String tenantId) {
    if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(channel)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板编码与通道不能为空")
          .build();
    }
    String tid = StringUtils.hasText(tenantId) ? tenantId : TenantContextHolder.getTenantId();
    String loc = StringUtils.hasText(locale) ? locale : MessageConstants.DEFAULT_LOCALE;
    // 精确 locale
    MsgTemplateVO entity =
        msgTemplateRepository.selectOne(
            new LambdaQueryWrapper<MsgTemplateVO>()
                .eq(MsgTemplate::getTemplateCode, templateCode)
                .eq(MsgTemplate::getChannel, channel)
                .eq(MsgTemplate::getLocale, loc)
                .eq(MsgTemplate::getTenantId, tid)
                .eq(MsgTemplate::getStatus, "ENABLED")
                .last("LIMIT 1"));
    if (entity != null) {
      return entity;
    }
    // 回退默认 zh-CN
    if (!MessageConstants.DEFAULT_LOCALE.equals(loc)) {
      entity =
          msgTemplateRepository.selectOne(
              new LambdaQueryWrapper<MsgTemplateVO>()
                  .eq(MsgTemplate::getTemplateCode, templateCode)
                  .eq(MsgTemplate::getChannel, channel)
                  .eq(MsgTemplate::getLocale, MessageConstants.DEFAULT_LOCALE)
                  .eq(MsgTemplate::getTenantId, tid)
                  .eq(MsgTemplate::getStatus, "ENABLED")
                  .last("LIMIT 1"));
    }
    return entity;
  }

  /**
   * 审核模板（状态流转 DRAFT→AUDITING→APPROVED/REJECTED）。
   *
   * <p>校验流转合法性（{@link #canTransitAudit}）；APPROVED 同步启用、REJECTED 同步禁用，并记录审核时间。
   *
   * @param id 模板 ID
   * @param dto 审核参数（auditStatus 必填）
   * @throws com.njydsz.common.exception.custom.SysException 参数为空 / 非法流转时
   */
  @Override
  public void audit(String id, TemplateAuditDTO dto) {
    if (dto == null || !StringUtils.hasText(dto.getAuditStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("审核状态不能为空")
          .build();
    }
    MsgTemplateVO entity = getById(id);
    TemplateAuditStatusEnum current = parseAuditStatus(entity.getAuditStatus());
    TemplateAuditStatusEnum target = parseAuditStatus(dto.getAuditStatus());
    if (!canTransitAudit(current, target)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("非法审核状态流转: " + current + " -> " + target)
          .build();
    }
    entity.setAuditStatus(target.name());
    entity.setAuditRemark(dto.getAuditRemark());
    // APPROVED 时同步启用状态
    if (target == TemplateAuditStatusEnum.APPROVED) {
      entity.setStatus("ENABLED");
    } else if (target == TemplateAuditStatusEnum.REJECTED) {
      entity.setStatus("DISABLED");
    }
    entity.setAuditAt(LocalDateTime.now());
    msgTemplateRepository.updateById(entity);
    log.info("[Template] 审核模板: id={} {} -> {}", id, current, target);
  }

  /**
   * 渲染模板预览。
   *
   * <p>先加载启用模板，再交由 {@link TemplateEngine} 按入参渲染变量；模板不存在抛 NOT_FOUND。
   *
   * @param templateCode 模板编码
   * @param channel 通道
   * @param params 渲染变量（可为 null）
   * @param locale 区域（可空）
   * @param tenantId 租户 ID（可空）
   * @return 渲染后的模板内容
   * @throws com.njydsz.common.exception.custom.SysException templateCode/channel 为空 / 模板不存在时
   */
  @Override
  public String preview(
      String templateCode,
      String channel,
      Map<String, Object> params,
      String locale,
      String tenantId) {
    if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(channel)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("模板编码与通道不能为空")
          .build();
    }
    MsgTemplateVO template = loadByCodeAndChannel(templateCode, channel, locale, tenantId);
    if (template == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("模板不存在: " + templateCode + "/" + channel)
          .build();
    }
    return templateEngine.render(template.getContent(), params);
  }

  /**
   * 校验审核状态流转合法性：DRAFT → AUDITING → APPROVED/REJECTED。
   *
   * @param current 当前状态
   * @param target 目标状态
   * @return true 表示允许流转
   */
  private boolean canTransitAudit(TemplateAuditStatusEnum current, TemplateAuditStatusEnum target) {
    if (current == target) {
      return true;
    }
    return switch (current) {
      case DRAFT ->
          target == TemplateAuditStatusEnum.AUDITING
              || target == TemplateAuditStatusEnum.APPROVED
              || target == TemplateAuditStatusEnum.REJECTED;
      case AUDITING ->
          target == TemplateAuditStatusEnum.APPROVED || target == TemplateAuditStatusEnum.REJECTED;
      case APPROVED, REJECTED -> false;
    };
  }

  private TemplateAuditStatusEnum parseAuditStatus(String value) {
    try {
      return TemplateAuditStatusEnum.valueOf(value);
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("非法审核状态: " + value)
          .build();
    }
  }

  /**
   * 将模板数据同步到统一搜索索引（ydsz_search_index）。
   *
   * <p>通过 {@link SearchIndexEventBridge} 异步写入，不阻塞主业务流程。 未引入 {@code ydsz-common-search} 时桥接器为空，跳过同步。
   *
   * @param template 消息模板实体
   */
  private void syncSearchIndex(MsgTemplateVO template) {
    SearchIndexEventBridge bridge = searchIndexEventBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("message_template", template);
    }
  }

  /**
   * 从统一搜索索引删除模板文档。
   *
   * @param id 模板 ID
   */
  private void deleteSearchIndex(String id) {
    SearchIndexEventBridge bridge = searchIndexEventBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexDelete("message_template", id);
    }
  }
}
