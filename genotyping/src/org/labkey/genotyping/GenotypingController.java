/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.QueryViewAction.QueryExportForm;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.PanelButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.genotyping.GenotypingQuerySchema.TableType;
import org.labkey.genotyping.galaxy.GalaxyFolderSettings;
import org.labkey.genotyping.galaxy.GalaxyManager;
import org.labkey.genotyping.galaxy.GalaxyUserSettings;
import org.labkey.genotyping.sequences.FastqGenerator;
import org.labkey.genotyping.sequences.FastqWriter;
import org.labkey.genotyping.sequences.SequenceManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
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
            return getRunsURL(getContainer());
        }
    }


    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic(new HelpTopic("genotyping"));
        return config;
    }


    public static ActionURL getAnalysisURL(Container c, int analysisId)
    {
        ActionURL url = new ActionURL(AnalysisAction.class, c);
        url.addParameter("analysis", analysisId);
        return url;
    }


    public static class AnalysisForm extends QueryExportForm
    {
        private Integer _analysis = null;
        private boolean _combine = false;
        private Integer _highlightId = null;

        public Integer getAnalysis()
        {
            return _analysis;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalysis(Integer analysis)
        {
            _analysis = analysis;
        }

        public boolean getCombine()
        {
            return _combine;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCombine(boolean combine)
        {
            _combine = combine;
        }

        public Integer getHighlightId()
        {
            return _highlightId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setHighlightId(Integer highlightId)
        {
            _highlightId = highlightId;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class AnalysisAction extends QueryViewAction<AnalysisForm, QueryView>
    {
        private GenotypingAnalysis _analysis = null;

        public AnalysisAction()
        {
            super(AnalysisForm.class);
        }

        @Override
        public ModelAndView getView(AnalysisForm form, BindException errors) throws Exception
        {
            // Now that the form has populated "highlightId", eliminate this parameter from the current ActionURL.  We don't want
            // links to propagate it.
            ActionURL currentURL = getViewContext().cloneActionURL().deleteParameter("highlightId");
            currentURL.setReadOnly();
            getViewContext().setActionURL(currentURL);

            ModelAndView qv = super.getView(form, errors);

            if (form.getCombine() && !form.isExport())
            {
                return new VBox(qv, new JspView("/org/labkey/genotyping/view/matchCombiner.jsp"));
            }
            else
            {
                return qv;
            }
        }

        @Override
        protected QueryView createQueryView(AnalysisForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            _analysis = GenotypingManager.get().getAnalysis(getContainer(), form.getAnalysis());

            QuerySettings settings = new QuerySettings(getViewContext(), "Analysis", TableType.Matches.toString());
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(true);
            settings.setBaseSort(new Sort("SampleId/library_sample_name,Alleles/AlleleName"));
            settings.getBaseFilter().addCondition("Analysis", form.getAnalysis());

            UserSchema gqs = new GenotypingQuerySchema(getUser(), getContainer());
            QueryView qv;

            // If the user doesn't have update permissions then just provide a normal, read-only view.
            // Otherwise, either add the "Alter Matches" button or display that mode, depending on the value of the "combine" parameter. 
            if (!getContainer().hasPermission(getUser(), UpdatePermission.class))
            {
                qv = new QueryView(gqs, settings, errors);
            }
            else
            {
                final ActionURL url = getViewContext().cloneActionURL();

                if (!form.getCombine())
                {
                    url.replaceParameter("combine", "1");

                    qv = new QueryView(gqs, settings, errors) {
                         @Override
                         protected void populateButtonBar(DataView view, ButtonBar bar)
                         {
                             super.populateButtonBar(view, bar);

                             ActionButton combineModeButton = new ActionButton(url, "Alter Matches");
                             bar.add(combineModeButton);
                         }
                    };
                }
                else
                {
                    url.deleteParameter("combine");
                    final Integer highlightId = form.getHighlightId();

                    qv = new QueryView(gqs, settings, errors) {
                        @Override
                        protected void populateButtonBar(DataView view, ButtonBar bar)
                        {
                            super.populateButtonBar(view, bar);

                            ActionButton combineModeButton = new ActionButton(url, "Stop Altering Matches");
                            bar.add(combineModeButton);

                            ActionButton combineButton = new ActionButton(CombineMatchesAction.class, "Combine");
                            combineButton.setRequiresSelection(true);
                            combineButton.setScript("combine(" + _analysis.getRowId() + ");return false;");
                            bar.add(combineButton);
                        }

                        @Override
                        protected DataRegion createDataRegion()
                        {
                            // Override to highlight the just-added match
                            DataRegion rgn = new DataRegion() {
                                @Override
                                protected String getRowClass(RenderContext ctx, int rowIndex)
                                {
                                    if (highlightId != null && highlightId.equals(ctx.get("RowId")))
                                        return "labkey-error-row";

                                    return super.getRowClass(ctx, rowIndex);
                                }
                            };
                            configureDataRegion(rgn);
                            return rgn;
                        }
                    };

                    qv.setShowRecordSelectors(true);
                }
            }

            qv.setShadeAlternatingRows(true);
            return qv;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Genotyping Analysis " + _analysis.getRowId());
        }
    }


    public static class CombineForm extends ReturnUrlForm
    {
        private int _analysis;
        private int[] _matchIds;
        private int[] _alleleIds;

        public int getAnalysis()
        {
            return _analysis;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalysis(int analysis)
        {
            _analysis = analysis;
        }

        public int[] getMatchIds()
        {
            return _matchIds;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMatchIds(int[] matchIds)
        {
            _matchIds = matchIds;
        }

        public int[] getAlleleIds()
        {
            return _alleleIds;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAlleleIds(int[] alleleIds)
        {
            _alleleIds = alleleIds;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class CombineMatchesAction extends RedirectAction<CombineForm>
    {
        private Integer _newId = null;

        @Override
        public URLHelper getSuccessURL(CombineForm form)
        {
            ActionURL url = form.getReturnActionURL();

            if (null != url && null != _newId)
                url.replaceParameter("highlightId", String.valueOf(_newId));

            return url;
        }

        @Override
        public boolean doAction(CombineForm form, BindException errors) throws Exception
        {
            _newId = GenotypingManager.get().combineMatches(getContainer(), getUser(), form.getAnalysis(), form.getMatchIds(), form.getAlleleIds());
            return true;
        }

        @Override
        public void validateCommand(CombineForm form, Errors errors)
        {
        }
    }

/*
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
*/

    @RequiresPermissionClass(AdminPermission.class)
    public class LoadSequencesAction extends SimpleRedirectAction<ReturnUrlForm>
    {
        @Override
        public URLHelper getRedirectURL(ReturnUrlForm form) throws Exception
        {
            long startTime = System.currentTimeMillis();
            SequenceManager.get().loadSequences(getContainer(), getUser());
            LOG.info(DateUtil.formatDuration(System.currentTimeMillis() - startTime) + " to load sequences");

            return form.getReturnURLHelper(getPortalURL());
        }
    }


    private ActionURL getPortalURL()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
    }


    public static class AdminForm extends ReturnUrlForm implements GenotypingFolderSettings, GalaxyFolderSettings, HasViewContext
    {
        private String _galaxyURL;
        private String _sequencesQuery;
        private String _runsQuery;
        private String _samplesQuery;
        private String _message;

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

        public String getMessage()
        {
            return _message;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMessage(String message)
        {
            _message = message;
        }
    }


    private ActionURL getAdminURL(String message, ActionURL returnURL)
    {
        ActionURL url = getAdminURL(getContainer(), returnURL);
        url.addParameter("message", message);
        return url;
    }


    public static ActionURL getAdminURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(AdminAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class AdminAction extends FormViewAction<AdminForm>
    {
        @Override
        public void validateCommand(AdminForm form, Errors errors)
        {
            String galaxyUrl = form.getGalaxyURL();

            // Allow null, #11130
            if (null != galaxyUrl)
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
                WebPartView loadSequences = new JspView<ReturnUrlForm>("/org/labkey/genotyping/view/loadSequences.jsp", form);
                loadSequences.setTitle("Reference Sequences");
                vbox.addView(loadSequences);
            }

            WebPartView configure = new JspView<AdminForm>("/org/labkey/genotyping/view/configure.jsp", form, errors);
            configure.setTitle("Configuration");
            vbox.addView(configure);

            return vbox;
        }

        @Override
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            // Save both the genotyping settings and Galaxy configuration settings
            GenotypingManager.get().saveSettings(getContainer(), form);
            GalaxyManager.get().saveSettings(getContainer(), form);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(AdminForm form)
        {
            return getAdminURL("Settings have been saved", form.getReturnActionURL());
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


    public static ActionURL getMySettingsURL(Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(MySettingsAction.class, c);
        url.addReturnURL(returnURL);
        return url;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class MySettingsAction extends FormViewAction<MySettingsForm>
    {
        @Override
        public void validateCommand(MySettingsForm form, Errors errors)
        {
            String key = form.getGalaxyKey().trim();

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


    public static class ImportReadsForm extends PipelinePathForm
    {
        private String _readsPath;
        private Integer _run;
        private Integer _metaDataRun = null;
        private boolean _analyze = false;
        private boolean _pipeline = false;

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

        public Integer getMetaDataRun()
        {
            return null != _metaDataRun ? _metaDataRun : _run;
        }

        public void setMetaDataRun(Integer metaDataRun)
        {
            _metaDataRun = metaDataRun;
        }

        public boolean getAnalyze()
        {
            return _analyze;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalyze(boolean analyze)
        {
            _analyze = analyze;
        }

        public boolean getPipeline()
        {
            return _pipeline;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPipeline(boolean pipeline)
        {
            _pipeline = pipeline;
        }
    }


    public static class ImportReadsBean
    {
        private List<Integer> _runs;
        private String _readsPath;

        private ImportReadsBean(List<Integer> runs, String readsPath)
        {
            _runs = runs;
            _readsPath = readsPath;
        }

        public List<Integer> getRuns()
        {
            return _runs;
        }

        public String getReadsPath()
        {
            return _readsPath;
        }
    }


    // NULL view name means use default view; need a placeholder string to display and post from the form
    public static final String DEFAULT_VIEW_PLACEHOLDER = "[default]";

    @RequiresPermissionClass(InsertPermission.class)
    public class ImportReadsAction extends FormViewAction<ImportReadsForm>
    {
        private ActionURL _successURL = null;

        @Override
        public void validateCommand(ImportReadsForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ImportReadsForm form, boolean reshow, BindException errors) throws Exception
        {
            GenotypingFolderSettings settings = GenotypingManager.get().getSettings(getContainer());
            TableInfo runs = new QueryHelper(getContainer(), getUser(), settings.getRunsQuery()).getTableInfo();
            List<Integer> allRuns = new ArrayList<Integer>(Arrays.asList(Table.executeArray(runs, "run_num", null, new Sort("-run_num"), Integer.class)));
            allRuns.removeAll(Arrays.asList(Table.executeArray(GenotypingSchema.get().getRunsTable(), "MetaDataId", null, null, Integer.class)));

            return new JspView<ImportReadsBean>("/org/labkey/genotyping/view/importReads.jsp", new ImportReadsBean(allRuns, form.getReadsPath()), errors);
        }

        @Override
        public boolean handlePost(ImportReadsForm form, BindException errors) throws Exception
        {
            if (null == form.getReadsPath())
            {
                File readsFile = form.getValidatedSingleFile(getContainer());
                form.setReadsPath(readsFile.getPath());
                return false;
            }

            String error = importReads(form);

            if (form.getPipeline())
            {
                if (null != error)
                {
                    errors.reject(ERROR_MSG, error);
                    return false;
                }
            }
            else
            {
                // Send back plain text message to scripts, leaving _successURL null for no redirect
                sendPlainText(null != error ? error : "SUCCESS");
                return true;
            }

            // Successful submission via the UI... redirect either to the pipeline status grid or analyze action
            ActionURL pipelineURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
            _successURL = form.getAnalyze() ? getAnalyzeURL(form.getRun(), pipelineURL) : pipelineURL;

            return true;
        }

        private String importReads(ImportReadsForm form) throws Exception
        {
            if (null == form.getRun())
                return "You must specify a run number";

            try
            {
                File readsFile = new File(form.getReadsPath());
                GenotypingRun run;

                try
                {
                    run = GenotypingManager.get().createRun(getContainer(), getUser(), form.getRun(), form.getMetaDataRun(), readsFile);
                }
                catch (SQLException e)
                {
                    if (SqlDialect.isConstraintException(e))
                        return "Run " + form.getRun() + " has already been imported";
                    else
                        throw e;
                }

                ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL());
                PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                PipelineJob prepareRunJob = new ImportReadsJob(vbi, root, new File(form.getReadsPath()), run);
                PipelineService.get().queueJob(prepareRunJob);
            }
            catch (Exception e)
            {
                if (form.getPipeline())
                    throw e;

                return null != e.getMessage() ? e.getMessage() : e.getClass().getSimpleName();
            }

            return null;
        }

        @Override
        public URLHelper getSuccessURL(ImportReadsForm form)
        {
            return _successURL;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Import Reads and Metrics");
        }
    }


    public static class AnalyzeForm extends ReturnUrlForm
    {
        private int _run;
        private String _sequencesView;
        private String _description;
        private String _samples;

        public int getRun()
        {
            return _run;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRun(int run)
        {
            _run = run;
        }

        public String getSequencesView()
        {
            return _sequencesView;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSequencesView(String sequencesView)
        {
            _sequencesView = sequencesView;
        }

        public String getDescription()
        {
            return _description;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDescription(String description)
        {
            _description = description;
        }

        public String getSamples()
        {
            return _samples;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSamples(String samples)
        {
            _samples = samples;
        }
    }


    private ActionURL getAnalyzeURL(int runId, ActionURL cancelURL)
    {
        ActionURL url = new ActionURL(AnalyzeAction.class, getContainer());
        url.addParameter("run", runId);
        url.addReturnURL(cancelURL);
        return url;
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class AnalyzeAction extends FormViewAction<AnalyzeForm>
    {
        @Override
        public void validateCommand(AnalyzeForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(AnalyzeForm form, boolean reshow, BindException errors) throws Exception
        {
            GenotypingRun run = GenotypingManager.get().getRun(getContainer(), form.getRun());

            SortedSet<CustomView> views = new TreeSet<CustomView>(new Comparator<CustomView>() {
                    @Override
                    public int compare(CustomView c1, CustomView c2)
                    {
                        String name1 = c1.getName();
                        String name2 = c2.getName();

                        return (null == name1 ? DEFAULT_VIEW_PLACEHOLDER : name1).compareTo((null == name2 ? DEFAULT_VIEW_PLACEHOLDER : name2));
                    }
                });
            GenotypingSchema gs = GenotypingSchema.get();
            views.addAll(QueryService.get().getCustomViews(getUser(), getContainer(), gs.getSchemaName(), gs.getSequencesTable().getName()));

            Map<Integer, Pair<String, String>> sampleMap = new TreeMap<Integer, Pair<String, String>>();
            ResultSet rs = null;

            try
            {
                Results results = SampleManager.get().selectSamples(getContainer(), getUser(), run, "library_sample_name, library_sample_species, key");
                rs = results.getResultSet();
                Map<FieldKey, ColumnInfo> fieldMap = results.getFieldMap();

                while (null != rs && rs.next())
                {
                    String sampleName = (String)fieldMap.get(FieldKey.fromString("library_sample_name")).getValue(rs);
                    String species = (String)fieldMap.get(FieldKey.fromString("library_sample_species")).getValue(rs);
                    int sampleId = (Integer)fieldMap.get(FieldKey.fromString("key")).getValue(rs);
                    sampleMap.put(sampleId, new Pair<String, String>(sampleName, species));
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            return new JspView<AnalyzeBean>("/org/labkey/genotyping/view/analyze.jsp", new AnalyzeBean(views, sampleMap, form.getReturnActionURL()), errors);
        }

        @Override
        public boolean handlePost(AnalyzeForm form, BindException errors) throws Exception
        {
            GenotypingRun run = GenotypingManager.get().getRun(getContainer(), form.getRun());
            File readsPath = new File(run.getPath(), run.getFileName());
            ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL());
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());

            String sequencesViewName = form.getSequencesView();
            String description = form.getDescription();
            String sequencesView = DEFAULT_VIEW_PLACEHOLDER.equals(sequencesViewName) ? null : sequencesViewName;
            String samples = form.getSamples();
            Set<Integer> sampleKeys;
            String[] keys = samples.split(",");
            sampleKeys = new HashSet<Integer>(keys.length);

            for (String key : keys)
                sampleKeys.add(Integer.parseInt(key));

            GenotypingAnalysis analysis = GenotypingManager.get().createAnalysis(getContainer(), getUser(), run, null == description ? null : description, sequencesView);
            PipelineJob analysisJob = new SubmitAnalysisJob(vbi, root, readsPath, analysis, sampleKeys);
            PipelineService.get().queueJob(analysisJob);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(AnalyzeForm analyzeForm)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Submit Analysis");
        }
    }


    public static class AnalyzeBean
    {
        private final SortedSet<CustomView> _sequencesViews;
        private final Map<Integer, Pair<String, String>> _sampleMap;
        private final ActionURL _returnURL;

        private AnalyzeBean(SortedSet<CustomView> sequenceViews, Map<Integer, Pair<String, String>> sampleMap, ActionURL returnURL)
        {
            _sequencesViews = sequenceViews;
            _sampleMap = sampleMap;
            _returnURL = returnURL;
        }

        public SortedSet<CustomView> getSequencesViews()
        {
            return _sequencesViews;
        }

        public Map<Integer, Pair<String, String>> getSampleMap()
        {
            return _sampleMap;
        }

        public ActionURL getReturnURL()
        {
            return _returnURL;
        }
    }


    @SuppressWarnings({"UnusedDeclaration"})
    @RequiresPermissionClass(AdminPermission.class)
    public class TestPerformance extends SimpleRedirectAction
    {
        @Override
        public URLHelper getRedirectURL(Object o) throws Exception
        {
            ImportReadsAction action = new ImportReadsAction();
            action.setViewContext(getViewContext());
            TableInfo runsTable = GenotypingSchema.get().getRunsTable();
            Integer maxRun = Table.executeSingleton(runsTable.getSchema(), "SELECT CAST(MAX(RowId) AS INTEGER) FROM " + runsTable, null, Integer.class);

            int start = maxRun + 1;
            int end = start + 10;

            for (int i = start; i < end; i++)
            {
                ImportReadsForm form = new ImportReadsForm();
                form.setRun(i);
                form.setMetaDataRun(113);
                form.setReadsPath("c:\\Users\\adam\\Desktop\\genotyping\\runs\\2010-10-20\\reads.txt");

                action.handlePost(form, null);
            }

            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }


    public static ActionURL getWorkflowCompleteURL(Container c, GenotypingAnalysis analysis)
    {
        ActionURL url = new ActionURL(WorkflowCompleteAction.class, c);
        url.addParameter("analysis", analysis.getRowId());
        url.addParameter("path", analysis.getPath());
        return url;
    }


    private static String FAILURE_PREFACE = "Failed to queue import analysis job: ";

    @RequiresNoPermission
    public class WorkflowCompleteAction extends SimpleViewAction<ImportAnalysisForm>
    {
        @Override
        public ModelAndView getView(ImportAnalysisForm form, BindException errors) throws Exception
        {
            LOG.info("Galaxy signaled the completion of analysis " + form.getAnalysis());
            String message;

            // Send any exceptions back to the Galaxy task so it can log it as well.
            try
            {
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
                message = "Import analysis job queued at " + new Date();
            }
            catch (FileNotFoundException fnf)
            {
                // Send back a vague, generic message in the case of all file-not-found-type problems, e.g., specified path is
                // missing, isn't a directory, lacks a properties.xml file, or doesn't match the analysis table path. This
                // prevents attackers from gaining any useful information about the file system.  Log the more detailed message.
                message = FAILURE_PREFACE + "Analysis path doesn't match import path (see system log for more details)";
                LOG.error(FAILURE_PREFACE + fnf.getMessage());
            }
            catch (Exception e)
            {
                message = FAILURE_PREFACE + e.getMessage();
                LOG.error(message);
            }

            // Plain text response back to Galaxy
            sendPlainText(message);

            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private void sendPlainText(String message) throws IOException
    {
        HttpServletResponse response = getViewContext().getResponse();
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.print(message);
        out.close();
        response.flushBuffer();
    }


    public static class ImportAnalysisForm
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


    @RequiresPermissionClass(InsertPermission.class)
    public class ImportAnalysisAction extends SimpleRedirectAction<PipelinePathForm>
    {
        @Override
        public ActionURL getRedirectURL(PipelinePathForm form) throws Exception
        {
            // Manual upload of genotyping analysis; pipeline provider posts to this action with matches file.
            File matches = form.getValidatedSingleFile(getContainer());
            File analysisDir = matches.getParentFile();

            // Load properties to determine the run.
            Properties props = GenotypingManager.get().readProperties(analysisDir);

            Integer analysisId = Integer.parseInt((String)props.get("analysis"));
            importAnalysis(analysisId, analysisDir, getUser());

            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }


    private void importAnalysis(int analysisId, File pipelineDir, User user) throws IOException, SQLException
    {
        GenotypingAnalysis analysis = GenotypingManager.get().getAnalysis(getContainer(), analysisId);
        File analysisDir = new File(analysis.getPath());

        if (!pipelineDir.equals(analysisDir))
            throw new FileNotFoundException("Analysis path (\"" + analysisDir.getAbsolutePath() +
                    "\") doesn't match specified path (\"" + pipelineDir.getAbsolutePath() + "\")");

        boolean success = GenotypingManager.get().updateAnalysisStatus(analysis, user, Status.Submitted, Status.Importing);

        if (success)
        {
            ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), user, getViewContext().getActionURL());
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            PipelineJob job = new ImportAnalysisJob(vbi, root, pipelineDir, analysis);
            PipelineService.get().queueJob(job);
        }
        else
        {
            throw new IllegalStateException("Current analysis status is not \"Submitted\"");
        }
    }


    public static class SequencesForm extends QueryExportForm
    {
        private Integer _dictionary = null;

        @Nullable
        public Integer getDictionary()
        {
            return _dictionary;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDictionary(Integer dictionary)
        {
            _dictionary = dictionary;
        }
    }


    public static ActionURL getSequencesURL(Container c, @Nullable Integer dictionary)
    {
        ActionURL url =  new ActionURL(SequencesAction.class, c);

        if (null != dictionary)
            url.addParameter("dictionary", dictionary);

        return url;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class SequencesAction extends QueryViewAction<SequencesForm, QueryView>
    {
        public SequencesAction()
        {
            super(SequencesForm.class);
        }

        @Override
        protected QueryView createQueryView(SequencesForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings settings = new QuerySettings(getViewContext(), "Sequences", TableType.Sequences.toString());
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("RowId");
            Integer dictionary = form.getDictionary();
            settings.getBaseFilter().addCondition("Dictionary", null != dictionary ? dictionary : SequenceManager.get().getCurrentDictionary(getContainer()).getRowId());

            QueryView qv = new QueryView(new GenotypingQuerySchema(getUser(), getContainer()), settings, errors);
            qv.setShadeAlternatingRows(true);

            return qv;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Reference Sequences");
        }
    }


    public static class SequenceForm extends SequencesForm
    {
        private Integer _analysis = null;
        private Integer _sequence = null;

        public Integer getSequence()
        {
            return _sequence;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSequence(Integer sequence)
        {
            _sequence = sequence;
        }

        @Nullable
        public Integer getAnalysis()
        {
            return _analysis;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalysis(Integer analysis)
        {
            _analysis = analysis;
        }
    }


    @SuppressWarnings({"UnusedDeclaration"})  // URL defined on sequences.rowId column in genotyping.xml
    @RequiresPermissionClass(ReadPermission.class)
    public class SequenceAction extends SimpleViewAction<SequenceForm>
    {
        public SequenceAction()
        {
            super(SequenceForm.class);
        }

        @Override
        public ModelAndView getView(SequenceForm form, BindException errors) throws Exception
        {
            QuerySettings settings = new QuerySettings(getViewContext(), "Sequence", TableType.Sequences.toString());

            // Adding the dictionary ensures we're grabbing a sequence from this container
            Integer dictionary = form.getDictionary();
            settings.getBaseFilter().addCondition("Dictionary", null != dictionary ? dictionary : SequenceManager.get().getCurrentDictionary(getContainer()).getRowId());
            QueryView qv = new QueryView(new GenotypingQuerySchema(getUser(), getContainer()), settings, errors);

            DataRegion rgn = new DataRegion();
            rgn.setDisplayColumns(qv.getDisplayColumns());
            DetailsView view = new DetailsView(rgn, form.getSequence());

            ButtonBar bb = new ButtonBar();
            Integer analysisId = form.getAnalysis();

            if (null != analysisId)
                bb.getList().add(new ActionButton("Analysis", getAnalysisURL(getContainer(), analysisId)));
            else
                bb.getList().add(new ActionButton("Reference Sequences", getSequencesURL(getContainer(), dictionary)));

            rgn.setButtonBar(bb, DataRegion.MODE_DETAILS);

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Reference Sequence");
        }
    }


    public static ActionURL getRunsURL(Container c)
    {
        return new ActionURL(RunsAction.class, c);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class RunsAction extends QueryViewAction<QueryExportForm, QueryView>
    {
        public RunsAction()
        {
            super(QueryExportForm.class);
        }

        @Override
        protected QueryView createQueryView(QueryExportForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            boolean isAdmin = getContainer().hasPermission(getUser(), AdminPermission.class);
            return new GenotypingRunsView(getViewContext(), errors, "Runs", isAdmin);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Sequencing Runs");
        }
    }


    public static ActionURL getAnalysesURL(Container c)
    {
        return new ActionURL(AnalysesAction.class, c);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class AnalysesAction extends QueryViewAction<QueryExportForm, QueryView>
    {
        public AnalysesAction()
        {
            super(QueryExportForm.class);
        }

        @Override
        protected QueryView createQueryView(QueryExportForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            boolean allowDelete = getContainer().hasPermission(getUser(), DeletePermission.class);
            return new GenotypingAnalysesView(getViewContext(), errors, "Analyses", null, allowDelete);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Analyses");
        }
    }


    public static class RunForm extends QueryExportForm
    {
        private int _run = 0;
        private boolean _filterLowQualityBases = false;

        public int getRun()
        {
            return _run;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRun(int run)
        {
            _run = run;
        }

        public boolean getFilterLowQualityBases()
        {
            return _filterLowQualityBases;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFilterLowQualityBases(boolean filterLowQualityBases)
        {
            _filterLowQualityBases = filterLowQualityBases;
        }
    }


    public static ActionURL getRunURL(Container c, GenotypingRun run)
    {
        ActionURL url = new ActionURL(RunAction.class, c);
        url.addParameter("run", run.getRowId());
        return url;
    }


    private abstract class ReadsAction<FORM extends RunForm> extends QueryViewAction<FORM, QueryView>
    {
        private static final String DATA_REGION_NAME = "Reads";
        private static final String FASTQ_FORMAT = "FASTQ";

        private ReadsAction(Class<? extends FORM> formClass)
        {
            super(formClass);
        }

        @Override
        public ModelAndView getView(FORM form, BindException errors) throws Exception
        {
            if (FASTQ_FORMAT.equals(form.getExportType()))
            {
                QueryView qv = createQueryView(form, errors, true, form.getExportRegion());
                qv.getSettings().setShowRows(ShowRows.ALL);
                DataView view = qv.createDataView();

                DataRegion rgn = view.getDataRegion();
                rgn.setAllowAsync(false);
                rgn.setShowPagination(false);

                RenderContext rc = view.getRenderContext();
                rc.setCache(false);
                ResultSet rs = null;

                try
                {
                    rs = rgn.getResultSet(rc);

                    FastqGenerator fg = new FastqGenerator(rs) {
                        @Override
                        public String getHeader(ResultSet rs) throws SQLException
                        {
                            return rs.getString("Name");
                        }

                        @Override
                        public String getSequence(ResultSet rs) throws SQLException
                        {
                            return rs.getString("Sequence");
                        }

                        @Override
                        public String getQuality(ResultSet rs) throws SQLException
                        {
                            return rs.getString("Quality");
                        }
                    };

                    FastqWriter writer = new FastqWriter(fg, form.getFilterLowQualityBases());
                    writer.write(getViewContext().getResponse(), "reads.fastq");
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }

                return null;
            }

            return super.getView(form, errors);
        }

        @Override
        protected QueryView createQueryView(FORM form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, getTableName());
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("RowId");
            handleSettings(settings);

            QueryView qv = new QueryView(new GenotypingQuerySchema(getUser(), getContainer()), settings, errors)
            {
                @Override
                public PanelButton createExportButton(boolean exportAsWebPage)
                {
                    PanelButton result = super.createExportButton(exportAsWebPage);
                    ActionURL url = getViewContext().cloneActionURL();
                    url.addParameter("exportType", FASTQ_FORMAT);

                    HttpView filesView = new JspView<ActionURL>("/org/labkey/genotyping/view/fastqExportOptions.jsp", url);
                    result.addSubPanel("FASTQ", filesView);

                    return result;
                }
            };

            qv.setShadeAlternatingRows(true);
            return qv;
        }

        protected abstract void handleSettings(QuerySettings settings);

        protected abstract String getTableName();
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class RunAction extends ReadsAction<RunForm>
    {
        private GenotypingRun _run;

        public RunAction()
        {
            super(RunForm.class);
        }

        @Override
        public ModelAndView getView(RunForm form, BindException errors) throws Exception
        {
            _run = GenotypingManager.get().getRun(getContainer(), form.getRun());
            ModelAndView readsView = super.getView(form, errors);

            // Just return the view in export case
            if (form.isExport())
                return readsView;

            VBox vbox = new VBox();
            final ActionButton submitAnalysis = new ActionButton("Add Analysis", getAnalyzeURL(_run.getRowId(), getViewContext().getActionURL()));

            if (GenotypingManager.get().hasAnalyses(_run))
            {
                GenotypingAnalysesView analyses = new GenotypingAnalysesView(getViewContext(), null, "Analyses", new SimpleFilter("Run", _run.getRowId()), false) {
                    @Override
                    protected void populateButtonBar(DataView view, ButtonBar bar)
                    {
                        bar.add(submitAnalysis);
                    }
                };
                analyses.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
                analyses.setTitle("Analyses");
                analyses.setTitleHref(getAnalysesURL(getContainer()));
                vbox.addView(analyses);
            }
            else
            {
                vbox.addView(new HttpView() {
                    @Override
                    protected void renderInternal(Object model, PrintWriter out) throws Exception
                    {
                        submitAnalysis.render(new RenderContext(getViewContext()), out);
                        out.println("<br>");
                    }
                });
            }

            vbox.addView(readsView);

            return vbox;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Run " + _run.getRowId());
        }

        @Override
        protected String getTableName()
        {
            return TableType.Reads.toString();
        }

        @Override
        protected void handleSettings(QuerySettings settings)
        {
            settings.getBaseFilter().addCondition("Run", _run.getRowId());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteRunsAction extends RedirectAction
    {
        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            Set<String> runs = DataRegionSelection.getSelected(getViewContext(), true);
            GenotypingManager gm = GenotypingManager.get();

            for (String runId : runs)
            {
                GenotypingRun run = gm.getRun(getContainer(), Integer.parseInt(runId));
                gm.deleteRun(run);
            }

            return true;
        }

        public void validateCommand(Object o, Errors errors)
        {
        }

        public ActionURL getSuccessURL(Object o)
        {
            return getRunsURL(getContainer());
        }
    }


    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteAnalysesAction extends RedirectAction
    {
        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            Set<String> analyses = DataRegionSelection.getSelected(getViewContext(), true);
            GenotypingManager gm = GenotypingManager.get();

            for (String analysisId : analyses)
            {
                GenotypingAnalysis analysis = gm.getAnalysis(getContainer(), Integer.parseInt(analysisId));
                gm.deleteAnalysis(analysis);
            }

            return true;
        }

        public void validateCommand(Object o, Errors errors)
        {
        }

        public ActionURL getSuccessURL(Object o)
        {
            return getAnalysesURL(getContainer());
        }
    }


    public static class MatchReadsForm extends RunForm
    {
        private int _match = 0;
        private int _analysis = 0;

        public int getMatch()
        {
            return _match;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMatch(int run)
        {
            _match = run;
        }

        public int getAnalysis()
        {
            return _analysis;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalysis(int analysis)
        {
            _analysis = analysis;
        }
    }


    @SuppressWarnings({"UnusedDeclaration"})  // URL defined on matches.reads column in genotyping.xml
    @RequiresPermissionClass(ReadPermission.class)
    public class MatchReadsAction extends ReadsAction<MatchReadsForm>
    {
        private GenotypingAnalysis _analysis;
        private int _matchId;

        public MatchReadsAction()
        {
            super(MatchReadsForm.class);
        }

        @Override
        public ModelAndView getView(MatchReadsForm form, BindException errors) throws Exception
        {
            _analysis = GenotypingManager.get().getAnalysis(getContainer(), form.getAnalysis());
            _matchId = form.getMatch();

            return super.getView(form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Reads for match " + _matchId);
        }

        @Override
        protected String getTableName()
        {
            return TableType.MatchReads.toString();
        }

        @Override
        protected void handleSettings(QuerySettings settings)
        {
            SimpleFilter baseFilter = settings.getBaseFilter();
            baseFilter.addCondition("Run", _analysis.getRun());
            baseFilter.addCondition("RowId/MatchId", _matchId);
        }
    }
}
