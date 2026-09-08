package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.system.domain.approval.ConfigApproval;

/**
 * 配置变更审批单 Mapper。
 *
 * <p>基于 MyBatis-Plus BaseMapper 提供基础 CRUD 能力。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Mapper
public interface ConfigApprovalMapper extends BaseMapper<ConfigApproval> {
}
