/*
 * Copyright (c) 2014-2017 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
//this function makes an Ext layout that can be used to add new specimens or batch edit existing ones. 
//basing this on specimen layout, so some names are still specimen just to make the migration faster
function layoutForm()
{
//enable quicktips

Ext.QuickTips.init();
		
//fields that allow only certain values should display as comboboxes. create data stores for these fields.
//i tried caching these on page load, but this was requiring unnecessary queries when people were viewing data but not necessarily adding or editing records
			
			var listOligoPurifications = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'oligo_purification',
				sort: 'oligo_purification',
				autoLoad: true
			});	

			var listOligoTypes = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'oligo_type',
				sort: 'oligo_type',
				autoLoad: true
			});	
			
			var listFreezerId = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'freezer_id',
				sort: 'freezer_id',
				autoLoad: true
			});	


//create form elements for sampleDetailsForm

			var sampleDetailsFormElements = 
				[{xtype: 'fieldset',
				title: 'Sample Details',
				labelWidth: 200,
				autoHeight: true,
				items:[
							{xtype: 'textarea',
							fieldLabel: 'Comments',
							id: 'comments',
							ref: 'comments',
							anchor: '95%',
							allowBlank: true}
					]
				}
				];
				
//create form elements for specimenDetailsForm
			
			var specimenDetailsFormElements = 
				[{xtype: 'fieldset',
				title: 'Oligonucleotide Details',
				labelWidth: 200,
				autoHeight: true,
				items:[
					{xtype: 'textfield',
					fieldLabel: 'Oligonucleotide Number*',
					id: 'oligo_number',
					ref: 'oligo_number',
					anchor: '95%',
					allowBlank: false},
					
					{xtype: 'textfield',
					fieldLabel: 'Oligonucleotide Name*',
					id: 'oligo_name',
					ref: 'oligo_name',
					anchor: '95%',
					allowBlank: false},

					{xtype: 'textfield',
					fieldLabel: 'Oligonucleotide Sequence*',
					id: 'oligo_sequence',
					ref: 'oligo_sequence',
					anchor: '95%',
					allowBlank: false},
					
					{xtype: 'combo',
					ref: 'oligo_type',
					id: 'oligo_type',
					store: listOligoTypes,
					listWidth: '350',
					fieldLabel: 'Oligonucleotide Types*',
					allowBlank: false,
					triggerAction: 'all',
					hideTrigger: false,
					editable: true,
					selectOnFocus: true,
					mode: 'local',
					forceSelection: true,
					valueField: 'oligo_type',
					anchor: '95%',
					displayField: 'oligo_type'
					},
					
					{xtype: 'combo',
					ref: 'oligo_modifications',
					id: 'oligo_modifications',
					store: listOligoPurifications,
					listWidth: '350',
					fieldLabel: 'Oligonucleotide Modification/Purification*',
					allowBlank: false,
					triggerAction: 'all',
					hideTrigger: false,
					editable: true,
					selectOnFocus: true,
					mode: 'local',
					forceSelection: true,
					valueField: 'oligo_purification',
					anchor: '95%',
					displayField: 'oligo_purification'
					},
					
					{xtype: 'textfield',
					fieldLabel: 'Oligonucleotide Target*',
					id: 'oligo_target',
					ref: 'oligo_target',
					anchor: '95%',
					allowBlank: true},
					
					{xtype: 'datefield',
					fieldLabel: 'Oligonucleotide Date',
					id: 'oligo_date',
					ref: 'oligo_date',
					anchor: '95%',
					allowBlank: true},
			
					]
				}
				];

//create form elements for labDetailsForm
			var labDetailsFormElements =
				[{xtype: 'fieldset',
				title: 'Lab Details',
				labelWidth: 200,
				autoHeight: true,
				items:[
						{xtype: 'textfield',
						fieldLabel: 'Investigator Initials*',
						id: 'initials',
						ref: 'initials',
						allowBlank: false,
						anchor: '95%'
						},
						
						{xtype: 'textfield',
						fieldLabel: 'Experiment Number',
						id: 'experiment',
						ref: 'experiment',
						anchor: '95%',
						allowBlank: true}
						]
				}
				];
			
//create form elements for freezerDetailsForm
			var freezerDetailsFormElements =
				[{xtype: 'fieldset',
				title: 'Freezer Details',
				labelWidth: 200,
				autoHeight: true,
				items:[
						{xtype: 'combo',
						ref: 'freezer',
						id: 'freezer',
						store: listFreezerId,
						listWidth: '100',
						fieldLabel: 'Freezer*',
						allowBlank: false,
						triggerAction: 'all',
						hideTrigger: false,
						editable: true,
						selectOnFocus: true,
						mode: 'local',
						forceSelection: true,
						valueField: 'rowid',
						displayField: 'freezer_id',
						anchor: '95%'
						},
		
						{xtype: 'textfield',
						fieldLabel: 'Cane*',
						id: 'cane',
						ref: 'cane',
						allowBlank: false,
						anchor: '95%'
						},
						
						{xtype: 'textfield',
						fieldLabel: 'Box*',
						id: 'box',
						ref: 'box',
						allowBlank: false,
						anchor: '95%'
						},
						
						{xtype: 'textfield',
						fieldLabel: 'Row',
						id: 'box_row',
						ref: 'box_row',
						anchor: '95%',
						allowBlank: true},
						
						{xtype: 'textfield',
						fieldLabel: 'Column',
						id: 'box_column',
						ref: 'box_column',
						anchor: '95%',
						allowBlank: true},
						
						{xtype: 'textfield',
						fieldLabel: 'Coordinate',
						id: 'coordinate',
						ref: 'coordinate',
						anchor: '95%',
						allowBlank: true}
						]	
				}
				];

//each of these forms has layout information. cannot be combined with the field definitions above to simplify code
		
		//create the sampleDetailsForm
			var sampleDetailsForm = 
				{columnWidth: .5,
				layout: 'form',
				border: false,
				items: sampleDetailsFormElements
				};
				
		//create the specimenDetailsFormElements
			var specimenDetailsForm = 
				{columnWidth: .5,
				layout: 'form',
				border: false,
				items: specimenDetailsFormElements
				};

		//create the labDetailsFormElements
			var labDetailsForm = 
				{columnWidth: 0.5,
				layout: 'form',
				border: false,
				items: labDetailsFormElements
				};				

		//create the freezerDetailsFormElements
			var freezerDetailsForm = 
				{columnWidth: 0.5,
				layout: 'form',
				labelWidth: 200,
				border: false,
				items: freezerDetailsFormElements
				};	
				
		//create the status box to display information about batch uploads	
			var displayStatus = 
				{xtype: 'fieldset',
				id: 'current_status',
				title: 'Status update',
				autoHeight: true,
				columnWidth: 1.0,
				html: 'Ready to add specimen...',
				border: false,
				};	
				
		//create form panel to hold the form. do not include buttons, because these define what action will happen to the formPanel. these are defined in inventory_specimen_available.html
			var specimenFormPanel = new Ext.FormPanel({
				frame: false,
				hideBorders: true,
				monitorValid: true,
				bodyStyle: 'background-color:#fff;padding: 5px',
				width: 780,
				items: [{
					bodyStyle: {
						margin: '0px 0px 15px 0px'
						},
					items: [{
						border: false,
						layout: 'column',
						items: [sampleDetailsForm, specimenDetailsForm]
						}]
				},
					{
					items: [{
						border: false,
						layout: 'column',
						items: [labDetailsForm, freezerDetailsForm]
						}]
				},
					{
					items: [{
						border: false,
						items: [displayStatus]
						}]
					}	
				]
			});

//return specimenFormPanel for use by other functions
return specimenFormPanel
			
};	