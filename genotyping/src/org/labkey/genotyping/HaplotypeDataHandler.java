/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.genotyping;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.DataType;
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
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 10/16/12
 */
public class HaplotypeDataHandler extends AbstractExperimentDataHandler
{
    @Override
    public DataType getDataType()
    {
        return HaplotypeAssayProvider.HAPLOTYPE_DATA_TYPE;
    }

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
            Map<String, String> runPropertyValues = getRunPropertyValues(expRun, runDomain);

            // parse the haplotype assignment data to get a list of data rows, animals, and haplotypes
            Map<String, String> animalIds = new CaseInsensitiveTreeMap<>();
            List<HaplotypeIdentifier> haplotypes = new ArrayList<>();
            List<HaplotypeAssignmentDataRow> dataRows = new ArrayList<>();
            TabLoader tabLoader = new TabLoader(dataFile, true);
            List<Map<String, Object>> rowsMap = tabLoader.load();
            parseHaplotypeData(protocol, rowsMap, runPropertyValues, animalIds, haplotypes, dataRows);

            // insert the new animal and haplotype records
            Map<String, Integer> animalRowIdMap = ensureAnimalIds(animalIds, runPropertyValues, info.getUser(), info.getContainer());
            Map<HaplotypeIdentifier, Integer> haplotypeRowIdMap = ensureHaplotypeNames(haplotypes, runPropertyValues.get("speciesId"), info.getUser(), info.getContainer());

            // insert the animal specific information for this run
            Map<Integer, Integer> animalAnalysisRowIdMap = insertAnimalAnalysis(expRun, dataRows, info.getUser(), info.getContainer(), animalRowIdMap, runPropertyValues);

