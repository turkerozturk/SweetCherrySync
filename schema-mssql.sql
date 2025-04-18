-- Bookmark tablosu
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'bookmark' AND type = 'U')
BEGIN
CREATE TABLE bookmark (
    node_id INTEGER UNIQUE,
    sequence INTEGER
);
END
GO
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'children' AND type = 'U')
BEGIN
-- Children tablosu
CREATE TABLE children (
    node_id INTEGER UNIQUE,
    father_id INTEGER,
    sequence INTEGER,
    master_id INTEGER
);
END
GO
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'codebox' AND type = 'U')
BEGIN
-- Codebox tablosu
CREATE TABLE codebox (
    node_id INTEGER,
    offset INTEGER,
    justification NVARCHAR(MAX),
    txt NVARCHAR(MAX),
    syntax NVARCHAR(255),
    width INTEGER,
    height INTEGER,
    is_width_pix INTEGER,
    do_highl_bra INTEGER,
    do_show_linenum INTEGER
);
END
GO
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'grid' AND type = 'U')
BEGIN
-- Grid tablosu
CREATE TABLE grid (
    node_id INTEGER,
    offset INTEGER,
    justification NVARCHAR(MAX),
    txt NVARCHAR(MAX),
    col_min INTEGER,
    col_max INTEGER
);
END
GO
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'image' AND type = 'U')
BEGIN
-- Image tablosu
CREATE TABLE image (
    node_id INTEGER,
    offset INTEGER,
    justification NVARCHAR(MAX),
    anchor NVARCHAR(255),
    png VARBINARY(MAX),
    filename NVARCHAR(255),
    link NVARCHAR(MAX),
    time INTEGER
);
END
GO
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'node' AND type = 'U')
BEGIN
-- Node tablosu
CREATE TABLE node (
    node_id INTEGER UNIQUE,
    name NVARCHAR(255),
    txt NVARCHAR(MAX),
    syntax NVARCHAR(255),
    tags NVARCHAR(MAX),
    is_ro INTEGER,
    is_richtxt INTEGER,
    has_codebox INTEGER,
    has_table INTEGER,
    has_image INTEGER,
    level INTEGER,
    ts_creation INTEGER,
    ts_lastsave INTEGER
);
END
GO