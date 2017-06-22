/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;

/**
 * User: adam
 * Date: Sep 22, 2010
 * Time: 7:34:16 AM
 */

// This job imports all the reads for the run.  Once imported, users can (optionally) submit an analysis
// of this run to Galaxy (see GalaxySubmitJob).
public class Import454ReadsJob extends ReadsJob
{
    private final File _reads;
    private final static boolean TEST_COMRESSION = false;

    public Import454ReadsJob(ViewBackgroundInfo info, PipeRoot root, File reads, GenotypingRun run)
    {
        super(Import454ReadsPipelineProvider.NAME, info, root, run);
        _reads = reads;
        setLogFile(new File(_reads.getParentFile(), FileUtil.makeFileNameWithTimestamp("import_reads", "log")));
    }

    @Override
    public String getDescription()
    {
        return "Import 454 reads for run " + _run.getRowId();
    }

    @Override
    public void run()
    {
        try
        {
            updateRunStatus(Status.Importing);

            try
            {
                importReads();
            }
            catch (SQLException se)
            {
                if (RuntimeSQLException.isConstraintException(se) && StringUtils.containsIgnoreCase(se.getMessage(), "uq_reads_name"))
                    throw new RuntimeException("A readname in this file already exists in the database; this run may have been imported previously", se);
                else
                    throw se;
            }

            updateRunStatus(Status.Complete);
            info("Import 454 reads complete");
            setStatus(TaskStatus.complete);
        }
        catch (Exception e)
        {
            error("Import 454 reads failed", e);
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

    AtomicLong totalSequence = new AtomicLong(0);
    AtomicLong totalQuality = new AtomicLong(0);
    AtomicLong deflateSequence = new AtomicLong(0);
    AtomicLong deflateQuality = new AtomicLong(0);
    AtomicLong rleSequence = new AtomicLong(0);
    AtomicLong rleQuality = new AtomicLong(0);
    AtomicLong rleAsciiSequence = new AtomicLong(0);
    AtomicLong rleAsciiQuality = new AtomicLong(0);

    private void importReads() throws IOException, SQLException, PipelineJobException
    {
        info("Importing " + _reads.getName());
        setStatus("IMPORTING READS");

        try (TabLoader loader = new TabLoader(_reads, true))
        {
            List<ColumnDescriptor> columns = new ArrayList<>();
            columns.addAll(Arrays.asList(loader.getColumns()));

            for (ColumnDescriptor col : columns)
            {
                col.name = col.name.replace("read_", "");
                col.name = col.name.replace("_", "");

                // Map "mid" (old name) and "mid5" (alias) to canonical name ("fivemid")
                if ("mid".equalsIgnoreCase(col.name) || "mid5".equalsIgnoreCase(col.name))
                    col.name = SampleManager.MID5_COLUMN_NAME;

                // Map "mid3" (alias) to canonical name ("threemid")
                if ("mid3".equalsIgnoreCase(col.name))
                    col.name = SampleManager.MID3_COLUMN_NAME;
            }

            Set<String> sampleKeyColumns = new HashSet<>();

            for (ColumnDescriptor col : columns)
                if (SampleManager.POSSIBLE_SAMPLE_KEYS.contains(col.name))
                    sampleKeyColumns.add(col.name);

            columns.add(new ColumnDescriptor("run", Integer.class, _run.getRowId()));
            columns.add(new ColumnDescriptor("sampleid", Integer.class));
            loader.setColumns(columns.toArray(new ColumnDescriptor[columns.size()]));

            SampleManager.SampleIdFinder finder = new SampleManager.SampleIdFinder(_run, getUser(), sampleKeyColumns, "importing reads");

            int rowCount = 0;

            TableInfo readsTable = GenotypingSchema.get().getReadsTable();
            DbScope scope = readsTable.getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (Map<String, Object> map : loader)
                {
                    Integer mid5 = (Integer) map.get(SampleManager.MID5_COLUMN_NAME);

                    // mid5 == 0 means null
                    if (null != mid5 && 0 == mid5)
                    {
                        mid5 = null;
                        map.put(SampleManager.MID5_COLUMN_NAME, null);
                    }

                    Integer mid3 = (Integer) map.get(SampleManager.MID3_COLUMN_NAME);

                    // mid3 == 0 means null
                    if (null != mid3 && 0 == mid3)
                    {
                        mid3 = null;
                        map.put(SampleManager.MID3_COLUMN_NAME, null);
                    }

                    map.put("sampleid", finder.getSampleId(mid5, mid3, (String) map.get(SampleManager.AMPLICON_COLUMN_NAME)));

                    String sequence = (String) map.get("sequence");
                    String quality = (String) map.get("quality");

                    totalSequence.addAndGet(sequence.length());
                    totalQuality.addAndGet(quality.length());

                    if (sequence.length() != quality.length())
                        throw new PipelineJobException("Sequence length differed from quality score length in read " + map.get("name"));

                    if (TEST_COMRESSION)
                        compress(sequence, quality);

                    Table.insert(getUser(), readsTable, map);
                    rowCount++;

                    if (0 == rowCount % 10000)
                        logReadsProgress("", rowCount);
                }

                transaction.commit();
            }
            logReadsProgress("Importing " + _reads.getName() + " complete: ", rowCount);
            setStatus("UPDATING STATISTICS");
            info("Updating reads table statistics");

            if (TEST_COMRESSION)
            {
                info("Sequence deflate ratio: " + Formats.percent1.format((double) deflateSequence.get() / totalSequence.get()));
                info("Sequence simple RLE ratio: " + Formats.percent1.format((double) rleSequence.get() / totalSequence.get()));
                info("Sequence ascii RLE ratio: " + Formats.percent1.format((double) rleAsciiSequence.get() / totalSequence.get()));
                info("Quality score deflate ratio: " + Formats.percent1.format((double) deflateQuality.get() / totalQuality.get()));
                info("Quality score simple RLE ratio: " + Formats.percent1.format((double) rleQuality.get() / totalQuality.get()));
                info("Quality score ascii RLE ratio: " + Formats.percent1.format((double) rleAsciiQuality.get() / totalQuality.get()));
            }

            readsTable.getSchema().getSqlDialect().updateStatistics(readsTable);
        }
    }


    // Test three potential compression algorithms for sequences & quality scores
    private void compress(String sequence, String quality)
    {
        try
        {
            byte[] b;
            testAndAccumulate(deflateSequence, sequence, b = Compress.deflate(sequence), Compress.inflate(b));
            testAndAccumulate(rleSequence, sequence, b = Compress.compressRle(sequence, Compress.Algorithm.SimpleRle), Compress.decompressRle(b, Compress.Algorithm.SimpleRle));
            testAndAccumulate(rleAsciiSequence, sequence, b = Compress.compressRle(sequence, Compress.Algorithm.AsciiRle), Compress.decompressRle(b, Compress.Algorithm.AsciiRle));

            testAndAccumulate(deflateQuality, quality, b = Compress.deflate(quality), Compress.inflate(b));
            testAndAccumulate(rleQuality, quality, b = Compress.compressRle(quality, Compress.Algorithm.SimpleRle), Compress.decompressRle(b, Compress.Algorithm.SimpleRle));
            testAndAccumulate(rleAsciiQuality, quality, b = Compress.compressRle(quality, Compress.Algorithm.AsciiRle), Compress.decompressRle(b, Compress.Algorithm.AsciiRle));
        }
        catch (DataFormatException e)
        {
            error("Decompression failed on sequence or quality score");
        }
    }


    private void testAndAccumulate(AtomicLong count, String source, byte[] compressed, String decompressed)
    {
        if (!source.equals(decompressed))
            warn("Roundtripping failed!");

        count.addAndGet(compressed.length);
    }


    private void logReadsProgress(String prefix, int count)
    {
        String formattedCount = Formats.commaf0.format(count);
        info(prefix + formattedCount + " reads imported");
        setStatus(formattedCount + " READS");    // Doesn't actually work... we're in one big transaction, so this doesn't update.
    }
}
