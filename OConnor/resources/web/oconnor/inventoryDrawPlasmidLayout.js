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
			
			var listDnaTypes = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'dna_type',
				sort: 'dna_type',
				autoLoad: true
			});	

			var listSpecimenSpecies = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'specimen_species',
				sort: 'specimen_species',
				autoLoad: true
			});	
			
			var listSpecimenGeographicOrigin = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'specimen_geographic_origin',
				sort: 'specimen_geographic_origin',
				autoLoad: true
			});
			
			var listSpecimenCollaborator = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'specimen_collaborator',
				sort: 'specimen_collaborator',
				autoLoad: true
			});	
			
			var listSpecimenAdditive = new LABKEY.ext.Store({
				schemaName: dbSchemaName,
				queryName: 'specimen_additive',
				sort: 'specimen_additive',
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
							{xtype: 'textfield',
							fieldLabel: 'Specimen ID',
							id: 'specimen_id',
							name: 'specimen_id',
							ref: 'specimen_id',
							anchor: '95%',
							allowBlank: true},
							
							{xtype: 'datefield',
							fieldLabel: 'Sample Date',
							id: 'sample_date',
							ref: 'sample_date',
							anchor: '95%',
							allowBlank: true},
			
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
				title: 'Plasmid Details',
				labelWidth: 200,
				autoHeight: true,
				items:[
					{xtype: 'combo',
					ref: 'dna_type',
					id: 'dna_type',
					store: listDnaTypes,
					listWidth: '350',
					fieldLabel: 'DNA Type*',
					allowBlank: false,
					triggerAction: 'all',
					hideTrigger: false,
					editable: true,
					selectOnFocus: true,
					mode: 'local',
					forceSelection: true,
					valueField: 'dna_type',
					anchor: '95%',
					displayField: 'dna_type'
					},
									
					{xtype: 'textfield',
					fieldLabel: 'DNA Vector',
					id: 'dna_vector',
					ref: 'dna_vector',
					anchor: '95%',
					allowBlank: true},
	
					{xtype: 'textfield',
					fieldLabel: 'DNA Insert',
					id: 'dna_insert',
					ref: 'dna_insert',
					anchor: '95%',
					allowBlank: true},
					
					{xtype: 'textarea',
					fieldLabel: 'DNA Sequence',
					id: 'dna_sequence',
					ref: 'dna_sequence',
					anchor: '95%',
					allowBlank: true},
						
					{xtype: 'combo',
					ref: 'specimen_species',
					id: 'specimen_species',
					store: listSpecimenSpecies,
					listWidth: '350',
					fieldLabel: 'Specimen Species',
					allowBlank: true,
					triggerAction: 'all',
					hideTrigger: false,
					editable: true,
					selectOnFocus: true,
					mode: 'local',
					forceSelection: true,
					valueField: 'specimen_species',
					displayField: 'specimen_species',
					anchor: '95%'
					},
									
					{xtype: 'combo',
					ref: 'specimen_geographic_origin',
					id: 'specimen_geographic_origin',
					store: listSpecimenGeographicOrigin,
					listWidth: '350',
					fieldLabel: 'Specimen Geographic Origin',
					allowBlank: true,
					triggerAction: 'all',
					hideTrigger: false,
					editable: true,
					selectOnFocus: true,
					mode: 'local',
					forceSelection: true,
					valueField: 'rowid',
					displayField: 'specimen_geographic_origin',
					anchor: '95%'
					},
	
					{
					xtype: 'combo',
					ref: 'specimen_collaborator',
					id: 'specimen_collaborator',
					store: listSpecimenCollaborator,
					listWidth: '350',
					fieldLabel: 'Specimen Collaborator',
					allowBlank: true,
					triggerAction: 'all',
					hideTrigger: false,
					editable: true,
					selectOnFocus: true,
					mode: 'local',
					forceSelection: true,
					valueField: 'rowid',
					displayField: 'specimen_collaborator',
					anchor: '95%'
					},
					
					{xtype: 'textfield',
					fieldLabel: 'Specimen Quantity',
					id: 'specimen_quantity',
					ref: 'specimen_quantity',
					anchor: '95%',
					allowBlank: true},
					
					{
					xtype: 'combo',
					id: 'specimen_additive',
					ref: 'specimen_additive',
					store: listSpecimenAdditive,
					listWidth: '350',
					fieldLabel: 'Specimen Additive',
					allowBlank: true,
					triggerAction: 'all',
					hideTrigger: false,
					editable: true,
					selectOnFocus: true,
					mode: 'local',
					forceSelection: true,
					valueField: 'rowid',
					displayField: 'specimen_additive',
					anchor: '95%'
					}
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
						fieldLabel: 'Genetics Services Animal ID',
						id: 'gs_id',
						ref: 'gs_id',
						anchor: '95%',
						allowBlank: true},
						
						{xtype: 'textfield',
						fieldLabel: 'Genetics Services Cohort ID',
						id: 'cohort_id',
						ref: 'cohort_id',
						anchor: '95%',
						allowBlank: true},
						
						{xtype: 'textfield',
						fieldLabel: 'Experiment Number',
						id: 'experiment',
						ref: 'experiment',
						anchor: '95%',
						allowBlank: true},
						
						{xtype: 'textfield',
						fieldLabel: 'Sample Number',
						id: 'sample_number',
						ref: 'sample_number',
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