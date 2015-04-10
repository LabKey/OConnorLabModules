/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
import java.util.Collections;

abstract public class GenotypingBaseTest extends BaseWebDriverTest
{
    public static final String first454importNum = "207";
    public static final String second454importNum = "208";
    public static final String illuminaImportNum = "206";
    protected int pipelineJobCount = 0;

    String pipelineLoc = TestFileUtils.getLabKeyRoot() + "/sampledata/genotyping";
    protected int runNum = 0; //this is globally unique, so we need to retrieve it every time.
    protected String checkboxId = ".select";
    //    private String expectedAnalysisCount = "1 - 61 of 61";

    DataRegionTable drt = null;
    protected String samples = "samples";
    protected String TEMPLATE_NAME = "GenotypingTest Saved Template";

    protected void configureAdmin()
    {
        clickProject(getProjectName());
        clickAndWait(Locator.id("adminSettings"));

        String[] listVals = {"sequences", "runs", samples};
        for(int i=0; i<3; i++)
        {
            click(Locator.linkContainingText("configure",i));
            _extHelper.waitForExt3Mask(WAIT_FOR_JAVASCRIPT);
            _extHelper.selectComboBoxItem("Schema:", "lists");
            _extHelper.selectComboBoxItem("Query:", listVals[i]);
            _extHelper.selectComboBoxItem("View:", "[default view]");
            _extHelper.clickExtButton("Submit", 0);
            _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);
        }
        setFormElement(Locator.name("galaxyURL"), "http://galaxy.labkey.org:8080");
        clickButton("Submit");
        clickButton("Load Sequences");

        log("Configure Galaxy Server Key");
        clickAndWait(Locator.linkWithText("My Settings"));
        setFormElement(Locator.name("galaxyKey"), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        clickButton("Submit");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        File dir = new File(pipelineLoc);
        File[] files = dir.listFiles();
        for(File file: files)
        {
            if(file.isDirectory() && file.getName().startsWith("analysis_"))
                TestFileUtils.deleteDir(file);
            if(file.getName().startsWith("import_reads_"))
                file.delete();
        }

        files = new File(pipelineLoc + "/secondRead").listFiles();

        if(files != null)
        {
            for(File file: files)
            {
                if(!file.getName().equals("reads.txt"))
                    file.delete();
            }
        }

        deleteTemplateRow(afterTest);
        deleteProject(getProjectName(), afterTest);
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
        catch (Exception ex)
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

    protected int getCombinedSampleRowIndex()
    {
        String xpath  = "//table[@id='dataregion_Analysis']/tbody/tr";
        Locator l = null;
        int index = 0;
        String goalClass = "labkey-error-row";
        for(index = 0; index<50; index++)
        {
            l = Locator.xpath(xpath + "[" + (index+5) + "]");    //the first four rows are invisible spacers and never contain data.
            if(getAttribute(l, "class").equals(goalClass))
                break;
        }
        return index;
    }

    //pre-
    protected void setUpLists()
    {
        log("Import genotyping list");
        clickProject(getProjectName());
        _listHelper.importListArchive(getProjectName(), new File(pipelineLoc, "sequencing.lists.zip"));
        assertTextPresent(
                samples,
                "mids",
                "sequences",
                "runs"
        );
    }

    protected void clickRunLink(String runId)
    {
        DataRegionTable dr = new DataRegionTable("Runs", this);
        int rowNum = dr.getRow("runs", runId);
        String rowId = dr.getColumnDataAsText("Run").get(rowNum);
        clickAndWait(Locator.linkWithText(rowId));
    }

    @LogMethod(category = LogMethod.MethodType.SETUP)
    public void setUp2()
    {
        _containerHelper.createProject(getProjectName(), "Genotyping");
        setUpLists();
        configureAdmin();
        clickProject(getProjectName());
        setPipelineRoot(pipelineLoc);
    }

}
