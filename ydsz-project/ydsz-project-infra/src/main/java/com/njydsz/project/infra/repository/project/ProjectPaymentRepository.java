package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectPayment;
import com.njydsz.project.domain.repository.project.IProjectPaymentRepository;
import com.njydsz.project.infra.mapper.project.ProjectPaymentMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectPayment Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectPaymentRepository extends ServiceImpl<ProjectPaymentMapper, ProjectPayment>
        implements IProjectPaymentRepository {
}
