<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.oconnorexperiments.model.OConnorExperimentsManager" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    ViewContext context = HttpView.currentContext();
    //Container c = context.getContainer();
    //OConnorExperimentsManager.get().getExperiment(c);

%>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi"));
        return resources;
    }
%>
<labkey:scriptDependency/>

<div id="msgDiv"></div>
<div id="formDiv"></div>
<script>

    Ext.onReady(function () {
        function createForm(data)
        {
            var f = new LABKEY.ext.FormPanel({
                id: 'experimentForm',
                border: false,
                selectRowsResults: data
            });

            f.render("formDiv");
        }

        LABKEY.Query.selectRows({
            requiredVersion: 9.1,
            schemaName: "OConnorExperiments",
            queryName: "Experiments",
            successCallback: createForm,
            errorCallback: function (errorInfo) {
                var error = (errorInfo && errorInfo.exception) ? errorInfo.exection : "An error occurred";
                Ext.fly("msgDiv").update("<span class='labkey-error'>" + Ext.util.Format.htmlEncode(error) + "</span>");
            }
        });

    });

</script>
