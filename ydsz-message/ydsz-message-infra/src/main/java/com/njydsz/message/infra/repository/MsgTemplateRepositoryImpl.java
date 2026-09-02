package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MsgTemplateDTO;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.repository.MsgTemplateRepository;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgTemplate;
import com.njydsz.message.infra.mapper.template.MsgTemplateMapper;

/**
 * 消息模板仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgTemplateRepository} 接口，封装 MsgTemplateMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class MsgTemplateRepositoryImpl implements MsgTemplateRepository {

  private final MsgTemplateMapper msgTemplateMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgTemplateDTO dto) {
    MsgTemplate entity = converter.dtoToEntity(dto);
    return msgTemplateMapper.insert(entity) > 0;
  }

  @Override
  public Optional<MsgTemplateVO> findById(String id) {
    return Optional.ofNullable(msgTemplateMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public boolean update(MsgTemplateDTO dto) {
    MsgTemplate entity = converter.dtoToEntity(dto);
    return msgTemplateMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgTemplateMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<MsgTemplateVO> findOne(TemplateQueryDTO query) {
    QueryWrapper<MsgTemplate> wrapper = buildWrapper(query);
    return Optional.ofNullable(msgTemplateMapper.selectOne(wrapper)).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<MsgTemplateVO>> findPage(TemplateQueryDTO query) {
    Page<MsgTemplate> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgTemplate> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgTemplate> entityPage = msgTemplateMapper.selectPage(page, wrapper);
    List<MsgTemplateVO> vos = converter.templateListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  private QueryWrapper<MsgTemplate> buildWrapper(TemplateQueryDTO query) {
    QueryWrapper<MsgTemplate> wrapper = new QueryWrapper<>();
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

}
