package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectCustomerCreditDO;
import com.njydsz.project.domain.repository.project.IProjectCustomerCreditRepository;
import com.njydsz.project.infra.mapper.project.ProjectCustomerCreditMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectCustomerCredit Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectCustomerCreditRepository extends ServiceImpl<ProjectCustomerCreditMapper, ProjectCustomerCreditDO>
        implements IProjectCustomerCreditRepository {
}
