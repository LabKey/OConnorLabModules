package org.labkey.genotyping;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssaySchema;

/**
 * User: jeckels
 * Date: 10/19/12
 */
public class HaplotypeProtocolSchema extends AssayProtocolSchema
{
    public HaplotypeProtocolSchema(User user, Container container, ExpProtocol protocol, Container targetStudy)
    {
        super(user, container, protocol, targetStudy);
    }

    @Override
    public FilteredTable createDataTable(boolean includeCopiedToStudyColumns)
    {
        FilteredTable table = (FilteredTable)new GenotypingQuerySchema(getUser(), getContainer()).getTable(GenotypingQuerySchema.TableType.AnimalAnalysis.name());
        table.getColumn("RunId").setFk(new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getTable(AssaySchema.getRunsTableName(getProtocol(), false));
            }
        });
        return table;
    }


}
