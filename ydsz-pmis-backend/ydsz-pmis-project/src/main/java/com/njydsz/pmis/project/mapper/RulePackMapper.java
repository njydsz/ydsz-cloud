package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.RulePackDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则集 Mapper（P2-14）
 */
@Mapper
public interface RulePackMapper extends BaseMapper<RulePackDO> {

    /**
     * 按规则集编码查询（按版本倒序）
     */
    List<RulePackDO> selectByPackCode(@Param("packCode") String packCode);

    /**
     * 按行业筛选
     */
    List<RulePackDO> selectByIndustry(@Param("industry") String industry);

    /**
     * 增加下载次数（+1）
     */
    int increaseDownloadCount(@Param("id") String id);
}
