package com.njydsz.generator.service;

import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.enums.DbDialectEnum;
import com.njydsz.generator.repository.GenDatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * 数据源领域服务。
 *
 * <p>管理代码生成器的数据库连接配置（多数据源），提供连接测试、CRUD 等能力。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

  private final GenDatasourceRepository datasourceRepository;

  /**
   * 查询全部数据源。
   *
   * @return 数据源列表
   */
  public List<GenDatasource> listAll() {
    return datasourceRepository.findAll();
  }

  /**
   * 查询默认数据源。
   *
   * @return Optional 数据源
   */
  public GenDatasource getDefault() {
    return datasourceRepository.findByDefaultFlagTrue().orElse(null);
  }

  /**
   * 根据 ID 查询数据源。
   *
   * @param id 数据源 ID
   * @return Optional 数据源
   */
  public GenDatasource getById(Long id) {
    return datasourceRepository.findById(id).orElse(null);
  }

  /**
   * 测试数据库连接。
   *
   * @param datasource 待测试的数据源
   * @return 连接是否成功
   */
  public boolean testConnection(GenDatasource datasource) {
    try {
      Class.forName(datasource.getDialect().getDriverClass());
      try (Connection conn = DriverManager.getConnection(
          datasource.getJdbcUrl(), datasource.getUsername(), datasource.getPassword())) {
        return conn != null && !conn.isClosed();
      }
    } catch (Exception e) {
      log.warn("数据源连接测试失败 name={} error={}", datasource.getName(), e.getMessage());
      return false;
    }
  }

  /**
   * 创建数据源。
   *
   * @param datasource 数据源实体
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenDatasource create(GenDatasource datasource) {
    datasource.setId(null);
    if (datasource.getDialect() == null) {
      datasource.setDialect(DbDialectEnum.fromUrl(datasource.getJdbcUrl()));
    }
    return datasourceRepository.save(datasource);
  }

  /**
   * 更新数据源。
   *
   * @param datasource 数据源实体
   * @return 持久化后的实体
   */
  @Transactional(rollbackFor = Exception.class)
  public GenDatasource update(GenDatasource datasource) {
    if (datasource.getDialect() == null) {
      datasource.setDialect(DbDialectEnum.fromUrl(datasource.getJdbcUrl()));
    }
    return datasourceRepository.save(datasource);
  }

  /**
   * 删除数据源。
   *
   * @param id 数据源 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteById(Long id) {
    datasourceRepository.deleteById(id);
    log.info("删除数据源 id={}", id);
  }

  /**
   * 统计数据源总数。
   *
   * @return 总数
   */
  public long count() {
    return datasourceRepository.count();
  }
}
