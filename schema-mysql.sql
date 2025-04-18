CREATE TABLE IF NOT EXISTS bookmark (node_id INTEGER UNIQUE,sequence INTEGER);

CREATE TABLE IF NOT EXISTS children (node_id INTEGER UNIQUE,father_id INTEGER,sequence INTEGER,master_id INTEGER);

CREATE TABLE IF NOT EXISTS codebox (node_id INTEGER,offset INTEGER,justification TEXT,txt LONGTEXT,syntax TEXT,width INTEGER,height INTEGER,is_width_pix INTEGER,do_highl_bra INTEGER,do_show_linenum INTEGER);

CREATE TABLE IF NOT EXISTS grid (node_id INTEGER,offset INTEGER,justification TEXT,txt LONGTEXT,col_min INTEGER,col_max INTEGER);

CREATE TABLE IF NOT EXISTS image (node_id INTEGER,offset INTEGER,justification TEXT,anchor TEXT,png MEDIUMBLOB,filename TEXT,link TEXT,time INTEGER);

CREATE TABLE IF NOT EXISTS node (node_id INTEGER UNIQUE,name TEXT,txt LONGTEXT,syntax TEXT,tags TEXT,is_ro INTEGER,is_richtxt INTEGER,has_codebox INTEGER,has_table INTEGER,has_image INTEGER,level INTEGER,ts_creation INTEGER,ts_lastsave INTEGER);

-- https://www.digitalocean.com/community/tutorials/sqlite-vs-mysql-vs-postgresql-a-comparison-of-relational-database-management-systems
-- * MEDIUMBLOB: A blob column with a maximum length of 16777215 (2^24 - 1) bytes of data.
-- BLOB: A binary string with a maximum length of 65535 (2^16 - 1) bytes of data.
-- TEXT: A string with a maximum length of 65535 (2^16 - 1) characters.
-- TINYTEXT: A text column with a maximum length of 255 (2^8 - 1) characters.
-- MEDIUMTEXT: A text column with a maximum length of 16777215 (2^24 - 1) characters.
-- * LONGTEXT: A text column with a maximum length of 4294967295 (2^32 - 1) characters.
-- INT or INTEGER: A normal-sized integer. The signed range for this numeric data type is -2147483648 to 2147483647, while the unsigned range is 0 to 4294967295.
-- MEDIUMINT: A medium-sized integer. The signed range for this numeric data type is -8388608 to 8388607, while the unsigned range is 0 to 16777215.
-- TINYINT: A very small integer. The signed range for this numeric data type is -128 to 127, while the unsigned range is 0 to 255.
-- SMALLINT: A small integer. The signed range for this numeric type is -32768 to 32767, while the unsigned range is 0 to 65535.
