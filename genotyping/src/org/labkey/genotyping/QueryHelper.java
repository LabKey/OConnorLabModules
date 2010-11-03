/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 24, 2010
 * Time: 5:51:46 PM
 */
public class QueryHelper
{
    private final Container  _c;
    private final User _user;
    private final String _schemaName;
    private final String _queryName;
    private final @Nullable String _viewName;

    private QueryHelper(Container c, User user, String[] parts)
    {
        _c = c;
        _user = user;
        _schemaName = parts[0];
        _queryName = parts[1];
        _viewName = parts.length > 2 ? parts[2] : null;
    }

    public QueryHelper(Container c, User user, String schemaName, String queryName, @Nullable String viewName)
    {
        this(c, user, new String[]{schemaName, queryName, viewName});
    }

    public QueryHelper(Container c, User user, @NotNull String schemaQueryView)
    {
        this(c, user, schemaQueryView.split(GenotypingFolderSettings.SEPARATOR));
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    @Nullable
    public String getViewName()
    {
        return _viewName;
    }

    public TableInfo getTableInfo()
    {
        UserSchema schema = getUserSchema();
        return schema.getTable(_queryName);
    }

    public UserSchema getUserSchema()
    {
        return QueryService.get().getUserSchema(_user, _c, _schemaName);
    }

    public SimpleFilter getViewFilter()
    {
        if (_viewName != null)
        {
            CustomView baseView = getCustomView();

            if (baseView != null)
            {
                return getViewFilter(baseView);
            }
            else
            {
                throw new IllegalStateException("Could not find view " + _viewName + " on query " + _queryName + " in schema " + _schemaName + ".");
            }
        }

        return new SimpleFilter();
    }

    private CustomView getCustomView()
    {
        return QueryService.get().getCustomView(_user, _c, _schemaName, _queryName, _viewName);
    }

    private SimpleFilter getViewFilter(CustomView baseView)
    {
        SimpleFilter viewFilter = new SimpleFilter(); // TODO: add container filter

        // copy our saved view filter into our SimpleFilter via an ActionURL (yuck...)
        ActionURL url = new ActionURL();
        baseView.applyFilterAndSortToURL(url, "mockDataRegion");
        viewFilter.addUrlFilters(url, "mockDataRegion");

        return viewFilter;
    }

    // TODO: maintain the order of columns
    public Report.Results select(SimpleFilter extraFilter, List<FieldKey> columns) throws SQLException
    {
        QueryService qs = QueryService.get();
        TableInfo ti = getTableInfo();

        Map<FieldKey, ColumnInfo> map = qs.getColumns(ti, columns);
        Set<FieldKey> fieldKeys = new LinkedHashSet<FieldKey>();

        for (ColumnInfo col : map.values())
        {
            col.getRenderer().addQueryFieldKeys(fieldKeys);
        }

        map = qs.getColumns(ti, fieldKeys);
        Collection<ColumnInfo> cols = map.values();

        return new Report.Results(qs.select(ti, cols, extraFilter, null), map);
    }

    // TODO: Add support for filter & sort, move to QueryService
    public Report.Results select(SimpleFilter extraFilter) throws SQLException
    {
        CustomView view = getCustomView();

        return select(extraFilter, view.getColumns());
    }

    public ActionURL getQueryGridURL()
    {
        return QueryService.get().urlFor(_user, _c, QueryAction.executeQuery, _schemaName, _queryName);        
    }

    @Override
    public String toString()
    {
        return _schemaName + '.' + _queryName + '.' + (null == _viewName ? "" : _viewName);
    }
}
