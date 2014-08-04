/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function removeSamples(dataReg){
	var checked = dataReg.getChecked();
	var theKeys = [];
	var theRows = [];
	
	var addNotes = new Ext.Window({
		width: 220,
		autoHeight: true,
		bodyStyle:'padding:5px',
		closeAction:'hide',
		closable: false,
		buttonAlign: 'left',
		title: 'Notes for Removed Samples',
		items: [{
			emptyText:'',
			label: 'Notes',
			xtype: 'textarea',
			width: 200,
			ref: 'notes',
		}],

		buttons: [{
			text:'Remove Samples',
			formBind: true,
			scope: this,
			handler: clearOld
		},{
			text: 'Cancel',
			scope: this,
			handler: function(){
				addNotes.hide();
			}
		}]

	});
		
	addNotes.show();
	
		function clearOld(){
		
		addNotes.hide();

		Ext.Msg.wait('Removing sample');

			
		LABKEY.Query.selectRows({
			timeout:100000000,
			schemaName: dataReg.schemaName,
			queryName: dataReg.queryName,
	        filterArray: [LABKEY.Filter.create('key', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
		   	containerPath: LABKEY.ActionURL.getContainer(),
		   	columns: '*',
			successCallback: function(data){
				for(var i = 0; i < data.rows.length; i++){
					tempKey = new Object({key : data.rows[i].key});
					theKeys.push(tempKey);
					theRows.push(data.rows[i]);
					delete theRows[i].key;
					
					var currentDate = new Date();
					theRows[i].status = 'removed';
					theRows[i].removed_date = currentDate.format('Y-m-d');
					theRows[i].removed_name = LABKEY.Security.currentUser.displayName;
					theRows[i].removed_notes = addNotes.notes.getValue();
					
					Ext.each(data.metaData.fields, function(field){
						if(theRows[i][field.name] && field.jsonType == 'date'){
							theRows[i][field.name] = new Date(theRows[i][field.name]);	
						}	
					}, this);					
				}

				
				LABKEY.Query.insertRows({
					timeout:100000000,
					schemaName: dataReg.schemaName,
					queryName: 'inventory_removed',
				   	containerPath: LABKEY.ActionURL.getContainer(),
					rows: theRows,
					successCallback: function(){
						
						LABKEY.Query.deleteRows({
							timeout:100000000,
							schemaName: dataReg.schemaName,
							queryName: dataReg.queryName,
						   	containerPath: LABKEY.ActionURL.getContainer(),
							rows: theKeys,
							successCallback: function(){
								window.location.reload()
							},
							failure: function(error){
							console.log(error);
							}
						});
						
					},
					failure: function(error){
					console.log(error);
					}
				});
			
			console.log(theRows);
			}
		});
		
	}
	
	
}


function updateRows(dataReg){
	selKeys = dataReg.getChecked();
	possCols = [];
	possValCols = [];
	
	LABKEY.Query.getQueryDetails({
		schemaName: dataReg.schemaName,
		queryName: dataReg.queryName,
		successCallback: function(data){
			for(var i = 0; i < data.columns.length; i++){
				
				console.log(data);
				
				if(data.columns[i].userEditable){
					possCols.push(data.columns[i].caption);
					possValCols.push(data.columns[i].name);
				}
			}
			displayUpdateWindow();
		}
	});

	function displayUpdateWindow(){
	
		var rowCount = 0;
		var theData = [];
		for(var i = 0; i < possCols.length; i++){
			theData.push([possCols[i], possValCols[i]]);
		}
		
		var theWindow = new Ext.Window({
			width: 365,
			autoHeight: true,
			bodyStyle:'padding:5px',
			closeAction:'hide',
			closable: false,
			plain: false,
			buttonAlign: 'left',
			title: 'Update Selected Rows',
			layout: 'table',
			layoutConfig: {
				columns: 2
			},
			items: [
			        {
				emptyText:'',
				xtype: 'displayfield',
				width: 150,
				required: true,
				value: 'Field',		
			}, {
				emptyText:'',
				xtype: 'displayfield',
				width: 150,
				required: true,
				value: 'New Value',		
			}
			],

			buttons: [{
				text:'Add Row',
				formBind: true,
				scope: this,
				handler: addRow
			},{
				text:'Delete Row',
				formBind: true,
				scope: this,
				handler: deleteRow
			},{
				text:'Update',
				formBind: true,
				scope: this,
				handler: update
			},{
				text: 'Cancel',
				scope: this,
				handler: function(){
					theWindow.hide();
				}
			}]

		});
		
		theWindow.show();
		addRow();

		function addRow(){

			rowCount++;
			var newRow = [];
						
			var combo = new Ext.form.ComboBox({
				typeAhead: true,
				typeAheadDelay: 0,
				allowBlank: false,
				triggerAction: 'all',
				lazyRender : true,
				ref: 'field' + rowCount,
				id: 'comboid' + rowCount,
				mode: 'local',
				forceSelection: true,
				store: new Ext.data.ArrayStore({
					id: 0,
					fields: [
					         'displayText',
					         'valueText'
					         ],
					         data: theData,
				}),
				valueField: 'valueText',
				displayField: 'displayText'
			});

			newRow.push(combo);
			
			newRow.push({
				emptyText:'',
				xtype: 'textfield',
				width: 150,
				id: 'textid' + rowCount,
				ref: 'value' + rowCount,
				required: true,
				value: '',		
			});
			
			theWindow.add(newRow);
			theWindow.doLayout();
		}

		function deleteRow(){
			if(rowCount>1){
				theWindow.remove('textid' + rowCount, true);
				theWindow.remove('comboid' + rowCount, true);
				rowCount--;
				theWindow.syncSize();
				theWindow.doLayout();
				theWindow.syncSize();
			}
		}

		function update(){

			var fields = [];
			var values = [];
			
			for(var i = 1; i <= rowCount; i++){
				values.push(eval('theWindow.value' + i + '.getValue()'));
				fields.push(eval('theWindow.field' + i + '.getValue()'));				
			}
			
			for(var t = 0; t < fields.length; t++){
				if(fields[t] == ''){
					alert('Please Select a Field for Each Row');
					return;
				}
			}
			
			var rowsToUpdate = [];
			
			for(var i = 0; i < selKeys.length; i++){
				rowsToUpdate.push({"Key" : parseInt(selKeys[i])})
				for(var t = 0; t < values.length; t++){
					rowsToUpdate[i][fields[t]]  = values[t];
				}
			}

			
			Ext.Msg.wait('Updating');


			console.log(rowsToUpdate);

			
			LABKEY.Query.updateRows({
				timeout:100000000,
				schemaName: dataReg.schemaName,
				queryName: dataReg.queryName,
				containerPath: "/WNPRC/WNPRC_Laboratories/oconnor/",
				rows: rowsToUpdate,
				successCallback: function(data){
					Ext.Msg.hide();
					window.location.reload( true );
				}
			});
		}
	}
}




function matrixElispotSelection(d, matrix1, matrix2, trad1, trad2){

	if(d != null){
		console.log(d.getChecked());	
	}
	
	var newExpsWin = new Ext.Window({
	        width: 190,
	        height: 180,
	        bodyStyle:'padding:5px',
	        closeAction:'hide',
	        closable: false,
	        plain: false,
	       
	       
	        title: 'Submit Requests',
	        layout: 'form',
	        items: [{
	            emptyText:''
	            ,fieldLabel: 'Matrix 1'
	            ,ref: 'm1'
	            ,xtype: 'textfield'
	            ,width: 50
	            ,value: matrix1
	            ,required: true
	        },{
	            emptyText:''
	            ,fieldLabel: 'Matrix 2'
	            ,ref: 'm2'
	            ,xtype: 'textfield'
	            ,width: 50
	            ,value: matrix2
	            ,required: true
	        },{
	            emptyText:''
	            ,fieldLabel: 'Traditional 1'
	            ,ref: 't1'
	            ,xtype: 'textfield'
	            ,width: 50
	            ,value: trad1
	            ,required: true
	        },{
	            emptyText:''
	            ,fieldLabel: 'Traditional 2'
	            ,ref: 't2'
	            ,xtype: 'textfield'
	            ,width: 50
	            ,value: trad2
	            ,required: true
		        }],
			
	        buttons: [{
	            text:'Submit',
	            ref: 'submitButton',
	            scope: this,
	            handler: submit
	        },{
	            text: 'Cancel',
	            scope: this,
	            handler: function(){
	            	newExpsWin.hide();
	            }
	        }],
	        keys: [
	             	{ key: [Ext.EventObject.ENTER], handler: function() {
						submit();
	            	}
	          	  }]
	        
	    });
	 	newExpsWin.show();  
	 	newExpsWin.m1.focus(true,10);	

		function submit(){
			
			var matrices = newExpsWin.m1.getValue() + ',' + newExpsWin.m2.getValue();
			var trads = newExpsWin.t1.getValue() + ',' + newExpsWin.t2.getValue();
			window.location = LABKEY.ActionURL.buildURL("oconnor", "Elispot_Matrix", "WNPRC/WNPRC_Laboratories/oconnor", {matrix:matrices, trad:trads});
		}	
}