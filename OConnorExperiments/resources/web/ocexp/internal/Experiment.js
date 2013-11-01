/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.ocexp)
    LABKEY.ocexp = {};

if (!LABKEY.ocexp.internal)
    LABKEY.ocexp.internal = {};

LABKEY.ocexp.internal.Experiment = new function () {

    /**
     * Checks the given experiment numbers actually exist in the parent container.
     * @param config
     * @param {String} config.parentExperiments A comma separated list of experiment numbers or an array of experiment number strings.
     * @param {Function} config.success A function that expects argument: boolean 'valid', json response, and an array of parent experiment numbers.
     */
    function validateParentExperiments(config) {
        var success = config.success;
        if (!success)
            throw new Error("Success callback required");

        var exps = config.parentExperiments;

        // Convert string into array of strings
        if (exps != undefined && exps != null && Ext.isString(exps))
        {
            if (exps == "") { // Empty string is ok, it just means we don't have any parents.
                exps = [];
            } else {
                var parts = exps.split(',');
                for (var i = 0; i < parts.length; i++)
                {
                    var part = parts[i];
                    if (part)
                        part = part.trim();
                }
                exps = parts;
            }
        }

        if (!exps || !Ext.isArray(exps))
            throw new Error("Expected array of strings");


        // Check if there are experiments that match the given ExperimentNumbers
        var filterValue = exps.join(";");
        LABKEY.Query.selectRows({
            schemaName: 'OConnorExperiments',
            queryName: 'Experiments',
            containerPath: LABKEY.container.parentPath,
            filterArray: [ LABKEY.Filter.create('ExperimentNumber', filterValue, LABKEY.Filter.Types.IN) ],
            success: function (json) {
                // Consider the values valid if we've received the same number of rows as in the parent experiments array.
                var valid = json.rows.length == exps.length;
                success(valid, json, exps);
            },
            failure: LABKEY.Utils.getOnFailure(config)
        });
    }

    return {

        /**
         * Get the Experiment row for the current container.
         * NOTE: ParentExperiments is a multi-value FK so the values for the columns
         * 'ParentExperiments/Container' and 'ParentExperiments/ExperimentNumber' will
         * be an array of values.
         *
         * @param config
         */
        getExperiment: function (config) {
            config = Ext.applyIf(config, {
                requiredVersion: 13.2,
                schemaName: 'OConnorExperiments',
                queryName: 'Experiments',
                columns: ['ExperimentNumber', 'Description', 'ExperimentType', 'ParentExperiments/Container', 'ParentExperiments/ExperimentNumber', 'Created', 'CreatedBy', 'CreatedBy/DisplayName', 'GrantId'],
                success: config.success,
                failure: config.failure
            });

            LABKEY.Query.selectRows(config);
        },


        /**
         * Save the Experiment row for the current container.
         * NOTE: ParentExperiments is a multi-value FK and the values must
         * be an array of container entity ids.
         *
         * @param config
         */
        saveExperiment: function (config) {
            var experiment = config.experiment;
            if (!experiment)
                throw new Error("Expected experiment");

            validateParentExperiments({
                parentExperiments: experiment['ParentExperiments/ExperimentNumber'],
                success: function (valid, json) {
                    if (valid)
                    {
                        experiment.ParentExperiments = [];
                        for(var i = 0; i < json.rows.length; i++) {
                            experiment.ParentExperiments.push(json.rows[i].Container);
                        }
                        // Save the results
                        // NOTE: ParentExperiments must be an array of container entity ids
                        LABKEY.Query.updateRows({
                            requiredVersion: 13.2,
                            schemaName: 'OConnorExperiments',
                            queryName: 'Experiments',
                            rows: [ experiment ],
                            success: config.success,
                            failure: config.failure
                        });
                    }
                    else
                    {
                        config.failure();
                    }
                }
            });
        }
    };
};

