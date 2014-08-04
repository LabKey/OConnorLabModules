/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
//2012-04-16 - added option for user to customize views to display other fields

//dependent on variables set in oconnorAlabrityConfig.js 

function createGrid(schema, query, view, title, render, dataRegion,customButtons)
{
//standard button bar elements for alabrity purchasing system
var buttonBarItems = [
					        LABKEY.QueryWebPart.standardButtons.deleteRows,
					        LABKEY.QueryWebPart.standardButtons.views,
							LABKEY.QueryWebPart.standardButtons.exportRows,
							LABKEY.QueryWebPart.standardButtons.pageSize,
							LABKEY.QueryWebPart.standardButtons.print,
							{text: 'Purchases', 
							items: [
            					{text: 'Request purchase', handler: makePurchase},
            					{text: 'My purchases', handler: myPurchases},
            					{text: 'All purchases', handler: allPurchases},
            					{text: 'Popular purchases', handler: popularPurchases},
            					{text: 'Mark', 
									items: [
            						{text: 'Purchases as ordered', handler: markOrdered},
            						{text: 'Purchases as received', handler: markReceived},
            						{text: 'Purchases as invoiced', handler: markInvoiced},
            						{text: 'Purchases as cancelled', handler: markCancelled}
         							]},
         						{text: 'Add keyword', handler: addKeyword}
         					]},
         					{text: 'Vendors',
         						items: [
         							{text: 'All vendors', handler: enabledVendors},
            						{text: 'Manage vendor quotes', handler: enabledQuotes}
         							]},
         					{text: 'Grants',
         					items: [
         						{text: 'All grants', handler: enabledGrants},
         						{text: 'Spending summary', handler: grantSummary}
         						]},
         					{text: 'Shipping Addresses', handler: enabledShipping}
         					
					 ];
					 
//add custom buttons from individual pages				 
var addCustomButtons = customButtons;
for(var i = 0; i < addCustomButtons.length; i++)
{
buttonBarItems.unshift(addCustomButtons[i]);
};

//if there are custom button bar items, set showRecordSelectors to true
if(addCustomButtons)
{
var recordSelect = true
}
else
{
var recordSelect = false
};

//draw labkey grid				 
var grid = new LABKEY.QueryWebPart({
	renderTo: render,
	title: title,
	schemaName: schema,
	queryName: query,
	allowChooseView: true,
	showReports: false,
	showRecordSelectors: recordSelect,
	dataRegionName: dataRegion,
	viewName: view,
    buttonBarPosition: 'top',
	buttonBar: {
					buttonBarPosition: 'top',
					includeStandardButtons: false,
					items: buttonBarItems
				}            				
	});
};

function makePurchase(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'purchase_item');};
function myPurchases(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'my_purchases');};
function allPurchases(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'all_purchases');};
function markOrdered(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'mark_ordered');};
function markReceived(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'mark_received');};
function markInvoiced(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'mark_invoiced');};
function markCancelled(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'mark_cancelled');};
function addKeyword(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'add_keyword');};
function enabledVendors(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'enabled_vendors');};
function enabledGrants(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'enabled_grants');};
function enabledShipping(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'enabled_shipping');};
function grantSummary(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'grant_summary');};
function popularPurchases(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'popular_purchases');};
function enabledQuotes(){window.location = LABKEY.ActionURL.buildURL(moduleName, 'enabled_vendor_quotes');};


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
