/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- DROP all views (current and obsolete)

-- NOTE: Don't remove any of these drop statements, even if we stop re-creating the view in *-create.sql. Drop statements must
-- remain in place so we can correctly upgrade from older versions, which we commit to for two years after each release.

SELECT core.fn_dropifexists('max_virus_challenge_date', 'oconnor', 'VIEW', NULL);
SELECT core.fn_dropifexists('active_quotes', 'oconnor', 'VIEW', NULL);
SELECT core.fn_dropifexists('active_grants', 'oconnor', 'VIEW', NULL);
