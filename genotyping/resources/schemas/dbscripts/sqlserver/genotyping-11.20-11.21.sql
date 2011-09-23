
-- Pull the analysis row id into the alleles junction table for performance

ALTER TABLE genotyping.AllelesJunction ADD Analysis INT;
GO

UPDATE genotyping.AllelesJunction SET Analysis = (SELECT Analysis FROM genotyping.Matches WHERE RowId = MatchId);

ALTER TABLE genotyping.AllelesJunction ALTER COLUMN Analysis INT NOT NULL;

CREATE INDEX IX_Analysis ON genotyping.AllelesJunction(Analysis);

ALTER TABLE genotyping.AllelesJunction
    ADD CONSTRAINT FK_AllelesJunction_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses (RowId);

