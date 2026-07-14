package com.njydsz.pmis.common.search.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.search.api.SearchFilter;
import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.core.SearchField;

/**
 * SearchProviderRegistry 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@DisplayName("SearchProviderRegistry 测试")
class SearchProviderRegistryTest {

    private SearchProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SearchProviderRegistry();
    }

    @Test
    @DisplayName("注册 Provider 后可通过类型查找")
    void register_andGetProvider() {
        SearchProvider<Object> provider = new TestProvider("project", "项目");
        registry.register(provider);

        SearchProvider<Object> found = registry.getProvider("project");
        assertThat(found).isNotNull();
        assertThat(found.getType()).isEqualTo("project");
    }

    @Test
    @DisplayName("未注册的类型返回 null")
    void getProvider_notRegistered_returnsNull() {
        SearchProvider<Object> found = registry.getProvider("nonexistent");
        assertThat(found).isNull();
    }

    @Test
    @DisplayName("自动注册构造器 — 传入 Provider 列表")
    void constructor_autoRegister() {
        SearchProvider<Object> p1 = new TestProvider("project", "项目");
        SearchProvider<Object> p2 = new TestProvider("contract", "合同");
        registry = new SearchProviderRegistry(List.of(p1, p2));

        assertThat(registry.getAllTypes()).containsExactlyInAnyOrder("project", "contract");
    }

    @Test
    @DisplayName("空列表构造器不报错")
    void constructor_emptyList() {
        registry = new SearchProviderRegistry(Collections.emptyList());
        assertThat(registry.getAllTypes()).isEmpty();
    }

    @Test
    @DisplayName("null 列表构造器不报错")
    void constructor_nullList() {
        registry = new SearchProviderRegistry(null);
        assertThat(registry.getAllTypes()).isEmpty();
    }

    @Test
    @DisplayName("重复注册同类型 Provider 会覆盖")
    void register_duplicate_overwrites() {
        registry.register(new TestProvider("project", "项目A"));
        registry.register(new TestProvider("project", "项目B"));

        assertThat(registry.getAllTypes()).hasSize(1);
    }

    @Test
    @DisplayName("注销 Provider")
    void unregister() {
        registry.register(new TestProvider("project", "项目"));
        registry.unregister("project");
        assertThat(registry.contains("project")).isFalse();
    }

    @Test
    @DisplayName("按类型列表过滤 Provider")
    void getProviders_byTypes() {
        registry.register(new TestProvider("project", "项目"));
        registry.register(new TestProvider("contract", "合同"));
        registry.register(new TestProvider("wiki", "知识库"));

        List<SearchProvider<?>> filtered = registry.getProviders(List.of("project", "wiki"));
        assertThat(filtered).hasSize(2);
    }

    @Test
    @DisplayName("空类型列表返回全部 Provider")
    void getProviders_emptyTypes_returnsAll() {
        registry.register(new TestProvider("project", "项目"));
        registry.register(new TestProvider("contract", "合同"));

        List<SearchProvider<?>> all = registry.getProviders(Collections.emptyList());
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("null Provider 注册被忽略")
    void register_null_ignored() {
        registry.register(null);
        assertThat(registry.getAllTypes()).isEmpty();
    }

    @Test
    @DisplayName("Provider 类型为 null 时注册被忽略")
    void register_nullType_ignored() {
        SearchProvider<Object> provider = new TestProvider(null, "无类型");
        registry.register(provider);
        assertThat(registry.getAllTypes()).isEmpty();
    }

    /**
     * 测试用 Provider 实现
     */
    private static class TestProvider implements SearchProvider<Object> {
        private final String type;
        private final String label;

        TestProvider(String type, String label) {
            this.type = type;
            this.label = label;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getTypeLabel() {
            return label;
        }

        @Override
        public IndexDocument toIndexDocument(Object entity) {
            return IndexDocument.builder().id("1").type(type).title("test").build();
        }

        @Override
        public List<SearchField> getSearchableFields() {
            return Collections.emptyList();
        }

        @Override
        public List<SearchFilter> getFilters(SearchProviderContext context) {
            return Collections.emptyList();
        }
    }
}
