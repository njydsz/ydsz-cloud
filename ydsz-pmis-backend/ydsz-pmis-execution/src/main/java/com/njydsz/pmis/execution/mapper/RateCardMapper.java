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

    /**
     * 按编码查询对外费率卡片
     *
     * @param code 费率编码
     * @return 对外费率卡片对象，未找到返回 null
     */
    RateCardDO selectByCode(@Param("code") String code);

    /**
     * 按职级+项目类型+客户等级 命中当前生效的费率
     *
     * @param levelCode     职级编码
     * @param projectType   项目类型
     * @param customerLevel 客户等级
     * @param date          生效日期
     * @return 生效的对外费率卡片，未找到返回 null
     */
    RateCardDO matchEffective(@Param("levelCode") String levelCode,
                              @Param("projectType") String projectType,
                              @Param("customerLevel") String customerLevel,
                              @Param("date") LocalDate date);

    /**
     * 按职级查询费率卡片列表
     *
     * @param levelCode 职级编码
     * @return 对外费率卡片列表
     */
    List<RateCardDO> selectByLevel(@Param("levelCode") String levelCode);

    /**
     * 全量查询
     *
     * @return 对外费率卡片列表
     */
    List<RateCardDO> selectAll();
}
