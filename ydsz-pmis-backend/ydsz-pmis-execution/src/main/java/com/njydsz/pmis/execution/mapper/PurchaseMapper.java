package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.PurchaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseMapper extends BaseMapper<PurchaseDO> {

    PurchaseDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approverId") Long approverId, @Param("approverName") String approverName);
}
