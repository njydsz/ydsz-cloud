package com.njydsz.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.DecisionTable;

/**
 * 决策表 Mapper
 *
 * @author ydsz
 * @since 2026-07-02
 */
@Mapper
public interface DecisionTableMapper extends BaseMapper<DecisionTable> {
}