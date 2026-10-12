package com.turkerozturk.sweetcherrysync.configurations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FromDataSourceConfigTest {
    @TempDir Path folder;
    @Test void missingSourceIsRejectedWithoutCreatingFile() {
        Path path = folder.resolve("missing.ctb");
        FromDataSourceConfig config = new FromDataSourceConfig();
        ReflectionTestUtils.setField(config, "fromDbUrl", "jdbc:sqlite:" + path);
        assertThrows(IllegalArgumentException.class, config::fromDataSource);
        assertFalse(java.nio.file.Files.exists(path));
    }
    @Test void configuredSourcePoolIsReadOnlyAndWorksWithSpacesInPath() {
        Path path = folder.resolve("my notes.ctb");
        SQLiteDataSource writer = new SQLiteDataSource(); writer.setUrl("jdbc:sqlite:" + path);
        new JdbcTemplate(writer).execute("CREATE TABLE sample(id INTEGER)");
        FromDataSourceConfig config = new FromDataSourceConfig();
        ReflectionTestUtils.setField(config, "fromDbUrl", "jdbc:sqlite:" + path);
        try (com.zaxxer.hikari.HikariDataSource pool = (com.zaxxer.hikari.HikariDataSource) config.fromDataSource()) {
            JdbcTemplate reader = new JdbcTemplate(pool);
            assertEquals(0, reader.queryForObject("SELECT count(*) FROM sample", Integer.class));
            assertThrows(RuntimeException.class, () -> reader.update("INSERT INTO sample VALUES(1)"));
        }
    }
    @Test void sameDestinationIsRejectedBeforeSchemaInitialization() {
        Path path = folder.resolve("notes.ctb");
        DestinationDataSourceConfig config = new DestinationDataSourceConfig();
        ReflectionTestUtils.setField(config, "sourceDbUrl", "jdbc:sqlite:" + path);
        ReflectionTestUtils.setField(config, "destinationDbUrl", "jdbc:sqlite:" + path);
        assertThrows(IllegalArgumentException.class, config::destinationDataSource);
    }
    @Test void serverSourceUsesConfiguredDriverAndCredentials() {
        FromDataSourceConfig config = new FromDataSourceConfig();
        ReflectionTestUtils.setField(config, "fromDbUrl", "jdbc:h2:mem:sourcecredentials;DB_CLOSE_DELAY=-1");
        ReflectionTestUtils.setField(config, "fromDbDriverClassName", "org.h2.Driver");
        ReflectionTestUtils.setField(config, "fromDbUsername", "reader");
        ReflectionTestUtils.setField(config, "fromDbPassword", "test-password");
        try (com.zaxxer.hikari.HikariDataSource pool = (com.zaxxer.hikari.HikariDataSource) config.fromDataSource()) {
            assertTrue(pool.isReadOnly());
            assertEquals("READER", new JdbcTemplate(pool).queryForObject("SELECT CURRENT_USER", String.class));
        }
    }

}
