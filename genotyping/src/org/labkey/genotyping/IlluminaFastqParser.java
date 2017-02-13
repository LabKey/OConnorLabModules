/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequence.IlluminaReadHeader;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is designed to parse the FASTQ files produced by a single run on an Illumina instrument and produce one gzipped FASTQ
 * for each sample in that run.  Parsing that CSV file to obtain the sample list is upstream of this class.
 * It is designed to be called from a pipeline job, although it should not need to be.
 *
 * User: bbimber
 * Date: 4/18/12
 * Time: 11:35 AM
 */
public class IlluminaFastqParser
{
    private String _outputPrefix;
    private Map<Integer, Integer> _sampleIndexToIdMap;
    private Map<Integer, Integer> _sampleIdToIndexMap;
    private List<File> _files;
    private Map<Pair<Integer, Integer>, File> _fileMap;
    private Map<Pair<Integer, Integer>, Integer> _sequenceTotals;
    private Logger _logger;
    private static FileType FASTQ_FILETYPE = new FileType(Arrays.asList("fastq", "fq"), "fastq", FileType.gzSupportLevel.SUPPORT_GZ);

    public IlluminaFastqParser(@Nullable String outputPrefix, Map<Integer, Integer> sampleIndexToIdMap, Map<Integer, Integer> sampleIdToIndexMap, Logger logger, List<File> files)
    {
        _outputPrefix = outputPrefix;
        _sampleIndexToIdMap = sampleIndexToIdMap;
        _sampleIdToIndexMap = sampleIdToIndexMap;
        _files = files;
        _logger = logger;
    }

    // because Illumina sample CSV files do not provide a clear way to identify the FASTQ files/
    // this method accepts the CSV input and an optional FASTQ file prefix.  it will return any
    // FASTQ files or zipped FASTQs in the same folder as the CSV and filter using the prefix, if provided.
    public static List<File> inferIlluminaInputsFromPath(String path, @Nullable String fastqPrefix)
    {
        File folder = new File(path);
        List<File> result = new ArrayList<>();

        for (File f : folder.listFiles())
        {
            if(!FASTQ_FILETYPE.isType(f))
                continue;
            // Skip over files whose main file name ends in _IX_XXX where X is any digit
            if (FASTQ_FILETYPE.getBaseName(f).matches(".*_I\\d_\\d\\d\\d"))
                continue;

            if(fastqPrefix != null && !f.getName().startsWith(fastqPrefix))
                continue;

            result.add(f);
        }

        return result;
    }

