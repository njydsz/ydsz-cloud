package com.njydsz.project.infra.repository.billable;

import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;
import com.njydsz.project.domain.repository.billable.IBillableUtilizationSnapshotRepository;
import com.njydsz.project.infra.mapper.billable.BillableUtilizationSnapshotMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * BillableUtilizationSnapshot Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class BillableUtilizationSnapshotRepository extends ServiceImpl<BillableUtilizationSnapshotMapper, BillableUtilizationSnapshot>
        implements IBillableUtilizationSnapshotRepository {
}
