package org.labkey.oconnorexperiments;

import org.labkey.api.services.ServiceRegistry;

/**
 * User: kevink
 * Date: 6/7/13
 */
public class OConnorExperimentsService
{
    public static OConnorExperimentsService get()
    {
        return ServiceRegistry.get(OConnorExperimentsService.class);
    }
}
