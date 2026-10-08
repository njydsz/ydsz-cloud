package com.njydsz.message.infra.repository;

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
import com.njydsz.message.domain.dto.MsgTemplateDTO;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.repository.MsgTemplateRepository;
import com.njydsz.message.domain.vo.MsgTemplateVO;

/**
 * 消息模板 Repository 集成测试（*IT.java，Failsafe 执行）。
 *
 * <p>验证：
 *
 * <ul>
 *   <li>save — 写入 PG，返回 true
 *   <li>findById — 按主键读取，字段与写入时一致
 *   <li>update — 更新 content 后读取验证
 *   <li>deleteById — 逻辑删除后读取返回 empty
 * </ul>
 *
 * <p>测试隔离：{@code @Transactional} 在测试方法结束时回滚全部写操作，不相不影响其他测试。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@SpringBootTest(classes = MsgTemplateRepositoryIT.TestApplication.class)
@TestPropertySource(properties = {
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.server-addr=127.0.0.1:0",
    "spring.cloud.nacos.discovery.server-addr=127.0.0.1:0",
    "ydsz.tenant.enabled=false",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:test-schema.sql",
    "logging.level.root=WARN",
    "logging.level.com.njydsz=INFO",
    "logging.level.org.apache.ibatis=DEBUG"
})
@Transactional
@DisplayName("MsgTemplateRepository IT")
class MsgTemplateRepositoryIT extends TestcontainersBase {

  @Autowired
  private MsgTemplateRepository repository;

  private String templateCode;

  @BeforeEach
  void setUp() {
    templateCode = "IT_" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  @DisplayName("save — 写入消息模板并持久化到 PG")
  void save_persistsTemplate() {
    MsgTemplateDTO dto = new MsgTemplateDTO();
    dto.setTemplateCode(templateCode);
    dto.setChannel("SMS");
    dto.setLocale("zh-CN");
    dto.setVersion("1");
    dto.setCategory("integration-test");
    dto.setSceneCode("IT_SCENE");
    dto.setSubject("集成测试标题");
    dto.setContent("Hello ${name}, code: ${code}");
    dto.setProvider("ALIYUN");
    dto.setSignName("妙手测试");
    dto.setStatus("ENABLED");

    boolean saved = repository.save(dto);

    assertThat(saved).isTrue();
  }

  @Test
  @DisplayName("findById — 按主键读取已持久化的模板")
  void findById_returnsPersistedTemplate() {
    MsgTemplateDTO dto = new MsgTemplateDTO();
    dto.setTemplateCode(templateCode);
    dto.setChannel("SMS");
    dto.setLocale("zh-CN");
    dto.setVersion("1");
    dto.setCategory("integration-test");
    dto.setSceneCode("IT_SCENE");
    dto.setSubject("查找测试");
    dto.setContent("content-${var}");
    dto.setStatus("ENABLED");

    repository.save(dto);
    MsgTemplateVO saved = repository.findOne(buildQueryDto(templateCode))
        .orElseThrow(() -> new AssertionError("模板保存后应以 template_code 查到"));

    Optional<MsgTemplateVO> result = repository.findById(saved.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getTemplateCode()).isEqualTo(templateCode);
    assertThat(result.get().getChannel()).isEqualTo("SMS");
    assertThat(result.get().getContent()).isEqualTo("content-${var}");
  }

  @Test
  @DisplayName("update — 更新模板内容后读取验证")
  void update_modifiesTemplateContent() {
    MsgTemplateDTO dto = new MsgTemplateDTO();
    dto.setTemplateCode(templateCode);
    dto.setChannel("EMAIL");
    dto.setLocale("en-US");
    dto.setVersion("1");
    dto.setCategory("integration-test");
    dto.setSubject("更新前");
    dto.setContent("old-content");
    dto.setStatus("DISABLED");

    repository.save(dto);
    MsgTemplateVO saved = repository.findOne(buildQueryDto(templateCode))
        .orElseThrow(() -> new AssertionError("预置数据未找到"));

    saved.setContent("new-content");
    saved.setSubject("更新后");
    MsgTemplateDTO updateDto = voToDto(saved);
    boolean updated = repository.update(updateDto);

    assertThat(updated).isTrue();

    MsgTemplateVO updatedVo = repository.findById(saved.getId()).orElseThrow();
    assertThat(updatedVo.getContent()).isEqualTo("new-content");
    assertThat(updatedVo.getSubject()).isEqualTo("更新后");
  }

  @Test
  @DisplayName("deleteById — 逻辑删除后 findById 返回 empty")
  void deleteById_logicallyRemovesTemplate() {
    MsgTemplateDTO dto = new MsgTemplateDTO();
    dto.setTemplateCode(templateCode);
    dto.setChannel("SMS");
    dto.setLocale("zh-CN");
    dto.setVersion("1");
    dto.setSubject("待删除");
    dto.setContent("will-be-deleted");
    dto.setStatus("ENABLED");

    repository.save(dto);
    MsgTemplateVO saved = repository.findOne(buildQueryDto(templateCode))
        .orElseThrow(() -> new AssertionError("预置数据未找到"));

    boolean deleted = repository.deleteById(saved.getId());

    assertThat(deleted).isTrue();
    assertThat(repository.findById(saved.getId())).isEmpty();
  }

  // ===== 私有辅助方法 =====

  private TemplateQueryDTO buildQueryDto(String code) {
    TemplateQueryDTO query = new TemplateQueryDTO();
    query.setTemplateCode(code);
    return query;
  }

  private MsgTemplateDTO voToDto(MsgTemplateVO vo) {
    MsgTemplateDTO dto = new MsgTemplateDTO();
    dto.setId(vo.getId());
    dto.setTemplateCode(vo.getTemplateCode());
    dto.setChannel(vo.getChannel());
    dto.setLocale(vo.getLocale());
    dto.setVersion(vo.getVersion());
    dto.setCategory(vo.getCategory());
    dto.setSceneCode(vo.getSceneCode());
    dto.setSubject(vo.getSubject());
    dto.setContent(vo.getContent());
    dto.setProvider(vo.getProvider());
    dto.setSignName(vo.getSignName());
    dto.setStatus(vo.getStatus());
    dto.setAuditStatus(vo.getAuditStatus());
    dto.setDescription(vo.getDescription());
    dto.setVariableDefs(vo.getVariableDefs());
    return dto;
  }

  /**
   * 测试专用 Spring Boot 启动类 — 仅扫描 Repository + JDBC 配置，避免引入 Web/Nacos/RocketMQ 等重型依赖。
   */
  @SpringBootApplication
  @ComponentScan(basePackages = {
      "com.njydsz.message.infra.repository",
      "com.njydsz.message.domain.converter"
  })
  @MapperScan("com.njydsz.message.infra.mapper.template")
  @Import(MybatisPlusConfiguration.class)
  static class TestApplication {
  }
}
