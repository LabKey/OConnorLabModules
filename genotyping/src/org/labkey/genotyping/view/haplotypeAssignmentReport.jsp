<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("Ext4");
    }
%>
<%
    JspView<GenotypingController.AssignmentReportBean> me = (JspView<GenotypingController.AssignmentReportBean>) HttpView.currentView();
    GenotypingController.AssignmentReportBean bean = me.getModelBean();
    final String idEntryFormDivId = "idEntryForm" + getRequestScopedUID();
    final String queryWebPartDivId = "queryWebPart" + getRequestScopedUID();
    final String duplicatesDivId = "duplicates" + getRequestScopedUID();
    final String assayName = bean.getAssayName();
    String initialIds = StringUtils.join(bean.getIds(), ";");
%>
<div id='<%=h(idEntryFormDivId)%>'></div>
<br/>
<div id='<%=h(duplicatesDivId)%>' class='labkey-error'></div>
<br/>
<div id='<%=h(queryWebPartDivId)%>'></div>

<script type="text/javascript">

    Ext4.onReady(function(){
        // lookup the Animal IDs if there were any selected from the results grid
        var ids = <%=q(initialIds)%>;
        if (ids.length > 0)
        {
            LABKEY.Query.selectRows({
                schemaName: 'assay.Haplotype.' + <%= PageFlowUtil.jsString(assayName)%>,
                queryName: 'Data',
                filterArray: [LABKEY.Filter.create('RowId', ids, LABKEY.Filter.Types.IN)],
                columns: 'AnimalId/LabAnimalId',
                success: function(data){
                    var animalIds = [];
                    Ext4.each(data.rows, function(row){
                        animalIds.push(row["AnimalId/LabAnimalId"]);
                    });

                    init(animalIds.join(", "));
                }
            });
        }
        else
            init();
    });

    function init(initialIds)
    {
        var assayName = <%=q(bean.getAssayName())%>;

        var idEntryForm = Ext4.create('Ext.form.FormPanel', {
            border: true,
            width: 525,
            bodyPadding: 11,
            itemId: 'idEntryForm',
            items: [
                {
                    xtype: 'combo',
                    itemId: 'searchId',
                    name: 'searchId',
                    fieldLabel: 'Search for animal IDs by',
                    labelWidth: 210,
                    editable: false,
                    store: Ext4.create('Ext.data.Store', {
                        fields: ['label', 'value'],
                        data: [
                            {label: 'Lab Animal ID', value: 'LabAnimalId'},
                            {label: 'Client Animal ID', value: 'ClientAnimalId'}
                        ]
                    }),
                    queryMode: 'local',
                    displayField: 'label',
                    valueField: 'value',
                    value: 'LabAnimalId'
                },
                {
                    xtype: 'combo',
                    itemId: 'displayId',
                    name: 'displayId',
                    fieldLabel: 'Show report column headers as',
                    labelWidth: 210,
                    editable: false,
                    store: Ext4.create('Ext.data.Store', {
                        fields: ['label', 'value'],
                        data: [
                            {label: 'Lab Animal ID', value: 'LabAnimalId'},
                            {label: 'Client Animal ID', value: 'ClientAnimalId'}
                        ]
                    }),
                    queryMode: 'local',
                    displayField: 'label',
                    valueField: 'value',
                    value: 'LabAnimalId'
                },
                {
                    xtype: 'textarea',
                    fieldLabel: 'Enter the animal IDs separated by whitespace, comma, or semicolon',
                    labelAlign: 'top',
                    itemId: 'idsTextArea',
                    name: 'idsTextArea',
                    value: initialIds,
                    allowBlank: false,
                    width:500,
                    height:150
                }
            ],
            buttonAlign: 'left',
            buttons: [
                {
                    formBind: true,
                    itemId: 'submitBtn',
                    text: 'Submit',
                    handler: function(){
                        idEntryForm.submitReport();
                    }
                },
                {
                    text: 'Cancel',
                    handler: function(){
                        window.location = '<%=bean.getReturnURL()%>';
                    }
                }
            ],

            submitReport: function(){
                var values = idEntryForm.getForm().getValues();
                var searchId = values["searchId"];
                var displayId = values["displayId"];
                var idArr = values["idsTextArea"].trim().split(/[,;\s]+/);
                if (idArr.length > 0)
                {
                    idEntryForm.getQueryWebPart(idArr, searchId, displayId);
                    idEntryForm.checkDuplicateIds(idArr, searchId);
                }
            },

            checkDuplicateIds: function(idArr, searchId)
            {
                LABKEY.Query.executeSql({
                    schemaName: 'assay.Haplotype.' + assayName,
                    sql: 'SELECT * FROM ( '
                       + '  SELECT AnimalId.' + searchId + ' AS Id, '
                       + '  COUNT(AnimalId) AS NumRecords '
                       + '  FROM Data '
                       + '  WHERE Enabled=TRUE AND RunId.enabled=TRUE '
                       + '    AND AnimalId.' + searchId + ' IN (' + this.getIdInClauseStr(idArr) + ')'
                       + '  GROUP BY AnimalId.' + searchId + ') AS x '
                       + 'WHERE x.NumRecords > 1',
                    success: function(data){
                        if (data.rows.length > 0)
                        {
                            var message = "Warning: multiple enabled assay results were found for the following IDs: ";
                            var sep = "";
                            Ext4.each(data.rows, function(row){
                                message += sep + row.Id + " (" + row.NumRecords + ")";
                                sep = ", ";
                            });

                            Ext4.get('<%=h(duplicatesDivId)%>').update(message);
                        }
                        else
                            Ext4.get('<%=h(duplicatesDivId)%>').update("");
                    }
                })
            },

            getQueryWebPart: function(idArr, searchId, displayId)
            {
                Ext4.get('<%=h(queryWebPartDivId)%>').update('');
                var qwp1 = new LABKEY.QueryWebPart({
                    renderTo: '<%=h(queryWebPartDivId)%>',
                    frame: 'none',
                    schemaName: 'genotyping',
                    sql: this.getAssignmentPivotSQL(idArr, searchId, displayId),
                    showDetailsColumn: false,
                    dataRegionName: 'report',
                    buttonBar: {
                        includeStandardButtons: false,
                        items:[
                            LABKEY.QueryWebPart.standardButtons.exportRows,
                            LABKEY.QueryWebPart.standardButtons.print,
                            LABKEY.QueryWebPart.standardButtons.pageSize
                        ]
                    }
                });
            },

            getIdInClauseStr: function(idArr)
            {
                var str = "";
                var sep = "";
                Ext4.each(idArr, function(id){
                    str += sep + "'" + id + "'";
                    sep = ", ";
                });
                return str;
            },

            getAssignmentPivotSQL: function(idArr, searchId, displayId)
            {
                return "SELECT Animal, "
                    + "Haplotype, "
                    + "COUNT(Haplotype) AS Counts "
                    + "FROM (SELECT AnimalAnalysisId.AnimalId." + displayId + " AS Animal, HaplotypeId.Name AS Haplotype "
                    + "      FROM AnimalHaplotypeAssignment "
                    + "      JOIN assay.Haplotype.\"" + assayName + "\".Runs AS runs "
                	+ "            ON runs.RowId = AnimalAnalysisId.RunId "
                    + "      WHERE AnimalAnalysisId.AnimalId." + searchId + " IN (" + this.getIdInClauseStr(idArr) + ") "
                    + "            AND AnimalAnalysisId.Enabled=TRUE AND runs.enabled=TRUE) AS x "
                    + "GROUP BY Animal, Haplotype "
                    + "PIVOT Counts BY Animal "
                    + "ORDER BY Haplotype LIMIT " + (idArr.length*10);
            }
        });
        idEntryForm.render('<%=h(idEntryFormDivId)%>');

        // if we have initial IDs values for the form, render the grid
        if (initialIds && initialIds.length > 0)
            idEntryForm.submitReport();
    }

</script>