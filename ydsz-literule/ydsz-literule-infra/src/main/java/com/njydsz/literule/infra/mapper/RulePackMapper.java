package com.njydsz.literule.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.literule.domain.entity.RulePackDO;

/**
 * 规则包 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_pack</code>。
 *
 * <p>规则包支持批量发布/回滚/导入导出，是规则运维的核心单位。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_pack_code — 包编码唯一索引
 *   <li>idx_status — 状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.literule.domain.entity.RulePackDO 规则包实体
 * @see com.njydsz.literule.server.service.RulePackService 规则包 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
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
   * @param packCode 规则集编码
   * @param packVersion 规则集版本号
   * @return 规则集实体；不存在时返回 null
   */
  RulePackDO selectByPackCodeVersion(
      @Param("packCode") String packCode, @Param("packVersion") String packVersion);

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
