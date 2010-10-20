package org.labkey.genotyping;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.springframework.validation.Errors;

/**
 * User: adam
 * Date: Oct 19, 2010
 * Time: 4:05:02 PM
 */
public class GenotypingRunsView extends QueryView
{
    public static WebPartFactory FACTORY = new BaseWebPartFactory("Genotyping Runs")
    {
        public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws Exception
        {
            WebPartView view = new GenotypingRunsView(ctx, null, "GenotypingRuns");
            view.setTitle("Genotyping Runs");
            return view;
        }
    };

    public GenotypingRunsView(ViewContext ctx, Errors errors, String dataRegion)
    {
        super(getUserSchema(ctx), getQuerySettings(ctx, dataRegion), errors);
        setShadeAlternatingRows(true);
    }

    private static UserSchema getUserSchema(ViewContext ctx)
    {
        return new GenotypingQuerySchema(ctx.getUser(), ctx.getContainer());
    }

    private static QuerySettings getQuerySettings(ViewContext ctx, String dataRegion)
    {
        QuerySettings settings = new QuerySettings(ctx, dataRegion, GenotypingQuerySchema.TableType.Runs.toString());
        settings.setAllowChooseQuery(false);
        settings.setAllowChooseView(true);
        settings.getBaseSort().insertSortColumn("RowId");

        return settings;
    }
}
