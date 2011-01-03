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
            successCallback: validateAndShow,
            errorCallback: onError
        });
    }

    function validateAndShow(selected)
    {
        var rows = selected.rows;
        var matches = rows.length;

        // Validate that we got back the number of rows we expected
        if (!rows || matches != expectedCount)
        {
            alert("Error: Queried matches differ from selected matches.");
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

        // Create an array of unique allele names across the selected matches (poor man's set)
        var uniqueNames = [];

        for (i = 0; i < matches; i++)
        {
            var matchNames = rows[i]['Alleles/AlleleName'].value;
            addAllIfAbsent(uniqueNames, matchNames);
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
            html: '<br><div style="' + labelStyle +'">' + uniqueNames.join(' ') + '<\/div>'
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

    // Add all elements to array if they're not already present
    function addAllIfAbsent(array, elements)
    {
        for (var j = 0; j < elements.length; j++)
        {
            var element = elements[j];
            addIfAbsent(array, element);
        }
    }

    // Add a single element to array if it's not already present
    function addIfAbsent(array, element)
    {
        for (var i = 0; i < array.length; i++)
            if (array[i] === element)
                return;

        array.push(element);
    }

    function onError(errorInfo)
    {
        alert(errorInfo.exception);
    }
</script>
