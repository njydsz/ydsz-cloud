package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.query.MsgTemplateVersionQuery;
import com.njydsz.message.domain.repository.MsgTemplateVersionRepository;
import com.njydsz.message.domain.vo.MsgTemplateVersionVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgTemplateVersionDO;
import com.njydsz.message.infra.mapper.template.MsgTemplateVersionMapper;

/**
 * 消息模板版本历史仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgTemplateVersionRepository} 接口，封装 MsgTemplateVersionMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgTemplateVersionRepositoryImpl implements MsgTemplateVersionRepository {

  private final MsgTemplateVersionMapper msgTemplateVersionMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgTemplateVersionVO vo) {
    MsgTemplateVersionDO entity = voToDO(vo);
    return msgTemplateVersionMapper.insert(entity) > 0;
  }

  @Override
  public List<MsgTemplateVersionVO> findList(MsgTemplateVersionQuery query) {
    QueryWrapper<MsgTemplateVersionDO> wrapper = new QueryWrapper<>();
    if (query.getTemplateCode() != null && !query.getTemplateCode().isBlank()) {
      wrapper.eq("template_code", query.getTemplateCode());
    }
    if (query.getAuditStatus() != null && !query.getAuditStatus().isBlank()) {
      wrapper.eq("audit_status", query.getAuditStatus());
    }
    wrapper.eq("deleted", 0);
    wrapper.orderByDesc("version");
    return converter.templateVersionDoListToVO(msgTemplateVersionMapper.selectList(wrapper));
  }

  @Override
  public Optional<MsgTemplateVersionVO> findOne(MsgTemplateVersionQuery query) {
    QueryWrapper<MsgTemplateVersionDO> wrapper = new QueryWrapper<>();
    if (query.getTemplateCode() != null && !query.getTemplateCode().isBlank()) {
      wrapper.eq("template_code", query.getTemplateCode());
    }
    if (query.getAuditStatus() != null && !query.getAuditStatus().isBlank()) {
      wrapper.eq("audit_status", query.getAuditStatus());
    }
    wrapper.eq("deleted", 0);
    return Optional.ofNullable(msgTemplateVersionMapper.selectOne(wrapper)).map(converter::doToVO);
  }

  private MsgTemplateVersionDO voToDO(MsgTemplateVersionVO vo) {
    if (vo == null) {
      return null;
    }
    MsgTemplateVersionDO entity = new MsgTemplateVersionDO();
    entity.setId(vo.getId());
    entity.setTemplateCode(vo.getTemplateCode());
    entity.setVersion(vo.getVersion());
    entity.setContent(vo.getContent());
    entity.setVariableDefs(vo.getVariableDefs());
    entity.setAuditStatus(vo.getAuditStatus());
    entity.setAuditor(vo.getAuditor());
    entity.setAuditRemark(vo.getAuditRemark());
    return entity;
  }
}
