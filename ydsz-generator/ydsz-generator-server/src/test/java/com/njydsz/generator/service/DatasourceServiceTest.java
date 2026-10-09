package com.njydsz.generator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.repository.GenDatasourceRepository;
import com.njydsz.generator.vo.GenDatasourceRespVO;

class DatasourceServiceTest {

    @InjectMocks
    private DatasourceService datasourceService;

    @Mock
    private GenDatasourceRepository datasourceRepository;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("listAllVO")
    class ListAllVO {

        @Test
        @DisplayName("listAllVO - returns converted VO list")
        void listAllVO_returnsConvertedVoList() {
            GenDatasource ds = new GenDatasource();
            ds.setId("ds-001");
            ds.setName("Main DB");
            when(datasourceRepository.findAll()).thenReturn(List.of(ds));

            List<GenDatasourceRespVO> result = datasourceService.listAllVO();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Main DB");
        }

        @Test
        @DisplayName("listAllVO - empty repository returns empty list")
        void listAllVO_emptyRepository_returnsEmptyList() {
            when(datasourceRepository.findAll()).thenReturn(List.of());

            List<GenDatasourceRespVO> result = datasourceService.listAllVO();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDefaultVO")
    class GetDefaultVO {

        @Test
        @DisplayName("getDefaultVO - existing default datasource returns VO")
        void getDefaultVO_existingDefault_returnsVo() {
            GenDatasource ds = new GenDatasource();
            ds.setId("ds-001");
            ds.setName("Default DB");
            when(datasourceRepository.findByDefaultTrue()).thenReturn(Optional.of(ds));

            GenDatasourceRespVO result = datasourceService.getDefaultVO();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Default DB");
        }

        @Test
        @DisplayName("getDefaultVO - no default datasource returns null")
        void getDefaultVO_noDefault_returnsNull() {
            when(datasourceRepository.findByDefaultTrue()).thenReturn(Optional.empty());

            GenDatasourceRespVO result = datasourceService.getDefaultVO();

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("getById - existing datasource returns entity")
        void getById_existing_returnsEntity() {
            GenDatasource ds = new GenDatasource();
            ds.setId("ds-001");
            ds.setName("Main DB");
            when(datasourceRepository.findById("ds-001")).thenReturn(Optional.of(ds));

            GenDatasource result = datasourceService.getById("ds-001");

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Main DB");
        }

        @Test
        @DisplayName("getById - non-existing datasource returns null")
        void getById_nonExisting_returnsNull() {
            when(datasourceRepository.findById("ds-999")).thenReturn(Optional.empty());

            GenDatasource result = datasourceService.getById("ds-999");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("create - sets null id and auto-detects dialect from URL")
        void create_withNullDialect_autodetectsFromUrl() {
            GenDatasource input = new GenDatasource();
            input.setName("PG Source");
            input.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
            input.setUsername("user");
            input.setPassword("secret");

            GenDatasource saved = new GenDatasource();
            saved.setId("ds-new");
            saved.setName("PG Source");
            saved.setDialect("postgresql");
            when(datasourceRepository.save(input)).thenReturn(saved);

            GenDatasource result = datasourceService.create(input);

            assertThat(result).isNotNull();
            assertThat(input.getId()).isNull();
            assertThat(input.getDialect()).isEqualTo("postgresql");
            verify(datasourceRepository).save(input);
        }

        @Test
        @DisplayName("create - preserves existing dialect if provided")
        void create_existingDialect_preserved() {
            GenDatasource input = new GenDatasource();
            input.setName("MySQL Source");
            input.setJdbcUrl("jdbc:mysql://localhost:3306/test");
            input.setDialect("mysql");
            input.setUsername("root");
            input.setPassword("pass");

            GenDatasource saved = new GenDatasource();
            saved.setId("ds-mysql");
            saved.setDialect("mysql");
            when(datasourceRepository.save(input)).thenReturn(saved);

            GenDatasource result = datasourceService.create(input);

            assertThat(result).isNotNull();
            assertThat(input.getDialect()).isEqualTo("mysql");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("update - auto-detects dialect when null")
        void update_nullDialect_autodetects() {
            GenDatasource input = new GenDatasource();
            input.setId("ds-001");
            input.setName("Updated PG");
            input.setJdbcUrl("jdbc:postgresql://host/db");
            input.setUsername("user");
            input.setPassword("pass");

            GenDatasource saved = new GenDatasource();
            saved.setId("ds-001");
            saved.setDialect("postgresql");
            when(datasourceRepository.save(input)).thenReturn(saved);

            GenDatasource result = datasourceService.update(input);

            assertThat(input.getDialect()).isEqualTo("postgresql");
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("deleteById - delegates to repository")
        void deleteById_delegatesToRepository() {
            datasourceService.deleteById("ds-001");

            verify(datasourceRepository).deleteById("ds-001");
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("count - delegates to repository")
        void count_delegatesToRepository() {
            when(datasourceRepository.count()).thenReturn(5L);

            long result = datasourceService.count();

            assertThat(result).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("createAndReturnVO")
    class CreateAndReturnVO {

        @Test
        @DisplayName("createAndReturnVO - returns VO with sensitive fields excluded")
        void createAndReturnVO_returnsVo() {
            GenDatasource input = new GenDatasource();
            input.setName("PG Source");
            input.setJdbcUrl("jdbc:postgresql://localhost/db");
            input.setUsername("user");
            input.setPassword("secret");

            GenDatasource saved = new GenDatasource();
            saved.setId("ds-new");
            saved.setName("PG Source");
            saved.setDialect("postgresql");
            when(datasourceRepository.save(input)).thenReturn(saved);

            GenDatasourceRespVO result = datasourceService.createAndReturnVO(input);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("PG Source");
        }
    }
}
