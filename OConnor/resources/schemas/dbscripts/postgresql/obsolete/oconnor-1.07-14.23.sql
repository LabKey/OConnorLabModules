/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- drop this column if it made it onto the table (should only be needed a release)
ALTER TABLE oconnor.all_specimens DROP IF EXISTS enabled;
ALTER TABLE oconnor.specimen_type ADD enabled boolean DEFAULT true NOT NULL;