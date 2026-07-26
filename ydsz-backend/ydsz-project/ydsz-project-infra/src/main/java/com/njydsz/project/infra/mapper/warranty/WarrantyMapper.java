package com.njydsz.project.infra.mapper.warranty;

import com.njydsz.project.domain.entity.warranty.WarrantyDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Warranty Mapper。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Mapper
public interface WarrantyMapper extends BaseMapper<WarrantyDO> {
}
