package com.njydsz.message.infra.mapper.template;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.template.MsgTemplateVersionDO;

/**
 * 模板版本历史 Mapper。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgTemplateVersionMapper extends BaseMapper<MsgTemplateVersionDO> {
}
