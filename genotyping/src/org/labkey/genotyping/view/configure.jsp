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
<%@ page import="org.labkey.genotyping.GenotypingController"%>
<%@ page import="org.labkey.genotyping.GenotypingFolderSettings" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    GenotypingController.AdminForm form = (GenotypingController.AdminForm)getModelBean();
%>
<script type="text/javascript">
//    Ext.QuickTips.init();
//    window.onbeforeunload = LABKEY.beforeunload();    // TODO: Check for dirty
    LABKEY.requiresScript("viewPicker.js");
    var queries = {};

    function update(query, hiddenId, divId)
    {
        document.getElementById(hiddenId).value = query;
        document.getElementById(divId).innerHTML = getDisplayValue(query);
        queries[hiddenId] = query;
    }

    function updateSequencesQuery(query)
    {
        update(query, 'sequencesQuery', 'sequencesQueryDiv');
    }

    function updateRunsQuery(query)
    {
        update(query, 'runsQuery', 'runsQueryDiv');
    }

    function updateSamplesQuery(query)
    {
        update(query, 'samplesQuery', 'samplesQueryDiv');
    }

    function getDisplayValue(query)
    {
        var html;

        if (query)
        {
            var parts = query.split('<%=GenotypingFolderSettings.SEPARATOR%>');
            html = parts[0] + '.' + parts[1] + '.' + parts[2];
        }
        else
        {
            html = '&lt;none&gt;';
        }

        return html;
    }

    function includeSchema(schemaName)
    {
        return 'genotyping' != schemaName;
    }

    Ext.onReady(function()
    {
        updateSequencesQuery(<%=q(form.getSequencesQuery())%>);
        updateRunsQuery(<%=q(form.getRunsQuery())%>);
        updateSamplesQuery(<%=q(form.getSamplesQuery())%>);
    });
</script>

<form <%=formAction(GenotypingController.AdminAction.class, Method.Post)%>>
    <table>
        <%=formatMissedErrorsInTable("form", 3)%>
        <%
            if (null != form.getMessage())
            {
        %>
        <tr><td colspan="3"><span style="color:green;"><%=h(form.getMessage())%></span></td></tr>
        <tr><td colspan=3>&nbsp;</td></tr>
        <%  } %>
        <tr><td><b>Configure Galaxy</b></td></tr>
        <tr><td>Galaxy server home page URL</td><td><input size="40" name="galaxyURL" value="<%=h(form.getGalaxyURL())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td><b>Configure Genotyping Queries</b></td></tr>
        <tr>
            <td>External source of DNA reference sequences&nbsp;&nbsp;</td>
            <td><div id="sequencesQueryDiv"></div></td>
            <td>
                <%=textLink("configure", "#", "chooseView('Choose DNA reference sequences query', 'Select a query that represents an external source of DNA reference sequences.  This query will be used periodically to replace the reference sequences that link to results.', '" + GenotypingFolderSettings.SEPARATOR + "', function(query){updateSequencesQuery(query);}, queries['sequencesQuery'], includeSchema);return false;", "id")%>
                <input type="hidden" name="sequencesQuery" id="sequencesQuery">
            </td>
        </tr>
        <tr>
            <td>Runs</td>
            <td><div id="runsQueryDiv"></div></td>
            <td>
                <%=textLink("configure", "#", "chooseView('Choose runs query', 'Select a query where runs are stored.', '" + GenotypingFolderSettings.SEPARATOR + "', function(query){updateRunsQuery(query);}, queries['runsQuery'], includeSchema);return false;", "id")%>
                <input type="hidden" name="runsQuery" id="runsQuery">
            </td>
        </tr>
        <tr>
            <td>Samples</td>
            <td><div id="samplesQueryDiv"></div></td>
            <td>
                <%=textLink("configure", "#", "chooseView('Choose samples query', 'Select a query that provides a list of samples.  This query is filtered by the library number specified in the run to produce the sample.txt file.', '" + GenotypingFolderSettings.SEPARATOR + "', function(query){updateSamplesQuery(query);}, queries['samplesQuery'], includeSchema);return false;", "id")%>
                <input type="hidden" name="samplesQuery" id="samplesQuery">
            </td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td><%=generateSubmitButton("Submit")%> <%=generateButton("Done", form.getReturnURLHelper())%><%=generateReturnUrlFormField(form)%></td></tr>
    </table>
</form>
