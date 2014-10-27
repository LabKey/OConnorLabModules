<%
/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.genotyping.HaplotypeDataCollector" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.genotyping.HaplotypeAssayProvider" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.genotyping.HaplotypeColumnMappingProperty" %>
<%@ page import="org.labkey.genotyping.HaplotypeProtocolBean" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<HaplotypeProtocolBean> me = (JspView<HaplotypeProtocolBean>) HttpView.currentView();
    HaplotypeProtocolBean bean = me.getModelBean();
    HaplotypeDataCollector dataCollector = bean.getDataCollector();
    String[] reshowData = {};
    if (dataCollector.getReshowValue("data") != null && !dataCollector.getReshowValue("data").equals(""))
    {
        reshowData = dataCollector.getReshowValue("data").split("\\r?\\n");
    }

    final String copyPasteDivId = "copypasteDiv" + getRequestScopedUID();
%>
<labkey:form action="importHaplotypeAssignments.post" method="post">
    <div id="<%=h(copyPasteDivId)%>"></div>
</labkey:form>

<script type="text/javascript">
    var expectedHeaders = [];
    <%
    for (Map.Entry<String, HaplotypeColumnMappingProperty> property : HaplotypeAssayProvider.getColumnMappingProperties(bean.getProtocol()).entrySet())
    {
        %>expectedHeaders.push({name: '<%=h(property.getKey())%>', label: '<%=h(property.getValue().getLabel())%>', reshowValue: '<%=h(dataCollector.getReshowValue(property.getKey()))%>', required: <%=h(property.getValue().isRequired())%>});<%
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
            fieldLabel: 'Copy/Paste the rows, including the headers, into the text area below',
            labelAlign: 'top',
            itemId: <%=q(HaplotypeAssayProvider.DATA_PROPERTY_NAME)%>,
            name: <%=q(HaplotypeAssayProvider.DATA_PROPERTY_NAME)%>,
            value: reshowData,
            allowBlank: false,
            width:580,
            height:300,
            listeners: {
                buffer: 500,
                change: function(cmp) {
                    copyPasteForm.loadColHeaderComboData(cmp.getValue());
                }
            }
        },{
            xtype: 'displayfield',
            value: 'Match the column headers from the tab-delimited data with the key fields:'
        }];
        Ext4.each(expectedHeaders, function(header) {
            var combo = Ext4.create('Ext.form.ComboBox', {
                xtype: 'combo',
                labelWidth: 180,
                width: 410,
                name: header.name,
                fieldLabel: header.label + (header.required ? " *" : ""),
                disabled: <%=reshowData.length == 0%>,
                queryMode: 'local',
                displayField: 'header',
                valueField: 'header',
                allowBlank: !header.required,
                submitEmptyText: false,
                emptyText: 'None',
                tpl: new Ext4.XTemplate('<tpl for=".">' + '<li style="height:22px;" class="x4-boundlist-item" role="option">' + '{header}' + '</li></tpl>'),
                editable: false,
                store: Ext4.create('Ext.data.Store', {
                    fields: ['header'],
                    data: []
                }),
                updateSelection: function() {
                    // select the combo item, if there is a match
                    var store = this.getStore();
                    var index1 = store.find('header', getReshowValue(header.name), 0, false, false, true);
                    var index2 = store.find('header', header.label, 0, false, false, true);
                    if (index1 != null && index1 > -1)
                        combo.select(store.getAt(index1));
                    else if (index2 != null && index2 > -1)
                        combo.select(store.getAt(index2));
                    else
                        combo.reset();

                    combo.enable();
                }
            });

            items.push(combo);
        });

        var copyPasteForm = Ext4.create('Ext.form.FormPanel', {
            renderTo: '<%=h(copyPasteDivId)%>',
            border: false,
            itemId: 'copyPasteForm',
            items: items,
            loadColHeaderComboData: function(data)
            {
                // parse the textarea data to get the column headers
                var lines = data.split('\n');
                var colHeaders = [''];
                if (lines.length > 0)
                {
                    var tokens = lines[0].split('\t');
                    for (var i = 0; i < tokens.length; i++)
                    {
                        if (tokens[i].trim().length > 0)
                        {
                            colHeaders.push({header: tokens[i].trim()});
                        }
                    }
                }

                // load the column headers data into the combo boxes
                var combos = Ext4.ComponentQuery.query('#copyPasteForm > combo');
                Ext4.each(combos, function(combo){
                    combo.getStore().loadData(colHeaders);
                    combo.updateSelection();
                });
            }
        });

        <%
        if (reshowData.length > 0)
        {
            %>copyPasteForm.down('textarea').fireEvent('change', copyPasteForm.down('textarea'));<%
        }
        %>
    });

    function getReshowValue(headerName)
    {
        var reshowVal = null;
        Ext4.each(expectedHeaders, function(headerProperty){
            if (headerProperty.name == headerName)
            {
                reshowVal = headerProperty.reshowValue;
            }
        });
        return reshowVal;
    }

</script>