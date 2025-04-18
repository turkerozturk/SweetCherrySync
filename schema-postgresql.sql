

CREATE TABLE IF NOT EXISTS bookmark (
    node_id integer,
    sequence integer
);



CREATE TABLE IF NOT EXISTS children (
    node_id integer,
    father_id integer,
    sequence integer,
    master_id integer
);




CREATE TABLE IF NOT EXISTS codebox (
    node_id integer,
    "offset" integer,
    justification text,
    txt text,
    syntax text,
    width integer,
    height integer,
    is_width_pix integer,
    do_highl_bra integer,
    do_show_linenum integer
);



CREATE TABLE IF NOT EXISTS grid (
    node_id integer,
    "offset" integer,
    justification text,
    txt text,
    col_min integer,
    col_max integer
);



CREATE TABLE IF NOT EXISTS image (
    node_id integer,
    "offset" integer,
    justification text,
    anchor text,
    png bytea,
    filename text,
    link text,
    "time" integer
);


CREATE TABLE IF NOT EXISTS node (
    node_id integer,
    name text,
    txt text,
    syntax text,
    tags text,
    is_ro integer,
    is_richtxt integer,
    has_codebox integer,
    has_table integer,
    has_image integer,
    level integer,
    ts_creation integer,
    ts_lastsave integer
);













