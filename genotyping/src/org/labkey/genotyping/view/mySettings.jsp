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
<%@ page import="org.labkey.api.util.URLHelper"%>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.galaxy.GalaxyFolderSettings" %>
<%@ page import="org.labkey.genotyping.galaxy.GalaxyManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    GenotypingController.MySettingsForm form = (GenotypingController.MySettingsForm)getModelBean();
    GalaxyFolderSettings settings = GalaxyManager.get().getSettings(getContainer());
    String serverURL = settings.getGalaxyURL();
    String preferencesHTML = "the API Keys page";

    // Make it a link if admin has set the Galaxy URL
    if (null != serverURL)
    {
        URLHelper userURL = new URLHelper((serverURL.endsWith("/") ? serverURL : serverURL + "/") + "user/api_keys");
        preferencesHTML = "<a href=\"" + h(userURL.getURIString()) + "\" target=\"pref\">" + preferencesHTML + "</a>";
    }
%>
<labkey:form action="mySettings.post" method="post">
    <table>
        <%=formatMissedErrorsInTable("form", 2)%>
        <tr>
            <td colspan="2" width="600px">
            You need to provide a Galaxy web API key to send data from LabKey to your Galaxy server. Galaxy generates a
            unique web API key for each user and uses it to authorize every call to the Galaxy web API. You can find or
            generate a key by logging into Galaxy and visiting <%=text(preferencesHTML)%>. Copy the 32-character hexidecimal
            string and paste it in the box below.
            </td>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Galaxy web API key</td><td><input size="40" name="galaxyKey" value="<%=h(form.getGalaxyKey())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td><%= button("Submit").submit(true) %> <%= button("Cancel").href(form.getReturnURLHelper()) %><%=generateReturnUrlFormField(form)%></td></tr>
    </table>
</labkey:form>
