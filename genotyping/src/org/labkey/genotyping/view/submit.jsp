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
    GenotypingController.SubmitToGalaxyBean bean = (GenotypingController.SubmitToGalaxyBean)getModelBean();
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
        <tr><td colspan="2"><%=generateButton("Add New MHC Analysis", "", "addNewAnalysis();return false;")%> <%=PageFlowUtil.generateButton("Delete Last MHC Analysis", "", "", "id=\"delete\"")%></td></tr>
        <tr><td>
            <input type="hidden" name="readyToSubmit" value="1">
            <input type="hidden" name="readsPath" value="<%=h(bean.getReadsPath())%>">
        </td></tr>
        <tr><td><%=generateSubmitButton("Submit")%></td></tr>
    </table>
</form>
<script type="text/javascript">
    var analysisCount = -1;

    updateDeleteButton();

    function addNewAnalysis()
    {
        var table = document.getElementById('analysesTable');
        analysisCount++;

        var newRow = table.insertRow(3 + analysisCount * 2);
        var titleCell = newRow.insertCell(0);
        titleCell.innerHTML = 'MHC Analysis #' + (analysisCount + 1);

        newRow = table.insertRow(4 + analysisCount * 2);
        newRow.insertCell(0).innerHTML = 'Reference Sequences:';
        newRow.insertCell(1).innerHTML = '<select name="sequencesViews"><%
            for (CustomView view : bean.getSequencesViews())
            {
                String name = view.getName();
            %><option><%=h(null == name ? "[all]" : name)%><\/option><%
            }
        %><\/select>';
        table.insertRow(5 + analysisCount * 2).insertCell(0).innertHTML = '&nbsp;';

        updateDeleteButton();
    }

    function deleteLastAnalysis()
    {
        var table = document.getElementById('analysesTable');
        table.deleteRow(3 + analysisCount * 2);
        table.deleteRow(3 + analysisCount * 2);
        table.deleteRow(3 + analysisCount * 2);
        analysisCount--;
        updateDeleteButton();
    }

    function updateDeleteButton()
    {
        var btn = document.getElementById('delete');

        if (analysisCount < 0)
            disable(btn);
        else
            enable(btn);
    }

    function enable(btn)
    {
        btn.className = "labkey-button";
        btn.href = "javascript:deleteLastAnalysis();";
        btn.disabled = false;
    }

    function disable(btn)
    {
        btn.className = "labkey-disabled-button";
        btn.href = "javascript:return false;";
        btn.disabled = true;
    }
</script>
