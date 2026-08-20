package com.njydsz.cronjob.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.GlueCodeVO;

/**
 * GLUE 脚本 Repository（domain 层契约）。
 *
 * <p>定义 GLUE 在线编码的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link GlueCodeVO}），非 DTO / infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface GlueCodeRepository {

  /**
   * 根据任务 ID 查询最新版本的 GLUE 代码。
   *
   * @param jobId 任务 ID
   * @return 最新版本的 GLUE 代码 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<GlueCodeVO> findLatestByJobId(String jobId);

  /**
   * 根据任务 ID 查询所有版本的 GLUE 代码列表（版本号降序）。
   *
   * @param jobId 任务 ID
   * @return GLUE 代码 VO 列表
   */
  List<GlueCodeVO> findAllByJobId(String jobId);

  /**
   * 根据任务 ID 查询所有版本的 GLUE 代码列表（版本号降序）。
   * <p>等同于 {@link #findAllByJobId(String)}，语义别名。</p>
   *
   * @param jobId 任务 ID
   * @return GLUE 代码 VO 列表（版本号降序）
   */
  List<GlueCodeVO> findByJobIdOrderByVersionDesc(String jobId);

  /**
   * 新增 GLUE 代码记录。
   *
   * @param vo GLUE 代码 VO（不含 id，由雪花生成器填充）
   * @return 新记录 ID
   */
  String insert(GlueCodeVO vo);
}
