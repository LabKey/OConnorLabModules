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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.genotyping.GenotypingManager" %>
<%@ page import="org.labkey.genotyping.sequences.SequenceManager" %>
<%@ page import="java.io.File" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    GenotypingController.ImportReadsBean bean = (GenotypingController.ImportReadsBean)getModelBean();
    String extensions = StringUtils.join(SequenceManager.FASTQ_EXTENSIONS, "\", \"");
%>
<form <%=formAction(GenotypingController.ImportReadsAction.class, Method.Post)%> name="importReads">
    If you select "<%=h(GenotypingManager.SEQUENCE_PLATFORMS.LS454.toString())%>", the pipeline will extract reads from the file "<%=h(GenotypingManager.READS_FILE_NAME)%>".
    <p></p>
    If you select "<%=h(GenotypingManager.SEQUENCE_PLATFORMS.ILLUMINA.toString())%>", the pipeline will attempt to load any files in this folder with the following extensions: "<%=h(extensions)%>" or gzipped versions of them.  If you choose a FASTQ prefix, only files beginning with the prefix will be used.
    <p></p>

    <table id="analysesTable">
        <%=formatMissedErrorsInTable("form", 2)%>
        <tr><td colspan="2">Run Information</td></tr>
        <tr><td>Associated Run:</td><td><select name="run"><%
            for (Integer run : bean.getRuns())
            { %><option><%=h(run)%></option>
             <%
            }
        %></select></td></tr>
        <tr><td>Platform:</td><td>
            <select name="platform"><%
                for (GenotypingManager.SEQUENCE_PLATFORMS platform : GenotypingManager.SEQUENCE_PLATFORMS.values())
                { %><option
                    <%=platform.equals(bean.getPlatform()) ? "selected" : ""%>
                    ><%=h(platform.toString())%></option>
                 <%
                }
            %>
            </select>
        </td></tr>
        <tr><td>FASTQ Prefix (Illumina only):</td>
        <td><input type="text" name="prefix" value="<%=h(bean.getPrefix())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>
            <input type="hidden" name="pipeline" value="1">
            <input type="hidden" name="readsPath" value="<%=h(bean.getReadsPath())%>">
            <input type="hidden" name="analyze" value="0">
        </td></tr>
        <tr><td><%=generateSubmitButton("Import Reads")%><%=PageFlowUtil.generateSubmitButton("Import Reads And Analyze", "document.importReads.analyze.value=1;")%></td></tr>
    </table>
</form>
