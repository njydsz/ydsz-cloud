package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.DagDefinitionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * DAG 定义 Mapper（P3-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Mapper
public interface DagDefinitionMapper extends BaseMapper<DagDefinitionDO> {
}
