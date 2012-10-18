
CREATE TABLE genotyping.AnimalAnalysis
(
    RowId SERIAL,
    AnimalId INT NOT NULL,
    RunId INT NOT NULL,
    TotalReads INT,
    IdentifiedReads INT,
    Enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,

    CONSTRAINT PK_AnimalAnalysis PRIMARY KEY (RowId),
    CONSTRAINT UQ_AnimalAnalysis UNIQUE (AnimalId, RunId),
    CONSTRAINT FK_AnimalAnalysis_Animal FOREIGN KEY (AnimalId) REFERENCES genotyping.Animal(RowId),
    CONSTRAINT FK_AnimalAnalysis_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId)
);

CREATE INDEX IDX_AnimalAnalysis_Run ON genotyping.AnimalAnalysis(RunId);
CREATE INDEX IDX_AnimalAnalysis_Animal ON genotyping.AnimalAnalysis(AnimalId);

-- change AnimalHaplotypeAssignment to use AnimalAnalysisId instead of AnimalId and RunId
DELETE FROM genotyping.AnimalHaplotypeAssignment;
DROP INDEX genotyping.IDX_AnimalHaplotypeAssignment_Run;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP CONSTRAINT UQ_AnimalHaplotypeAssignment;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP COLUMN AnimalId;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP COLUMN RunId;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD COLUMN AnimalAnalysisId INT NOT NULL;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD CONSTRAINT FK_AnimalHaplotypeAssignment_AnimalAnalysis FOREIGN KEY (AnimalAnalysisId) REFERENCES genotyping.AnimalAnalysis(RowId);

-- drop not null contraint on Animal table for case where table not configured to be extensible
ALTER TABLE genotyping.Animal ALTER COLUMN Lsid DROP NOT NULL;

-- add Lsid column to Haplotype to allow it to be extensible
ALTER TABLE genotyping.Haplotype ADD COLUMN Lsid LsidType;
ALTER TABLE genotyping.Haplotype ADD CONSTRAINT FK_Haplotype_Lsid FOREIGN KEY (Lsid) REFERENCES exp.Object(ObjectURI);

-- add Type column to Haplotype to store 'Mamu-A' or 'Mamu-B'
ALTER TABLE genotyping.Haplotype ADD COLUMN Type VARCHAR(20);