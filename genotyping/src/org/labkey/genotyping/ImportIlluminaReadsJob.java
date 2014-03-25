/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequence.IlluminaFastqParser;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bbimber
 * Date: Apr 19, 2012
 * Time: 7:34:16 AM
 */

// This job imports all the reads for the run.
public class ImportIlluminaReadsJob extends PipelineJob
{
    private final File _sampleFile;
    private List<File> _fastqFiles;
    private String _fastqPrefix;
    private final GenotypingRun _run;

    public ImportIlluminaReadsJob(ViewBackgroundInfo info, PipeRoot root, File reads, GenotypingRun run)
    {
        this(info, root, reads, run, null);
    }

    public ImportIlluminaReadsJob(ViewBackgroundInfo info, PipeRoot root, File sampleFile, GenotypingRun run, @Nullable String fastqPrefix)
    {
        super("Import Illumina Reads", info, root);
        _sampleFile = sampleFile;
        _run = run;
        _fastqPrefix = fastqPrefix;
        setLogFile(new File(_sampleFile.getParentFile(), FileUtil.makeFileNameWithTimestamp("import_reads", "log")));
    }

    @Override
    public ActionURL getStatusHref()
    {
        return GenotypingController.getRunURL(getContainer(), _run);
    }


    @Override
    public String getDescription()
    {
        return "Import Illumina reads for run " + _run.getRowId();
    }


    @Override
    public void run()
    {
        try
        {
            updateRunStatus(Status.Importing);

            importReads();

            updateRunStatus(Status.Complete);
            info("Import Illumina reads complete");
            setStatus(COMPLETE_STATUS);
        }
        catch (Exception e)
        {
            error("Import Illumina reads failed", e);
            setStatus(ERROR_STATUS);

            try
            {
                info("Deleting run " + _run.getRowId());
                GenotypingManager.get().deleteRun(_run);
            }
            catch (SQLException se)
            {
                error("Failed to delete run " + _run.getRowId(), se);
            }
        }
    }


    private void updateRunStatus(Status status) throws PipelineJobException, SQLException
    {
        // Issue 14880: if a job has run and failed, we will have deleted the run.  trying to update the status of this non-existant row
        // causes an OptimisticConflictException.  therefore we first test whether the runs exists
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("rowid"), _run.getRowId());

        if (!new TableSelector(GenotypingSchema.get().getRunsTable(), Collections.singleton("RowId"), f, null).exists())
        {
            try
            {
                File file = new File(_run.getPath(), _run.getFileName());
                GenotypingRun newRun = GenotypingManager.get().createRun(getContainer(), getUser(), _run.getMetaDataId(), file, _run.getPlatform());
                _run.setRowId(newRun.getRowId());
            }
            catch (SQLException e)
            {
                if (RuntimeSQLException.isConstraintException(e))
                    throw new PipelineJobException("Run " + _run.getMetaDataId() + " has already been imported");
                else
                    throw e;
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("Status", status.getStatusId());
        Table.update(getUser(), GenotypingSchema.get().getRunsTable(), map, _run.getRowId());
    }

    private void importReads() throws PipelineJobException
    {
        try
        {
            info("Importing Run From File: " + _sampleFile.getName());
            setStatus("IMPORTING READS");

            try (CSVReader reader = new CSVReader(new FileReader(_sampleFile)))
            {
                Set<String> columns = new HashSet<String>()
                {{
                        add(SampleManager.MID5_COLUMN_NAME);
                        add(SampleManager.MID3_COLUMN_NAME);
                        add(SampleManager.AMPLICON_COLUMN_NAME);
                    }};

                SampleManager.SampleIdFinder finder = new SampleManager.SampleIdFinder(_run, getUser(), columns, "importing reads");

                //parse the samples file
                String[] nextLine;
                Map<Integer, Integer> sampleMap = new HashMap<>();
                sampleMap.put(0, 0); //placeholder for control and unmapped reads
                Boolean inSamples = false;
                int sampleIdx = 0;

                while ((nextLine = reader.readNext()) != null)
                {
                    if (nextLine.length > 0 && "[Data]".equals(nextLine[0]))
                    {
                        inSamples = true;
                        continue;
                    }

                    //NOTE: for now we only parse samples.  at a future point we might consider using more of this file
                    if (!inSamples)
                        continue;

                    if (nextLine.length == 0 || null == nextLine[0])
                        continue;

                    if ("Sample_ID".equalsIgnoreCase(nextLine[0]))
                        continue;

                    int sampleId;
                    try
                    {
                        sampleId = Integer.parseInt(nextLine[0]);

                        if (!finder.isValidSampleKey(sampleId))
                            throw new PipelineJobException("Invalid sample Id for this run: " + nextLine[0]);

                        sampleIdx++;
                        sampleMap.put(sampleIdx, sampleId);
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
                IlluminaFastqParser<Integer> parser = new IlluminaFastqParser<>(FileUtil.getBaseName(_run.getFileName()), sampleMap, getLogger(), new ArrayList<>(_fastqFiles));
                Map<Pair<Integer, Integer>, File> fileMap = parser.parseFastqFiles();
                Map<Pair<Integer, Integer>, Integer> readcounts = parser.getReadCounts();

                info("Created " + fileMap.keySet().size() + " FASTQ files");
                info("Compressing FASTQ files");

                //GZIP and create record for each file
                Map<String, Object> row;

                for (Pair<Integer, Integer> sampleKey : fileMap.keySet())
                {
                    row = new CaseInsensitiveHashMap<>();
                    row.put("Run", _run.getRowId());
                    Integer sampleId = (Integer) sampleKey.getKey();
                    if (sampleId > 0)
                        row.put("SampleId", sampleKey.getKey());

                    if (readcounts.containsKey(sampleKey))
                    {
                        row.put("ReadCount", readcounts.get(sampleKey));
                    }

                    File input = fileMap.get(sampleKey);
                    File output = Compress.compressGzip(input);
                    input.delete();

                    if (input.exists())
                        throw new PipelineJobException("Unable to delete file: " + input.getPath());

                    ExpData data = ExperimentService.get().createData(getContainer(), new DataType("Illumina FASTQ File " + sampleKey.getValue()));
                    data.setDataFileURI(output.toURI());
                    data.setName(output.getName());
                    data.save(getUser());
                    row.put("DataId", data.getRowId());

                    Table.insert(getUser(), GenotypingSchema.get().getSequenceFilesTable(), row);
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
