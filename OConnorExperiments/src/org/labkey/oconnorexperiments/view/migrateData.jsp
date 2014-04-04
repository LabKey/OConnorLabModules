<%
/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.oconnorexperiments.OConnorExperimentsController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<form <%=formAction(OConnorExperimentsController.MigrateDataAction.class, Method.Post)%>><labkey:csrf/>
<h3>OConnor Experiments to Workbooks data migration</h3>
<table><%=formatMissedErrorsInTable("form", 2)%>
    <tr>
        <td>Source Project Name:</td>
        <td><input type="text" name="sourceProject" id="sourceProject" size="50" value=""></td>
    </tr>
    <tr>
        <td>Experiment Number Range:</td>
        <td><input type="text" name="beginRange" id="beginRange" size="10" value=""> - <input type="text" name="endRange" id="endRange" size="10" value=""></td>
    </tr>
    <tr>
        <td>Final Migration:</td>
        <td><input type="checkbox" name="finalMigration" id="finalMigration"></td>
    </tr>
    <tr>
        <td colspan=2><%= button("Submit").submit(true) %></td>
    </tr>
</table>
</form>
