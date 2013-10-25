
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
        if (exps && Ext.isString(exps))
        {
            var parts = exps.split(',');
            for (var i = 0; i < parts.length; i++)
            {
                var part = parts[i];
                if (part)
                    part = part.trim();
            }
            exps = parts;
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
                parentExperiments: experiment.parentExperiments,
                success: function (valid, json, exps) {

                    if (valid)
                    {
                        // Save the results
                        // NOTE: ParentExperiments must be an array of container entity ids
                        experiment.ParentExperiments = exps;
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
                        // TODO: display an error
                    }
                }
            });
        },

        createPanel: function (config) {
            var renderTo = config.renderTo;
            if (!renderTo)
                throw new Error("Expected renderTo");

            var experiment = config.experiment;
            if (!experiment)
                throw new Error("Expected experiment");

            var canEdit = LABKEY.Security.currentUser.canUpdate;

            /*
            var panel = new Ext.Panel({
                renderTo: renderTo,
                layout: 'table',
                border: false,
                //bodyStyle: 'background-color:transparent;',
                defaults: {
                    bodyStyle: 'padding:20px; background-color:transparent;'
                },
                layoutConfig: {
                    columns: 3,
                    tableAttrs: {
                        style: {
                            width: '100%'
                        }
                    }
                },
                items: [{
                    html: 'created'
                },{
                    rowspan: 3,
                    colspan: 2,
                    border: false,
                    background: false,
                    itemId: 'descriptionItem',
                    height: '100px',
                    html: '<div id="' + renderTo + '_description" style="minHeight:60px;">' +
                            Ext.util.Format.htmlEncode(experiment.getValue("Description") || '') +
                            '</div>'

                },{
                    html: 'type'
                },{
                    html: 'parents'
                },{
                    html: 'grants'
                }]
            });
            */

            /*
            var template = new Ext.XTemplate(
                '<table width="100%" style="cell-padding:20px">',
                '  <tr>',
                '    <td style="vertical-align:top;">',
                '      Created by {createdBy:htmlEncode} on {[values.created ? values.created.format("Y-m-d") : "NA"]}',
                '    </td>',
                '    <td style="vertical-align:top; rowspan:3">',
                '      <div id="{id}_description">{description:htmlEncode}</div>',
                '    </td>',
                '  </tr>',
                '  <tr>',
                '    <td style="vertical-align:top;">',
                '      <div id="{id}_type">{type:htmlEncode}</div>',
                '    </td>',
                '  </tr>',
                '  <tr>',
                '    <td style="vertical-align:top;">',
                '      parents',
                '    </td>',
                '  </tr>',
                '  <tr>',
                '    <td style="vertical-align:top;">',
                '      grants',
                '    </td>',
                '  </tr>',
                '  <tr>',
                '</table>'
            );

            var values = {
                id: renderTo,
                created: (experiment.getValue("Created") && new Date(experiment.getValue("Created"))) || null,
                createdBy: (experiment.get("CreatedBy") && experiment.get("CreatedBy").displayValue) || '[unknown]',
                description: experiment.getValue("Description") || ''
            };

            var overviewEl = template.insertFirst(renderTo, values, true);
            */

            var descriptionEditor;
            if (canEdit)
            {
                descriptionEditor = new LABKEY.ext.EditInPlaceElement({
                    applyTo: config.renderTo + '_description',
                    emptyText: 'Enter description'
                });
            }

        },

    };
};

