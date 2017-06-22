/*
 * Copyright (c) 2014-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/* oconnor-16.30-16.31.sql */

ALTER TABLE oconnor.inventory ALTER COLUMN box_row DROP DEFAULT;
ALTER TABLE oconnor.inventory ALTER COLUMN box_row TYPE INT USING (box_row::integer);

ALTER TABLE oconnor.inventory ALTER COLUMN box_column DROP DEFAULT;
ALTER TABLE oconnor.inventory ALTER COLUMN box_column TYPE INT USING (box_column::integer);