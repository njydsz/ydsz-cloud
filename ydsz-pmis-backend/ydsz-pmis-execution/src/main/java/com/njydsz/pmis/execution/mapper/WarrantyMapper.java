package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.WarrantyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WarrantyMapper extends BaseMapper<WarrantyDO> {

    WarrantyDO selectByCode(@Param("code") String code);

    List<WarrantyDO> selectByInitiation(@Param("initiationId") Long initiationId);

    /** 即将到期（end_date ≤ 截止日期 且状态为 ACTIVE/EXPIRING_SOON） */
    List<WarrantyDO> selectExpiringBefore(@Param("until") LocalDate until);

    /** 已过期（end_date < today 且状态非 EXPIRED/TERMINATED） */
    List<WarrantyDO> selectOverdue(@Param("today") LocalDate today);

    int markStatus(@Param("id") Long id, @Param("status") String status,
                   @Param("terminatedReason") String terminatedReason);
}
