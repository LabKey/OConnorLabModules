/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--max_virus_challenge_date is an sql view defined in oconnor schema using static load of virus_challenges data from ehr
SELECT
i.specimen_id,
i.sample_date,
m.challenge_date AS last_siv_challenge_date,
(ROUND(timestampdiff('SQL_TSI_DAY', m.challenge_date, i.sample_date)/7,1)) AS weeks_post_last_siv_challenge,
i.gs_id,
i.cohort_id,
i.specimen_type,
i.cell_type,
i.experiment,
i.sample_number,
i.initials,
i.specimen_species,
i.specimen_geographic_origin,
i.specimen_collaborator,
i.specimen_quantity,
i.specimen_additive,
i.comments,
i.freezer,
i.cane,
i.box,
i.box_row,
i.box_column,
i.coordinate
FROM inventory i
LEFT JOIN max_virus_challenge_date m
ON m.id = LCASE(i.specimen_id)