package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectProfitSimulationDO;
import com.njydsz.project.domain.repository.project.IProjectProfitSimulationRepository;
import com.njydsz.project.infra.mapper.project.ProjectProfitSimulationMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectProfitSimulation Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectProfitSimulationRepository extends ServiceImpl<ProjectProfitSimulationMapper, ProjectProfitSimulationDO>
        implements IProjectProfitSimulationRepository {
}
