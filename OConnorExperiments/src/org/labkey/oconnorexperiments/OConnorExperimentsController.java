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

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.UserSchemaAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

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
            return new JspView("/org/labkey/oconnorexperiments/view/hello.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    /*
    @RequiresPermissionClass(ReadPermission.class)
    public class ExperimentDetailsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            if (!c.isWorkbook())
                throw new NotFoundException("Current container is not a workbook");

            return new JspView<Object>("/org/labkey/oconnorexperiments/view/details.jsp", errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Experiments");
        }
    }
    */

//    @RequiresPermissionClass(UpdatePermission.class)
//    public class UpdateExperimentAction extends FormViewAction
//    {
//
//    }

    /**
     * I couldn't figure out how to add a hidden input form field for the 'folderType' column
     * using the generic query insert view so this action is a workaround that
     * will add the hidden form field to the DataRegion.
     */
    @RequiresLogin @CSRF
    @RequiresPermissionClass(InsertPermission.class)
    public class InsertExperimentAction extends UserSchemaAction
    {
        @Override
        protected QueryForm createQueryForm(ViewContext context)
        {
            QueryForm form = super.createQueryForm(context);
            form.setSchemaName(OConnorExperimentsUserSchema.NAME);
            form.setQueryName(OConnorExperimentsUserSchema.Table.Experiments.name());
            return form;
        }

        @Override
        public ModelAndView getView(QueryUpdateForm form, boolean reshow, BindException errors) throws Exception
        {
            InsertView view = new InsertView(form, errors);
            view.getDataRegion().setButtonBar(createSubmitCancelButtonBar(form));
            view.getDataRegion().addHiddenFormField(QueryUpdateForm.PREFIX + "folderType", OConnorExperimentFolderType.NAME);
            return view;
        }

        @Override
        public boolean handlePost(QueryUpdateForm form, BindException errors) throws Exception
        {
            doInsertUpdate(form, errors, true);
            return 0 == errors.getErrorCount();
        }
    }

//    @RequiresPermissionClass(DeletePermission.class)
//    public class DeleteAction extends ConfirmAction
//    {
//
//    }

}
