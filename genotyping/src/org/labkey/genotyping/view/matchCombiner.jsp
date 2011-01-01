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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script type="text/javascript">
    var expectedCount;

    function combine(analysis)
    {
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
            queryName: 'Matches',
            columns: 'SampleId,Alleles/AlleleName',
            filterArray: [
                LABKEY.Filter.create('Analysis/RowId', analysis, LABKEY.Filter.Types.EQUAL),
                LABKEY.Filter.create('RowId', selected.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
            ],
            sort: null,
            successCallback: validate,
            errorCallback: onError
        });
    }

    function validate(selected)
    {
        var rows = selected.rows;
        var matches = rows.length;

        if (!rows || matches != expectedCount)
        {
            alert("Error: Queried matches differ from selected matches.");
            return;
        }

        // Essentially a set -- create an array of unique allele names across the selected matches
        var alleleNames = [];

        for (var i = 0; i < rows.length; i++)
        {
            var names = rows[i]['Alleles/AlleleName'].value;

            for (var j = 0; j < names.length; j++)
            {
                var name = names[j];
                addIfAbsent(alleleNames, name);
            }
        }

        var labelStyle = 'border-bottom:1px solid #AAAAAA;margin:3px';
        var instructions;
        var title;
        var submitCaption;

        if (matches > 1)
        {
            title = 'Combine Matches';
            instructions = 'These ' + matches + ' matches will be combined and the new match assigned the alleles you select below. This operation is permanent and can\'t be undone.';
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

        var actionLabel = new Ext.form.Label({
            html: '<br><div style="' + labelStyle +'">' + alleleNames.join(' ') + '<\/div>'
        });

        var formPanel = new Ext.form.FormPanel({
            padding: 5,
            items: [instructionsLabel, actionLabel]});

        var win = new Ext.Window({
            title: title,
            layout: 'fit',
            border: false,
            width: 475,
            height: 300,
            closeAction: 'close',
            modal: true,
            items: formPanel,
            resizable: false,
            buttons: [{
                text: submitCaption,
                disabled: true,
                id: 'btn_submit',
                handler: function(){
                    // TODO: Post match rowids and new alleles
                    win.close();
                }
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){
                    win.close();
                }
            }],
            bbar: [{ xtype: 'tbtext', text: '', id: 'statusTxt'}]
        });
        win.show();
    }

    function addIfAbsent(array, element)
    {
        for (var i = 0; i < array.length; i++)
            if (array[i] == element)
                return;

        array.push(element);
    }

    function onError(errorInfo)
    {
        alert(errorInfo.exception);
    }
</script>
