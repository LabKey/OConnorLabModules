package org.labkey.genotyping;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: cnathe
 * Date: 10/16/12
 */
public class HaplotypeDataHandler extends AbstractExperimentDataHandler
{

    @Override
    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        if (!dataFile.exists())
        {
            log.warn("Could not find file " + dataFile.getAbsolutePath() + " on disk for data with LSID " + data.getLSID());
            return;
        }
        ExpRun expRun = data.getRun();
        if (expRun == null)
        {
            throw new ExperimentException("Could not load haplotype file " + dataFile.getAbsolutePath() + " because it is not owned by an experiment run");
        }

        try
        {
            ExpProtocol protocol = expRun.getProtocol();
            AssayProvider provider = AssayService.get().getProvider(protocol);
            Domain runDomain = provider.getRunDomain(protocol);
            if (runDomain == null)
            {
                throw new ExperimentException("Could not find run domain for protocol with LSID " + protocol.getLSID());
            }

            // get the column header mapping supplied by the user as run properties
            Map<String, String> colHeaderMap = getColumnHeaderMapping(expRun, runDomain);

            // parse the haplotype assignment data to get a list of data rows, animals, and haplotypes
            Map<String, String> animalIds = new CaseInsensitiveTreeMap<String>();
            List<Pair<String, String>> haplotypes = new ArrayList<Pair<String, String>>();
            List<HaplotypeAssignmentDataRow> dataRows = new ArrayList<HaplotypeAssignmentDataRow>();
            parseHaplotypeData(dataFile, colHeaderMap, animalIds, haplotypes, dataRows);

            // insert the new animal and haplotype records
            Map<String, Integer> animalRowIdMap = ensureAnimalIds(animalIds, data, info.getUser(), info.getContainer());
            Map<String, Integer> haplotypeRowIdMap = ensureHaplotypeNames(haplotypes, info.getUser(), info.getContainer());

            // insert the animal specific information for this run
            Map<Integer, Integer> animalAnalysisRowIdMap = insertAnimalAnalysis(expRun, dataRows, info.getUser(), info.getContainer(), animalRowIdMap);

            // insert the animal to haplotype assignments
            insertHaplotypeAssignments(dataRows, expRun, info.getUser(), info.getContainer(), animalRowIdMap, haplotypeRowIdMap, animalAnalysisRowIdMap);
        }
        catch (IOException e)
        {
            throw new ExperimentException("Failed to read from data file " + dataFile.getName(), e);
        }
        catch (Exception e)
        {
            throw new ExperimentException(e.getMessage());
        }
    }

    private Map<String, String> getColumnHeaderMapping(ExpRun run, Domain domain)
    {
        Map<String, String> colHeaderMap = new HashMap<String, String>();
        for (Pair<String, String> property : HaplotypeAssayProvider.COLUMN_HEADER_MAPPING_PROPERTIES)
        {
            DomainProperty runProp = domain.getPropertyByName(property.first);
            if (runProp != null)
            {
                Object value = run.getProperty(runProp);
                if (value != null)
                    colHeaderMap.put(runProp.getName(), value.toString());
            }
        }
        return colHeaderMap;
    }

    private List<HaplotypeAssignmentDataRow> parseHaplotypeData(File dataFile, Map<String, String> colHeaderMap,
                Map<String, String> animals, List<Pair<String, String>> haplotypes, List<HaplotypeAssignmentDataRow> dataRows) throws Exception
    {
        TabLoader tabLoader = new TabLoader(dataFile, true);
        for (Map<String, Object> rowMap : tabLoader)
        {
            HaplotypeAssignmentDataRow dataRow = new HaplotypeAssignmentDataRow();
            for (Map.Entry<String, String> colHeader : colHeaderMap.entrySet())
            {
                dataRow.addToDataMap(colHeader.getKey(), rowMap.get(colHeader.getValue()).toString());
            }
            dataRows.add(dataRow);

            String animalId = dataRow.getMapValue(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN_NAME);
            if (animals.containsKey(animalId))
            {
                throw new ExperimentException("Duplicate value found in Lab Animal ID column: \"" + animalId + "\"");
            }
            animals.put(animalId, dataRow.getMapValue(HaplotypeAssayProvider.CUSTOMER_ANIMAL_COLUMN_NAME));

            haplotypes.addAll(dataRow.getHaplotypeList());
        }
        return dataRows;
    }

    private Map<String, Integer> ensureAnimalIds(Map<String, String> ids, ExpData data, User user, Container container) throws Exception
    {
        // get the updateService for the Animal table
        TableInfo tinfo = GenotypingQuerySchema.TableType.Animal.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Animal table");
        }

        // put the animal Ids map into the proper list format for the updateService
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, String> entry : ids.entrySet())
        {
            Map<String, Object> keys = new CaseInsensitiveHashMap<Object>();
            keys.put(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN_NAME, entry.getKey());
            keys.put(HaplotypeAssayProvider.CUSTOMER_ANIMAL_COLUMN_NAME, entry.getValue());
            rows.add(keys);
        }

        // insert any new animal Ids and get the RowIds for all
        for (Map<String, Object> row : rows)
        {
            String animalKey = row.get(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN_NAME).toString();

            // first check if the animal row exists
            Integer rowId = new TableSelector(tinfo, Collections.singleton("RowID"), new SimpleFilter(FieldKey.fromParts(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN_NAME), animalKey), null).getObject(Integer.class);
            if (rowId != null)
            {
                row.put("RowId", rowId);
            }
            else
            {
                // insert the new animal row
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> insertedRow = updateService.insertRows(user, container, Collections.singletonList(row), errors, new HashMap<String, Object>());
                throwFirstError(errors);
                if (insertedRow.size() != 1)
                {
                    throw new ExperimentException("Unable to insert a row into the Animal table for " + animalKey);
                }
                row.put("rowid", insertedRow.get(0).get("RowId"));
            }
        }

        // return a mapping from the animal Id to the RowId
        Map<String, Integer> map = new CaseInsensitiveHashMap<Integer>();
        for (Map<String, Object> row : rows)
        {
            map.put(row.get(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN_NAME).toString(), Integer.parseInt(row.get("RowId").toString()));
        }
        return map;
    }

    private Map<String, Integer> ensureHaplotypeNames(List<Pair<String, String>> haplotypes, User user, Container container) throws Exception
    {
        // get the updateService for the Haplotype table
        TableInfo tinfo = GenotypingQuerySchema.TableType.Haplotype.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Haplotype table");
        }

        // put the haplotype names into the proper list format for the updateService
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Pair<String, String> entry : haplotypes)
        {
            Map<String, Object> keys = new CaseInsensitiveHashMap<Object>();
            keys.put("name", entry.first);
            keys.put("type", entry.second);
            rows.add(keys);
        }

        // insert any new haplotype names and get the RowIds for all
        for (Map<String, Object> row : rows)
        {
            String haplotypeName = row.get("name").toString();

            // first check if the haplotype row exists
            Integer rowId = new TableSelector(tinfo, Collections.singleton("RowID"), new SimpleFilter(FieldKey.fromParts("name"), haplotypeName), null).getObject(Integer.class);
            if (rowId != null)
            {
                row.put("RowId", rowId);
            }
            else
            {
                // insert the new haplotype row
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> insertedRow = updateService.insertRows(user, container, Collections.singletonList(row), errors, new HashMap<String, Object>());
                throwFirstError(errors);
                if (insertedRow.size() != 1)
                {
                    throw new ExperimentException("Unable to insert a row into the Haplotype table for " + haplotypeName);
                }
                row.put("rowid", insertedRow.get(0).get("RowId"));
            }
        }

        // return a mapping from the Haplotype name to the RowId
        Map<String, Integer> map = new CaseInsensitiveHashMap<Integer>();
        for (Map<String, Object> row : rows)
        {
            map.put(row.get("name").toString(), Integer.parseInt(row.get("RowId").toString()));
        }
        return map;
    }

    private Map<Integer, Integer> insertAnimalAnalysis(ExpRun run, List<HaplotypeAssignmentDataRow> dataRows,
              User user, Container container, Map<String, Integer> animalRowIdMap) throws Exception
    {
        // get the updateService for the AnimalAnalysis table
        TableInfo tinfo = GenotypingQuerySchema.TableType.AnimalAnalysis.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Haplotype table");
        }

        // insert the animal/run values (totalReads, identifiedreads, etc.)
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (HaplotypeAssignmentDataRow dataRow : dataRows)
        {
            Map<String, Object> values = new CaseInsensitiveHashMap<Object>();
            values.put("animalid", animalRowIdMap.get(dataRow.getMapValue(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN_NAME)));
            values.put("runid", run.getRowId());
            values.put(HaplotypeAssayProvider.TOTAL_READS_COLUMN_NAME, dataRow.getIntegerValue(HaplotypeAssayProvider.TOTAL_READS_COLUMN_NAME));
            values.put(HaplotypeAssayProvider.IDENTIFIED_READS_COLUMN_NAME, dataRow.getIntegerValue(HaplotypeAssayProvider.IDENTIFIED_READS_COLUMN_NAME));
            rows.add(values);
        }

        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> insertedRows = updateService.insertRows(user, container, rows, errors, new HashMap<String, Object>());
        throwFirstError(errors);

         // return a mapping from the AnimalId to the AnimalAnalysis RowId
         Map<Integer, Integer> map = new HashMap<Integer, Integer>();
         for (Map<String, Object> insertedRow : insertedRows)
         {
             map.put(Integer.parseInt(insertedRow.get("animalid").toString()), Integer.parseInt(insertedRow.get("RowId").toString()));
         }
         return map;
    }

    private void insertHaplotypeAssignments(List<HaplotypeAssignmentDataRow> dataRows, ExpRun run, User user, Container container,
           Map<String, Integer> animalRowIdMap, Map<String, Integer> haplotypeRowIdMap, Map<Integer, Integer> animalAnalysisRowIdMap) throws Exception
    {
        // get the updateService for the AnimalHaplotypeAssignment table
        TableInfo tinfo = GenotypingQuerySchema.TableType.AnimalHaplotypeAssignment.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Haplotype table");
        }

        // insert the animalAnalysis/haplotype assignment values (one row for each haplotype, 4 will be input via this process for each animal)
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (HaplotypeAssignmentDataRow dataRow : dataRows)
        {
            for (Pair<String, String> haplotype : dataRow.getHaplotypeList())
            {
                Map<String, Object> values = new CaseInsensitiveHashMap<Object>();
                values.put("animalanalysisid", animalAnalysisRowIdMap.get(animalRowIdMap.get(dataRow.getMapValue(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN_NAME))));
                values.put("haplotypeid", haplotypeRowIdMap.get(haplotype.first));
                rows.add(values);
            }
        }

        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> insertedRows = updateService.insertRows(user, container, rows, errors, new HashMap<String, Object>());
        throwFirstError(errors);
    }

    private void throwFirstError(BatchValidationException errors) throws ExperimentException
    {
        if (errors.hasErrors())
        {
            throw new ExperimentException(errors.getRowErrors().get(0));
        }
    }

    @Override
    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (HaplotypeAssayProvider.HAPLOTYPE_DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    @Override
    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActionURL getContentURL(Container container, ExpData data)
    {
        return null;
    }

    @Override
    public void beforeDeleteData(List<ExpData> data) throws ExperimentException
    {
        try
        {
            GenotypingSchema gs = GenotypingSchema.get();
            for (ExpData expData : data)
            {
                // clean up the AnimalHaplotypeAssignment table
                Table.execute(gs.getSchema(), "DELETE FROM " + gs.getAnimalHaplotypeAssignmentTable() +
                        " WHERE AnimalAnalysisId IN (SELECT RowId FROM " + gs.getAnimalAnalysisTable() +
                        " WHERE RunId = ?)", expData.getRunId());

                // clean up the AnimalAnalysis table
                Table.execute(gs.getSchema(), "DELETE FROM " + gs.getAnimalAnalysisTable() +
                        " WHERE RunId = ?", expData.getRunId());
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public void deleteData(ExpData data, Container container, User user)
    {
    }

    public class HaplotypeAssignmentDataRow
    {
        private Map<String, String> _dataMap = new HashMap<String, String>();

        public void addToDataMap(String key, String value)
        {
            _dataMap.put(key, value);
        }

        public Map<String, String> getDataMap()
        {
            return _dataMap;
        }

        public String getMapValue(String key)
        {
            return _dataMap.get(key);
        }

        public Integer getIntegerValue(String key)
        {
            return Integer.parseInt(_dataMap.get(key).replaceAll(",", ""));
        }

        public List<Pair<String, String>> getHaplotypeList()
        {
            List<Pair<String, String>> rowHaplotypes = new ArrayList<Pair<String, String>>();
            for (String columnName : HaplotypeAssayProvider.HAPLOTYPE_COLUMN_NAMES)
            {
                String type = columnName.startsWith("mamuA") ? "Mamu-A" : (columnName.startsWith("mamuB") ? "Mamu-B" : null);
                rowHaplotypes.add(new Pair<String, String>(_dataMap.get(columnName), type));
            }
            return rowHaplotypes;
        }
    }
}