    //this returns a map connecting samples with output FASTQ files.
    // the key of the map is a pair where the first item is the sampleId and the second item indicated whether this file is the forward (1) or reverse (2) reads
    public Map<Pair<Integer, Integer>, File> parseFastqFiles(PipelineJob job) throws PipelineJobException
    {
        _fileMap = new HashMap<>();
        _sequenceTotals = new HashMap<>();

        FastqReader reader = null;

        // Original->target mapping
        Map<File, File> filesToMove = new LinkedHashMap<>();
        Map<String, Integer> fileNameWithoutPairingInfoMap = new LinkedHashMap<>();//ex. if file name is SampleSheet-R1-1234.fastq, this map contains SampleSheet-1234

        int index = 1;
        for (File f : _files)
        {
            job.setStatus("PARSING FILE " + index + " OF " + _files.size());

            if(f.length() == 0)
            {
                _logger.info("File " + f.getName() + " has no content to parse.");
                continue;
            }
            try
            {
                _logger.info("Beginning to parse file: " + f.getName());
                File targetDir = f.getParentFile();
                String fileName = f.getName();

                reader = new FastqReader(f);
                int sampleIdx = Integer.MIN_VALUE;
                int pairNumber = Integer.MIN_VALUE;
                int totalReads = 0;
                while (reader.hasNext())
                {
                    FastqRecord fq = reader.next();
                    String header = fq.getReadHeader();
                    IlluminaReadHeader parsedHeader = new IlluminaReadHeader(header, fileName);
                    if(parsedHeader.getSampleName() != null)  // may be new header format, so let's try alternate lookup
                    {
                        try
                        {
                            parsedHeader.setSampleNum(_sampleIdToIndexMap.get(Integer.parseInt(parsedHeader.getSampleName())));
                        }
                        catch (NumberFormatException nfe)
                        {
                            throw new PipelineJobException("Could not resolve index for sample named '" + parsedHeader.getSampleName() + "'. Sample map is: " + _sampleIdToIndexMap);
                        }
                    }
                    if ((sampleIdx != Integer.MIN_VALUE && sampleIdx != parsedHeader.getSampleNum()) ||
                            (pairNumber != Integer.MIN_VALUE && pairNumber != parsedHeader.getPairNumber()))
                        throw new IllegalStateException("Only one sample ID is allowed per fastq file.");
                    sampleIdx = parsedHeader.getSampleNum();
                    pairNumber = parsedHeader.getPairNumber();
                    totalReads++;
                }

                String error = addToPairingInfoMap(fileName, fileNameWithoutPairingInfoMap, totalReads);
                if(null != error)
                {
                    _logger.error(error);
                    reader.close();
                    throw new PipelineJobException();
                }
                else if(reader.getLineNumber() == 1 && totalReads == 0 && !f.getName().contains("null"))//empty file
                {
                    _logger.warn("File " + fileName + " has no content to parse.");
                    reader.close();
                    continue;
                }
                else
                {
                    reader.close();
                    Pair<Integer, Integer> key = Pair.of(_sampleIndexToIdMap.get(sampleIdx), pairNumber);
                    _sequenceTotals.put(key, totalReads);

                    Integer sampleId = _sampleIndexToIdMap.get(sampleIdx);
                    if (sampleIdx != 0 && sampleId == null)
                    {
                        throw new PipelineJobException("Could not resolve id for sample at index " + sampleIdx + ". Sample map is: " + _sampleIndexToIdMap);
                    }
                    String name = (_outputPrefix == null ? "Reads" : _outputPrefix) + "-R" + pairNumber + "-" + (sampleIdx == 0 ? "Control" : sampleId) + ".fastq.gz";
                    File newFile = new File(targetDir, name);

                    if (!f.equals(newFile))
                    {
                        filesToMove.put(f, newFile);
                    }
                    _fileMap.put(Pair.of(sampleId, pairNumber), newFile);
                    _logger.info("Finished parsing file: " + f.getName());
                }
            }
            finally
            {
                if (reader != null)
                    reader.close();
            }
            index++;
        }

        checkForDuplicateTargets(filesToMove);
        checkForExistingTargets(filesToMove.values());

        // Rename the files to the preferred convention after we've finished processing all of the files
        try
        {
            for (Map.Entry<File, File> entry : filesToMove.entrySet())
            {
                File oldFile = entry.getKey();
                File newFile = entry.getValue();
                FileUtils.moveFile(oldFile, newFile);
                _logger.info("Moved file " + oldFile.getName() + " to " + newFile.getName() );
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        Map<Pair<Integer, Integer>, File> outputs = new HashMap<>();
        for (Pair<Integer, Integer> key : _fileMap.keySet())
        {
            outputs.put(key, _fileMap.get(key));
        }

        return outputs;
    }

    /** Checks if one or more desired target files are already present */
    private void checkForExistingTargets(Collection<File> targetFiles) throws PipelineJobException
    {
        Set<File> existingFiles = new HashSet<>();
        for (File targetFile : targetFiles)
        {
            if (targetFile.exists())
            {
                existingFiles.add(targetFile);
            }
        }
        if (!existingFiles.isEmpty())
        {
            throw new PipelineJobException("Input files cannot be renamed - at least one target file already exists: " + existingFiles);
        }
    }

    /** Ensure that we don't have multiple source files trying to map to the same target file */
    private void checkForDuplicateTargets(Map<File, File> filesToMove) throws PipelineJobException
    {
        ListValuedMap<File, File> map = new ArrayListValuedHashMap<>();
        for (Map.Entry<File, File> entry : filesToMove.entrySet())
        {
            map.put(entry.getValue(), entry.getKey());
        }

        boolean error = false;
        for (File targetFile : map.keySet())
        {
            List<File> sourceFiles = map.get(targetFile);
            if (sourceFiles.size() > 1)
            {
                error = true;
                _logger.error("Multiple input files map to the target file " + targetFile + " - they are: " + sourceFiles);
            }
        }
        if (error)
        {
            throw new PipelineJobException("Some target files have more than one source file, see output for details");
        }
    }

    private String addToPairingInfoMap(String fileName, Map<String, Integer> m, int readCount)
    {
        String fileNameWithoutPairingInfo = "";
        if(fileName.contains("-R1"))
        {
            String[] splitStr = fileName.split("R1");
            for(String s : splitStr)
                fileNameWithoutPairingInfo += s;
        }
        else if(fileName.contains("-R2"))
        {
            String[] splitStr = fileName.split("R2");
            for (String s : splitStr)
                fileNameWithoutPairingInfo += s;
        }

        if(m.containsKey(fileNameWithoutPairingInfo))
        {
            Integer rc = m.get(fileNameWithoutPairingInfo);
            if(readCount == 0 && rc != 0)
                return fileName + " is empty and has 0 reads, while its pair file is not empty and has " + rc + " reads.";
            else if(rc == 0 && readCount != 0)
                return fileName + " has " + readCount + " reads, while its pair file is empty and has 0 reads.";
        }
        else
            m.put(fileNameWithoutPairingInfo, new Integer(readCount));

        return null;

    }

    public Map<Pair<Integer, Integer>, Integer> getReadCounts()
    {
        return _sequenceTotals;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testNoDupes() throws PipelineJobException
        {
            IlluminaFastqParser parser = new IlluminaFastqParser(null, Collections.emptyMap(), Collections.emptyMap(), Logger.getLogger(TestCase.class), Collections.emptyList());
            Map<File, File> files = new HashMap<>();
            files.put(new File("a"), new File("b"));
            files.put(new File("c"), new File("d"));
            parser.checkForDuplicateTargets(files);
        }

        @Test(expected = PipelineJobException.class)
        public void testDupeTargets() throws PipelineJobException
        {
            IlluminaFastqParser parser = new IlluminaFastqParser(null, Collections.emptyMap(), Collections.emptyMap(), Logger.getLogger(TestCase.class), Collections.emptyList());
            Map<File, File> files = new HashMap<>();
            files.put(new File("a"), new File("b"));
            files.put(new File("c"), new File("b"));
            parser.checkForDuplicateTargets(files);
        }

    }
}


