package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.domain.query.PostPageQuery;
import com.njydsz.userinfo.domain.vo.PostVO;

/**
 * 岗位 Repository 接口
 *
 * <p>封装岗位表（{@code ydsz_post}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>入参为 DTO / Query / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface PostRepository {

  /**
   * 根据 ID 查询岗位。
   *
   * @param id 岗位 ID
   * @return 岗位 VO
   */
  Optional<PostVO> findById(String id);

  /**
   * 根据岗位编码查询岗位。
   *
   * @param postCode 岗位编码
   * @return 岗位 VO
   */
  Optional<PostVO> findByPostCode(String postCode);

  /**
   * 分页查询岗位列表。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<PostVO>> page(PostPageQuery query);

  /**
   * 条件查询岗位列表。
   *
   * @param query 查询参数
   * @return 岗位列表
   */
  List<PostVO> list(PostPageQuery query);

  /**
   * 批量根据 ID 查询岗位。
   *
   * @param ids 岗位 ID 集合
   * @return 岗位列表
   */
  List<PostVO> listByIds(Collection<String> ids);

  /**
   * 保存岗位（创建或更新）。
   *
   * <p>统一 DTO：创建时 {@code id} 可不传，更新时 {@code id} 必填。
   *
   * @param dto 岗位 DTO
   * @return 保存后的岗位 VO
   */
  PostVO save(PostDTO dto);

  /**
   * 根据 ID 删除岗位（逻辑删除）。
   *
   * @param id 岗位 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);

  /**
   * 统计符合条件的岗位数量。
   *
   * @param query 查询参数
   * @return 岗位数量
   */
  long countByQuery(PostPageQuery query);
}
