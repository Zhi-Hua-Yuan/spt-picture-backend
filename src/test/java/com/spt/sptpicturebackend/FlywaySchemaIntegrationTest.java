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

        assertThat(migrationCount).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("select count(*) from picture_task", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from search_index_state", Integer.class)).isEqualTo(1);
    }
}
