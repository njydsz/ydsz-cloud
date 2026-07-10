package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.agent.AgentDocumentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档 Mapper（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Mapper
public interface AgentDocumentMapper extends BaseMapper<AgentDocumentDO> {
}
