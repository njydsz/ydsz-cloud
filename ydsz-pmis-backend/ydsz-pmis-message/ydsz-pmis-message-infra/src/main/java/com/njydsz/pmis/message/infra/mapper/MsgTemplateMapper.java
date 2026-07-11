package com.njydsz.pmis.message.infra.mapper.template;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息模板 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgTemplateMapper extends BaseMapper<MsgTemplateDO> {
}
