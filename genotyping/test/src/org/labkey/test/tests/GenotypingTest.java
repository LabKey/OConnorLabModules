/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.util.DataRegionTable;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({CustomModules.class})
public class GenotypingTest extends GenotypingBaseTest
{
    public static final String first454importNum = "207";
    public static final String second454importNum = "208";
    protected int pipelineJobCount = 0;

    String pipelineLoc = TestFileUtils.getLabKeyRoot() + "/sampledata/genotyping";
    protected int runNum = 0; //this is globally unique, so we need to retrieve it every time.
    protected String checkboxId = ".select";

    DataRegionTable drt = null;

    @Override
    protected String getProjectName()
    {
        return "GenotypingVerifyProject";
    }

    @Test
    public void testSteps() throws Exception
    {
        setUp2();
        goToProjectHome();

        //TODO: need to fix 454/genotyping tests
        importRunTest();
        importRunAgainTest(); //Issue 13695
        runAnalysisTest();
        importSecondRunTest();
        verifyAnalysis();
    }

    private void importSecondRunTest()
    {
        if(!WebTestHelper.isGroupConcatSupported())
            return;
        goToProjectHome();
        startImportRun("secondRead/reads.txt", "Import 454 Reads", second454importNum);
        waitForPipelineJobsToComplete(++pipelineJobCount, "Import 454 reads for run", false);
        clickAndWait(Locator.linkContainingText("Import 454 reads for run"));
        assertTextPresent("G3BTA6P01BEVU9", "G3BTA6P01BD5P9");
    }

    //importing the same thing again should fail
    //Issue 13695
    private void importRunAgainTest()
    {
//        log("verify we can't import the same run twice");
//        goToProjectHome();
//        startImportRun("/reads.txt", "Import Reads");
//        waitForText("ERROR");
    }

    private void runAnalysisTest()
    {

        if(!WebTestHelper.isGroupConcatSupported())
            return;
//        getToRunScreen();
        sendDataToGalaxyServer();
        receiveDataFromGalaxyServer();
    }

    private void verifyAnalysis()
    {
        if (!WebTestHelper.isGroupConcatSupported())
            return;
        goToProjectHome();

        clickAndWait(Locator.linkWithText("View Analyses"));
        clickAndWait(Locator.linkWithText("" + getRunNumber()));  // TODO: This is probably still too permissive... need a more specific way to get the run link

        assertTextPresent("Reads", "Sample Id", "Percent", "TEST09");
//        assertTextPresent("TEST14", 2);
        assertElementPresent(Locator.paginationText(1, 100, 1410));
        startAlterMatches();
        deleteMatchesTest();
        alterMatchesTest();

    }

    private void deleteMatchesTest()
    {

        String[] alleleContentsBeforeDeletion = drt.getColumnDataAsText("Allele Name").toArray(new String[] {"a"});

        //attempt to delete a row and cancel
        drt.checkCheckbox(2);

        clickButton("Delete", 0);
        cancelAlert();
        assertElementPresent(Locator.paginationText(1, 100, 1410));

        //delete some rows
        prepForPageLoad();
        clickButton("Delete", 0);
        getAlert();
        waitForPageToLoad();

        waitForText("1 match was deleted.");
        assertElementPresent(Locator.paginationText(1, 100, 1409));
    }

    private void alterMatchesTest()
    {
        sleep(5000);
        String expectedNewAlleles = "Mafa-A1*063:03:01, Mafa-A1*063:01";

        //combine two samples
        click(Locator.name(checkboxId).index(0));
        click(Locator.name(checkboxId).index(1));
        clickButton("Combine", 0);
        _extHelper.waitForExt3Mask(WAIT_FOR_JAVASCRIPT);

        /*verify the list is what we expct.  Because the two samples had the following lists
        * WE expect them to combine to the following:
         */
        String[] alleles = {"Mamu-A1*004:01:01", "Mamu-A1*004:01:02"};
        for(String allele: alleles)
        {
            Locator.XPathLocator l =  Locator.tagWithText("div", allele);
            isElementPresent(l);
            assertEquals(1, getElementCount(l));
        }

        //combine some but not all of the matches
        _extHelper.clickXGridPanelCheckbox(0, true);
        clickButtonContainingText("Combine", WAIT_FOR_EXT_MASK_TO_DISSAPEAR);
        refresh();

        int newIdIndex = getCombinedSampleRowIndex();
        assertEquals("19", drt.getDataAsText(newIdIndex, "Reads") );
        assertEquals("7.3%", drt.getDataAsText(newIdIndex, "Percent") );
        assertEquals("300.0", drt.getDataAsText(newIdIndex,"Average Length") );
        assertEquals("14", drt.getDataAsText(newIdIndex, "Pos Reads") );
        assertEquals("5", drt.getDataAsText(newIdIndex, "Neg Reads") );
        assertEquals("0", drt.getDataAsText(newIdIndex, "Pos Ext Reads") );
        assertEquals("0", drt.getDataAsText(newIdIndex, "Neg Ext Reads") );
//        String[] allelesAfterMerge = drt.getDataAsText(newIdIndex, "Allele Name").replace(" ", "").split(",") ;
//        assertEquals(1,allelesAfterMerge.length);
        assertTextPresent(alleles[0]);
    }

    /**
     * enable altering of matches and verify expected changes
     * precondition:  already at analysis page
     */
    private void startAlterMatches()
    {
       clickButton("Alter Matches");

        for(String buttonText : new String[] {"Stop Altering Matches", "Combine", "Delete"})
        {
            assertElementPresent(Locator.xpath("//a[contains(@class,'button')]/span[text()='" + buttonText + "']"));
        }

        drt = new DataRegionTable( "Analysis", this);
    }

    private void receiveDataFromGalaxyServer()
    {
        String[] filesToCopy = {"matches.txt", "analysis_complete.txt"};
        String analysisFolder = "analysis_" + getRunNumber();
        for(String file: filesToCopy)
        {
            copyFile(pipelineLoc + "/" + file, pipelineLoc + "/" + analysisFolder + "/" + file);
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
        Locator menuLocator = Locator.xpath("//input[@name='sequencesView']/../input[2]");
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

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("genotyping");
    }

}
