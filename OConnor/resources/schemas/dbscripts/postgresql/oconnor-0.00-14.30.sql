/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/* oconnor-0.00-1.07.sql */

CREATE SCHEMA oconnor;

CREATE FUNCTION oconnor.add_order_number() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      --check to see if any purchases exist in container, assign first order number 1. if orders already exist in container, assign next order number
        IF
    (SELECT
          COUNT(*) as row_count
          FROM
          oconnor.purchases p
      WHERE
      p.container=NEW.container
    ) = 0
    THEN
      NEW.order_number :=1;
    ELSE
    NEW.order_number :=
      (SELECT
          p.order_number+1 as test
          FROM
          oconnor.purchases p
          WHERE
      p.container=NEW.container
          ORDER by p.order_number DESC
      LIMIT 1);
    END IF;
        RETURN NEW;
    END;
$$;

--
-- Name: add_quote_number(); Type: FUNCTION; Schema: oconnor; Owner: oconnor
--

CREATE FUNCTION oconnor.add_quote_number() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
      --check to see if any quotes exist in container, assign first quote number 1. if quotes already exist in container, assign next quote number
        IF
    (SELECT
          COUNT(*) as row_count
          FROM
          oconnor.quotes p
      WHERE
      p.container=NEW.container
    ) = 0
    THEN
      NEW.quote_number :=1;
    ELSE
    NEW.quote_number :=
      (SELECT
          p.quote_number+1 as test
          FROM
          oconnor.quotes p
          WHERE
      p.container=NEW.container
          ORDER by p.quote_number DESC
      LIMIT 1);
    END IF;
        RETURN NEW;
    END;
$$;

CREATE FUNCTION oconnor.acreate_miseq_mhc_genotypes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$BEGIN
DROP TABLE IF EXISTS miseq_mhc_genotypes;

--CREATE TABLE oconnor.to hold miseq_mhc_genotypes from query. note there is no pkey on this table as per rules with 'CREATE TABLE oconnor.as'

CREATE TABLE oconnor.miseq_mhc_genotypes
AS
(SELECT
--extract run name from each read. use regular expression to find run name, which is second component of header
SUBSTRING(read from '^\\w{6}\\+(\\d{1,5})\\\+') AS run,
--get multiplex identifier
mid,
--get allele lineage
lineage,
--count instances of lineage
COUNT(read) as read_ct
FROM
oconnor.miseq_mhc_reads
GROUP BY
--extract run name from read name using regexp. then group by run name, mid, and lineage.
--this will create a row for each run,mid,lineage combination.
--to get back to indvidual reads, use sql query to find reads with these combinations
SUBSTRING(read from '^\\w{6}\\+(\\d{1,5})\\\+'),mid,lineage);

--this statement exits out of the trigger function
RETURN OLD;

END;
$$;


CREATE FUNCTION oconnor.inventory_audit_deleted() RETURNS trigger
    LANGUAGE plpgsql
    AS $$BEGIN
DROP TABLE IF EXISTS emp;

--set values for status and removed_date
OLD.status='removed';
OLD.removed_date=NOW();

--add records to inventory_removed table
INSERT INTO oconnor.inventory_removed VALUES (OLD.*);
RETURN OLD;
END;$$;


CREATE FUNCTION oconnor.inventory_duplicate_check() RETURNS trigger
    LANGUAGE plpgsql
    AS $$--set variable quantity to 0. will update when the query runs. if the query finds one or more rows (indicating existing sample), it errors out
DECLARE
quantity INTEGER := 0;

BEGIN
  SELECT
  COUNT(*)
    INTO quantity
        FROM
        oconnor.inventory i
        WHERE
        i.freezer=NEW.freezer
  AND
  i.cane=NEW.cane
  AND
  i.box=NEW.box
  AND
  i.box_row=NEW.box_row
  AND
  i.box_column=NEW.box_column
  AND
  i.status='available';

IF quantity > 0 THEN
    RAISE EXCEPTION 'Sample already exists in freezer: %, cane: %, box: %, row: %, column: %. Please check your coordinates and try again.', NEW.freezer, NEW.cane, NEW.box, NEW.box_row, NEW.box_column;
END IF;

RETURN NEW;
END;
$$;


--
-- Name: alabrity_sequence; Type: SEQUENCE; Schema: oconnor; Owner: oconnor
--

CREATE SEQUENCE oconnor.alabrity_sequence
    START WITH 30770
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: SEQUENCE alabrity_sequence; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON SEQUENCE oconnor.alabrity_sequence IS 'sequence used for primary key throughout entire alabrity system';

