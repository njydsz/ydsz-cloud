package com.njydsz.common.redis.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TenantRedisKeyPrefixer} 的租户隔离语义测试。
 *
 * <p>为什么先测它：多租户 Redis 隔离是**数据边界**。`prefixKey`/`deprefixKey`/`KeySerializer`
 * 三者任何一处不一致，都会让 A 租户读到 B 租户的缓存或锁 —— `ydsz-common-redis` 此前零测试。
 *
 * <p>钉住的口径（与类注释一致）：
 *
 * <ul>
 *   <li>启用且租户非空、非 {@code "0"} → {@code t:{tenantId}:{key}}
 *   <li>未启用 / 租户为 null / 租户为 {@code "0"}（超管）→ 原样返回，不加以区分
 *   <li>剥离只认**当前租户**的固定前缀；别人的前缀不得被误剥
 *   <li>序列化器 serialize→deserialize 必须回到原 key（往返一致）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("租户 Redis Key 前缀器")
class TenantRedisKeyPrefixerTest {

  private static TenantRedisKeyPrefixer prefixer(String tenantId) {
    return new TenantRedisKeyPrefixer(() -> tenantId, true);
  }

  @Test
  @DisplayName("启用时加 t:{tenantId}: 前缀，业务 key 原样保留")
  void should_prefix_key_with_tenant() {
    assertThat(prefixer("42").prefixKey("user:1")).isEqualTo("t:42:user:1");
  }

  @Test
  @DisplayName("未启用、租户为 null、租户为 0 时都不加前缀")
  void should_not_prefix_when_disabled_or_super_admin() {
    assertThat(new TenantRedisKeyPrefixer(() -> "42", false).prefixKey("user:1")).isEqualTo("user:1");
    assertThat(prefixer(null).prefixKey("user:1")).isEqualTo("user:1");
    assertThat(prefixer("0").prefixKey("user:1")).isEqualTo("user:1");
  }

  @Test
  @DisplayName("null key 安全透传")
  void should_pass_null_key_through() {
    assertThat(prefixer("42").prefixKey(null)).isNull();
    assertThat(prefixer("42").deprefixKey(null)).isNull();
  }

  @Test
  @DisplayName("剥离当前租户前缀；其他租户的前缀不得被误剥")
  void should_only_strip_own_tenant_prefix() {
    TenantRedisKeyPrefixer p = prefixer("42");

    assertThat(p.deprefixKey("t:42:user:1")).isEqualTo("user:1");
    assertThat(p.deprefixKey("t:43:user:1")).isEqualTo("t:43:user:1");
    assertThat(p.deprefixKey("user:1")).isEqualTo("user:1");
  }

  @Test
  @DisplayName("业务 key 里含 t: 字样也不会被误判（前缀带固定标记与冒号）")
  void should_not_mistake_business_key_for_prefix() {
    TenantRedisKeyPrefixer p = prefixer("42");

    assertThat(p.deprefixKey("t:420:user")).isEqualTo("t:420:user");
    assertThat(p.prefixKey("t:42:already")).isEqualTo("t:42:t:42:already");
  }

  @Test
  @DisplayName("Key 序列化器往返一致（写进去的 key 读出来还是原 key）")
  void serializer_should_round_trip() {
    TenantRedisKeyPrefixer p = prefixer("42");
    var serializer = p.createKeySerializer();

    byte[] bytes = serializer.serialize("lock:order:9");

    assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("t:42:lock:order:9");
    assertThat(serializer.deserialize(bytes)).isEqualTo("lock:order:9");
    assertThat(serializer.deserialize(null)).isNull();
  }

  @Test
  @DisplayName("关闭前缀时序列化器不加前缀")
  void serializer_should_be_transparent_when_disabled() {
    var serializer = new TenantRedisKeyPrefixer(() -> "42", false).createKeySerializer();

    assertThat(serializer.deserialize(serializer.serialize("user:1"))).isEqualTo("user:1");
  }
}
