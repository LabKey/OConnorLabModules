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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingManager" %>
<%@ page import="org.labkey.genotyping.sequences.SequenceManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext ctx = getViewContext();
    Container c = ctx.getContainer();
    User user = ctx.getUser();
    ActionURL submitURL = urlProvider(PipelineUrls.class).urlBrowse(c, null);
    ActionURL statusURL = urlProvider(PipelineUrls.class).urlBegin(c);

    String containerType = c.isProject() ? "Project" : "Folder";
    int runCount = GenotypingManager.get().getRunCount(c);
    int analysisCount = GenotypingManager.get().getAnalysisCount(c, null);
    long sequencesCount = SequenceManager.get().getCurrentSequenceCount(c);
%>
<table>
    <tr><td colspan="3" class="labkey-announcement-title"><span><%=containerType%> Contents</span></td></tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr><td width="10"></td><td>&bull;&nbsp;<%=Formats.commaf0.format(runCount)%> run<%=(1 == runCount ? "" : "s")%></td><td><% if (runCount > 0) { out.print(textLink("View Runs", GenotypingController.getRunsURL(c))); } else { %>&nbsp;<% } %></td></tr>
    <tr><td></td><td>&bull;&nbsp;<%=Formats.commaf0.format(analysisCount)%> analys<%=(1 == analysisCount ? "is" : "es")%></td><td><% if (analysisCount > 0) { out.print(textLink("View Analyses", GenotypingController.getAnalysesURL(c))); } else { %>&nbsp;<% } %></td></tr>
    <tr><td></td><td>&bull;&nbsp;<%=Formats.commaf0.format(sequencesCount)%> reference sequence<%=(1 == sequencesCount ? "" : "s")%>&nbsp;&nbsp;</td><td><% if (sequencesCount > 0) { out.print(textLink("View Reference Sequences", GenotypingController.getSequencesURL(c, null))); } else { %>&nbsp;<% } %></td></tr>

    <tr><td colspan="3" class="labkey-announcement-title"><span>Tasks</span></td></tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr><%
    if (c.hasPermission(user, InsertPermission.class))
    {
    %>
    <tr><td></td><td><%=textLink("Import Run", submitURL)%></td></tr><%
    }
    %>
    <tr><td></td><td><%=textLink("View Pipeline Status", statusURL)%></td></tr>

    <tr><td colspan="3" class="labkey-announcement-title"><span>Settings</span></td></tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr><td></td><td><%=textLink("My Settings", GenotypingController.getMySettingsURL(c, ctx.getActionURL()))%></td></tr><%
    if (c.hasPermission(user, AdminPermission.class))
    {
    %>
    <tr><td></td><td><%=textLink("Admin", GenotypingController.getAdminURL(c, ctx.getActionURL()))%></td></tr><%
    }
    %>
</table>