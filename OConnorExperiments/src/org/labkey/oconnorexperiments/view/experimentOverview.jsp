<%
/*
* Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
//    Container container = getContainer();
//    User user = getUser();
//
//    Experiment experiment = OConnorExperimentsManager.get().getExperiment(container);
//    User createdBy = UserManager.getUser(container.getCreatedBy());
%>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext"));
        resources.add(ClientDependency.fromPath("/editInPlaceElement.js"));
        resources.add(ClientDependency.fromPath("/editInPlaceElement.css"));
        resources.add(ClientDependency.fromPath("/ocexp/internal/Experiment.js"));
        return resources;
    }
%>


