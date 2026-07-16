package com.njydsz.common.core.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.core.constant.PageConstants;

/**
 * {@link PageRequest} 单元测试。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("PageRequest 测试")
class PageRequestTest {

    @Nested
    @DisplayName("安全分页方法")
    class SafePagination {

        @Test
        @DisplayName("getSafePageNum 正常值原样返回")
        void getSafePageNum_normal() {
            PageRequest req = PageRequest.of(3L, 20L);
            assertThat(req.getSafePageNum()).isEqualTo(3L);
        }

        @Test
        @DisplayName("getSafePageNum null 返回 1")
        void getSafePageNum_null() {
            PageRequest req = new PageRequest();
            req.setPageNum(null);
            assertThat(req.getSafePageNum()).isEqualTo(1L);
        }

        @Test
        @DisplayName("getSafePageNum 负数返回 1")
        void getSafePageNum_negative() {
            PageRequest req = new PageRequest();
            req.setPageNum(-5L);
            assertThat(req.getSafePageNum()).isEqualTo(1L);
        }

        @Test
        @DisplayName("getSafePageSize 正常值原样返回")
        void getSafePageSize_normal() {
            PageRequest req = PageRequest.of(1L, 50L);
            assertThat(req.getSafePageSize()).isEqualTo(50L);
        }

        @Test
        @DisplayName("getSafePageSize null 返回默认值")
        void getSafePageSize_null() {
            PageRequest req = new PageRequest();
            req.setPageSize(null);
            assertThat(req.getSafePageSize()).isEqualTo((long) PageConstants.DEFAULT_PAGE_SIZE);
        }

        @Test
        @DisplayName("getSafePageSize 超过上限时返回 MAX_PAGE_SIZE")
        void getSafePageSize_exceedsMax() {
            PageRequest req = new PageRequest();
            req.setPageSize(99999L);
            assertThat(req.getSafePageSize()).isEqualTo((long) PageConstants.MAX_PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("偏移量计算")
    class OffsetCalculation {

        @Test
        @DisplayName("offset = (pageNum - 1) * pageSize")
        void getOffset() {
            PageRequest req = PageRequest.of(3L, 20L);
            assertThat(req.getOffset()).isEqualTo(40L);
        }

        @Test
        @DisplayName("第一页 offset 为 0")
        void getOffset_firstPage() {
            PageRequest req = PageRequest.of(1L, 10L);
            assertThat(req.getOffset()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("排序校验")
    class SortValidation {

        @Test
        @DisplayName("单字段排序通过校验")
        void singleField_passes() {
            PageRequest req = new PageRequest();
            req.setOrderBy("name");
            assertThat(req.getSafeOrderBy()).isEqualTo("name");
        }

        @Test
        @DisplayName("表名.字段名 格式通过校验")
        void tableDotField_passes() {
            PageRequest req = new PageRequest();
            req.setOrderBy("user.name");
            assertThat(req.getSafeOrderBy()).isEqualTo("user.name");
        }

        @Test
        @DisplayName("多字段排序通过校验（与 validateSort 一致）")
        void multiField_passes() {
            PageRequest req = new PageRequest();
            req.setOrderBy("name ASC, age DESC");
            assertThat(req.getSafeOrderBy()).isEqualTo("name ASC, age DESC");
            req.validateSort(); // 不抛异常
        }

        @Test
        @DisplayName("单字段带方向通过校验")
        void singleFieldWithDirection_passes() {
            PageRequest req = new PageRequest();
            req.setOrderBy("create_time DESC");
            assertThat(req.getSafeOrderBy()).isEqualTo("create_time DESC");
        }

        @Test
        @DisplayName("SQL 注入尝试返回 null")
        void sqlInjection_returnsNull() {
            PageRequest req = new PageRequest();
            req.setOrderBy("name; DROP TABLE users");
            assertThat(req.getSafeOrderBy()).isNull();
        }

        @Test
        @DisplayName("特殊字符返回 null")
        void specialChars_returnsNull() {
            PageRequest req = new PageRequest();
            req.setOrderBy("name' OR '1'='1");
            assertThat(req.getSafeOrderBy()).isNull();
        }

        @Test
        @DisplayName("空字符串返回 null")
        void emptyString_returnsNull() {
            PageRequest req = new PageRequest();
            req.setOrderBy("");
            assertThat(req.getSafeOrderBy()).isNull();
        }

        @Test
        @DisplayName("null 返回 null")
        void null_returnsNull() {
            PageRequest req = new PageRequest();
            req.setOrderBy(null);
            assertThat(req.getSafeOrderBy()).isNull();
        }

        @Test
        @DisplayName("validateSort 非法字段抛异常")
        void validateSort_invalidThrows() {
            PageRequest req = new PageRequest();
            req.setOrderBy("name; DROP TABLE");
            assertThatThrownBy(req::validateSort)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getSafeOrderDir DESC 返回 DESC")
        void getSafeOrderDir_desc() {
            PageRequest req = new PageRequest();
            req.setOrderDir("DESC");
            assertThat(req.getSafeOrderDir()).isEqualTo("DESC");
        }

        @Test
        @DisplayName("getSafeOrderDir 非法值返回 ASC")
        void getSafeOrderDir_invalid() {
            PageRequest req = new PageRequest();
            req.setOrderDir("RANDOM");
            assertThat(req.getSafeOrderDir()).isEqualTo("ASC");
        }
    }

    @Test
    @DisplayName("工厂方法 of 限制范围")
    void factoryOf_clamps() {
        PageRequest req = PageRequest.of(-1L, 99999L);
        assertThat(req.getSafePageNum()).isEqualTo(1L);
        assertThat(req.getSafePageSize()).isEqualTo((long) PageConstants.MAX_PAGE_SIZE);
    }
}
