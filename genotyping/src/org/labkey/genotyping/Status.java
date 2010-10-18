package org.labkey.genotyping;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Oct 16, 2010
 * Time: 6:05:41 PM
 */
public enum Status
{
    NotSubmitted(0), Submitted(1), Importing(2), Complete(3);

    private static final Map<Integer, Status> _map = new HashMap<Integer, Status>();

    static
    {
        for (Status status : values())
            _map.put(status.getStatusId(), status);
    }

    public static Status getStatus(int statusId)
    {
        return _map.get(statusId);
    }

    private int _statusId;

    private Status(int statusId)
    {
        _statusId = statusId;
    }

    public int getStatusId()
    {
        return _statusId;
    }
}
