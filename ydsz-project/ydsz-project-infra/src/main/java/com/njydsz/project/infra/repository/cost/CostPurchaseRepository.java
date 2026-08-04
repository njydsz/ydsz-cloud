package com.njydsz.project.infra.repository.cost;

import com.njydsz.project.domain.entity.cost.CostPurchase;
import com.njydsz.project.domain.repository.cost.ICostPurchaseRepository;
import com.njydsz.project.infra.mapper.cost.CostPurchaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * CostPurchase Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class CostPurchaseRepository extends ServiceImpl<CostPurchaseMapper, CostPurchase>
        implements ICostPurchaseRepository {
}
