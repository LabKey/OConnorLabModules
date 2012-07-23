--convert the rowid of runs table from int to identity

ALTER TABLE genotyping.Reads DROP CONSTRAINT FK_Reads_Runs;
ALTER TABLE genotyping.Analyses DROP CONSTRAINT FK_Analyses_Runs;
ALTER TABLE genotyping.SequenceFiles DROP CONSTRAINT FK_SequenceFiles_Runs;

-- rename new table to old table's name
EXEC sp_rename 'genotyping.Runs','Runs2';
GO
EXEC sp_rename 'genotyping.PK_Runs','PK_Runs2', 'OBJECT';


CREATE TABLE genotyping.Runs
(
    RowId INT IDENTITY (1,1) NOT NULL,
    MetaDataId INT NULL,          -- Must match a row in the runs metadata list (chosen by lab)
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    Path VARCHAR(1000) NOT NULL,
    FileName VARCHAR(200) NOT NULL,
    Status INT NOT NULL,
    Platform varchar(200)

    CONSTRAINT PK_Runs PRIMARY KEY (RowId)
);
GO
SET IDENTITY_INSERT genotyping.Runs ON;
GO
INSERT INTO genotyping.Runs (RowId, MetaDataId, Container, CreatedBy, Created, Path, FileName, Status, Platform)
  SELECT RowId, MetaDataId, Container, CreatedBy, Created, Path, FileName, Status, Platform FROM genotyping.Runs2;
GO
SET IDENTITY_INSERT genotyping.Runs OFF;

-- drop the original (now empty) table
DROP TABLE genotyping.Runs2;

ALTER TABLE genotyping.Runs ADD CONSTRAINT UNIQUE_Runs UNIQUE (MetaDataId, Container)
ALTER TABLE genotyping.Reads ADD CONSTRAINT FK_Reads_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);
ALTER TABLE genotyping.Analyses ADD CONSTRAINT FK_Analyses_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);
ALTER TABLE genotyping.SequenceFiles ADD CONSTRAINT FK_SequenceFiles_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);