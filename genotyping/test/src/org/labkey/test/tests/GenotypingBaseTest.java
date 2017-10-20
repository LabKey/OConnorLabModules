/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.test.tests;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.DeleteRowsCommand;
import org.labkey.remoteapi.query.SaveRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PasswordUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

abstract public class GenotypingBaseTest extends BaseWebDriverTest
{
    private static final File pipelineLoc = TestFileUtils.getSampleData("genotyping");
    protected static int pipelineJobCount = 0;
    protected String samples = "samples";
    protected String TEMPLATE_NAME = "GenotypingTest Saved Template";

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "GenotypingVerifyProject";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("genotyping");
    }

    @BeforeClass
    public static void resetPipelineCount()
    {
        pipelineJobCount = 0;
    }

    protected void configureAdmin(boolean configureSequences)
    {
        clickProject(getProjectName());
        clickAndWait(Locator.id("adminSettings"));

        String[] listVals = {"sequences", "runs", samples};
        for(int i=0; i<3; i++)
        {
            if (configureSequences || i > 0)
            {
                Locator link = Locator.linkContainingText("configure").index(i);
                String list = listVals[i];
                doAndWaitForPageSignal(() -> click(link),
                        "schemaCombo-loaded");
                doAndWaitForPageSignal(() -> _extHelper.selectComboBoxItem("Schema:", "lists"),
                        "queryCombo-loaded");
                doAndWaitForPageSignal(() -> _extHelper.selectComboBoxItem("Query:", list),
                        "viewCombo-loaded");
                _extHelper.selectComboBoxItem("View:", "[default view]");
                _extHelper.clickExtButton("Submit", 0);
                _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);
            }
        }
        if (configureSequences)
        {
            setFormElement(Locator.name("galaxyURL"), "http://galaxy.labkey.org:8080");
            clickButton("Submit");
            clickButton("Load Sequences");

            log("Configure Galaxy Server Key");
            clickAndWait(Locator.linkWithText("My Settings"));
            setFormElement(Locator.name("galaxyKey"), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        }
        clickButton("Submit");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        File[] files = getPipelineLoc().listFiles();
        for(File file: files)
        {
            if(file.isDirectory() && file.getName().startsWith("analysis_"))
                TestFileUtils.deleteDir(file);
            if(file.getName().startsWith("import_reads_"))
                file.delete();
        }

        files = new File(getPipelineLoc(), "secondRead").listFiles();

        if(files != null)
        {
            for(File file: files)
            {
                if(!file.getName().equals("reads.txt"))
                    file.delete();
            }
        }

        deleteTemplateRow(afterTest);
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    private void deleteTemplateRow(boolean failOnError)
    {
        Connection cn = new Connection(getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
        DeleteRowsCommand cmd = new DeleteRowsCommand("genotyping", "IlluminaTemplates");
        cmd.addRow(Collections.singletonMap("Name", (Object) TEMPLATE_NAME));
        SaveRowsResponse resp;
        try
        {
            resp = cmd.execute(cn, getProjectName());
        }
        catch (IOException fail)
        {
            throw new RuntimeException(fail);
        }
        catch (CommandException ex)
        {
            if (failOnError)
                throw new RuntimeException(ex);
            else
            {
                log("Template rows not deleted. Nothing to be deleted.");
                return;
            }
        }
        log("Template rows deleted: " + resp.getRowsAffected());
    }

    //pre-
    protected void setUpLists(@Nullable String listArchive, boolean hasSequencesList)
    {
        log("Import genotyping list");
        clickProject(getProjectName());
        _listHelper.importListArchive(getProjectName(), new File(getPipelineLoc(), (listArchive == null ? "sequencing.lists.zip" : listArchive)));
        assertTextPresent(
                samples,
                "mids",
                "runs"
        );
        if (hasSequencesList)
            assertTextPresent("sequences");
    }

    protected void clickRunLink(String runId)
    {
        DataRegionTable dr = new DataRegionTable("Runs", this);
        int rowNum = dr.getRow("runs", runId);
        String rowId = dr.getColumnDataAsText("Run").get(rowNum);
        clickAndWait(Locator.linkWithText(rowId));
    }

    @LogMethod
    public void setUp2(@Nullable String listArchive, boolean hasSequencesList)
    {
        _containerHelper.createProject(getProjectName(), "Genotyping");
        setUpLists(listArchive, hasSequencesList);
        configureAdmin(hasSequencesList);
        clickProject(getProjectName());
        setPipelineRoot(getPipelineLoc().getAbsolutePath());
    }

    protected File getPipelineLoc()
    {
        return pipelineLoc;
    }

    /**
     *
     * @param row 0 based
     */
    protected void clickRunLink(int row)
    {
        DataRegionTable runs = new DataRegionTable("Runs", this);
        clickAndWait(runs.link(row, "Run"));
    }
}
