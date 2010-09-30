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

import org.apache.log4j.Logger;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.genotyping.galaxy.GalaxyFolderSettings;
import org.labkey.genotyping.galaxy.GalaxyManager;
import org.labkey.genotyping.galaxy.GalaxyUserSettings;
import org.labkey.genotyping.sequences.SequenceManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class GenotypingController extends SpringActionController
{
    private static final Logger LOG = Logger.getLogger(GenotypingController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(GenotypingController.class);

    public GenotypingController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            return getViewURL();
        }
    }


    private ActionURL getViewURL()
    {
        return new ActionURL(ViewAction.class, getContainer());
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ViewAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DataRegion dr = getDataRegion();
            GridView grid = new GridView(dr, new RenderContext(getViewContext()));
            HtmlView links = new HtmlView(PageFlowUtil.textLink("export results", new ActionURL(TsvAction.class, getContainer())) + " " +
                                          PageFlowUtil.textLink("admin", getAdminURL()) + " " +
                                          PageFlowUtil.textLink("my settings", getMySettingsURL()));

            return new VBox(links, grid);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private DataRegion getDataRegion()
    {
        GenotypingSchema gt = GenotypingSchema.get();
        TableInfo tinfoMatches = gt.getMatchesTable();
        List<ColumnInfo> columns = new ArrayList<ColumnInfo>();
        columns.addAll(tinfoMatches.getColumns("SampleId,Reads,Percent,AverageLength,PosReads,NegReads,PosExtReads,NegExtReads"));

        ColumnInfo alleles = tinfoMatches.getColumn("Alleles");
        ColumnInfo allele_rowid = alleles.getFk().createLookupColumn(alleles, "RowId");
        columns.add(allele_rowid);
        ColumnInfo allele = alleles.getFk().createLookupColumn(alleles, "AlleleName");
        columns.add(allele);

        // TODO: move to XML?
        allele.setURL(new DetailsURL(getViewURL(), "id", FieldKey.fromParts("Alleles", "RowId")));

        DataRegion dr = new DataRegion();
        dr.setShadeAlternatingRows(true);
        dr.setColumns(columns);

        dr.getDisplayColumn(8).setVisible(false);

        return dr;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class TsvAction extends ExportAction<Object>
    {
        @Override
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            DataRegion dr = getDataRegion();
            RenderContext ctx = new RenderContext(getViewContext());
            ResultSet rs = dr.getResultSet(ctx);
            List<DisplayColumn> cols = dr.getDisplayColumns();
            TSVGridWriter tsv = new TSVGridWriter(rs, ctx.getFieldMap(), cols);

            tsv.write(response);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ViewTestAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DbSchema schema = DbSchema.get("mvc");
            TableInfo tinfoAdults = schema.getTable("Adults");
            VBox vbox = new VBox();

            {
                TableInfo tinfoChildren = schema.getTable("Children");
                ColumnInfo multiValuedColumn = null;//new MultiValuedColumn("Children", tinfoAdults.getColumn("RowId"), tinfoChildren.getColumn("AdultId"), tinfoChildren.getColumn("Name"));
                List<ColumnInfo> columns = new ArrayList<ColumnInfo>(tinfoAdults.getColumns());
                columns.add(multiValuedColumn);
                DataRegion dr = new DataRegion();
                dr.setColumns(columns);
                //vbox.addView(new GridView(dr, new RenderContext(getViewContext())));
            }

            {
                TableInfo tinfoJunction = schema.getTable("Junction");
                TableInfo tinfoHobbies = schema.getTable("Hobbies");
                LookupColumn lookup = new LookupColumn(tinfoJunction.getColumn("HobbyId"), tinfoHobbies.getColumn("RowId"), tinfoHobbies.getColumn("Name"));
                ColumnInfo multiValuedColumn = null; //new MultiValuedColumn("Hobbies", tinfoAdults.getColumn("RowId"), tinfoJunction.getColumn("AdultId"), lookup);
                List<ColumnInfo> columns = new ArrayList<ColumnInfo>(tinfoAdults.getColumns());
                columns.add(multiValuedColumn);
                DataRegion dr = new DataRegion();
                dr.setColumns(columns);
                vbox.addView(new GridView(dr, new RenderContext(getViewContext())));
            }

            return vbox;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class LoadSequencesAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            long startTime = System.currentTimeMillis();
            SequenceManager.get().loadSequences(getContainer(), getUser());
            LOG.info(DateUtil.formatDuration(System.currentTimeMillis() - startTime) + " to load sequences");

            return getViewURL();
        }
    }


    public static class AdminForm extends ReturnUrlForm implements GenotypingFolderSettings, GalaxyFolderSettings, HasViewContext
    {
        private String _galaxyURL;
        private String _sequencesQuery;
        private String _runsQuery;
        private String _samplesQuery;

        @Override
        public void setViewContext(ViewContext context)
        {
            Container c = context.getContainer();

            GenotypingFolderSettings genotypingSettings = GenotypingManager.get().getSettings(c);
            _sequencesQuery = genotypingSettings.getSequencesQuery();
            _runsQuery = genotypingSettings.getRunsQuery();
            _samplesQuery = genotypingSettings.getSamplesQuery();

            GalaxyFolderSettings galaxySettings = GalaxyManager.get().getSettings(c);
            _galaxyURL = galaxySettings.getGalaxyURL();
        }

        @Override
        public ViewContext getViewContext()
        {
            throw new IllegalStateException();
        }

        @Override
        public String getGalaxyURL()
        {
            return _galaxyURL;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setGalaxyURL(String galaxyURL)
        {
            _galaxyURL = galaxyURL;
        }

        @Override
        public String getSequencesQuery()
        {
            return _sequencesQuery;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSequencesQuery(String sequencesQuery)
        {
            _sequencesQuery = sequencesQuery;
        }

        public String getRunsQuery()
        {
            return _runsQuery;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRunsQuery(String runsQuery)
        {
            _runsQuery = runsQuery;
        }

        @Override
        public String getSamplesQuery()
        {
            return _samplesQuery;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSamplesQuery(String samplesQuery)
        {
            _samplesQuery = samplesQuery;
        }
    }


    private ActionURL getAdminURL()
    {
        ActionURL url = new ActionURL(AdminAction.class, getContainer());
        url.addReturnURL(getViewContext().getActionURL());
        return url;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class AdminAction extends FormViewAction<AdminForm>
    {
        @Override
        public void validateCommand(AdminForm form, Errors errors)
        {
            String galaxyUrl = form.getGalaxyURL();

            if (null == galaxyUrl)
            {
                errors.reject(ERROR_MSG, "Please specify the URL of your Galaxy server home page, e.g., http://galaxy.test.com");
            }
            else
            {
                try
                {
                    new URL(galaxyUrl);
                }
                catch (MalformedURLException e)
                {
                    errors.reject(ERROR_MSG, "Invalid Galaxy URL");
                }
            }
        }

        @Override
        public ModelAndView getView(AdminForm form, boolean reshow, BindException errors) throws Exception
        {
            GenotypingFolderSettings currentSettings = GenotypingManager.get().getSettings(getContainer());
            VBox vbox = new VBox();

            if (null != currentSettings.getSequencesQuery())
            {
                WebPartView loadSequences = new JspView("/org/labkey/genotyping/view/loadSequences.jsp");
                loadSequences.setTitle("Load Reference Sequences");
                vbox.addView(loadSequences);
            }

            WebPartView configure = new JspView<AdminForm>("/org/labkey/genotyping/view/admin.jsp", form, errors);
            configure.setTitle("Configuration");
            vbox.addView(configure);

            return vbox;
        }

        @Override
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            // Save both the genotyping settings and Galaxy configuration settigns
            GenotypingManager.get().saveSettings(getContainer(), form);
            GalaxyManager.get().saveSettings(getContainer(), form);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(AdminForm form)
        {
            return form.getReturnURLHelper();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Genotyping Admin");
        }
    }


    public static class MySettingsForm extends ReturnUrlForm implements GalaxyUserSettings, HasViewContext
    {
        private String _galaxyKey;

        @Override
        public void setViewContext(ViewContext context)
        {
            Container c = context.getContainer();
            User user = context.getUser();
            GalaxyUserSettings settings = GalaxyManager.get().getUserSettings(c, user);
            _galaxyKey = settings.getGalaxyKey();
        }

        @Override
        public ViewContext getViewContext()
        {
            throw new IllegalStateException();
        }

        @Override
        public String getGalaxyKey()
        {
            return _galaxyKey;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setGalaxyKey(String galaxyKey)
        {
            _galaxyKey = galaxyKey;
        }
    }


    private ActionURL getMySettingsURL()
    {
        ActionURL url = new ActionURL(MySettingsAction.class, getContainer());
        url.addReturnURL(getViewContext().getActionURL());
        return url;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class MySettingsAction extends FormViewAction<MySettingsForm>
    {
        @Override
        public void validateCommand(MySettingsForm form, Errors errors)
        {
            String key = form.getGalaxyKey();

            if (null == key)
            {
                errors.reject(ERROR_MSG, "Please provide a Galaxy web API key. To generate this, log into your Galaxy server and visit User -> Preferences -> Manage your information.");
            }
            else
            {
                String advice = " Please copy the web API key from your Galaxy server account (User -> Preferences -> Manage your information) and paste it below.";

                if (key.length() != 32)
                {
                    errors.reject(ERROR_MSG, "Galaxy web API key is the wrong length." + advice);
                }
                else
                {
                    boolean success = false;

                    try
                    {
                        BigInteger bi = new BigInteger(key, 16);
                        String hex = bi.toString(16);

                        if (hex.equalsIgnoreCase(key))
                            success = true;
                    }
                    catch (NumberFormatException e)
                    {
                        // Error below
                    }

                    if (!success)
                        errors.reject(ERROR_MSG, "Galaxy web API key is not valid hexidecimal." + advice);
                }
            }
        }

        @Override
        public ModelAndView getView(MySettingsForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<MySettingsForm>("/org/labkey/genotyping/view/mySettings.jsp", form, errors);
        }

        @Override
        public boolean handlePost(MySettingsForm form, BindException errors) throws Exception
        {
            GalaxyManager.get().saveUserSettings(getContainer(), getUser(), form);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(MySettingsForm form)
        {
            return form.getReturnURLHelper();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("My Galaxy Settings");
        }
    }


    public static class SubmitAnalysisForm extends PipelinePathForm
    {
        private boolean _readyToSubmit = false;
        private String[] _sequencesViews;
        private String[] _descriptions;
        private String _readsPath;
        private Integer _run;

        public boolean isReadyToSubmit()
        {
            return _readyToSubmit;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setReadyToSubmit(boolean readyToSubmit)
        {
            _readyToSubmit = readyToSubmit;
        }

        public String[] getSequencesViews()
        {
            return _sequencesViews;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSequencesViews(String[] sequencesViews)
        {
            _sequencesViews = sequencesViews;
        }

        public String[] getDescriptions()
        {
            return _descriptions;
        }

        public void setDescriptions(String[] descriptions)
        {
            _descriptions = descriptions;
        }

        public String getReadsPath()
        {
            return _readsPath;
        }

        public void setReadsPath(String readsPath)
        {
            _readsPath = readsPath;
        }

        public Integer getRun()
        {
            return _run;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRun(Integer run)
        {
            _run = run;
        }
    }


    public static class SubmitAnalysisBean
    {
        private List<Integer> _runs;
        private Collection<CustomView> _sequencesViews;
        private String _readsPath;

        private SubmitAnalysisBean(List<Integer> runs, Collection<CustomView> sequenceViews, String readsPath)
        {
            _runs = runs;
            _sequencesViews = sequenceViews;
            _readsPath = readsPath;
        }

        public List<Integer> getRuns()
        {
            return _runs;
        }

        public Collection<CustomView> getSequencesViews()
        {
            return _sequencesViews;
        }

        public String getReadsPath()
        {
            return _readsPath;
        }
    }


    // NULL view name means use default view; need a placeholder string to display and post from the form
    public static final String DEFAULT_VIEW_PLACEHOLDER = "[default]";

    @RequiresPermissionClass(AdminPermission.class)
    public class ImportReadsAction extends FormViewAction<SubmitAnalysisForm>
    {
        @Override
        public void validateCommand(SubmitAnalysisForm form, Errors errors)
        {
            if (form.isReadyToSubmit())
            {
                if (null == form.getRun())
                    errors.reject(ERROR_MSG, "You must select a run number");
            }
        }

        @Override
        public ModelAndView getView(SubmitAnalysisForm form, boolean reshow, BindException errors) throws Exception
        {
            GenotypingFolderSettings settings = GenotypingManager.get().getSettings(getContainer());
            TableInfo runs = new QueryHelper(getContainer(), getUser(), settings.getRunsQuery()).getTableInfo();
            List<Integer> runNums = Arrays.asList(Table.executeArray(runs, "run_num", null, new Sort("-run_num"), Integer.class));

            Set<CustomView> views = new TreeSet<CustomView>(new Comparator<CustomView>() {
                    @Override
                    public int compare(CustomView c1, CustomView c2)
                    {
                        String name1 = c1.getName();
                        String name2 = c2.getName();

                        return (null == name1 ? DEFAULT_VIEW_PLACEHOLDER : name1).compareTo((null == name2 ? DEFAULT_VIEW_PLACEHOLDER : name2));
                    }
                });
            views.addAll(QueryService.get().getCustomViews(getUser(), getContainer(), "sequences", "sequences"));

            return new JspView<SubmitAnalysisBean>("/org/labkey/genotyping/view/submit.jsp", new SubmitAnalysisBean(runNums, views, form.getReadsPath()), errors);
        }

        @Override
        public boolean handlePost(SubmitAnalysisForm form, BindException errors) throws Exception
        {
            if (!form.isReadyToSubmit())
            {
                File readsFile = form.getValidatedSingleFile(getContainer());
                form.setReadsPath(readsFile.getPath());
                return false;
            }

            GenotypingRun run = GenotypingManager.get().getRun(getContainer(), getUser(), form.getRun());

            // TODO: Just for testing -- delete all previous analyses, reads, matches, etc. associated with this run.
            GenotypingManager.get().clearRun(run);

            ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL());
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            PipelineJob prepareRunJob = new ImportReadsJob(vbi, root, new File(form.getReadsPath()), run);
            PipelineService.get().queueJob(prepareRunJob);

            String[] sequencesViews = form.getSequencesViews();
            String[] descriptions = form.getDescriptions();

            for (int i = 0; null != sequencesViews && i < sequencesViews.length; i++)
            {
                String sequencesView = DEFAULT_VIEW_PLACEHOLDER.equals(sequencesViews[i]) ? null : sequencesViews[i];
                GenotypingAnalysis analysis = GenotypingManager.get().createAnalysis(getContainer(), getUser(), run, null == descriptions ? null : descriptions[i], sequencesView);
                PipelineJob analysisJob = new SubmitAnalysisJob(vbi, root, new File(form.getReadsPath()), run, analysis);
                PipelineService.get().queueJob(analysisJob);
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(SubmitAnalysisForm form)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Import Reads and Metrics");
        }
    }


    public static ActionURL getWorkflowCompleteURL(Container c, GenotypingAnalysis analysis)
    {
        ActionURL url = new ActionURL(WorkflowCompleteAction.class, c);
        url.addParameter("analysis", analysis.getRowId());
        url.addParameter("path", analysis.getPath());
        return url;
    }


    @RequiresNoPermission
    public class WorkflowCompleteAction extends SimpleViewAction<AnalysisForm>
    {
        @Override
        public ModelAndView getView(AnalysisForm form, BindException errors) throws Exception
        {
            LOG.info("Galaxy claims to be complete with analysis " + form.getAnalysis());

            int analysisId = form.getAnalysis();
            File analysisDir = new File(form.getPath());

            User user = getUser();

            if (user.isGuest())
            {
                Properties props = GenotypingManager.get().readProperties(analysisDir);
                String email = (String)props.get("user");

                if (null != email)
                {
                    // Possible that user doesn't exist or changed email (e.g., re-loading an old analysis)
                    User test = UserManager.getUser(new ValidEmail(email));

                    if (null != test)
                        user = test;
                }
            }

            importAnalysis(analysisId, analysisDir, user);

            // Plain text response back to Galaxy
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("text/plain");
            PrintWriter out = response.getWriter();
            out.print("Import analysis job has been queued");
            out.close();
            response.flushBuffer();

            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class AnalysisForm
    {
        private int _analysis;
        private String _path;

        public int getAnalysis()
        {
            return _analysis;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalysis(int analysis)
        {
            _analysis = analysis;
        }

        public String getPath()
        {
            return _path;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPath(String path)
        {
            _path = path;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class LoadAction extends SimpleRedirectAction<PipelinePathForm>
    {
        @Override
        public ActionURL getRedirectURL(PipelinePathForm form) throws Exception
        {
            // Manual upload of results; pipeline provider posts to this action with matches file.
            File matches = form.getValidatedSingleFile(getContainer());
            File analysisDir = matches.getParentFile();

            // Load properties to determine the run.
            Properties props = GenotypingManager.get().readProperties(analysisDir);

            Integer analysisId = Integer.parseInt((String)props.get("analysis"));
            importAnalysis(analysisId, analysisDir, getUser());

            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }


    // TODO: Verify that file path and analysis # match -- just look in properties file?
    // TODO: synchronously verify that analysis hasn't already been loaded 

    private void importAnalysis(int analysisId, File pipelineDir, User user) throws IOException
    {
        GenotypingAnalysis analysis = GenotypingManager.get().getAnalysis(getContainer(), analysisId);
        ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), user, getViewContext().getActionURL());
        PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
        PipelineJob job = new ImportAnalysisJob(vbi, root, pipelineDir, analysis);
        PipelineService.get().queueJob(job);
    }
}