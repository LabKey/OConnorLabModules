/*
 * Copyright (c) 2010-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.genotyping;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

public class GenotypingSchema
{
    private static final GenotypingSchema _instance = new GenotypingSchema();

    public static GenotypingSchema get()
    {
        return _instance;
    }

    private GenotypingSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.genotyping.GenotypingSchema.getInstance()
    }

    public String getSchemaName()
    {
        return "genotyping";
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(getSchemaName(), DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getDictionariesTable()
    {
        return getSchema().getTable("Dictionaries");
    }

    public TableInfo getRunsTable()
    {
        return getSchema().getTable("Runs");
    }

    public TableInfo getSequencesTable()
    {
        return getSchema().getTable("Sequences");
    }

    public TableInfo getSequenceFilesTable()
    {
        return getSchema().getTable("SequenceFiles");
    }

    public TableInfo getReadsTable()
    {
        return getSchema().getTable("Reads");
    }

    public TableInfo getMatchesTable()
    {
        return getSchema().getTable("Matches");
    }

    public TableInfo getAllelesJunctionTable()
    {
        return getSchema().getTable("AllelesJunction");
    }

    public TableInfo getReadsJunctionTable()
    {
        return getSchema().getTable("ReadsJunction");
    }

    public TableInfo getAnalysesTable()
    {
        return getSchema().getTable("Analyses");
    }

    public TableInfo getAnalysisSamplesTable()
    {
        return getSchema().getTable("AnalysisSamples");
    }

    public TableInfo getIlluminaTemplatesTable()
    {
        return getSchema().getTable("IlluminaTemplates");
    }

    public SchemaTableInfo getAnimalTable()
    {
        return getSchema().getTable("Animal");
    }

    public SchemaTableInfo getHaplotypeTable()
    {
        return getSchema().getTable("Haplotype");
    }

    public SchemaTableInfo getSpeciesTable()
    {
        return getSchema().getTable("Species");
    }

    public SchemaTableInfo getAnimalAnalysisTable()
    {
        return getSchema().getTable("AnimalAnalysis");
    }

    public SchemaTableInfo getAnimalHaplotypeAssignmentTable()
    {
        return getSchema().getTable("AnimalHaplotypeAssignment");
    }
}