            // insert the animal to haplotype assignments
            insertHaplotypeAssignments(protocol, dataRows, expRun, info.getUser(), info.getContainer(), animalRowIdMap, haplotypeRowIdMap, animalAnalysisRowIdMap);
        }
        catch (IOException e)
        {
            throw new ExperimentException("Failed to read from data file " + dataFile.getName(), e);
        }
        catch (ExperimentException e)
        {
            throw e;
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getRunPropertyValues(ExpRun run, Domain domain)
    {
        Map<String, String> runPropValues = new HashMap<>();
        for (DomainProperty runProp : domain.getProperties())
        {
            Object value = run.getProperty(runProp);
            if (value != null)
                runPropValues.put(runProp.getName(), value.toString());
        }
        return runPropValues;
    }

    private List<HaplotypeAssignmentDataRow> parseHaplotypeData(ExpProtocol protocol, List<Map<String, Object>> tabLoaderData, Map<String, String> colHeaderMap,
                Map<String, String> animals, List<HaplotypeIdentifier> haplotypes, List<HaplotypeAssignmentDataRow> dataRows) throws ExperimentException
    {
        int rowIndex = 0;
        for (Map<String, Object> rowMap : tabLoaderData)
        {
            rowIndex++;
            HaplotypeAssignmentDataRow dataRow = new HaplotypeAssignmentDataRow();
            for (Map.Entry<String, String> colHeader : colHeaderMap.entrySet())
            {
                Object value = rowMap.get(colHeader.getValue());
                dataRow.addToDataMap(colHeader.getKey(), value != null ? value.toString() : null);
            }
            dataRows.add(dataRow);

            String animalId = dataRow.getMapValue(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName());
            if (animalId == null)
            {
                throw new ExperimentException("No Lab Animal ID found for row " + rowIndex);
            }
            else if (animals.containsKey(animalId))
            {
                throw new ExperimentException("Duplicate value found in Lab Animal ID column: " + animalId);
            }
            animals.put(animalId, dataRow.getMapValue(HaplotypeAssayProvider.CLIENT_ANIMAL_COLUMN.getName()));

            for (HaplotypeAssignment haplotypeAssignment : dataRow.getHaplotypeList(protocol))
            {
                haplotypes.add(haplotypeAssignment.getHaplotype());
            }
        }
        return dataRows;
    }

    private Map<String, Integer> ensureAnimalIds(Map<String, String> ids, Map<String, String> runPropertyValues, User user, Container container) throws Exception
    {
        // get the updateService for the Animal table
        TableInfo tinfo = GenotypingQuerySchema.TableType.Animal.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Animal table");
        }

        // put the animal Ids map into the proper list format for the updateService
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, String> entry : ids.entrySet())
        {
            Map<String, Object> keys = new CaseInsensitiveHashMap<>();
            keys.putAll(runPropertyValues); // if there are run fields that match up with fields on the Animal table, we should propagate the values over for all new animals
            keys.put(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName(), entry.getKey());
            keys.put(HaplotypeAssayProvider.CLIENT_ANIMAL_COLUMN.getName(), entry.getValue());
            rows.add(keys);
        }

        // insert any new animal Ids and get the RowIds for all
        for (Map<String, Object> row : rows)
        {
            String animalKey = row.get(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName()).toString();

            // first check if the animal row exists
            Map<String, Object> existingAnimal = new TableSelector(tinfo, new SimpleFilter(FieldKey.fromParts(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName()), animalKey), null).getMap();
            if (existingAnimal != null)
            {
                Object rowId = existingAnimal.get("RowId");
                if (rowId == null)
                {
                    throw new IllegalStateException("Could not find RowId in existing row for animal " + animalKey);
                }
                row.put("RowId", rowId);

                mergeIncomingAnimalInfoIntoExisting(row, existingAnimal);
                // Push the merged animal info back to the database
                updateService.updateRows(user, container, Collections.singletonList(existingAnimal), Collections.singletonList(row), null, null);
            }
            else
            {
                // insert the new animal row
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> insertedRow = updateService.insertRows(user, container, Collections.singletonList(row), errors, null, new HashMap<String, Object>());
                throwFirstError(errors);
                if (insertedRow.size() != 1)
                {
                    throw new ExperimentException("Unable to insert a row into the Animal table for " + animalKey);
                }
                row.put("rowid", insertedRow.get(0).get("RowId"));
            }
        }

        // return a mapping from the animal Id to the RowId
        Map<String, Integer> map = new CaseInsensitiveHashMap<>();
        for (Map<String, Object> row : rows)
        {
            if (row.get(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName()) != null && row.get("RowId") != null)
            {
                map.put(row.get(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName()).toString(), Integer.parseInt(row.get("RowId").toString()));
            }
        }
        return map;
    }

    private void mergeIncomingAnimalInfoIntoExisting(Map<String, Object> incomingAnimal, Map<String, Object> existingAnimal)
    {
        for (String animalPropertyName : new HashSet<>(incomingAnimal.keySet()))
        {
            // If the incoming animal record has a property set, transfer its value into the existing animal row
            if (incomingAnimal.get(animalPropertyName) != null)
            {
                existingAnimal.put(animalPropertyName, incomingAnimal.get((animalPropertyName)));
            }
        }
    }

    private Map<HaplotypeIdentifier, Integer> ensureHaplotypeNames(List<HaplotypeIdentifier> haplotypes, String speciesId, User user, Container container) throws Exception
    {
        // get the updateService for the Haplotype table
        TableInfo tinfo = GenotypingQuerySchema.TableType.Haplotype.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Haplotype table");
        }

        // put the haplotype names into the proper list format for the updateService
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HaplotypeIdentifier entry : haplotypes)
        {
            Map<String, Object> keys = new CaseInsensitiveHashMap<>();
            keys.put("name", entry._name);
            keys.put("type", entry._type);
            keys.put("speciesId", speciesId);
            rows.add(keys);
        }

        // insert any new haplotype names and get the RowIds for all
        for (Map<String, Object> row : rows)
        {
            String haplotypeName = row.get("name").toString();
            String haplotypeType = row.get("type").toString();

            // first check if the haplotype row exists
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("name"), haplotypeName);
            filter.addCondition(FieldKey.fromParts("type"), haplotypeType);
            Integer rowId = new TableSelector(tinfo, Collections.singleton("RowID"), filter, null).getObject(Integer.class);
            if (rowId != null)
            {
                row.put("RowId", rowId);
            }
            else
            {
                // insert the new haplotype row
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> insertedRow = updateService.insertRows(user, container, Collections.singletonList(row), errors, null, new HashMap<String, Object>());
                throwFirstError(errors);
                if (insertedRow.size() != 1)
                {
                    throw new ExperimentException("Unable to insert a row into the Haplotype table for " + haplotypeName);
                }
                row.put("rowid", insertedRow.get(0).get("RowId"));
            }
        }

        // return a mapping from the Haplotype name to the RowId
        Map<HaplotypeIdentifier, Integer> map = new HashMap<>();
        for (Map<String, Object> row : rows)
        {
            Object name = row.get("name");
            if (name == null)
            {
                throw new NullPointerException("No value from 'name' in the Haplotypes table");
            }
            Object type = row.get("type");
            if (type == null)
            {
                throw new NullPointerException("No value from 'type' in the Haplotypes table");
            }
            Object rowId = row.get("RowId");
            if (rowId == null)
            {
                throw new NullPointerException("No value from 'RowId' in the Haplotypes table");
            }
            map.put(new HaplotypeIdentifier(name.toString(), type.toString()), Integer.parseInt(rowId.toString()));
        }
        return map;
    }

    private Map<Integer, Integer> insertAnimalAnalysis(ExpRun run, List<HaplotypeAssignmentDataRow> dataRows,
              User user, Container container, Map<String, Integer> animalRowIdMap, Map<String, String> runPropertyValues) throws Exception
    {
        // get the updateService for the AnimalAnalysis table
        TableInfo tinfo = GenotypingQuerySchema.TableType.AnimalAnalysis.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Haplotype table");
        }

        // insert the animal/run values (totalReads, identifiedreads, etc.)
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++)
        {
            HaplotypeAssignmentDataRow dataRow = dataRows.get(i);

            Map<String, Object> values = new CaseInsensitiveHashMap<>();
            values.put("animalid", animalRowIdMap.get(dataRow.getMapValue(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName())));
            values.put("runid", run.getRowId());
            values.put(HaplotypeAssayProvider.TOTAL_READS_COLUMN.getName(), dataRow.getIntegerValue(HaplotypeAssayProvider.TOTAL_READS_COLUMN.getName(), i));
            values.put(HaplotypeAssayProvider.IDENTIFIED_READS_COLUMN.getName(), dataRow.getIntegerValue(HaplotypeAssayProvider.IDENTIFIED_READS_COLUMN.getName(), i));
            values.put("enabled", runPropertyValues.get("enabled"));
            rows.add(values);
        }

        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> insertedRows = updateService.insertRows(user, container, rows, errors, null, new HashMap<String, Object>());
        throwFirstError(errors);

         // return a mapping from the AnimalId to the AnimalAnalysis RowId
         Map<Integer, Integer> map = new HashMap<>();
         for (Map<String, Object> insertedRow : insertedRows)
         {
             map.put(Integer.parseInt(insertedRow.get("animalid").toString()), Integer.parseInt(insertedRow.get("RowId").toString()));
         }
         return map;
    }

    /** Used to bind a specific haplotype (mhcA, A004) to an animal */
    private static class HaplotypeAssignment
    {
        private final int _diploidNumber;
        @NotNull
        private final HaplotypeIdentifier _identifier;

        public HaplotypeAssignment(@NotNull HaplotypeIdentifier identifier, int diploidNumber)
        {
            _diploidNumber = diploidNumber;
            _identifier = identifier;
        }

        public int getDiploidNumber()
        {
            return _diploidNumber;
        }

        public HaplotypeIdentifier getHaplotype()
        {
            return _identifier;
        }
    }

    /** A type and specific value combination. mhcA and A004, for example */
    private static class HaplotypeIdentifier
    {
        @NotNull
        private final String _name;
        @NotNull
        private final String _type;

        public HaplotypeIdentifier(@NotNull String name, @NotNull String type)
        {
            _name = name;
            _type = type;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HaplotypeIdentifier that = (HaplotypeIdentifier) o;

            if (!_name.equalsIgnoreCase(that._name)) return false;
            if (!_type.equalsIgnoreCase(that._type)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _name.toLowerCase().hashCode();
            result = 31 * result + _type.toLowerCase().hashCode();
            return result;
        }
    }

    private void insertHaplotypeAssignments(ExpProtocol protocol, List<HaplotypeAssignmentDataRow> dataRows, ExpRun run, User user, Container container,
           Map<String, Integer> animalRowIdMap, Map<HaplotypeIdentifier, Integer> haplotypeRowIdMap, Map<Integer, Integer> animalAnalysisRowIdMap) throws Exception
    {
        // get the updateService for the AnimalHaplotypeAssignment table
        TableInfo tinfo = GenotypingQuerySchema.TableType.AnimalHaplotypeAssignment.createTable(new GenotypingQuerySchema(user, container));
        QueryUpdateService updateService = tinfo.getUpdateService();
        if (updateService == null)
        {
            throw new ExperimentException("Unable to get update service for Haplotype table");
        }

        // insert the animalAnalysis/haplotype assignment values (one row for each haplotype, 4 will be input via this process for each animal)
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HaplotypeAssignmentDataRow dataRow : dataRows)
        {
            for (HaplotypeAssignment haplotype : dataRow.getHaplotypeList(protocol))
            {
                Map<String, Object> values = new CaseInsensitiveHashMap<>();
                values.put("animalanalysisid", animalAnalysisRowIdMap.get(animalRowIdMap.get(dataRow.getMapValue(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName()))));
                values.put("diploidnumber", haplotype.getDiploidNumber());
                values.put("diploidnumberinferred", false);
                values.put("haplotypeid", haplotypeRowIdMap.get(haplotype.getHaplotype()));
                rows.add(values);
            }
        }

        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> insertedRows = updateService.insertRows(user, container, rows, errors, null, new HashMap<String, Object>());
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
    public ActionURL getContentURL(ExpData data)
    {
        return null;
    }

    @Override
    public void beforeDeleteData(List<ExpData> data) throws ExperimentException
    {
        for (ExpData expData : data)
        {
            deleteDatas(expData);
        }
    }

    @Override
    public void deleteData(ExpData data, Container container, User user)
    {
        deleteDatas(data);
    }

    private void deleteDatas(ExpData data)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        if (data.getRunId() != null)
        {
            SqlExecutor executor = new SqlExecutor(gs.getSchema());
            // clean up the AnimalHaplotypeAssignment table
            SQLFragment deleteAssignmentSql = new SQLFragment("DELETE FROM " + gs.getAnimalHaplotypeAssignmentTable() +
                " WHERE AnimalAnalysisId IN (SELECT RowId FROM " + gs.getAnimalAnalysisTable() +
                " WHERE RunId = ?)", data.getRunId());
            executor.execute(deleteAssignmentSql);

            // clean up the AnimalAnalysis table
            SQLFragment deleteAnimalAnalysisSql = new SQLFragment("DELETE FROM " + gs.getAnimalAnalysisTable() +
                " WHERE RunId = ?", data.getRunId());
            executor.execute(deleteAnimalAnalysisSql);
        }
    }

    /** Correponds to a single animal's row in the incoming TSV */
    public class HaplotypeAssignmentDataRow
    {
        private Map<String, String> _dataMap = new HashMap<>();

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

        public Integer getIntegerValue(String key, int rowIndex) throws ExperimentException
        {
            try
            {
                String value = _dataMap.get(key);
                if (value != null)
                    return Integer.parseInt(value.replaceAll(",", ""));
                else
                    return null;
            }
            catch(NumberFormatException e)
            {
                throw new ExperimentException("Error parsing integer value from column \"" + key + "\" for row " + (rowIndex+1) + ": " + _dataMap.get(key), e);
            }
        }

        public List<HaplotypeAssignment> getHaplotypeList(ExpProtocol protocol)
        {
            List<HaplotypeAssignment> rowHaplotypes = new ArrayList<>();
            String name;

            List<? extends DomainProperty> props = HaplotypeAssayProvider.getDomainProps(protocol);

            HashSet<String> defaults = HaplotypeAssayProvider.getDefaultColumns();

            for (DomainProperty column : props)
            {
                if (_dataMap.get(column.getName()) != null)
                {
                    name = column.getName();
                    if (name.endsWith("1") || name.endsWith("2"))
                    {
                        String type = name.substring(0, name.length() - 1).replaceAll("Haplotype", "");

                        int diploidNumber = name.endsWith("1") ? 1 : 2;

                        if (!column.isShownInInsertView() && !defaults.contains(name))
                            rowHaplotypes.add(new HaplotypeAssignment(new HaplotypeIdentifier(_dataMap.get(name), type), diploidNumber));
                    }
                }
            }
            return rowHaplotypes;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testParseHaplotypeData()
        {
//            try
//            {
//                Map<String, String> animals = new CaseInsensitiveTreeMap<String>();
//                List<Pair<String, String>> haplotypes = new ArrayList<Pair<String, String>>();
//                List<HaplotypeAssignmentDataRow> datarows = new ArrayList<HaplotypeAssignmentDataRow>();
//                Map<String, String> colMap = new CaseInsensitiveTreeMap<String>();
//                List<Map<String, Object>> rowsMap = new ArrayList<Map<String, Object>>();
//
//                // populate the column header map
//                for (Map.Entry<String, HaplotypeColumnMappingProperty> property : HaplotypeAssayProvider.getColumnMappingProperties().entrySet())
//                {
//                    colMap.put(property.getKey(), property.getValue().getLabel());
//                }
//
//                // add rows to the row map (to represent the results from a TabLoader
//                rowsMap.add(getRowMap(new String[]{"ID-1", "x59045", "6,927", "5550", "A007", "A008", "B048", "B043a"}));
//                rowsMap.add(getRowMap(new String[]{"ID-2", "x43290", "4,339", "3522", "A007", "A004", "B048", "B069a"}));
//                rowsMap.add(getRowMap(new String[]{"ID-3", "x00495", "4,068", "3362", "A023", "A026", "B017", "B017(H)"}));
//                rowsMap.add(getRowMap(new String[]{"ID-4", "x45763", "4,603", "3889", "A023", "A023(H)", "B017", "B015a"}));
//                rowsMap.add(getRowMap(new String[]{"ID-5", "x90453", "5,836", "5020", "A002", "A002", "B012b", "B012b"}));
//
////                List<HaplotypeAssignmentDataRow> response = new HaplotypeDataHandler().parseHaplotypeData(
////                        rowsMap, colMap, animals, haplotypes, datarows);
//
//                // check the list of animals
//                assertEquals("Unexpected number of animal IDs", 5, animals.size());
//                assertEquals("x59045", animals.get("ID-1"));
//                assertEquals("x90453", animals.get("ID-5"));
//                // check the list of haplotypes
//                assertEquals("Unexpected number of haplotype names", 20, haplotypes.size());
//                assertEquals(new Pair<String, String>("A007", "Mamu-A"), haplotypes.get(0));
//                assertEquals(new Pair<String, String>("B012b", "Mamu-B"), haplotypes.get(19));
//                // check the last data row
//                assertEquals("Unexpected number of HaplotypeAssignmentDataRows parsed", 5, response.size());
//                HaplotypeAssignmentDataRow r = response.get(4);
//                assertEquals("A002", r.getHaplotypeList().get(0).first);
//                assertEquals("Mamu-A", r.getHaplotypeList().get(0).second);
//                assertEquals("A002", r.getHaplotypeList().get(1).first);
//                assertEquals("Mamu-A", r.getHaplotypeList().get(1).second);
//                assertEquals("B012b", r.getHaplotypeList().get(2).first);
//                assertEquals("Mamu-B", r.getHaplotypeList().get(2).second);
//                assertEquals("B012b", r.getHaplotypeList().get(3).first);
//                assertEquals("Mamu-B", r.getHaplotypeList().get(3).second);
//                assertEquals("x90453", r.getMapValue(HaplotypeAssayProvider.CLIENT_ANIMAL_COLUMN.getName()));
//                assertEquals(new Integer(5836), r.getIntegerValue(HaplotypeAssayProvider.TOTAL_READS_COLUMN.getName(), 0));
//                assertEquals(new Integer(5020), r.getIntegerValue(HaplotypeAssayProvider.IDENTIFIED_READS_COLUMN.getName(), 0));
//                assertEquals(null, r.getIntegerValue("keyDoesntExist", 0));
//            }
//            catch(Exception e)
//            {
//                fail(e.getMessage());
//            }
        }

        private Map<String, Object> getRowMap(String[] valuesArr)
        {
            Map<String, Object> row = new HashMap<>();
            int index = 0;
            for (Map.Entry<String, HaplotypeColumnMappingProperty> property : HaplotypeAssayProvider.getColumnMappingProperties(true).entrySet())
            {
                row.put(property.getValue().getLabel(), valuesArr[index]);
                index++;
            }
            return row;
        }
    }
}
