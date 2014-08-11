/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

DROP TABLE IF EXISTS public.all_species CASCADE;
DROP TABLE IF EXISTS public.all_specimens CASCADE;
DROP TABLE IF EXISTS public.animals CASCADE;
DROP TABLE IF EXISTS public.diff_snp CASCADE;
DROP TABLE IF EXISTS public.dna_sequences CASCADE;
DROP TABLE IF EXISTS public.dna_sequences_draft_2013 CASCADE;
DROP TABLE IF EXISTS public.elispot_matrix CASCADE;
DROP TABLE IF EXISTS public.experiment_db CASCADE;
DROP TABLE IF EXISTS public.experiment_types CASCADE;
DROP TABLE IF EXISTS public.flow_markers CASCADE;
DROP TABLE IF EXISTS public.flow_markers_copy CASCADE;
DROP TABLE IF EXISTS public.inventory_removed CASCADE;
DROP TABLE IF EXISTS public.jr_read_length CASCADE;
DROP TABLE IF EXISTS public.virus_challenges CASCADE;
DROP TABLE IF EXISTS public.mcm_cd8_tcell_epitopes CASCADE;
DROP TABLE IF EXISTS public.mhc_haplotypes CASCADE;
DROP TABLE IF EXISTS public.mhc_haplotypes_dictionary CASCADE;
DROP TABLE IF EXISTS public.miseq_mhc_genotypes CASCADE;
DROP TABLE IF EXISTS public.miseq_mhc_samples CASCADE;
DROP TABLE IF EXISTS public.peptide_vendor CASCADE;
DROP TABLE IF EXISTS public.peptides CASCADE;
DROP TABLE IF EXISTS public.simple_experiment CASCADE;
DROP TABLE IF EXISTS public.specimen CASCADE;
DROP TABLE IF EXISTS public.tmp_ordernum CASCADE;
DROP TABLE IF EXISTS public.virus_sequencing_data CASCADE;
DROP TABLE IF EXISTS public.miseq_mhc_reads CASCADE;
DROP TABLE IF EXISTS public.availability CASCADE;
DROP TABLE IF EXISTS public.cell_type CASCADE;
DROP TABLE IF EXISTS public.dna_type CASCADE;
DROP TABLE IF EXISTS public.freezer_id CASCADE;
DROP TABLE IF EXISTS public.grants CASCADE;
DROP TABLE IF EXISTS public.laboratory CASCADE;
DROP TABLE IF EXISTS public.oligo_purification CASCADE;
DROP TABLE IF EXISTS public.oligo_type CASCADE;
DROP TABLE IF EXISTS public.sample_type CASCADE;
DROP TABLE IF EXISTS public.shipping CASCADE;
DROP TABLE IF EXISTS public.specimen_additive CASCADE;
DROP TABLE IF EXISTS public.specimen_collaborator CASCADE;
DROP TABLE IF EXISTS public.specimen_geographic_origin CASCADE;
DROP TABLE IF EXISTS public.specimen_institution CASCADE;
DROP TABLE IF EXISTS public.specimen_species CASCADE;
DROP TABLE IF EXISTS public.specimen_type CASCADE;
DROP TABLE IF EXISTS public.status CASCADE;
DROP TABLE IF EXISTS public.purchases CASCADE;
DROP TABLE IF EXISTS public.vendors CASCADE;
DROP TABLE IF EXISTS public.quotes CASCADE;
DROP TABLE IF EXISTS public.virus_strain CASCADE;
DROP TABLE IF EXISTS public.inventory CASCADE;

DROP SEQUENCE IF EXISTS public.alabrity_sequence;
DROP SEQUENCE IF EXISTS public.oc_sequence;

DROP FUNCTION IF EXISTS public.add_order_number();
DROP FUNCTION IF EXISTS public.add_quote_number();
DROP FUNCTION IF EXISTS public.create_miseq_mhc_genotypes();
DROP FUNCTION IF EXISTS public.inventory_audit_deleted();
DROP FUNCTION IF EXISTS public.inventory_duplicate_check();

SELECT core.fn_dropifexists('*', 'oconnor', 'SCHEMA', NULL);