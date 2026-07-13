package com.njydsz.pmis.project.infra.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.RateInternalDO;

/**
 * 内部成本费率 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface RateInternalMapper extends BaseMapper<RateInternalDO> {

    /**
     * 按编码查询内部成本费率
     *
     * @param code 费率编码
     * @return 内部成本费率对象，未找到返回 null
     */
    RateInternalDO selectByCode(@Param("code") String code);

    /**
     * 按职级+事业部 命中当前生效的费率
     *
     * @param levelCode    职级编码
     * @param departmentId 事业部 ID
     * @param date         生效日期
     * @return 生效的内部成本费率，未找到返回 null
     */
    RateInternalDO matchEffective(@Param("levelCode") String levelCode,
                                  @Param("departmentId") String departmentId,
                                  @Param("date") LocalDate date);

    /**
     * 按职级+事业部 查询费率列表
     *
     * @param levelCode    职级编码
     * @param departmentId 事业部 ID
     * @return 内部成本费率列表
     */
    List<RateInternalDO> selectByLevelAndDept(@Param("levelCode") String levelCode,
                                              @Param("departmentId") String departmentId);

    /**
     * 全量查询
     *
     * @return 内部成本费率列表
     */
    List<RateInternalDO> selectAll();
}
