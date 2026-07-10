package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.orchestration.DagInstanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * DAG 实例 Mapper（P3-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Mapper
public interface DagInstanceMapper extends BaseMapper<DagInstanceDO> {
}
