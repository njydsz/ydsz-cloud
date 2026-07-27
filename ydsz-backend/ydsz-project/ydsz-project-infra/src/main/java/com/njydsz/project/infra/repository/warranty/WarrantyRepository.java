package com.njydsz.project.infra.repository.warranty;

import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.project.domain.repository.warranty.IWarrantyRepository;
import com.njydsz.project.infra.mapper.warranty.WarrantyMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * Warranty Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class WarrantyRepository extends ServiceImpl<WarrantyMapper, Warranty>
        implements IWarrantyRepository {
}
