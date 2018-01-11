<%
/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingManager" %>
<%@ page import="org.labkey.genotyping.GenotypingManager.SEQUENCE_PLATFORMS" %>
<%@ page import="org.labkey.genotyping.sequences.SequenceManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    GenotypingController.ImportReadsBean bean = (GenotypingController.ImportReadsBean)getModelBean();
    String extensions = StringUtils.join(SequenceManager.FASTQ_EXTENSIONS, "\", \"");
    SEQUENCE_PLATFORMS platform = bean.getPlatform();
%>
<form <%=formAction(GenotypingController.ImportReadsAction.class, Method.Post)%> name="importReads"><labkey:csrf/>
<%
    if (platform == SEQUENCE_PLATFORMS.LS454)
    {
%>
    The pipeline will load reads from the file "<%=h(GenotypingManager.READS_FILE_NAME)%>".

<%
    }

    if (platform == SEQUENCE_PLATFORMS.ILLUMINA)
    {
%>
    The pipeline will attempt to load any files in this folder with the following extensions: "<%=h(extensions)%>" or gzipped versions of them.  If you enter a FASTQ prefix, only files beginning with that prefix will be used.
<%
    }
%>
    <p></p>

    <table id="analysesTable">
        <%=formatMissedErrorsInTable("form", 2)%>
        <tr><td colspan="2"><b>Run Information</b></td></tr>
        <tr><td>Platform:</td><td><%=h(bean.getPlatform())%></td></tr>
        <tr><td>Associated Run:</td><td><select name="run"><%
            for (Integer run : bean.getRuns())
            { %><option><%=h(run)%></option>
             <%
            }
        %></select></td></tr>
<%
    if (platform == SEQUENCE_PLATFORMS.ILLUMINA || platform == SEQUENCE_PLATFORMS.PACBIO)
    {
%>
        <tr><td>FASTQ Prefix (Optional):</td>
        <td><input type="text" name="prefix" value="<%=h(bean.getPrefix())%>"></td></tr>
<%
    }
%>
        <tr><td>&nbsp;</td></tr>
        <tr><td>
            <input type="hidden" name="platform" value="<%=h(bean.getPlatform())%>">
            <input type="hidden" name="pipeline" value="1">
            <input type="hidden" name="readsPath" value="<%=h(bean.getReadsPath())%>">
            <input type="hidden" name="analyze" value="0">
        </td></tr>
        <tr><td><%= button("Import Reads").submit(true) %>
            <%=platform == SEQUENCE_PLATFORMS.LS454 ? button("Import Reads And Analyze").submit(true).onClick("document.importReads.analyze.value=1;") : ""%>
        </td></tr>
    </table>
</form>
