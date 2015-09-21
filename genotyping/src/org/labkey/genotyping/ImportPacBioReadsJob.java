/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

/*
 * User: binalpatel
 */
package org.labkey.genotyping;

import au.com.bytecode.opencsv.CSVReader;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ImportPacBioReadsJob extends ReadsJob
{
    private static FileType FASTQ_FILETYPE = new FileType(Arrays.asList("fastq", "fq"), "fastq", FileType.gzSupportLevel.SUPPORT_GZ);

    private File _sampleFile;
    private Map<File, List<File>> _dirFastqMap;//size of this map == num of pools in a run.
    private List<File> _fastqFiles;
    private String _fastqPrefix;
    private GenotypingRun _run;
    private List<PacBioPool> _pools = new LinkedList<>();
    private String _dirSubstring = "pool";
    private String[] _extensions = {"fastq.gz", "fastq"};

    public ImportPacBioReadsJob(ViewBackgroundInfo info, PipeRoot root, File sampleFile, GenotypingRun run, @Nullable String fastqPrefix)
    {
        super("Process PacBio Reads", info, root);
        _sampleFile = sampleFile;
        _run = run;
        _fastqPrefix = fastqPrefix;
        setLogFile(new File(_sampleFile.getParentFile(), FileUtil.makeFileNameWithTimestamp("import_pacbio_reads", "log")));
        _dirFastqMap = new HashMap<>();
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public void run()
    {
        try
        {
            updateRunStatus(Status.Importing, _run);

            importReads();

            updateRunStatus(Status.Complete, _run);
            info("Processing PacBio reads complete");
            setStatus(TaskStatus.complete);

            sendMessageToUser(_run, GenotypingManager.SEQUENCE_PLATFORMS.PACBIO.name());
        }
        catch (CancelledException e)
        {
            setActiveTaskStatus(TaskStatus.cancelled);
        }
        catch (Exception e)
        {
            error("Processing PacBio reads failed", e);
            setStatus(TaskStatus.error);

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

    private void importReads() throws PipelineJobException
    {

        Map<String, Integer> sampleNameSampleIdMap = new HashMap<>();
        Map<Integer, Object> sampleIdsFromSamplesList = null;

        try
        {
            sampleIdsFromSamplesList = SampleManager.get().getSampleIdsFromSamplesList(getContainer(), getUser(), _run, "importing reads");
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        readFromSampleSheetFile(sampleNameSampleIdMap, sampleIdsFromSamplesList);

        collectFastqFilesAsPools();

        //error if no pools were found and no fastq files were found to parse
        if (_pools.size() == 0)
        {
            try
            {
                throw new PipelineJobException("No FASTQ files" + (_fastqPrefix == null ? "" : " matching the prefix '" + _fastqPrefix) + "' were found.");
            }
            catch (PipelineJobException e)
            {
                e.printStackTrace();
            }
        }

        persistPacBioPoolRecords(sampleNameSampleIdMap);
    }

    private void readFromSampleSheetFile(Map<String, Integer> sampleNameSampleIdMap, Map<Integer, Object> sampleIdsFromSamplesList) throws PipelineJobException
    {
        try
        {
            CSVReader reader = new CSVReader(new FileReader(_sampleFile));
            String[] nextLine;
            boolean inSamples = false;

            try
            {
                while ((nextLine = reader.readNext()) != null)
                {
                    if (nextLine.length == 0 || null == nextLine[0] || nextLine[0].trim().isEmpty())
                        continue;

                    if (nextLine.length > 0 && "[Data]".equals(nextLine[0]))
                    {
                        inSamples = true;
                        continue;
                    }

                    if (!inSamples)
                        continue;

                    if ("Sample_ID".equalsIgnoreCase(nextLine[0].trim()))
                        continue;

                    int sampleId = Integer.parseInt(nextLine[0].trim());

                    //identify whether Sample ID in sample sheet matches Sample ID in samples list
                    if(!sampleIdsFromSamplesList.containsKey(sampleId))
                        throw new PipelineJobException("Sample ID" + sampleId + " does not match Sample ID in samples list.");

                    String sampleName = nextLine[1].trim();

                    //Unlike MiSeq, PacBio fastq file names do not have sample id substring in its name to be able to associate
                    //fastq file with that sample id. Hence, we are requiring to provide sample name, which in this case will
                    //be the fastq file name (without the .fastq at least now).
                    if (StringUtils.isEmpty(sampleName))
                        throw new PipelineJobException("Sample Name for Sample ID" + sampleId + " cannot be empty.");

                    sampleNameSampleIdMap.put(sampleName.toLowerCase().trim(), sampleId);
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    private void collectFastqFilesAsPools()
    {
        String path = _sampleFile.getParent();
        File sampleSheetRootFolder = new File(path);

        //gather and store fastq files
        if (sampleSheetRootFolder.isDirectory())
        {
            if (sampleSheetRootFolder.getName().toLowerCase().contains(_dirSubstring))
            {
                int extractedPoolNumFromDirName = extractPoolNumFromDirectoryName(sampleSheetRootFolder.getName());

                Collection<File> allFastqFiles = FileUtils.listFiles(sampleSheetRootFolder, _extensions, false); //gather all the fastq files in this directory

                if (_fastqPrefix != null)//filter files w/prefix (if prefix is provided)
                {
                    for (File f : sampleSheetRootFolder.listFiles())
                    {
                        Collection<File> filteredWithPrefixFastqFiles = new LinkedList<>();

                        for (File fastqFile : allFastqFiles)
                        {
                            if (!FASTQ_FILETYPE.isType(fastqFile))
                                continue;

                            if (!f.getName().startsWith(_fastqPrefix))
                                continue;

                            filteredWithPrefixFastqFiles.add(fastqFile);
                        }
                        _pools.add(new PacBioPool(extractedPoolNumFromDirName, filteredWithPrefixFastqFiles)); //add prefixed fastq files
                    }
                }
                else
                    _pools.add(new PacBioPool(extractedPoolNumFromDirName, allFastqFiles)); //add all fastq files
            }
        }
    }

    private void persistPacBioPoolRecords(Map<String, Integer> sampleNameSampleIdMap)
    {
        for(PacBioPool pool : _pools)
        {
            Map<String, Object> row;

            int fileCount = 1;
            info("Recording records for each FASTQ file in Pool " + pool.getPoolNum() + ".");
            for (File file : pool.getFastqFiles())
            {
                Integer sampleId = getSampleId(file, sampleNameSampleIdMap);
                Integer numReads = getNumReads(file, fileCount++, pool.getPoolNum());

                row = new CaseInsensitiveHashMap<>();
                row.put("Run", _run.getRowId());
                row.put("PoolNum", pool.getPoolNum());
                row.put("SampleId", sampleId);
                row.put("ReadCount", numReads);
                ExpData data = ExperimentService.get().createData(getContainer(), new DataType("PacBio FASTQ File " + file.getName()));
                data.setDataFileURI(file.toURI());
                data.setName(file.getName());
                data.save(getUser());
                row.put("DataId", data.getRowId());

                try
                {
                    Table.insert(getUser(), GenotypingSchema.get().getSequenceFilesTable(), row);
                }
                catch (RuntimeSQLException rse)
                {
                    rse.printStackTrace();
                }

            }
        }
    }

    private void validateSampleID(int sampleId, Results results)
    {
        Map<FieldKey, Integer> key = results.getFieldIndexMap();
    }

    private int extractPoolNumFromDirectoryName(String dirName)
    {
        String[] splitStr = dirName.split("_"); //expected directory name pattern to be "pool1_barcoded_fastq", "123_pool2_..."

        //extract pool num
        for(int i = 0 ; i < splitStr.length ; i++)
        {
            if (splitStr[i].contains(_dirSubstring))
                return Integer.valueOf(splitStr[i].substring(_dirSubstring.length()));
        }
        return 1; //if pool num is not found, then default pool num to be 1.
    }

    private int getSampleId(File file, Map<String, Integer> sampleNameSampleIdMap)
    {
        String fileName = FileUtil.getBaseName(file).trim().toLowerCase();
        return sampleNameSampleIdMap.get(fileName);
    }

    public Integer getNumReads(File fastqFile, int fileNum, int poolNum)
    {
        FastqReader reader = null;
        int totalReads = 0;

        setStatus("PARSING FILE " + fileNum + " in Pool " + poolNum);

        if(fastqFile.length() == 0)
        {
            _logger.info("File " + fastqFile.getName() + " has no content to parse.");
            return 0;
        }

        _logger.info("Beginning to parse file: " + fastqFile.getName());

        reader = new FastqReader(fastqFile);
        while (reader.hasNext())
        {
            try
            {
                FastqRecord fq = reader.next();
                totalReads++;
            }
            catch(SAMException same)
            {
                setStatus("Error parsing " + fileNum + " in Pool " + poolNum + ". See Status.");
                _logger.error("Error parsing " + fastqFile.getName() + " in Pool " + poolNum + ", read count may be inaccurate: " + same.getMessage());
                reader.close();
                return totalReads;
            }
        }

        _logger.info("Finished parsing file: " + fastqFile.getName());

        reader.close();

        return totalReads;
    }

    class PacBioPool
    {
        int poolNum;
        Collection <File> fastqFiles;
        Map <File, Integer> fileNumReadsMap;
        Map <File, Integer> fileSampleIdMap;

        public PacBioPool(int poolNum, Collection <File> fastqFiles)
        {
            this.poolNum = poolNum;
            this.fastqFiles = fastqFiles;
            fileNumReadsMap = new HashMap<>();
            fileSampleIdMap = new HashMap<>();
//            removeEmptyFastqFiles();
        }

        public int getPoolNum()
        {
            return poolNum;
        }

        public void setPoolNum(int poolNum)
        {
            this.poolNum = poolNum;
        }

        public Collection<File> getFastqFiles()
        {
            return fastqFiles;
        }

        public void setFastqFiles(Collection<File> fastqFiles)
        {
            this.fastqFiles = fastqFiles;
        }

        public Map <File, Integer> getFileNumReadsMap()
        {
            return fileNumReadsMap;
        }

        public Map <File, Integer> getFileSampleIdMap()
        {
            return fileSampleIdMap;
        }

        private void removeEmptyFastqFiles()
        {
            for(File f : fastqFiles)
            {
                if(f.length() == 0)
                    fastqFiles.remove(f);
            }
        }
    }
}//end class