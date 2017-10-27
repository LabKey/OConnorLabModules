/*
 * Copyright (c) 2014-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


/* oconnor-17.20-17.21.sql */

ALTER TABLE oconnor.inventory ADD ModifiedBy USERID;
ALTER TABLE oconnor.inventory ADD CreatedBy USERID;