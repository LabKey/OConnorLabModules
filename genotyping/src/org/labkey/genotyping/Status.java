/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

    private static final Map<Integer, Status> _map = new HashMap<>();

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
