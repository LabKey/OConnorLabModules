/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT 
m.haplotype,
--count number of animals with haplotype, then divide by total number of animals with haplotype
--round to single digit after decimal
--cast integers to numbers in order to allow decimals to be calculated
--multiply number of animals times two to get the number of chromosomes to use as denominator
ROUND(CAST(COUNT(m.haplotype) AS NUMERIC) /
CAST(2*(SELECT COUNT(DISTINCT n.id)
FROM mhc_haplotypes n) AS NUMERIC)*100, 1)
as cohort_frequency,
--fetch major alleles for each haplotype
	(SELECT GROUP_CONCAT(d.allele)
	FROM mhc_haplotypes_dictionary d
	WHERE d.haplotype = m.haplotype
	AND d.major = TRUE
	GROUP BY d.haplotype) as major_alleles,
--fetch all alleles associated with each haplotype
	(SELECT GROUP_CONCAT(d.allele)
	FROM mhc_haplotypes_dictionary d
	WHERE d.haplotype = m.haplotype
	GROUP BY d.haplotype) as all_alleles
FROM mhc_haplotypes m
GROUP BY m.haplotype