package com.njydsz.userinfo.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.njydsz.userinfo.infra.entity.SamlIdpConfig;

/**
 * SAML 身份提供者配置 Mapper 接口（P2-1）。
 *
 * <p>对应数据表 {@code ydsz_idp_saml_config}，存储 SAML IdP 的元数据和证书。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_entity_id} — Entity ID 唯一索引</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface SamlIdpConfigMapper extends BaseMapper<SamlIdpConfig> {

  /**
   * 根据 Entity Id 查询 IdP 配置。
   *
   * @param entityId IdP Entity ID
   * @return IdP 配置 DO；不存在返回 null
   */
  @Select("SELECT * FROM ydsz_idp_saml_config WHERE entity_id = #{entityId} AND deleted = 0")
  SamlIdpConfig selectByEntityId(String entityId);

  /**
   * 查询所有已启用的 IdP 配置（按 sort_order 升序）。
   *
   * @return 已启用的 IdP 配置列表
   */
  @Select("SELECT * FROM ydsz_idp_saml_config WHERE status = 'ENABLED' AND deleted = 0 ORDER BY sort_order ASC")
  List<SamlIdpConfig> selectEnabledConfigs();
}
