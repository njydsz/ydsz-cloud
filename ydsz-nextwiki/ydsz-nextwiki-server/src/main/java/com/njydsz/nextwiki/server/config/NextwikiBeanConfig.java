package com.njydsz.nextwiki.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.service.FilePermissionDomainService;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService;
import com.njydsz.nextwiki.domain.service.FolderDomainService;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.nextwiki.domain.service.SearchDomainService;
import com.njydsz.nextwiki.domain.service.ShareAccessLogDomainService;
import com.njydsz.nextwiki.domain.service.ShareLinkDomainService;
import com.njydsz.nextwiki.domain.service.SpaceDomainService;
import com.njydsz.nextwiki.domain.service.TagDomainService;
import com.njydsz.nextwiki.domain.service.TrashDomainService;
import com.njydsz.nextwiki.server.service.SearchQueryParser;

/**
 * NextWiki 领域服务 Bean 注册配置。
 *
 * <p>将 domain 层的纯领域对象注册为 Spring Bean，使其能被 server 层的 Application Service 通过构造函数注入使用。
 *
 * <p>domain 层本身不携带任何 Spring 注解（{@code @Service}、{@code @Component} 等），
 * 所有 Spring 相关的 Bean 定义集中在本类中，确保 domain 层的纯净性。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
public class NextwikiBeanConfig {

  // ==================== 基础设施 Bean ====================

  /**
   * 注册 BCrypt 密码编码器 Bean。
   *
   * <p>用于对分享链接的访问密码进行安全散列存储与校验，供 {@link ShareLinkDomainService} 使用。
   *
   * @return BCryptPasswordEncoder 实例（线程安全，可全局复用）
   */
  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  // ==================== 领域服务 Bean ====================

  /**
   * 注册回收站领域服务 Bean。
   *
   * <p><b>DDD 合规：</b>domain 层 {@link TrashDomainService} 不依赖 Spring {@code ApplicationEventPublisher}，
   * 事件由应用层发布。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return TrashDomainService 实例
   */
  @Bean
  public TrashDomainService trashDomainService(SnowflakeIdGenerator snowflakeIdGenerator) {
    return new TrashDomainService(snowflakeIdGenerator);
  }

  /**
   * 注册标签领域服务 Bean。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return TagDomainService 实例
   */
  @Bean
  public TagDomainService tagDomainService(SnowflakeIdGenerator snowflakeIdGenerator) {
    return new TagDomainService(snowflakeIdGenerator);
  }

  /**
   * 注册知识库空间领域服务 Bean。
   *
   * @return SpaceDomainService 实例
   */
  @Bean
  public SpaceDomainService spaceDomainService() {
    return new SpaceDomainService();
  }

  /**
   * 注册分享链接领域服务 Bean。
   *
   * <p><b>DDD 合规：</b>domain 层 {@link ShareLinkDomainService} 不依赖 Redis 或 BCrypt，
   * 密码哈希由应用层通过 {@code BCryptPasswordEncoder} 完成后传入，Redis 防暴力破解由应用层负责。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return ShareLinkDomainService 实例
   */
  @Bean
  public ShareLinkDomainService shareLinkDomainService(
      SnowflakeIdGenerator snowflakeIdGenerator) {
    return new ShareLinkDomainService(snowflakeIdGenerator);
  }

  /**
   * 注册分享访问日志领域服务 Bean。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return ShareAccessLogDomainService 实例
   */
  @Bean
  public ShareAccessLogDomainService shareAccessLogDomainService(
      SnowflakeIdGenerator snowflakeIdGenerator) {
    return new ShareAccessLogDomainService(snowflakeIdGenerator);
  }

  /**
   * 注册搜索语法解析器 Bean。
   *
   * @return SearchQueryParser 实例
   */
  @Bean
  public SearchQueryParser searchQueryParser() {
    return new SearchQueryParser();
  }

  /**
   * 注册搜索领域服务 Bean。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return SearchDomainService 实例
   */
  @Bean
  public SearchDomainService searchDomainService(SnowflakeIdGenerator snowflakeIdGenerator) {
    return new SearchDomainService(snowflakeIdGenerator);
  }

  /**
   * 注册配额领域服务 Bean。
   *
   * @return QuotaDomainService 实例
   */
  @Bean
  public QuotaDomainService quotaDomainService() {
    return new QuotaDomainService();
  }

  /**
   * 注册目录树领域服务 Bean。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return FolderDomainService 实例
   */
  @Bean
  public FolderDomainService folderDomainService(SnowflakeIdGenerator snowflakeIdGenerator) {
    return new FolderDomainService(snowflakeIdGenerator);
  }

  /**
   * 注册文件版本领域服务 Bean。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return FileVersionDomainService 实例
   */
  @Bean
  public FileVersionDomainService fileVersionDomainService(
      SnowflakeIdGenerator snowflakeIdGenerator) {
    return new FileVersionDomainService(snowflakeIdGenerator);
  }

  /**
   * 注册文件权限领域服务 Bean。
   *
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return FilePermissionDomainService 实例
   */
  @Bean
  public FilePermissionDomainService filePermissionDomainService(
      SnowflakeIdGenerator snowflakeIdGenerator) {
    return new FilePermissionDomainService(snowflakeIdGenerator);
  }
}
