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
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingFolderSettings" %>
<%@ page import="org.labkey.genotyping.GenotypingManager" %>
<%@ page import="org.labkey.genotyping.QueryHelper" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainer();
    User user = getViewContext().getUser();
    GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
    QueryHelper qHelper = new QueryHelper(c, user, settings.getSequencesQuery());
%>
<form <%=formAction(GenotypingController.LoadSequencesAction.class, Method.Post)%>>
    <table>
        <tr>
            <td>
                This will load a new dictionary of DNA sequences from the source query "<%=h(qHelper)%>" and set it as the
                dictionary of reference sequences to use for future genotyping analyses. Existing analysis runs will continue to
                link to the sequences used at the time of their analysis.
            </td>
        </tr>
        <tr><td><%=generateSubmitButton("Load Sequences")%></td></tr>
    </table>
</form>
