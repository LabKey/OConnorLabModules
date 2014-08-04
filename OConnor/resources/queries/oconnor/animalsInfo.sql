/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT a.id,
a.Grant,
a.Protocol,
a.Project,

CAST((ROUND((timestampdiff('SQL_TSI_DAY',a.challenge_date, CURDATE())/7) * 10 ))/10 as FLOAT) AS WPI, 
CAST((ROUND((timestampdiff('SQL_TSI_DAY',a.vaccine_date, CURDATE())/7) * 10 ))/10 as FLOAT) AS WPV, 


FROM(
	SELECT b.*,
		
	(SELECT f.ActiveResearchAssignments
		FROM "/WNPRC/EHR/".study.demographicsAssignmentSummary f
		WHERE b.Id = f.Id) AS Project,
		
	(SELECT f.protocol
		FROM "/WNPRC/EHR/".lists.project f
		WHERE  
			(SELECT f.ActiveResearchAssignments
			FROM "/WNPRC/EHR/".study.demographicsAssignmentSummary f
			WHERE b.Id = f.Id)
	
		= CAST( f.project as VARCHAR) ) AS Protocol,
		
	(SELECT f.account
		FROM "/WNPRC/EHR/".lists.project f
		WHERE  
			(SELECT f.ActiveResearchAssignments
			FROM "/WNPRC/EHR/".study.demographicsAssignmentSummary f
			WHERE b.Id = f.Id)
	
		= CAST( f.project as VARCHAR) ) AS Grant

	FROM oconnor.animals b

) a
