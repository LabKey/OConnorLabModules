/*
 * Copyright (c) 2013-2026 LabKey Corporation
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

import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.DOM;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;
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
    public static class BeginAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return HttpView.redirect(PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    /**
     * Use the QueryUpdateService to create a new experiment so the Experiment and Workbook defaults are used
     * then redirect to the newly created experiment begin page.
     */
    @RequiresLogin
    @RequiresPermission(InsertPermission.class)
    public static class InsertExperimentAction extends FormHandlerAction<Object>
    {
        private Container newExperiment;

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(newExperiment);
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
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
                String entityId = (String)result.getFirst().get("Container");
                newExperiment = ContainerManager.getForId(entityId);
                return true;
            }
            return false;
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public static class GetExperimentAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), OConnorExperimentsUserSchema.NAME);
            TableInfo table = schema.getTable(OConnorExperimentsUserSchema.Table.Experiments.name());
            QueryUpdateService qus = table.getUpdateService();

            List<Map<String, Object>> pks = Collections.singletonList(Collections.singletonMap("Container", getContainer().getId()));
            List<Map<String, Object>> result = qus.getRows(getUser(), getContainer(), pks);

            ApiSimpleResponse resp = new ApiSimpleResponse();
            if (result != null && !result.isEmpty())
            {
                Map<String, Object> exp = result.getFirst();

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
    public static class HistoryAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            getPageConfig().setNoIndex();
            getPageConfig().setNoFollow();
            return new JspView<>("/org/labkey/oconnorexperiments/view/history.jsp", null, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Experiment History");
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
    public static class LookupWorkbookAction extends SimpleViewAction<LookupWorkbookForm>
    {
        @Override
        public ModelAndView getView(LookupWorkbookForm form, BindException errors)
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
            HtmlView message = new HtmlView(DOM.P(DOM.cl( "labkey-error"), "Could not find a workbook with id '" + form.getId() + "' in this folder or subfolders. Try searching or entering a different id."));
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), OConnorExperimentsSchema.NAME);
            org.labkey.oconnorexperiments.WorkbookQueryView wbqview = new WorkbookQueryView(getViewContext(), schema);
            return new VBox(message, new JspView<>("/org/labkey/oconnorexperiments/view/workbookSearch.jsp", new WorkbookSearchBean(wbqview, null)), wbqview);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            //if a view ends up getting rendered, the workbook id was not found
            root.addChild(OConnorExperimentsSchema.EXPERIMENTS);
        }
    }
}
