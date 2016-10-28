<%
    /*
     * Copyright (c) 2014-2016 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.study.actions.AssayResultsAction" %>
<%@ page import="org.labkey.api.study.assay.AssayProtocolSchema" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingController.STRDiscrepancies" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    STRDiscrepancies model = (STRDiscrepancies)HttpView.currentModel();
    ActionURL reportURL = new ActionURL(GenotypingController.STRDiscrepanciesAssignmentReportAction.class, model.getContainer());
%>
<p>
    <form method="GET" action="<%= h(reportURL)%>">
        <input type="hidden" name="rowId" value="<%= model.getRowId() %>" />
        <input type="checkbox" name="ignoreSubtype" <%= text(model.isIgnoreSubtype() ? "checked" : "") %> onclick="this.form.submit();" /> Ignore haplotype subtype distinctions ('a', 'b', 'c', etc suffixes on the haplotype names)
    </form>
</p>

<table class="labkey-data-region labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Animal Id</td>
        <td class="labkey-column-header">Sites With Discrepancies</td>
    </tr>
    <% if (model.isEmpty()) { %><tr><td colspan="2">No discrepancies were found.</td></tr> <% } %>
    <% for (Map.Entry<String, Set<String>> entry : model.getDiscrepancies().entrySet()) { %>
    <tr>
        <%  ActionURL animalLink = new ActionURL(AssayResultsAction.class, getContainer()).addParameter("rowId", model.getProtocol().getRowId());
            animalLink.addFilter(AssayProtocolSchema.DATA_TABLE_NAME, FieldKey.fromParts("AnimalId","LabAnimalId"), CompareType.EQUAL, entry.getKey());
            String url = animalLink.toString();
        %>
        <td><a href="<%= h(url) %>"><%= h(entry.getKey())%></a></td>
        <td><%= h((entry.getValue() != null) ? StringUtils.join(new ArrayList<>(entry.getValue()), ", ") : "") %></td>
    </tr>
    <% } %>
</table>
