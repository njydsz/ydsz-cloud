package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.InitiationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface InitiationMapper extends BaseMapper<InitiationDO> {

    InitiationDO selectByCode(@Param("code") String code);

    int updateStage(@Param("id") Long id,
                    @Param("stage") String stage,
                    @Param("gate") String gate);

    List<Map<String, Object>> aggregateByStage(@Param("tenantId") Long tenantId);

    Long countByStage(@Param("stage") String stage, @Param("tenantId") Long tenantId);
}
