-- New dedicated mirror tables only; existing tables are not migrated.

CREATE TABLE IF NOT EXISTS "node" (
    "node_id" BIGINT NOT NULL,
    "name" TEXT,
    "txt" TEXT,
    "syntax" TEXT,
    "tags" TEXT,
    "is_ro" INTEGER,
    "is_richtxt" INTEGER,
    "has_codebox" INTEGER,
    "has_table" INTEGER,
    "has_image" INTEGER,
    "level" INTEGER,
    "ts_creation" BIGINT,
    "ts_lastsave" BIGINT,
    PRIMARY KEY ("node_id")
);

CREATE TABLE IF NOT EXISTS "children" (
    "node_id" BIGINT NOT NULL,
    "father_id" BIGINT,
    "sequence" BIGINT,
    "master_id" BIGINT,
    PRIMARY KEY ("node_id")
);

CREATE TABLE IF NOT EXISTS "bookmark" (
    "node_id" BIGINT NOT NULL,
    "sequence" BIGINT,
    PRIMARY KEY ("node_id")
);

CREATE TABLE IF NOT EXISTS "codebox" (
    "node_id" BIGINT NOT NULL,
    "offset" INTEGER NOT NULL,
    "justification" TEXT,
    "txt" TEXT,
    "syntax" TEXT,
    "width" INTEGER,
    "height" INTEGER,
    "is_width_pix" INTEGER,
    "do_highl_bra" INTEGER,
    "do_show_linenum" INTEGER,
    PRIMARY KEY ("node_id", "offset")
);

CREATE TABLE IF NOT EXISTS "grid" (
    "node_id" BIGINT NOT NULL,
    "offset" INTEGER NOT NULL,
    "justification" TEXT,
    "txt" TEXT,
    "col_min" INTEGER,
    "col_max" INTEGER,
    PRIMARY KEY ("node_id", "offset")
);

CREATE TABLE IF NOT EXISTS "image" (
    "node_id" BIGINT NOT NULL,
    "offset" INTEGER NOT NULL,
    "justification" TEXT,
    "anchor" TEXT,
    "png" BYTEA,
    "filename" TEXT,
    "link" TEXT,
    "time" BIGINT,
    PRIMARY KEY ("node_id", "offset")
);
