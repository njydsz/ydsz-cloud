package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.MessageLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageLogMapper extends BaseMapper<MessageLogDO> {
}
