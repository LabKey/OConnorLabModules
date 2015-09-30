/*
 * Copyright (c) 2015 LabKey Corporation
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
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import javax.mail.MessagingException;
import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class ReadsJob extends PipelineJob
{
    GenotypingRun _run;

    public ReadsJob(PipelineJob job, GenotypingRun run)
    {
        super(job);
        _run = run;
    }

    public ReadsJob(@Nullable String provider, ViewBackgroundInfo info, @NotNull PipeRoot root,  GenotypingRun run)
    {
        super(provider, info, root);
        _run = run;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return GenotypingController.getRunURL(getContainer(), _run);
    }

    public void updateRunStatus(Status status) throws PipelineJobException, SQLException
    {
        // Issue 14880: if a job has run and failed, we will have deleted the run.  trying to update the status of this non-existent row
        // causes an OptimisticConflictException.  therefore we first test whether the runs exists
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("rowid"), _run.getRowId());

        if (!new TableSelector(GenotypingSchema.get().getRunsTable(), Collections.singleton("RowId"), f, null).exists())
        {
            try
            {
                File file = new File(_run.getPath(), _run.getFileName());
                GenotypingRun newRun = GenotypingManager.get().createRun(getContainer(), getUser(), _run.getMetaDataId(), file, _run.getPlatform());
                _run.setRowId(newRun.getRowId());
            }
            catch (RuntimeSQLException e)
            {
                getLogger().error("Run " + _run.getMetaDataId() + " has already been processed");
                return;
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("Status", status.getStatusId());

        TableInfo runsTable = GenotypingSchema.get().getRunsTable();
        DbScope scope = runsTable.getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Table.update(getUser(), runsTable, map, _run.getRowId());
            transaction.commit();
        }
    }

    public void sendMessageToUser(GenotypingRun genotypingRun, String sequencerName)
    {
        User user = UserManager.getUser(genotypingRun.getCreatedBy());
        if (user != null)
        {
            MailHelper.ViewMessage m = null;
            try
            {
                m = MailHelper.createMessage(LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress(), user.getEmail());
                m.setSubject(sequencerName + " Run " + genotypingRun.getRowId() + " Processing Complete");
                m.setText(sequencerName + " Run " + genotypingRun.getRowId() + " has finished processing. You can view it at " + getContainer().getStartURL(user));
            }
            catch (MessagingException e)
            {
                getLogger().error("Error creating message", e);
            }

            try
            {
                MailHelper.send(m, getUser(), getContainer());
            }
            catch (ConfigurationException e)
            {
                getLogger().error("Failed to send success notification, but job has completed successfully", e);
            }
        }
    }
}
