CREATE TABLE IF NOT EXISTS bookmark (node_id INTEGER UNIQUE,sequence INTEGER);

CREATE TABLE IF NOT EXISTS children (node_id INTEGER UNIQUE,father_id INTEGER,sequence INTEGER,master_id INTEGER);

CREATE TABLE IF NOT EXISTS codebox (node_id INTEGER,offset INTEGER,justification TEXT,txt TEXT,syntax TEXT,width INTEGER,height INTEGER,is_width_pix INTEGER,do_highl_bra INTEGER,do_show_linenum INTEGER);

CREATE TABLE IF NOT EXISTS grid (node_id INTEGER,offset INTEGER,justification TEXT,txt TEXT,col_min INTEGER,col_max INTEGER);

CREATE TABLE IF NOT EXISTS image (node_id INTEGER,offset INTEGER,justification TEXT,anchor TEXT,png BLOB,filename TEXT,link TEXT,time INTEGER);

CREATE TABLE IF NOT EXISTS node (node_id INTEGER UNIQUE,name TEXT,txt TEXT,syntax TEXT,tags TEXT,is_ro INTEGER,is_richtxt INTEGER,has_codebox INTEGER,has_table INTEGER,has_image INTEGER,level INTEGER,ts_creation INTEGER,ts_lastsave INTEGER);
