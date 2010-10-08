<%
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
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingSchema" %>
<%@ page import="org.labkey.genotyping.QueryHelper" %>
<%@ page import="org.labkey.genotyping.GenotypingManager" %>
<%@ page import="org.labkey.genotyping.GenotypingFolderSettings" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext ctx = getViewContext();
    User user = ctx.getUser();
    Container c = ctx.getContainer();
    GenotypingSchema gs = GenotypingSchema.get();

    GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);

    ActionURL runsURL = null;
    String runsQuery = settings.getRunsQuery();

    if (null != runsQuery)
    {
        QueryHelper qHelper = new QueryHelper(c, user, runsQuery);
        runsURL = qHelper.getQueryGridURL();
    }

    ActionURL sequencesURL = QueryService.get().urlFor(user, c, QueryAction.executeQuery, gs.getSchemaName(), gs.getSequencesTable().getName());
    ActionURL submitURL = urlProvider(PipelineUrls.class).urlBrowse(c, null);
    ActionURL statusURL = urlProvider(PipelineUrls.class).urlBegin(c);
%>
<p><a href="<%=h(sequencesURL)%>">Reference Sequences</a></p><%
    if (null != runsURL)
    {
%>
<p><a href="<%=h(runsURL)%>">Runs</a></p><%
    }
%>
<p><a href="<%=h(GenotypingController.getMatchesURL(c))%>">Matches</a></p>
<p><a href="<%=h(submitURL)%>">Import Run</a></p>
<p><a href="<%=h(statusURL)%>">Pipeline Status</a></p>
<p><a href="<%=h(GenotypingController.getMySettingsURL(c, ctx.getActionURL()))%>">My Settings</a></p>
<p><a href="<%=h(GenotypingController.getAdminURL(c, ctx.getActionURL()))%>">Admin</a></p>

