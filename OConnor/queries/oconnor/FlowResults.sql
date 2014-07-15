SELECT 
a.Experiment,
a.Id,
a.SampleDate,
a.SurfaceMarker,
a.PercentLymphocytes,
a.dataid,
a.rowid,
a.Run,


CAST((ROUND((timestampdiff('SQL_TSI_DAY', a.Id.challenge_date,a.SampleDate)/7) * 10 ))/10 as FLOAT) AS WPI, 
CAST((ROUND((timestampdiff('SQL_TSI_DAY', a.Id.vaccine_date,a.SampleDate)/7) * 10 ))/10 as FLOAT) AS WPV, 


FROM "/WNPRC/WNPRC_Laboratories/oconnor".assay."Lymphocyte Counts Data" a
