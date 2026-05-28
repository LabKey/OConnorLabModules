/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Dropping idx_animalanalysis_animal [AnimalId] because it overlaps with uq_animalanalysis [AnimalId, RunId]
DROP INDEX genotyping.idx_animalanalysis_animal;
-- Dropping idx_animal_container [Container] because it overlaps with uq_animal_labanimalid [Container, LabAnimalId]
DROP INDEX genotyping.idx_animal_container;
-- Converting unique_runs [RowId, Container, MetaDataId] from unique to non-unique index because pk_runs [RowId] overlaps it with a smaller column set
ALTER TABLE genotyping.Runs DROP CONSTRAINT unique_runs;
CREATE INDEX index_runs ON genotyping.Runs(RowId, Container, MetaDataId);
