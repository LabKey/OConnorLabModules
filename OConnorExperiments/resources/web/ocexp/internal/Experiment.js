/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ocexp.internal.InPlaceText', {
    extend : 'Ext.container.Container',
    alias: 'ocexp-text',

    emptyText: 'Click to edit',
    emptyCls: Ext4.baseCSSPrefix + 'form-empty-field',

    constructor : function(config){
        config.layout = 'card';
        this.callParent([config]);
        this.addCls("ocexp-edit-in-place-text");
    },

    initComponent: function(){
        this.displayField = Ext4.create('Ext.form.field.Display', {
            fieldLabel: this.fieldLabel,
            labelWidth: this.labelWidth,
            value: !this.value || this.value == '' ? this.emptyText : this.value,
            listeners: {
                scope: this,
                render: function(cmp){
                    this.applyEmptyStyle();
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
                        var oldValue = this.oldValue;
                        this.oldValue = this.textInput.getValue();
                        this.fireEvent('change', this, this.textInput.getValue(), oldValue);
                    }
                }
            }
        });

        this.items = [this.displayField, this.textInput];

        this.callParent();
    },

    applyEmptyStyle: function(){
        if (this.emptyText && this.displayField.getValue() == this.emptyText) {
            // Unfortunately, the 'x4-form-display-field' rule is taking precedence over the 'x4-form-empty-field' rule.
            //this.displayField.getActionEl().addCls(this.emptyCls);
            this.displayField.getActionEl().setStyle("color", "gray");
        } else {
            //this.displayField.getActionEl().removeCls(this.emptyCls);
            this.displayField.getActionEl().setStyle("color", "");
        }
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
            this.applyEmptyStyle();
        }
        this.getLayout().setActiveItem(this.displayField.getId());
    },

    setDisplayValue: function(value){
        this.displayField.setValue(value);
    },

    setValue: function(value){
        this.displayField.setValue(value == '' || value == null ? this.emptyText : value);
        this.textInput.setValue(value);
        this.applyEmptyStyle();
    },

    getValue: function(){
        return this.textInput.getValue();
    }
});

Ext4.define('LABKEY.ocexp.internal.InPlaceTextArea', {
    extend : 'Ext.container.Container',
    alias: 'ocexp-textarea',

    emptyText: 'Click to edit',
    emptyCls: Ext4.baseCSSPrefix + 'form-empty-field',

    constructor: function(config){
        config.layout = 'card';
        this.callParent([config]);
        this.addCls("ocexp-edit-in-place-text");
    },

    initComponent: function(){
        this.displayField = Ext4.create('Ext.form.field.Display', {
            emptyText: this.emptyText,
            fieldLabel: this.fieldLabel,
            labelWidth: this.labelWidth,
            value: !this.value || this.value == '' ? this.emptyText : Ext4.String.htmlEncode(this.value).replace(/\n/g, '<br />'),
            listeners: {
                scope: this,
                render: function(cmp){
                    this.applyEmptyStyle();
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
                        var oldValue = this.oldValue;
                        this.oldValue = this.textArea.getValue();
                        this.fireEvent('change', this, this.textArea.getValue(), oldValue);
                    }
                }
            }
        });

        this.items = [this.displayField, this.textArea];

        this.callParent();
    },

    applyEmptyStyle: function(){
        if (this.emptyText && this.displayField.getValue() == this.emptyText) {
            // Unfortunately, the 'x4-form-display-field' rule is taking precedence over the 'x4-form-empty-field' rule.
            //this.displayField.getActionEl().addCls(this.emptyCls);
            this.displayField.getActionEl().setStyle("color", "gray");
        } else {
            //this.displayField.getActionEl().removeCls(this.emptyCls);
            this.displayField.getActionEl().setStyle("color", "");
        }
    },

    showInput: function(){
        this.oldValue = this.textArea.getValue();
        this.getLayout().setActiveItem(this.textArea.getId());
        this.textArea.focus(false);
    },

    showDisplayField: function(){
        var inputValue = this.textArea.getValue();
        inputValue = Ext4.String.htmlEncode(inputValue).replace(/\n/g, '<br />');
        this.displayField.setValue(inputValue == '' || inputValue == null ? this.emptyText : inputValue);
        this.applyEmptyStyle();
        this.getLayout().setActiveItem(this.displayField.getId());
    },

    setValue: function(value){
        this.displayField.setValue(value == '' || value == null ? this.emptyText : value);
        this.textArea.setValue(value);
        this.applyEmptyStyle();
    },

    setDisplayValue: function(value){
        this.displayField.setValue(value);
    },

    getValue: function(){
        return this.textArea.getValue();
    }
});

if (!LABKEY.ocexp)
    LABKEY.ocexp = {};

if (!LABKEY.ocexp.internal)
    LABKEY.ocexp.internal = {};

LABKEY.ocexp.internal.Experiment = new function () {

    /** Remove duplicate items in an Array. */
    function uniq(a) {
        var o = {};
        for (var i = 0; i < a.length; i++)
        {
            var item = a[i];
            if (item.length > 0)
                o[item] = true;
        }

        var ret = [];
        for (var key in o)
        {
            if (o.hasOwnProperty(key))
                ret.push(key);
        }

        return ret;
    }

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
        if (exps != undefined && exps != null && Ext4.isString(exps))
        {
            if (exps == "") { // Empty string is ok, it just means we don't have any parents.
                exps = [];
            } else {
                var parts = exps.split(/[\s;,]+/);
                for (var i = 0; i < parts.length; i++)
                {
                    var part = parts[i];
                    if (part)
                        part = part.trim();
                }
                exps = parts;
            }
        }

        if (!exps || !Ext4.isArray(exps))
            throw new Error("Expected array of strings");

        exps = uniq(exps);

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
            config = Ext4.applyIf(config, {
                requiredVersion: 13.2,
                schemaName: 'OConnorExperiments',
                queryName: 'Experiments',
                columns: ['ExperimentNumber', 'Description', 'ExperimentTypeId', 'ParentExperiments/Container', 'ParentExperiments/ExperimentNumber', 'Created', 'CreatedBy', 'CreatedBy/DisplayName', 'GrantId', 'Container'],
                success: config.success,
                failure: config.failure,
                viewName:  'BlankExperiments'  // needed otherwise the default view is used which has an unwanted filter
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
                success: function (valid, json, exps) {
                    if (valid)
                    {
                        // Create ParentExperiments array of container entity ids
                        var parentExpContainers = [];
                        for(var i = 0; i < json.rows.length; i++) {
                            parentExpContainers.push(json.rows[i].Container);
                        }
                        experiment.ParentExperiments = uniq(parentExpContainers);

                        // Save the results
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
                        // Create array of values that weren't found on the server
                        var invalidValues = Ext4.Array.clone(exps);
                        for (var i = 0; i < json.rows.length; i++) {
                            Ext4.Array.remove(invalidValues, "" + json.rows[i].ExperimentNumber);
                        }

                        var msg = "Experiment " + (invalidValues.length > 1 ? "numbers" : "number") + " not found";
                        if (invalidValues.length > 0)
                            msg += ": " + invalidValues.join(", ");

                        if (config.invalid)
                            config.invalid(msg);
                        else
                            config.failure();
                    }
                }
            });
        }
    };
};
