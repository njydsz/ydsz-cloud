package com.njydsz.userinfo.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.njydsz.userinfo.infra.entity.SocialClient;

/**
 * 社交平台客户端配置 Mapper 接口（P1-1）。
 *
 * <p>对应数据表 {@code ydsz_social_client}，存储社交平台 OAuth2 应用的客户端配置。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_platform} — 平台标识唯一索引</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface SocialClientMapper extends BaseMapper<SocialClient> {

  /**
   * 查询所有已启用的平台配置（按 sort_order 升序）。
   *
   * @return 已启用的平台配置列表
   */
  @Select("SELECT * FROM ydsz_social_client WHERE status = 'ENABLED' AND deleted = 0 ORDER BY sort_order ASC")
  List<SocialClient> selectEnabledClients();

  /**
   * 根据平台标识查询客户端配置。
   *
   * @param platform 平台标识
   * @return 客户端配置；不存在返回 null
   */
  @Select("SELECT * FROM ydsz_social_client WHERE platform = #{platform} AND deleted = 0")
  SocialClient selectByPlatform(String platform);
}
