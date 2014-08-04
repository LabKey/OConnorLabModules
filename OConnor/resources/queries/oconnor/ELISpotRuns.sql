/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT 
a.Flag,
a.Links,
a.Name,
a.Created,
a.CreatedBy,
a.Protocol,
a.RunGroups,
a.RunDate,
a.PerformedBy,
a.Batch,
a.rowid,

FROM "dho".assay."ELISpot Runs" a
