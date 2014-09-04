<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.query.QueryHelper" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingQueryHelper" %>
<%@ page import="org.labkey.genotyping.ValidatingGenotypingFolderSettings" %>
<%@ page import="org.labkey.genotyping.sequences.SequenceDictionary" %>
<%@ page import="org.labkey.genotyping.sequences.SequenceManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    ReturnUrlForm form = (ReturnUrlForm)getModelBean();
    Container c = getContainer();
    User user = getUser();
    ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(c, user, "loading sequences");
    QueryHelper qHelper = new GenotypingQueryHelper(c, user, settings.getSequencesQuery());
    SequenceDictionary dictionary = SequenceManager.get().getCurrentDictionary(c, user, false);
%>
<form <%=formAction(GenotypingController.LoadSequencesAction.class, Method.Post)%>><labkey:csrf/>
    <table><tr><td>
        <p><%
            if (null == dictionary)
            {
        %>
            <span class="labkey-error">Reference sequences have not been loaded in this folder. You must load reference sequences before submitting
            genotyping analyses.</span>
        <%
            }
            else
            {
        %>
            Reference sequences in this folder are currently set to version <%=dictionary.getRowId()%>, loaded
            <%=formatDateTime(dictionary.getCreated())%> by <%=h(dictionary.getCreatedBy().getDisplayName(user))%>.
        <%
            }
        %>
        </p>
        <p>
            Clicking the button below will load a new dictionary of DNA sequences from the source query "<%=h(qHelper)%>" and set it as the
            dictionary of reference sequences to use for future genotyping analyses. Existing analysis runs will continue to
            link to the sequences used at the time of their analysis.
        </p>
        <%= button("Load Sequences").submit(true) %><%=generateReturnUrlFormField(form)%>
    </td></tr></table>
</form>
