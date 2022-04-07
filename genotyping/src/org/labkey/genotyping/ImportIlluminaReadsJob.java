/*
 * Copyright (c) 2012-2018 LabKey Corporation
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

import au.com.bytecode.opencsv.CSVReader;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This job processes all the reads for the run, but doesn't import them all into the database.
 * User: bbimber
 * Date: Apr 19, 2012
 * Time: 7:34:16 AM
 */

public class ImportIlluminaReadsJob extends ReadsJob
{
    private final File _sampleFile;
    private List<File> _fastqFiles;
    private final String _fastqPrefix;

    @JsonCreator
    protected ImportIlluminaReadsJob(@JsonProperty("_sampleFile") File sampleFile, @JsonProperty("_fastqFiles") List<File> fastqFiles,
                                     @JsonProperty("_fastqPrefix") String fastqPrefix)
    {
        _sampleFile = sampleFile;
        _fastqFiles = fastqFiles;
        _fastqPrefix = fastqPrefix;
    }

    public ImportIlluminaReadsJob(ViewBackgroundInfo info, PipeRoot root, File sampleFile, GenotypingRun run, @Nullable String fastqPrefix)
    {
        super(ImportIlluminaReadsPipelineProvider.NAME, info, root, run);
        _sampleFile = sampleFile;
        _fastqPrefix = fastqPrefix;
        setLogFile(new File(_sampleFile.getParentFile(), FileUtil.makeFileNameWithTimestamp("import_reads", "log")));
    }

    @Override
    public String getDescription()
    {
        return "Process Illumina reads for run " + _run.getRowId() + " in directory " + getLogFile().getParentFile().getName();
    }

    @Override
    public void run()
    {
        try
        {
            updateRunStatus(Status.Importing);

            importReads();

            updateRunStatus(Status.Complete);
            info("Processing Illumina reads complete");
            setStatus(TaskStatus.complete);
            sendMessageToUser(_run, GenotypingManager.SEQUENCE_PLATFORMS.ILLUMINA.name());

        }
        catch (CancelledException e)
        {
            setActiveTaskStatus(TaskStatus.cancelled);
            // Don't need to do anything else, job has already been set to CANCELLED
        }
        catch (Exception e)
        {
            error("Processing Illumina reads failed", e);
            setStatus(TaskStatus.error);
            info("Deleting run " + _run.getRowId());
            GenotypingManager.get().deleteRun(_run);
        }
    }

