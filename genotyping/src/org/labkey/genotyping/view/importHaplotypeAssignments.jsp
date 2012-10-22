
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.genotyping.HaplotypeDataCollector" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.genotyping.HaplotypeAssayProvider" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<HaplotypeDataCollector> me = (JspView<HaplotypeDataCollector>) HttpView.currentView();
    HaplotypeDataCollector bean = me.getModelBean();
    String[] reshowData = {};
    if (bean.getData() != null && !bean.getData().equals(""))
    {
        reshowData = bean.getData().split("\\r?\\n");
    }

    final String copyPasteDivId = "copypasteDiv" + getRequestScopedUID();
%>
<form action="importHaplotypeAssignments.post" method="post">
    <div id="<%=h(copyPasteDivId)%>"></div>
</form>

<script type="text/javascript">
    var expectedHeaders = [];
    <%
    for (Pair<String, String> entry : HaplotypeAssayProvider.COLUMN_HEADER_MAPPING_PROPERTIES)
    {
        %>expectedHeaders.push({name: '<%=h(entry.first)%>', label: '<%=h(entry.second)%>'});<%
    }
    %>

    var reshowData = ""
    <%
    for (String line : reshowData)
    {
        %>+ "<%=h(line)%> \n"<%
    }
    %>

    Ext4.onReady(function(){
        var items = [{
            xtype: 'textarea',
            fieldLabel: 'Copy/Paste the header rows into the text area below',
            labelAlign: 'top',
            itemId: '<%=h(HaplotypeAssayProvider.DATA_PROPERTY_NAME)%>',
            name: '<%=h(HaplotypeAssayProvider.DATA_PROPERTY_NAME)%>',
            value: reshowData,
            allowBlank: false,
            width:580,
            height:300,
            listeners: {
                'change': function(cmp) {
                    copyPasteForm.loadColHeaderComboData(cmp.getValue());
                }
            }
        },{
            xtype: 'displayfield',
            value: 'Match the column headers from the tab-delimited data with the key fields:'
        }];
        Ext4.each(expectedHeaders, function(header){
            var combo = Ext4.create('Ext.form.ComboBox', {
                xtype: 'combo',
                labelWidth: 170,
                width: 400,
                name: header.name,
                fieldLabel: header.label,
                disabled: <%=reshowData.length == 0%>,
                queryMode: 'local',
                displayField: 'header',
                valueField: 'header',
                allowBlank: false,
                editable: false,
                store: Ext4.create('Ext.data.Store', {
                    fields: ['header'],
                    data: []
                })
            });
            combo.store.on('datachanged', function(store){
                // select the combo item, if there is a match
                var index = store.find('header', header.label, 0, false, false, true);
                if (index != null && index > -1)
                    combo.select(store.getAt(index));
                else
                    combo.reset();
            }, this, {buffer: 500});

            items.push(combo);
        });

        var copyPasteForm = Ext4.create('Ext.form.FormPanel', {
            border: false,
            itemId: 'copyPasteForm',
            items: items,

            loadColHeaderComboData: function(data)
            {
                // parse the textarea data to get the column headers
                var lines = data.split('\n');
                var colHeaders = [];
                if (lines.length > 0)
                {
                    var tokens = lines[0].split('\t');
                    for (var i = 0; i < tokens.length; i++)
                        colHeaders.push({header: tokens[i]});
                }

                // load the column headers data into the combo boxes
                var combos = Ext4.ComponentQuery.query('#copyPasteForm > combo');
                Ext4.each(combos, function(combo){
                    combo.getStore().loadData(colHeaders);
                    combo.enable();
                });
            }
        });
        copyPasteForm.render(<%=h(copyPasteDivId)%>);

        <%
        if (reshowData.length > 0)
        {
            %>copyPasteForm.down('textarea').fireEvent('change', copyPasteForm.down('textarea'));<%
        }
        %>
    });

</script>