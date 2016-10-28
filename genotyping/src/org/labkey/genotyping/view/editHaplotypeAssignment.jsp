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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.genotyping.GenotypingController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext4");
    }
%>
<%
    JspView<GenotypingController.AssignmentForm> me = (JspView<GenotypingController.AssignmentForm>) HttpView.currentView();
    GenotypingController.AssignmentForm bean = me.getModelBean();
    final String formDivId = "form" + getRequestScopedUID();

    ActionURL returnURL = bean.getReturnActionURL();
    if (returnURL == null)
        returnURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
%>
<%
    if (getErrors("form").hasErrors())
    {
        %><%=formatMissedErrors("form")%><%
    }
    else
    {
%>
<div id='<%=h(formDivId)%>'></div>
<%
    }
%>
<script type="text/javascript">

    Ext4.onReady(function(){
        LABKEY.Query.selectRows({
            schemaName: 'genotyping',
            queryName: 'AnimalAnalysis',
            columns: 'RowId,AnimalId/LabAnimalId,Enabled,TotalReads,IdentifiedReads',
            filterArray: [LABKEY.Filter.create('RowId', <%=bean.getRowId()%>)],
            success: function(data) {
                if (data.rows.length != 1)
                    Ext4.get('<%=h(formDivId)%>').update("<span class='labkey-error'>Error: No record found in AnimalAnalysis table for rowId <span>");
                else
                    init(data.rows[0]);
            }
        });
    });

    function init(formData)
    {
        var assignmentForm = Ext4.create('Ext.form.FormPanel', {
            border: false,
            width: 400,
            bodyPadding: 5,
            bodyStyle : 'background-color: transparent;',
            itemId: 'assignmentForm',
            defaults: {labelWidth: 170},
            items: [],
            buttonAlign: 'left',
            buttons: [
                {
                    text: 'Submit',
                    type:'submit',
                    handler: function(btn){
                        assignmentForm.getEl().mask("Saving...");

                        var form = this.up('form').getForm();
                        var haplotypeRows = [];
                        // create a rows array of the haplotype assignments to update
                        Ext4.each(form.getFields().items, function(field){
                            if (field.name == 'haplotype')
                                haplotypeRows.push({RowId: field.haplotypeRowId, HaplotypeId: field.getValue()})
                        });

                        var commands = [{
                            schemaName: 'genotyping',
                            queryName: 'AnimalAnalysis',
                            command: 'update',
                            rows: [{RowId: <%=bean.getRowId()%>, Enabled: form.findField('enabled').getValue()}]
                        }];
                        if (haplotypeRows.length > 0)
                        {
                            commands.push({
                                schemaName: 'genotyping',
                                queryName: 'AnimalHaplotypeAssignment',
                                command: 'update',
                                rows: haplotypeRows
                            });
                        }

                        LABKEY.Query.saveRows({
                            commands: commands,
                            success: function(data) {
                                assignmentForm.getEl().unmask();
                                window.location = '<%=returnURL.getLocalURIString()%>'
                            },
                            failure: function(response) {
                                alert(response.exception);
                                assignmentForm.getEl().unmask();
                            }
                        });
                    }
                },
                {
                    text: 'Cancel',
                    handler: function(){
                        window.location = '<%=returnURL.getLocalURIString()%>'
                    }
                }
            ],

            configureHaplotypeCombos: function() {
                // query the server for the current set of assigned haplotypes
                LABKEY.Query.selectRows({
                    schemaName: 'genotyping',
                    queryName: 'AnimalHaplotypeAssignment',
                    columns: 'RowId,HaplotypeId,HaplotypeId/Type',
                    filterArray: [LABKEY.Filter.create('AnimalAnalysisId', <%=bean.getRowId()%>)],
                    sort: 'HaplotypeId/Name',
                    success: function(data) {
                        for (var i = 0; i < data.rows.length; i++)
                        {
                            var row = data.rows[i];
                            this.add({
                                xtype: 'combo',
                                itemId: 'haplotype' + i,
                                name: 'haplotype',
                                haplotypeRowId: row.RowId,
                                fieldLabel: row['HaplotypeId/Type'] + ' Haplotype',
                                editable: false,
                                store: Ext4.create('LABKEY.ext4.Store', {
                                    schemaName: "genotyping",
                                    queryName: "Haplotype",
                                    columns: "RowId,Name",
                                    filterArray: [LABKEY.Filter.create('Type', row['HaplotypeId/Type'])],
                                    autoLoad: true,
                                    sort: "Name"
                                }),
                                queryMode: 'local',
                                displayField: 'Name',
                                valueField: 'RowId',
                                value: row.HaplotypeId
                            });
                        }
                    },
                    scope: this
                });
            },

            configureEnabledCheckbox: function() {
                this.add({
                    xtype: 'checkbox',
                    fieldLabel: 'Enabled',
                    itemId: 'enabled',
                    name: 'enabled',
                    checked: formData.Enabled
                });
            },

            configureAnimalDisplay: function() {
                this.add({
                    xtype: 'displayfield',
                    fieldLabel: 'Lab Animal ID',
                    value: formData['AnimalId/LabAnimalId']
                });
                this.add({
                    xtype: 'displayfield',
                    fieldLabel: 'Total # Reads Evaluated',
                    value: formData.TotalReads
                });
                this.add({
                    xtype: 'displayfield',
                    fieldLabel: 'Total # Reads Identified',
                    value: formData.IdentifiedReads
                });
            },

            initializeItems: function() {
                this.configureAnimalDisplay();
                this.configureEnabledCheckbox();
                this.configureHaplotypeCombos();
            }
        });
        assignmentForm.initializeItems();
        assignmentForm.render('<%=h(formDivId)%>');
    }

</script>