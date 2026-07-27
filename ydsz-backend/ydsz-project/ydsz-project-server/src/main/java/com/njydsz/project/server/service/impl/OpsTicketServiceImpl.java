package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.ops.OpsTicketDO;
import com.njydsz.project.domain.repository.ops.IOpsTicketRepository;
import com.njydsz.project.server.service.OpsTicketService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpsTicketServiceImpl implements OpsTicketService {
    private final IOpsTicketRepository repository;

    public OpsTicketDO getById(String id) { return repository.getById(id); }
    public IPage<OpsTicketDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(OpsTicketDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(OpsTicketDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
