/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: 10/12/12
 */
public class HaplotypeAssayProvider extends AbstractAssayProvider
{
    public static final String NAME = "Haplotype";
    public static final String LSID_PREFIX = "Haplotype";
    public static final AssayDataType HAPLOTYPE_DATA_TYPE = new AssayDataType(LSID_PREFIX, new FileType(".xls"));

    public static final String DATA_PROPERTY_NAME = "data";
    public static final String ENABLED_PROPERTY_NAME = "enabled";

    public static final HaplotypeColumnMappingProperty LAB_ANIMAL_COLUMN = new HaplotypeColumnMappingProperty("labAnimalId", "Lab Animal ID", true);
    public static final HaplotypeColumnMappingProperty CLIENT_ANIMAL_COLUMN = new HaplotypeColumnMappingProperty("clientAnimalId", "Client Animal ID", false);
    public static final HaplotypeColumnMappingProperty TOTAL_READS_COLUMN = new HaplotypeColumnMappingProperty("totalReads", "Total # Reads Evaluated", true);
    public static final HaplotypeColumnMappingProperty IDENTIFIED_READS_COLUMN = new HaplotypeColumnMappingProperty("identifiedReads","Total # Reads Identified", true);
    public static final HaplotypeColumnMappingProperty[] HAPLOTYPE_COLUMNS = {
            new HaplotypeColumnMappingProperty("mamuAHaplotype1", "Mamu-A Haplotype 1", true),
            new HaplotypeColumnMappingProperty("mamuAHaplotype2", "Mamu-A Haplotype 2", true),
            new HaplotypeColumnMappingProperty("mamuBHaplotype1", "Mamu-B Haplotype 1", true),
            new HaplotypeColumnMappingProperty("mamuBHaplotype2", "Mamu-B Haplotype 2", true)
    };

    public HaplotypeAssayProvider()
    {
        super(LSID_PREFIX, LSID_PREFIX, HAPLOTYPE_DATA_TYPE, ModuleLoader.getInstance().getModule(GenotypingModule.class));
    }

