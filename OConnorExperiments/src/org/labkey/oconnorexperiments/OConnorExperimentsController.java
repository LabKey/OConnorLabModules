/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

package org.labkey.oconnorexperiments;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class OConnorExperimentsController extends SpringActionController
{
    public static final String EXPERIMENTS = "Experiments";
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OConnorExperimentsController.class);

    public OConnorExperimentsController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MigrateDataAction extends FormViewAction<UserForm>
    {
        public void validateCommand(UserForm target, Errors errors)
        {
        }

        public ModelAndView getView(UserForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/oconnorexperiments/view/migrateData.jsp");
        }

        public boolean handlePost(UserForm form, BindException errors) throws Exception
        {
            if (form.isFinalMigration())
            {
                Logger.getLogger(OConnorExperimentsController.class).info("Final migration to be performed - file move events will be performed (irreversible).");
            }

            // global containers
            Container sourceContainer = ContainerManager.getForPath(form.getSourceProject());
            Container targetContainer = getContainer();
            FileContentService fileContentService = FileContentService.get();

            // get table info for the source table
            UserSchema sourceSchema = QueryService.get().getUserSchema(getUser(), sourceContainer, "oconnor");
            TableInfo sourceTable = sourceSchema.getTable("simple_experiment");
            TableSelector tableSelector = new TableSelector(sourceTable, null, new Sort("ExpNumber"));
            Collection<Map<String, Object>> sourceCollection = tableSelector.getMapCollection();

            // get table info for target table
            UserSchema targetSchema = QueryService.get().getUserSchema(getUser(), getContainer(), OConnorExperimentsSchema.NAME);
            TableInfo targetTable = targetSchema.getTable(OConnorExperimentsSchema.EXPERIMENTS);
            QueryUpdateService queryUpdateService = targetTable.getUpdateService();
            BatchValidationException batchErrors = new BatchValidationException();

            TableInfo typeTable = targetSchema.getTable(OConnorExperimentsSchema.EXPERIMENT_TYPE);
            QueryUpdateService typeUpdateService = typeTable.getUpdateService();

            int maxExpNumber = 0;

            // parse the sourceCollection into the target collection
            for (Map<String, Object> databaseMap : sourceCollection)
            {
                Map<String, Object> map = new CaseInsensitiveHashMap<>();
                map.put("ExperimentNumber", databaseMap.get("expnumber"));
                int expNumber = (int) databaseMap.get("expnumber");
                if (expNumber > form.getBeginRange() && expNumber < form.getEndRange())
                {
                    if (expNumber > maxExpNumber)
                        maxExpNumber = expNumber;
                    map.put("Name", databaseMap.get("expnumber"));
                    map.put("Description", databaseMap.get("expDescription"));

                    String currentType = (String) databaseMap.get("expType");
                    Integer targetType = null;
                    if (currentType != null)
                    {
                        TableSelector typeSelector = new TableSelector(typeTable, Collections.singleton("RowId"), new SimpleFilter(FieldKey.fromParts("Name"), currentType), null);
                        targetType = typeSelector.getObject(Integer.class);
                        if (targetType == null)
                        {
                            Map<String, Object> newType = new CaseInsensitiveHashMap<>();
                            newType.put("Name", currentType);
                            newType.put("Enabled", true);
                            List<Map<String, Object>> newTypes = typeUpdateService.insertRows(getUser(), getContainer(), Collections.singletonList(newType), new BatchValidationException(), null, null);
                            targetType = (Integer) newTypes.get(0).get("RowId");
                        }
                    }

                    map.put("ExperimentTypeId", targetType);
                    //map.put("Modified", databaseMap.get("created"));

                    // get the user name
                    User effectiveUser;
                    if (databaseMap.get("initials") == null)
                    {
                        effectiveUser = getUser();
                    }
                    else
                    {
                        User user = UserManager.getUserByDisplayName((String) databaseMap.get("initials"));
                        if (user == null)
                        {
                            Logger.getLogger(OConnorExperimentsController.class).warn("User '" + databaseMap.get("initials") + "' not found for experiment " + databaseMap.get("expnumber"));
                            effectiveUser = getUser();
                        }
                        else
                        {
                            effectiveUser = user;
                        }
                    }

                    databaseMap.put("EffectiveUser", effectiveUser);
                    Logger.getLogger(OConnorExperimentsController.class).info("Insert on experiment " + databaseMap.get("expnumber"));
                    List<Map<String, Object>> updateResult;
                    try
                    {
                        updateResult = queryUpdateService.insertRows(getUser(), getContainer(), Collections.singletonList(map), batchErrors, null, null);
                    }
                    catch (Exception e)
                    {
                        // log the error to the logfile and continue
                        Logger.getLogger(OConnorExperimentsController.class).warn("Error inserting expNumber " + expNumber + " with exception " + e.getMessage());
                        continue;
                    }
                    if (batchErrors.hasErrors())
                    {
                        // throw batchErrors.getLastRowError();
                        Logger.getLogger(OConnorExperimentsController.class).warn("Error inserting expNumber " + expNumber);
                    }

                    Container workbookContainer = ContainerManager.getForId((String)updateResult.get(0).get("EntityId"));
                    databaseMap.put("ContainerObj", workbookContainer);
                    databaseMap.put("ContainerStr", updateResult.get(0).get("EntityId"));

                    // We don't want these fields to be spoofable through the QueryUpdateService (and hence the Client API),
                    // so preserve the value from the source data manually
                    Date created = (Date)databaseMap.get("created");
                    new SqlExecutor(CoreSchema.getInstance().getSchema()).execute("UPDATE core.containers SET CreatedBy = ?, Created = ? WHERE RowId = ?", effectiveUser.getUserId(), created, workbookContainer.getRowId());

                    // Move files
                    File sourceFile = new File(fileContentService.getFileRoot(sourceContainer).getPath() + File.separator + "@files", databaseMap.get("expnumber").toString());
                    File targetDir = new File(fileContentService.getFileRoot(targetContainer).getPath() + File.separator + databaseMap.get("expnumber").toString() + File.separator + "@files");
                    Logger.getLogger(OConnorExperimentsController.class).info("Copy from file '" + sourceFile.toString() + "' to directory '" + targetDir.toString() +"'" );
                    if (sourceFile.exists())
                    {
                        FileUtils.copyDirectory(sourceFile, targetDir);
                        // only fire the File Move Event if this is a final migration - it is difficult to reverse
                        if (form.isFinalMigration())
                        {
                            fileContentService.fireFileMoveEvent(sourceFile, targetDir, effectiveUser, getContainer());
                        }
                    }
                }
            }

            //
            // 2nd pass - update all ParentExperiment fields
            //
            for (Map<String, Object> databaseMap : sourceCollection)
            {
                int expNumber = (int) databaseMap.get("expnumber");
                if (expNumber > form.getBeginRange() && expNumber < form.getEndRange())
                {
                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                    map.put("container", databaseMap.get("ContainerStr"));

                    String[] parents = new String[0];
                    ArrayList<String> parentsEntityId = new ArrayList<>();
                    if (databaseMap.get("expParent") != null)
                    {
                        String parentString = databaseMap.get("expParent").toString();
                        parents = parentString.split("[\\s,;&]+");
                        // get Container for SortOrder
                        for ( int i =0; i< parents.length; i++)
                        {
                            if ( ! parents[i].equalsIgnoreCase("and"))
                            {
                                Container child = targetContainer.getChild(parents[i]);
                                if (child != null)
                                {
                                    parentsEntityId.add( child.getEntityId().toString() );
                                }
                                else
                                {
                                    Logger.getLogger(OConnorExperimentsController.class).warn("child container not found: " + parents[i] + " for experiment " + databaseMap.get("expnumber") + " with username " + databaseMap.get("initials"));
                                }
                            }
                        }
                        if (parentsEntityId.size() > 0)
                        {
                            map.put("ParentExperiments", parentsEntityId.toArray(new String[parentsEntityId.size()]));

                            // workaround, pass user, container - databaseMap.get("Container"), singleton list
                            Logger.getLogger(OConnorExperimentsController.class).info("Update rows on experiment " + databaseMap.get("expnumber"));
                            try
                            {
                                queryUpdateService.updateRows(getUser(), targetContainer, Collections.singletonList(map), null, null, null);
                            }
                            catch (Exception e)
                            {
                                // log the error to the logfile and continue
                                Logger.getLogger(OConnorExperimentsController.class).warn("Error updating parent experiments for experiment number " + expNumber + " with exception " + e.getMessage());
                                continue;
                            }

                        }
                    }
                }
            }

            //
            // 3rd pass update the wiki, done seperately so that the cache is not modified
            //
            for (Map<String, Object> databaseMap : sourceCollection)
            {
                int expNumber = (int) databaseMap.get("expnumber");
                if (expNumber > form.getBeginRange() && expNumber < form.getEndRange())
                {

                    // Update the existing Wiki content with the value from the old table, if present
                    String wikiText = (String)databaseMap.get("expcomments");
                    if (wikiText != null)
                    {
                        User user = UserManager.getUserByDisplayName((String) databaseMap.get("initials"));
                        User effectiveUser = user == null ? getUser() : user;
                        Container workbookContainer = targetContainer.getChild(databaseMap.get("expnumber").toString());
                        if (workbookContainer == null)
                        {
                            Logger.getLogger(OConnorExperimentsController.class).warn("Updating wiki, container not found: " + databaseMap.get("expnumber"));
                        }
                        else
                        {
                            Path path = new Path("_webdav").append(workbookContainer.getParsedPath()).append("@wiki", "default", "default.html");
                            WebdavResolver resolver = ServiceRegistry.get(WebdavResolver.class);
                            WebdavResource resource = resolver.lookup(path);

                            FileStream.StringFileStream in = new FileStream.StringFileStream(wikiText);
                            try
                            {
                                resource.copyFrom(effectiveUser, in);
                            }
                            catch (Exception e)
                            {
                                // log the error to the logfile and continue
                                Logger.getLogger(OConnorExperimentsController.class).warn("Error wiki for experiment number " + expNumber + " with exception " + e.getMessage());
                                continue;
                            }
                            finally
                            {
                                in.closeInputStream();
                            }
                            Logger.getLogger(OConnorExperimentsController.class).info("Inserting wiki for experiment " + databaseMap.get("expnumber"));
                        }
                    }
                }
            }

            // we need to leave the target location set up correctly so that the next experiment that's created gets the next value in the sequence
            DbSequenceManager.get(targetContainer, ContainerManager.WORKBOOK_DBSEQUENCE_NAME).ensureMinimum(maxExpNumber);

            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

        public ActionURL getSuccessURL(UserForm form)
        {
            UserSchema targetSchema = QueryService.get().getUserSchema(getUser(), getContainer(), OConnorExperimentsSchema.NAME);
            return targetSchema.getQueryDefForTable(OConnorExperimentsSchema.EXPERIMENTS).urlFor(QueryAction.executeQuery);
        }
    }

    public static class UserForm extends ReturnUrlForm
    {
        private String _sourceProject;
        private int _beginRange;
        private int _endRange;
        private boolean _finalMigration;

        public String getSourceProject()
        {
            return _sourceProject;
        }

        public void setSourceProject(String sourceProject)
        {
            _sourceProject = sourceProject;
        }

        public int getBeginRange()
        {
            return _beginRange;
        }

        public void setBeginRange(int beginRange)
        {
            _beginRange = beginRange;
        }

        public int getEndRange()
        {
            return _endRange;
        }

        public void setEndRange(int endRange)
        {
            _endRange = endRange;
        }

        public boolean isFinalMigration()
        {
            return _finalMigration;
        }

        public void setFinalMigration(boolean finalMigration)
        {
            _finalMigration = finalMigration;
        }
    }


    /**
     * Use the QueryUpdateService to create a new experiment so the Experiment and Workbook deafults are used
     * then redirect to the newly created experiment begin page.
     */
    @RequiresLogin @CSRF
    @RequiresPermission(InsertPermission.class)
    public class InsertExperimentAction extends RedirectAction
    {
        private Container newExperiment;

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(newExperiment);
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), OConnorExperimentsUserSchema.NAME);
            TableInfo table = schema.getTable(OConnorExperimentsUserSchema.Table.Experiments.name());
            QueryUpdateService qus = table.getUpdateService();

            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("Container", getContainer().getEntityId());

            BatchValidationException batchErrors = new BatchValidationException();
            List<Map<String, Object>> result = qus.insertRows(getUser(), getContainer(), Collections.singletonList(row), batchErrors, null, null);
            if (batchErrors.hasErrors())
                throw batchErrors;

            if (result != null && !result.isEmpty())
            {
                String entityId = (String)result.get(0).get("Container");
                newExperiment = ContainerManager.getForId(entityId);
                return true;
            }

            return false;
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }
    }

    @RequiresLogin @CSRF
    @RequiresPermission(ReadPermission.class)
    public class GetExperimentAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), OConnorExperimentsUserSchema.NAME);
            TableInfo table = schema.getTable(OConnorExperimentsUserSchema.Table.Experiments.name());
            QueryUpdateService qus = table.getUpdateService();

            List<Map<String, Object>> pks = Collections.singletonList(Collections.singletonMap("Container", (Object)getContainer().getId()));
            List<Map<String, Object>> result = qus.getRows(getUser(), getContainer(), pks);

            ApiSimpleResponse resp = new ApiSimpleResponse();
            if (result != null && !result.isEmpty())
            {
                Map<String, Object> exp = result.get(0);

                resp.put("success", true);
                resp.put("experiment", exp);
            }
            else
            {
                resp.put("success", false);
            }

            return resp;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class HistoryAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setNoIndex();
            getPageConfig().setNoFollow();
            return new JspView("/org/labkey/oconnorexperiments/view/history.jsp", null, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Experiment History");
        }
    }

    public static class LookupWorkbookForm
    {
        private String _id;

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class LookupWorkbookAction extends SimpleViewAction<LookupWorkbookForm>
    {
        public ModelAndView getView(LookupWorkbookForm form, BindException errors) throws Exception
        {
            if (null == form.getId())
                throw new NotFoundException("You must supply the id of the workbook you wish to find.");

            try
            {
                int id = Integer.parseInt(form.getId());
                //try to lookup based on id
                Container container = ContainerManager.getForRowId(id);
                //if found, ensure it's a descendant of the current container, and redirect
                if (null != container && container.isDescendant(getContainer()))
                    throw new RedirectException(container.getStartURL(getUser()));
            }
            catch (NumberFormatException e) { /* continue on with other approaches */ }

            //next try to lookup based on name
            Container container = getContainer().findDescendant(form.getId());
            if (null != container)
                throw new RedirectException(container.getStartURL(getUser()));

            //otherwise, return a workbooks list with the search view
            HtmlView message = new HtmlView("<p class='labkey-error'>Could not find a workbook with id '" + form.getId() + "' in this folder or subfolders. Try searching or entering a different id.</p>");
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), OConnorExperimentsSchema.NAME);
            org.labkey.oconnorexperiments.WorkbookQueryView wbqview = new WorkbookQueryView(getViewContext(), schema);
            return new VBox(message, new JspView<>("/org/labkey/oconnorexperiments/view/workbookSearch.jsp", new WorkbookSearchBean(wbqview, null)), wbqview);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            //if a view ends up getting rendered, the workbook id was not found
            return root.addChild(OConnorExperimentsSchema.EXPERIMENTS);
        }
    }
}
