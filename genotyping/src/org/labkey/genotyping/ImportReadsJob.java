/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Sep 22, 2010
 * Time: 7:34:16 AM
 */

// This job imports all the reads for the run.  Once imported, users can (optionally) submit an analysis
// of this run to Galaxy (see GalaxySubmitJob).
public class ImportReadsJob extends PipelineJob
{
    private final File _reads;
    private final GenotypingRun _run;

    public ImportReadsJob(ViewBackgroundInfo info, PipeRoot root, File reads, GenotypingRun run)
    {
        super("Import Reads", info, root);
        _reads = reads;
        _run = run;
        setLogFile(new File(_reads.getParentFile(), FileUtil.makeFileNameWithTimestamp("import_reads", "log")));
    }


    @Override
    public ActionURL getStatusHref()
    {
        return GenotypingController.getRunURL(getContainer(), _run);
    }


    @Override
    public String getDescription()
    {
        return "Import reads for run " + _run.getRowId();
    }


    @Override
    public void run()
    {
        try
        {
            updateRunStatus(Status.Importing);
            importReads();
            updateRunStatus(Status.Complete);
            info("Import reads complete");
            setStatus(COMPLETE_STATUS);
        }
        catch (Exception e)
        {
            error("Import reads failed", e);
            setStatus(ERROR_STATUS);
        }
    }


    private void updateRunStatus(Status status) throws SQLException
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("Status", status.getStatusId());
        Table.update(getUser(), GenotypingSchema.get().getRunsTable(), map, _run.getRowId());
    }


    private void importReads() throws IOException, SQLException
    {
        info("Importing " + _reads.getName());
        setStatus("IMPORTING READS");

        TabLoader loader = null;
        DbScope scope = null;

        try
        {
            loader = new TabLoader(_reads, true);

            List<ColumnDescriptor> columns = new ArrayList<ColumnDescriptor>();
            columns.addAll(Arrays.asList(loader.getColumns()));

            for (ColumnDescriptor col : columns)
            {
                col.name = col.name.replace("read_", "");
                col.name = col.name.replace("_", "");
            }

            columns.add(new ColumnDescriptor("run", Integer.class, _run.getRowId()));
            loader.setColumns(columns.toArray(new ColumnDescriptor[columns.size()]));

            TableInfo readsTable = GenotypingSchema.get().getReadsTable();

            scope = readsTable.getSchema().getScope();
            scope.beginTransaction();

            int rowCount = 0;

            for (Map<String, Object> map : loader)
            {
                Integer mid = (Integer)map.get("mid");

                if (0 == mid)
                    map.put("mid", null);

                Table.insert(getUser(), readsTable, map);
                rowCount++;

                if (0 == rowCount % 10000)
                    logReadsProgress("", rowCount);
            }

            scope.commitTransaction();
            logReadsProgress("Importing " + _reads.getName() + " complete: ", rowCount);
            setStatus("UPDATING STATISTICS");
            info("Updating reads table statistics");
            readsTable.getSchema().getSqlDialect().updateStatistics(readsTable);
        }
        finally
        {
            if (null != scope)
                scope.closeConnection();
            if (null != loader)
                loader.close();
        }
    }


    private void logReadsProgress(String prefix, int count)
    {
        String formattedCount = Formats.commaf0.format(count);
        info(prefix + formattedCount + " reads imported");
        setStatus(formattedCount + " READS");    // Doesn't actually work... we're in one big transaction, so this doesn't update.
    }
}
