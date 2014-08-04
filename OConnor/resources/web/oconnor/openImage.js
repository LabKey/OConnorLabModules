/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
	
	function openImage(dataReg){
				
    	var checked = dataReg.getChecked();
		console.log('checked: ' + checked);

    	
    	
		LABKEY.Query.selectRows({
			  queryName: "ELISpotResults",
   	       schemaName: "oconnor",
   	       containerPath: "/WNPRC/WNPRC_Laboratories/oconnor/",

            columns: ['Experiment/workbook','Well', 'Experiment'],
            filterArray: [LABKEY.Filter.create('rowid', checked.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)],
            successCallback: function(d){				
				           
            	
            	var wbks = [];
            	var wells = [];
            	var exps = [];
            	            	
            	for(var i = 0; i < d.rows.length; i++){
            		wbks.push(d.rows[i]["Experiment/workbook"]);
            		wells.push(d.rows[i]["Well"]);
            		exps.push(d.rows[i]["Experiment"]);
            	}
        		
                        	
            	var i = 0;
            	getImages(i,wbks, wells, exps);
            	
            	function getImages(i,workbooks,theWells, exps){


            		
            		LABKEY.Query.selectRows({
            		    schemaName: "exp",
            		    queryName: "Datas",
            		    containerPath: "/WNPRC/WNPRC_Laboratories/oconnor/experiments/workbook-" + workbooks[i],
            	        successCallback: function(d2) { 
    						
            	        	
            	        	if (d2.rows.length == 0){
            	        		alert('No images were found in experiment ' + exps[i]);
            	        	}else{
            	        	
	        	        		var found = false;
	            	        	for(var t = 0; t < d2.rows.length; t++){
	            	        		
	            	        		if(d2.rows[t].Name == theWells[i] + '.JPG'){
	           						
	            	        			found = true;
	            						var win = window.open(LABKEY.ActionURL.buildURL("experiment", "showData","WNPRC/WNPRC_Laboratories/oconnor/experiments/workbook-" + workbooks[i], {rowId : d2.rows[t].RowId }), '_blank', 'location = 0');
	            	        			break;
	            	        		}
	            	        		
	            	        	}
	            	        	
	            	        	if (!found){
	        	        			alert(theWells[i] + '.JPG was not found for experiment ' + exps[i]);
	        	        		}
            	        	
            	        	}
            	        	
            	        	i++;
            	        	if(i < wbks.length){
            	        		getImages(i, workbooks, theWells, exps);
            	        		
            	        	}
            	        }
            		});
            		
            	}
            	
            	
        		
            }
		
		});
	}