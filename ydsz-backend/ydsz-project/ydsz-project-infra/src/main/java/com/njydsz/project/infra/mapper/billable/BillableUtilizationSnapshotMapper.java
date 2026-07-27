package com.njydsz.project.infra.mapper.billable;

import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * BillableUtilizationSnapshot Mapper。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Mapper
public interface BillableUtilizationSnapshotMapper extends BaseMapper<BillableUtilizationSnapshot> {
}
