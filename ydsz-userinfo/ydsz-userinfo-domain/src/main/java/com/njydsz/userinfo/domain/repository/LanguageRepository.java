package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.LanguageDTO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;

/**
 * 语言配置 Repository 接口
 *
 * <p>封装语言配置表（{@code ydsz_acct_user_language}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>入参为 DTO / Query / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface LanguageRepository {

  /**
   * 根据 ID 查询语言配置。
   *
   * @param id 语言 ID
   * @return 语言 VO
   */
  Optional<LanguageVO> findById(String id);

  /**
   * 根据语言编码查询语言配置。
   *
   * @param languageCode 语言编码
   * @return 语言 VO
   */
  Optional<LanguageVO> findByLanguageCode(String languageCode);

  /**
   * 查询默认语言配置。
   *
   * @return 语言 VO
   */
  Optional<LanguageVO> findDefault();

  /**
   * 分页查询语言配置。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<LanguageVO>> page(LanguagePageQuery query);

  /**
   * 条件查询语言列表。
   *
   * @param query 查询参数
   * @return 语言列表
   */
  List<LanguageVO> list(LanguagePageQuery query);

  /**
   * 保存语言配置（创建或更新）。
   *
   * <p>统一 DTO：创建时 {@code id} 可不传，更新时 {@code id} 必填。
   *
   * @param dto 语言 DTO
   * @return 保存后的语言 VO
   */
  LanguageVO save(LanguageDTO dto);

  /**
   * 根据 ID 删除语言配置（逻辑删除）。
   *
   * @param id 语言 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);

  /**
   * 统计符合条件的语言数量。
   *
   * @param query 查询参数
   * @return 语言数量
   */
  long countByQuery(LanguagePageQuery query);
}
