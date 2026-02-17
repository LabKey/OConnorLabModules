DO $$
  DECLARE
rec RECORD;
BEGIN
FOR rec IN
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'oconnor'
  AND lower(column_name) IN ('createdby',
                             'modifiedby')
  AND data_type = 'smallint'
    LOOP
          EXECUTE format(
              'ALTER TABLE oconnor.%I ALTER COLUMN %I
  TYPE userid',
              rec.table_name, rec.column_name
          );
END LOOP;
END $$;