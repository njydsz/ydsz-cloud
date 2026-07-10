package com.njydsz.pmis.agent.mapper.orchestration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.orchestration.DagNodeInstanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * DAG 节点实例 Mapper（P3-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Mapper
public interface DagNodeInstanceMapper extends BaseMapper<DagNodeInstanceDO> {
}