    private void importReads() throws PipelineJobException, SQLException
    {
        try
        {
            info("Processing Run From File: " + _sampleFile.getName());
            setStatus("PROCESSING READS");

            Set<Integer> sampleIdsFromSamplesList = SampleManager.get().getSampleIdsFromSamplesList(getContainer(), getUser(), _run, "importing reads");

            try (CSVReader reader = new CSVReader(Readers.getReader(_sampleFile)))
            {
                Set<String> columns = new HashSet<>()
                {{
                    add(SampleManager.MID5_COLUMN_NAME);
                    add(SampleManager.MID3_COLUMN_NAME);
                    add(SampleManager.AMPLICON_COLUMN_NAME);
                }};

                SampleManager.SampleIdFinder finder = new SampleManager.SampleIdFinder(_run, getUser(), columns, "importing reads");

                //parse the samples file
                String[] nextLine;
                /* Index is the number of the sample as ordered in SampleType CSV, mapped to the unique RowId for each sample */
                Map<Integer, Integer> sampleIndexToIdMap = new HashMap<>();
                /* Unique RowId for each sample mapped to the index is the number of the sample as ordered in SampleType CSV */
                Map<Integer, Integer> sampleIdToIndexMap = new HashMap<>();
                /* Name for each sample, mapped to the RowId of the sample */
                Map<String, Integer> sampleNameToIdMap = new HashMap<>();
                sampleIndexToIdMap.put(0, 0); //placeholder for control and unmapped reads
                sampleIdToIndexMap.put(0, 0);
                Boolean inSamples = false;
                int sampleIdx = 0;

                while ((nextLine = reader.readNext()) != null)
                {
                    if (nextLine.length > 0 && "[Data]".equals(nextLine[0]))
                    {
                        inSamples = true;
                        continue;
                    }

                    //NOTE: for now we only parse the samples in the [Data] section of the file.
                    // At a future point we might consider using more of this file
                    if (!inSamples)
                        continue;

                    // Skip blank lines
                    if (nextLine.length == 0)
                        continue;

                    // Find the value of the sample ID and name columns when possible
                    String sampleId = StringUtils.trimToNull(nextLine[0]);
                    String sampleName = null;
                    if (nextLine.length > 1)
                    {
                        sampleName = StringUtils.trimToNull(nextLine[1]);
                    }

                    // Skip the header line
                    if ("Sample_ID".equalsIgnoreCase(nextLine[0]))
                        continue;

                    try
                    {
                        sampleIdx++;

                        int parsedSampleId = Integer.parseInt(sampleId);

                        //identify whether Sample ID in sample sheet matches Sample ID in samples list
                        if (!sampleIdsFromSamplesList.contains(parsedSampleId))
                            throw new PipelineJobException("Sample ID " + parsedSampleId + " does not match Sample ID in samples list.");

                        if (!finder.isValidSampleKey(parsedSampleId))
                            throw new PipelineJobException("Invalid sample Id for this run: " + nextLine[0]);

                        sampleIndexToIdMap.put(sampleIdx, parsedSampleId);
                        sampleIdToIndexMap.put(parsedSampleId, sampleIdx);

                        sampleNameToIdMap.put(sampleName, parsedSampleId);
                    }
                    catch (NumberFormatException e)
                    {
                        throw new PipelineJobException("The sample Id was not an integer: " + nextLine[0]);
                    }
                }

                //find the files
                if (null == _fastqFiles)
                {
                    _fastqFiles = IlluminaFastqParser.inferIlluminaInputsFromPath(_sampleFile.getParent(), _fastqPrefix);
                }

                if (_fastqFiles.size() == 0)
                {
                    throw new PipelineJobException("No FASTQ files" + (_fastqPrefix == null ? "" : " matching the prefix '" + _fastqPrefix) + "' were found");
                }

                //now bin the FASTQ files into 2 per sample
                IlluminaFastqParser parser = new IlluminaFastqParser(FileUtil.getBaseName(_run.getFileName()), sampleIndexToIdMap, sampleIdToIndexMap, sampleNameToIdMap,
                        getLogger(), new ArrayList<>(_fastqFiles));
                Map<Pair<Integer, Integer>, IlluminaFastqParser.FileInfo> fileMap = parser.parseFastqFiles(this);

                info("Recording records for each FASTQ file");

                List<Map<String, Object>> listOfRows = new LinkedList<>();

                //GZIP and create record for each file
                Map<String, Object> row;

                for (Pair<Integer, Integer> sampleKey : fileMap.keySet())
                {
                    row = new CaseInsensitiveHashMap<>();
                    row.put("Run", _run.getRowId());
                    Integer sampleId = sampleKey.getKey();
                    if (sampleId > 0)
                        row.put("SampleId", sampleKey.getKey());

                    IlluminaFastqParser.FileInfo fileInfo = fileMap.get(sampleKey);
                    File input = fileInfo.getFile();

                    row.put("ReadCount", fileInfo.getReadCount());

                    ExpData data = ExperimentService.get().createData(getContainer(), new DataType("Illumina FASTQ File " + sampleKey.getValue()));
                    data.setDataFileURI(input.toURI());
                    data.setName(input.getName());
                    data.save(getUser());
                    row.put("DataId", data.getRowId());

                    listOfRows.add(row);
                }

                TableInfo sequenceFilesTable = GenotypingSchema.get().getSequenceFilesTable();
                DbScope scope = sequenceFilesTable.getSchema().getScope();

                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    for (Map<String, Object> rowToInsert : listOfRows)
                        Table.insert(getUser(), sequenceFilesTable, rowToInsert);

                    transaction.commit();
                }
            }
            catch (SQLException e)
            {
                throw new PipelineJobException(e);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
