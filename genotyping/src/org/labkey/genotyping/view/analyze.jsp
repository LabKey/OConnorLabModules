<%
/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page import="org.labkey.genotyping.GenotypingController.AnalyzeBean" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.SortedSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
    }
%>
<%
    AnalyzeBean bean = (AnalyzeBean)getModelBean();

// ext-all.css hard-codes a white background, we want transparent instead  %>
<style type="text/css">
    .x-panel-body{background-color: transparent;}
</style>
<labkey:errors/>
<div id="form"></div>

<script type="text/javascript">
    var samples = [
<%
    String sep = "";

    for (Map.Entry<Integer, Pair<String, String>> e : bean.getSampleMap().entrySet())
    {
        out.print(sep);
        out.print("        [");
        out.print("'" + e.getKey() + "', ");
        out.print(q(e.getValue().getKey()) + ", ");
        out.print(q(e.getValue().getValue()));
        out.print("]");

        sep = ",\n";
    }
%>
    ];

var sampleStore = new Ext.data.SimpleStore({
    fields:['key', 'name', 'species'],
    data:samples
});

var views = [
<%
    sep = "";

    SortedSet<CustomView> views = bean.getSequencesViews();

    // Add [default] view even if it hasn't been customized, #11110
    if (views.isEmpty() || null != views.first().getName())
        sep = addViewName(out, null, sep);

    // Add all the defined custom views
    for (CustomView view : bean.getSequencesViews())
        sep = addViewName(out, view.getName(), sep);
%><%!
    private String addViewName(JspWriter out, String viewName, String sep) throws IOException
    {
        out.print(sep);
        viewName = (null == viewName ? GenotypingController.DEFAULT_VIEW_PLACEHOLDER : viewName);
        out.print("        [" + q(viewName) + "]");
        return ",\n";
    }
%>
    ];

var seqViews = new Ext.data.SimpleStore({
    fields:['name'],
    data:views
});

var sequencesViewCombo = new Ext.form.ComboBox({fieldLabel:'Reference Sequences', mode:'local', store:seqViews, valueField:'name', displayField:'name', allowBlank:false, hiddenName:'sequencesView', editable:false, triggerAction:'all'});
var selectedSamples = new Ext.form.TextField({name:'samples', hidden:true});
var description = new Ext.form.TextArea({name:'description', fieldLabel:'Description', width:600, height:200, resizable:true, autoCreate:{tag:"textarea", style:"font-family:'Courier'", autocomplete:"off", wrap:"off"}});

var selModel = new Ext.grid.CheckboxSelectionModel();

// create the table grid
var samplesGrid = new Ext.grid.GridPanel({
    fieldLabel:'Samples',
    title:'&nbsp;',
    store: sampleStore,
    columns: [
        selModel,
        {id:'name', width: 160, sortable: false, dataIndex: 'name'},
        {id:'species', width: 160, sortable: false, dataIndex: 'species'}
    ],
    stripeRows: true,
    collapsed: true,
    collapsible: true,
    autoExpandColumn: 'name',
    autoHeight: true,
    width: 600,
    selModel: selModel
});

var f = new LABKEY.ext.FormPanel({
    width:955,
    labelWidth:150,
    border:false,
    standardSubmit:true,
    items:[
        sequencesViewCombo,
        samplesGrid,
        description,
        selectedSamples
    ],
    buttons:[{text:'Submit', type:'submit', handler:submit}, {text:'Cancel', handler:function() {document.location = <%=q(bean.getReturnURL().toString())%>;}}],
    buttonAlign:'left'
});

Ext.onReady(function()
{
    samplesGrid.on('viewready', initializeSelection);
    f.render('form');
});

function initializeSelection()
{
    samplesGrid.selModel.selectAll();
    updateGridTitle();
    samplesGrid.on('expand', updateGridTitle);
    samplesGrid.on('collapse', updateGridTitle);
    selModel.addListener('rowselect', updateGridTitle);
    selModel.addListener('rowdeselect', updateGridTitle);
}

function submit()
{
    var value = '';
    var sep = '';
    samplesGrid.selModel.each(function(record) {
            value = value + sep + record.get('key');
            sep = ',';
        });
    selectedSamples.setValue(value);

    f.getForm().submit();
}

function updateGridTitle()
{
    var title;
    var selectedCount = samplesGrid.selModel.getCount();

    if (selectedCount == sampleStore.getCount())
    {
        title = "All (" + selectedCount + ") samples";
    }
    else
    {
        if (0 == selectedCount)
            title = "No samples";
        else if (1 == selectedCount)
            title = "1 sample";
        else
            title = selectedCount + " samples";
    }

    title += " will be analyzed";

    if (samplesGrid.collapsed)
        title += "; click + to change the samples to submit";

    samplesGrid.setTitle(title);
}
</script>
