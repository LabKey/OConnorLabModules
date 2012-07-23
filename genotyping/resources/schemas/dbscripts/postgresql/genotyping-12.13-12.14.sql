--convert the rowid of runs table from int to serial
CREATE SEQUENCE genotyping.runs_rowid_seq
  INCREMENT 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 OWNED BY genotyping.runs.rowid;

select setval('genotyping.runs_rowid_seq', (select max(rowid)+1 from genotyping.runs), false);

ALTER TABLE genotyping.runs ALTER COLUMN rowid
  SET DEFAULT nextval('genotyping.runs_rowid_seq'::regclass);

ALTER TABLE genotyping.runs ADD CONSTRAINT UNIQUE_Runs UNIQUE (container, metadataid);