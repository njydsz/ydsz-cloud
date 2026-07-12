package com.njydsz.pmis.agent.infra.mapper.tool;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.domain.entity.tool.TokenUsageLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Token 使用明细数据访问层（P2-4 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Mapper
public interface TokenUsageLogMapper extends BaseMapper<TokenUsageLogDO> {
}
