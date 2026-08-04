package com.njydsz.project.infra.mapper.ops;

import com.njydsz.project.domain.entity.ops.OpsTicket;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpsTicket Mapper。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Mapper
public interface OpsTicketMapper extends BaseMapper<OpsTicket> {
}
