-- Dropping key [key] because it overlaps with inventorytemp_pkey [key]
DROP INDEX oconnor.key;
-- Dropping experiment_db_experiment_number_key [experiment_number] because it overlaps with pk_experiment_db [experiment_number]
DROP INDEX oconnor.experiment_db_experiment_number_key;
-- Converting experiment_db_experiment_number_created_createdby_description_t [experiment_number, created, createdby, description, type, parents, workbook, container, modifiedby, modified, comments] from unique to non-unique index because pk_experiment_db [experiment_number] overlaps it with a smaller column set
DROP INDEX oconnor.experiment_db_experiment_number_created_createdby_description_t;
CREATE INDEX experiment_db_experiment_number_created_createdby_description_t ON oconnor.experiment_db(experiment_number, created, createdby, description, type, parents, workbook, container, modifiedby, modified, comments);
