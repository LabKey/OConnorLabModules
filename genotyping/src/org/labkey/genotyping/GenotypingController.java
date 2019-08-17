/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.QueryViewAction.QueryExportForm;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.assay.actions.AssayHeaderView;
import org.labkey.api.assay.actions.AssayRunsAction;
import org.labkey.api.assay.actions.BaseAssayAction;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.AssayView;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MinorConfigurationException;
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
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.genotyping.GenotypingManager.SEQUENCE_PLATFORMS;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
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
    @SuppressWarnings({"unchecked"})
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(GenotypingController.class);

    public GenotypingController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o)
        {
            return getRunsURL(getContainer());
        }
    }


    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        return config.setHelpTopic(new HelpTopic("genotyping"));
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
        private Integer _highlightId = null;
        private String _error = null;

        public Integer getAnalysis()
        {
            return _analysis;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalysis(Integer analysis)
        {
            _analysis = analysis;
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

        public String getError()
        {
            return _error;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setError(String error)
        {
            _error = error;
        }
    }


    @RequiresPermission(ReadPermission.class)
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
            // Now that the form has populated "highlightId" and "error", eliminate these parameters from the current
            // ActionURL.  We don't want links to propagate it.
            ActionURL currentURL = getViewContext().cloneActionURL().deleteParameter("highlightId").deleteParameter("error");
            currentURL.setReadOnly();
            getViewContext().setActionURL(currentURL);

            String error = form.getError();
            VBox vbox = new VBox();

            if (null != error)
            {
                // Unfortunately, this doesn't work... DataRegion never bothers to render the errors TODO: Fix DataView/DataRegion
                errors.reject("form", error);
                // So throw an error view at the top of the page
                vbox.addView(new SimpleErrorView(errors, false));
            }

            vbox.addView(super.getView(form, errors));

            return vbox;
        }

        @Override
        protected QueryView createQueryView(AnalysisForm form, BindException errors, boolean forExport, String dataRegion)
        {
            _analysis = GenotypingManager.get().getAnalysis(getContainer(), form.getAnalysis());

            QuerySettings settings = new QuerySettings(getViewContext(), "Analysis", TableType.Matches.toString());
            settings.setAllowChooseView(true);
            settings.setBaseSort(new Sort("SampleId/library_sample_name,Alleles/AlleleName"));
            settings.getBaseFilter().addCondition(FieldKey.fromParts("Analysis"), form.getAnalysis());

            UserSchema gqs = new GenotypingQuerySchema(getUser(), getContainer(), form.getAnalysis());
            QueryView qv = new QueryView(gqs, settings, errors);
            qv.setShadeAlternatingRows(true);
            return qv;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Genotyping Analysis " + _analysis.getRowId());
        }
    }


    public static class MatchesForm extends ReturnUrlForm
    {
        private int _analysis;
        private int[] _matchIds;

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
    }

    private ActionURL getDeleteMatchesURL(int analysisId)
    {
        return new ActionURL(DeleteMatchesAction.class, getContainer()).addParameter("analysis", analysisId);
    }


    // TODO: Delete this action? No longer used?
    @RequiresPermission(DeletePermission.class)
    public class DeleteMatchesAction extends FormHandlerAction<MatchesForm>
    {
        private int _count = 0;
        private String _error = null;

        @Override
        public URLHelper getSuccessURL(MatchesForm form)
        {
            ActionURL url = getAnalysisURL(getContainer(), form.getAnalysis());
            url.addParameter("alter", 1);

            if (null != _error)
                url.addParameter("error", _error);
            else if (_count > 0)
                url.addParameter("delete", _count);

            return url;
        }

        @Override
        public void validateCommand(MatchesForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(MatchesForm form, BindException errors)
        {
            List<String> ids = getViewContext().getList(DataRegion.SELECT_CHECKBOX_NAME);
            List<Integer> matchIds = new LinkedList<>();

            for (String id : ids)
                matchIds.add(Integer.parseInt(id));

            try
            {
                _count = GenotypingManager.get().deleteMatches(getContainer(), getUser(), form.getAnalysis(), matchIds);
            }
            catch (IllegalStateException e)
            {
                _error = "Delete failed: " + e.getMessage();
            }
            return true;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class LoadSequencesAction extends FormHandlerAction<ReturnUrlForm>
    {
        @Override
        public void validateCommand(ReturnUrlForm target, Errors errors)
        {
            //Issue 15583: if sequences table has not been set properly, reject the import
            ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(getContainer(), getUser(), "loading sequences");
            GenotypingQueryHelper qHelper = new GenotypingQueryHelper(getContainer(), getUser(), settings.getSequencesQuery());

            if (qHelper.getTableInfo(null) == null)
                errors.reject("Could not find sequences query " + settings.getSequencesQuery());
        }

        @Override
        public boolean handlePost(ReturnUrlForm returnUrlForm, BindException errors)
        {
            long startTime = System.currentTimeMillis();
            SequenceManager.get().loadSequences(getContainer(), getUser());
            LOG.info(DateUtil.formatDuration(System.currentTimeMillis() - startTime) + " to load sequences");

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ReturnUrlForm form)
        {
            ActionURL begin = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
            return form.getReturnURLHelper(begin);
        }
    }


    // TODO: Annotate getters with @Nullable
    public static class AdminForm extends ReturnUrlForm implements GenotypingFolderSettings, GalaxyFolderSettings, HasViewContext
    {
        private String _galaxyURL;
        private String _sequencesQuery;
        private String _runsQuery;
        private String _samplesQuery;
        private String _haplotypesQuery;
        private String _message;

        @Override
        public void setViewContext(ViewContext context)
        {
            Container c = context.getContainer();

            NonValidatingGenotypingFolderSettings genotypingSettings = new NonValidatingGenotypingFolderSettings(c);
            _sequencesQuery = genotypingSettings.getSequencesQuery();
            _runsQuery = genotypingSettings.getRunsQuery();
            _samplesQuery = genotypingSettings.getSamplesQuery();
            _haplotypesQuery = genotypingSettings.getHaplotypesQuery();

            GalaxyFolderSettings galaxySettings = GalaxyManager.get().getSettings(c);
            _galaxyURL = galaxySettings.getGalaxyURL();
        }

        @Override
        public ViewContext getViewContext()
        {
            throw new IllegalStateException();
        }

        @Override
        public @Nullable String getGalaxyURL()
        {
            return _galaxyURL;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setGalaxyURL(@Nullable String galaxyURL)
        {
            _galaxyURL = galaxyURL;
        }

        @Override
        public @Nullable String getSequencesQuery()
        {
            return _sequencesQuery;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSequencesQuery(String sequencesQuery)
        {
            _sequencesQuery = sequencesQuery;
        }

        @Override
        public @Nullable String getRunsQuery()
        {
            return _runsQuery;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRunsQuery(String runsQuery)
        {
            _runsQuery = runsQuery;
        }

        @Override
        public @Nullable String getSamplesQuery()
        {
            return _samplesQuery;
        }

        @Override
        public @Nullable String getHaplotypesQuery()
        {
            return _haplotypesQuery;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setHaplotypesQuery(String haplotypesQuery)
        {
            _haplotypesQuery = haplotypesQuery;
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


    @RequiresPermission(AdminPermission.class)
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
        public ModelAndView getView(AdminForm form, boolean reshow, BindException errors)
        {
            NonValidatingGenotypingFolderSettings currentSettings = new NonValidatingGenotypingFolderSettings(getContainer());
            VBox vbox = new VBox();

            if (null != currentSettings.getSequencesQuery())
            {
                WebPartView loadSequences = new JspView<ReturnUrlForm>("/org/labkey/genotyping/view/loadSequences.jsp", form);
                loadSequences.setTitle("Reference Sequences");
                vbox.addView(loadSequences);
            }

            WebPartView configure = new JspView<>("/org/labkey/genotyping/view/configure.jsp", form, errors);
            configure.setTitle("Configuration");
            vbox.addView(configure);

            return vbox;
        }

        @Override
        public boolean handlePost(AdminForm form, BindException errors)
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

    @RequiresPermission(ReadPermission.class)
    @IgnoresTermsOfUse
    public class MergeFastqFilesAction extends ExportAction<MergeFastqFilesForm>
    {
        public void export(MergeFastqFilesForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            if(form.getDataIds() == null || form.getDataIds().length == 0)
            {
                throw new NotFoundException("No files provided");
            }

            String filename = form.getZipFileName();
            if(filename == null)
            {
                throw new NotFoundException("Must provide a filename for the archive");
            }
            filename += ".fastq.gz";

            Set<File> files = new HashSet<>();
            for (Integer id : form.getDataIds())
            {
                ExpData d = ExperimentService.get().getExpData(id);
                if (d == null)
                {
                    throw new NotFoundException("Unable to find ExpData for ID: " + id);
                }
                if (!d.getContainer().hasPermission(getUser(), ReadPermission.class))
                {
                    throw new SecurityException("You do not have read permissions for the file with ID: " + id);
                }
                files.add(d.getFile());
            }

            PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), filename, true);

            for (File f : files)
            {
                if (!f.exists())
                {
                    throw new NotFoundException("File " + f.getPath() + " does not exist");
                }

                try (FileInputStream in = new FileInputStream(f))
                {
                    IOUtils.copy(in, response.getOutputStream());
                }
            }
        }
    }

    public static class MergeFastqFilesForm extends ReturnUrlForm
    {
        private int[] _dataIds;
        private String _zipFileName;

        public int[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(int[] dataIds)
        {
            _dataIds = dataIds;
        }

        public String getZipFileName()
        {
            return _zipFileName;
        }

        public void setZipFileName(String zipFileName)
        {
            _zipFileName = zipFileName;
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


    @RequiresPermission(ReadPermission.class)
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
                key = key.trim();
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
                        errors.reject(ERROR_MSG, "Galaxy web API key is not valid hexadecimal." + advice);
                }
            }
        }

        @Override
        public ModelAndView getView(MySettingsForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/genotyping/view/mySettings.jsp", form, errors);
        }

        @Override
        public boolean handlePost(MySettingsForm form, BindException errors)
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
        private String _platform;
        private String _prefix;

        public enum Platforms{
            LS454{

                @Override
                public void prepareAndQueueRunJob(ViewBackgroundInfo vbi, PipeRoot root, File readsFile, GenotypingRun run, @Nullable String prefix) throws PipelineValidationException
                {
                    PipelineJob prepareRunJob = new Import454ReadsJob(vbi, root, readsFile, run);
                    PipelineService.get().queueJob(prepareRunJob);
                }
            },
            ILLUMINA{

                @Override
                public void prepareAndQueueRunJob(ViewBackgroundInfo vbi, PipeRoot root, File readsFile, GenotypingRun run, @Nullable String prefix) throws PipelineValidationException
                {
                    PipelineJob prepareRunJob = new ImportIlluminaReadsJob(vbi, root, readsFile, run, prefix);
                    PipelineService.get().queueJob(prepareRunJob);
                }
            },
            PACBIO{

                @Override
                public void prepareAndQueueRunJob(ViewBackgroundInfo vbi, PipeRoot root, File readsFile, GenotypingRun run, @Nullable String prefix) throws PipelineValidationException
                {
                    PipelineJob prepareRunJob = new ImportPacBioReadsJob(vbi, root, readsFile, run, prefix);
                    PipelineService.get().queueJob(prepareRunJob);
                }
            };

            abstract public void prepareAndQueueRunJob(ViewBackgroundInfo vbi, PipeRoot root, File readsFile, GenotypingRun run, @Nullable String prefix) throws PipelineValidationException;

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

        public String getPlatform()
        {
            return _platform;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPlatform(String platform)
        {
            _platform = platform;
        }

        public String getPrefix()
        {
            return _prefix;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }


    public static class ImportReadsBean
    {
        private List<Integer> _runs;
        private SEQUENCE_PLATFORMS _platform;
        private String _readsPath;
        private String _path;
        private String _prefix;

        private ImportReadsBean(List<Integer> runs, String readsPath, String path, @Nullable String platform, @Nullable String prefix)
        {
            _runs = runs;
            _readsPath = readsPath;
            _path = path;
            _platform = SEQUENCE_PLATFORMS.getPlatform(platform) ;
            _prefix = prefix;
        }

        public List<Integer> getRuns()
        {
            return _runs;
        }

        public String getReadsPath()
        {
            return _readsPath;
        }

        public @NotNull SEQUENCE_PLATFORMS getPlatform()
        {
            return _platform;
        }

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public String getPath()
        {
            return _path;
        }
    }


    // NULL view name means use default view; need a placeholder string to display and post from the form
    public static final String DEFAULT_VIEW_PLACEHOLDER = "[default]";

    @RequiresPermission(InsertPermission.class)
    public class ImportReadsAction extends FormViewAction<ImportReadsForm>
    {
        private ActionURL _successURL = null;

        @Override
        public void validateCommand(ImportReadsForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ImportReadsForm form, boolean reshow, BindException errors)
        {
            ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(getContainer(), getUser(), "importing reads");
            TableInfo runs = new GenotypingQueryHelper(getContainer(), getUser(), settings.getRunsQuery()).getTableInfo(null);
            GenotypingQueryHelper.validateRunsQuery(runs);
            settings.getSamplesQuery();  // Pipeline job will flag this if missing, but let's proactively validate before we launch the job
            List<Integer> allRuns = new TableSelector(runs, PageFlowUtil.set(GenotypingQueryHelper.RUN_NUM), null, new Sort("-run_num")).getArrayList(Integer.class);   // TODO: Should restrict to this folder, #14278

            // Issue 14278: segregate genotyping runs by container
            SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());

            if(!SEQUENCE_PLATFORMS.PACBIO.name().equalsIgnoreCase(form.getPlatform()))//for PacBio allow association with a run multiple times
                allRuns.removeAll(new TableSelector(GenotypingSchema.get().getRunsTable(), PageFlowUtil.set("MetaDataId"), filter, null).getCollection(Integer.class));

            return new JspView<>("/org/labkey/genotyping/view/importReads.jsp", new ImportReadsBean(allRuns, form.getReadsPath(), form.getPath(), form.getPlatform(), form.getPrefix()), errors);
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

            if (null == form.getPlatform())
                return "You must specify a sequence platform";

            try
            {
                File readsFile = new File(form.getReadsPath());
                GenotypingRun run;

                try
                {
                    run = GenotypingManager.get().createRun(getContainer(), getUser(), form.getMetaDataRun(), readsFile, form.getPlatform());
                }
                catch (RuntimeSQLException e)
                {
                    if (RuntimeSQLException.isConstraintException(e.getSQLException()))
                        return "Run " + form.getRun() + " has already been imported";
                    else
                        throw e;
                }

                ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL());
                PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                ImportReadsForm.Platforms platform = ImportReadsForm.Platforms.valueOf(form.getPlatform());

                platform.prepareAndQueueRunJob(vbi, root, new File(form.getReadsPath()), run, form.getPrefix());
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
            return root.addChild("Import Reads");
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


    @RequiresPermission(InsertPermission.class)
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

            // Verify that galaxy properties are set before submitting job.  This will throw NotFoundException if either URL or web API key isn't set.
            // 12.1: relax this requirement... allow users to submit jobs without a galaxy server configured or available
            //GalaxyUtils.get(getContainer(), getUser());

            SortedSet<CustomView> views = new TreeSet<>((c1, c2) ->
            {
                String name1 = c1.getName();
                String name2 = c2.getName();

                return (null == name1 ? DEFAULT_VIEW_PLACEHOLDER : name1).compareTo((null == name2 ? DEFAULT_VIEW_PLACEHOLDER : name2));
            });
            GenotypingSchema gs = GenotypingSchema.get();
            views.addAll(QueryService.get().getCustomViews(getUser(), getContainer(), getUser(), gs.getSchemaName(), gs.getSequencesTable().getName(), false));

            Map<Integer, Pair<String, String>> sampleMap = new TreeMap<>();
            ResultSet rs = null;

            try
            {
                Results results = SampleManager.get().selectSamples(getContainer(), getUser(), run, "library_sample_name, library_sample_species, key", "creating an analysis");
                rs = results.getResultSet();
                Map<FieldKey, ColumnInfo> fieldMap = results.getFieldMap();
                ColumnInfo sampleNameColumn = getColumnInfo(fieldMap, "library_sample_name");
                ColumnInfo sampleSpeciesColumn = getColumnInfo(fieldMap, "library_sample_species");
                ColumnInfo keyColumn = getColumnInfo(fieldMap, "key");

                while (null != rs && rs.next())
                {
                    String sampleName = (String)sampleNameColumn.getValue(rs);
                    String species = (String)sampleSpeciesColumn.getValue(rs);
                    int sampleId = (Integer)keyColumn.getValue(rs);
                    sampleMap.put(sampleId, new Pair<>(sampleName, species));
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            return new JspView<>("/org/labkey/genotyping/view/analyze.jsp", new AnalyzeBean(views, sampleMap, form.getReturnActionURL()), errors);
        }

        // Throws NotFoundException if column doesn't exist
        private ColumnInfo getColumnInfo(Map<FieldKey, ColumnInfo> fieldMap, String columnName)
        {
            ColumnInfo column = fieldMap.get(FieldKey.fromString(columnName));

            if (null == column)
                throw new NotFoundException("Expected to find a column named \"" + columnName + "\" in the samples query");

            return column;
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
            if(samples == null)
            {
                errors.reject(ERROR_MSG, "Must provide a list of sample IDs");
                return false;
            }

            Set<Integer> sampleKeys;
            String[] keys = samples.split(",");
            sampleKeys = new HashSet<>(keys.length);

            for (String key : keys)
                sampleKeys.add(Integer.parseInt(key));

            GenotypingAnalysis analysis = GenotypingManager.get().createAnalysis(getContainer(), getUser(), run, description, sequencesView);
            try
            {
                PipelineJob analysisJob = new SubmitAnalysisJob(vbi, root, readsPath, analysis, sampleKeys);
                PipelineService.get().queueJob(analysisJob);
            }
            catch (MinorConfigurationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

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


    public static ActionURL getWorkflowCompleteURL(Container c, GenotypingAnalysis analysis)
    {
        ActionURL url = new ActionURL(WorkflowCompleteAction.class, c);
        url.addParameter("analysis", analysis.getRowId());
        url.addParameter("path", analysis.getPath());
        return url;
    }


    @RequiresNoPermission
    @CSRF(CSRF.Method.NONE)
    public class WorkflowCompleteAction extends MutatingApiAction<ImportAnalysisForm>
    {
        @Override
        public void validateForm(ImportAnalysisForm form, Errors errors)
        {
            if (null == form.getAnalysis())
                errors.reject(ERROR_MSG, "Must specify an analysis parameter");

            if (null == form.getPath())
                errors.reject(ERROR_MSG, "Must specify a path parameter");
        }

        @Override
        public Object execute(ImportAnalysisForm form, BindException errors) throws Exception
        {
            LOG.info("Galaxy signaled the completion of analysis " + form.getAnalysis());
            String message;

            // Send any exceptions back to the Galaxy task so it can log it as well.
            String FAILURE_PREFACE = "Failed to queue import analysis job: ";

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

                // But log more detail to the administrator so they're aware
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
    }


    public static class ImportAnalysisForm
    {
        private Integer _analysis = null;
        private String _path = null;

        public Integer getAnalysis()
        {
            return _analysis;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setAnalysis(Integer analysis)
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


    @RequiresPermission(InsertPermission.class)
    public class ImportAnalysisAction extends FormHandlerAction<PipelinePathForm>
    {
        @Override
        public void validateCommand(PipelinePathForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(PipelinePathForm form, BindException errors) throws IOException, PipelineValidationException
        {
            // Manual upload of genotyping analysis; pipeline provider posts to this action with matches file.
            File matches = form.getValidatedSingleFile(getContainer());
            File analysisDir = matches.getParentFile();

            // Load properties to determine the run.
            Properties props = GenotypingManager.get().readProperties(analysisDir);
            int analysisId = Integer.parseInt((String)props.get("analysis"));
            importAnalysis(analysisId, analysisDir, getUser());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(PipelinePathForm pipelinePathForm)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }


    private void importAnalysis(int analysisId, File pipelineDir, User user) throws IOException, PipelineValidationException
    {
        GenotypingAnalysis analysis = GenotypingManager.get().getAnalysis(getContainer(), analysisId);
        File analysisDir = new File(analysis.getPath());

        String pipelinePath = pipelineDir.getCanonicalPath();
        String analysisPath = analysisDir.getCanonicalPath();

        if (!pipelinePath.equals(analysisPath))
            throw new FileNotFoundException("Analysis path (\"" + analysisPath +
                    "\") doesn't match specified path (\"" + pipelinePath + "\")");

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
            Status currentStatus = Status.getStatus(analysis.getStatus());
            String message = "Could not import an analysis: it was ";

            // First case would be a race condition...
            switch (currentStatus)
            {
                case Submitted:
                    message += "not completely submitted";
                    break;
                case NotSubmitted:
                    message += "not submitted";
                    break;
                case Importing:
                    message += "already importing";
                    break;
                case Complete:
                    message += "previously imported";
                    break;
            }

            throw new MinorConfigurationException(message);
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


    @RequiresPermission(ReadPermission.class)
    public class SequencesAction extends QueryViewAction<SequencesForm, QueryView>
    {
        public SequencesAction()
        {
            super(SequencesForm.class);
        }

        @Override
        protected QueryView createQueryView(SequencesForm form, BindException errors, boolean forExport, String dataRegion)
        {
            QuerySettings settings = new QuerySettings(getViewContext(), "Sequences", TableType.Sequences.toString());
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("RowId");
            Integer dictionary = form.getDictionary();
            settings.getBaseFilter().addCondition(FieldKey.fromParts("Dictionary"), null != dictionary ? dictionary : SequenceManager.get().getCurrentDictionary(getContainer(), getUser()).getRowId());

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
    @RequiresPermission(ReadPermission.class)
    public class SequenceAction extends SimpleViewAction<SequenceForm>
    {
        @Override
        public ModelAndView getView(SequenceForm form, BindException errors)
        {
            QuerySettings settings = new QuerySettings(getViewContext(), "Sequence", TableType.Sequences.toString());

            // Adding the dictionary ensures we're grabbing a sequence from this container
            Integer dictionary = form.getDictionary();
            settings.getBaseFilter().addCondition(FieldKey.fromParts("Dictionary"), null != dictionary ? dictionary : SequenceManager.get().getCurrentDictionary(getContainer(), getUser()).getRowId());
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


    @RequiresPermission(ReadPermission.class)
    public class RunsAction extends QueryViewAction<QueryExportForm, QueryView>
    {
        public RunsAction()
        {
            super(QueryExportForm.class);
        }

        @Override
        protected QueryView createQueryView(QueryExportForm form, BindException errors, boolean forExport, String dataRegion)
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


    @RequiresPermission(ReadPermission.class)
    public class AnalysesAction extends QueryViewAction<QueryExportForm, QueryView>
    {
        public AnalysesAction()
        {
            super(QueryExportForm.class);
        }

        @Override
        protected QueryView createQueryView(QueryExportForm form, BindException errors, boolean forExport, String dataRegion)
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

        public enum Platforms{

            LS454 {
                @Override
                public String getTableName()
                {
                    return TableType.Reads.toString();
                }
            },
            ILLUMINA {
                @Override
                public String getTableName()
                {
                    return TableType.SequenceFiles.toString();
                }
            },
            PACBIO {
                @Override
                public String getTableName()
                {
                    return TableType.SequenceFiles.toString();
                }
            };

            public String getTableName()
            {
                return TableType.Reads.toString();
            }
        }

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

    public static final String FASTQ_FILE_FORMAT = "FASTQ_FILE";
    public static final String FASTQ_FORMAT = "FASTQ";

    private abstract class ReadsAction<FORM extends RunForm> extends QueryViewAction<FORM, QueryView>
    {
        private static final String DATA_REGION_NAME = "Reads";

        private ReadsAction(Class<? extends FORM> formClass)
        {
            super(formClass);
        }

        @Override
        public ModelAndView getView(FORM form, BindException errors) throws Exception
        {
            GenotypingRun _run = GenotypingManager.get().getRun(getContainer(), form.getRun());

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

                    FastqGenerator fg = new FastqGenerator(rs)
                    {
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
            else if (FASTQ_FILE_FORMAT.equals(form.getExportType()))
            {

            }
            return super.getView(form, errors);
        }

        @Override
        protected QueryView createQueryView(FORM form, BindException errors, boolean forExport, String dataRegion)
        {
            GenotypingRun _run = GenotypingManager.get().getRun(getContainer(), form.getRun());
            final String platform = null==_run?"":_run.getPlatform();

            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, getTableName());
            settings.setAllowChooseView(true);
            if(platform.equals(GenotypingManager.SEQUENCE_PLATFORMS.ILLUMINA.toString()))
            {
                settings.getBaseSort().insertSortColumn("DataId/Name");
                settings.getBaseSort().insertSortColumn("SampleId");
            }
            else
            {
                settings.getBaseSort().insertSortColumn("RowId");
            }
            handleSettings(settings);

            QueryView qv = new GenotypingQuerySchema(getUser(), getContainer()).createView(getViewContext(), settings, errors);
            qv.setShadeAlternatingRows(true);
            return qv;
        }

        protected abstract void handleSettings(QuerySettings settings);

        protected abstract String getTableName();
    }


    @RequiresPermission(ReadPermission.class)
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

            if (null == _run)
                throw new NotFoundException("Run not found");

            final boolean allowAnalysis = GenotypingManager.SEQUENCE_PLATFORMS.LS454.toString().equals(_run.getPlatform());

            ModelAndView readsView;
            readsView = super.getView(form, errors);

            // Just return the view in export case
            if (form.isExport())
                return readsView;

            VBox vbox = new VBox();
            final ActionButton submitAnalysis = new ActionButton("Add Analysis", getAnalyzeURL(_run.getRowId(), getViewContext().getActionURL()));

            if (GenotypingManager.get().hasAnalyses(_run))
            {
                GenotypingAnalysesView analyses = new GenotypingAnalysesView(getViewContext(), null, "Analyses", new SimpleFilter(FieldKey.fromParts("Run"), _run.getRowId()), false) {
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
                        if(allowAnalysis)
                        {
                            submitAnalysis.render(new RenderContext(getViewContext()), out);
                            out.println("<br><br>");
                        }
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
            return RunForm.Platforms.valueOf(_run.getPlatform()).getTableName();
        }

        @Override
        protected void handleSettings(QuerySettings settings)
        {
            settings.getBaseFilter().addCondition(FieldKey.fromParts("Run"), _run.getRowId());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class DeleteRunsAction extends FormHandlerAction
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            GenotypingManager gm = GenotypingManager.get();
            Set<Integer> runs = DataRegionSelection.getSelectedIntegers(getViewContext(), true);

            for (Integer runId : runs)
            {
                GenotypingRun run = gm.getRun(getContainer(), runId);
                gm.deleteRun(run);
            }

            return true;
        }

        @Override
        public ActionURL getSuccessURL(Object o)
        {
            return getRunsURL(getContainer());
        }
    }


    @RequiresPermission(DeletePermission.class)
    public class DeleteAnalysesAction extends FormHandlerAction
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            GenotypingManager gm = GenotypingManager.get();

            for (Integer analysisId : DataRegionSelection.getSelectedIntegers(getViewContext(), true))
            {
                GenotypingAnalysis analysis = gm.getAnalysis(getContainer(), analysisId);
                gm.deleteAnalysis(analysis);
            }

            return true;
        }

        @Override
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
    @RequiresPermission(ReadPermission.class)
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
            baseFilter.addCondition(FieldKey.fromParts("Run"), _analysis.getRun());
            baseFilter.addCondition(FieldKey.fromParts("RowId", "MatchId"), _matchId);
        }
    }

    public class AssignmentReportBean
    {
        private final Collection<Integer> _ids;
        private final String _assayName;
        private final ActionURL _returnURL;

        public AssignmentReportBean(Collection<Integer> ids, String assayName, ActionURL returnURL)
        {
            _ids = ids;
            _assayName = assayName;
            _returnURL = returnURL;
        }

        public Collection<Integer> getIds()
        {
            return _ids;
        }

        public String getAssayName()
        {
            return _assayName;
        }

        public ActionURL getReturnURL()
        {
            return _returnURL;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class HaplotypeAssignmentReportAction extends SimpleViewAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors)
        {
            _protocol = form.getProtocol();

            // when coming from the results grid, we use the selected rows as the initial set of IDs for the report form
            Set<Integer> selected = DataRegionSelection.getSelectedIntegers(getViewContext(), false);

            VBox result = new VBox();
            AssayHeaderView header = new AssayHeaderView(form.getProtocol(), form.getProvider(), false, true, null);
            result.addView(header);

            ActionURL returnURL = form.getReturnActionURL(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer()));

            AssignmentReportBean bean = new AssignmentReportBean(selected, _protocol.getName(), returnURL);
            JspView report = new JspView<>("/org/labkey/genotyping/view/haplotypeAssignmentReport.jsp", bean);
            result.addView(report);

            return result;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
            root.addChild(_protocol.getName(), new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId()));
            root.addChild("Haplotype Assignment Report");
            return root;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DuplicateAssignmentReportAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors)
        {
            _protocol = form.getProtocol();

            AssayView result = new AssayView();
            AssaySchema schema = form.getProvider().createProtocolSchema(getUser(), getContainer(), form.getProtocol(), null);
            QuerySettings settings = new QuerySettings(getViewContext(), "query", HaplotypeProtocolSchema.DUPLICATE_ASSIGNMENT_QUERY_NAME);
            QueryView view = new QueryView(schema, settings, errors);
            result.setupViews(view, false, form.getProvider(), _protocol);

            return result;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild(_protocol.getName() + ": Duplicate Assignment Report");

            return root;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class STRDiscrepanciesAssignmentReportAction extends SimpleViewAction<STRDiscrepancies>
    {
        private ExpProtocol _protocol;
        private static final String DELIM = "[;,/]";

        @Override
        public ModelAndView getView(final STRDiscrepancies form, BindException errors)
        {
            _protocol = form.getProtocol();
            GenotypingSchema gs = GenotypingSchema.get();

            // NOTE: need to narrow down based on protocol/assay/container

            SQLFragment sql = new SQLFragment("SELECT a." + HaplotypeAssayProvider.LAB_ANIMAL_ID + ", h.name, h.type FROM ");
            sql.append(gs.getAnimalHaplotypeAssignmentTable(), "aha");
            sql.append(" JOIN ");
            sql.append(gs.getHaplotypeTable(), "h");
            sql.append(" ON aha.haplotypeid = h.rowid ");
            sql.append(" JOIN ");
            sql.append(gs.getAnimalAnalysisTable(), "aa");
            sql.append(" ON aha.animalanalysisid = aa.rowid ");
            sql.append(" JOIN ");
            sql.append(gs.getAnimalTable(), "a");
            sql.append(" ON aa.animalid = a.rowid ");
            sql.append(" JOIN ");
            sql.append(ExperimentService.get().getTinfoExperimentRun(), "er");
            sql.append(" ON aa.RunId = er.RowId");
            sql.append(" JOIN ");
            sql.append(ExperimentService.get().getTinfoProtocol(), "p");
            sql.append(" ON er.ProtocolLSID = p.LSID");
            sql.append(" WHERE p.RowId = ? AND aa.enabled = ?");
            sql.add(_protocol.getRowId());
            sql.add(true);

            // NOTE: this has assignments already mixed in (site/key 'STR').
            final Map<String, Map<String, Set<String>>> animalHaplotypes = new TreeMap<>();

            new SqlSelector(gs.getSchema(), sql).forEach(rs -> {
                String labAnimalId = rs.getString(HaplotypeAssayProvider.LAB_ANIMAL_ID);
                String type = rs.getString("type");

                for (String name : getHaplotypeArray(rs, "name"))
                {
                    name = stripSubType(name, form);

                    animalHaplotypes.computeIfAbsent(labAnimalId, k -> new TreeMap<>());
                    Map<String, Set<String>> haplotypeMap = animalHaplotypes.get(labAnimalId);

                    haplotypeMap.computeIfAbsent(type, k -> new HashSet<>());

                    haplotypeMap.get(type).add(name);
                }
            });

            // Next need to get STR haplotypes
            final Map <String, Set<Map<String, String>>> strHaplotypes = new TreeMap<>();
            TableInfo tableInfo = QueryService.get().getUserSchema(getUser(), getContainer(), "lists").getTable(HaplotypeAssayProvider.STR_HAPLOTYPE);
            if (tableInfo == null)
            {
                throw new NotFoundException("Could not find table '" + HaplotypeAssayProvider.STR_HAPLOTYPE + "' in schema 'lists'");
            }

            new TableSelector(tableInfo).forEach(rs -> {
                String[] mamuAs = getHaplotypeArray(rs, HaplotypeAssayProvider.MHC_A);
                String[] mamuBs = getHaplotypeArray(rs, HaplotypeAssayProvider.MHC_B);
                String[] mamuDRs = getHaplotypeArray(rs, HaplotypeAssayProvider.MHC_DR);
                Set<Map<String, String>> strGrouping = new HashSet<>();

                for(String mamuA : mamuAs)
                {
                    mamuA = stripSubType(mamuA, form);
                    for(String mamuB : mamuBs)
                    {
                        mamuB = stripSubType(mamuB, form);
                        for(String mamuDR : mamuDRs)
                        {
                            mamuDR = stripSubType(mamuDR, form);
                            Map<String, String> haplotypeMap = new TreeMap<>();
                            if (!mamuA.isEmpty())
                                haplotypeMap.put(HaplotypeAssayProvider.MHC_A, mamuA);
                            if (!mamuB.isEmpty())
                                haplotypeMap.put(HaplotypeAssayProvider.MHC_B, mamuB);
                            if (!mamuDR.isEmpty())
                                haplotypeMap.put(HaplotypeAssayProvider.MHC_DRB, mamuDR);
                            strGrouping.add(haplotypeMap);
                        }
                    }
                }
                strHaplotypes.put(rs.getString(HaplotypeAssayProvider.STR_HAPLOTYPE), strGrouping);
            });

            // now loop over STR assignments looking for discrepancies

            for (Map.Entry<String, Map<String, Set<String>>> entry : animalHaplotypes.entrySet() )
            {
                String currentAnimal = entry.getKey();
                // NOTE: consider better name of var...
                Map<String, Set<String>> m = entry.getValue();
                Set<String> currentAssignments = m.get(HaplotypeAssayProvider.MHC_STR);

                if (m.containsKey(HaplotypeAssayProvider.MHC_STR))
                {
                    for (String assignment : currentAssignments)
                    {
                        Set<Map<String, String>> haplotypeAssignments = strHaplotypes.get(assignment);
                        if (haplotypeAssignments != null)
                        {
                            for (Map<String, String> strHaplotype : haplotypeAssignments)
                            {
                                for (Map.Entry<String, String> strDefinition : strHaplotype.entrySet())
                                {
                                    String currentSite = strDefinition.getKey();
                                    if (m.get(currentSite) != null && !m.get(currentSite).contains(strDefinition.getValue()))
                                        form.insertDiscrepancy(currentAnimal, currentSite);
                                }
                            }
                        }
                    }
                }
            }

            AssayHeaderView header = new AssayHeaderView(_protocol, form.getProvider(), false, true, tableInfo.getContainerFilter());
            JspView<STRDiscrepancies> view = new JspView<>("/org/labkey/genotyping/view/strDiscrepancies.jsp", form, errors);
            return new VBox(header, view);
        }

        @NotNull
        private String[] getHaplotypeArray(ResultSet rs, String columnName) throws SQLException
        {
            String value = rs.getString(columnName);
            if (value == null)
            {
                value = "";
            }
            return value.split(DELIM);
        }

        private String stripSubType(String name, STRDiscrepancies form)
        {
            if (!form.isIgnoreSubtype())
            {
                return name;
            }
            // Strip off subtype suffix, if any
            while (name.length() > 0 && !Character.isDigit(name.charAt(name.length() - 1)))
            {
                name = name.substring(0, name.length() - 1);
            }
            return name;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
            root.addChild(_protocol.getName(), new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId()));
            root.addChild("STR Discrepancies Report");
            return root;
        }
    }

    public static class STRDiscrepancies extends ProtocolIdForm
    {
        private boolean ignoreSubtype;
        private Map<String, Set<String>> discrepancies;

        public STRDiscrepancies()
        {
            this.discrepancies = new TreeMap<>();
        }

        public void insertDiscrepancy(String name, String disrepancy)
        {
            if (!discrepancies.containsKey(name))
                discrepancies.put(name, new TreeSet<>());
            //NOTE: this might be better as a set...
            discrepancies.get(name).add(disrepancy);
        }

        public boolean isEmpty()
        {
            return discrepancies.isEmpty();
        }

        public Map<String, Set<String>> getDiscrepancies()
        {
            return discrepancies;
        }

        public boolean isIgnoreSubtype()
        {
            return ignoreSubtype;
        }

        public void setIgnoreSubtype(boolean ignoreSubtype)
        {
            this.ignoreSubtype = ignoreSubtype;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class AggregatedResultsReportAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors)
        {
            _protocol = form.getProtocol();

            AssaySchema schema = form.getProvider().createProtocolSchema(getUser(), getContainer(), form.getProtocol(), null);
            QuerySettings settings = new QuerySettings(getViewContext(), "query", HaplotypeProtocolSchema.AGGREGATED_RESULTS_QUERY_NAME);
            QueryView view = new QueryView(schema, settings, errors);
            AssayView result = new AssayView();
            result.setupViews(view, false, form.getProvider(), _protocol);

            return result;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild(_protocol.getName() + ": Aggregated Results Report");

            return root;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class EditHaplotypeAssignmentAction extends SimpleViewAction<AssignmentForm>
    {
        @Override
        public ModelAndView getView(AssignmentForm form, BindException errors)
        {
            if (form.getRowId() == -1)
                errors.reject(ERROR_MSG, "Error: Please provide an rowId for the AnimalAnalysis table.");



            return new JspView<>("/org/labkey/genotyping/view/editHaplotypeAssignment.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Edit Haplotype Assignments");
        }
    }

    public static class AssignmentForm extends ReturnUrlForm
    {
        private int _rowId = -1; // i.e. rowId for the AnimalAnalysis table

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public void setSrcURL(String srcURL)
        {
            setReturnUrl(srcURL);
        }
    }
}
