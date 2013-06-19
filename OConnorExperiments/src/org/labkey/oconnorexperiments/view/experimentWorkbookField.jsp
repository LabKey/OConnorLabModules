<%
    /*
    * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.oconnorexperiments.model.Experiment" %>
<%@ page import="org.labkey.oconnorexperiments.model.OConnorExperimentsManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView me = (JspView) HttpView.currentView();
    Container container = me.getViewContext().getContainer();
    Experiment experiment = OConnorExperimentsManager.get().getExperiment(container);
%>
<script type="text/javascript">
    LABKEY.requiresCss("editInPlaceElement.css");
    LABKEY.requiresScript("editInPlaceElement.js");
</script>

<script type="text/javascript">
    var _wb_titleId = Ext.id();
    LABKEY.NavTrail.setTrail("<span>Experiment&nbsp;</span><span class='labkey-edit-in-place' id='" + _wb_titleId + "'><%=h(container.getTitle())%></span>",
            undefined, <%=PageFlowUtil.jsString(container.getTitle())%>);
    //LABKEY.NavTrail.setTrail("<%=h(container.getTitle())%>");
</script>

<style type="text/css">
    .wb-name
    {
        color: #999999;
    }
</style>
<span>Created <%=container.getCreated()%></span>
<div id='dropbox'></div>

<script type="text/javascript">
    Ext.onReady(function(){

        if (!LABKEY.Security.currentUser.canUpdate)
            return;

        var experimentData;

        LABKEY.Query.selectRows({
            schemaName : 'OConnorExperiments',
            queryName : 'Experiments',
            filterArray : [
                LABKEY.Filter.create('ExperimentNumber', <%=h(container.getTitle())%>, LABKEY.Filter.Types.EQUALS)
            ],
            success : function(data){
                experimentData = data.rows[0];
                generateEditableElement('Description', 'Description');
                generateEditableElement('Experiment Type', 'ExperimentType');
            },
            scope : this
        });



        new LABKEY.ext.EditInPlaceElement({
            applyTo: _wb_titleId,
            updateConfig: {
                url: LABKEY.ActionURL.buildURL("core", "updateTitle"),
                jsonDataPropName: 'title'
            },
            listeners: {
                beforecomplete: function(newText){
                    return (newText.length > 0);
                }
            }
        });

        function generateEditableElement(title, name){
            var header = document.createElement("h3");
            header.innerHTML = title+':';
            var editable = document.createElement("div");
            editable.id = name ;
            editable.class = 'labkey-edit-in-place';
            editable.innerHTML = experimentData[name] != null ? experimentData[name] : '[Field currently blank]';

            document.getElementById('dropbox').appendChild(header);
            document.getElementById('dropbox').appendChild(editable);

            new LABKEY.ext.EditInPlaceElement({
                applyTo: name,
                multiLine: true,
                emptyText: 'No description provided. Click to add one.',
                listeners : {
                    complete : function(){
                        var row = {
                            Container : experimentData.Container
                        };
                        row[name] = editable.innerHTML;

                        LABKEY.Query.updateRows({
                            schemaName : 'OConnorExperiments',
                            queryName : 'Experiments',
                            rows : [row]
                        });
                    }
                }
            });
        }
    });
</script>