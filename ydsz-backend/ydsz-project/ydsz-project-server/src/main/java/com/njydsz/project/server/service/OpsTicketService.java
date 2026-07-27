package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.ops.OpsTicketDO;

public interface OpsTicketService {
    OpsTicketDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<OpsTicketDO> page(int pageNum, int pageSize);
    boolean save(OpsTicketDO entity);
    boolean updateById(OpsTicketDO entity);
    boolean removeById(String id);
}
