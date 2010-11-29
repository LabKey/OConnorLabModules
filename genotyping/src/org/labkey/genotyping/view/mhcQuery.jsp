<%
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
%>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.dialect.SqlDialect" %>
<%@ page import="org.labkey.genotyping.ImportAnalysisJob" %>
<%@ page extends="org.labkey.api.jsp.JspContext" %>
<%
    ImportAnalysisJob.QueryContext ctx = (ImportAnalysisJob.QueryContext)getModelBean();
    SqlDialect dialect = ctx.schema.getSqlDialect();
%>
SELECT  sample,
        alleles,
        CAST(COUNT(*) AS INT) AS reads,
        CAST(COUNT(*) AS REAL)/total_reads AS <%=dialect.getColumnSelectName("percent")%>,
        AVG(avg_length) AS avg_length,
        CAST(SUM(pos_reads) AS INT) AS pos_reads,
        CAST(SUM(neg_reads) AS INT) AS neg_reads,
        CAST(SUM(pos_ext_reads) AS INT) AS pos_ext_reads,
        CAST(SUM(neg_ext_reads) AS INT) AS neg_ext_reads,
        <%=dialect.getGroupConcat(new SQLFragment("rowid"), true, false)%> AS ReadIds
    FROM <%=ctx.samples%> samples
    INNER JOIN <%=ctx.reads%> reads ON samples.mid_num = reads.mid AND reads.Run = <%=ctx.run%>
    INNER JOIN
    (
        SELECT mid, COUNT(*) AS total_reads
        FROM <%=ctx.reads%> reads
        WHERE reads.Run = <%=ctx.run%>
        GROUP BY mid
    ) read_count ON read_count.mid = reads.mid
    INNER JOIN
    (
        SELECT read_name, <%=dialect.getGroupConcat(new SQLFragment("match"), false, true)%> AS Alleles, AVG(length) AS Avg_Length,
            CASE WHEN direction = '+' THEN 1 ELSE 0 END AS pos_reads,
            CASE WHEN direction = '-' THEN 1 ELSE 0 END AS neg_reads,
            CASE WHEN direction = '+ext' THEN 1 ELSE 0 END AS pos_ext_reads,
            CASE WHEN direction = '-ext' THEN 1 ELSE 0 END AS neg_ext_reads
        FROM <%=ctx.matches%>
        GROUP BY read_name, direction
    ) matches ON matches.read_name = reads.name
GROUP BY sample, alleles, total_reads
HAVING COUNT(*) > 1
ORDER BY sample, alleles