    @Override @NotNull
    public AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol)
    {
        return new AssayTableMetadata(this, protocol, null, FieldKey.fromParts("RunId"), FieldKey.fromParts("RowId"));
    }

    @Override
    public ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new HtmlView("");
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        return super.createBatchDomain(c, user, false);
    }

    @Override
    public AssayProtocolSchema createProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        return new HaplotypeProtocolSchema(user, container, protocol, targetStudy);
    }

    @Override
    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Collections.emptyList();
    }

    @Override
    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public PipelineProvider getPipelineProvider()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Imports manually assigned haplotype assignments";
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = super.createRunDomain(c, user);
        Domain runDomain = result.getKey();

        addProperty(runDomain, ENABLED_PROPERTY_NAME, PropertyType.BOOLEAN).setLabel("Enabled");

        // add run properties (hidden from insert view) that will be used for the mapping of the column headers for the input data
        for (Map.Entry<String, HaplotypeColumnMappingProperty> property : getColumnMappingProperties().entrySet())
        {
            DomainProperty dp = addProperty(runDomain, property.getKey(), PropertyType.STRING);
            dp.setLabel(property.getValue().getLabel());
            dp.setDescription("Used for mapping the column headers in the tsv data with this key field.");
            dp.setShownInInsertView(false);
            dp.setShownInUpdateView(false);
        }

        return result;
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = super.getRequiredDomainProperties();

        Set<String> runProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_RUN);
        if (runProperties == null)
        {
            runProperties = new HashSet<String>();
            domainMap.put(ExpProtocol.ASSAY_DOMAIN_RUN, runProperties);
        }
        runProperties.add(ENABLED_PROPERTY_NAME);
        for (String propName : getColumnMappingProperties().keySet())
        {
            runProperties.add(propName);
        }

        return domainMap;
    }

    @Override
    public List<AssayDataCollector> getDataCollectors(@Nullable Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        return Collections.<AssayDataCollector>singletonList(new HaplotypeDataCollector());
    }

    @Override
    public List<NavTree> getHeaderLinks(ViewContext viewContext, ExpProtocol protocol, ContainerFilter containerFilter)
    {
        List<NavTree> result = super.getHeaderLinks(viewContext, protocol, containerFilter);

        ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(viewContext.getContainer(), protocol, GenotypingController.DuplicateAssignmentReportAction.class);
        result.add(new NavTree("view duplicates", url));

        return result;
    }

    public static Map<String, HaplotypeColumnMappingProperty> getColumnMappingProperties()
    {
        Map<String, HaplotypeColumnMappingProperty> properties = new LinkedHashMap<String, HaplotypeColumnMappingProperty>();
        properties.put(LAB_ANIMAL_COLUMN.getName(), LAB_ANIMAL_COLUMN);
        properties.put(CLIENT_ANIMAL_COLUMN.getName(), CLIENT_ANIMAL_COLUMN);
        properties.put(TOTAL_READS_COLUMN.getName(), TOTAL_READS_COLUMN);
        properties.put(IDENTIFIED_READS_COLUMN.getName(), IDENTIFIED_READS_COLUMN);
        properties.put(HAPLOTYPE_COLUMNS[0].getName(), HAPLOTYPE_COLUMNS[0]);
        properties.put(HAPLOTYPE_COLUMNS[1].getName(), HAPLOTYPE_COLUMNS[1]);
        properties.put(HAPLOTYPE_COLUMNS[2].getName(), HAPLOTYPE_COLUMNS[2]);
        properties.put(HAPLOTYPE_COLUMNS[3].getName(), HAPLOTYPE_COLUMNS[3]);
        return properties;
    }

    public static Map<String, HaplotypeColumnMappingProperty> getColumnMappingProperties(ExpProtocol protocol)
    {
        Map<String, HaplotypeColumnMappingProperty> properties = new LinkedHashMap<String, HaplotypeColumnMappingProperty>();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain domain = provider.getRunDomain(protocol);
        DomainProperty[] props = domain.getProperties();
        properties.put(LAB_ANIMAL_COLUMN.getName(), LAB_ANIMAL_COLUMN);
        properties.put(CLIENT_ANIMAL_COLUMN.getName(), CLIENT_ANIMAL_COLUMN);
        properties.put(TOTAL_READS_COLUMN.getName(), TOTAL_READS_COLUMN);
        properties.put(IDENTIFIED_READS_COLUMN.getName(), IDENTIFIED_READS_COLUMN);

        String label;
        HashSet<String> defaults = getDefaultColumns();

        for (int i = 0; i < props.length; i++)
        {

           label = props[i].getLabel() != null ? props[i].getLabel() : ColumnInfo.labelFromName(props[i].getName());


            if(!props[i].isShownInInsertView() && (label.contains(" ")) && (label.endsWith("1") || label.endsWith("2")) && !defaults.contains(props[i].getName()))
                properties.put(props[i].getName(), new HaplotypeColumnMappingProperty(props[i].getName(), label, false));
        }

        return properties;
    }

    public static HashSet<String> getDefaultColumns(){
        HashSet<String> defaults = new HashSet<String>();
        defaults.add(HaplotypeAssayProvider.LAB_ANIMAL_COLUMN.getName());
        defaults.add(HaplotypeAssayProvider.CLIENT_ANIMAL_COLUMN.getName());
        defaults.add(HaplotypeAssayProvider.TOTAL_READS_COLUMN.getName());
        defaults.add(HaplotypeAssayProvider.IDENTIFIED_READS_COLUMN.getName());

        return defaults;
    }

    public static DomainProperty[] getDomainProps(ExpProtocol protocol){
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain domain = provider.getRunDomain(protocol);
        DomainProperty[] props = domain.getProperties();
        return props;
    }

}
