package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.canary.MsgCanaryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 灰度桶 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgCanaryMapper extends BaseMapper<MsgCanaryDO> {
}
