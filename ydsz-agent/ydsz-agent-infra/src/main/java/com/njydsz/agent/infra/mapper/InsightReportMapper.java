package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.agent.domain.insight.InsightReport;

/**
 * 洞察报告 Mapper。
 *
 * <p>基于 MyBatis-Plus BaseMapper 提供 CRUD 操作。映射表 {@code ydsz_agt_insight_report}。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Mapper
public interface InsightReportMapper extends BaseMapper<InsightReport> {}
