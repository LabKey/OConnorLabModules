/*
 * Copyright (c) 2011-2018 LabKey Corporation
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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({CustomModules.class})
@BaseWebDriverTest.ClassTimeout(minutes = 9)
public class GenotypingTest extends GenotypingBaseTest
{
    public static final String first454importNum = "207";
    public static final String second454importNum = "208";

    protected int runNum = 0; //this is globally unique, so we need to retrieve it every time.

    @Override
    protected String getProjectName()
    {
        return "GenotypingTest Project";
    }

    @BeforeClass
    public static void setupProject()
    {
        GenotypingTest init = (GenotypingTest)getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        setUp2(null, true);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testSteps()
    {
        //TODO: need to fix 454/genotyping tests
        importRunTest();
        runAnalysisTest();
        importSecondRunTest();
        verifyAnalysis();
    }

    private void importSecondRunTest()
    {
        goToProjectHome();
        startImportRun("secondRead/reads.txt", "Import 454 Reads", second454importNum);
        waitForPipelineJobsToComplete(++pipelineJobCount, "Import 454 reads for run", false);
        clickAndWait(Locator.linkContainingText("Import 454 reads for run"));
        assertTextPresent("G3BTA6P01BEVU9", "G3BTA6P01BD5P9");
    }

    //importing the same thing again should fail
    private void importRunAgainTest()
    {
        log("verify we can't import the same run twice");
        goToProjectHome();
        try
        {
            startImportRun("/reads.txt", "Import 454 Reads", first454importNum);
        }
        catch (NoSuchElementException expected)
        {
            if (!expected.getMessage().startsWith("Cannot locate element with text: " + first454importNum))
                throw expected;
        }
    }

    private void runAnalysisTest()
    {
        sendDataToGalaxyServer();
        receiveDataFromGalaxyServer();
    }

    private void verifyAnalysis()
    {
        goToProjectHome();

        clickAndWait(Locator.linkWithText("View Analyses"));
        clickAndWait(Locator.linkWithText("" + getRunNumber()));  // TODO: This is probably still too permissive... need a more specific way to get the run link

        assertTextPresent("Reads", "Sample Id", "Percent", "TEST09");
        assertElementPresent(Locator.paginationText(1, 100, 1410));
    }

    private void receiveDataFromGalaxyServer()
    {
        String[] filesToCopy = {"matches.txt", "analysis_complete.txt"};
        String analysisFolder = "analysis_" + getRunNumber();
        for (String file: filesToCopy)
        {
            copyFile(new File(getPipelineLoc(), file), new File(getPipelineLoc(), analysisFolder + "/" + file));
        }
        refresh();
        waitForPipelineJobsToComplete(++pipelineJobCount, "Import genotyping analysis", false);
    }

    private int getRunNumber()
    {
        return runNum;
    }

    private void sendDataToGalaxyServer()
    {
        clickButton("Add Analysis");
        _extHelper.selectComboBoxItem("Reference Sequences:", "[default]");                       //TODO:  this should be cyno
        clickButton("Submit");
        waitForPipelineJobsToComplete(++pipelineJobCount, "Submit genotyping analysis", false);
        findAndSetAnalysisNumber();

    }

    private void findAndSetAnalysisNumber()
    {
        Locator l = Locator.tagContainingText("td", "Submit genotyping analysis");
        isElementPresent(l);
        getText(l);
        String[] temp = getText(l).split(" ");
        setAnalysisNumber(Integer.parseInt(temp[temp.length-1]));

    }

    private void setAnalysisNumber(int i)
    {
        runNum = i;
    }

    private void importRunTest()
    {
        log("import genotyping run");
        startImportRun("reads.txt", "Import 454 Reads", first454importNum);
        waitForPipelineJobsToComplete(++pipelineJobCount, "Import Run", false);

        goToProjectHome();
        clickAndWait(Locator.linkWithText("View Runs"));
        clickRunLink(first454importNum);

        verifySamples();

        pushLocation();
        importRunAgainTest(); //Issue 13695
        popLocation();
    }

    private void verifySamples()
    {
        waitForElementWithRefresh(Locator.paginationText(1, 100, 9411), defaultWaitForPage);
        assertTextPresent("Name", "Sample Id", "Sequence", "G3BT");
    }

    private void startImportRun(String file, String importAction, String associatedRun)
    {
        clickAndWait(Locator.linkContainingText("Import Run"));
        _fileBrowserHelper.importFile(file, importAction);
        selectOptionByText(Locator.name("run"), associatedRun);
        clickButton("Import Reads");

    }
}
