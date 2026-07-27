package com.njydsz.project.infra.repository.cost;

import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.domain.repository.cost.ICostAllocationRepository;
import com.njydsz.project.infra.mapper.cost.CostAllocationMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * CostAllocation Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class CostAllocationRepository extends ServiceImpl<CostAllocationMapper, CostAllocation>
        implements ICostAllocationRepository {
}
