package com.njydsz.pmis.project.mapper.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.finance.ProfitSimulationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 利润模拟 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ProfitSimulationMapper extends BaseMapper<ProfitSimulationDO> {

    /**
     * 按编码查询利润模拟记录
     *
     * @param code 模拟编码
     * @return 利润模拟对象，未找到返回 null
     */
    ProfitSimulationDO selectByCode(@Param("code") String code);

    /**
     * 按立项 ID 查询利润模拟列表
     *
     * @param initiationId 立项 ID
     * @return 利润模拟列表
     */
    List<ProfitSimulationDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 同项目下最大版本号
     *
     * @param initiationId 立项 ID
     * @return 最大版本号，无记录返回 null
     */
    Integer maxVersion(@Param("initiationId") String initiationId);
}
