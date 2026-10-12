package com.spt.sptpicturebackend;

import com.spt.sptpicturebackend.support.TestcontainersIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywaySchemaIntegrationTest extends TestcontainersIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesAllVersionedMigrationsToAnEmptyDatabase() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 1", Integer.class);

        assertThat(migrationCount).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject("select count(*) from picture_task", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name = 'search_index_state'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'space' and index_name = 'uk_space_active_owner_type'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'picture' and index_name = 'uk_picture_taskItemId'",
                Integer.class)).isEqualTo(1);
    }
}
