package com.njydsz.pmis.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.literule.domain.entity.DecisionTableDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 决策表 Mapper
 *
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Mapper
public interface DecisionTableMapper extends BaseMapper<DecisionTableDO> {
}