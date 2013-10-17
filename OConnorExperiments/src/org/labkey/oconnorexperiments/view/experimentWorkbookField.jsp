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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.oconnorexperiments.OConnorExperimentsController" %>
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
<span id='createdSpan'></span>
&nbsp; <%=PageFlowUtil.textLink("History", new ActionURL(OConnorExperimentsController.HistoryAction.class, container))%>

<div id='errorBox'></div>
<div id='dropbox'></div>

<script type="text/javascript">
    Ext.onReady(function(){
        var experimentData;

        LABKEY.Query.selectRows({
            schemaName : 'OConnorExperiments',
            queryName : 'Experiments',
            columns : ['Description', 'ExperimentType', 'ParentExperiments/Container', 'ParentExperiments/ExperimentNumber', 'Created', 'CreatedBy/DisplayName', 'GrantId'],
            filterArray : [
                LABKEY.Filter.create('ExperimentNumber', <%=h(container.getTitle())%>, LABKEY.Filter.Types.EQUALS)
            ],

            success : function(data){
                experimentData = data.rows[0];
                console.log(experimentData);
                var created = document.getElementById('createdSpan');
                created.innerHTML = 'Created ' + experimentData['Created'] + ' by ' + experimentData['CreatedBy/DisplayName'];

                generateEditableElement(false, 'Description', 4000, true, false, true);
                generateParentExperimentField(true);
                generateEditableElement('Experiment Type', 'ExperimentType', 255, false, true, false);
                generateComboElement('Grant', 'GrantId', 255, false, true);
            },
            scope : this
        });

        function generateParentExperimentField(boxed)
        {
            var header = document.createElement("strong");
            header.innerHTML = 'Parent Experiments:  ';
            var editable = document.createElement("span");
            editable.id = 'ParentExperiments' ;
            editable.class = 'labkey-edit-in-place';
            editable.innerHTML = '';
            editable.style.width = (Ext.getBody().getWidth()/3)-header.width + 'px';
            for(var i = 0; i < experimentData['ParentExperiments/ExperimentNumber'].length; i++)
            {
                if(i != experimentData['ParentExperiments/ExperimentNumber'].length-1)
                    editable.innerHTML += experimentData['ParentExperiments/ExperimentNumber'][i] +', ';
                else
                    editable.innerHTML += experimentData['ParentExperiments/ExperimentNumber'][i];
            }

            var error = document.createElement("div");
            error.id = editable.id + "-error";

            if(boxed)
            {
                var box = document.createElement('div');
                box.id = 'ParentExperiment-box';
                box.style.float = 'left';
                box.style.width = (Ext.getBody().getWidth()/3) - 15+'px';


                box.appendChild(header);
                box.appendChild(editable);
                document.getElementById('dropbox').appendChild(box);
            }
            else
            {
                document.getElementById('dropbox').appendChild(header);
                document.getElementById('dropbox').appendChild(editable);
            }

            document.getElementById('errorBox').appendChild(error);

            var errorMessage = new Ext.form.Label ({
                renderTo : editable.id + '-error',
                style : 'color:red'
            });


            if (!LABKEY.Security.currentUser.canUpdate)
                return;

            var parentEditInPlace = new LABKEY.ext.EditInPlaceElement({
                id : 'parentEditInPlace',
                applyTo: 'ParentExperiments',
                editWidth : boxed? box.clientWidth - header.offsetWidth - 15 : null,
                emptyText : 'No Parent Experiments provided. Click to enter a comma separated list of experiment IDs (ex. 1, 2, 3).',
                multiLine: true,
                listeners : {
                    complete : function(){submitParentElements(errorMessage, parentEditInPlace)}
                }
            });
        }

        function submitParentElements(errorMessage, parentEditInPlace){

            var row = {
                Container : experimentData.Container
            };
            var expNums;
            if(document.getElementById('ParentExperiments').innerHTML != this.emptyText)
            {
                expNums = document.getElementById('ParentExperiments').innerHTML.split(',');
            }
            else
            {
                expNums = [''];
            }
            LABKEY.Query.selectRows({
                schemaName : 'OConnorExperiments',
                queryName : 'Experiments',
                containerPath : LABKEY.container.parentId,
                columns : ['ExperimentNumber', 'Container'],
                success : function(data){
                    if(errorMessage)
                        errorMessage.setText('');

                    row.ParentExperiments = [];
                    var found;
                    for(var i = 0; i < expNums.length; i++)
                    {
                        if(expNums[0] === '')
                            continue;

                        found = false;
                        for(var r = 0; r < data.rows.length; r++)
                        {
                            if(expNums[i] == data.rows[r].ExperimentNumber)
                            {
                                row.ParentExperiments.push(data.rows[r].Container);
                                found = true;
                            }
                        }

                        if (found == false && errorMessage)
                        {
                            errorMessage.setText(expNums[i] + " is not a valid Experiment Number");
                            document.getElementById('ParentExperiments').innerHTML = parentEditInPlace.oldText;
                        }
                    }

                    if(!errorMessage || errorMessage.text === '')
                    {
                        LABKEY.Query.updateRows({
                            schemaName : 'OConnorExperiments',
                            queryName : 'Experiments',
                            rows : [row]
                        });
                    }
                },
                scope : this
            });
        }


        function generateEditableElement(title, name, maxLength, lineBreak, boxed, multiline){

            var header = document.createElement("strong");
            header.innerHTML = title+': ';
            if(boxed)
                var editable = document.createElement("span");
            else
                var editable = document.createElement("div");
            editable.id = name ;
            editable.class = 'labkey-edit-in-place';


            if(typeof experimentData[name] == 'string')
                editable.innerHTML = experimentData[name] != null ? experimentData[name].replace(/\n/g, '<br>') : '';
            else
                editable.innerHTML = experimentData[name];

            var error = document.createElement("div");
            error.id = editable.id + "-error";

            if(boxed)
            {
                var box = document.createElement('div');
                box.id = name+'-box';
                box.style.float = 'left';
                box.style.display = 'inline-block';
                box.style.width = (Ext.getBody().getWidth()/3) - 25+'px';


                if(title)
                    box.appendChild(header);

                box.appendChild(editable);
//                box.innerHTML = editable.innerHTML;

                document.getElementById('dropbox').appendChild(box);
            }
            else
            {
                if(title)
                    document.getElementById('dropbox').appendChild(header);

                document.getElementById('dropbox').appendChild(editable);
            }
            if(lineBreak)
            {
                document.getElementById('dropbox').appendChild(document.createElement('br'));
                document.getElementById('dropbox').appendChild(document.createElement('br'));
            }
            document.getElementById('errorBox').appendChild(error);

            if (!LABKEY.Security.currentUser.canUpdate)
                return;

            var errorMessage = new Ext.form.Label({
                renderTo : editable.id + '-error',
                style : 'color:red'
            });

            if(boxed)
            {
                console.log(header.offsetWidth);
            }
            if(!title)
                title = name;

            new LABKEY.ext.EditInPlaceElement({
                applyTo: name,
                editWidth : boxed? box.clientWidth - header.offsetWidth - 15 : null,
                multiLine: true,
                enterCompletesEdit : !multiline,
                emptyText: 'No '+title+' provided. Click to add one.',
                maxLength: maxLength,
                listeners : {
                    validitychange : function (field, isValid) {
                        if (isValid) {
                            errorMessage.setText('');
                        } else {
                            var errors = this.getErrors();
                            var msg = "Invalid value";
                            if (errors && errors.length > 0)
                                msg = errors[0];
                            errorMessage.setText(msg);
                        }
                    },
                    editstarted : function(){
                        editable.innerHTML = editable.innerHTML.replace(/<br>/g, '\n');
                    },
                    complete : function(){
                        submitParentElements();
                        var row = {
                            Container : experimentData.Container
                        };
                        row[name] = editable.innerHTML;
                        if(row[name] === this.emptyText)
                            row[name] = '';

                        LABKEY.Query.updateRows({
                            schemaName : 'OConnorExperiments',
                            queryName : 'Experiments',
                            rows : [row]
                        });

                        editable.innerHTML = editable.innerHTML.replace(/\n/g, '<br>');
                    },
                    canceledit : function(){
                        editable.innerHTML = editable.innerHTML.replace(/\n/g, '<br>');
                    }
                }
            });
        }

        function generateComboElement(title, name, maxLength, lineBreak, boxed)
        {

            var comboDrop = document.createElement("span");
            var error = document.createElement("div");
            error.id = comboDrop.id + "-error";

            if(boxed)
            {
                var box = document.createElement('div');
                box.id = name+'-box';
                box.style.float = 'left';
                box.style.display = 'inline-block';
                box.style.width = (Ext.getBody().getWidth()/3) - 25+'px';

                box.appendChild(comboDrop);

                document.getElementById('dropbox').appendChild(box);
            }
            else
            {
                if(title)
                    document.getElementById('dropbox').appendChild(header);

                document.getElementById('dropbox').appendChild(comboDrop);
            }
            if(lineBreak)
            {
                document.getElementById('dropbox').appendChild(document.createElement('br'));
                document.getElementById('dropbox').appendChild(document.createElement('br'));
            }
            document.getElementById('errorBox').appendChild(error);

            var combo = new Ext.form.ComboBox({
                store : new LABKEY.ext.Store({
                    columns : ['Key', 'title'],
                    schemaName : 'lists',
                    queryName : 'Grants',
                    containerPath : LABKEY.container.parentPath,
                    autoLoad : true,
                    listeners : {
                        load : function(store){
                            if(experimentData[name]);
                                combo.setValue(experimentData[name]);
                        }
                    }
                }),
                fieldLabel : '<strong>'+title+'</strong>',
                displayField : 'title',
                editable : false,
                valueField : 'Key',
                disabled : !LABKEY.Security.currentUser.canUpdate,
                listeners : {
                    select : function(cb){
                        var row = {
                            Container : experimentData.Container
                        };
                        row[name] = cb.getValue();

                        LABKEY.Query.updateRows({
                            schemaName : 'OConnorExperiments',
                            queryName : 'Experiments',
                            rows : [row]
                        });
                    }
                }
            });


            new Ext.form.FormPanel({
               renderTo : box.id,
                items : [combo],
                border : false,
                bodyStyle : 'background: none'

            });
        }
    });


</script>
