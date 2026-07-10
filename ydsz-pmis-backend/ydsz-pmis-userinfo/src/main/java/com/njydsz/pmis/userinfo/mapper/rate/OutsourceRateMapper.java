package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.rate.OutsourceRateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 外包职级费率 Mapper（V1-V18）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface OutsourceRateMapper extends BaseMapper<OutsourceRateDO> {

    /**
     * 按级别编码 + 日期匹配生效中的费率（按版本号倒序取最新）
     *
     * @param rateCode 级别编码
     * @param date     生效日期
     * @return 生效费率记录，未找到返回 null
     */
    @Select("""
            SELECT * FROM pmis_outsource_rate
            WHERE rate_code = #{rateCode}
              AND effective_date <= #{date}
              AND (expire_date IS NULL OR expire_date >= #{date})
              AND deleted = 0
              AND status = 'ACTIVE'
            ORDER BY version DESC
            LIMIT 1
            """)
    OutsourceRateDO selectEffective(@Param("rateCode") String rateCode, @Param("date") LocalDate date);

    /**
     * 查询某日期生效中的所有外包费率
     *
     * @param date 生效日期
     * @return 生效费率列表（按排序序号、级别编码升序）
     */
    @Select("""
            SELECT * FROM pmis_outsource_rate
            WHERE effective_date <= #{date}
              AND (expire_date IS NULL OR expire_date >= #{date})
              AND deleted = 0
              AND status = 'ACTIVE'
            ORDER BY sort_order ASC, rate_code ASC
            """)
    List<OutsourceRateDO> listEffective(@Param("date") LocalDate date);
}
