-- Creates missing dbo tables only; existing tables are not migrated.
-- Bookmark tablosu
IF OBJECT_ID(N'dbo.[bookmark]', N'U') IS NULL
BEGIN
CREATE TABLE dbo.[bookmark] (
    [node_id] INTEGER UNIQUE,
    [sequence] INTEGER
);
END
GO
IF OBJECT_ID(N'dbo.[children]', N'U') IS NULL
BEGIN
-- Children tablosu
CREATE TABLE dbo.[children] (
    [node_id] INTEGER UNIQUE,
    [father_id] INTEGER,
    [sequence] INTEGER,
    [master_id] INTEGER
);
END
GO
IF OBJECT_ID(N'dbo.[codebox]', N'U') IS NULL
BEGIN
-- Codebox tablosu
CREATE TABLE dbo.[codebox] (
    [node_id] INTEGER,
    [offset] INTEGER,
    [justification] NVARCHAR(MAX),
    [txt] NVARCHAR(MAX),
    [syntax] NVARCHAR(255),
    [width] INTEGER,
    [height] INTEGER,
    [is_width_pix] INTEGER,
    [do_highl_bra] INTEGER,
    [do_show_linenum] INTEGER
);
END
GO
IF OBJECT_ID(N'dbo.[grid]', N'U') IS NULL
BEGIN
-- Grid tablosu
CREATE TABLE dbo.[grid] (
    [node_id] INTEGER,
    [offset] INTEGER,
    [justification] NVARCHAR(MAX),
    [txt] NVARCHAR(MAX),
    [col_min] INTEGER,
    [col_max] INTEGER
);
END
GO
IF OBJECT_ID(N'dbo.[image]', N'U') IS NULL
BEGIN
-- Image tablosu
CREATE TABLE dbo.[image] (
    [node_id] INTEGER,
    [offset] INTEGER,
    [justification] NVARCHAR(MAX),
    [anchor] NVARCHAR(255),
    [png] VARBINARY(MAX),
    [filename] NVARCHAR(255),
    [link] NVARCHAR(MAX),
    [time] BIGINT
);
END
GO
IF OBJECT_ID(N'dbo.[node]', N'U') IS NULL
BEGIN
-- Node tablosu
CREATE TABLE dbo.[node] (
    [node_id] INTEGER UNIQUE,
    [name] NVARCHAR(255),
    [txt] NVARCHAR(MAX),
    [syntax] NVARCHAR(255),
    [tags] NVARCHAR(MAX),
    [is_ro] INTEGER,
    [is_richtxt] INTEGER,
    [has_codebox] INTEGER,
    [has_table] INTEGER,
    [has_image] INTEGER,
    [level] INTEGER,
    [ts_creation] BIGINT,
    [ts_lastsave] BIGINT
);
END
GO