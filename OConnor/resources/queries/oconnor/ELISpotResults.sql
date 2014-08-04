/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT 
a.Well,
a.Experiment,
a.Id,
a.SampleDate,
a.PeptideId,
a.Control,
a.CellNumber,
a.Spots,
a.Comment,
a.result,
a.sfc,
a.cutoff,
a.neg_mean,
a.min_spots,
a.mean_multiplier,
a.stddev_number,
a.overall_multiplier,
a.rowid,
a.Run,

(SELECT f.official_name
	FROM oconnor.mcm_cd8_tcell_epitopes f,
	oconnor.peptides p

	WHERE CAST(p.peptide_number AS VARCHAR) = a.PeptideId AND LOCATE(p.amino_acid_sequence, f.sequence) <> 0) AS mappedEpitope,


CAST((ROUND((timestampdiff('SQL_TSI_DAY', a.Id.challenge_date,a.SampleDate)/7) * 10 ))/10 as FLOAT) AS WPI, 

FROM "/dho".assay."ELISpot Data" a
