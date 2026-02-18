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
    view_rec RECORD;
BEGIN
    -- Save view definitions and drop all views in oconnor schema to clear dependencies
    CREATE TEMP TABLE _oconnor_view_defs (viewname text, definition text);

FOR view_rec IN
SELECT viewname, definition
FROM pg_views
WHERE schemaname = 'oconnor'
    LOOP
INSERT INTO _oconnor_view_defs VALUES (view_rec.viewname, view_rec.definition);
EXECUTE format('DROP VIEW IF EXISTS oconnor.%I CASCADE', view_rec.viewname);
END LOOP;

    -- Alter container columns on base tables
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

    -- Recreate views from saved definitions
FOR view_rec IN
SELECT viewname, definition FROM _oconnor_view_defs
    LOOP
    EXECUTE format('CREATE OR REPLACE VIEW oconnor.%I AS %s', view_rec.viewname, view_rec.definition);
END LOOP;

DROP TABLE _oconnor_view_defs;
END $$;