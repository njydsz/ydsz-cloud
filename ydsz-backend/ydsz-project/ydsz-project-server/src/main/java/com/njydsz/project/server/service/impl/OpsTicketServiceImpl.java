package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.ops.OpsTicket;
import com.njydsz.project.domain.repository.ops.IOpsTicketRepository;
import com.njydsz.project.server.service.OpsTicketService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpsTicketServiceImpl implements OpsTicketService {
    private final IOpsTicketRepository repository;

    public OpsTicket getById(String id) { return repository.getById(id); }
    public IPage<OpsTicket> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(OpsTicket e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(OpsTicket e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
