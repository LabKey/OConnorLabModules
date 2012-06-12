create table genotyping.IlluminaTemplates (
  name varchar(100) not null,
  json varchar(4000),
  editable boolean default true,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,

  constraint PK_illumina_templates PRIMARY KEY (name)
);

insert into genotyping.IlluminaTemplates (name, json, editable)
VALUES
('Default', '{' ||
  '"Header": [["Template",""],["IEMFileVersion","3"],["Assay",""],["Chemistry","Default"]],' ||
  '"Reads": [["151",""], ["151",""]]' ||
  '}', false
);

insert into genotyping.IlluminaTemplates (name, json, editable)
VALUES
('Resequencing', '{' ||
  '"Header": [["Template","Resequencing"],["IEMFileVersion","3"],["Assay","TruSeq DNA/RNA"],["Chemistry","Default"]],' ||
  '"Reads": [["151",""], ["151",""]],' ||
  '"Settings": [["OnlyGenerateFASTQ","1"]]' ||
  '}', true
);