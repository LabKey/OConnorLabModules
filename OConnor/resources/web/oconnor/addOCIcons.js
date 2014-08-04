/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function addIcons() {
	
	
	var navPanel = document.getElementById('menubar');
	var menu = navPanel.childNodes[0].childNodes[0].childNodes[1].childNodes[0];
	var tableRow = document.createElement("span");	
	var context = LABKEY.ActionURL.getContextPath();
	
	var icons = '';
	
	var home = LABKEY.ActionURL.buildURL("project", "begin", "dho" );
	var inventory = LABKEY.ActionURL.buildURL("oconnor", "inventory_specimen_available", "dho");
	var shopping = LABKEY.ActionURL.buildURL("oconnor", "all_purchases", "dho");
	var exp = LABKEY.ActionURL.buildURL("oconnor", "experiments", "dho/experiments");
	//var expCreate = LABKEY.ActionURL.buildURL("oconnor", "Experiment_Create", "WNPRC/WNPRC_Laboratories/oconnor");
	//var expSearch = LABKEY.ActionURL.buildURL("oconnor", "Experiment_Search", "WNPRC/WNPRC_Laboratories/oconnor");
	//var bloodDraws = LABKEY.ActionURL.buildURL("oconnor", "BloodDraws", "WNPRC/WNPRC_Laboratories/oconnor");
	//var bloodCal = LABKEY.ActionURL.buildURL("oconnor", "BloodCalendar", "WNPRC/WNPRC_Laboratories/oconnor");
	//var animals = LABKEY.ActionURL.buildURL("oconnor", "AnimalList", "WNPRC/WNPRC_Laboratories/oconnor");


	icons += '<a style="padding-right:5px;" href=' + home + '>Home</a>';
	
	
	icons += '<a style="padding-right:5px;" href=' + inventory + '>Inventory</a>';
	icons += '<a style="padding-right:5px;" href=' + shopping + '>Purchasing</a>';

	//icons += '<a style="padding-right:5px;" href="#" onclick="jumpTo(); return false;"><img src="' + context + '/oconnor/jump.png" border="30"></a>';
	icons += '<a style="padding-right:5px;" href=' + exp + '>Experiments</a>';
	//icons += '<a style="padding-right:5px;" href=' + expSearch + '><img src="' + context + '/oconnor/folder.png" border="30"></a>';
	
	
	
	
	tableRow.innerHTML = icons;
	
//	tableRow.style.paddingLeft = '30px';

	var menu = navPanel.childNodes[0].childNodes[0].childNodes[1].childNodes[0];

	menu.parentNode.insertBefore(tableRow, menu);



	
}



function jumpTo(){
	var theWindow = new Ext.Window({
	       width: '220',
	       height: '100',
	       bodyStyle:'padding:5px',
	       closeAction:'hide',
	       closable:false,
	       plain: false,
	      
	       title: 'Jump To',
	       layout: 'form',
	       
	       items: [{	
	           emptyText:'',
	           fieldLabel: 'Experiment',
	           ref: 'exp',
	          width: 100,
	    //      value:'Enter Experiment',
	          selectOnFocus: true,
	           xtype: 'textfield',
	           required: true
			}],
			
	       buttons: [{
	           text:'Submit',
	           disabled:false,
	           formBind: true,
	           ref: 'submit',
	           scope: this,
	           handler: jump
	       },{	        
	           text: 'Cancel',
	           scope: this,
	           handler: function(){
	               theWindow.hide(); 
	           }
	       }],
	        keys: [
            	{ key: [Ext.EventObject.ENTER], handler: function() {
					jump(theWindow.exp.getValue());
            	}
          	  }]
            	       
	   });
	    

	   theWindow.show();      
		theWindow.exp.focus(true,10);	

	
	   function jump(experiment){
		   
			LABKEY.Query.selectRows({
		        schemaName: 'oconnor',
		        queryName: 'experiment_db',
		        columns: 'workbook',
				containerPath: 'WNPRC/WNPRC_Laboratories/oconnor/',
		        filterArray: [LABKEY.Filter.create('experiment_number', experiment, LABKEY.Filter.Types.EQUAL)],
		        successCallback: function(data){
		        	var wbcontainer = "/WNPRC/WNPRC_Laboratories/oconnor/experiments/workbook-" + data.rows[0].workbook;	        	
		        	window.location = LABKEY.ActionURL.buildURL('project','begin', wbcontainer);		        	
		        }
		    });	
	   }
}

function toExperiment(experiment){
	   
	LABKEY.Query.selectRows({
        schemaName: 'oconnor',
        queryName: 'experiment_db',
        columns: 'workbook',
		containerPath: 'WNPRC/WNPRC_Laboratories/oconnor/',
        filterArray: [LABKEY.Filter.create('experiment_number', experiment, LABKEY.Filter.Types.EQUAL)],
        successCallback: function(data){
        	var wbcontainer = "/WNPRC/WNPRC_Laboratories/oconnor/experiments/workbook-" + data.rows[0].workbook;	        	
        	window.location = LABKEY.ActionURL.buildURL('project','begin', wbcontainer);		        	
        }
    });	
}
