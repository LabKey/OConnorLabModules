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
<%@ page import="org.labkey.genotyping.GenotypingController.AnalyzeBean" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AnalyzeBean bean = (AnalyzeBean)getModelBean();
%>
<labkey:errors/>
<div id="form"></div>

<script type="text/javascript">
    var samples = [
<%
    String sep = "";

    for (Map.Entry<Integer, String> e : bean.getSampleMap().entrySet())
    {
        out.print(sep);
        out.print("        [");
        out.print("'" + e.getKey() + "', ");
        out.print("'" + e.getValue() + "'");
        out.print("]");

        sep = ",\n";
    }
%>
    ];

var sampleStore = new Ext.data.SimpleStore({
    fields:['key', 'name'],
    data:samples
});

var views = [
<%
    sep = "";

    for (CustomView view : bean.getSequencesViews())
    {
        out.print(sep);
        out.print("        ['" + view.getName() + "']");

        sep = ",\n";
    }
%>
    ];

var seqViews = new Ext.data.SimpleStore({
    fields:['name'],
    data:views
});

var sequencesViewCombo = new Ext.form.ComboBox({fieldLabel:'Reference Sequences', mode:'local', store:seqViews, valueField:'name', displayField:'name', hiddenName:'sequenceView', editable:false, triggerAction:'all'});
var selectedSamples = new Ext.form.TextField({name:'samples', hidden:true});
var description = new Ext.form.TextArea({name:'description', fieldLabel:'Description', width:600, height:200, resizable:true, autoCreate:{tag:"textarea", style:"font-family:'Courier'", autocomplete:"off", wrap:"off"}});
//var run = new Ext.form.TextField({name:'run', hidden:true, value:<%=bean.getRun()%>});

var selModel = new Ext.grid.CheckboxSelectionModel();
selModel.addListener('rowselect', updateGridTitle);
selModel.addListener('rowdeselect', updateGridTitle);

// create the table grid
var samplesGrid = new Ext.grid.GridPanel({
    fieldLabel:'Samples',
    title:'&nbsp;',
    store: sampleStore,
    columns: [
        selModel,
        {id:'name', width: 160, sortable: false, dataIndex: 'name'}
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
        selectedSamples,
//        run
    ],
    buttons:[{text:'Submit', type:'submit', handler:submit}, {text:'Cancel', handler:function() {document.location = <%=q(bean.getReturnURL().toString())%>;}}],
    buttonAlign:'left'
});

Ext.onReady(function()
{
    f.render('form');
    samplesGrid.on('expand', updateGridTitle);
    samplesGrid.on('collapse', updateGridTitle);
    samplesGrid.selModel.selectAll();
    updateGridTitle();
});

function submit()
{
    if (samplesGrid.selModel.getCount() == sampleStore.getCount())
    {
        selectedSamples.setValue('*');
    }
    else
    {
        var value = '';
        var sep = '';
        samplesGrid.selModel.each(function(record) {
                value = value + sep + record.get('key');
                sep = ',';
            });
        selectedSamples.setValue(value);
    }

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
