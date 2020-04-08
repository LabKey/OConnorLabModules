/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

CREATE INDEX IDX_inventory_container ON oconnor.inventory(container);
CREATE INDEX IDX_inventory_status ON oconnor.inventory(status);
CREATE INDEX IDX_inventory_cell_type ON oconnor.inventory(cell_type);
CREATE INDEX IDX_inventory_dna_type ON oconnor.inventory(dna_type);
CREATE INDEX IDX_inventory_freezer ON oconnor.inventory(freezer);
CREATE INDEX IDX_inventory_lab_name ON oconnor.inventory(lab_name);
CREATE INDEX IDX_inventory_oligo_purification ON oconnor.inventory(oligo_purification);
CREATE INDEX IDX_inventory_oligo_type ON oconnor.inventory(oligo_type);
CREATE INDEX IDX_inventory_sample_type ON oconnor.inventory(sample_type);
CREATE INDEX IDX_inventory_specimen_additive ON oconnor.inventory(specimen_additive);
CREATE INDEX IDX_inventory_specimen_collaborator ON oconnor.inventory(specimen_collaborator);
CREATE INDEX IDX_inventory_specimen_geographic_origin ON oconnor.inventory(specimen_geographic_origin);
CREATE INDEX IDX_inventory_specimen_institution ON oconnor.inventory(specimen_institution);
CREATE INDEX IDX_inventory_specimen_species ON oconnor.inventory(specimen_species);
CREATE INDEX IDX_inventory_specimen_type ON oconnor.inventory(specimen_type);
CREATE INDEX IDX_inventory_virus_strain ON oconnor.inventory(virus_strain);

CREATE INDEX IDX_purchases_container ON oconnor.purchases(container);
CREATE INDEX IDX_purchases_grant_number ON oconnor.purchases(grant_number);
CREATE INDEX IDX_purchases_address ON oconnor.purchases(address);
CREATE INDEX IDX_purchases_status ON oconnor.purchases(status);
CREATE INDEX IDX_purchases_vendor ON oconnor.purchases(vendor);

CREATE INDEX IDX_quotes_vendor ON oconnor.quotes(vendor);
