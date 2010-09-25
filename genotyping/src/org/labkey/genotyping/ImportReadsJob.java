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
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
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

// This job imports all the reads and the metrics for the run.  Once imported, users can (optionally) submit an analysis
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
        setLogFile(new File(_reads.getParent(), FileUtil.makeFileNameWithTimestamp("import_reads", "log")));
    }


    @Override
    public ActionURL getStatusHref()
    {
        return new ActionURL(GenotypingController.BeginAction.class, getInfo().getContainer());
    }


    @Override
    public String getDescription()
    {
        return "Import reads";
    }


    @Override
    public void run()
    {
        try
        {
            updateRunRecord();
            importReads();
            loadMetrics();
        }
        catch (Exception e)
        {
            error("Import reads and metrics failed", e);
            setStatus(ERROR_STATUS);
            return;
        }

        info("Import reads and metrics complete");
        setStatus(COMPLETE_STATUS);
    }

    private void loadMetrics()
    {
        // TODO
    }


    private void updateRunRecord() throws SQLException
    {
        info("Updating run information");

        if (false)
        {
        // TODO: add container filter
        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(getContainer());
        QueryHelper qHelper = new QueryHelper(getContainer(), getUser(), settings.getRunsQuery());
        TableInfo runs = qHelper.getTableInfo();
        @SuppressWarnings({"unchecked"}) Map<String, Object> map = Table.selectObject(runs, _run.getRun(), Map.class);
        Integer sampleLibrary = (Integer)map.get("run_sample_library");
        if (null == sampleLibrary)
            throw new NotFoundException("Sample library not specified");
//        _sampleLibrary = sampleLibrary;
        Map<String, Object> in = new HashMap<String, Object>();
        in.put("reads", _reads.getPath());
        // TODO  update sff and sequence view name
        }
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

            columns.add(new ColumnDescriptor("run", Integer.class, _run.getRun()));
            loader.setColumns(columns.toArray(new ColumnDescriptor[columns.size()]));

            TableInfo ti = GenotypingSchema.get().getReadsTable();

            // TODO: Just for testing
            Table.delete(ti, new SimpleFilter("Run", _run.getRun()));

            scope = ti.getSchema().getScope();
            scope.beginTransaction();

            int rowCount = 0;

            for (Map<String, Object> map : loader)
            {
                Table.insert(getUser(), ti, map);
                rowCount++;

                if (0 == rowCount % 10000)
                {
                    String formattedCount = Formats.commaf0.format(rowCount);
                    info(formattedCount + " reads imported");
                    setStatus(formattedCount + " READS");
                }
            }

            scope.commitTransaction();
        }
        finally
        {
            if (null != scope)
                scope.closeConnection();
            if (null != loader)
                loader.close();
        }
    }
}
