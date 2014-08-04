/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function moreInfo(checked){	
    var theWindow = new Ext.Window({
       width: 380,
       height: 150,
       bodyStyle:'padding:5px',
       closeAction:'hide',
       plain: false,
      
       title: 'More Info',
       layout: 'form',
       items: [{	
           emptyText:'',
           fieldLabel: 'Animal List',
           ref: 'list',
           width: 200,
           xtype: 'textarea',
           value: checked,
           required: false
		}],
		
       buttons: [{
           text:'VL Chart',
           disabled:false,
           formBind: true,
           ref: '../submit',
           scope: this,
           handler: submitChart
       },{
           text:'Blood Info',
           disabled:false,
           formBind: true,
           ref: '../submit',
           scope: this,
           handler: submitBlood
       },{
    	   text:'Animal History',
           disabled:false,
           formBind: true,
           ref: '../submit',
           scope: this,
           handler: submitHistory
       },{
           text: 'Cancel',
           scope: this,
           handler: function(){
               theWindow.hide();
           }
       }]
       
   });
    
   theWindow.show();      

	
	function submitChart(){
		var animalList =  theWindow.list.getValue();
		animalList = animalList.toLowerCase();
		animalList = animalList.split(' ').join('');
		
		var submitChart = LABKEY.ActionURL.buildURL("oconnor", "VLChart", "WNPRC/WNPRC_Laboratories/oconnor",{'animals' : animalList} );
		
		window.location = submitChart;
	}
	
	function submitHistory(){
		var animalList =  theWindow.list.getValue();
		animalList = animalList.toLowerCase();
		animalList = animalList.split(' ').join('');
		animalList = animalList.split(',').join(';');
		
		var submitHistory = LABKEY.ActionURL.buildURL("ehr", "animalHistory", "WNPRC/EHR");
		
		submitHistory += '?#_inputType:renderMultiSubject&_showReport:1&subject:' + animalList + '&activeReport:abstract';
		
		window.location = submitHistory;
				
	}
	
	function submitBlood(){
		var animalList =  theWindow.list.getValue();
		animalList = animalList.toLowerCase();
		animalList = animalList.split(' ').join('');
		
		var submitBlood = LABKEY.ActionURL.buildURL("oconnor", "BloodInfo", "/WNPRC/WNPRC_Laboratories/oconnor",{'animals' : animalList} );
		
		window.location = submitBlood;
		
	}


}
