package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.repository.MsgTemplateRepository;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgTemplateDO;
import com.njydsz.message.infra.mapper.template.MsgTemplateMapper;

/**
 * 消息模板仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgTemplateRepository} 接口，封装 MsgTemplateMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgTemplateRepositoryImpl implements MsgTemplateRepository {

  private final MsgTemplateMapper msgTemplateMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgTemplateVO vo) {
    MsgTemplateDO entity = voToDO(vo);
    return msgTemplateMapper.insert(entity) > 0;
  }

  @Override
  public Optional<MsgTemplateVO> findById(String id) {
    return Optional.ofNullable(msgTemplateMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public boolean update(MsgTemplateVO vo) {
    MsgTemplateDO entity = voToDO(vo);
    return msgTemplateMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgTemplateMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<MsgTemplateVO> findOne(TemplateQueryDTO query) {
    QueryWrapper<MsgTemplateDO> wrapper = buildWrapper(query);
    return Optional.ofNullable(msgTemplateMapper.selectOne(wrapper)).map(converter::doToVO);
  }

  @Override
  public PageResponse<List<MsgTemplateVO>> findPage(TemplateQueryDTO query) {
    Page<MsgTemplateDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgTemplateDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgTemplateDO> entityPage = msgTemplateMapper.selectPage(page, wrapper);
    List<MsgTemplateVO> vos = converter.templateDoListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  private QueryWrapper<MsgTemplateDO> buildWrapper(TemplateQueryDTO query) {
    QueryWrapper<MsgTemplateDO> wrapper = new QueryWrapper<>();
    if (query.getTemplateCode() != null && !query.getTemplateCode().isBlank()) {
      wrapper.eq("template_code", query.getTemplateCode());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getLocale() != null && !query.getLocale().isBlank()) {
      wrapper.eq("locale", query.getLocale());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    if (query.getAuditStatus() != null && !query.getAuditStatus().isBlank()) {
      wrapper.eq("audit_status", query.getAuditStatus());
    }
    if (query.getCategory() != null && !query.getCategory().isBlank()) {
      wrapper.eq("category", query.getCategory());
    }
    if (query.getSceneCode() != null && !query.getSceneCode().isBlank()) {
      wrapper.eq("scene_code", query.getSceneCode());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgTemplateDO voToDO(MsgTemplateVO vo) {
    if (vo == null) {
      return null;
    }
    MsgTemplateDO entity = new MsgTemplateDO();
    entity.setId(vo.getId());
    entity.setTemplateCode(vo.getTemplateCode());
    entity.setChannel(vo.getChannel());
    entity.setLocale(vo.getLocale());
    entity.setVersion(vo.getVersion());
    entity.setCategory(vo.getCategory());
    entity.setSceneCode(vo.getSceneCode());
    entity.setSubject(vo.getSubject());
    entity.setContent(vo.getContent());
    entity.setProvider(vo.getProvider());
    entity.setProviderKey(vo.getProviderKey());
    entity.setSignName(vo.getSignName());
    entity.setStatus(vo.getStatus());
    entity.setAuditStatus(vo.getAuditStatus());
    entity.setAuditBy(vo.getAuditBy());
    entity.setAuditAt(vo.getAuditAt());
    entity.setAuditRemark(vo.getAuditRemark());
    entity.setDescription(vo.getDescription());
    entity.setVariableDefs(vo.getVariableDefs());
    return entity;
  }
}
