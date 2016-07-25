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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("clientapi/ext3");
    }
%>
<%
    ActionURL currentURL = getActionURL();
    boolean showNewMatchMessage = (null != currentURL.getParameter("highlightId"));
    String deleted = currentURL.getParameter("delete");
    Integer deletedCount = null;

    if (null != deleted)
        deletedCount = Integer.parseInt(deleted);

    String message = showNewMatchMessage ? "Newly added match is highlighted below." : (null != deletedCount ? deletedCount + (deletedCount == 1 ? " match was" : " matches were") + " deleted." : null);
%>
<script type="text/javascript"><%
    // Show a message if we just added or deleted a match
    if (null != message)
    { %>
    (function($) {
        LABKEY.DataRegions['Analysis'].addMessage('<span style="color:green;"><%=message%></span>');
    })(jQuery);
<%  } %>
    var expectedCount;
    var grid;
    var combineWin;
    var submitButton;
    var formPanel;
    var analysisId;
    var alleleIdsText;

    function combine(analysis)
    {
        analysisId = analysis;
        var selected = LABKEY.DataRegions['Analysis'].getChecked();
        expectedCount = selected.length;

        if (expectedCount < 1)
        {
            alert("You must select one or more matches.");
            return;
        }
        
        LABKEY.Query.selectRows({
            requiredVersion: 9.1,
            schemaName: 'genotyping',
            queryName: 'Matches_' + analysisId,  // Hack to improve perf, see #11949. TODO: support multi-column FK
            columns: 'RowId,SampleId,Alleles/AlleleName,Alleles/RowId',
            filterArray: [
                LABKEY.Filter.create('Analysis/RowId', analysis, LABKEY.Filter.Types.EQUAL),
                LABKEY.Filter.create('RowId', selected.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
            ],
            sort: null,
            success: validateAndShow,
            failure: onError
        });
    }

    function validateAndShow(selected)
    {
        var rows = selected.rows;
        var matches = rows.length;

        // Validate that we got back the number of rows we expected
        if (!rows || matches != expectedCount)
        {
            alert("Error: Selected match" + (1 == expectedCount ? " has" : "es have") + " been modified.");
            return;
        }

        // Validate that every match has the same sample id
        var sampleId = null;

        for (var i = 0; i < matches; i++)
        {
            var testId = rows[i]['SampleId'].value;

            if (null == sampleId)
            {
                sampleId = testId;
            }
            else if (sampleId != testId)
            {
                alert("Error: You can't combine matches from different samples.");
                return;
            }
        }

        var matchIds = [];
        // Create an array of unique allele names across the selected matches (poor man's set)
        var uniqueAlleles = [];

        for (i = 0; i < matches; i++)
        {
            matchIds.push(rows[i]['RowId'].value);
            var matchAlleleNames = rows[i]['Alleles/AlleleName'].value;
            var matchAlleleRowIds = rows[i]['Alleles/RowId'].value;

            for (var j = 0; j < matchAlleleNames.length; j++)
            {
                var allele = [matchAlleleNames[j], matchAlleleRowIds[j]];
                addAlleleIfAbsent(uniqueAlleles, allele);
            }
        }

        var labelStyle = 'padding-bottom:7px';
        var instructions;
        var title;
        var submitCaption;

        if (matches > 1)
        {
            title = 'Combine Matches';
            instructions = 'The ' + matches + ' matches you selected will be combined into a single match, and the new match assigned the alleles you select below. This operation is permanent and can\'t be undone.';
            submitCaption = 'Combine';
        }
        else
        {
            title = 'Alter Assigned Alleles';
            instructions = 'This match will be altered by assigning the alleles you select below. This operation is permanent and can\'t be undone.';
            submitCaption = 'Alter';
        }

        var instructionsLabel = new Ext.form.Label({
            html: '<div style="' + labelStyle +'">' + instructions + '<\/div>'
        });

        var store = new Ext.data.SimpleStore({
            fields:['name', 'rowId'],
            data:uniqueAlleles
        });

        var selModel = new Ext.grid.CheckboxSelectionModel();
        selModel.addListener('rowselect', updateCombineButton);
        selModel.addListener('rowdeselect', updateCombineButton);

        submitButton = new Ext.Button({
                text: submitCaption,
                type: 'submit',
                disabled: true,
                id: 'btn_submit',
                handler: submit
            });

        var returnUrlText = new Ext.form.TextField({name:'<%=ActionURL.Param.returnUrl%>', hidden:true, value:<%=PageFlowUtil.jsString(getActionURL().toString())%>});
        var analysisText = new Ext.form.TextField({name:'analysis', hidden:true, value:analysisId});
        var matchIdsText = new Ext.form.TextField({name:'matchIds', hidden:true, value:matchIds.join(",")});
        alleleIdsText = new Ext.form.TextField({name:'alleleIds', hidden:true});

        // create the alleles grid
        grid = new Ext.grid.GridPanel({
            title:'Alleles',
            store: store,
            columns: [
                selModel,
                {id:'name', width: 100, sortable: false, dataIndex: 'name'}
            ],
            stripeRows: false,
            collapsed: false,
            collapsible: false,
            autoExpandColumn: 'name',
            autoHeight: false,
            forceFit: true,
            width: 400,
            height: 300,
            selModel: selModel
        });

        formPanel = new LABKEY.ext.FormPanel({
            standardSubmit: true,
            padding: 6,
            items: [instructionsLabel, grid, returnUrlText, analysisText, matchIdsText, alleleIdsText]});

        combineWin = new Ext.Window({
            title: title,
            layout: 'fit',
            border: false,
            width: 430,
            height: 455,
            closeAction: 'close',
            modal: true,
            items: formPanel,
            resizable: false,
            buttons: [submitButton,
                {
                    text: 'Cancel',
                    id: 'btn_cancel',
                    handler: function()
                        {
                            combineWin.close();
                        }
            }],
            bbar: [{ xtype: 'tbtext', text: '', id: 'statusTxt'}]
        });

        combineWin.show();
    }

    function updateCombineButton()
    {
        var selectedCount = grid.selModel.getCount();

        if (selectedCount < 1)
            submitButton.disable();
        else
            submitButton.enable();
    }

    // Add a single allele to array if it's not already present
    function addAlleleIfAbsent(alleles, allele)
    {
        for (var i = 0; i < alleles.length; i++)
            if (alleles[i][0] === allele[0])
                return;

        alleles.push(allele);
    }

    function onError(errorInfo)
    {
        alert(errorInfo.exception);
    }

    function submit()
    {
        var value = '';
        var sep = '';
        grid.selModel.each(function(record) {
                value = value + sep + record.get('rowId');
                sep = ',';
            });
        alleleIdsText.setValue(value);

        var form = formPanel.getForm();
        form.url = LABKEY.ActionURL.buildURL("genotyping", "combineMatches");
        form.method = 'POST';

        form.submit();
        combineWin.close();
    }
</script>
