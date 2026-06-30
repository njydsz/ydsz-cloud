package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.MessageTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageTemplateMapper extends BaseMapper<MessageTemplateDO> {

    MessageTemplateDO selectByCodeAndChannel(@Param("code") String code,
                                              @Param("channel") String channel,
                                              @Param("tenantId") Long tenantId);

    List<MessageTemplateDO> selectByChannel(@Param("channel") String channel,
                                            @Param("tenantId") Long tenantId);
}
