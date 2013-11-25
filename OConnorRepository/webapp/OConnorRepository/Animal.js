/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.oconnor.Animal', {

    extend : 'Ext.panel.Panel',

    border : false,
    frame : false,
    items : [],
    cls : 'wrapper',

    animalId : null,

    initComponent : function() {

        this.queryFilterArray = [new LABKEY.Query.Filter('Id', this.animalId, LABKEY.Filter.Types.EQUAL)];

        this.items.push(Ext4.create('Ext.Container', {
            items : [{
                xtype : 'container',
                cls : 'columninfo',
                width : 250,
                items : [this.getDemographicsCfg()]
            },{
                xtype : 'container',
                cls : 'columninfo',
                width : 650,
                items : [
                    this.getHaplotypesCfg(),
                    this.getViralChallengesCfg(),
                    this.getViralLoadsCfg(),
                    this.getPCRSSPTypingCfg(),
                    this.getGroupingCfg(),
                    this.getAliasCfg()
                ]
            }]
        }));

        this.callParent();
    },

    getDemographicsCfg : function() {

        var tpl = new Ext4.XTemplate(
                '<div id="demographics-content">',
                '<span class="header">Demographics</span>',
                '<table class="detail">',
                '<tr><td>',
                '<table cellspacing="7">',
                '<tr><td class="brown">&nbsp;</td><td>{gender.value}</td></tr>',
                '<tr><td class="rose">&nbsp;</td><td class="italic">{species.value}</td></tr>',
                '<tr><td class="blue">&nbsp;</td><td>{geoOrigin.value:this.renderNull}</td></tr>',
                '<tr><td class="gold">&nbsp;</td><td>{origin.value:this.renderNull}</td></tr>',
                '</table>',
                '<tr><td>&nbsp;</td></tr>',
                '<tr><td class="line">&nbsp;</td></tr>',
                //'<tr><td>{weight.value:this.renderWeight}</td></tr>',
                '<tr><td>{birth.value:this.renderAge}</td></tr>',
                '<tr><td>{death.value:this.renderLivingStatus}</td></tr>',
                '<tr><td>&nbsp;</td></tr>',
                '<tr><td class="line">&nbsp;</td></tr>',
                '<tr><td>',
                '<table>',
                '<tr><td width="100px">Sire</td><td>{sire.value}</td></tr>',
                '<tr><td>Dam</td><td>{dam.value}</td></tr>',
                //'<tr><td>Offspring</td><td>{offspring.value}</td></tr>',
                '</table>',
                '</td></tr>',
                '</table>',
                '</div>',
                {
                    renderWeight : function(weight) {
                        return this.renderNull(weight) + ' lbs';
                    },
                    renderAge : function(birthDate) {
                        if (null == birthDate) {
                            return null;
                        }
                        var d = new Date(birthDate);

                        // TODO: this should be calculated on the server in SQL
                        var age = Ext4.util.Format.round(Ext4.Date.getElapsed(d) / 31536000000, 0);
                        return age + ' years (Born ' + Ext4.Date.format(d, 'Y-m-d') + ')';
                    },
                    renderLivingStatus : function(deathDate) {
                        return deathDate != null ? 'Dead' : 'Living';
                    },
                    renderNull : function(v) {
                        if (v == null) {
                            return '--';
                        }
                        return v;
                    }
                }
        );

        var getDemographicData = function(cmp) {
            LABKEY.Query.selectRows({
                requiredVersion : '13.2',
                schemaName : 'study',
                queryName : 'Demographics',
                filterArray : this.queryFilterArray,
                success : function(data) {
                    if (data.getRowCount() > 0)
                        cmp.update(data.getRow(0));
                },
                failure : function() {
                    // do nothing
                },
                scope: this
            });
        };

        return {
            xtype : 'component',
            tpl : tpl,
            cls : 'main',
            border : false,
            frame : false,
            data : {},
            listeners : {
                scope : this,
                afterrender : getDemographicData
            }
        };
    },

    getHaplotypesCfg : function() {

        var tpl = new Ext4.XTemplate(
                '<div id="haplotype-content">',
                '<span class="header">Haplotypes</span>',
                '<table class="detail">',
                '<tr>',
                '<tpl for=".">',
                '<td width="100px">{value}</td>',
                '<tpl if="index % 2 == 1"></tr><tr></tpl>', // two haplotypes per row
                '</tpl>',
                '</tr>',
                '<tr><td>' + LABKEY.Utils.textLink({text: 'Full Details', href: LABKEY.ActionURL.buildURL('study', 'dataset', null, {datasetId: 5007, 'Dataset.Id~eq': this.animalId})}) + '</td></tr>',
                '</table>',
                '</div>'
        );

        var getHaplotypesData = function(cmp) {
            // TODO: this is a placeholder until we see what the copied-to-study Haplotype dataset name/definition looks like
            LABKEY.Query.selectRows({
                requiredVersion : '13.2',
                schemaName : 'study',
                queryName : 'Animal Haplotypes',
                filterArray : this.queryFilterArray,
                success : function(data) {
                    if (data.rows.length == 1)
                    {
                        var haplotypes = data.rows[0].ConcatenatedHaplotypes.value.split(',');
                        var haplotypeArr = [];
                        for (var i = 0; i < haplotypes.length; i++)
                            haplotypeArr.push({index: i, value: haplotypes[i]});

                        cmp.update(haplotypeArr);
                    }
                },
                failure : function() {
                    // do nothing
                },
                scope: this
            });
        };

        return {
            xtype : 'component',
            tpl : tpl,
            cls : 'main',
            border : false,
            frame : false,
            data : {},
            listeners : {
                scope : this,
                afterrender : getHaplotypesData
            }
        };
    },

    getViralChallengesCfg : function() {

        var tpl = new Ext4.XTemplate(
                '<div id="viralchallenge-content">',
                '<span class="header">Viral Challenges</span>',
                '<table class="detail">',
                '<tpl for=".">',
                '<tr><td width="175px">{date.value:this.renderDate}</td><td>{challenge_type.value} - {meaning.value}</td></tr>',
                '</tpl>',
                '<tr><td>' + LABKEY.Utils.textLink({text: 'Full Details', href: LABKEY.ActionURL.buildURL('study', 'dataset', null, {datasetId: 5003, 'Dataset.Id~eq': this.animalId})}) + '</td></tr>',
                '</table>',
                '</div>',
                {
                    renderDate : function(date) {
                        var d = new Date(date);
                        return Ext4.Date.format(d, 'Y-m-d h:i A');
                    }
                }
        );

        var getViralChallengesData = function(cmp) {
            LABKEY.Query.selectRows({
                requiredVersion : '13.2',
                schemaName : 'study',
                queryName : 'ViralChallenges',
                filterArray : this.queryFilterArray,
                success : function(data) {
                    cmp.update(data.rows);
                },
                failure : function() {
                    // do nothing
                },
                scope: this
            });
        };

        return {
            xtype : 'component',
            tpl : tpl,
            cls : 'main',
            border : false,
            frame : false,
            data : {},
            listeners : {
                scope : this,
                afterrender : getViralChallengesData
            }
        };
    },

    getViralLoadsCfg : function() {

        var tpl = new Ext4.XTemplate(
                '<div id="viralloads-content">',
                '<span class="header">Viral Loads</span>',
                '<table class="detail">',
                '<tpl for=".">',
                '<tr><td width="175px">{date.value:this.renderDate}</td><td>{ViralLoad.value} - {LogVL.value}</td></tr>',
                '</tpl>',
                '<tr><td>' + LABKEY.Utils.textLink({text: 'Full Details', href: LABKEY.ActionURL.buildURL('study', 'dataset', null, {datasetId: 5002, 'Dataset.Id~eq': this.animalId})}) + '</td></tr>',
                '</table>',
                '</div>',
                {
                    renderDate : function(date) {
                        var d = new Date(date);
                        return Ext4.Date.format(d, 'Y-m-d h:i A');
                    }
                }
        );

        var getViralLoadsData = function(cmp) {
            LABKEY.Query.selectRows({
                requiredVersion : '13.2',
                schemaName : 'study',
                queryName : 'ViralLoads',
                filterArray : this.queryFilterArray,
                success : function(data) {
                    cmp.update(data.rows);
                },
                failure : function() {
                    // do nothing
                },
                scope: this
            });
        };

        return {
            xtype : 'component',
            tpl : tpl,
            cls : 'main',
            border : false,
            frame : false,
            data : {},
            listeners : {
                scope : this,
                afterrender : getViralLoadsData
            }
        };
    },

    getPCRSSPTypingCfg : function() {

        var tpl = new Ext4.XTemplate(
                '<div id="pcrssptyping-content">',
                '<span class="header">PCR SSP Typing</span>',
                '<table class="detail">',
                '<tpl for=".">',
                '<tr><td width="175px">{date.value:this.renderDate}</td><td>{Allele.value} - {ShortName.value} - {Status.value}</td></tr>',
                '</tpl>',
                '<tr><td>' + LABKEY.Utils.textLink({text: 'Full Details', href: LABKEY.ActionURL.buildURL('study', 'dataset', null, {datasetId: 5004, 'Dataset.Id~eq': this.animalId})}) + '</td></tr>',
                '</table>',
                '</div>',
                {
                    renderDate : function(date) {
                        var d = new Date(date);
                        return Ext4.Date.format(d, 'Y-m-d h:i A');
                    }
                }
        );

        var getPCRSSPTypingData = function(cmp) {
            LABKEY.Query.selectRows({
                requiredVersion : '13.2',
                schemaName : 'study',
                queryName : 'PCR SSP Typing',
                filterArray : this.queryFilterArray,
                success : function(data) {
                    cmp.update(data.rows);
                },
                failure : function() {
                    // do nothing
                },
                scope: this
            });
        };

        return {
            xtype : 'component',
            tpl : tpl,
            cls : 'main',
            border : false,
            frame : false,
            data : {},
            listeners : {
                scope : this,
                afterrender : getPCRSSPTypingData
            }
        };
    },

    getGroupingCfg : function() {

        var tpl = new Ext4.XTemplate(
                '<div id="grouping-content">',
                '<span class="header">Groups</span>',
                '<table class="detail">',
                '<tr><td>{Groups}</td></tr>',
                '</table>',
                '<span class="header">Cohorts</span>',
                '<table class="detail">',
                '<tr><td>{Cohorts}</td></tr>',
                '</table>',
                '</div>'
        );

        var getGroupingData = function(cmp) {
            LABKEY.Query.selectRows({
                requiredVersion : '13.2',
                schemaName : 'study',
                queryName : 'ParticipantGroupCohortUnion',
                filterArray : this.queryFilterArray,
                success : function(data) {
                    var groups = [];
                    var cohorts = [];

                    Ext4.each(data.rows, function(row){
                        if (row.Cohort.value != null)
                            cohorts.push(row.Cohort.displayValue);
                        if (row.GroupId.value != null)
                            groups.push(row.GroupId.displayValue);
                    });

                    cmp.update({Groups: groups.length > 0 ? groups.join(', ') : '--', Cohorts: cohorts.length > 0 ? cohorts.join(', ') : '--'});
                },
                scope: this
            });
        };

        return {
            xtype : 'component',
            tpl : tpl,
            cls : 'main',
            border : false,
            frame : false,
            data : {},
            listeners : {
                scope : this,
                afterrender : getGroupingData
            }
        };
    },

    getAliasCfg : function() {

        var tpl = new Ext4.XTemplate(
                '<div id="alias-content">',
                '<span class="header">Aliases</span>',
                '<table class="detail">',
                '<tpl for=".">',
                '<tr><td>{SourceID.value}</td></tr>',
                '</tpl>',
                '</table>',
                '</div>',
                {
                    renderDate : function(date) {
                        var d = new Date(date);
                        return Ext4.Date.format(d, 'Y-m-d h:i A');
                    }
                }
        );

        var getAliasesData = function(cmp) {
            LABKEY.Query.selectRows({
                requiredVersion : '13.2',
                schemaName : 'study',
                queryName : 'Aliases',
                filterArray : this.queryFilterArray,
                success : function(data) {
                    cmp.update(data.rows);
                },
                failure : function() {
                    // do nothing
                },
                scope: this
            });
        };

        return {
            xtype : 'component',
            tpl : tpl,
            cls : 'main',
            border : false,
            frame : false,
            data : {},
            listeners : {
                scope : this,
                afterrender : getAliasesData
            }
        };
    }


});
