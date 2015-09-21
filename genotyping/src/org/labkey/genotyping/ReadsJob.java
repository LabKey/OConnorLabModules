package org.labkey.genotyping;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
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

public class ReadsJob extends PipelineJob
{

    public ReadsJob(PipelineJob job)
    {
        super(job);
    }

    public ReadsJob(@Nullable String provider, ViewBackgroundInfo info, @NotNull PipeRoot root)
    {
        super(provider, info, root);
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    public void updateRunStatus(Status status, GenotypingRun genotypingRun) throws PipelineJobException, SQLException
    {
        // Issue 14880: if a job has run and failed, we will have deleted the run.  trying to update the status of this non-existent row
        // causes an OptimisticConflictException.  therefore we first test whether the runs exists
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("rowid"), genotypingRun.getRowId());

        if (!new TableSelector(GenotypingSchema.get().getRunsTable(), Collections.singleton("RowId"), f, null).exists())
        {
            try
            {
                File file = new File(genotypingRun.getPath(), genotypingRun.getFileName());
                GenotypingRun newRun = GenotypingManager.get().createRun(getContainer(), getUser(), genotypingRun.getMetaDataId(), file, genotypingRun.getPlatform());
                genotypingRun.setRowId(newRun.getRowId());
            }
            catch (RuntimeSQLException e)
            {
                if (RuntimeSQLException.isConstraintException(e.getSQLException()))
                    throw new PipelineJobException("Run " + genotypingRun.getMetaDataId() + " has already been processed");
                else
                    throw e;
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("Status", status.getStatusId());
        Table.update(getUser(), GenotypingSchema.get().getRunsTable(), map, genotypingRun.getRowId());
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
                e.printStackTrace();
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
