/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 *
 * @class Genotyping.ext.IlluminaSampleExportPanel
 */

var defaultView = '[Library QC]';
var exportRows = null;
var currentURL = LABKEY.ActionURL;

var panel = Ext4.define('Genotyping.ext.IlluminaSampleExportPanel', {
    extend: 'Ext.panel.Panel',
    sectionNames: ['Header', 'Reads', 'Settings', 'Data'],
    ignoredSectionNames: ['Data'],

    initComponent: function(){

        //Store and model for the viewCombo
        Ext4.define('viewModel', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'default',     type : 'boolean'},
                {name : 'name',        type : 'string',  sortType: 'asUCText'},
                {name : 'viewDataUrl', type : 'string'}
            ]
        });

        var viewStore = Ext4.create('Ext.data.Store', {
            model : 'viewModel',
            sorters: { property: 'name', direction: 'ASC' }
        });

        Ext4.QuickTips.init();
        this.sequenceWarning = Ext4.create('Ext.form.Label', {
            text : 'Warning: Sample indexes do not support both color channels at each position.  See Preview Samples tab for more information.',
            style : 'color:red;',
            padding : '5px',
            hidden : true
        });

        // Use the hard-coded fields to validate; do this at init time since it won't change based on selected view
        var validationRows = this.getStandardDataSectionRows();
        this.validI7Rows = this.validateDataSectionRows(validationRows, 5);


        if ((validationRows.length < 8 && this.validI7Rows.indexOf(true) != -1 ) || (validationRows.length > 7))
        {
            this.validI5Rows = this.validateDataSectionRows(validationRows, 7);
        }

        if(this.validI7Rows == null && this.validI5Rows == null)
        {
            Ext4.apply(this, {
                title: 'Error',
                html: "<h4 style='color:red'> Row(s) with null 'fivemid' and 'threemid' value(s) selected. Unable to create 'Illumina Sample Sheet.'</h4>",
                buttons: [{
                    text: 'Back',
                    handler: function(btn){
                        window.history.back();
                    }
                }]
            });
        }
        else if(this.validI7Rows == null) {
            Ext4.apply(this, {
                title: 'Error',
                html: "<h4 style='color:red'> Row(s) with null 'fivemid' value(s) selected. Unable to create 'Illumina Sample Sheet.'</h4>",
                buttons: [{
                    text: 'Back',
                    handler: function(btn){
                        window.history.back();
                    }
                }]
            });
        }
        else if(this.validI5Rows == null && validationRows.length > 7) {
            Ext4.apply(this, {
                title: 'Error',
                html: "<h4 style='color:red'> Row(s) with null 'threemid' value(s) selected. Unable to create 'Illumina Sample Sheet.'</h4>",
                buttons: [{
                    text: 'Back',
                    handler: function(btn){
                        window.history.back();
                    }
                }]
            });
        }
        else
        {

            Ext4.apply(this, {
                title: 'Create Illumina Sample Sheet',
                itemId: 'illuminaPanel',
                width: '100%',
                validSequences: true,
                defaults: {
                    border: false
                },
                items: [{
                    html: 'You have chosen to export ' + this.rows.length + ' samples',
                    border: false,
                    bodyStyle: 'padding: 5px;',
                    style: 'padding-bottom: 5px;'
                }, this.sequenceWarning, {
                    xtype: 'tabpanel',
                    defaults: {
                        border: false
                    },
                    listeners: {
                        scope: this,
                        beforetabchange: this.onBeforeTabChange,
                        tabchange: this.onTabChange
                    },
                    items: [{
                        xtype: 'form',
                        title: 'General Info',
                        itemId: 'defaultTab',
                        bodyStyle: 'padding: 5px;',
                        defaults: {
                            width: 400,
                            labelWidth: 150,
                            maskRe: /[^,]/
                        },
                        items: [{
                            xtype: 'textfield',
                            allowBlank: false,
                            fieldLabel: 'Reagent Cassette Id',
                            helpPopup: 'This should match the ID of the Reagent cassette.  It will be used as the filename of the sample sheet.  If you do not have this value, you can always rename the file later',
                            itemId: 'fileName',
                            value: 'Illumina',
                            maskRe: /[0-9A-Za-z_-]/,
                            maxLength: 100
                        }, {
                            xtype: 'textfield',
                            itemId: 'investigator',
                            fieldLabel: 'Investigator Name',
                            value: LABKEY.Security.currentUser.displayName,
                            section: 'Header'
                        }, {
                            xtype: 'textfield',
                            itemId: 'experimentName',
                            fieldLabel: 'Experiment Number',
                            section: 'Header'
                        }, {
                            xtype: 'textfield',
                            itemId: 'projectName',
                            fieldLabel: 'Project Name',
                            section: 'Header'
                        }, {
                            xtype: 'datefield',
                            itemId: 'dateField',
                            fieldLabel: 'Date',
                            value: new Date(),
                            section: 'Header'
                        }, {
                            xtype: 'textfield',
                            itemId: 'description',
                            fieldLabel: 'Description',
                            section: 'Header'
                        }, {
                            xtype: 'combo',
                            itemId: 'customView',
                            fieldLabel: 'Custom View',
                            queryMode: 'local',
                            displayField: 'name',
                            valueField: 'name',
                            editable: false,
                            allowBlank: false,
                            section: 'Header',
                            store: viewStore,
                            value: defaultView,
                            listConfig: {
                                getInnerTpl: function (dfield)
                                {
                                    return '{' + dfield + ':htmlEncode}'; // 15202
                                }
                            }
                        }, {
                            xtype: 'combo',
                            itemId: 'template',
                            fieldLabel: 'Template',
                            queryMode: 'local',
                            allowBlank: false,
                            displayField: 'Name',
                            valueField: 'Name',
                            value: 'Default',
                            store: Ext4.create('LABKEY.ext4.Store', {
                                schemaName: 'genotyping',
                                queryName: 'IlluminaTemplates',
                                columns: 'Name,Json,Editable',
                                autoLoad: true,
                                supressErrorAlert: true,
                                exception: function (error)
                                {
                                    console.log('There was an error loading templates');
                                    console.log(error)
                                }
                            })
                        }],
                        listeners: {
                            render: {
                                fn: function ()
                                {
                                    LABKEY.Query.getQueryViews({
                                        scope: this,
                                        schemaName: 'genotyping',
                                        queryName: 'Samples',
                                        successCallback: function (details)
                                        {
                                            var filteredViews = [];
                                            filteredViews.push({name: defaultView});
                                            for (var i = 0; i < details.views.length; i++)
                                            {
                                                if (!details.views[i].hidden)
                                                {
                                                    // Skip default view, #16848
                                                    if (details.views[i].name === "" || details.views[i].default)
                                                        continue;

                                                    filteredViews.push(details.views[i]);
                                                }
                                            }
                                            viewStore.loadData(filteredViews);
                                        }
                                    });
                                }
                            }
                        }
                    }, {
                        title: 'Preview Header',
                        itemId: 'previewTab',
                        bodyStyle: 'padding: 5px;'
                    }, {
                        title: 'Preview Samples',
                        itemId: 'previewSamplesTab',
                        bodyStyle: 'padding: 5px;'
                    }]
                }],
                buttons: [{
                    text: 'Download',
                    handler: this.onDownload,
                    scope: this
                }, {
                    text: 'Save As Template',
                    handler: this.onSaveTemplate,
                    hidden: !LABKEY.Security.currentUser.canUpdate,
                    scope: this
                }, {
                    text: 'Cancel',
                    handler: function (btn)
                    {
                        var url = LABKEY.ActionURL.getParameter('srcURL');
                        if (url)
                            window.location = decodeURIComponent(url);
                        else
                            window.location = LABKEY.ActionURL.buildURL('project', 'start');
                    }
                }]
            });
        }

        this.callParent();

        //button should require selection, so this should never happen...
        if(!this.rows || !this.rows.length){
            this.hide();
            alert('No Samples Selected');
        }
    },

    onBeforeTabChange: function(){
        var template = this.down('#defaultTab').down('#template').getValue();
        if(!template){
            Ext4.Msg.alert('Error', 'You must choose a template');
            return false;
        }
    },

    onTabChange: function(panel, newTab, oldTab){
        if (newTab.itemId == 'defaultTab'){

        }
        else if (newTab.itemId == 'previewTab'){
            this.populatePreviewTab();
        }
        else if (newTab.itemId == 'previewSamplesTab'){
            this.populatePreviewSamplesTab();
        }

    },

    populatePreviewSamplesTab: function(){
        this.generateSamplesPreview();
    },

    populatePreviewTab: function(){
        var previewTab = this.down('#previewTab');

        var items = this.generateTemplatePreview();
        previewTab.removeAll();
        previewTab.add({
            border: false,
            xtype: 'form',
            defaults: {
                labelSeparator: '',
                labelWidth: 200,
                width: 500
            },
            items: items,
            buttonAlign: 'left',
            buttons: [{
                text: 'Edit Sheet',
                scope: this,
                handler: this.onEditTemplate
            }]
        });
    },

    parseText: function(text){
        text = text.split(/[\r\n|\r|\n]+/g);

        var vals = {};
        var activeSection = '';
        var errors = [];
        Ext4.each(text, function(line, idx){
            if(!line)
                return;

            line = line.split(/[\,|\t]+/g);

            if(line.length > 2)
                errors.push('Error reading line ' + (idx+1) + '. Line contains too many elements: "' + line.join(',') + '"');

            var prop = line.shift();
            if(prop.match(/^\[/)){
                prop = prop.replace(/\]|\[/g, '');
                if(this.sectionNames.indexOf(prop) == -1)
                    errors.push('Unknown section name: ' + prop);
                if(this.ignoredSectionNames.indexOf(prop) != -1)
                    errors.push('Cannot edit section: [' + prop + '] from this page');

                activeSection = prop;
                return;
            }

            if(!vals[activeSection])
                vals[activeSection] = [];

            var val = line.join('');
            vals[activeSection].push([prop, val]);
        }, this);

        if(errors.length){
            Ext4.Msg.alert("Error", errors.join('<br>'));
            return false;
        }

        return vals;
    },

    buildValuesObj: function(){
        var errors = [];
        this.down('form').items.each(function(field){
            if(field.isFormField && !field.isValid()){
                Ext4.each(field.getErrors(), function(e){
                    errors.push(field.fieldLabel + ': ' + e);
                }, this);
            }
        });

        if(errors.length){
            errors = Ext4.unique(errors);
            errors = errors.join('<br>');
            Ext4.Msg.alert("Error", "There are errors in the form:<br>" + errors);
            return;
        }

        var valuesObj = {};
        Ext4.each(this.sectionNames, function(header){
            this.down('form').items.each(function(item){
                if(item.section == header){
                    if(!valuesObj[header])
                        valuesObj[header] = [];

                    valuesObj[header].push([item.fieldLabel, item.getValue()]);
                }
            }, this);
        }, this);

        //apply values from the selected template
        var templateField = this.down('form').down('#template');
        var recIdx = templateField.store.find(templateField.valueField, templateField.getValue());
        var rec = templateField.store.getAt(recIdx);
        if(rec && rec.get('Json')){
            try
            {
                var json = Ext4.JSON.decode(rec.get('Json'));
                for (var i in json){
                    if(!valuesObj[i])
                        valuesObj[i] = [];

                    valuesObj[i] = valuesObj[i].concat(json[i]);
                }
            }
            catch (error) {
                alert('Something is wrong with this saved template');
            }
        }

        return valuesObj;
    },

    generateTemplatePreview: function(){
        var obj = this.buildValuesObj();
        if (!obj)
            return;

        var rows = [];
        Ext4.each(this.sectionNames, function(section){
            if(obj[section]){
                rows.push({
                    xtype: 'displayfield',
                    fieldLabel: '<b>[' + section + ']</b>'
                });

                Ext4.each(obj[section], function(row){
                    var value = Ext4.isDate(row[1]) ? Ext4.Date.format(row[1], 'm/d/Y') : row[1];

                    rows.push({
                        xtype: 'displayfield',
                        fieldLabel: row[0],
                        value: value,
                        htmlEncode: true
                    });
                }, this);
            }
        }, this);

        return rows;
    },

    generateSamplesPreview: function(){
        this.getExportDataSection(function(exportRows, sequenceColumns, badColumns, scope){
            if (sequenceColumns.length > 0)
            {
                scope.redGreenText(exportRows, sequenceColumns);
                exportRows.push(badColumns);
            }

            var table = {
                layout: {
                    type: 'table',
                    columns: exportRows[0].length,
                    defaults: {
                        border: false
                    }
                },
                items: []
            };

            Ext4.each(exportRows, function(row, idx){
                Ext4.each(row, function(cell){
                    table.items.push({
                        tag: 'div',
                        autoEl: {
                            style: 'padding: 5px;'
                        },
                        border: false,
                        style: idx ? null : 'border-bottom: black medium solid;',
                        html: Ext4.isEmpty(cell) ? '&nbsp;' : cell.toString()
                    });
                }, this);
            }, this);

            var previewTab = scope.down('#previewSamplesTab');

            previewTab.removeAll();
            previewTab.add({
                border: false,
                xtype: 'container',
                defaults: {
                    border: false
                },
                items: [table],
                buttonAlign: 'left',
                buttons: [{
                    text: 'Edit Samples',
                    hidden: true,
                    scope: this,
                    handler: this.onEditSamples
                }]
            });
        }, this);
    },

    positionMatches: function(validRows){
      var misses = "";
        for(var i = 0; i < validRows.length; i++){
            if(!validRows[i]){
                misses += '<span style="color:green">&nbsp;</span>';
            }
            else {
                misses += '<span style="color:red">X</span>';
            }
        }
        return '<span style="font-family:monospace; font-size:12pt">' + misses + '</span>';
    },

    // Apply red/green formatting to all sequences in the specified rows and columns
    redGreenText : function(rows, columns){
        for (var i = 1; i < rows.length; i++)
            for (var j = 0; j < columns.length; j++)
                this.redGreenTextRow(rows[i], columns[j]);
    },

    // Apply red/green formatting to a single sequence in the grid
    redGreenTextRow: function(row, index){
        var sequence = row[index];
        var coloredSequence = '';

        for (var q = 0; q < sequence.length; q++){
            var chr = sequence.charAt(q);
            if(chr == 'A' || chr == 'C'){
                coloredSequence += '<span style="color:red">' + chr + '</span>';
            }
            else if(chr == 'G' || chr == 'T'){
                coloredSequence += '<span style="color:green">' + chr + '</span>';
            }
        }

        row[index] = '<span style="font-family:monospace; font-size:12pt">' + coloredSequence + '</span>';
    },

    validateDataSectionRows: function(rows, col){
        var validRows = [];
        if(rows.length == 2) {
            this.validSequences = false;
            this.sequenceWarning.text = 'Warning: You have only selected one sequence.';
            this.sequenceWarning.show();
            return [true];
        }
        for(var pos = 0, target; pos < rows[1][col].length; pos++){
            validRows[pos] = true;
            if(rows[1][col].charAt(pos) == 'A' || rows[1][col].charAt(pos) == 'C'){
                target = 'RED';
            }
            else {
                target = 'GREEN';
            }

            for(var i = 2; i < rows.length; i++){
                if(rows[i][col] == null) {
                    return null;
                }
                if(target == 'RED' && (rows[i][col].charAt(pos) == 'A' || rows[i][col].charAt(pos) == 'C')){
                    continue;
                }
                else if (target == 'GREEN' && (rows[i][col].charAt(pos) == 'G' || rows[i][col].charAt(pos) == 'T')){
                    continue;
                }
                else {
                    validRows[pos] = false;
                    break;
                }
            }
            if(validRows[pos]){
                this.validSequences = false;
                this.sequenceWarning.show();
            }
        }
        return validRows;
    },

    generateHeaderArray: function(){
        var obj = this.buildValuesObj();
        if (!obj)
            return;

        var rows = [];
        Ext4.each(this.sectionNames, function(section){
            if(obj[section]){
                rows.push(['[' + section + ']']);
                Ext4.each(obj[section], function(row){
                    var value = Ext4.isDate(row[1]) ? Ext4.Date.format(row[1], 'm/d/Y') : row[1];
                    var thisRow = [row[0]];
                    if(!Ext4.isEmpty(value))
                        thisRow.push(value);

                    rows.push(thisRow);
                }, this);
            }
        }, this);

        return rows;
    },

    generateHeaderText: function(){
        var rowArray = [];
        Ext4.each(this.generateHeaderArray(), function(row){
            rowArray.push(row.join(','))
        }, this);

        return rowArray.join('\n');
    },

    getExportDataSection: function(finishExport, scope){
        var viewCombo = this.down('#defaultTab').down('#customView');

        if (viewCombo.value === defaultView)
        {
            this.finalizeExportDataSection(this.getStandardDataSectionRows(), finishExport, this)
        }
        else
        {
            var keys = [];
            Ext4.each(this.rows, function(row){
                keys.push(row.Key);
            });

            var pkFilter = LABKEY.Filter.create('Key', keys.join(";"), LABKEY.Filter.Types.IN);

            // Query the selected view and transform to the export format
            LABKEY.Query.selectRows({
                schemaName: 'genotyping',
                queryName: 'Samples',
                viewName: viewCombo.value,
                filterArray: [pkFilter],
                requiredVersion: 9.1,
                scope: this,
                success: function(data){
                    var exportRows = [];
                    var fields = data.metaData.fields;
                    var header = [];
                    Ext4.each(fields, function(fieldMetaData){
                        if (!fieldMetaData.hidden)
                            header.push(this.getHeaderCell(fieldMetaData.caption, fieldMetaData.fieldKey));
                    }, this);
                    exportRows.push(header);
                    Ext4.each(data.rows, function(row){
                        var toAdd = [];
                        Ext4.each(fields, function(fieldMetaData){
                            if (!fieldMetaData.hidden)
                            {
                                var field = row[fieldMetaData.name];
                                toAdd.push(field.displayValue ? field.displayValue : field.value);
                            }
                        }, this);
                        exportRows.push(toAdd);
                    }, this);
                    this.finalizeExportDataSection(exportRows, finishExport, this);
                },
                failure: function(errorInfo, options, responseObj)
                {
                    if (errorInfo && errorInfo.exception)
                        alert("Failure: " + errorInfo.exception);
                    else
                        alert("Failure: " + responseObj.statusText);
                }
            });
        }
    },

    finalizeExportDataSection: function(exportRows, finishExport, scope) {
        var headerRow = exportRows[0];
        var sequenceColumns = [];
        var badColumns = [];

        // Find the sequence columns based on header property... then stash the captions in the header
        for (var i = 0; i < headerRow.length; i++)
        {
            var cell = headerRow[i];

            if (cell.i5Sequence)
            {
                sequenceColumns.push(i);
                if(scope.validI5Rows)
                    badColumns[i] = scope.positionMatches(scope.validI5Rows);
            }
            else if (cell.i7Sequence)
            {
                sequenceColumns.push(i);
                badColumns[i] = scope.positionMatches(scope.validI7Rows);
            }

            headerRow[i] = cell.caption;
        }

        finishExport(exportRows, sequenceColumns, badColumns, scope);
    },

    getStandardDataSectionRows: function(){
        var exportRows = [];

        var sampleColumns = [
            ['Sample_ID', 'Key'],
            ['Sample_Name', 'library_sample_name'],
            ['Sample_Plate', 'Sample_Plate'],
            ['Sample_Well', 'Sample_Well'],
            ['Sample_Project', 'Sample_Project'],
            ['index', 'fivemid/mid_sequence'],
            ['I7_Index_ID', 'fivemid']
        ];

        // Only include the I5 columns if we have data for at least one row
        var hasI5 = false;
        Ext4.each(this.rows, function(row){
            if (row['threemid'])
            {
                hasI5 = true;
            }
        }, this);

        if (hasI5)
        {
            sampleColumns.push(
                ['index2', 'threemid/mid_sequence'],
                ['I5_Index_ID', 'threemid']
            );
        }

        sampleColumns.push(
            ['Description', 'description'],
            ['GenomeFolder', 'GenomeFolder']
        );

        var headerRow = [];
        Ext4.each(sampleColumns, function(col){
            headerRow.push(this.getHeaderCell(col[0], col[1]));
        }, this);
        exportRows.push(headerRow);

        Ext4.each(this.rows, function(row){
            var toAdd = [];
            Ext4.each(sampleColumns, function(col){
                toAdd.push(row[col[1]]);
            }, this);
            exportRows.push(toAdd);
        }, this);

        return exportRows;
    },

    // Mark i5 and i7 seqence columns based on fieldkey
    getHeaderCell: function(caption, fieldKey)
    {
        var columnHeader = {caption: caption};

        if (fieldKey === "threemid/mid_sequence")
            columnHeader.i5Sequence = true;
        else if (fieldKey === "fivemid/mid_sequence")
            columnHeader.i7Sequence = true;

        return columnHeader;
    },

    generateSampleText: function(){
        var text = '';
        var rows = this.getStandardDataSectionRows();
        Ext4.each(rows, function(row){
            text += row.join(',') + '\n';
        }, this);

        return text;
    },

    validateTemplate: function(){
        //TODO
    },

    onEditTemplate: function(btn){
        var tab = this.down('#previewTab');
        tab.removeAll();
        tab.add({
            xtype: 'form',
            bodyStyle: 'padding: 5px;',
            items: [{
                html: 'This view allows you to edit the raw text in the sample sheet.  The sheet is divided into sections, with each section beginning with a term in brackets (ie. \'[Header]\').  The supported section names are: ' + this.sectionNames.join(', ') + '; however, Data cannot be edited through this page.  Within each section, you can enter rows as name/value pairs, which are separated by a comma. When you are finished editing, hit \'Done Editing\' to view the result.<br><br>NOTE: None of the fields on the General Info tab will be included if you save this as a template.',
                width: 800,
                style: 'padding-bottom: 10px;',
                border: false
            },{
                xtype: 'textarea',
                itemId: 'sourceField',
                width: 800,
                height: 400,
                value: this.generateHeaderText()
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Done Editing',
                scope: this,
                handler: this.onDoneEditing
            },{
                text: 'Cancel',
                scope: this,
                handler: this.populatePreviewTab
            }]
        })
    },

    onEditSamples: function(btn){
        //TODO
    },

    onDoneEditing: function(){
        if(this.down('#previewTab').down('#sourceField')){
            var field = this.down('#sourceField');
            if(field.isDirty()){
                var val = this.parseText(field.getValue());
                if(val === false)
                    return false;

                this.setValuesFromText(val);
            }
        }
        this.populatePreviewTab();
    },

    setValuesFromText: function(values){
        var json = {};
        var form = this.down('#defaultTab');
        for (var section in values){
            Ext4.each(values[section], function(pair){
                var field = form.items.findBy(function(item){
                    return item.section == section && item.fieldLabel == pair[0];
                }, this);

                if(field){
                    field.setValue(pair[1])
                }
                else {
                    if(!json[section])
                        json[section] = [];

                    json[section].push(pair)
                }
            }, this);
        }

        var templateField = this.down('#defaultTab').down('#template');
        var recIdx = templateField.store.find('Name', 'Custom');
        if(recIdx == -1){
            var recs = templateField.store.add(new templateField.store.model({}, 'Custom'));
            recs[0].set({
                Name: 'Custom',
                Json: Ext4.JSON.encode(json)
            });
            recs[0].phantom = true;
        }
        else {
            templateField.store.getAt(recIdx).set('Json', Ext4.JSON.encode(json));
        }

        templateField.setValue('Custom');
    },

    onDownload: function(btn){
        if(this.onDoneEditing() === false)
            return;

        if(!this.down('form').getForm().isValid())
            return;

        var fileNamePrefix = this.down('#defaultTab').down('#fileName').getValue();
        if(!fileNamePrefix){
            alert('Must provide the flow cell Id, which will be used as the filename.  If you do not know this, fill out another value and rename the file later.');
            return;
        }

        var text = this.generateHeaderArray();
        text.push(['[Data]']);

        this.getExportDataSection(function(dataSection) {
            text = text.concat(dataSection);
            LABKEY.Utils.convertToTable({
                fileNamePrefix: fileNamePrefix,
                delim: 'COMMA',
                rows: text
            });
        })
    },

    onSaveTemplate: function(btn){
        //if we're editing the source, need to save first
        if(this.onDoneEditing() === false)
            return false;

        var field = this.down('#defaultTab').down('#template');
        var rec = field.store.getAt(field.store.find('Name', field.getValue()));

        if(!rec.dirty && !rec.phantom){
            alert('Template is already saved');
        }
        else {
            if(rec.phantom || !rec.get('Editable')){
                var isPhantom = rec.phantom;
                var msg = 'Choose a name for this template';
                if(!rec.get('Editable'))
                    msg = 'This template cannot be edited.  Please choose a different name:';

                Ext4.Msg.prompt('Choose Name', msg, function(btn, msg){
                    if(btn == 'ok'){
                        if(Ext4.isEmpty(msg)){
                            alert('Must enter a name');
                            this.onSaveTemplate();
                            return;
                        }

                        var idx = field.store.find('name', msg);
                        if(idx != -1){
                            alert('Error: name is already is use');
                            this.onSaveTemplate();
                            return;
                        }
                        rec.set('Name', msg);
                        rec.phantom = isPhantom;
                        this.down('#defaultTab').down('#template').setValue(msg);
                        this.saveTemplate(rec);
                    }
                }, this);
            }
            else {
                this.saveTemplate(rec);
            }
        }
    },

    saveTemplate: function(rec){
        rec.set('Editable', true);
        var config = {
            schemaName: 'genotyping',
            queryName: 'IlluminaTemplates',
            rows: [rec.data],
            scope: this,
            success: function(){
                var field = this.down('#defaultTab').down('#template');
                field.store.load();
            },
            failure: function(error){
                console.log('Error saving templates');
                console.log(error);
            }
        };

        if(rec.phantom){
            LABKEY.Query.insertRows(config)
        }
        else {
            LABKEY.Query.updateRows(config)
        }
    }
});
