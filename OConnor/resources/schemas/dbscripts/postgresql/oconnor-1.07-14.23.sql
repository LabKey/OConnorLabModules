-- drop this column if it made it onto the table (should only be needed a release)
ALTER TABLE oconnor.all_specimens DROP IF EXISTS enabled;
ALTER TABLE oconnor.specimen_type ADD enabled boolean DEFAULT true NOT NULL;