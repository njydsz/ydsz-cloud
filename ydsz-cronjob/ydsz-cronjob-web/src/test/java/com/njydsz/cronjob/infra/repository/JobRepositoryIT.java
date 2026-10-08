package com.njydsz.cronjob.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.test.TestcontainersBase;
import com.njydsz.common.jdbc.config.MybatisPlusConfiguration;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.infra.repository.impl.JobRepositoryImpl;

/**
 * Job 定时任务 Repository 集成测试（*IT.java，Failsafe 执行）。
 *
 * <p>验证：
 *
 * <ul>
 *   <li>insert — 写入 PG，返回主键 ID
 *   <li>findById — 按主键读取，字段与写入时一致
 *   <li>putUpdate — 更新 cronExpression 后读取验证
 *   <li>deleteById — 逻辑删除后读取返回 empty
 * </ul>
 *
 * <p>测试隔离：{@code @Transactional} 在测试方法结束时回滚全部写操作。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@SpringBootTest(classes = JobRepositoryIT.TestApplication.class)
@TestPropertySource(properties = {
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.server-addr=127.0.0.1:0",
    "spring.cloud.nacos.discovery.server-addr=127.0.0.1:0",
    "ydsz.tenant.enabled=false",
    "ydsz.cronjob.scan.normal-load-limit=100",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:test-schema.sql",
    "logging.level.root=WARN",
    "logging.level.com.njydsz=INFO",
    "logging.level.org.apache.ibatis=DEBUG"
})
@Transactional
@DisplayName("JobRepository IT")
class JobRepositoryIT extends TestcontainersBase {

  @Autowired
  private JobRepository repository;

  private String jobKey;

  @BeforeEach
  void setUp() {
    jobKey = "IT_" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  @DisplayName("insert — 写入定时任务并返回主键 ID")
  void insert_persistsJob() {
    JobPostDTO dto = new JobPostDTO();
    dto.setJobName("集成测试任务-" + jobKey);
    dto.setJobGroup("integration-test");
    dto.setJobKey(jobKey);
    dto.setHandler("heartbeatJobHandler");
    dto.setCronExpression("0 0/5 * * * ?");
    dto.setScheduleType("CRON");
    dto.setStatus("NORMAL");
    dto.setMaxRetries(3);
    dto.setBlockStrategy("SERIAL");
    dto.setMisfirePolicy("FIRE_NOW");

    String id = repository.insert(dto);

    assertThat(id).isNotNull().isNotBlank();
  }

  @Test
  @DisplayName("findById — 按主键读取已持久化的任务定义")
  void findById_returnsPersistedJob() {
    JobPostDTO dto = new JobPostDTO();
    dto.setJobName("查找测试任务-" + jobKey);
    dto.setJobGroup("integration-test");
    dto.setJobKey(jobKey);
    dto.setHandler("heartbeatJobHandler");
    dto.setCronExpression("0 0/10 * * * ?");
    dto.setScheduleType("CRON");
    dto.setStatus("NORMAL");
    dto.setMaxRetries(5);
    dto.setBlockStrategy("SERIAL");
    dto.setMisfirePolicy("SKIP");

    String id = repository.insert(dto);
    assertThat(id).isNotNull().isNotBlank();

    Optional<JobVO> result = repository.findById(id);

    assertThat(result).isPresent();
    JobVO vo = result.get();
    assertThat(vo.getJobKey()).isEqualTo(jobKey);
    assertThat(vo.getHandler()).isEqualTo("heartbeatJobHandler");
    assertThat(vo.getCronExpression()).isEqualTo("0 0/10 * * * ?");
    assertThat(vo.getStatus()).isEqualTo("NORMAL");
  }

  @Test
  @DisplayName("putUpdate — 更新 cronExpression 后读取验证")
  void putUpdate_modifiesCronExpression() {
    JobPostDTO dto = new JobPostDTO();
    dto.setJobName("更新测试任务-" + jobKey);
    dto.setJobGroup("integration-test");
    dto.setJobKey(jobKey);
    dto.setHandler("heartbeatJobHandler");
    dto.setCronExpression("0 0 0 * * ?");
    dto.setScheduleType("CRON");
    dto.setStatus("NORMAL");
    dto.setBlockStrategy("SERIAL");
    dto.setMisfirePolicy("FIRE_NOW");

    String id = repository.insert(dto);
    assertThat(id).isNotNull().isNotBlank();

    JobPutDTO putDto = new JobPutDTO();
    putDto.setId(id);
    putDto.setJobName("更新后-" + jobKey);
    putDto.setCronExpression("0 30 2 * * ?");
    putDto.setScheduleType("CRON");
    putDto.setBlockStrategy("SERIAL");
    putDto.setMisfirePolicy("FIRE_NOW");
    int affected = repository.putUpdate(putDto);

    assertThat(affected).isGreaterThanOrEqualTo(1);

    JobVO updated = repository.findById(id).orElseThrow();
    assertThat(updated.getCronExpression()).isEqualTo("0 30 2 * * ?");
    assertThat(updated.getJobName()).isEqualTo("更新后-" + jobKey);
  }

  @Test
  @DisplayName("deleteById — 逻辑删除后 findById 返回 empty")
  void deleteById_logicallyRemovesJob() {
    JobPostDTO dto = new JobPostDTO();
    dto.setJobName("待删除任务-" + jobKey);
    dto.setJobGroup("integration-test");
    dto.setJobKey(jobKey);
    dto.setHandler("heartbeatJobHandler");
    dto.setCronExpression("0 0 1 * * ?");
    dto.setScheduleType("CRON");
    dto.setStatus("NORMAL");
    dto.setBlockStrategy("SERIAL");
    dto.setMisfirePolicy("FIRE_NOW");

    String id = repository.insert(dto);
    assertThat(id).isNotNull().isNotBlank();

    int affected = repository.deleteById(id);

    assertThat(affected).isGreaterThanOrEqualTo(1);
    assertThat(repository.findById(id)).isEmpty();
  }

  /**
   * 测试专用 Spring Boot 启动类 — 仅扫描 Job Repository + JDBC 配置。
   */
  @SpringBootApplication
  @ComponentScan(basePackages = {
      "com.njydsz.cronjob.infra.repository",
      "com.njydsz.cronjob.domain.converter"
  })
  @MapperScan("com.njydsz.cronjob.infra.mapper.job")
  @Import(MybatisPlusConfiguration.class)
  static class TestApplication {
  }
}
