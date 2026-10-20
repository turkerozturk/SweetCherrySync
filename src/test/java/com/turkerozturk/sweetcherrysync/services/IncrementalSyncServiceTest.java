package com.turkerozturk.sweetcherrysync.services;

import com.turkerozturk.sweetcherrysync.my.services.MyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncrementalSyncServiceTest {
    @TempDir Path folder;
    SQLiteDataSource source, target;
    JdbcTemplate from, to;
    SyncService service;

    @BeforeEach void setup() throws Exception {
        source = SyncServiceTest.dataSource(folder.resolve("source.ctb"), false);
        target = SyncServiceTest.dataSource(folder.resolve("target.ctb"), false);
        from = new JdbcTemplate(source);
        to = new JdbcTemplate(target);
        for (String sql : Files.readString(Path.of("schema-sqlite.sql")).split(";")) {
            if (!sql.isBlank()) { from.execute(sql); to.execute(sql); }
        }
        service = new SyncService(SyncServiceTest.dataSource(folder.resolve("source.ctb"), true),
            target, mock(MyService.class), 20, false);
    }

    void node(int id, long modified) {
        from.update("INSERT INTO node(node_id,name,txt,ts_creation,ts_lastsave) VALUES(?,?,?,?,?)",
            id, "n" + id, "content" + id, 1, modified);
        from.update("INSERT INTO children(node_id,father_id,sequence,master_id) VALUES(?,?,?,?)",
            id, 0, id, 0);
    }

    @Test void copiesChangedAndNewNodeDependentsWithoutFullBlobRead() {
        node(1, 1);
        from.update("INSERT INTO image(node_id,offset,png) VALUES(1,1,?)", new byte[]{1, 2, 3});
        from.update("INSERT INTO grid(node_id,offset,txt) VALUES(1,2,'old grid')");
        service.syncNodes();
        node(2, 2);
        from.update("INSERT INTO codebox(node_id,offset,txt) VALUES(2,1,'new code')");
        from.update("UPDATE node SET txt='changed',ts_lastsave=3 WHERE node_id=1");
        from.update("UPDATE image SET png=? WHERE node_id=1", new byte[]{9, 8});
        from.update("DELETE FROM grid WHERE node_id=1");
        service.syncIncremental();
        assertEquals("SUCCEEDED", service.getStatus().state());
        assertEquals(2, to.queryForObject("SELECT COUNT(*) FROM node", Integer.class));
        assertEquals("changed", to.queryForObject("SELECT txt FROM node WHERE node_id=1", String.class));
        assertArrayEquals(new byte[]{9,8}, to.queryForObject("SELECT png FROM image WHERE node_id=1", byte[].class));
        assertEquals(0, to.queryForObject("SELECT count(*) FROM grid", Integer.class));
        assertEquals("new code", to.queryForObject("SELECT txt FROM codebox WHERE node_id=2", String.class));
        service.syncIncremental();
        assertEquals("SUCCEEDED", service.getStatus().state());
    }

    @Test void deleteRealNodeAndAttachmentsWithoutRemovingUnrelatedNodes() {
        for (int i = 1; i <= 5; i++) node(i, i);
        from.update("INSERT INTO image(node_id,offset,png) VALUES(5,1,?)", new byte[]{5});
        service.syncNodes();
        from.update("DELETE FROM image WHERE node_id=5");
        from.update("DELETE FROM children WHERE node_id=5");
        from.update("DELETE FROM node WHERE node_id=5");
        service.syncIncremental();
        assertEquals(4, to.queryForObject("SELECT COUNT(*) FROM node", Integer.class));
        assertEquals(0, to.queryForObject("SELECT COUNT(*) FROM image", Integer.class));
        assertEquals(4, to.queryForObject("SELECT COUNT(*) FROM children", Integer.class));
    }

    @Test void bookmarkRemovalAndReorderAndSharedChildChangesDoNotRequireNodeModification() {
        node(1, 1);
        from.update("INSERT INTO children VALUES(10,0,5,1)");
        from.update("INSERT INTO bookmark VALUES(1,1)");
        service.syncNodes();
        from.update("DELETE FROM children WHERE node_id=10");
        from.update("INSERT INTO children VALUES(11,0,6,1)");
        from.update("UPDATE bookmark SET sequence=7 WHERE node_id=1");
        service.syncIncremental();
        assertEquals(0, to.queryForObject("SELECT COUNT(*) FROM children WHERE node_id=10", Integer.class));
        assertEquals(1, to.queryForObject("SELECT COUNT(*) FROM children WHERE node_id=11", Integer.class));
        assertEquals(7, to.queryForObject("SELECT sequence FROM bookmark WHERE node_id=1", Integer.class));
        assertEquals(1, to.queryForObject("SELECT COUNT(*) FROM node", Integer.class));
        from.update("DELETE FROM bookmark");
        service.syncIncremental();
        assertEquals(0, to.queryForObject("SELECT COUNT(*) FROM bookmark", Integer.class));
    }

    @Test void rollsBackIncrementalOnWriteFailureAndRetrySucceeds() {
        node(1, 1);
        from.update("INSERT INTO image(node_id,offset,png) VALUES(1,1,?)", new byte[]{1});
        service.syncNodes();
        from.update("UPDATE node SET txt='new',ts_lastsave=2 WHERE node_id=1");
        from.update("UPDATE image SET png=? WHERE node_id=1", new byte[]{2});
        to.execute("CREATE TRIGGER fail_image BEFORE UPDATE ON image BEGIN SELECT RAISE(ABORT,'fail image'); END");
        assertThrows(RuntimeException.class, service::syncIncremental);
        assertEquals("FAILED", service.getStatus().state());
        assertEquals("content1", to.queryForObject("SELECT txt FROM node WHERE node_id=1", String.class));
        assertArrayEquals(new byte[]{1}, to.queryForObject("SELECT png FROM image", byte[].class));
        to.execute("DROP TRIGGER fail_image");
        service.syncIncremental();
        assertEquals("new", to.queryForObject("SELECT txt FROM node WHERE node_id=1", String.class));
        assertArrayEquals(new byte[]{2}, to.queryForObject("SELECT png FROM image", byte[].class));
    }

    @Test void supportsLegacySourceWithoutChildrenMasterId() {
        from.execute("ALTER TABLE children DROP COLUMN master_id");
        from.update("INSERT INTO node(node_id,name,txt,ts_creation,ts_lastsave) VALUES(1,'old','body',1,1)");
        from.update("INSERT INTO children(node_id,father_id,sequence) VALUES(1,0,1)");
        service.syncIncremental();
        assertEquals(1, to.queryForObject("SELECT COUNT(*) FROM node", Integer.class));
        assertEquals(0, to.queryForObject("SELECT master_id FROM children", Integer.class));
        service.syncIncremental();
        assertEquals("SUCCEEDED", service.getStatus().state());
    }

    @Test void verificationFailureRollsBackNodeChange() {
        node(1, 1);
        service.syncNodes();
        from.update("UPDATE node SET txt='new',ts_lastsave=2 WHERE node_id=1");
        to.execute("CREATE TRIGGER corrupt AFTER UPDATE ON node BEGIN UPDATE node SET txt='tampered' WHERE node_id=NEW.node_id; END");
        assertThrows(RuntimeException.class, service::syncIncremental);
        assertEquals("content1", to.queryForObject("SELECT txt FROM node WHERE node_id=1", String.class));
    }

    @Test void guardAgainstExcessiveDeletions() {
        for (int i = 1; i <= 5; i++) node(i, 1);
        service.syncNodes();
        from.update("DELETE FROM children WHERE node_id >= 3");
        from.update("DELETE FROM node WHERE node_id >= 3");
        assertThrows(SyncService.SyncSafetyException.class, service::syncIncremental);
        assertEquals(5, to.queryForObject("SELECT COUNT(*) FROM node", Integer.class));
    }

    @Test void unchangedNodeTargetBlobTamperIsNotRepairedByIncrementalButLegacyCanRepair() {
        node(1, 1);
        from.update("INSERT INTO image(node_id,offset,png) VALUES(1,1,?)", new byte[]{1});
        service.syncNodes();
        to.update("UPDATE image SET png=? WHERE node_id=1", new byte[]{99});
        service.syncIncremental();
        assertArrayEquals(new byte[]{99}, to.queryForObject("SELECT png FROM image", byte[].class));
        service.syncNodes();
        assertArrayEquals(new byte[]{1}, to.queryForObject("SELECT png FROM image", byte[].class));
    }
}
