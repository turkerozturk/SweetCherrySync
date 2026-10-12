package com.turkerozturk.sweetcherrysync.services;

import com.turkerozturk.sweetcherrysync.my.services.MyService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncServiceTest {
    @TempDir Path folder;
    SQLiteDataSource source, target;
    JdbcTemplate from, to;
    MyService statistics;
    SyncService service;

    @BeforeEach void setup() throws Exception {
        source = dataSource(folder.resolve("source.ctb"), false);
        target = dataSource(folder.resolve("target.ctb"), false);
        from = new JdbcTemplate(source);
        to = new JdbcTemplate(target);
        String schema = Files.readString(Path.of("schema-sqlite.sql"));
        for (String sql : schema.split(";")) {
            if (!sql.isBlank()) { from.execute(sql); to.execute(sql); }
        }
        statistics = mock(MyService.class);
        service = new SyncService(dataSource(folder.resolve("source.ctb"), true), target, statistics, 20, false);
    }

    static SQLiteDataSource dataSource(Path path, boolean readOnly) {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(readOnly);
        config.setBusyTimeout(2000);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + path);
        return ds;
    }
    void node(int id) {
        from.update("INSERT INTO node(node_id,name,txt,ts_creation,ts_lastsave) VALUES(?,?,?,?,?)", id, "n" + id, "old", 1, 1);
        from.update("INSERT INTO children(node_id,father_id,sequence,master_id) VALUES(?,?,?,?)", id, 0, id, 0);
    }
    String text(JdbcTemplate jdbc, String table) { return jdbc.queryForObject("SELECT txt FROM " + table + " WHERE node_id=1", String.class); }

    @Test void copiesEveryTableIncludingSharedNodesAndBinaryAttachments() {
        node(1);
        from.update("INSERT INTO children VALUES(2,0,2,1)"); // alias has no node row
        from.update("INSERT INTO bookmark VALUES(2,1)");
        from.update("INSERT INTO grid(node_id,offset,txt) VALUES(1,1,'table')");
        from.update("INSERT INTO codebox(node_id,offset,txt) VALUES(1,2,'code')");
        from.update("INSERT INTO image(node_id,offset,png,filename) VALUES(1,3,?,?)", new byte[]{0,1,-1}, "test.bin");
        service.syncNodes();
        assertEquals("SUCCEEDED", service.getStatus().state());
        for (String table : List.of("node", "bookmark", "grid", "codebox", "image")) {
            assertEquals(1, to.queryForObject("SELECT count(*) FROM " + table, Integer.class));
        }
        assertEquals(2, to.queryForObject("SELECT count(*) FROM children", Integer.class));
        assertArrayEquals(new byte[]{0,1,-1}, to.queryForObject("SELECT png FROM image", byte[].class));
    }

    @Test void repairsContentAndAttachmentsEvenWithUnchangedTimestampAndCounts() {
        node(1);
        from.update("INSERT INTO grid(node_id,offset,txt) VALUES(1,1,'old grid')");
        service.syncNodes();
        from.update("UPDATE node SET txt='new' WHERE node_id=1");
        from.update("UPDATE grid SET txt='new grid' WHERE node_id=1");
        service.syncNodes();
        assertEquals("new", text(to, "node"));
        assertEquals("new grid", text(to, "grid"));
        verify(statistics).set("updatedNodes", "1");
        verify(statistics).set("updatedGrids", "1");
    }

    @Test void unchangedRunDoesNotRewriteRows() {
        node(1);
        service.syncNodes();
        to.execute("CREATE TRIGGER forbid_update BEFORE UPDATE ON node BEGIN SELECT RAISE(ABORT,'unexpected update'); END");
        service.syncNodes();
        assertEquals("SUCCEEDED", service.getStatus().state());
    }

    @Test void rollsBackEarlierWritesWhenLaterAttachmentWriteFailsAndRetryRepairsEverything() {
        node(1);
        from.update("INSERT INTO image(node_id,offset,png) VALUES(1,1,?)", new byte[]{1});
        service.syncNodes();
        from.update("UPDATE node SET txt='new',ts_lastsave=2");
        from.update("UPDATE image SET png=?", new byte[]{2});
        to.execute("CREATE TRIGGER fail_image BEFORE UPDATE ON image BEGIN SELECT RAISE(ABORT,'simulated failure'); END");
        assertThrows(RuntimeException.class, service::syncNodes);
        assertEquals("FAILED", service.getStatus().state());
        assertEquals("old", text(to, "node"));
        assertEquals(1L, to.queryForObject("SELECT ts_lastsave FROM node", Long.class));
        assertArrayEquals(new byte[]{1}, to.queryForObject("SELECT png FROM image", byte[].class));
        to.execute("DROP TRIGGER fail_image");
        service.syncNodes();
        assertEquals("new", text(to, "node"));
        assertArrayEquals(new byte[]{2}, to.queryForObject("SELECT png FROM image", byte[].class));
    }

    @Test void verificationDetectsSilentDatabaseChangesAndRollsBack() {
        node(1);
        service.syncNodes();
        from.update("UPDATE node SET txt='new'");
        to.execute("CREATE TRIGGER corrupt AFTER UPDATE ON node BEGIN UPDATE node SET txt='corrupted' WHERE node_id=NEW.node_id; END");
        assertThrows(IllegalStateException.class, service::syncNodes);
        assertEquals("old", text(to, "node"));
    }

    @Test void blocksEmptyAndLargeDeletionBeforeAnyWrites() {
        for (int i = 1; i <= 5; i++) node(i);
        service.syncNodes();
        from.update("DELETE FROM children WHERE node_id>3");
        from.update("DELETE FROM node WHERE node_id>3");
        from.update("UPDATE node SET txt='new' WHERE node_id=1");
        assertThrows(IllegalStateException.class, service::syncNodes);
        assertEquals(5, to.queryForObject("SELECT count(*) FROM node", Integer.class));
        assertEquals("old", text(to, "node"));
        from.update("DELETE FROM children"); from.update("DELETE FROM node");
        assertThrows(IllegalStateException.class, service::syncNodes);
        assertEquals(5, to.queryForObject("SELECT count(*) FROM node", Integer.class));
    }

    @Test void normalDeletionRemovesDependantsAndExplicitOverrideAllowsEmptyMirror() {
        for (int i = 1; i <= 5; i++) node(i);
        from.update("INSERT INTO image(node_id,offset,png) VALUES(5,1,?)", new byte[]{1});
        service.syncNodes();
        from.update("DELETE FROM image WHERE node_id=5");
        from.update("DELETE FROM children WHERE node_id=5");
        from.update("DELETE FROM node WHERE node_id=5");
        service.syncNodes();
        assertEquals(4, to.queryForObject("SELECT count(*) FROM node", Integer.class));
        assertEquals(0, to.queryForObject("SELECT count(*) FROM image", Integer.class));
        from.update("DELETE FROM children"); from.update("DELETE FROM node");
        new SyncService(source, target, statistics, 100, true).syncNodes();
        assertEquals(0, to.queryForObject("SELECT count(*) FROM node", Integer.class));
    }

    @Test void rejectsUnknownSchemaAndDuplicateKeysWithoutChangingTarget() {
        node(1); service.syncNodes();
        from.execute("ALTER TABLE node ADD COLUMN unknown TEXT");
        assertThrows(IllegalStateException.class, service::syncNodes);
        assertEquals("old", text(to, "node"));
        from.execute("ALTER TABLE node DROP COLUMN unknown");
        from.update("INSERT INTO grid(node_id,offset,txt) VALUES(1,1,'a')");
        from.update("INSERT INTO grid(node_id,offset,txt) VALUES(1,1,'b')");
        assertThrows(IllegalStateException.class, service::syncNodes);
        assertEquals(0, to.queryForObject("SELECT count(*) FROM grid", Integer.class));
    }

    @Test void rejectsSameSourceAndTargetEvenWhenSourceUsesUri() {
        node(1);
        var same = new SyncService(source, source, statistics, 100, true);
        assertThrows(IllegalStateException.class, same::syncNodes);
        assertTrue(SyncService.sameSqliteFile("jdbc:sqlite:" + folder.resolve("source.ctb").toUri() + "?mode=ro", source.getUrl()));
    }

    @Test void sourceConnectionIsActuallyReadOnly() {
        JdbcTemplate readOnly = new JdbcTemplate(dataSource(folder.resolve("source.ctb"), true));
        assertThrows(RuntimeException.class, () -> readOnly.update("DELETE FROM node"));
    }

    @Test void concurrentRunIsRejectedAndLockIsReleasedAfterSuccess() throws Exception {
        node(1);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(i -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return null; }).when(statistics).resetStatistics();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(service::syncNodes);
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertThrows(SyncService.SyncBusyException.class, service::syncNodes);
            release.countDown(); first.get(10, TimeUnit.SECONDS);
            service.syncNodes();
            assertEquals("SUCCEEDED", service.getStatus().state());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void monitoringFailureDoesNotMisreportCommittedDataAsFailure() {
        node(1);
        doThrow(new IllegalStateException("monitoring offline")).when(statistics).resetStatistics();
        service.syncNodes();
        assertEquals("SUCCEEDED", service.getStatus().state());
        assertEquals("old", text(to, "node"));
        assertTrue(service.getStatus().message().contains("unavailable"));
    }

    @Test void concurrentSourceSaveDoesNotMixVersionsAcrossTables() {
        node(1);
        from.execute("PRAGMA journal_mode=WAL");
        from.update("INSERT INTO grid(node_id,offset,txt) VALUES(1,1,'old grid')");
        AtomicBoolean changed = new AtomicBoolean();
        DataSource hooked = new DelegatingDataSource(dataSource(folder.resolve("source.ctb"), true)) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p, m, args) -> {
                    try {
                        Object result = m.invoke(connection, args);
                        if (m.getName().equals("createStatement")) {
                            Statement statement = (Statement) result;
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Statement.class}, (p2, m2, args2) -> {
                                if (m2.getName().equals("executeQuery") && ((String) args2[0]).contains("children") && changed.compareAndSet(false, true)) {
                                    from.update("UPDATE node SET txt='new'");
                                    from.update("UPDATE grid SET txt='new grid'");
                                }
                                try { return m2.invoke(statement, args2); }
                                catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                            });
                        }
                        return result;
                    } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
            }
        };
        SyncService hookedService = new SyncService(hooked, target, statistics, 20, false);
        hookedService.syncNodes();
        assertEquals("old", text(to, "node"));
        assertEquals("old grid", text(to, "grid"));
        hookedService.syncNodes();
        assertEquals("new", text(to, "node"));
        assertEquals("new grid", text(to, "grid"));
    }
    @Test void oldSourceWithoutMasterColumnCopiesToModernDestination() {
        node(1);
        from.execute("ALTER TABLE children DROP COLUMN master_id");
        service.syncNodes();
        assertEquals(0, to.queryForObject("SELECT master_id FROM children WHERE node_id=1", Integer.class));
        assertEquals("SUCCEEDED", service.getStatus().state());
    }

    @Test void oldDestinationSupportsPlainNodesButRejectsSharedNodesAtomically() {
        node(1);
        to.execute("ALTER TABLE children DROP COLUMN master_id");
        from.update("UPDATE children SET master_id=NULL");
        service.syncNodes();
        assertEquals("old", text(to, "node"));
        from.update("UPDATE node SET txt='new'");
        from.update("INSERT INTO children VALUES(2,0,2,1)");
        assertThrows(SyncService.SyncSafetyException.class, service::syncNodes);
        assertEquals("old", text(to, "node"));
        assertEquals(1, to.queryForObject("SELECT count(*) FROM children", Integer.class));
    }

    @Test void bothOldSchemasSupportOrderChanges() {
        node(1);
        from.execute("ALTER TABLE children DROP COLUMN master_id");
        to.execute("ALTER TABLE children DROP COLUMN master_id");
        service.syncNodes();
        from.update("UPDATE children SET sequence=5");
        service.syncNodes();
        assertEquals(5, to.queryForObject("SELECT sequence FROM children", Integer.class));
    }

    @Test void differentJdbcDatabaseCanBeSourceAndDestination() throws Exception {
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL("jdbc:h2:mem:crossdriver;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=OFFSET;DB_CLOSE_DELAY=-1");
        JdbcTemplate server = new JdbcTemplate(h2);
        for (String sql : Files.readString(Path.of("schema-sqlite.sql")).split(";")) {
            if (!sql.isBlank()) server.execute(sql);
        }
        node(1);
        from.update("UPDATE node SET txt='Türkçe 漢字',ts_lastsave=3000000000");
        from.update("INSERT INTO image(node_id,offset,png) VALUES(1,1,?)", new byte[]{1,2,-1});
        // H2 schema uses INTEGER here; widen the timestamp to exercise cross-driver 64-bit values.
        server.execute("ALTER TABLE node ALTER COLUMN ts_lastsave BIGINT");
        new SyncService(source, h2, statistics, 20, false).syncNodes();
        new SyncService(h2, target, statistics, 20, false).syncNodes();
        assertEquals("Türkçe 漢字", text(to, "node"));
        assertEquals(3000000000L, to.queryForObject("SELECT ts_lastsave FROM node", Long.class));
        assertArrayEquals(new byte[]{1,2,-1}, to.queryForObject("SELECT png FROM image", byte[].class));
    }

}
