/*
 * Copyright (c) 2014-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


/* oconnor-17.30-17.31.sql */

ALTER TABLE oconnor.inventory_removed ADD ModifiedBy USERID;
ALTER TABLE oconnor.inventory_removed ADD CreatedBy USERID;