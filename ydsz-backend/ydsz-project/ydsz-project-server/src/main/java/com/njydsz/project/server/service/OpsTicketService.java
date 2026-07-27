package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.ops.OpsTicket;

public interface OpsTicketService {
    OpsTicket getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<OpsTicket> page(int pageNum, int pageSize);
    boolean save(OpsTicket entity);
    boolean updateById(OpsTicket entity);
    boolean removeById(String id);
}
