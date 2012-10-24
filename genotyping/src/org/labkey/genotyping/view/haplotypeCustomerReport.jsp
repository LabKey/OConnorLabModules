<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReturnUrlForm> me = (JspView<ReturnUrlForm>) HttpView.currentView();
    ReturnUrlForm bean = me.getModelBean();
    final String idEntryFormDivId = "idEntryForm" + getRequestScopedUID();
    final String queryWebPartDivId = "queryWebPart" + getRequestScopedUID();
    final String duplicatesDivId = "duplicates" + getRequestScopedUID();
    ActionURL cancelURL = bean.getReturnActionURL();
%>
<div id='<%=h(idEntryFormDivId)%>'></div>
<br/>
<div id='<%=h(duplicatesDivId)%>' class='labkey-error'></div>
<br/>
<div id='<%=h(queryWebPartDivId)%>'></div>

<script type="text/javascript">

    Ext4.onReady(function(){
        // lookup the Assay design name based on the rowid param
        var rowid = LABKEY.ActionURL.getParameter("rowId");
        if (rowid)
        {
            LABKEY.Query.selectRows({
                schemaName: 'assay',
                queryName: 'AssayList',
                filterArray: [LABKEY.Filter.create('RowId', rowid)],
                columns: 'Name',
                success: function(data){
                    if (data.rows.length == 1)
                        init(data.rows[0].Name);
                }
            });
        }
        else
            Ext4.get('<%=h(duplicatesDivId)%>').update('Error: no assay design rowId parameter');
    });

    function init(assayName)
    {
        var idEntryForm = Ext4.create('Ext.form.FormPanel', {
            border: false,
            width: 525,
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
                            {label: 'Customer Animal ID', value: 'CustomerAnimalId'}
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
                            {label: 'Customer Animal ID', value: 'CustomerAnimalId'}
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
                    allowBlank: false,
                    width:500,
                    height:150
                }
            ],
            buttonAlign: 'left',
            buttons: [
                {
                    formBind: true,
                    text: 'Submit',
                    handler: function(){
                        var form = this.up('form');
                        var values = form.getForm().getValues();
                        var searchId = values["searchId"];
                        var displayId = values["displayId"];
                        var idArr = values["idsTextArea"].trim().split(/[,;\s]+/);
                        if (idArr.length > 0)
                        {
                            form.getQueryWebPart(idArr, searchId, displayId);
                            form.checkDuplicateIds(idArr, searchId);
                        }
                    }
                },
                {
                    text: 'Cancel',
                    handler: function(){
                        window.location = '<%=cancelURL.getLocalURIString()%>';
                    }
                }
            ],

            checkDuplicateIds: function(idArr, searchId)
            {
                LABKEY.Query.executeSql({
                    schemaName: 'assay.Haplotype.' + assayName,
                    sql: 'SELECT * FROM ( '
                       + '  SELECT AnimalId.' + searchId + ' AS Id, '
                       + '  COUNT(AnimalId) AS NumRecords '
                       + '  FROM Data '
                       + '  WHERE Enabled AND RunId.enabled '
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
                    + "            AND AnimalAnalysisId.Enabled AND runs.enabled) AS x "
                    + "GROUP BY Animal, Haplotype "
                    + "PIVOT Counts BY Animal "
                    + "ORDER BY Haplotype LIMIT " + (idArr.length*4);
            }
        });
        idEntryForm.render('<%=h(idEntryFormDivId)%>');
    }

</script>