package org.labkey.genotyping;

import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: Oct 7, 2010
 * Time: 10:47:28 AM
 */
public class GenotypingWebPart extends JspView
{
    public static WebPartFactory FACTORY = new BaseWebPartFactory("Genotyping")
    {
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new GenotypingWebPart();
        }
    };

    public GenotypingWebPart()
    {
        super("/org/labkey/genotyping/view/overview.jsp");
        setTitle("Genotyping Overview");
    }
}
