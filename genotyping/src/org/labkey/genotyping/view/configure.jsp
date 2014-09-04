<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
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
<%@ page import="org.labkey.api.exp.property.PropertyService"%>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingFolderSettings" %>
<%@ page import="org.labkey.genotyping.GenotypingQuerySchema" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext3"));
        resources.add(ClientDependency.fromFilePath("viewPicker.js"));
        return resources;
    }
%>
<%
    GenotypingController.AdminForm form = (GenotypingController.AdminForm)getModelBean();
    GenotypingQuerySchema schema = new GenotypingQuerySchema(getUser(), getContainer());

    ActionURL animalQueryURL = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "genotyping", "Animal");
    ActionURL animalEditDomainURL = PropertyService.get().getDomainKind(schema.getDomainURI(GenotypingQuerySchema.TableType.Animal.name())).urlCreateDefinition(GenotypingQuerySchema.NAME, GenotypingQuerySchema.TableType.Animal.name(), getContainer(), getUser());
    animalEditDomainURL.addParameter(ActionURL.Param.returnUrl, getActionURL().toString());

    ActionURL haplotypeQueryURL = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "genotyping", "Haplotype");
    ActionURL haplotypeEditDomainURL = PropertyService.get().getDomainKind(schema.getDomainURI(GenotypingQuerySchema.TableType.Haplotype.name())).urlCreateDefinition(GenotypingQuerySchema.NAME, GenotypingQuerySchema.TableType.Haplotype.name(), getContainer(), getUser());
    haplotypeEditDomainURL.addParameter(ActionURL.Param.returnUrl, getActionURL().toString());
%>
<script type="text/javascript">
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
            html = parts[0] + '.' + parts[1] + ((parts[2] && parts[2].length() > 0) ? ('.' + parts[2]) : '');
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

<form <%=formAction(GenotypingController.AdminAction.class, Method.Post)%>><labkey:csrf/>
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
        <tr>
            <td><a href="<%=animalQueryURL.getLocalURIString()%>">Animal</a></td>
            <td>
                <%=textLink("configure", animalEditDomainURL, "configureAnimal")%>
            </td>
        </tr>
        <tr>
            <td><a href="<%=haplotypeQueryURL.getLocalURIString()%>">Haplotype</a></td>
            <td>
                <%=textLink("configure", haplotypeEditDomainURL, "configureHaplotype")%>
            </td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td><%= button("Submit").submit(true) %> <%= button("Done").href(form.getReturnURLHelper()) %><%=generateReturnUrlFormField(form)%></td></tr>
    </table>
</form>
