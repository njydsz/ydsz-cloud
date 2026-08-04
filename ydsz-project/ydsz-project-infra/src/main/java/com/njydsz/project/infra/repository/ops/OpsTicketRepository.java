package com.njydsz.project.infra.repository.ops;

import com.njydsz.project.domain.entity.ops.OpsTicket;
import com.njydsz.project.domain.repository.ops.IOpsTicketRepository;
import com.njydsz.project.infra.mapper.ops.OpsTicketMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * OpsTicket Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class OpsTicketRepository extends ServiceImpl<OpsTicketMapper, OpsTicket>
        implements IOpsTicketRepository {
}
