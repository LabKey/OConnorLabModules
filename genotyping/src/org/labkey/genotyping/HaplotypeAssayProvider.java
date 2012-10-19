package org.labkey.genotyping;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
    public static final String LAB_ANIMAL_COLUMN_NAME = "labAnimalId";
    public static final String CUSTOMER_ANIMAL_COLUMN_NAME = "customerAnimalId";
    public static final String TOTAL_READS_COLUMN_NAME = "totalReads";
    public static final String IDENTIFIED_READS_COLUMN_NAME = "identifiedReads";
    public static final String[] HAPLOTYPE_COLUMN_NAMES = {"mamuAHaplotype1", "mamuAHaplotype2", "mamuBHaplotype1", "mamuBHaplotype2"};
    public static final List<Pair<String, String>> COLUMN_HEADER_MAPPING_PROPERTIES = Arrays.asList( // Pair: first = name, second = label
            new Pair<String, String>(LAB_ANIMAL_COLUMN_NAME, "Lab Animal ID"),
            new Pair<String, String>(CUSTOMER_ANIMAL_COLUMN_NAME, "Customer Animal ID"),
            new Pair<String, String>(TOTAL_READS_COLUMN_NAME, "Total # Reads Evaluated"),
            new Pair<String, String>(IDENTIFIED_READS_COLUMN_NAME, "Total # Reads Identified"),
            new Pair<String, String>(HAPLOTYPE_COLUMN_NAMES[0], "Mamu-A Haplotype 1"),
            new Pair<String, String>(HAPLOTYPE_COLUMN_NAMES[1], "Mamu-A Haplotype 2"),
            new Pair<String, String>(HAPLOTYPE_COLUMN_NAMES[2], "Mamu-B Haplotype 1"),
            new Pair<String, String>(HAPLOTYPE_COLUMN_NAMES[3], "Mamu-B Haplotype 2")
    );

    public HaplotypeAssayProvider()
    {
        super(LSID_PREFIX, LSID_PREFIX, HAPLOTYPE_DATA_TYPE);
    }

    @Override @NotNull
    public AssayTableMetadata getTableMetadata(ExpProtocol protocol)
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
    public FilteredTable createDataTable(final AssayProtocolSchema schema, boolean includeCopiedToStudyColumns)
    {
        FilteredTable table = (FilteredTable)new GenotypingQuerySchema(schema.getUser(), schema.getContainer()).getTable(GenotypingQuerySchema.TableType.AnimalAnalysis.name());
        table.getColumn("RunId").setFk(new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return schema.getTable(AssaySchema.getRunsTableName(schema.getProtocol(), false));
            }
        });
        return table;
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
        for (Pair<String, String> property : COLUMN_HEADER_MAPPING_PROPERTIES)
        {
            DomainProperty dp = addProperty(runDomain, property.first, PropertyType.STRING);
            dp.setLabel(property.second);
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
        for (Pair<String, String> prop : COLUMN_HEADER_MAPPING_PROPERTIES)
        {
            runProperties.add(prop.first);
        }

        return domainMap;
    }

    @Override
    public List<AssayDataCollector> getDataCollectors(@Nullable Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        return Collections.<AssayDataCollector>singletonList(new HaplotypeDataCollector());
    }
}
