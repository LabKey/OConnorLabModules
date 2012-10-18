package org.labkey.genotyping;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: 10/12/12
 */
public class HaplotypeAssayProvider extends AbstractAssayProvider
{
    public static final String NAME = "Haplotype";
    public static final String LSID_PREFIX = "Haplotype";

    public HaplotypeAssayProvider()
    {
        super(LSID_PREFIX, LSID_PREFIX, new AssayDataType(LSID_PREFIX, new FileType(".xls")));
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
    public ContainerFilterable createDataTable(final AssayProtocolSchema schema, boolean includeCopiedToStudyColumns)
    {
        TableInfo table = new GenotypingQuerySchema(schema.getUser(), schema.getContainer()).getTable(GenotypingQuerySchema.TableType.AnimalHaplotypeAssignment.name());
        table.getColumn("RunId").setFk(new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return schema.getTable(AssaySchema.getRunsTableName(schema.getProtocol(), false));
            }
        });
        return (ContainerFilterable) table;
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
}
