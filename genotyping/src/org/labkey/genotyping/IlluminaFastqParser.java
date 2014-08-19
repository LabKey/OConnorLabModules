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

import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequence.IlluminaReadHeader;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is designed to parse the FASTQ files produced by a single run on an illumina instructment and produce one gzipped FASTQ
 * for each sample in that run.  Parsing that CSV file to obtain the sample list is upstream of this class.
 * It is designed to be called from a pipeline job, although it should not need to be.
 *
 * User: bbimber
 * Date: 4/18/12
 * Time: 11:35 AM
 */
public class IlluminaFastqParser<SampleIdType>
{
    private String _outputPrefix;
    private Map<Integer, SampleIdType> _sampleMap;
    private List<File> _files;
    private Map<Pair<SampleIdType, Integer>, File> _fileMap;
    private Map<Pair<SampleIdType, Integer>, Integer> _sequenceTotals;
    private Logger _logger;
    private static FileType FASTQ_FILETYPE = new FileType(Arrays.asList("fastq", "fq"), "fastq", FileType.gzSupportLevel.SUPPORT_GZ);

    public IlluminaFastqParser(@Nullable String outputPrefix, Map<Integer, SampleIdType> sampleMap, Logger logger, List<File> files)
    {
        _outputPrefix = outputPrefix;
        _sampleMap = sampleMap;
        _files = files;
        _logger = logger;
    }

    // because ilumina sample CSV files do not provide a clear way to identify the FASTQ files/
    // this method accepts the CSV input and an optional FASTQ file prefix.  it will return any
    // FASTQ files or zipped FASTQs in the same folder as the CSV and filter using the prefix, if provided.
    public static List<File> inferIlluminaInputsFromPath(String path, @Nullable String fastqPrefix)
    {
        File folder = new File(path);
        List<File> _fastqFiles = new ArrayList<>();

        for (File f : folder.listFiles())
        {
            if(!FASTQ_FILETYPE.isType(f))
                continue;

            if(fastqPrefix != null && !f.getName().startsWith(fastqPrefix))
                continue;

            _fastqFiles.add(f);
        }

        return _fastqFiles;
    }

    //this returns a map connecting samples with output FASTQ files.
    // the key of the map is a pair where the first item is the sampleId and the second item indicated whether this file is the forward (1) or reverse (2) reads
    public Map<Pair<SampleIdType, Integer>, File> parseFastqFiles() throws PipelineJobException
    {
        _fileMap = new HashMap<>();
        _sequenceTotals = new HashMap<>();

        FastqReader reader = null;

        for (File f : _files)
        {
            try
            {
                _logger.info("Beginning to parse file: " + f.getName());
                File targetDir = f.getParentFile();

                reader = new FastqReader(f);
                int sampleIdx = Integer.MIN_VALUE;
                int pairNumber = Integer.MIN_VALUE;
                int totalReads = 0;
                while (reader.hasNext())
                {
                    FastqRecord fq = reader.next();
                    String header = fq.getReadHeader();
                    IlluminaReadHeader parsedHeader = new IlluminaReadHeader(header);
                    if ((sampleIdx != Integer.MIN_VALUE && sampleIdx != parsedHeader.getSampleNum()) ||
                            (pairNumber != Integer.MIN_VALUE && pairNumber != parsedHeader.getPairNumber()))
                        throw new IllegalStateException("Only one sample ID is allowed per fastq file.");
                    sampleIdx = parsedHeader.getSampleNum();
                    pairNumber = parsedHeader.getPairNumber();
                    totalReads++;
                }
                reader.close();
                Pair<SampleIdType, Integer> key = Pair.of(_sampleMap.get(sampleIdx), pairNumber);
                _sequenceTotals.put(key, totalReads);


                SampleIdType sampleId = _sampleMap.get(sampleIdx);
                String name = (_outputPrefix == null ? "Reads" : _outputPrefix) + "-R" + pairNumber + "-" + (sampleIdx == 0 ? "Control" : sampleId) + ".fastq.gz";
                File newFile = new File(targetDir, name);

                if (!f.equals(newFile))
                {
                    FileUtils.moveFile(f, newFile);
                    _logger.info("Move of file " + f.getName() + " to " + newFile.getName() );
                }
                _fileMap.put(Pair.of(sampleId, pairNumber), newFile);
                _logger.info("Finished parsing file: " + f.getName());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            finally
            {
                if (reader != null)
                    reader.close();
            }
        }

        Map<Pair<SampleIdType, Integer>, File> outputs = new HashMap<>();
        for (Pair<SampleIdType, Integer> key :_fileMap.keySet())
        {
            outputs.put(key, _fileMap.get(key));
        }

        return outputs;
    }

    public Map<Pair<SampleIdType, Integer>, Integer> getReadCounts()
    {
        return _sequenceTotals;
    }

}


