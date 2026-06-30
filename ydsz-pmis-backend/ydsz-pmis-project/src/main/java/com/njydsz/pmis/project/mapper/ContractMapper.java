package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ContractDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContractMapper extends BaseMapper<ContractDO> {

    ContractDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int adjustTotalAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);

    List<Map<String, Object>> aggregateByStatus(@Param("tenantId") Long tenantId);

    List<Map<String, Object>> aggregateByRisk(@Param("tenantId") Long tenantId);

    Long countByStatus(@Param("status") String status, @Param("tenantId") Long tenantId);
}