-- Name: grants; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.grants (
    key integer DEFAULT nextval('oconnor.alabrity_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    createdby public.userid NOT NULL,
    modifiedby public.userid NOT NULL,
    id character varying(255) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    title text NOT NULL,
    funding_source text,
    grant_type character varying(255),
    grant_unit character varying(255),
    expiration_date timestamp without time zone NOT NULL,
    budget numeric,
    comments text
);


--
-- Name: active_grants; Type: VIEW; Schema: oconnor; Owner: oconnor
--

CREATE VIEW oconnor.active_grants AS
    SELECT g.id, g.container, (((g.id)::text || ' - '::text) || g.title) AS displaytitle FROM oconnor.grants g WHERE ((g.enabled = true) AND (now() < g.expiration_date));


--
-- Name: active_quotes; Type: VIEW; Schema: oconnor; Owner: oconnor
--

CREATE VIEW oconnor.active_quotes AS
    SELECT g.id, g.container, (((g.id)::text || ' - '::text) || g.title) AS displaytitle FROM oconnor.grants g WHERE ((g.enabled = true) AND (now() < g.expiration_date));


--
-- Name: oc_sequence; Type: SEQUENCE; Schema: oconnor; Owner: oconnor
--

CREATE SEQUENCE oconnor.oc_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: all_species; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.all_species (
    specimen_species character varying(255) NOT NULL,
    species_common_name character varying(255) NOT NULL,
    species_short_name character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE all_species; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.all_species IS 'used as lookup in inventory app';


--
-- Name: all_specimens; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.all_specimens (
    specimen_type character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE all_specimens; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.all_specimens IS 'used as lookup in inventory app';


--
-- Name: animals; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.animals (
    id character varying(255) NOT NULL,
    gs_id character varying(255),
    cr_id character varying(255),
    mhc_haplotype character varying(255),
    study character varying(255),
    current_use character varying(255),
    challenge_type character varying(255),
    challenge_date timestamp without time zone,
    vaccine_type character varying(255),
    vaccine_date timestamp without time zone,
    comments character varying(255),
    kir_haplotype character varying(255)
);


--
-- Name: TABLE animals; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.animals IS 'animals intensively studied by the O''Connor lab. Used to store data on commonly used fields like SIV infection date and MHC genotype.';


--
-- Name: availability; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.availability (
    availability character varying(25) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE availability; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.availability IS 'lookup used by inventory system';


--
-- Name: cell_type; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.cell_type (
    cell_type character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE cell_type; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.cell_type IS 'lookup used by inventory system';


--
-- Name: diff_snp; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.diff_snp (
    key integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    uploaded_variation character varying(100) NOT NULL,
    location character varying(100) NOT NULL,
    allele character varying(100) NOT NULL,
    gene character varying(100) NOT NULL,
    transcript character varying(100) NOT NULL,
    consequence character varying(100) NOT NULL,
    cdna_position character varying(100) NOT NULL,
    cds_position character varying(100) NOT NULL,
    protein_position character varying(100) NOT NULL,
    amino_acids character varying(100) NOT NULL,
    codons character varying(100) NOT NULL,
    existing_variation character varying(100) NOT NULL,
    extra character varying(255) NOT NULL,
    gene_common_name character varying(100) NOT NULL,
    created timestamp without time zone DEFAULT now() NOT NULL,
    modified timestamp without time zone
);


--
-- Name: TABLE diff_snp; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.diff_snp IS 'comparison of differential SNPs in genome sequencing data from CY0165 and CY0166. intended for data browsing, not long-term data storage. ';


--
-- Name: dna_sequences; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.dna_sequences (
    allele_name character varying(255) NOT NULL,
    initials character varying(255) NOT NULL,
    file_active integer NOT NULL,
    genbank_id character varying(255) NOT NULL,
    expt_number character varying(255) NOT NULL,
    comments character varying(255) NOT NULL,
    locus character varying(255) NOT NULL,
    species character varying(255) NOT NULL,
    origin character varying(255) NOT NULL,
    sequence text NOT NULL,
    previous_name character varying(255) NOT NULL,
    last_edit timestamp without time zone NOT NULL,
    version integer NOT NULL,
    modified_by character varying(255) NOT NULL,
    translation text NOT NULL,
    type character varying(255) NOT NULL,
    ipd_accession character varying(255) NOT NULL,
    reference integer NOT NULL,
    region character varying(255) NOT NULL,
    id integer NOT NULL,
    variant integer NOT NULL,
    upload_id character varying(255) NOT NULL,
    full_length integer NOT NULL,
    uid integer NOT NULL,
    allele_family character varying(255) NOT NULL
);


--
-- Name: dna_sequences_draft_2013; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.dna_sequences_draft_2013 (
    allele_name character varying(255) NOT NULL,
    initials character varying(255) NOT NULL,
    file_active integer DEFAULT 1 NOT NULL,
    genbank_id character varying(255),
    expt_number character varying(255),
    comments character varying(255),
    locus character varying(255),
    species character varying(255) NOT NULL,
    origin character varying(255),
    sequence text NOT NULL,
    previous_name character varying(255),
    last_edit timestamp without time zone,
    version integer,
    modified_by character varying(255),
    translation text,
    type character varying(255),
    ipd_accession character varying(255),
    reference integer,
    region character varying(255),
    id integer NOT NULL,
    variant integer,
    upload_id character varying(255),
    full_length integer,
    uid integer NOT NULL,
    allele_family character varying(255)
);


--
-- Name: dna_type; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.dna_type (
    dna_type character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE dna_type; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.dna_type IS 'lookup used in inventory system';


--
-- Name: elispot_matrix; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.elispot_matrix (
    pool character varying(255) NOT NULL,
    id integer NOT NULL,
    "500-587" integer,
    "588-684" integer,
    "685-762" integer,
    "763-850" integer,
    "851-946" integer,
    "947-1024" integer,
    "1025-1111" integer,
    "1112-1189" integer,
    "1190-1267" integer,
    "1169-1254" integer,
    "1255-1321" integer,
    "1149" integer
);


--
-- Name: TABLE elispot_matrix; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.elispot_matrix IS 'used to deconvolute elispot pools. written by paul hines.';


--
-- Name: experiment_db; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.experiment_db (
    experiment_number integer NOT NULL,
    created timestamp without time zone,
    createdby public.userid,
    description text,
    type character varying(255) DEFAULT NULL::character varying,
    parents character varying(255) DEFAULT NULL::character varying,
    workbook integer,
    container public.entityid,
    modifiedby public.userid,
    modified timestamp without time zone,
    comments character varying(255),
    oc_comments character varying(2555)
);


--
-- Name: TABLE experiment_db; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.experiment_db IS 'oconnor lab experiment log.';


--
-- Name: experiment_db_experiment_number_seq; Type: SEQUENCE; Schema: oconnor; Owner: oconnor
--

CREATE SEQUENCE oconnor.experiment_db_experiment_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: experiment_db_experiment_number_seq; Type: SEQUENCE OWNED BY; Schema: oconnor; Owner: oconnor
--

ALTER SEQUENCE oconnor.experiment_db_experiment_number_seq OWNED BY oconnor.experiment_db.experiment_number;


--
-- Name: experiment_types; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.experiment_types (
    type character varying(255) NOT NULL,
    container public.entityid,
    createdby public.userid,
    created timestamp without time zone,
    modifiedby public.userid,
    modified timestamp without time zone
);


--
-- Name: TABLE experiment_types; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.experiment_types IS 'lookup used in experiment system';


--
-- Name: flow_markers; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.flow_markers (
    surfacemarkers character varying(255) NOT NULL,
    subsetname character varying(255) NOT NULL
);


--
-- Name: flow_markers_copy; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.flow_markers_copy (
    surfacemarkers character varying(255) NOT NULL,
    subsetname character varying(255) NOT NULL
);


--
-- Name: freezer_id; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.freezer_id (
    freezer_id character varying(25) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE freezer_id; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.freezer_id IS 'lookup used by inventory system';


--
-- Name: inventory; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.inventory (
    key integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    status character varying(255) DEFAULT NULL::character varying NOT NULL,
    experiment integer,
    sample_number character varying(255) DEFAULT NULL::character varying,
    sample_type character varying(255) DEFAULT NULL::character varying,
    initials character varying(255) DEFAULT NULL::character varying,
    lab_name character varying(255) DEFAULT NULL::character varying,
    comments text,
    freezer character varying(255) DEFAULT NULL::character varying,
    cane character varying(255) DEFAULT NULL::character varying,
    coordinate character varying(255) DEFAULT NULL::character varying,
    box character varying(255) DEFAULT NULL::character varying,
    box_row character varying(255) DEFAULT NULL::character varying,
    box_column character varying(255) DEFAULT NULL::character varying,
    specimen_id character varying(255) DEFAULT NULL::character varying,
    specimen_type character varying(255) DEFAULT NULL::character varying,
    specimen_species character varying(255) DEFAULT NULL::character varying,
    specimen_geographic_origin character varying(255) DEFAULT NULL::character varying,
    specimen_institution character varying(255) DEFAULT NULL::character varying,
    specimen_collaborator character varying(255) DEFAULT NULL::character varying,
    specimen_quantity character varying(255) DEFAULT NULL::character varying,
    specimen_additive character varying(255) DEFAULT NULL::character varying,
    shipped_recipient_name character varying(255) DEFAULT NULL::character varying,
    shipped_recipient_institution character varying(255) DEFAULT NULL::character varying,
    shipped_notes text,
    removed_name character varying(255) DEFAULT NULL::character varying,
    dna_vector character varying(255) DEFAULT NULL::character varying,
    dna_insert character varying(255) DEFAULT NULL::character varying,
    dna_type character varying(255) DEFAULT NULL::character varying,
    dna_sequence text,
    oligo_number integer,
    oligo_name character varying(255) DEFAULT NULL::character varying,
    oligo_sequence character varying(255) DEFAULT NULL::character varying,
    oligo_type character varying(255) DEFAULT NULL::character varying,
    oligo_modifications character varying(255) DEFAULT NULL::character varying,
    oligo_target character varying(255) DEFAULT NULL::character varying,
    oligo_cognate character varying(255) DEFAULT NULL::character varying,
    oligo_purification character varying(255) DEFAULT NULL::character varying,
    oligo_melting numeric(20,2) DEFAULT NULL::numeric,
    cell_type character varying(255) DEFAULT NULL::character varying,
    cell_source character varying(255) DEFAULT NULL::character varying,
    cell_concentration character varying(255) DEFAULT NULL::character varying,
    cell_mhc_restriction character varying(255) DEFAULT NULL::character varying,
    cell_peptide_stimulation character varying(255) DEFAULT NULL::character varying,
    cell_passage_number character varying(255) DEFAULT NULL::character varying,
    cell_transforming_virus character varying(255) DEFAULT NULL::character varying,
    virus_strain character varying(255) DEFAULT NULL::character varying,
    virus_vl numeric(20,2) DEFAULT NULL::numeric,
    virus_tcid50 numeric(20,2) DEFAULT NULL::numeric,
    virus_grown_on character varying(255) DEFAULT NULL::character varying,
    nucleic_extraction character varying(255) DEFAULT NULL::character varying,
    nucleic_purity character varying(20) DEFAULT NULL::numeric,
    gs_id character varying(255) DEFAULT NULL::character varying,
    cohort_id character varying(255),
    container character varying(36) NOT NULL,
    sample_date timestamp without time zone,
    shipped_date timestamp without time zone,
    removed_date timestamp without time zone,
    oligo_date timestamp without time zone,
    cell_freeze_date timestamp without time zone,
    virus_freeze_date timestamp without time zone,
    modified timestamp without time zone,
    created timestamp without time zone
);


--
-- Name: TABLE inventory; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.inventory IS 'samples in oconnor inventory';


--
-- Name: COLUMN inventory.container; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON COLUMN oconnor.inventory.container IS 'container default is for ''oconnor'' folder on Labkey';


--
-- Name: inventory_removed; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.inventory_removed (
    key integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    status character varying(255) NOT NULL,
    experiment integer,
    sample_number character varying(255),
    sample_type character varying(255),
    initials character varying(255),
    lab_name character varying(255),
    comments text,
    freezer character varying(255),
    cane character varying(255),
    coordinate character varying(255),
    box character varying(255),
    box_row character varying(255),
    box_column character varying(255),
    specimen_id character varying(255),
    specimen_type character varying(255),
    specimen_species character varying(255),
    specimen_geographic_origin character varying(255),
    specimen_institution character varying(255),
    specimen_collaborator character varying(255),
    specimen_quantity character varying(255),
    specimen_additive character varying(255),
    shipped_recipient_name character varying(255),
    shipped_recipient_institution character varying(255),
    shipped_notes text,
    removed_name character varying(255),
    dna_vector character varying(255),
    dna_insert character varying(255),
    dna_type character varying(255),
    dna_sequence text,
    oligo_number integer,
    oligo_name character varying(255),
    oligo_sequence character varying(255),
    oligo_type character varying(255),
    oligo_modifications character varying(255),
    oligo_target character varying(255),
    oligo_cognate character varying(255),
    oligo_purification character varying(255),
    oligo_melting numeric(20,2),
    cell_type character varying(255),
    cell_source character varying(255),
    cell_concentration character varying(255),
    cell_mhc_restriction character varying(255),
    cell_peptide_stimulation character varying(255),
    cell_passage_number character varying(255),
    cell_transforming_virus character varying(255),
    virus_strain character varying(255),
    virus_vl numeric(20,2),
    virus_tcid50 numeric(20,2),
    virus_grown_on character varying(255),
    nucleic_extraction character varying(255),
    nucleic_purity character varying(20),
    gs_id character varying(255),
    cohort_id character varying(255),
    container character varying(36) NOT NULL,
    sample_date timestamp without time zone,
    shipped_date timestamp without time zone,
    removed_date timestamp without time zone,
    oligo_date timestamp without time zone,
    cell_freeze_date timestamp without time zone,
    virus_freeze_date timestamp without time zone,
    created timestamp without time zone,
    modified timestamp without time zone
);


--
-- Name: TABLE inventory_removed; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.inventory_removed IS 'samples removed from oconnor inventory. automatically populated using a trigger that runs when a sample is deleted from the inventory table.';


--
-- Name: COLUMN inventory_removed.container; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON COLUMN oconnor.inventory_removed.container IS 'defaults to container id for oconnor folder on labkey';


--
-- Name: jr_read_length; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.jr_read_length (
    rowid integer NOT NULL,
    run integer NOT NULL,
    length integer NOT NULL,
    count integer NOT NULL
);


--
-- Name: TABLE jr_read_length; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.jr_read_length IS 'apparently used as part of 454 sequence manger. not entirely sure how it is used.
';


--
-- Name: jr_read_length_rowid_seq; Type: SEQUENCE; Schema: oconnor; Owner: oconnor
--

CREATE SEQUENCE oconnor.jr_read_length_rowid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: jr_read_length_rowid_seq; Type: SEQUENCE OWNED BY; Schema: oconnor; Owner: oconnor
--

ALTER SEQUENCE oconnor.jr_read_length_rowid_seq OWNED BY oconnor.jr_read_length.rowid;


--
-- Name: laboratory; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.laboratory (
    laboratory character varying(25) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE laboratory; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.laboratory IS 'lookup used in inventory system';


--
-- Name: virus_challenges; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.virus_challenges (
    id character varying(255) NOT NULL,
    challenge_date timestamp(6) without time zone NOT NULL,
    code character varying(255) NOT NULL,
    meaning character varying(255) NOT NULL,
    remark character varying(255) NOT NULL,
    challenge_type character varying(255) NOT NULL
);


--
-- Name: max_virus_challenge_date; Type: VIEW; Schema: oconnor; Owner: oconnor
--

CREATE VIEW oconnor.max_virus_challenge_date AS
    SELECT v.id, max(v.challenge_date) AS challenge_date FROM oconnor.virus_challenges v WHERE ((v.challenge_type)::text ~~ '%SIV%'::text) GROUP BY v.id;


--
-- Name: mcm_cd8_tcell_epitopes; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.mcm_cd8_tcell_epitopes (
    official_name character varying(255) NOT NULL,
    confirmed boolean NOT NULL,
    allele_specificity character varying(255) NOT NULL,
    sequence character varying(255) NOT NULL,
    "tetramer " boolean,
    viral_suppression integer,
    "viral_escape " integer,
    response_stage character varying(255),
    present_in_m3ko boolean
);


--
-- Name: TABLE mcm_cd8_tcell_epitopes; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.mcm_cd8_tcell_epitopes IS 'not sure who is using or maintaining this.';


--
-- Name: mhc_haplotypes; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.mhc_haplotypes (
    key integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    "createdBy" integer NOT NULL,
    "modifiedBy" integer NOT NULL,
    id character varying(10) NOT NULL,
    haplotype character varying(10) NOT NULL,
    reads integer,
    run character varying(20),
    comments text,
    experiment integer,
    initials character varying(20) NOT NULL,
    enabled boolean DEFAULT true NOT NULL
);


--
-- Name: mhc_haplotypes_dictionary; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.mhc_haplotypes_dictionary (
    key integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    "createdBy" integer NOT NULL,
    "modifiedBy" integer NOT NULL,
    haplotype character varying(50) NOT NULL,
    allele character varying(50) NOT NULL,
    locus character varying(10) NOT NULL,
    major boolean NOT NULL,
    comments text,
    initials character varying(50) NOT NULL,
    enabled boolean NOT NULL
);


--
-- Name: miseq_mhc_genotypes; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.miseq_mhc_genotypes (
    run text,
    mid smallint,
    lineage character varying(50),
    read_ct bigint
);


--
-- Name: miseq_mhc_reads; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.miseq_mhc_reads (
    key integer DEFAULT nextval('oconnor.alabrity_sequence'::regclass),
    container character varying(36) NOT NULL,
    created timestamp without time zone DEFAULT now() NOT NULL,
    modified timestamp without time zone DEFAULT now() NOT NULL,
    "createdBy" public.userid DEFAULT 1001 NOT NULL,
    "modifiedBy" public.userid DEFAULT 1001 NOT NULL,
    mid smallint NOT NULL,
    read character varying(50) NOT NULL,
    lineage character varying(50) NOT NULL
);


--
-- Name: miseq_mhc_samples; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.miseq_mhc_samples (
    key integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    "createdBy" public.userid NOT NULL,
    "modifiedBy" public.userid NOT NULL,
    run smallint NOT NULL,
    mid smallint NOT NULL,
    sample_id character varying(255),
    sample_name character varying(255)
);


--
-- Name: oligo_purification; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.oligo_purification (
    oligo_purification character varying(25) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE oligo_purification; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.oligo_purification IS 'lookup used in inventory system.';


--
-- Name: oligo_type; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.oligo_type (
    oligo_type character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE oligo_type; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.oligo_type IS 'lookup used in inventory system.';


--
-- Name: peptide_vendor; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.peptide_vendor (
    peptide_vendor character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE peptide_vendor; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.peptide_vendor IS 'lookup used in inventory system';


--
-- Name: peptides; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.peptides (
    peptide_number smallint NOT NULL,
    pool_contents character varying(255),
    peptide_virus_strain character varying(255),
    peptide_protein character varying(255),
    peptide_alias character varying(255),
    target_sequence character varying(255),
    sequence_length smallint,
    amino_acid_sequence character varying(255),
    concentration character varying(255),
    molecular_weight real,
    comments character varying(255),
    container character varying(36) NOT NULL
);


--
-- Name: TABLE peptides; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.peptides IS 'synthetic peptides used in immunology experiments. separated from inventory in 2011 by Paul Hines to support ELISPOT data analysis within labkey';


--
-- Name: purchases; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.purchases (
    key integer DEFAULT nextval('oconnor.alabrity_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    "createdBy" public.userid NOT NULL,
    "modifiedBy" public.userid NOT NULL,
    order_number integer,
    item character varying(255) NOT NULL,
    item_unit character varying(255),
    placed_by character varying(255) NOT NULL,
    item_number character varying(255),
    quantity smallint NOT NULL,
    price numeric NOT NULL,
    grant_number character varying(255),
    vendor character varying(255) NOT NULL,
    address text,
    confirmation_number character varying(255),
    status smallint DEFAULT 1 NOT NULL,
    ordered_by character varying(255),
    ordered_date timestamp without time zone,
    received_by character varying(255),
    received_date timestamp without time zone,
    received_location character varying(255),
    invoice_number character varying(255),
    invoiced_date timestamp without time zone,
    invoiced_by character varying(255),
    comment text,
    keyword character varying(255)
);


--
-- Name: quotes; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.quotes (
    key integer DEFAULT nextval('oconnor.alabrity_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    "createdBy" public.userid NOT NULL,
    "modifiedBy" public.userid NOT NULL,
    quote_number integer,
    vendor character varying(255) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    vendor_quote_number character varying(255),
    vendor_quote_expiration_date timestamp without time zone,
    vendor_quote_comments text
);


--
-- Name: sample_type; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.sample_type (
    sample_type character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE sample_type; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.sample_type IS 'lookup used in inventory system';


--
-- Name: shipping; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.shipping (
    key integer DEFAULT nextval('oconnor.alabrity_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    "createdBy" public.userid NOT NULL,
    "modifiedBy" public.userid NOT NULL,
    id character varying(255) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    building character varying(255),
    address text NOT NULL,
    city character varying(255) NOT NULL,
    state character varying(2) NOT NULL,
    zip character varying(20) NOT NULL
);


--
-- Name: simple_experiment; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.simple_experiment (
    key integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp(6) without time zone DEFAULT NULL::timestamp without time zone NOT NULL,
    modified timestamp(6) without time zone DEFAULT NULL::timestamp without time zone NOT NULL,
    "expNumber" integer NOT NULL,
    "expDescription" text,
    "expParent" character varying(100),
    "expType" character varying(100),
    initials character varying(100) NOT NULL,
    "expComments" text,
    "expFiles" integer
);


--
-- Name: COLUMN simple_experiment."expFiles"; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON COLUMN oconnor.simple_experiment."expFiles" IS 'estimated number of files associated with experiment';


--
-- Name: specimen; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.specimen (
    "Experiment" integer NOT NULL,
    sample_number character varying(255) NOT NULL,
    sample_type character varying(255) NOT NULL,
    initials character varying(255) NOT NULL,
    specimen_id character varying(255) NOT NULL,
    gs_id character varying(255) NOT NULL,
    cohort_id character varying(255) NOT NULL,
    specimen_type character varying(255) NOT NULL,
    specimen_quantity real NOT NULL,
    sample_date character varying(255) NOT NULL,
    lab_name character varying(255) NOT NULL,
    specimen_species character varying(255) NOT NULL,
    specimen_geographic_origin character varying(255) NOT NULL,
    specimen_institution character varying(255) NOT NULL,
    specimen_collaborator character varying(255) NOT NULL,
    specimen_additive character varying(255) NOT NULL,
    nucleic_extraction character varying(255) NOT NULL,
    nucleic_purity character varying(255) NOT NULL,
    "Freezer" character varying(255) NOT NULL,
    "Cane" character varying(255) NOT NULL,
    "Box" character varying(255) NOT NULL,
    box_row character varying(255) NOT NULL,
    box_column character varying(255) NOT NULL,
    created character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    "Comments" character varying(255) NOT NULL,
    container character varying(255) NOT NULL
);


--
-- Name: specimen_additive; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.specimen_additive (
    specimen_additive character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE specimen_additive; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.specimen_additive IS 'lookup used in inventory system';


--
-- Name: specimen_collaborator; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.specimen_collaborator (
    specimen_collaborator character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE specimen_collaborator; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.specimen_collaborator IS 'lookup used in inventory system';


--
-- Name: specimen_geographic_origin; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.specimen_geographic_origin (
    specimen_geographic_origin character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE specimen_geographic_origin; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.specimen_geographic_origin IS 'lookup used in inventory system';


--
-- Name: specimen_institution; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.specimen_institution (
    specimen_institution character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    institution_display_name character varying(255)
);


--
-- Name: TABLE specimen_institution; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.specimen_institution IS 'lookup used in inventory system';


--
-- Name: specimen_species; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.specimen_species (
    specimen_species character varying(255) NOT NULL,
    species_common_name character varying(255),
    species_short_name character varying(255),
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE specimen_species; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.specimen_species IS 'lookup used in inventory system';


--
-- Name: specimen_type; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.specimen_type (
    specimen_type character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE specimen_type; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.specimen_type IS 'lookup used in inventory system';


--
-- Name: status; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.status (
    key integer DEFAULT nextval('oconnor.alabrity_sequence'::regclass) NOT NULL,
    id smallint NOT NULL,
    description character varying(10) NOT NULL
);


--
-- Name: tmp_ordernum; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.tmp_ordernum (
    order_number integer NOT NULL,
    placed_by character varying(255) NOT NULL,
    user_id integer NOT NULL
);


--
-- Name: vendors; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.vendors (
    key integer DEFAULT nextval('oconnor.alabrity_sequence'::regclass) NOT NULL,
    container character varying(36) NOT NULL,
    created timestamp without time zone NOT NULL,
    modified timestamp without time zone NOT NULL,
    "createdBy" public.userid NOT NULL,
    "modifiedBy" public.userid NOT NULL,
    vendor character varying(255) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    phone character varying(255),
    fax character varying(255),
    email character varying(255),
    url character varying(255),
    address text,
    city character varying(100),
    state character varying(50),
    zip character varying(15),
    po character varying(255),
    comments text,
    account_number character varying(255)
);


--
-- Name: virus_sequencing_data; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.virus_sequencing_data (
    labkey_id character varying(255) NOT NULL,
    ref_nt smallint NOT NULL,
    actg_ct smallint NOT NULL,
    n_ct smallint NOT NULL,
    variant_percent numeric(10,2) NOT NULL,
    protein character varying(10) NOT NULL,
    aa_site character varying(10) NOT NULL,
    category character varying(255) NOT NULL,
    variant_aa character varying(10) NOT NULL,
    variant_codon character varying(10) NOT NULL,
    a_ct smallint NOT NULL,
    c_ct smallint NOT NULL,
    g_ct smallint NOT NULL,
    t_ct smallint NOT NULL,
    n_percent numeric NOT NULL,
    dip_ct smallint NOT NULL,
    dip_percent numeric NOT NULL,
    date_added timestamp without time zone DEFAULT now() NOT NULL,
    active smallint DEFAULT 1 NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL,
    code text
);


--
-- Name: TABLE virus_sequencing_data; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.virus_sequencing_data IS 'used to store viral sequencing data output from Galaxy whole-genome workflows. Mainly for HIV/SIV 454 data.';


--
-- Name: COLUMN virus_sequencing_data.code; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON COLUMN oconnor.virus_sequencing_data.code IS '4 character code representing percentage of A,C,G,T at a given position';


--
-- Name: virus_strain; Type: TABLE; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE TABLE oconnor.virus_strain (
    virus_strain character varying(255) NOT NULL,
    rowid integer DEFAULT nextval('oconnor.oc_sequence'::regclass) NOT NULL
);


--
-- Name: TABLE virus_strain; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TABLE oconnor.virus_strain IS 'lookup used in inventory system';


--
-- Name: experiment_number; Type: DEFAULT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.experiment_db ALTER COLUMN experiment_number SET DEFAULT nextval('oconnor.experiment_db_experiment_number_seq'::regclass);


--
-- Name: rowid; Type: DEFAULT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.jr_read_length ALTER COLUMN rowid SET DEFAULT nextval('oconnor.jr_read_length_rowid_seq'::regclass);


--
-- Name: ELISpotMatrix_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.elispot_matrix
    ADD CONSTRAINT "ELISpotMatrix_pkey" PRIMARY KEY (id);


--
-- Name: all_species_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.all_species
    ADD CONSTRAINT all_species_pkey PRIMARY KEY (rowid);


--
-- Name: all_specimens_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.all_specimens
    ADD CONSTRAINT all_specimens_pkey PRIMARY KEY (rowid);


--
-- Name: animals_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.animals
    ADD CONSTRAINT animals_pkey PRIMARY KEY (id);


--
-- Name: availability_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.availability
    ADD CONSTRAINT availability_pkey PRIMARY KEY (rowid);


--
-- Name: cell_type_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.cell_type
    ADD CONSTRAINT cell_type_pkey PRIMARY KEY (rowid);


--
-- Name: diff_snp_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.diff_snp
    ADD CONSTRAINT diff_snp_pkey PRIMARY KEY (key);


--
-- Name: dna_sequences_draft_2013_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.dna_sequences_draft_2013
    ADD CONSTRAINT dna_sequences_draft_2013_pkey PRIMARY KEY (id);


--
-- Name: dna_type_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.dna_type
    ADD CONSTRAINT dna_type_pkey PRIMARY KEY (rowid);


--
-- Name: duplicate_reads; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.miseq_mhc_reads
    ADD CONSTRAINT duplicate_reads UNIQUE (read);


--
-- Name: flow_markers_copy_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.flow_markers_copy
    ADD CONSTRAINT flow_markers_copy_pkey PRIMARY KEY (surfacemarkers);


--
-- Name: flow_markers_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.flow_markers
    ADD CONSTRAINT flow_markers_pkey PRIMARY KEY (surfacemarkers);


--
-- Name: freezer_id_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.freezer_id
    ADD CONSTRAINT freezer_id_pkey PRIMARY KEY (rowid);


--
-- Name: grant_exists; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.grants
    ADD CONSTRAINT grant_exists UNIQUE (container, id);


--
-- Name: grants_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.grants
    ADD CONSTRAINT grants_pkey PRIMARY KEY (key);


--
-- Name: inventory_copy2_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.inventory_removed
    ADD CONSTRAINT inventory_copy2_pkey PRIMARY KEY (key);


--
-- Name: inventorytemp_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT inventorytemp_pkey PRIMARY KEY (key);


--
-- Name: jr_read_length_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.jr_read_length
    ADD CONSTRAINT jr_read_length_pkey PRIMARY KEY (rowid);


--
-- Name: laboratory_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.laboratory
    ADD CONSTRAINT laboratory_pkey PRIMARY KEY (rowid);


--
-- Name: mcm_cd8_tcell_epitopes_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.mcm_cd8_tcell_epitopes
    ADD CONSTRAINT mcm_cd8_tcell_epitopes_pkey PRIMARY KEY (official_name);


--
-- Name: mhc_haplotypes_dictionary_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.mhc_haplotypes_dictionary
    ADD CONSTRAINT mhc_haplotypes_dictionary_pkey PRIMARY KEY (key);


--
-- Name: mhc_haplotypes_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.mhc_haplotypes
    ADD CONSTRAINT mhc_haplotypes_pkey PRIMARY KEY (key);


--
-- Name: oligo_purification_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.oligo_purification
    ADD CONSTRAINT oligo_purification_pkey PRIMARY KEY (rowid);


--
-- Name: oligo_type_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.oligo_type
    ADD CONSTRAINT oligo_type_pkey PRIMARY KEY (rowid);


--
-- Name: peptide_vendor_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.peptide_vendor
    ADD CONSTRAINT peptide_vendor_pkey PRIMARY KEY (rowid);


--
-- Name: peptides_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.peptides
    ADD CONSTRAINT peptides_pkey PRIMARY KEY (peptide_number);


--
-- Name: pk_experiment_db; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.experiment_db
    ADD CONSTRAINT pk_experiment_db PRIMARY KEY (experiment_number);


--
-- Name: pk_module_experiment_types; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.experiment_types
    ADD CONSTRAINT pk_module_experiment_types PRIMARY KEY (type);


--
-- Name: purchases_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.purchases
    ADD CONSTRAINT purchases_pkey PRIMARY KEY (key);


--
-- Name: quotes_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.quotes
    ADD CONSTRAINT quotes_pkey PRIMARY KEY (key);


--
-- Name: sample_type_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.sample_type
    ADD CONSTRAINT sample_type_pkey PRIMARY KEY (rowid);


--
-- Name: ship_address_exists; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.shipping
    ADD CONSTRAINT ship_address_exists UNIQUE (container, id);


--
-- Name: shipping_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.shipping
    ADD CONSTRAINT shipping_pkey PRIMARY KEY (key);


--
-- Name: simple_experiment_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.simple_experiment
    ADD CONSTRAINT simple_experiment_pkey PRIMARY KEY (key);


--
-- Name: specimen_additive_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.specimen_additive
    ADD CONSTRAINT specimen_additive_pkey PRIMARY KEY (rowid);


--
-- Name: specimen_collaborator_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.specimen_collaborator
    ADD CONSTRAINT specimen_collaborator_pkey PRIMARY KEY (rowid);


--
-- Name: specimen_geographic_origin_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.specimen_geographic_origin
    ADD CONSTRAINT specimen_geographic_origin_pkey PRIMARY KEY (rowid);


--
-- Name: specimen_institution_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.specimen_institution
    ADD CONSTRAINT specimen_institution_pkey PRIMARY KEY (rowid);


--
-- Name: specimen_species_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.specimen_species
    ADD CONSTRAINT specimen_species_pkey PRIMARY KEY (rowid);


--
-- Name: specimen_type_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.specimen_type
    ADD CONSTRAINT specimen_type_pkey PRIMARY KEY (rowid);


--
-- Name: status_exists; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.status
    ADD CONSTRAINT status_exists UNIQUE (id);


--
-- Name: status_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.status
    ADD CONSTRAINT status_pkey PRIMARY KEY (key);


--
-- Name: vendor_exists; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.vendors
    ADD CONSTRAINT vendor_exists UNIQUE (container, vendor);


--
-- Name: CONSTRAINT vendor_exists ON vendors; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON CONSTRAINT vendor_exists ON oconnor.vendors IS 'Does not allow creation of vendors that already exist';


--
-- Name: vendors_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.vendors
    ADD CONSTRAINT vendors_pkey PRIMARY KEY (key);


--
-- Name: virus_sequencing_data_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.virus_sequencing_data
    ADD CONSTRAINT virus_sequencing_data_pkey PRIMARY KEY (rowid);


--
-- Name: virus_strain_pkey; Type: CONSTRAINT; Schema: oconnor; Owner: oconnor; Tablespace:
--

ALTER TABLE ONLY oconnor.virus_strain
    ADD CONSTRAINT virus_strain_pkey PRIMARY KEY (rowid);


--
-- Name: all_species_specimen_species_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX all_species_specimen_species_key ON oconnor.all_species USING btree (specimen_species);


--
-- Name: availability_availability_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX availability_availability_key ON oconnor.availability USING btree (availability);


--
-- Name: cell_type_cell_type_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX cell_type_cell_type_key ON oconnor.cell_type USING btree (cell_type);


--
-- Name: consequence_index; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE INDEX consequence_index ON oconnor.diff_snp USING hash (consequence);


--
-- Name: dna_type_dna_type_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX dna_type_dna_type_key ON oconnor.dna_type USING btree (dna_type);


--
-- Name: experiment_db_experiment_number_created_createdby_description_t; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX experiment_db_experiment_number_created_createdby_description_t ON oconnor.experiment_db USING btree (experiment_number, created, createdby, description, type, parents, workbook, container, modifiedby, modified, comments);


--
-- Name: experiment_db_experiment_number_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX experiment_db_experiment_number_key ON oconnor.experiment_db USING btree (experiment_number);


--
-- Name: freezer_id_freezer_id_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX freezer_id_freezer_id_key ON oconnor.freezer_id USING btree (freezer_id);


--
-- Name: gene_common_name_index; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE INDEX gene_common_name_index ON oconnor.diff_snp USING btree (gene_common_name);


--
-- Name: key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX key ON oconnor.inventory USING btree (key);


--
-- Name: laboratory_laboratory_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX laboratory_laboratory_key ON oconnor.laboratory USING btree (laboratory);


--
-- Name: oligo_purification_oligo_purification_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX oligo_purification_oligo_purification_key ON oconnor.oligo_purification USING btree (oligo_purification);


--
-- Name: oligo_type_oligo_type_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX oligo_type_oligo_type_key ON oconnor.oligo_type USING btree (oligo_type);


--
-- Name: peptide_vendor_peptide_vendor_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX peptide_vendor_peptide_vendor_key ON oconnor.peptide_vendor USING btree (peptide_vendor);


--
-- Name: sample_type_sample_type_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX sample_type_sample_type_key ON oconnor.sample_type USING btree (sample_type);


--
-- Name: selectViruses; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE INDEX "selectViruses" ON oconnor.virus_sequencing_data USING btree (labkey_id, ref_nt);


--
-- Name: specimen_additive_specimen_additive_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX specimen_additive_specimen_additive_key ON oconnor.specimen_additive USING btree (specimen_additive);


--
-- Name: specimen_collaborator_specimen_collaborator_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX specimen_collaborator_specimen_collaborator_key ON oconnor.specimen_collaborator USING btree (specimen_collaborator);


--
-- Name: specimen_geographic_origin_specimen_geographic_origin_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX specimen_geographic_origin_specimen_geographic_origin_key ON oconnor.specimen_geographic_origin USING btree (specimen_geographic_origin);


--
-- Name: specimen_institution_specimen_institution_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX specimen_institution_specimen_institution_key ON oconnor.specimen_institution USING btree (specimen_institution);


--
-- Name: specimen_species_specimen_species_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX specimen_species_specimen_species_key ON oconnor.specimen_species USING btree (specimen_species);


--
-- Name: specimen_type_specimen_type_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX specimen_type_specimen_type_key ON oconnor.specimen_type USING btree (specimen_type);


--
-- Name: virus_strain_virus_strain_key; Type: INDEX; Schema: oconnor; Owner: oconnor; Tablespace:
--

CREATE UNIQUE INDEX virus_strain_virus_strain_key ON oconnor.virus_strain USING btree (virus_strain);


--
-- Name: add_order; Type: TRIGGER; Schema: oconnor; Owner: oconnor
--

CREATE TRIGGER add_order BEFORE INSERT ON oconnor.purchases FOR EACH ROW EXECUTE PROCEDURE oconnor.add_order_number();


--
-- Name: add_quote; Type: TRIGGER; Schema: oconnor; Owner: oconnor
--

CREATE TRIGGER add_quote BEFORE INSERT ON oconnor.quotes FOR EACH ROW EXECUTE PROCEDURE oconnor.add_quote_number();


--
-- Name: check_dup; Type: TRIGGER; Schema: oconnor; Owner: oconnor
--

CREATE TRIGGER check_dup BEFORE INSERT ON oconnor.inventory FOR EACH ROW EXECUTE PROCEDURE oconnor.inventory_duplicate_check();


--
-- Name: TRIGGER check_dup ON oconnor.inventory; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TRIGGER check_dup ON oconnor.inventory IS 'ensures location of sample being inserted is not redundant';


--
-- Name: copy_inventory; Type: TRIGGER; Schema: oconnor; Owner: oconnor
--

CREATE TRIGGER copy_inventory AFTER DELETE ON oconnor.inventory FOR EACH ROW EXECUTE PROCEDURE oconnor.inventory_audit_deleted();


--
-- Name: TRIGGER copy_inventory ON oconnor.inventory; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TRIGGER copy_inventory ON oconnor.inventory IS 'moves deleted samples from inventory to inventory_removed';


--
-- Name: create_genotype_table; Type: TRIGGER; Schema: oconnor; Owner: oconnor
--

CREATE TRIGGER create_genotype_table AFTER INSERT OR UPDATE ON oconnor.miseq_mhc_reads FOR EACH STATEMENT EXECUTE PROCEDURE oconnor.acreate_miseq_mhc_genotypes();


--
-- Name: TRIGGER create_genotype_table ON miseq_mhc_reads; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON TRIGGER create_genotype_table ON oconnor.miseq_mhc_reads IS 'drops genotypes table if exists and recreates it using data from reads table';


--
-- Name: availability; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT availability FOREIGN KEY (status) REFERENCES oconnor.availability(availability);


--
-- Name: cell_type; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT cell_type FOREIGN KEY (cell_type) REFERENCES oconnor.cell_type(cell_type);


--
-- Name: dna_type; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT dna_type FOREIGN KEY (dna_type) REFERENCES oconnor.dna_type(dna_type);


--
-- Name: freezer; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT freezer FOREIGN KEY (freezer) REFERENCES oconnor.freezer_id(freezer_id);


--
-- Name: grant_number; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.purchases
    ADD CONSTRAINT grant_number FOREIGN KEY (grant_number, container) REFERENCES oconnor.grants(id, container);


--
-- Name: laboratory; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT laboratory FOREIGN KEY (lab_name) REFERENCES oconnor.laboratory(laboratory);


--
-- Name: oligo_purification; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT oligo_purification FOREIGN KEY (oligo_purification) REFERENCES oconnor.oligo_purification(oligo_purification);


--
-- Name: oligo_type; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT oligo_type FOREIGN KEY (oligo_type) REFERENCES oconnor.oligo_type(oligo_type);


--
-- Name: sampleType; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "sampleType" FOREIGN KEY (sample_type) REFERENCES oconnor.sample_type(sample_type);


--
-- Name: shipping_address; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.purchases
    ADD CONSTRAINT shipping_address FOREIGN KEY (address, container) REFERENCES oconnor.shipping(id, container);


--
-- Name: specimenAdditive; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "specimenAdditive" FOREIGN KEY (specimen_additive) REFERENCES oconnor.specimen_additive(specimen_additive);


--
-- Name: specimenCollaborator; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "specimenCollaborator" FOREIGN KEY (specimen_collaborator) REFERENCES oconnor.specimen_collaborator(specimen_collaborator);


--
-- Name: specimenGeographicOrigin; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "specimenGeographicOrigin" FOREIGN KEY (specimen_geographic_origin) REFERENCES oconnor.specimen_geographic_origin(specimen_geographic_origin);


--
-- Name: specimenInstitution; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "specimenInstitution" FOREIGN KEY (specimen_institution) REFERENCES oconnor.specimen_institution(specimen_institution);


--
-- Name: specimenSpecies; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "specimenSpecies" FOREIGN KEY (specimen_species) REFERENCES oconnor.specimen_species(specimen_species);


--
-- Name: specimenType; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "specimenType" FOREIGN KEY (specimen_type) REFERENCES oconnor.specimen_type(specimen_type);


--
-- Name: status; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.purchases
    ADD CONSTRAINT status FOREIGN KEY (status) REFERENCES oconnor.status(id);


--
-- Name: vendor; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.purchases
    ADD CONSTRAINT vendor FOREIGN KEY (vendor, container) REFERENCES oconnor.vendors(vendor, container);


--
-- Name: vendor_exists; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.quotes
    ADD CONSTRAINT vendor_exists FOREIGN KEY (vendor, container) REFERENCES oconnor.vendors(vendor, container);


--
-- Name: CONSTRAINT vendor_exists ON quotes; Type: COMMENT; Schema: oconnor; Owner: oconnor
--

COMMENT ON CONSTRAINT vendor_exists ON oconnor.quotes IS 'requires vendor to exist before a quote number can be added';


--
-- Name: virusStrain; Type: FK CONSTRAINT; Schema: oconnor; Owner: oconnor
--

ALTER TABLE ONLY oconnor.inventory
    ADD CONSTRAINT "virusStrain" FOREIGN KEY (virus_strain) REFERENCES oconnor.virus_strain(virus_strain);

/* oconnor-1.07-14.30.sql */

-- drop this column if it made it onto the table (should only be needed a release)
ALTER TABLE oconnor.all_specimens DROP IF EXISTS enabled;
ALTER TABLE oconnor.specimen_type ADD enabled boolean DEFAULT true NOT NULL;