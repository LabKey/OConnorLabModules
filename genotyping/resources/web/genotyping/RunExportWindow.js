/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.ns('LABKEY.Genotyping');

LABKEY.Genotyping.exportFilesBtnHandler = function(dataRegionName){
    var dr = LABKEY.DataRegions[dataRegionName];
    var selected = dr.getChecked();

    LABKEY.Query.selectRows({
        schemaName: 'genotyping',
        queryName: 'SequenceFiles',
        filterArray: [
            LABKEY.Filter.create('RowId', selected.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF)
        ],
        columns: 'RowId,DataId,DataId/Name,DataId/DownloadLink,ReadCount',
        scope: this,
        requiredVersion: 9.1,
        success: function(result){
            var ids = [];
            var readTotal = 0;
            if(result && result.rows.length){
                Ext4.each(result.rows, function(row){
                    ids.push(row.DataId.value);
                    readTotal += row.ReadCount.value;
                }, this);

                if(!ids.length){
                    alert('Error: no matching files were found')
                }
                //if you just checked 1 file, just download it directly
                else if (ids.length == 1){
                    var url = result.rows[0]['DataId/DownloadLink'].value;
                    console.log(url);
                    var form = Ext4.create('Ext.form.Panel', {
                        url: url,
                        standardSubmit: true
                    });
                    form.submit();
                }
                else {
                    Ext4.create('LABKEY.Genotyping.RunExportWindow', {
                        dataRegionName: dataRegionName,
                        readTotal: readTotal,
                        dataIds: ids
                    }).show();
                }

            }
        }
    })
}

Ext4.define('LABKEY.Genotyping.RunExportWindow', {
    extend: 'Ext.window.Window',
    initComponent: function(){
        Ext4.apply(this, {
            title: 'Export Files',
            modal: true,
            width: 400,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                defaults: {
                    width: 350
                },
                items: [{
                    html: 'You have chosen to export ' + this.readTotal + ' reads',
                    border: false,
                    style: 'padding-bottom: 15px;'
                },{
                    xtype: 'textfield',
                    allowBlank: false,
                    fieldLabel: 'File Prefix',
                    itemId: 'fileName',
                    name: 'filePrefix',
                    value: 'Sequences'
                },{
                    xtype: 'radiogroup',
                    itemId: 'exportType',
                    columns: 1,
                    fieldLabel: 'Export Files As',
                    items: [{
                        xtype: 'radio',
                        boxLabel: 'ZIP Archive of Individual Files',
                        name: 'exportType',
                        checked: true,
                        inputValue: 'zip'
                    },{
                        xtype: 'radio',
                        name: 'exportType',
                        boxLabel: 'Merge into Single FASTQ File',
                        inputValue: 'fastq'
                    }]
                }]
            }],
            buttons: [{
                text: 'Submit',
                handler: this.onSubmit,
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').destroy();
                }
            }]
        });

        this.callParent();

        //button should require selection, so this should never happen...
        var dr = LABKEY.DataRegions[this.dataRegionName];
        var selected = dr.getChecked();
        if(!selected.length){
            this.hide();
            alert('No Files Selected');
        }
    },

    onSubmit: function(btn){
        var win = btn.up('window');

        var fileNameField = win.down('#fileName');
        if(!fileNameField.getValue()){
            var msg = 'Must provide a filename';
            fileName.markInvalid(msg);
            alert(msg);
            return;
        }

        var exportType = this.down('#exportType').getValue().exportType;
        var fileName = fileNameField.getValue();
        var url;
        if(exportType == 'zip'){
            url = LABKEY.ActionURL.buildURL('experiment', 'exportFiles', null, {dataIds: this.dataIds, zipFileName: fileName + ".zip"});
        }
        else {
            url = LABKEY.ActionURL.buildURL('genotyping', 'mergeFastqFiles', null, {dataIds: this.dataIds, zipFileName: fileName});
        }

        var form = Ext4.create('Ext.form.Panel', {
            url: url,
            standardSubmit: true,
            items : [{ xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF }]
        });
        form.submit();

        win.destroy();
    }
})