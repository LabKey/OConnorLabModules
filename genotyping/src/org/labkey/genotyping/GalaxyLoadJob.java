package org.labkey.genotyping;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableWriter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 20, 2010
 * Time: 12:11:53 PM
 */
public class GalaxyLoadJob extends PipelineJob
{
    private File _dir;
    private int _run;

    public GalaxyLoadJob(ViewBackgroundInfo info, PipeRoot root, File pipelineDir, int run)
    {
        super("Galaxy Load", info, root);
        _dir = pipelineDir;
        _run = run;
        setLogFile(new File(_dir, FileUtil.makeFileNameWithTimestamp("galaxy_load", "log")));

        if (!_dir.exists())
            throw new IllegalArgumentException("Pipeline directory does not exist: " + _dir.getAbsolutePath());

        if (0 == _run)
            throw new IllegalArgumentException("Run was not specified");
    }


    @Override
    public ActionURL getStatusHref()
    {
        return new ActionURL(GenotypingController.BeginAction.class, getInfo().getContainer());
    }


    @Override
    public String getDescription()
    {
        return "Galaxy load job";
    }


    @Override
    public void run()
    {
        long startTime = System.currentTimeMillis();

        try
        {
            // TODO: Container, Run

            File sourceSamples = new File(_dir, GenotypingManager.SAMPLES_FILE_NAME);
            File sourceMatches = new File(_dir, GenotypingManager.MATCHES_FILE_NAME);
            File sourceReads = new File(_dir, GenotypingManager.READS_FILE_NAME);

            DbSchema schema = GenotypingSchema.getInstance().getSchema();

            Table.TempTableInfo samples = null;
            Table.TempTableInfo matches = null;
            Table.TempTableInfo reads = null;

            try
            {
                ResultSet rs = null;

                try
                {
                    setStatus("LOADING TEMP TABLES");
                    info("Loading samples temp table");
                    samples = createTempTable(sourceSamples, schema, "mid_num,sample");
                    info("Loading matches temp table");
                    matches = createTempTable(sourceMatches, schema, null);
                    info("Loading reads temp table");
                    reads = createTempTable(sourceReads, schema, null);

                    QueryContext ctx = new QueryContext(schema, samples, matches, reads);
                    JspTemplate<QueryContext> jspQuery = new JspTemplate<QueryContext>("/org/labkey/galaxy/view/mhcQuery.jsp", ctx);
                    String sql = jspQuery.render();

                    // TODO
                    Table.execute(schema, "DELETE FROM galaxy.Junction", null);
                    Table.execute(schema, "DELETE FROM galaxy.Matches", null);
                    Table.execute(schema, "DELETE FROM galaxy.Alleles", null);

                    Map<String, Object> singleAllele = new HashMap<String, Object>();   // Map to reuse for each insertion to Alleles
                    Map<String, Object> singleJunction = new HashMap<String, Object>(); // Map to reuse for each insertion to Junction
                    Map<String, Integer> alleleToId = new HashMap<String, Integer>();

                    setStatus("IMPORTING RESULTS");
                    info("Executing query to join results");
                    rs = Table.executeQuery(schema, sql, null);

                    info("Importing results");

                    while (rs.next())
                    {
                        String sampleId = rs.getString("sample");

                        if (null != sampleId)
                        {
                            String allelesString = rs.getString("alleles");
                            Map<String, Object> row = new HashMap<String, Object>();
                            row.put("SampleId", sampleId);
                            row.put("Reads", rs.getInt("reads"));
                            row.put("Percent", rs.getFloat("percent"));
                            row.put("AverageLength", rs.getFloat("avg_length"));
                            row.put("PosReads", rs.getInt("pos_reads"));
                            row.put("NegReads", rs.getInt("neg_reads"));
                            row.put("PosExtReads", rs.getInt("pos_ext_reads"));
                            row.put("NegExtReads", rs.getInt("neg_ext_reads"));

                            Map<String, Object> matchOut = Table.insert(getUser(), GenotypingSchema.getInstance().getMatchesTable(), row);

                            String[] alleles = allelesString.split(",");

                            if (alleles.length > 0)
                            {
                                singleJunction.put("RowId", matchOut.get("RowId"));

                                for (String allele : alleles)
                                {
                                    Integer alleleId = alleleToId.get(allele);

                                    if (null == alleleId)
                                    {
                                        singleAllele.put("allele", allele);
                                        Map<String, Object> alleleOut = Table.insert(getUser(), GenotypingSchema.getInstance().getAllelesTable(), singleAllele);
                                        alleleId = (Integer)alleleOut.get("RowId");
                                        alleleToId.put(allele, alleleId);
                                    }

                                    singleJunction.put("AlleleId", alleleId);
                                    Table.insert(getUser(), GenotypingSchema.getInstance().getJunctionTable(), singleJunction);
                                }
                            }
                        }
                    }
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }

                setStatus("IMPORTING UNMATCHED SEQUENCES");
                info("Importing unmatched sequences");
                SQLFragment sql = new SQLFragment("INSERT INTO ");
                sql.append(GenotypingSchema.getInstance().getReadsTable());
                sql.append(" SELECT mid, sequence FROM ");
                sql.append(reads);
                sql.append(" reads LEFT OUTER JOIN ");
                sql.append(matches);
                sql.append(" matches ON reads.read_name = matches.read_name WHERE matches.read_name IS NULL");

                int count = Table.execute(schema, sql);
                info("Saved " + count + " unknown sequences");
            }
            finally
            {
                info("Deleting temporary tables");

                // Dump all the temp tables
                if (null != samples)
                    samples.delete();
                if (null != matches)
                    matches.delete();
                if (null != reads)
                    reads.delete();
            }
        }
        catch (Exception e)
        {
            error("Galaxy load failed", e);
            setStatus(ERROR_STATUS);
            return;
        }

        setStatus(COMPLETE_STATUS);
        info("Successfully loaded genotyping matching data in " + DateUtil.formatDuration(System.currentTimeMillis() - startTime));
    }


    // columnNames: comma-separated list of column names to include; null means include all columns
    private Table.TempTableInfo createTempTable(File file, DbSchema schema, @Nullable String columnNames) throws IOException, SQLException
    {
        Reader isr = new BufferedReader(new FileReader(file));
        TabLoader loader = new TabLoader(isr, true);         // TODO: Constructor that takes an inputstream

        // Load only the specified columns
        if (null != columnNames)
        {
            Set<String> includeNames = PageFlowUtil.set(columnNames.split(","));

            for (ColumnDescriptor descriptor : loader.getColumns())
                descriptor.load = includeNames.contains(descriptor.name);
        }

        TempTableWriter ttw = new TempTableWriter(loader);
        return ttw.loadTempTable(schema);
    }

    public static class QueryContext
    {
        public final DbSchema schema;
        public final TableInfo samples;
        public final TableInfo matches;
        public final TableInfo reads;

        private QueryContext(DbSchema schema, TableInfo samples, TableInfo matches, TableInfo reads)
        {
            this.schema = schema;
            this.samples = samples;
            this.matches = matches;
            this.reads = reads;
        }
    }
}
