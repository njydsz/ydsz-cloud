package com.njydsz.pmis.project.mapper.ruleengine;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ruleengine.RulePackDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则集 Mapper（P2-14）。
 *
 * <p>对应 {@code pmis_rule_pack} 表，提供按编码/版本/行业查询及下载量递增等操作。
 * 继承 {@link BaseMapper} 获得基础 CRUD 能力，扩展方法定义在 XML 中。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-14)
 */
@Mapper
public interface RulePackMapper extends BaseMapper<RulePackDO> {

    /**
     * 按规则集编码查询所有版本（按版本号倒序）。
     *
     * @param packCode 规则集编码
     * @return 版本列表（最新版本在前）
     */
    List<RulePackDO> selectByPackCode(@Param("packCode") String packCode);

    /**
     * 按规则集编码 + 版本号精确查询（P2-8 知识包版本管理）。
     *
     * @param packCode    规则集编码
     * @param packVersion 规则集版本号
     * @return 规则集实体；不存在时返回 null
     */
    RulePackDO selectByPackCodeVersion(@Param("packCode") String packCode, @Param("packVersion") String packVersion);

    /**
     * 按行业筛选规则集列表。
     *
     * @param industry 行业编码（如 FINANCE / MANUFACTURING）
     * @return 匹配行业的规则集列表
     */
    List<RulePackDO> selectByIndustry(@Param("industry") String industry);

    /**
     * 递增下载次数（+1）。
     *
     * <p>规则集安装时调用，使用 {@code UPDATE ... SET download_count = download_count + 1} 原子操作。
     *
     * @param id 规则集 ID
     * @return 受影响行数（1=成功, 0=规则集不存在）
     */
    int increaseDownloadCount(@Param("id") String id);
}
