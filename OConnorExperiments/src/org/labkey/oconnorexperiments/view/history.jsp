<%
/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.audit.AuditLogService"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QuerySettings" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.wiki.WikiService" %>
<%@ page import="org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    ViewContext context = getViewContext();
    User user = getUser();
    Container c = getContainer();
%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi");
    }
%>
<labkey:scriptDependency/>

<h3>Experiment History</h3>
<%
    {
        UserSchema schema = AuditLogService.get().createSchema(user, c);
        QuerySettings settings = schema.getSettings(context, "queryUpdates");
        settings.setQueryName("QueryUpdateAuditEvent");
        settings.setAllowChooseQuery(false);
        settings.setAllowChooseView(false);

        QueryView view = schema.createView(context, settings, null);

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("SchemaName"), OConnorExperimentsUserSchema.NAME);
        filter.addCondition(FieldKey.fromParts("QueryName"), OConnorExperimentsUserSchema.Table.Experiments.name());
        filter.addCondition(FieldKey.fromParts("ContainerId"), c.getEntityId());
        settings.getBaseFilter().addAllClauses(filter);

        view.setShowConfiguredButtons(false);
        view.setShowDetailsColumn(true);
        view.setShowDeleteButton(false);
        view.setAllowableContainerFilterTypes(ContainerFilter.Type.Current);

        include(view, out);
    }
%>

<h3>File History</h3>
<%
    {
        UserSchema schema = AuditLogService.get().createSchema(user, c);
        QuerySettings settings = schema.getSettings(context, "fileUpdates");
        settings.setQueryName("FileSystem");
        settings.setAllowChooseQuery(false);
        settings.setAllowChooseView(false);

        QueryView view = schema.createView(context, settings, null);

        view.setShowConfiguredButtons(false);
        view.setShowDetailsColumn(true);
        view.setShowDeleteButton(false);
        view.setAllowableContainerFilterTypes(ContainerFilter.Type.Current);

        include(view, out);
    }
%>

<h3>Notes History</h3>
<%
    {
        WebPartView view = ServiceRegistry.get(WikiService.class).getHistoryView(c, "default");
        if (view != null)
            include(view, out);
    }
%>

