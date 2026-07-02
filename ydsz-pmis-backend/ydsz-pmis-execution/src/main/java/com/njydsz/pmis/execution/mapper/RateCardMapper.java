package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.RateCardDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 对外费率卡片 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface RateCardMapper extends BaseMapper<RateCardDO> {

    RateCardDO selectByCode(@Param("code") String code);

    /** 按职级+项目类型+客户等级 命中当前生效的费率 */
    RateCardDO matchEffective(@Param("levelCode") String levelCode,
                              @Param("projectType") String projectType,
                              @Param("customerLevel") String customerLevel,
                              @Param("date") LocalDate date);

    List<RateCardDO> selectByLevel(@Param("levelCode") String levelCode);

    /** 全量查询 */
    List<RateCardDO> selectAll();
}