Ext4.onReady(function(){
    Ext4.define('LABKEY.ocexp.internal.InPlaceText', {
        extend : 'Ext.container.Container',
        alias: 'ocexp-text',

        constructor : function(config){
            config.layout = 'card';
            this.callParent([config]);
        },

        initComponent: function(){
            this.displayField = Ext4.create('Ext.form.field.Display', {
                fieldLabel: this.fieldLabel,
                labelWidth: this.labelWidth,
                value: !this.value || this.value == '' ? this.emptyText : this.value,
                listeners: {
                    scope: this,
                    render: function(cmp){
                        cmp.getEl().on('click', this.showInput, this);
                    }
                }
            });

            this.textInput = Ext4.create('Ext.form.field.Text', {
                fieldLabel: this.fieldLabel,
                labelWidth: this.labelWidth,
                value: this.value,
                listeners: {
                    scope: this,
                    blur: function(){
                        this.showDisplayField();
                        if(this.oldValue != this.textInput.getValue()) {
                            this.fireEvent('change', this, this.textInput.getValue(), this.oldValue);
                        }
                    }
                }
            });

            this.items = [this.displayField, this.textInput];

            this.callParent();
        },

        showInput: function(){
            this.oldValue = this.textInput.getValue();
            this.oldDisplayValue = this.displayField.getValue();
            this.getLayout().setActiveItem(this.textInput.getId());
            this.textInput.focus(true);
        },

        showDisplayField: function(){
            var inputValue = this.textInput.getValue();
            if(this.oldValue == inputValue) {
                this.displayField.setValue(this.oldDisplayValue);
            } else {
                this.displayField.setValue(inputValue == '' || inputValue == null ? this.emptyText : inputValue);
            }
            this.getLayout().setActiveItem(this.displayField.getId());
        },

        setDisplayValue: function(value){
            this.displayField.setValue(value);
        },

        setValue: function(value){
            this.displayField.setValue(value == '' || value == null ? this.emptyText : value);
            this.textInput.setValue(value);
        },

        getValue: function(){
            return this.textInput.getValue();
        }
    });

    Ext4.define('LABKEY.ocexp.internal.InPlaceTextArea', {
        extend : 'Ext.container.Container',
        alias: 'ocexp-textarea',

        constructor: function(config){
            config.layout = 'card';
            this.callParent([config]);
        },

        initComponent: function(){
            this.displayField = Ext4.create('Ext.form.field.Display', {
                fieldLabel: this.fieldLabel,
                labelWidth: this.labelWidth,
                value: !this.value || this.value == '' ? this.emptyText : Ext4.String.htmlEncode(this.value).replace(/\n/g, '<br />'),
                listeners: {
                    scope: this,
                    render: function(cmp){
                        cmp.getEl().on('click', this.showInput, this);
                    }
                }
            });

            this.textArea = Ext4.create('Ext.form.field.TextArea', {
                fieldLabel: this.fieldLabel,
                labelWidth: this.labelWidth,
                value: this.value,
                listeners: {
                    scope: this,
                    blur: function(){
                        this.showDisplayField();
                        if(this.oldValue != this.textArea.getValue()) {
                            this.fireEvent('change', this, this.textArea.getValue(), this.oldValue);
                        }
                    }
                }
            });

            this.items = [this.displayField, this.textArea];

            this.callParent();
        },

        showInput: function(){
            this.oldValue = this.textArea.getValue();
            this.getLayout().setActiveItem(this.textArea.getId());
            this.textArea.focus(true);
        },

        showDisplayField: function(){
            var inputValue = this.textArea.getValue();
            inputValue = Ext4.String.htmlEncode(inputValue).replace(/\n/g, '<br />');
            this.displayField.setValue(inputValue == '' || inputValue == null ? this.emptyText : inputValue);
            this.getLayout().setActiveItem(this.displayField.getId());
        },

        setValue: function(value){
            this.displayField.setValue(value == '' || value == null ? this.emptyText : value);
            this.textArea.setValue(value);
        },

        setDisplayValue: function(value){
            this.displayField.setValue(value);
        },

        getValue: function(){
            return this.textArea.getValue();
        }
    });
});