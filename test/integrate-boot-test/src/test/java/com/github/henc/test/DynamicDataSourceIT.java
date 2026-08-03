package com.github.henc.test;

import com.mybatisflex.core.datasource.DataSourceKey;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies dynamic datasource support: with {@code integrate-boot.data.datasource.dynamic.enabled=true}
 * and two datasources declared under {@code mybatis-flex.datasource}, queries are routed to
 * the right database via {@link DataSourceKey}.
 */
@SpringBootTest
@ActiveProfiles("dynamic")
@Transactional
class DynamicDataSourceIT {

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // MyBatis-Flex's FlexDataSource holds the inner datasources. Initialise both with
        // an identical schema but distinct seed data, so we can tell them apart by content.
        runScriptOn("master", "CREATE TABLE IF NOT EXISTS `user` (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64), age INT)", "master");
        runScriptOn("slave", "CREATE TABLE IF NOT EXISTS `user` (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64), age INT)", "slave");
    }

    private void runScriptOn(String key, String ddl, String seedTag) throws Exception {
        DataSourceKey.use(key, () -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(ddl);
                // Tag rows so each datasource is identifiable. Truncate first to keep tests repeatable.
                stmt.execute("DELETE FROM `user`");
                stmt.execute("INSERT INTO `user` (user_name, age) VALUES ('" + seedTag + "-user', 1)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void defaultDatasourceIsMaster() {
        List<Row> rows = Db.selectAll("user");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getString("USER_NAME")).isEqualTo("master-user");
    }

    @Test
    void switchToSlaveByDataSourceKey() {
        List<Row> rows = DataSourceKey.use("slave", () -> Db.selectAll("user"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getString("USER_NAME")).isEqualTo("slave-user");
    }

    @Test
    void switchIsScopedToTheLambda() {
        // Inside the lambda the slave datasource is used.
        String inLambda = DataSourceKey.use("slave", () -> Db.selectAll("user").get(0).getString("USER_NAME"));
        assertThat(inLambda).isEqualTo("slave-user");

        // Outside the lambda we are back on the default (master).
        String afterLambda = Db.selectAll("user").get(0).getString("USER_NAME");
        assertThat(afterLambda).isEqualTo("master-user");
    }
}
