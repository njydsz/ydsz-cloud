package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.OpportunityDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OpportunityMapper extends BaseMapper<OpportunityDO> {

    OpportunityDO selectByCode(@Param("code") String code);

    List<Map<String, Object>> aggregateByStatus(@Param("tenantId") Long tenantId);

    List<Map<String, Object>> aggregateByLevel(@Param("tenantId") Long tenantId);

    /**
     * 更新商机状态
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("lostReason") String lostReason);

    /**
     * 统计指定状态的商机数量
     */
    Long countByStatus(@Param("status") String status, @Param("tenantId") Long tenantId);
}
