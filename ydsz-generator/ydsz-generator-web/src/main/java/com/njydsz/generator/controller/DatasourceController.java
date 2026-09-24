package com.njydsz.generator.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.service.DatasourceService;
import com.njydsz.generator.vo.GenDatasourceRespVO;

/**
 * 数据源管理 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@ApiVersion("26.09.01")
@Secured("ROLE_GENERATOR_ADMIN")
@RestController
@RequestMapping("/generator/datasources")
@RequiredArgsConstructor
public class DatasourceController {

  private final DatasourceService datasourceService;

  /**
   * 查询全部已配置的数据源。
   *
   * <p>返回当前租户下所有数据源配置（MySQL、PostgreSQL、Oracle、SQL Server 等），
   * 响应 VO 不含敏感字段 password。
   *
   * @return 数据源列表，每个元素包含 id、name、jdbcUrl、username、dialect（数据库方言）、
   *         isDefault（是否默认数据源）、description 等；无数据源时返回空列表
   */
  @GetMapping
  public YdszResponse<List<GenDatasourceRespVO>> list() {
    return YdszResponse.success(datasourceService.listAllVO());
  }

  /**
   * 获取标记为默认的数据源。
   *
   * <p>默认数据源在用户未显式指定数据源时使用。响应 VO 不含敏感字段 password。
   *
   * @return 默认数据源配置（VO），含 id、name、jdbcUrl、dialect 等；
   *         未配置默认数据源时返回 null
   */
  @GetMapping("/default")
  public YdszResponse<GenDatasourceRespVO> getDefault() {
    return YdszResponse.success(datasourceService.getDefaultVO());
  }

  /**
   * 测试数据源 JDBC 连接是否可用。
   *
   * <p>使用传入的 jdbcUrl/username/password 尝试建立数据库连接，
   * 不保存配置。用于在创建/更新数据源前验证连接参数正确性。
   * 支持 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库。
   *
   * @param datasource 数据源配置，至少需包含 jdbcUrl、username、password
   * @return true 表示连接成功，false 表示连接失败
   */
  @Audit(module = "数据源管理", action = AuditAction.OTHER, content = "'测试数据库连接'", recordRequest = false)
  @PostMapping("/test")
  public YdszResponse<Boolean> testConnection(@RequestBody GenDatasource datasource) {
    return YdszResponse.success(datasourceService.testConnection(datasource));
  }

  /**
   * 创建新的数据源配置。
   *
   * <p>支持 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库。
   * 密码将加密存储，不会出现在返回结果中。
   *
   * @param datasource 数据源实体，需包含 name（名称）、jdbcUrl（JDBC 连接串）、
   *                   username（用户名）、password（密码）、dialect（数据库方言，
   *                   如 mysql/postgresql/oracle/sqlserver）
   * @return 持久化后的数据源 VO（不含 password 字段）
   */
  @Audit(module = "数据源管理", action = AuditAction.CREATE, excludeParams = {"password", "url"}, recordRequest = false)
  @PostMapping
  public YdszResponse<GenDatasourceRespVO> create(@RequestBody GenDatasource datasource) {
    return YdszResponse.success(datasourceService.createAndReturnVO(datasource));
  }

  /**
   * 更新已有数据源配置。
   *
   * <p>需传入完整实体，id 必填。密码字段若传入则更新，若不传则保留原密码。
   *
   * @param datasource 数据源实体，id 必填，其余字段为需更新的内容
   * @return 更新后的数据源 VO（不含 password 字段）
   */
  @Audit(module = "数据源管理", action = AuditAction.UPDATE, excludeParams = {"password", "url"}, recordRequest = false)
  @PostMapping("/update")
  public YdszResponse<GenDatasourceRespVO> update(@RequestBody GenDatasource datasource) {
    return YdszResponse.success(datasourceService.updateAndReturnVO(datasource));
  }

  /**
   * 删除指定数据源配置。
   *
   * <p>若数据源下存在关联的表元数据缓存，将一并清除。
   * 删除默认数据源后，其他数据源的 isDefault 状态需重新指定。
   *
   * @param id 数据源 ID
   * @return 操作结果，成功时无数据返回（data 为 null）
   */
  @Audit(module = "数据源管理", action = AuditAction.DELETE, content = "'删除数据源:' + #id")
  @DeleteMapping("/{id}")
  public YdszResponse<Void> delete(@PathVariable Long id) {
    datasourceService.deleteById(id);
    return YdszResponse.success(null);
  }
}
