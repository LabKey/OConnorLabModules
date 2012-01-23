/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ResultSetUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/*
* User: adam
* Date: Jan 9, 2011
* Time: 2:21:06 PM
*/
public class GenotypingUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = Logger.getLogger(GenotypingUpgradeCode.class);

    // Invoked from genotyping-10.31-10.32.sql in order to populate sample ids based on the previously imported MID column
    @SuppressWarnings({"UnusedDeclaration"})
    public void populateReadsSampleIds(ModuleContext ctx)
    {
        if (ctx.isNewInstall())
            return;

        GenotypingSchema gs = GenotypingSchema.get();

        try
        {
            // Get all the runs
            GenotypingRun[] runs = new TableSelector(gs.getRunsTable()).getArray(GenotypingRun.class);

            // Do the update one run at a time, since each run has a different MID -> sampleid mapping
            for (GenotypingRun run : runs)
            {
                Results rs = null;
                Map<Integer, Integer> midToRowIdMap = new LinkedHashMap<Integer, Integer>();

                try
                {
                    // Create the MID -> sample id mapping for this run
                    rs = SampleManager.get().selectSamples(run.getContainer(), ctx.getUpgradeUser(), run, "library_sample_f_mid/mid_name, key");

                    while (rs.next())
                        midToRowIdMap.put(rs.getInt(1), rs.getInt(2));
                }
                catch (Exception e)
                {
                    // An exception could mean samples and meta data runs have not been configured in this container
                    // TODO: selectSamples should return null in this case
                    continue;
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }

                // Issue a separate update for each MID -> sample id pair
                for (Map.Entry<Integer, Integer> entry : midToRowIdMap.entrySet())
                {
                    SQLFragment updateSQL = new SQLFragment("UPDATE ");
                    updateSQL.append(gs.getReadsTable(), "reads");
                    updateSQL.append(" SET SampleId = ? WHERE Mid = ? AND Run = ?");
                    updateSQL.addAll(new Object[]{entry.getValue(), entry.getKey(), run.getRowId()});

                    Table.execute(gs.getSchema(), updateSQL);
                }
            }
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }
}
