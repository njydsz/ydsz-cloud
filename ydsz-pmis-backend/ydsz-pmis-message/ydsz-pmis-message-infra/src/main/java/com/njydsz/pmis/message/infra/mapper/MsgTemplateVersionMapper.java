package com.njydsz.pmis.message.infra.mapper.template;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateVersionDO;

/**
 * 模板版本历史 Mapper。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface MsgTemplateVersionMapper extends BaseMapper<MsgTemplateVersionDO> {
}
