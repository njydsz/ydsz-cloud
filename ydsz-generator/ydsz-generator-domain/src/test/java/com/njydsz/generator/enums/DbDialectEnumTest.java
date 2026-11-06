package com.njydsz.generator.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DbDialectEnum} 单元测试。
 *
 * <p>覆盖：getter（dialect/urlPrefix/driverClass）、fromUrl 解析（前缀匹配、默认 MySQL、null/空/非法）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("数据库方言枚举")
class DbDialectEnumTest {

  @Nested
  @DisplayName("getter")
  class Accessors {

    @ParameterizedTest
    @CsvSource({
      "MYSQL, mysql, jdbc:mysql://, com.mysql.cj.jdbc.Driver",
      "POSTGRESQL, postgresql, jdbc:postgresql://, org.postgresql.Driver",
      "ORACLE, oracle, jdbc:oracle:thin:@, oracle.jdbc.OracleDriver"
    })
    @DisplayName("每个枚举的 dialect/urlPrefix/driverClass 一致")
    void accessors(
        DbDialectEnum dialect,
        String expectedDialect,
        String expectedPrefix,
        String expectedDriver) {
      assertThat(dialect.getDialect()).isEqualTo(expectedDialect);
      assertThat(dialect.getUrlPrefix()).isEqualTo(expectedPrefix);
      assertThat(dialect.getDriverClass()).isEqualTo(expectedDriver);
    }
  }

  @Nested
  @DisplayName("fromUrl 解析")
  class FromUrl {

    @ParameterizedTest
    @CsvSource({
      "jdbc:mysql://localhost:3306/db, MYSQL",
      "jdbc:mysql://127.0.0.1:3306/test?useSSL=false, MYSQL",
      "jdbc:postgresql://localhost:5432/db, POSTGRESQL",
      "jdbc:postgresql://192.168.1.1:5432/prod, POSTGRESQL",
      "jdbc:oracle:thin:@localhost:1521:orcl, ORACLE",
      "jdbc:oracle:thin:@//host:1521/service, ORACLE"
    })
    @DisplayName("前缀匹配返回对应方言")
    void prefixMatch(String url, DbDialectEnum expected) {
      assertThat(DbDialectEnum.fromUrl(url)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串返回默认 MYSQL")
    void nullAndEmpty(String url) {
      assertThat(DbDialectEnum.fromUrl(url)).isEqualTo(DbDialectEnum.MYSQL);
    }

    @Test
    @DisplayName("空白串返回默认 MYSQL")
    void blankReturnsMysql() {
      assertThat(DbDialectEnum.fromUrl("   ")).isEqualTo(DbDialectEnum.MYSQL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "jdbc:sqlserver://localhost:1433/db",
      "jdbc:h2:mem:test",
      "jdbc:sqlite:sample.db",
      "jdbc:mariadb://localhost:3306/db"
    })
    @DisplayName("未匹配前缀返回默认 MYSQL")
    void unknownPrefixReturnsMysql(String url) {
      assertThat(DbDialectEnum.fromUrl(url)).isEqualTo(DbDialectEnum.MYSQL);
    }
  }
}
