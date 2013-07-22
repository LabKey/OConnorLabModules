/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class OConnorExperimentsController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OConnorExperimentsController.class);

    public OConnorExperimentsController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(AdminPermission.class)
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
            boolean success = true;
            if (success)
            {
                // get table info for the source table
                UserSchema sourceSchema = QueryService.get().getUserSchema(getViewContext().getUser(), getContainer(), "OConnorExport");
                TableInfo sourceTable = sourceSchema.getTable("simple_experiment");
                TableSelector tableSelector = new TableSelector(sourceTable, null, new Sort("ExpNumber"));
                Collection<Map<String, Object>> sourceCollection = tableSelector.getMapCollection();

                // get table info for target table
                UserSchema targetSchema = QueryService.get().getUserSchema(getViewContext().getUser(), getContainer(), "OConnorExperiments");
                TableInfo targetTable = targetSchema.getTable("Experiments");
                QueryUpdateService queryUpdateService = targetTable.getUpdateService();
                BatchValidationException batchErrors = new BatchValidationException();

                // parse the sourceCollection into the target collection
                for (Map<String, Object> databaseMap : sourceCollection)
                {
                    // get the user name
                    User user = UserManager.getUserByDisplayName((String) databaseMap.get("initials"));

                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                    map.put("ExperimentNumber", databaseMap.get("expnumber"));
                    map.put("Name", databaseMap.get("expnumber"));
                    map.put("Description", databaseMap.get("expDescription"));
                    map.put("ExperimentType", databaseMap.get("expType"));
                    String[] parents = new String[0];
                    if (databaseMap.get("expParent") != null)
                    {
                        String parentString = databaseMap.get("expParent").toString();
                        parents = parentString.split("[\\s,;]+");
                    }
                    map.put("ParentExperiments", parents);
                    User effectiveUser = user == null ? getUser() : user;
                    List<Map<String, Object>> updateResult = queryUpdateService.insertRows(getUser(), getContainer(), Collections.singletonList(map), batchErrors, null);
                    if (batchErrors.hasErrors())
                    {
                        throw batchErrors.getLastRowError();
                    }

                    Container workbookContainer = ContainerManager.getForId((String)updateResult.get(0).get("EntityId"));

                    // We don't want these fields to be spoofable through the QueryUpdateService (and hence the Client API),
                    // so preserve the value from the source data manually
                    Date created = (Date)databaseMap.get("created");
                    new SqlExecutor(CoreSchema.getInstance().getSchema()).execute("UPDATE core.containers SET CreatedBy = ?, Created = ? WHERE RowId = ?", effectiveUser.getUserId(), created, workbookContainer.getRowId());

                    // Update the existing Wiki content with the value from the old table, if present
                    String wikiText = (String)databaseMap.get("expcomments");
                    if (wikiText != null)
                    {
                        Path path = new Path("_webdav").append(workbookContainer.getParsedPath()).append("@wiki", "default", "default.html");
                        WebdavResolver resolver = ServiceRegistry.get(WebdavResolver.class);
                        WebdavResource resource = resolver.lookup(path);

                        FileStream.StringFileStream in = new FileStream.StringFileStream(wikiText);
                        try
                        {
                            resource.copyFrom(effectiveUser, in);
                        }
                        finally
                        {
                            in.closeInputStream();
                        }
                    }

                    // Move files
                    Container sourceContainer = getContainer();
                    Container targetContainer = ContainerManager.getForPath(form.getSourceProject());
                    FileContentService fileContentService = ServiceRegistry.get(FileContentService.class);
                    File sourceFile = new File(fileContentService.getFileRoot(sourceContainer).getPath() + "\\@Files", databaseMap.get("expnumber").toString());
                    if (sourceFile.exists())
                    {
                        File targetDir = new File(fileContentService.getFileRoot(targetContainer).getPath() + "\\@Files");
                        File targetFile = new File(targetDir, databaseMap.get("expnumber").toString());
                        FileUtils.moveToDirectory(sourceFile, targetDir, true);
                        fileContentService.fireFileMoveEvent(sourceFile, targetFile, user, getContainer());
                    }
                }

                return true;
            }
            else
            {
                return false;
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

        public ActionURL getSuccessURL(UserForm form)
        {
            UserSchema targetSchema = QueryService.get().getUserSchema(getViewContext().getUser(), getContainer(), "OConnorExperiments");
            return targetSchema.getQueryDefForTable("Experiments").urlFor(QueryAction.executeQuery);
        }
    }

    public static class UserForm extends ReturnUrlForm
    {
        private String _sourceProject;

        public String getSourceProject()
        {
            return _sourceProject;
        }

        public void setSourceProject(String sourceProject)
        {
            _sourceProject = sourceProject;
        }
    }


    /**
     * Use the QueryUpdateService to create a new experiment so the Experiment and Workbook deafults are used
     * then redirect to the newly created experiment begin page.
     */
    @RequiresLogin @CSRF
    @RequiresPermissionClass(InsertPermission.class)
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
            List<Map<String, Object>> result = qus.insertRows(getUser(), getContainer(), Collections.singletonList(row), batchErrors, null);
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

}
