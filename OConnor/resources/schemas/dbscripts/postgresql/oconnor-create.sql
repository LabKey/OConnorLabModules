/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

CREATE VIEW oconnor.active_grants AS
    SELECT g.id, g.container, (g.id::text || ' - ' || g.title) AS displaytitle
    FROM oconnor.grants g
    WHERE g.enabled = true AND now() < g.expiration_date;

CREATE VIEW oconnor.active_quotes AS
    SELECT g.id, g.container, (g.id::text || ' - ' || g.title) AS displaytitle
    FROM oconnor.grants g
    WHERE g.enabled = true AND now() < g.expiration_date;

CREATE VIEW oconnor.max_virus_challenge_date AS
    SELECT v.id, max(v.challenge_date) AS challenge_date
    FROM oconnor.virus_challenges v
    WHERE v.challenge_type::text LIKE '%SIV%'
    GROUP BY v.id;
