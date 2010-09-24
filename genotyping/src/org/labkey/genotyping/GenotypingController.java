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
import org.labkey.api.data.ResultSetIterator;
import org.labkey.api.data.SimpleFilter;
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
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
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
import org.labkey.genotyping.galaxy.GalaxyServer;
import org.labkey.genotyping.galaxy.GalaxyUserSettings;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            HtmlView links = new HtmlView(PageFlowUtil.textLink("load results", new ActionURL(LoadAction.class, getContainer())) + " " +
                                          PageFlowUtil.textLink("export results", new ActionURL(TsvAction.class, getContainer())) + " " +
                                          PageFlowUtil.textLink("test galaxy api", new ActionURL(GalaxyAction.class, getContainer())) + " " +
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


    private GalaxyServer getGalaxyServer()
    {
        return GalaxyServer.get(getContainer(), getUser());
    }


    private DataRegion getDataRegion()
    {
        GenotypingSchema gt = GenotypingSchema.get();
        TableInfo tinfoMatches = gt.getMatchesTable();
        List<ColumnInfo> columns = new ArrayList<ColumnInfo>();
        columns.addAll(tinfoMatches.getColumns("SampleId,Reads,Percent,AverageLength,PosReads,NegReads,PosExtReads,NegExtReads"));

        ColumnInfo alleles = tinfoMatches.getColumn("Alleles");
        ColumnInfo allele_rowid = alleles.getFk().createLookupColumn(alleles, "rowId");
        columns.add(allele_rowid);
        ColumnInfo allele = alleles.getFk().createLookupColumn(alleles, "allele");
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
    public class ReplaceSequencesAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            long startTime = System.currentTimeMillis();

            GenotypingFolderSettings settings = GenotypingManager.get().getSettings(getContainer());
            String sequenceSource = settings.getSequencesQuery();

            SimpleFilter viewFilter = getViewFilter(sequenceSource);
            viewFilter.addCondition("file_active", 1);
            TableInfo source = getTableInfo(sequenceSource);

            ResultSet rs = null;

            try
            {
                rs = Table.select(source, Table.ALL_COLUMNS, viewFilter, null);
                ResultSetIterator iter = ResultSetIterator.get(rs);

                // To be safe, we wait until successful select from the external source before deleting exiting sequences.
                TableInfo destination = GenotypingSchema.get().getSequencesTable();
                Table.execute(destination.getSchema(), "DELETE FROM " + destination, null);

                while (iter.hasNext())
                {
                    Map<String, Object> map = iter.next();
                    Map<String, Object> inMap = new HashMap<String, Object>(map.size() * 2);

                    // TODO: ResultSetIterator should have a way to map column names
                    for (Map.Entry<String, Object> entry : map.entrySet())
                    {
                        String key = entry.getKey().replaceAll("_", "");
                        inMap.put(key, entry.getValue());
                    }

                    inMap.put("container", getContainer());
                    Table.insert(getUser(), destination, inMap);
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            LOG.info(DateUtil.formatDuration(System.currentTimeMillis() - startTime) + " to load");

            return getViewURL();
        }
    }


    public static class AdminForm extends ReturnUrlForm implements GenotypingFolderSettings, HasViewContext
    {
        private String _galaxyURL;
        private String _sequencesQuery;
        private String _runsQuery;
        private String _samplesQuery;

        @Override
        public void setViewContext(ViewContext context)
        {
            Container c = context.getContainer();
            GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
            _galaxyURL = settings.getGalaxyURL();
            _sequencesQuery = settings.getSequencesQuery();
            _runsQuery = settings.getRunsQuery();
            _samplesQuery = settings.getSamplesQuery();
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


    private SimpleFilter getViewFilter(String query)
    {
        return getViewFilter(query, getContainer(), getUser());
    }


    public static SimpleFilter getViewFilter(String query, Container c, User user)
    {
        String[] parts = query.split(GenotypingFolderSettings.SEPARATOR);
        String schemaName = parts[0];
        String queryName = parts[1];
        String viewName = parts.length > 2 ? parts[2] : null;

        if (viewName != null)
        {
            CustomView baseView = QueryService.get().getCustomView(user, c, schemaName, queryName, viewName);

            if (baseView != null)
            {
                return getViewFilter(baseView);
            }
            else
            {
                throw new IllegalStateException("Could not find view " + viewName + " on query " + queryName + " in schema " + schemaName + ".");
            }
        }

        return new SimpleFilter();
    }


    public static SimpleFilter getViewFilter(CustomView baseView)
    {
        SimpleFilter viewFilter = new SimpleFilter(); // TODO: add container filter

        // copy our saved view filter into our SimpleFilter via an ActionURL (yuck...)
        ActionURL url = new ActionURL();
        baseView.applyFilterAndSortToURL(url, "mockDataRegion");
        viewFilter.addUrlFilters(url, "mockDataRegion");

        return viewFilter;
    }


    public TableInfo getTableInfo(String query)
    {
        return getTableInfo(query, getContainer(), getUser());
    }


    public static UserSchema getUserSchema(String query, Container c, User user)
    {
        String[] parts = query.split(GenotypingFolderSettings.SEPARATOR);
        String schemaName = parts[0];

        return QueryService.get().getUserSchema(user, c, schemaName);
    }


    public static TableInfo getTableInfo(String query, Container c, User user)
    {
        String[] parts = query.split(GenotypingFolderSettings.SEPARATOR);
        String queryName = parts[1];

        UserSchema schema = getUserSchema(query, c, user);
        return schema.getTable(queryName);
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
                WebPartView replaceSequences = new JspView("/org/labkey/genotyping/view/replaceSequences.jsp");
                replaceSequences.setTitle("Replace Sequences");
                vbox.addView(replaceSequences);
            }

            WebPartView configure = new JspView<AdminForm>("/org/labkey/genotyping/view/admin.jsp", form, errors);
            configure.setTitle("Configuration");
            vbox.addView(configure);

            return vbox;
        }

        @Override
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            GenotypingManager.get().saveSettings(getContainer(), form);
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
            GalaxyUserSettings settings = GenotypingManager.get().getUserSettings(c, user);
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
            GenotypingManager.get().saveUserSettings(getContainer(), getUser(), form);
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


    @RequiresPermissionClass(ReadPermission.class)
    public class GalaxyAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            VBox views = new VBox();

            GalaxyServer server = getGalaxyServer();
            List<GalaxyServer.DataLibrary> libraries = server.getDataLibraries();
            HtmlView librariesView = display(libraries);
            librariesView.setTitle("Existing Data Libraries");
            views.addView(librariesView);

            Set<String> existingNames = new HashSet<String>();
            for (GalaxyServer.DataLibrary library : libraries)
                existingNames.add(library.getName());

            String newName = null;

            for (int i = 1; i < 1000; i++)
            {
                newName = "api_test" + i;

                if (!existingNames.contains(newName))
                    break;
            }

            GalaxyServer.DataLibrary newLibrary = server.createLibrary(newName, "This is my test description", "And here's the synopsis!");
            HtmlView createView = new HtmlView(getHtml(newLibrary));
            createView.setTitle("Here's the data library I just created");
            views.addView(createView);

            GalaxyServer.Folder root = newLibrary.getRootFolder();
            GalaxyServer.Folder newSubfolder = root.createFolder("SubFolder1", "Description for sub folder 1");
            GalaxyServer.Folder newSubfolder2 = newSubfolder.createFolder("SubFolder2", "Description for sub folder 2");
            HtmlView subfolderView = new HtmlView(getHtml(newSubfolder) + getHtml(newSubfolder2));
            subfolderView.setTitle("Here are a couple new subfolders");
            views.addView(subfolderView);

            root.uploadFromImportDirectory("test", "txt", null, true);

            List<GalaxyServer.DataLibrary> newLibraries = server.getDataLibraries();
            HtmlView newLibrariesView = display(newLibraries);
            newLibrariesView.setTitle("Existing Data Libraries: post create and import");
            views.addView(newLibrariesView);

            return views;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        private HtmlView display(List<GalaxyServer.DataLibrary> libraries) throws IOException
        {
            StringBuilder html = new StringBuilder();

            for (GalaxyServer.DataLibrary library : libraries)
            {
                html.append(getHtml(library));

                List<GalaxyServer.LibraryItem> libraryItems = library.getChildren();

                for (GalaxyServer.LibraryItem libraryItem : libraryItems)
                {
                    html.append(getHtml(libraryItem));
                }
            }

            return new HtmlView(html.toString());
        }

        private String getHtml(GalaxyServer.DataLibrary library)
        {
            return library.getName() + ": " + library.getId() + "<br>\n";
        }

        private String getHtml(GalaxyServer.LibraryItem item) throws IOException
        {
            StringBuilder html = new StringBuilder();
            html.append("&nbsp;&nbsp;&nbsp;");
            html.append(item.getName()).append(": ").append(item.getId()).append(" (").append(item.getType()).append(")<br>\n");

            if (item.getType() == GalaxyServer.ItemType.LibraryFile)
            {
                Map<String, Object> info = ((GalaxyServer.File)item).getProperties();

                for (Map.Entry<String, Object> entry : info.entrySet())
                {
                    html.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                    html.append(entry.getKey());
                    html.append(": ");
                    html.append(entry.getValue());
                    html.append("<br>\n");
                }
            }

            return html.toString();
        }
    }


    public static class GalaxyPipelineForm extends PipelinePathForm
    {
        private Integer _run;

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


    public static class SubmitToGalaxyForm extends GalaxyPipelineForm
    {
        private boolean _readyToSubmit = false;
        private String[] _sequencesViews;
        private String _readsPath;

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

        public String getReadsPath()
        {
            return _readsPath;
        }

        public void setReadsPath(String readsPath)
        {
            _readsPath = readsPath;
        }
    }


    public static class SubmitToGalaxyBean
    {
        private List<Integer> _runs;
        private Collection<CustomView> _sequencesViews;
        private String _readsPath;

        private SubmitToGalaxyBean(List<Integer> runs, Collection<CustomView> sequenceViews, String readsPath)
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


    @RequiresPermissionClass(AdminPermission.class)
    public class ImportReadsAction extends FormViewAction<SubmitToGalaxyForm>
    {
        @Override
        public void validateCommand(SubmitToGalaxyForm form, Errors errors)
        {
            if (form.isReadyToSubmit())
            {
                if (null == form.getRun())
                    errors.reject(ERROR_MSG, "You must select a run number");
            }
        }

        @Override
        public ModelAndView getView(SubmitToGalaxyForm form, boolean reshow, BindException errors) throws Exception
        {
            GenotypingFolderSettings settings = GenotypingManager.get().getSettings(getContainer());
            TableInfo runs = getTableInfo(settings.getRunsQuery());
            List<Integer> runNums = Arrays.asList(Table.executeArray(runs, "run_num", null, new Sort("-run_num"), Integer.class));

            Set<CustomView> views = new TreeSet<CustomView>(new Comparator<CustomView>() {
                    @Override
                    public int compare(CustomView c1, CustomView c2)
                    {
                        String name1 = c1.getName();
                        String name2 = c2.getName();

                        return (null == name1 ? "[all]": name1).compareTo((null == name2 ? "[all]": name2));
                    }
                });
            views.addAll(QueryService.get().getCustomViews(getUser(), getContainer(), "sequences", "dnasequences"));

            return new JspView<SubmitToGalaxyBean>("/org/labkey/genotyping/view/submit.jsp", new SubmitToGalaxyBean(runNums, views, form.getReadsPath()), errors);
        }

        @Override
        public boolean handlePost(SubmitToGalaxyForm form, BindException errors) throws Exception
        {
            if (!form.isReadyToSubmit())
            {
                File readsFile = form.getValidatedSingleFile(getContainer());
                form.setReadsPath(readsFile.getPath());
                return false;
            }

            GenotypingRun run = GenotypingManager.get().getRun(getContainer(), getUser(), form.getRun());
            ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL());
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            PipelineJob prepareRunJob = new ImportReadsJob(vbi, root, new File(form.getReadsPath()), run);
            PipelineService.get().queueJob(prepareRunJob);

            String[] sequencesViews = form.getSequencesViews();

            for (int i = 0; null != sequencesViews && i < sequencesViews.length; i++)
            {
                PipelineJob analysisJob = new GenotypingSubmitJob(vbi, root, new File(form.getReadsPath()), run, i, sequencesViews[i]);
                PipelineService.get().queueJob(analysisJob);
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(SubmitToGalaxyForm form)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Import Reads and Metrics");
        }
    }


    public static ActionURL getWorkflowCompleteURL(Container c, int run, File path)
    {
        ActionURL url = new ActionURL(WorkflowCompleteAction.class, c);
        url.addParameter("run", run);
        url.addParameter("path", path.getAbsolutePath());
        return url;
    }


    @RequiresNoPermission
    public class WorkflowCompleteAction extends SimpleViewAction<RunForm>
    {
        @Override
        public ModelAndView getView(RunForm form, BindException errors) throws Exception
        {
            LOG.info("Galaxy claims to be complete with run " + form.getRun());

            int run = form.getRun();
            File pipelineDir = new File(form.getPath());

            // TODO: Verify that path and run match -- just look in properties file?
            // TODO: verify that run is pending

            submitLoadJob(run, pipelineDir);

            // Plain text response back to Galaxy
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("text/plain");
            PrintWriter out = response.getWriter();
            out.print("Job to load results has been queued");
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


    public static class RunForm
    {
        private int _run;
        private String _path;

        public int getRun()
        {
            return _run;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRun(int run)
        {
            _run = run;
        }

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class LoadAction extends FormViewAction<PipelinePathForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

        @Override
        public void validateCommand(PipelinePathForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(PipelinePathForm form, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        @Override
        public boolean handlePost(PipelinePathForm form, BindException errors) throws Exception
        {
            // Manual upload of results; pipeline provider posts to this action with properties.xml file.
            File matches = form.getValidatedSingleFile(getContainer());
            File pipelineDir = matches.getParentFile();
            File properties = new File(pipelineDir, GenotypingManager.PROPERTIES_FILE_NAME);

            // Load properties to determine the run.
            Properties props = new Properties();
            InputStream is = new FileInputStream(properties);
            props.loadFromXML(is);
            is.close();

            Integer run = Integer.parseInt((String)props.get("run"));

            submitLoadJob(run, properties.getParentFile());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(PipelinePathForm form)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }


    private void submitLoadJob(int run, File pipelineDir) throws IOException
    {
        ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), getUser(), getViewContext().getActionURL());
        PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
        PipelineJob job = new GalaxyLoadJob(vbi, root, pipelineDir, run);
        PipelineService.get().queueJob(job);
    }
}