package com.njydsz.pmis.userinfo.mapper.rate;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.rate.RankDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 职级 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface RankMapper extends BaseMapper<RankDO> {

    /**
     * 根据职级编码查询职级
     *
     * @param code 职级编码
     * @return 职级对象，未找到返回 null
     */
    @Select("SELECT * FROM pmis_rank WHERE level_code = #{code} AND deleted = 0 LIMIT 1")
    RankDO selectByCode(@Param("code") String code);

    /**
     * 查询全部启用职级（按 sort_order 排序）
     *
     * @return 职级列表
     */
    @Select("SELECT * FROM pmis_rank WHERE deleted = 0 ORDER BY sort_order, id")
    List<RankDO> selectAllEnabled();
}
