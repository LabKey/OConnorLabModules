package org.labkey.genotyping;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

/**
 * Render the concatenated haplotypes with each value linking to the haplotype definition list, if configured
 * Created by: jeckels
 * Date: 4/12/15
 */
public class ConcatenatedHaplotypesDisplayColumn extends DataColumn
{
    @NotNull
    private final Container _container;
    @NotNull
    private final TableInfo _haplotypeTableInfo;

    public ConcatenatedHaplotypesDisplayColumn(@NotNull ColumnInfo col, @NotNull Container container, @NotNull TableInfo haplotypeTableInfo)
    {
        super(col);
        _container = container;
        _haplotypeTableInfo = haplotypeTableInfo;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);
        if (o == null)
        {
            super.renderGridCellContents(ctx, out);
        }
        else
        {
            String values = o.toString();
            String[] haplotypes = values.split(",");
            String separator = "";
            for (String haplotype : haplotypes)
            {
                out.write(separator);
                separator = ", ";
                ActionURL url = _haplotypeTableInfo.getGridURL(_container).clone();
                // Filter the default grid URL to just show matches for this haplotype
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Haplotype"), haplotype);
                filter.applyToURL(url, "query");
                String evaluatedURL = url.getURIString();
                out.write("<a href=\"");
                out.write(PageFlowUtil.filter(evaluatedURL));
                out.write("\">");
                out.write(PageFlowUtil.filter(haplotype));
                out.write("</a>");
            }
        }
    }
}
