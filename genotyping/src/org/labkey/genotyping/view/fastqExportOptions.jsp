<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ActionURL url = (ActionURL)HttpView.currentModel();
    String id = "cb_" + UniqueID.getRequestScopedUID(getViewContext().getRequest());
    String filter = PageFlowUtil.jsString(url.clone().replaceParameter("filterLowQualityBases", "1").getLocalURIString());
    String noFilter = PageFlowUtil.jsString(url.clone().replaceParameter("filterLowQualityBases", "0").getLocalURIString());
    String onClickScript = "window.location = document.getElementById('" + id + "').checked ? " + filter + " : " + noFilter + "; return false;";

%>
<table class="lk-fields-table">
    <tr>
        <td>
            <table class="labkey-export-tab-layout"><tr><td>Export sequences as FASTQ</td></tr></table>
        </td>
    </tr>
    <tr>
        <td>
            <%= button("Export to FASTQ").href(url).onClick(onClickScript) %>
        </td>
    </tr>
    <tr><td>&nbsp;</td></tr>
    <tr>
        <td>
            <table class="labkey-export-tab-layout"><tr><td><input id="<%=id%>" type="checkbox" checked> Filter out low-quality bases</td></tr></table>
        </td>
    </tr>
</table>
