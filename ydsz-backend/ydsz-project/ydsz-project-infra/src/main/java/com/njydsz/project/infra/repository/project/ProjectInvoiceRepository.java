package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectInvoiceDO;
import com.njydsz.project.domain.repository.project.IProjectInvoiceRepository;
import com.njydsz.project.infra.mapper.project.ProjectInvoiceMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectInvoice Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectInvoiceRepository extends ServiceImpl<ProjectInvoiceMapper, ProjectInvoiceDO>
        implements IProjectInvoiceRepository {
}
