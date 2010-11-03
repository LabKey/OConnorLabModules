package org.labkey.genotyping;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.SimpleFilter;
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
public class GenotypingAnalysesView extends QueryView
{
    private final boolean _allowDelete;

    public static WebPartFactory FACTORY = new BaseWebPartFactory("Genotyping Analyses")
    {
        public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws Exception
        {
            WebPartView view = new GenotypingAnalysesView(ctx, null, "GenotypingAnalyses");
            view.setTitle("Genotyping Analyses");
            view.setTitleHref(GenotypingController.getAnalysesURL(ctx.getContainer()));
            return view;
        }
    };

    public GenotypingAnalysesView(ViewContext ctx, Errors errors, String dataRegion)
    {
        this(ctx, errors, dataRegion, null, false);
    }

    public GenotypingAnalysesView(ViewContext ctx, Errors errors, String dataRegion, @Nullable SimpleFilter baseFilter, boolean allowDelete)
    {
        super(getUserSchema(ctx), getQuerySettings(ctx, dataRegion, baseFilter), errors);
        setShadeAlternatingRows(true);
        setShowDeleteButton(allowDelete);
        _allowDelete = allowDelete;
    }

    @Override
    protected boolean canDelete()
    {
        return _allowDelete;
    }

    @Override
    public ActionButton createDeleteButton()
    {
        ActionButton btnDelete = new ActionButton(GenotypingController.DeleteAnalysesAction.class, "Delete");
        btnDelete.setActionType(ActionButton.Action.POST);
        btnDelete.setRequiresSelection(true, "Are you sure you want to delete these analyses?");
        return btnDelete;
    }

    private static UserSchema getUserSchema(ViewContext ctx)
    {
        return new GenotypingQuerySchema(ctx.getUser(), ctx.getContainer());
    }

    private static QuerySettings getQuerySettings(ViewContext ctx, String dataRegion, @Nullable SimpleFilter baseFilter)
    {

        QuerySettings settings = new QuerySettings(ctx, dataRegion, GenotypingQuerySchema.TableType.Analyses.toString());
        settings.setAllowChooseQuery(false);
        settings.setAllowChooseView(true);
        settings.getBaseSort().insertSortColumn("RowId");

        if (null != baseFilter)
            settings.getBaseFilter().addAllClauses(baseFilter);

        return settings;
    }
}
