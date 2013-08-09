/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Add indexes on all junction table columns to speed up deletes.  Create only if they don't exist to accommodate 11.1
-- servers that have already run this script.
CREATE FUNCTION core.ensureIndex(text, text, text, text) RETURNS VOID AS $$
DECLARE
    index_name ALIAS FOR $1;
    schema_name ALIAS FOR $2;
    table_name ALIAS FOR $3;
    column_name ALIAS FOR $4;

    BEGIN
        IF NOT EXISTS(SELECT * FROM pg_indexes WHERE SchemaName = LOWER(schema_name) AND TableName = LOWER(table_name) AND IndexName = LOWER(index_name)) THEN
            EXECUTE 'CREATE INDEX ' || index_name || ' ON ' || schema_name || '.' || table_name || '(' || column_name || ')';
        END IF;
    END;
$$ LANGUAGE plpgsql;

SELECT core.ensureIndex('IX_ReadsJunction_MatchId', 'genotyping', 'ReadsJunction', 'MatchId');
SELECT core.ensureIndex('IX_ReadsJunction_ReadId', 'genotyping', 'ReadsJunction', 'ReadId');
SELECT core.ensureIndex('IX_AllelesJunction_MatchId', 'genotyping', 'AllelesJunction', 'MatchId');
SELECT core.ensureIndex('IX_AllelesJunction_SequenceId', 'genotyping', 'AllelesJunction', 'SequenceId');

DROP FUNCTION core.ensureIndex(text, text, text, text);