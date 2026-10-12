package com.turkerozturk.sweetcherrysync;

import com.turkerozturk.sweetcherrysync.services.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "my.scheduler.enabled=false")
@AutoConfigureMockMvc
class ApplicationIntegrationTest {
    static final Path folder;
    static {
        try {
            folder = Files.createTempDirectory("sweetcherrysync-context-");
            SQLiteDataSource writer = new SQLiteDataSource();
            writer.setUrl("jdbc:sqlite:" + folder.resolve("source.ctb"));
            JdbcTemplate jdbc = new JdbcTemplate(writer);
            for (String sql : Files.readString(Path.of("schema-sqlite.sql")).split(";")) {
                if (!sql.isBlank()) jdbc.execute(sql);
            }
            jdbc.update("INSERT INTO node(node_id,name,txt,ts_lastsave) VALUES(1,'test','note',1)");
            jdbc.update("INSERT INTO children VALUES(1,0,1,0)");
            jdbc.execute("ALTER TABLE children DROP COLUMN master_id");
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("from.datasource.url", () -> "jdbc:sqlite:" + folder.resolve("source.ctb"));
        registry.add("destination.datasource.url", () -> "jdbc:sqlite:" + folder.resolve("target.ctb"));
        registry.add("destination.datasource.dialect", () -> "org.hibernate.community.dialect.SQLiteDialect");
        registry.add("destination.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("destination.datasource.username", () -> "");
        registry.add("destination.datasource.password", () -> "");
        registry.add("destination.datasource.schema", () -> "schema-sqlite.sql");
        registry.add("my.datasource.url", () -> "jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1");
    }
    @Autowired MockMvc mvc;
    @Autowired SyncService service;
    @Autowired @Qualifier("destinationDataSource") DataSource target;

    @Test void applicationStartsWithoutScheduledRunAndManualSyncRendersVerifiedStatus() throws Exception {
        assertEquals("IDLE", service.getStatus().state());
        mvc.perform(post("/sync")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SUCCEEDED"));
        assertEquals("note", new JdbcTemplate(target).queryForObject("SELECT txt FROM node WHERE node_id=1", String.class));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("SUCCEEDED")));
    }
}
