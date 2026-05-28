/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
DO $$
  DECLARE
rec RECORD;
BEGIN
FOR rec IN
SELECT c.table_name, c.column_name
FROM information_schema.columns c
JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
WHERE c.table_schema = 'oconnor'
  AND t.table_type = 'BASE TABLE'
  AND lower(c.column_name) IN ('createdby',
                               'modifiedby')
  AND c.data_type = 'smallint'
    LOOP
          EXECUTE format(
              'ALTER TABLE oconnor.%I ALTER COLUMN %I
  TYPE userid',
              rec.table_name, rec.column_name
          );
END LOOP;
END $$;

DO $$
  DECLARE
rec RECORD;
BEGIN
FOR rec IN
SELECT c.table_name, c.column_name
FROM information_schema.columns c
JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
WHERE c.table_schema = 'oconnor'
  AND t.table_type = 'BASE TABLE'
  AND lower(c.column_name) = 'container'
  AND c.data_type = 'character varying'
    LOOP
          EXECUTE format(
              'ALTER TABLE oconnor.%I ALTER COLUMN %I
  TYPE entityid',
              rec.table_name, rec.column_name
          );
END LOOP;
END $$;
