/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- Dropping key [key] because it overlaps with inventorytemp_pkey [key]
DROP INDEX oconnor.key;
-- Dropping experiment_db_experiment_number_key [experiment_number] because it overlaps with pk_experiment_db [experiment_number]
DROP INDEX oconnor.experiment_db_experiment_number_key;
-- Dropping unique index because pk_experiment_db [experiment_number] overlaps it with a smaller column set. Not bothering
-- to convert this unique index to a non-unique index because it has 11 columns, which is absurd.
DROP INDEX oconnor.experiment_db_experiment_number_created_createdby_description_t;
