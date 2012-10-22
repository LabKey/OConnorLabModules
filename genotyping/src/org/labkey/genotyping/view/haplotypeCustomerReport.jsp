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
    ActionURL cancelURL = bean.getReturnActionURL();
%>
<div id='<%=h(idEntryFormDivId)%>'></div>
<br/><br/>
<div id='<%=h(queryWebPartDivId)%>'></div>

<script type="text/javascript">

    Ext4.onReady(function(){
        var idEntryForm = Ext4.create('Ext.form.FormPanel', {
            border: false,
            itemId: 'idEntryForm',
            items: [{
                xtype: 'textarea',
                fieldLabel: 'Enter the lab animal IDs separated by whitespace, comma, or semicolon',
                labelAlign: 'top',
                itemId: 'idsTextArea',
                name: 'idsTextArea',
                allowBlank: false,
                width:500,
                height:150
            }],
            buttonAlign: 'left',
            buttons: [
                {
                    formBind: true,
                    text: 'Submit',
                    handler: function(){
                        var form = this.up('form');
                        var values = form.getForm().getValues();
                        var idArr = values["idsTextArea"].trim().split(/[,;\s]+/);
                        if (idArr.length > 0)
                            form.getQueryWebPart(idArr);
                    }
                },
                {
                    text: 'Cancel',
                    handler: function(){
                        window.location = '<%=cancelURL.getLocalURIString()%>';
                    }
                }
            ],

            getQueryWebPart: function(idArr)
            {
                Ext4.get('<%=h(queryWebPartDivId)%>').update('');
                var qwp1 = new LABKEY.QueryWebPart({
                    renderTo: '<%=h(queryWebPartDivId)%>',
                    frame: 'none',
                    schemaName: 'genotyping',
                    sql: this.getAssignmentPivotSQL(idArr),
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

            getAssignmentPivotSQL: function(idArr)
            {
                return "SELECT Animal, "
                    + "Haplotype, "
                    + "COUNT(Haplotype) AS Counts "
                    + "FROM (SELECT AnimalAnalysisId.AnimalId.LabAnimalId AS Animal, HaplotypeId.Name AS Haplotype "
                    + "      FROM AnimalHaplotypeAssignment "
                    + "      WHERE AnimalAnalysisId.AnimalId.LabAnimalId IN (" + this.getIdInClauseStr(idArr) + ")) AS x "
                    + "GROUP BY Animal, Haplotype "
                    + "PIVOT Counts BY Animal "
                    + "ORDER BY Haplotype LIMIT " + idArr.length;
            }
        });
        idEntryForm.render('<%=h(idEntryFormDivId)%>');
    });

</script>