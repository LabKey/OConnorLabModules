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

import org.labkey.api.action.RedirectAction;
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
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;
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
