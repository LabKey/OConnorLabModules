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
<%@ page import="org.labkey.api.query.CustomView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    GenotypingController.SubmitAnalysisBean bean = (GenotypingController.SubmitAnalysisBean)getModelBean();
%>
<form <%=formAction(GenotypingController.ImportReadsAction.class, Method.Post)%>>
    <table id="analysesTable">
        <%=formatMissedErrorsInTable("form", 2)%>
        <tr><td colspan="2">Run Information</td></tr>
        <tr><td>Associated Run:</td><td><select name="run"><%
            for (Integer run : bean.getRuns())
            { %><option><%=h(run)%></option>
             <%
            }
        %></select></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td colspan="2"><%=generateButton("Add New MHC Analysis", "", "addNewAnalysis();return false;")%> <%=PageFlowUtil.generateButton("Delete Last MHC Analysis", "#", "return deleteLastAnalysis();", "id=\"delete\"")%></td></tr>
        <tr><td>
            <input type="hidden" name="readyToSubmit" value="1">
            <input type="hidden" name="readsPath" value="<%=h(bean.getReadsPath())%>">
        </td></tr>
        <tr><td><%=generateSubmitButton("Submit")%></td></tr>
    </table>
</form>
<script type="text/javascript">
    var rowsPerAnalysis = 4;
    var analysisCount = 0;

    updateDeleteButton();

    function addNewAnalysis()
    {
        analysisCount++;
        var table = document.getElementById('analysesTable');
        var currentRow = (analysisCount - 1) * rowsPerAnalysis + 3;

        var newRow = table.insertRow(currentRow++);
        var titleCell = newRow.insertCell(0);
        titleCell.innerHTML = '<b>MHC Analysis #' + analysisCount + '</b>';

        newRow = table.insertRow(currentRow++);
        newRow.insertCell(0).innerHTML = 'Reference Sequences:';
        newRow.insertCell(1).innerHTML = '<select name="sequencesViews"><%
            for (CustomView view : bean.getSequencesViews())
            {
                String name = view.getName();
            %><option><%=h(null == name ? GenotypingController.DEFAULT_VIEW_PLACEHOLDER : name)%><\/option><%
            }
        %><\/select>';

        newRow = table.insertRow(currentRow++);
        newRow.insertCell(0).innerHTML = 'Description:';
        newRow.insertCell(1).innerHTML = '<textarea cols="40" rows="5" name="descriptions"/>';

        table.insertRow(currentRow++).insertCell(0).innerHTML = '&nbsp;';

        updateDeleteButton();
    }

    function deleteLastAnalysis()
    {
        if (analysisCount > 0)
        {
            var table = document.getElementById('analysesTable');
            var currentRow = (analysisCount - 1) * rowsPerAnalysis + 3

            for (var i = 0; i < rowsPerAnalysis; i++)
                table.deleteRow(currentRow);

            analysisCount--;
            updateDeleteButton();
        }

        return false;
    }

    // Just gray the button; deleteLastAnalysis() will check analysisCount > 0 before deleting.
    function updateDeleteButton()
    {
        var btn = document.getElementById('delete');

        if (analysisCount > 0)
            btn.className = "labkey-button";
        else
            btn.className = "labkey-disabled-button";
    }
</script>
