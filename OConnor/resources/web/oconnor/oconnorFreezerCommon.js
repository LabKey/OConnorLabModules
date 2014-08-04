/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
//2012-04-16 - created file to draw new ui for accessing freezer system
//will begin by creating options for viewing existing records, may eventually build new interface for sample upload

//moduleName and dbSchemaName set in oconnorAlabrityConfig.js 

function createGrid(schema, query, view, title, render, dataRegion,customButtons)
{
//customize content of grid toolbar
var buttonBarItems = [
					        LABKEY.QueryWebPart.standardButtons.deleteRows,
					        LABKEY.QueryWebPart.standardButtons.views,
							LABKEY.QueryWebPart.standardButtons.exportRows,
							LABKEY.QueryWebPart.standardButtons.pageSize,
							LABKEY.QueryWebPart.standardButtons.print,
							{text: 'Available Samples', 
								items: 
								[
            					{text: 'Specimens', handler: viewSpecimens},
            					{text: 'Plasmids', handler: viewPlasmids},
            					{text: 'Cell lines', handler: viewCellLines},
            					{text: 'Oligonucleotides', handler: viewOligos},
            					{text: 'Virus stocks', handler: viewStocks},
            					{text: 'Entire inventory', handler: viewAll},
            					{text: 'Inventory with SIV dates', handler: viewSIVDates}
            					]
            				},
         					{text: 'Edit Allowable Values', 
								items: 
								[
            					{text: 'Species', handler: viewSpecies},
            					{text: 'Specimen types', handler: viewSpecimenTypes},
            					{text: 'Sample statuses', handler: viewAvailability},
            					{text: 'Cell types', handler: viewCellTypes},
            					{text: 'DNA preparations', handler: viewDNAPreparations},
            					{text: 'Freezers', handler: viewFreezers},
            					{text: 'Laboratories', handler: viewLaboratories},
            					{text: 'Oligo purifications', handler: viewOligoPurification},
            					{text: 'Oligo types', handler: viewOligoType},
            					{text: 'Sample types', handler: viewSampleTypes},
            					{text: 'Specimen additives', handler: viewSpecimenAdditives},
            					{text: 'Specimen collaborators', handler: viewSpecimenCollaborators},
            					{text: 'Specimen geographic origins', handler: viewSpecimenGeographicOrigins},
            					{text: 'Specimen institutions', handler: viewSpecimenInstitutions},
            					{text: 'Specimen species', handler: viewSpecimenSpecies},
            					{text: 'Specimen types', handler: viewSpecimenTypes},
            					{text: 'Virus strains', handler: viewVirusStrains}
         						]
         					},
         					{text: 'Removed samples', handler: viewRemovedSamples}
					 ];
					 
//add custom buttons from individual pages				 
var addCustomButtons = customButtons;
for(var i = 0; i < addCustomButtons.length; i++)
{
buttonBarItems.unshift(addCustomButtons[i]);
};
					 
//draw labkey grid				 
var grid = new LABKEY.QueryWebPart({
	renderTo: render,
	title: title,
	schemaName: schema,
	showRecordSelectors: true,
	queryName: query,
	shoeDeleteButton: true,
	allowChooseView: true,
	showRecordSelectors: true,
	dataRegionName: dataRegion,
	viewName: view,
	frame: 'none',
    buttonBarPosition: 'top',
	buttonBar: {
					buttonBarPosition: 'top',
					includeStandardButtons: false,
					items: buttonBarItems
				}            				
	});
};

//define available sample functions to redirect to sample-specific pages
function viewSpecimens(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'inventory_specimen_available');};
function viewDNA(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'inventory_clinical_nucacid_available');};
function viewPlasmids(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'inventory_dna_available');};
function viewCellLines(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'inventory_cells_available');};
function viewOligos(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'inventory_oligo_available');};
function viewStocks(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'inventory_virus_available');};
function viewAll(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'inventory_all_samples');};
function viewSIVDates(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "inventory_specimens_siv_date"});};


//define batch edit functions
function batchEdit(){};

//define links to validation tables
function viewSpecies(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "all_species"});};
function viewSpecimenTypes(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "all_specimens"});};
function viewAvailability(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "availability"});};
function viewCellTypes(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "cell_type"});};
function viewDNAPreparations(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "dna_type"});};
function viewFreezers(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "freezer_id"});};
function viewLaboratories(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "laboratory"});};
function viewOligoPurification(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "oligo_purification"});};
function viewOligoType(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "oligo_type"});};
function viewSampleTypes(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "sample_type"});};
function viewSpecimenAdditives(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "specimen_additive"});};
function viewSpecimenCollaborators(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "specimen_collaborator"});};
function viewSpecimenGeographicOrigins(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "specimen_geographic_origin"});};
function viewSpecimenInstitutions(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "specimen_institution"});};
function viewSpecimenSpecies(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "specimen_species"});};
function viewSpecimenTypes(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "specimen_type"});};
function viewVirusStrains(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "virus_strain"});};

//define link to removed samples
function viewRemovedSamples(){window.location = LABKEY.ActionURL.buildURL("query", "executeQuery",LABKEY.ActionURL.getContainer(), {schemaName: "oconnor", queryName: "inventory_removed"});};


//generic failure
function onFailure(errorInfo, options, responseObj)
{
	if (errorInfo && errorInfo.exception)
		alert("Failure: " + errorInfo.exception);
	else
		alert("Failure: " + responseObj.statusText);
};

//generic success
function onSuccess(data)
{
	//dialog to user showing order status.
	var notice = ('Success!');

	//display notice to user
	var theWindow = new Ext.Window({
		title: 'Status update',
		width: 320,
		bodyStyle: 'background-color:#fff;padding: 10px',
		html: notice,
		modal: true,
		buttons: [{
			text:'OK',
			disabled:false,
			formBind: true,
			ref: '../submit',
			scope: this,
			handler: function()
			{
				reloadWin();
			}
		}]
	});

	theWindow.show();

	function reloadWin()
	{
		//refresh browser to show unordered products
		theWindow.hide();
	};
};
