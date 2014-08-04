/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
//run this function when the add new experiment link is followed
function addExperiment(grid)
{
	 //create function to make text box and capture expDescription, expType, and expParent. needs to be nested to be in scope

		function makeForm()
		{
		var expDescription = new Ext.form.TextArea({
							fieldLabel: 'Description',
							name: 'expDescription',
							ref: 'expDescription',
							emptyText: '(required)',
							width: 300
							});

		var expComments = new Ext.form.TextArea({
							fieldLabel: 'Additional Comments',
							name: 'expComments',
							ref: 'expComments',
							emptyText: '(optional)',
							width: 300
							});

		var expParent = new Ext.form.TextField({
							fieldLabel: 'Parent Experiment',
							name: 'expParent',
							ref: 'expParent',
							emptyText: '(optional)',
							width: 300
							});

		var expType = new Ext.form.TextField({
							fieldLabel: 'Experiment Type',
							name: 'expType',
							ref: 'expType',
							emptyText: '(optional)',
							width: 300
							});

		var theForm = new Ext.FormPanel({
			xtype: 'form',
			  layout: 'form',
			  width: 450,
			  ref: 'theForm',
			  border: false,
			  labelWidth: 130,
			  items:[expDescription, expComments, expParent, expType]
			  });

		var theWindow = new Ext.Window({
			title: 'Create experiment',
			width: 500,
			bodyStyle: 'background-color:#fff;padding: 10px',
			items: theForm,
			buttonAlign: 'center',
			buttons: [{
						   text:'Submit',
						   disabled:false,
						   formBind: true,
						   ref: '../submit',
						   scope: this,
						   handler: function()
								{
								theWindow.hide();
								var expDescription = theWindow.theForm.expDescription.getValue();
								var expComments = theWindow.theForm.expComments.getValue();
								var expParent = theWindow.theForm.expParent.getValue();
								var expType = theWindow.theForm.expType.getValue();
								addExp(expDescription,expComments,expParent,expType);
								}
						 },
						 {
						   text:'Cancel',
						   disabled:false,
						   formBind: true,
						   ref: '../submit',
						   scope: this,
						   handler: function(){
							theWindow.hide()
							}
						 }]
		});

		theWindow.show();
		theWindow.theForm.expDescription.focus();
		};

		//draw text box. calls updateRecords function on submit of text box to update records
		makeForm();


//add experiment in labkey

function addExp(expDescription,expComments,expParent,expType)
        		{
        		//log all arguments to addExp
				console.log(["addExp arguments: ", arguments]);

        		//get current date
        		var now = new Date();

        		//get current user
        		var displayName =  LABKEY.Security.currentUser.displayName;

				//get last experiment number in container and add one integer to calculate next experiment number
				LABKEY.Query.executeSql({
                     		schemaName: 'oconnor',
                     		sql: 'SELECT (1+e.expNumber) AS exp FROM simple_experiment e ORDER BY e.expNumber DESC LIMIT 1',
                     		success: writeTotals
             				});

function writeTotals(data)
				{
				//log all arguments to writeTotals
				console.log(["writeTotals arguments: ", arguments]);

				//if no existing experiment numbers in sql query from above (as in a new installation), initialize first experiment as number 1
							if (data.rows.length == 0)
							{
							nextExpNum = 1;
							}
							else
							{
							nextExpNum = data.rows[0].exp;
							};

                LABKEY.Query.insertRows({
                schemaName: 'oconnor',
                queryName: 'simple_experiment',
                rowDataArray: [
                {expNumber: nextExpNum,
                expDescription: expDescription,
                expComments: expComments,
                expParent: expParent,
                expType: expType,
                created: now,
                initials: displayName}],
                successCallback: onSuccess,
                failureCallback: onFailure
                });
                };
                };

function onSuccess(data)
{
    //log all data entered into insertRows
    console.log(["inserted data", data.rows[0]]);

    //create folder to hold experiment files, code from Mark Igra 2012-03-04.
    var dirName = nextExpNum;
    //uses fileBrowser.js. must be loaded at top of script
    var fileSystem = new LABKEY.FileSystem.WebdavFileSystem({baseUrl:'/_webdav' + window.encodeURI(LABKEY.container.path + '/@files') });
    fileSystem.createDirectory({path: dirName, success: directoryCreationSuccess, failure: function() {alert('Test')}});
    return false;
}

function directoryCreationSuccess(fs, path)
{
    //dialog to user showing order status.
    var notice = ('Experiment number ' + nextExpNum + ' added by ' + LABKEY.Security.currentUser.displayName);

    //display notice to user
    var theWindow = new Ext.Window({
        title: 'Status',
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
                            //refresh browser
                            theWindow.hide();
                            window.location.reload( true );
                        }
                     }]
    });

    theWindow.show();
};
}

//generic failure. disabled on 2012-04-06 because of innocuous error when run on ehr.primate.wisc.edu
function onFailure(errorInfo, options, responseObj)
{
//             if(errorInfo && errorInfo.exception)
//             alert("Failure: " + errorInfo.exception);
//             else
//             alert("Failure: " + responseObj.statusText);
};
