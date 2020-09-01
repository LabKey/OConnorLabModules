<%
/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.NotFoundException" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingManager" %>
<%@ page import="org.labkey.genotyping.GenotypingQuerySchema" %>
<%@ page import="org.labkey.genotyping.ValidatingGenotypingFolderSettings" %>
<%@ page import="org.labkey.genotyping.sequences.SequenceManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    User user = getUser();
    ActionURL submitURL = urlProvider(PipelineUrls.class).urlBrowse(c, null);
    ActionURL statusURL = urlProvider(PipelineUrls.class).urlBegin(c);

    ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(c,  user, "managing sample information");
    ActionURL samplesURL = new ActionURL("query", QueryAction.executeQuery.toString(), c);
    samplesURL.addParameter("schemaName", "genotyping");
    samplesURL.addParameter("query.queryName", GenotypingQuerySchema.TableType.Samples.toString());

    ActionURL runMetadataURL = new ActionURL("query", QueryAction.executeQuery.toString(), c);
    runMetadataURL.addParameter("schemaName", "genotyping");
    runMetadataURL.addParameter("query.queryName", GenotypingQuerySchema.TableType.RunMetadata.toString());

    String containerType = c.isProject() ? "Project" : "Folder";
    int runCount = GenotypingManager.get().getRunCount(c);
    int analysisCount = GenotypingManager.get().getAnalysisCount(c, null);
    long sequencesCount = SequenceManager.get().getCurrentSequenceCount(c, user);
%>
<table>
    <tr><td colspan="3" class="labkey-announcement-title"><span><%=h(containerType)%> Contents</span></td></tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr><td width="10"></td><td>&bull;&nbsp;<%=h(Formats.commaf0.format(runCount))%> run<%=h(1 == runCount ? "" : "s")%></td><td><% if (runCount > 0) { out.print(link("View Runs", GenotypingController.getRunsURL(c))); } else { %>&nbsp;<% } %></td></tr>
    <tr><td></td><td>&bull;&nbsp;<%=h(Formats.commaf0.format(analysisCount))%> analys<%=h(1 == analysisCount ? "is" : "es")%></td><td><% if (analysisCount > 0) { out.print(link("View Analyses", GenotypingController.getAnalysesURL(c))); } else { %>&nbsp;<% } %></td></tr>
    <tr><td></td><td>&bull;&nbsp;<%=h(Formats.commaf0.format(sequencesCount))%> reference sequence<%=h(1 == sequencesCount ? "" : "s")%>&nbsp;&nbsp;</td><td><% if (sequencesCount > 0) { out.print(link("View Reference Sequences", GenotypingController.getSequencesURL(c, null))); } else { %>&nbsp;<% } %></td></tr>

    <tr><td colspan="3" class="labkey-announcement-title"><span>Tasks</span></td></tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr><%
    if (c.hasPermission(user, InsertPermission.class))
    {
    %>
    <tr><td></td><td><%=link("Import Run", submitURL)%></td></tr><%
    }
    %>
    <tr><td></td><td><%=link("View Pipeline Status", statusURL)%></td></tr>

    <tr><td colspan="3" class="labkey-announcement-title"><span>Manage Sample Information</span></td></tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <%--<tr><td></td><td><%=textLink("MIDs", submitURL)%></td></tr>--%>
<%--TODO--%>
    <tr><td></td><td>
    <%
        try
        {
            String runsQuery = settings.getRunsQuery();
            %><%=link("Run Metadata", runMetadataURL)%><%
        }
        catch(NotFoundException e)
        {
            %><%=h(e.getMessage())%><%
        }
    %>
    </td></tr>
    <tr><td></td><td>
    <%
        try
        {
            String samplesQuery = settings.getSamplesQuery();
            %><%=link("Samples", samplesURL)%><%
        }
        catch(NotFoundException e)
        {
            %><%=h(e.getMessage())%><%
        }
    %>
    </td></tr>

    <tr><td colspan="3" class="labkey-announcement-title"><span>Settings</span></td></tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr><td></td><td><%=link("My Settings", GenotypingController.getMySettingsURL(c, getActionURL()))%></td></tr><%
    if (c.hasPermission(user, AdminPermission.class))
    {
    %>
    <tr><td></td><td><%=link("Admin", GenotypingController.getAdminURL(c, getActionURL())).id("adminSettings")%></td></tr><%
    }
    %>
</table>