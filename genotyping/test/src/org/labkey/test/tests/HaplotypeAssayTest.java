/*
 * Copyright (c) 2012-2018 LabKey Corporation
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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.categories.OConnor;
import org.labkey.test.components.DomainDesignerPage;
import org.labkey.test.components.domain.DomainFormPanel;
import org.labkey.test.pages.ReactAssayDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.LogMethod;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Category({CustomModules.class, OConnor.class})
@BaseWebDriverTest.ClassTimeout(minutes = 15)
public class HaplotypeAssayTest extends GenotypingBaseTest
{
    private static final String ASSAY_NAME = "HaplotypeAssay";// + TRICKY_CHARACTERS_NO_QUOTES;
    private static final File FIRST_RUN_FILE = TestFileUtils.getSampleData("genotyping/haplotypeAssay/firstRunData.txt");
    private static final File SECOND_RUN_FILE = TestFileUtils.getSampleData("genotyping/haplotypeAssay/secondRunData.txt");
    private static final File ERROR_RUN_FILE = TestFileUtils.getSampleData("genotyping/haplotypeAssay/errorRunData.txt");
    private static final File DRB_RUN_FILE = TestFileUtils.getSampleData("genotyping/haplotypeAssay/drbRunData.txt");
    private static final File STR_RUN_FILE = TestFileUtils.getSampleData("genotyping/haplotypeAssay/strRunData.txt");
    private static final String DRB_ASSAY = "DRB assay";
    private static final String DRB_RUN = "drb run";
    private static final String STR_RUN = "str run";

    @Override
    protected String getProjectName()
    {
        return "HaplotypeAssayTest Project";
    }

    @BeforeClass
    public static void setupProject()
    {
        HaplotypeAssayTest init = (HaplotypeAssayTest)getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        setUp2(null, true);
        File listArchive = TestFileUtils.getSampleData("genotyping/haplotypeAssay/STRHaplotype.lists.zip");
        new ListHelper(this).importListArchive(getProjectName(), listArchive);
    }

    @Test
    public void testSteps()
    {
        configureExtensibleTables();
        setupHaplotypeAssay();
        verifyAssayUploadErrors();
        verifyFirstRun();
        verifySecondRun();
        verifyAggregatedResults();
        verifyExtraHaplotypeAssignment();
        verifyAssignmentReport();
        verifyDuplicateRecords();
        verifyAribitraryHaplotypeAssay();
        verifySTRDisrepancies();
        verifyColumnRenaming();
    }

    @LogMethod
    private void verifyColumnRenaming()
    {
        String assayId = "second run";

        renameColumns("Mamu");
        // at this point could check that titles have changed...

        // reset view
        clickAndWait(Locator.linkWithText(assayId));
        waitForText(ASSAY_NAME + " Results");
        _customizeViewsHelper.revertUnsavedViewGridClosed();

        verifySecondRun("Mamu");

        renameColumns("MHC");

        clickAndWait(Locator.linkWithText(assayId));
        waitForText(ASSAY_NAME + " Results");
        //_customizeViewsHelper.revertUnsavedViewGridClosed();
    }


    private void renameColumns(String prefix)
    {
        String prefix2 = prefix.toLowerCase();

        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));

        ReactAssayDesignerPage assayDesignerPage = _assayHelper.clickEditAssayDesign();
        DomainFormPanel domainFormPanel = assayDesignerPage.expandFieldsPanel("Run");
        domainFormPanel.getField(5).setName(prefix2+"AHaplotype1").setLabel(prefix+"-A Haplotype 1");
        domainFormPanel.getField(6).setName(prefix2+"AHaplotype2").setLabel(prefix+"-A Haplotype 2");
        domainFormPanel.getField(7).setName(prefix2+"BHaplotype1").setLabel(prefix+"-B Haplotype 1");
        domainFormPanel.getField(8).setName(prefix2+"BHaplotype2").setLabel(prefix+"-B Haplotype 2");
        assayDesignerPage.clickFinish();
    }

    @LogMethod
    private void verifySTRDisrepancies()
    {
        final String strDisrepanciesHeader = "view STR discrepancies report";

        goToManageAssays();
        clickAndWait(Locator.linkWithText(DRB_ASSAY));
        clickAndWait(Locator.linkWithText(strDisrepanciesHeader));

        assertEquals("STR-ID-4-BAD", getTableCellText(Locator.tagWithClass("table", "labkey-data-region"), 1, 0) );
        assertEquals("mhcDRB", getTableCellText(Locator.tagWithClass("table", "labkey-data-region"), 1, 1));

        assertEquals("STR-ID-5-BAD", getTableCellText(Locator.tagWithClass("table", "labkey-data-region"), 2, 0));
        assertEquals("mhcDRB", getTableCellText(Locator.tagWithClass("table", "labkey-data-region"), 2, 1));

        assertTextNotPresent("STR-ID-1-GOOD", "STR-ID-2-GOOD", "STR-ID-3-GOOD");
    }

    @LogMethod
    private void verifyAribitraryHaplotypeAssay()
    {
        setupHaplotypeAssay(DRB_ASSAY,  new String[][] {{"mhcDRBHaplotype", "MHC-DRB Haplotype" }, {"mhcSTRHaplotype", "MHC-STR Haplotype"}});
        importRun(DRB_RUN, DRB_ASSAY, DRB_RUN_FILE, true);

        clickAndWait(Locator.linkWithText(DRB_RUN));
        DataRegionTable drt = new DataRegionTable("Data", this);

        verifyColumnDataValues(drt, "MHC-AHaplotype1", "A001", "A023", "A001", "A004", "A002a");
        verifyColumnDataValues(drt, "MHC-AHaplotype2", "A023", "A021", "A001", "A023", "A002a");
        verifyColumnDataValues(drt, "MHC-DRB Haplotype 1", "D025a", "D012b", "D001c", "D012b", "D002");
        verifyColumnDataValues(drt, "MHC-DRB Haplotype 2", "D015c", "D017a", "D017a", "D012b", "D002");

        importRun(STR_RUN, DRB_ASSAY, STR_RUN_FILE, true);

        // TODO - verify STR data import and discrepancy report
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @LogMethod
    private void configureExtensibleTables()
    {
        log("Configure extensible Animal table");
        goToProjectHome();
        clickAndWait(Locator.id("adminSettings"));
        DomainDesignerPage domainDesignerPage = goToEditDefinition("configureAnimal");
        DomainFormPanel domainFormPanel = domainDesignerPage.fieldsPanel();
        domainFormPanel.addField(new FieldDefinition("animalStrTest", FieldDefinition.ColumnType.String).setLabel("Animal String Test"));
        domainFormPanel.addField(new FieldDefinition("animalIntTest", FieldDefinition.ColumnType.Integer).setLabel("Animal Integer Test"));
        domainDesignerPage.clickFinish();
        clickAndWait(Locator.linkWithText("Animal"));
        _customizeViewsHelper.openCustomizeViewPanel();
        waitForText("Animal String Test");
        assertTextPresent("Animal Integer Test");

        log("Configure extensible Haplotype table");
        goToProjectHome();
        clickAndWait(Locator.id("adminSettings"));
        domainDesignerPage = goToEditDefinition("configureHaplotype");
        domainFormPanel = domainDesignerPage.fieldsPanel();
        domainFormPanel.addField(new FieldDefinition("haplotypeStrTest", FieldDefinition.ColumnType.String).setLabel("Haplotype String Test"));
        domainFormPanel.addField(new FieldDefinition("haplotypeIntTest", FieldDefinition.ColumnType.Integer).setLabel("Haplotype Integer Test"));
        domainDesignerPage.clickFinish();
        clickAndWait(Locator.linkWithText("Haplotype"));
        _customizeViewsHelper.openCustomizeViewPanel(); //TODO:  should this be necessary?
        assertTextPresent("Haplotype String Test", "Haplotype Integer Test");
    }

    private DomainDesignerPage goToEditDefinition(String tableName)
    {
        clickAndWait(Locator.id(tableName));
        return new DomainDesignerPage(getDriver());
    }

    private void setupHaplotypeAssay()
    {
        setupHaplotypeAssay(ASSAY_NAME, null);
    }

    @LogMethod
    private void setupHaplotypeAssay(String name, String[][] extraHaplotypes)
    {
        log("Setting up Haplotype assay");
        goToProjectHome();
        goToManageAssays();
        ReactAssayDesignerPage assayDesignerPage = _assayHelper.createAssayDesign("Haplotype", name);
        assayDesignerPage.setEditableRuns(true);

        if (extraHaplotypes!=null)
        {
            DomainFormPanel domainFormPanel = assayDesignerPage.goToRunFields();

            for (String[] haplotype : extraHaplotypes)
            {
                domainFormPanel.addField(new FieldDefinition(haplotype[0] + "1")
                        .setLabel(haplotype[1] + " 1")
                        .setShownInInsertView(false)
                        .setShownInUpdateView(false));

                domainFormPanel.addField(new FieldDefinition(haplotype[0] + "2")
                        .setLabel(haplotype[1] + " 2")
                        .setShownInInsertView(false)
                        .setShownInUpdateView(false));
            }
        }

        assayDesignerPage.clickFinish();
    }

    @LogMethod
    private void verifyAssayUploadErrors()
    {
        log("Test errors with Haplotype assay upload");
        goToAssayImport(ASSAY_NAME);
        clickButton("Save and Finish");
        waitForText("Species Name is required and must be of type Integer.");
        selectOptionByText(Locator.name("speciesId"), "mamu");
        clickButton("Save and Finish");
        waitForText("Data contained zero data rows");
        setFormElement(Locator.name("data"), TestFileUtils.getFileContents(ERROR_RUN_FILE));
        sleep(1000);
        clickButton("Save and Finish");
        waitForText("Column header mapping missing for: Lab Animal ID");
        sleep(1000); // give the form a second to reshow on error
        _ext4Helper.selectComboBoxItem("Lab Animal ID *:", "OC ID");
        clickButton("Save and Finish");
//        waitForText("Column header mapping missing for: Total # Reads Evaluated");
        waitForText("Duplicate value found in Lab Animal ID column: ID-1");
        clickButton("Cancel");
    }

    @LogMethod
    private void verifyFirstRun()
    {
        log("Verify Haplotype Assignment data for the first run");
        importRun("first run", ASSAY_NAME, FIRST_RUN_FILE, false);

        // add the Animal/ClientAnimalId column so we can verify that as well
        goToAssayRun("first run");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.addColumn("AnimalId/ClientAnimalId");
        _customizeViewsHelper.removeFilter("RunId/RowId");
        _customizeViewsHelper.saveCustomView();

        goToAssayRun("first run");
        DataRegionTable drt = new DataRegionTable("Data", this);
        verifyColumnDataValues(drt, "Animal", "ID-1", "ID-2", "ID-3", "ID-4", "ID-5");
        verifyColumnDataValues(drt, "TotalReads", "1000", "2000", "3000", "4000", "5000");
        verifyColumnDataValues(drt, "IdentifiedReads", "300", "1000", "600", "2500", "3250");
        verifyColumnDataValues(drt, "%Unknown", "70.0", "50.0", "80.0", "37.5", "35.0");
        verifyColumnDataValues(drt, "MHC-AHaplotype1", "A001", "A023", "A001", "A004", "A002a");
        verifyColumnDataValues(drt, "MHC-AHaplotype2", "A023", "A021", "A001", "A023", "A002a");
        verifyColumnDataValues(drt, "MHC-BHaplotype1", "B015c", "B012b", "B001c", "B012b", "B002");
        verifyColumnDataValues(drt, "MHC-BHaplotype2", "B025a", "B017a", "B017a", "B012b", "B002");
        verifyColumnDataValues(drt, "Enabled", "true", "true", "true", "true", "true");
        verifyColumnDataValues(drt, "ClientAnimalId", "x123", "x234", "x345", "x456", "x567");

        // verify concatenated haplotype strings
        List<String> concatenated = drt.getColumnDataAsText("ConcatenatedHaplotypes");
        assertEquals("Wrong number of data rows", 5, concatenated.size());
        assertEquals("Wrong ID-1 concatenated haplotypes", "A001, A023, B015c, B025a", concatenated.get(0));
        assertEquals("Wrong ID-2 concatenated haplotypes", "A023, A021, B012b, B017a", concatenated.get(1));
        assertEquals("Wrong ID-3 concatenated haplotypes", "A001, A001, B001c, B017a", concatenated.get(2));
        assertEquals("Wrong ID-4 concatenated haplotypes", "A004, A023, B012b, B012b", concatenated.get(3));
        assertEquals("Wrong ID-5 concatenated haplotypes", "A002a, A002a, B002, B002", concatenated.get(4));

        // verify that the animal and haplotype rows were properly inserted
        goToQuery("Animal");
        drt = new DataRegionTable("query", this);
        assertEquals("Unexpected number of Animal records", 5, drt.getDataRowCount());
        verifyColumnDataValues(drt, "Lab Animal Id", "ID-1", "ID-2", "ID-3", "ID-4", "ID-5");
        verifyColumnDataValues(drt, "Client Animal Id", "x123", "x234", "x345", "x456", "x567");

        verifyHaplotypeRecordsByType(11, 5, 6);
    }

    @LogMethod
    private void verifySecondRun()
    {
        importRun("second run", ASSAY_NAME, SECOND_RUN_FILE, false);

        log("Verify Haplotype Assignment data for the second run");
        goToAssayRun("second run");

        verifySecondRun("MHC");

        // validate extra column in view
        DataRegionTable drt = new DataRegionTable("Data", this);
        verifyColumnDataValues(drt, "ClientAnimalId", "x456", "x567", "x678", "x789", "x888", "x999");

        // verify that the animal and haplotype rows were properly inserted
        goToQuery("Animal");
        drt = new DataRegionTable("query", this);
        assertEquals("Unexpected number of Animal records", 9, drt.getDataRowCount());
        verifyColumnDataValues(drt, "LabAnimalId", "ID-1", "ID-2", "ID-3", "ID-4", "ID-5", "ID-6", "ID-7", "ID-8", "ID-9");
        verifyColumnDataValues(drt, "ClientAnimalId", "x123", "x234", "x345", "x456", "x567", "x678", "x789", "x888", "x999");

        verifyHaplotypeRecordsByType(13, 6, 7);
    }


    private void verifySecondRun(String prefix)
    {
        DataRegionTable drt = new DataRegionTable("Data", this);
        verifyColumnDataValues(drt, "Animal", "ID-4", "ID-5", "ID-6", "ID-7", "ID-8", "ID-9");
        verifyColumnDataValues(drt, "TotalReads", "4000", "5000", "6000", "7000", " ", "0");
        verifyColumnDataValues(drt, "IdentifiedReads", "2500", "3250", "3000", "3500", " ", "1");
        verifyColumnDataValues(drt, "%Unknown", "37.5", "35.0", "50.0", "50.0", " ", " ");
        verifyColumnDataValues(drt, prefix+"-AHaplotype1", "A001", " ", "A033", "A004", "A004", "A004");
        verifyColumnDataValues(drt, prefix+"-AHaplotype2", "A023", " ", "A033", " ", "A004", "A004");
        verifyColumnDataValues(drt, prefix+"-BHaplotype1", "B015c", " ", "B012b", "B033", "B033", "B033");
        verifyColumnDataValues(drt, prefix+"-BHaplotype2", "B025a", " ", "B012b", "B033", "B033", "B033");
        verifyColumnDataValues(drt, "Enabled", "true", "true", "true", "true", "true", "true");

        // verify concatenated haplotype strings
        List<String> concatenated = drt.getColumnDataAsText("ConcatenatedHaplotypes");
        assertEquals("Wrong number of data rows", 6, concatenated.size());
        assertEquals("Wrong ID-4 concatenated haplotypes", "A001, A023, B015c, B025a", concatenated.get(0));
        assertEquals("Wrong ID-5 concatenated haplotypes", " ", concatenated.get(1));
        assertEquals("Wrong ID-6 concatenated haplotypes", "A033, A033, B012b, B012b", concatenated.get(2));
        assertEquals("Wrong ID-7 concatenated haplotypes", "A004, B033, B033", concatenated.get(3)); // Record with only 3 haplotypes
    }

    @LogMethod
    private void verifyAggregatedResults()
    {
        log("Verify Haplotype Assignment data for the second run");
        goToAssayRun("first run");
        clickAndWait(Locator.linkWithText("view results"));

        DataRegionTable drt = new DataRegionTable("query", this);
        assertEquals("Unexpected number of Animal records", 9, drt.getDataRowCount());
        verifyColumnDataValues(drt, "Animal", "ID-1", "ID-2", "ID-3", "ID-4", "ID-5", "ID-6", "ID-7", "ID-8", "ID-9");
        verifyColumnDataValues(drt, "Total Reads", "1000", "2000", "3000", "8000", "10000", "6000", "7000", " ", "0");
        verifyColumnDataValues(drt, "Total Identified Reads", "300", "1000", "600", "5000", "6500", "3000", "3500", " ", "1");
        verifyColumnDataValues(drt, "Total % Unknown", "70.0%", "50.0%", "80.0%", "37.5%", "35.0%", "50.0%", "50.0%", " ", " ");
        verifyColumnDataValues(drt, "Inconsistent Assignments", "false", "false", "false", "true", "false", "false", "false", "false", "false");
        verifyColumnDataValues(drt, "mhcA Haplotype1", "A001", "A023", "A001", "A001", "A002a", "A033", "A004", "A004", "A004");
        verifyColumnDataValues(drt, "mhcA Haplotype2", "A023", "A021", "A001", "A023", "A002a", "A033", " ", "A004", "A004");
        verifyColumnDataValues(drt, "mhcB Haplotype1", "B015c", "B012b", "B001c", "B012b", "B002", "B012b", "B033", "B033", "B033");
        verifyColumnDataValues(drt, "mhcB Haplotype2", "B025a", "B017a", "B017a", "B012b", "B002", "B012b", "B033", "B033", "B033");
    }

    @LogMethod
    private void verifyExtraHaplotypeAssignment()
    {
        log("Verify Animal Haplotype Assignment with > 4 assignments");
        goToAssayRun("first run");
        _customizeViewsHelper.openCustomizeViewPanel();
        _customizeViewsHelper.showHiddenItems();
        _customizeViewsHelper.addColumn("RowId");
        _customizeViewsHelper.removeFilter("RunId/RowId");
        _customizeViewsHelper.saveCustomView();

        goToAssayRun("first run");
        DataRegionTable drt = new DataRegionTable("Data", this);
        String animalAnalysisId = drt.getDataAsText(4, "RowId"); // row index 4 is ID-5
        goToQuery("AnimalHaplotypeAssignment");
        waitForElement(Locator.paginationText(1, 39, 39));

        // ADD: animal ID-5, haplotype A001
        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        selectOptionByText(Locator.name("quf_HaplotypeId"), "A001");
        selectOptionByText(Locator.name("quf_AnimalAnalysisId"), animalAnalysisId);
        setFormElement(Locator.name("quf_DiploidNumber"), "1");
        clickButton("Submit");

        // ADD: animal ID-5, haplotype B002
        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        selectOptionByText(Locator.name("quf_HaplotypeId"), "B002");
        selectOptionByText(Locator.name("quf_AnimalAnalysisId"), animalAnalysisId);
        setFormElement(Locator.name("quf_DiploidNumber"), "1");
        clickButton("Submit");

        // verify the calculated columns in the assay results view for concatenated haplotype, etc.
        goToProjectHome();
        goToAssayRun("first run");
        drt = new DataRegionTable("Data", this);
        drt.setFilter("AnimalId", "Equals", "ID-5");
        verifyColumnDataValues(drt, "MHC-AHaplotype1", "A001");
        verifyColumnDataValues(drt, "MHC-AHaplotype2", "A002a");
        verifyColumnDataValues(drt, "MHC-BHaplotype1", "B002");
        verifyColumnDataValues(drt, "MHC-BHaplotype2", "B002");
        drt.clearFilter("AnimalId");

        // NOTE: this should clean up what it has done in order to make the test more modular...

    }

    @LogMethod
    private void verifyAssignmentReport()
    {
        log("Verify Haplotype Assignment Report");
        goToProjectHome();
        goToAssayRun("first run");
        clickButton("Produce Report");
        waitForText("Search for animal IDs by:");
        assertTextPresent("Show report column headers as:",
                "Enter the animal IDs separated by whitespace, comma, or semicolon:");

        // test a single ID
        final DataRegionTable drt = submitAssignmentReport("ID-3", null);
        verifyColumnDataValues(drt, "Haplotype","A001", "B001c", "B017a");
        verifyColumnDataValues(drt, "ID-3", "2", "1", "1");

        _ext4Helper.selectComboBoxItem("Show report column headers as:", "Client Animal ID");
        submitAssignmentReport(null, drt);
        verifyColumnDataValues(drt, "Haplotype","A001", "B001c", "B017a");
        verifyColumnDataValues(drt, "x345", "2", "1", "1");

        // test with IDs that only have one result
        _ext4Helper.selectComboBoxItem("Search for animal IDs by:", "Client Animal ID");
        _ext4Helper.selectComboBoxItem("Show report column headers as:", "Lab Animal ID");
        submitAssignmentReport("x123,x234;x345 x678 x789", drt);
        waitForElement(Locator.paginationText(11));
        assertEquals("Table columns", List.of("Haplotype", "ID-1", "ID-2", "ID-3", "ID-6", "ID-7"), drt.getColumnLabels());
        drt.setFilter("ID-1::Counts", "Equals", "1");
        verifyColumnDataValues(drt, "Haplotype", "A001", "A023", "B015c", "B025a");
        drt.clearFilter("ID-1::Counts");
        drt.setFilter("ID-6::Counts", "Equals", "2");
        verifyColumnDataValues(drt, "Haplotype", "A033", "B012b");
        drt.clearFilter("ID-6::Counts");

        // test with IDs that have duplicate reocrds
        _ext4Helper.selectComboBoxItem("Search for animal IDs by:", "Lab Animal ID");
        _ext4Helper.selectComboBoxItem("Show report column headers as:", "Lab Animal ID");
        submitAssignmentReport("ID-4,ID-5", drt);
        waitForElement(Locator.paginationText(1, 8, 8));
        waitForText("Warning: multiple enabled assay results were found for the following IDs: ID-4 (2), ID-5 (2)");
        verifyColumnDataValues(drt, "ID-4", "1", " ", "1", "2", " ", "2", "1", "1");
        verifyColumnDataValues(drt, "ID-5", "1", "2", " ", " ", "3", " ", " ", " ");
    }

    private DataRegionTable submitAssignmentReport(String idsText, DataRegionTable drt)
    {
        if (idsText != null)
        {
            WebElement idsTextArea = Locator.name("idsTextArea").findElement(getDriver());
            setFormElement(idsTextArea, idsText);
            new Actions(getDriver())
                    .moveToElement(idsTextArea, idsTextArea.getSize().getWidth() + 5, 0)
                    .click() // click outside of text area
                    .perform();
        }

        WebElement submitButton = Ext4Helper.Locators.ext4Button("Submit").findElement(getDriver());
        if (drt == null)
        {
            shortWait().until(wd -> !submitButton.getAttribute("class").contains("disabled"));
            submitButton.click();
            drt = new DataRegionTable.DataRegionFinder(getDriver()).withName("report").waitFor();
            drt.setAsync(true);
            return drt;
        }
        else
        {
            drt.doAndWaitForUpdate(submitButton::click);
            return drt;
        }
    }

    @LogMethod
    private void verifyDuplicateRecords()
    {
        // verify that the two duplicates show up on the duplicates report
        goToAssayRun("first run");
        clickAndWait(Locator.linkWithText("view duplicates"));
        waitForText("# Active Assignments");
        assertElementPresent(Locator.linkWithText("ID-4"));
        assertElementPresent(Locator.linkWithText("ID-5"));

        // test editing a run/animal record to clear a duplicate for ID-4
        goToAssayRun("first run");
        DataRegionTable drt = new DataRegionTable("Data", this);
        drt.setFilter("AnimalId", "Equals", "ID-4");
        drt.clickEditRow(0);
        waitForText("mhcB Haplotype", 2, WAIT_FOR_JAVASCRIPT);
        _ext4Helper.uncheckCheckbox("Enabled:");
        clickButton("Submit");
        drt = new DataRegionTable("Data", this);
        verifyColumnDataValues(drt, "Enabled", "false");
        setReportId("ID-4");
        String duplicates = getText(Locator.id("duplicates3"));
        assertEquals("unexpected duplicate assay results were found for the following IDs", "", duplicates);
        assertTextPresentInThisOrder("A001","A023","B015c","B025a");
        assertTextNotPresent("A004", "B012b");

        // test disabling a run and clearing the other duplicate for ID-5
        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        drt = new DataRegionTable("Runs", this);
        drt.setFilter("Name", "Equals", "second run");
        drt.clickEditRow(0);
        uncheckCheckbox(Locator.name("quf_enabled"));
        clickButton("Submit");
        goToAssayRun("second run");
        setReportId("ID-5");
        duplicates = getText(Locator.id("duplicates3"));
        assertEquals("unexpected duplicate assay results were found for the following IDs", "", duplicates);
        assertTextPresentInThisOrder("A001","A002a","B002");

        // verify that the duplicates report is now clear
        clickAndWait(Locator.linkWithText("view duplicates"));
        waitForText("# Active Assignments");
        assertTextNotPresent("ID-4", "ID-5");
    }

    private void setReportId(String id)
    {
        // this method assumes that we are already viewing the Assay results grid
        DataRegionTable drt = new DataRegionTable("Data", this);
        drt.setFilter("AnimalId", "Equals", id);
        drt.checkCheckbox(0);
        clickButton("Produce Report");
        waitForText("Enter the animal IDs separated by whitespace, comma, or semicolon:");
        DataRegionTable.DataRegion(getDriver()).withName("report").waitFor();
    }

    private void goToQuery(String queryName)
    {
        goToSchemaBrowser();
        selectQuery("genotyping", queryName);
        waitForText("view data");
        clickAndWait(Locator.linkContainingText("view data"));
    }

    @LogMethod
    private void verifyHaplotypeRecordsByType(int total, int typeACount, int typeBCount)
    {
        goToQuery("Haplotype");
        DataRegionTable drt = new DataRegionTable("query", this);
        assertEquals("Unexpected number of Haplotype records", total, drt.getDataRowCount());
        drt.setFilter("Type", "Equals", "mhcA");
        assertEquals("Unexpected number of filtered Haplotype records", typeACount, drt.getDataRowCount());
        drt.clearFilter("Type");
        drt.setFilter("Type", "Equals", "mhcB");
        assertEquals("Unexpected number of filtered Haplotype records", typeBCount, drt.getDataRowCount());
        drt.clearFilter("Type");
    }

    private void verifyColumnDataValues(DataRegionTable drt, String colName, String... values)
    {
        assertEquals("Unexpected values in " + colName + " column", Arrays.asList(values), drt.getColumnDataAsText(colName));
    }

    @LogMethod
    private void importRun(String assayId, String assayName, File dataFile, boolean isDRB)
    {
        log("Importing Haplotype Run: " + assayId);
        goToAssayImport(assayName);
        setFormElement(Locator.name("name"), assayId);
        checkCheckbox(Locator.name("enabled"));
        selectOptionByText(Locator.name("speciesId"), "mamu");
        // NOTE: consider breaking these into seperate tests...
        if (isDRB)
            setDataAndColumnHeaderPropertiesForDRB(dataFile);
        else
            setDataAndColumnHeaderPropertiesForHaplotype(dataFile);

        clickButton("Save and Finish");
        waitForText(assayName + " Runs");
        assertElementPresent(Locator.linkWithText(assayId));
    }

    @LogMethod
    private void setDataAndColumnHeaderPropertiesForHaplotype(File dataFile)
    {
        setDataAndColumnHeaderProperties(dataFile);
        _ext4Helper.selectComboBoxItem("MHC-B Haplotype 1:", "Mamu-B Haplotype 1");
        _ext4Helper.selectComboBoxItem("MHC-B Haplotype 2:", "Mamu-B Haplotype 2");
    }

    @LogMethod
    private void setDataAndColumnHeaderPropertiesForDRB(File dataFile)
    {
        setDataAndColumnHeaderProperties(dataFile);
        _ext4Helper.selectComboBoxItem("MHC-DRB Haplotype 1:", "Mamu-DRB Haplotype 1");
        _ext4Helper.selectComboBoxItem("MHC-DRB Haplotype 2:", "Mamu-DRB Haplotype 2");
    }

    @LogMethod
    private void setDataAndColumnHeaderProperties(File dataFile)
    {
        // adding text to the data text area triggers the events to enable the comboboxes and load their stores
        Locator cb = Locator.xpath("//table[contains(@class,'disabled')]//label[text() = 'Lab Animal ID *:']");
        if (!isElementPresent(cb))
            fail("The Haplotype column header mapping comboboxes should be disbabled until the data is pasted in.");

        setFormElement(Locator.name("data"), TestFileUtils.getFileContents(dataFile));
        waitForElementToDisappear(cb, WAIT_FOR_JAVASCRIPT);
        _ext4Helper.selectComboBoxItem("Lab Animal ID *:", "OC ID");
        _ext4Helper.selectComboBoxItem("Client Animal ID:", "Animal ID");
        _ext4Helper.selectComboBoxItem("Total # Reads Evaluated:", "# Reads Merged");
        _ext4Helper.selectComboBoxItem("Total # Reads Identified:", "# Reads Identified");
        _ext4Helper.selectComboBoxItem("MHC-A Haplotype 1:", "Mamu-A Haplotype 1");
        _ext4Helper.selectComboBoxItem("MHC-A Haplotype 2:", "Mamu-A Haplotype 2");
    }

    private void goToAssayRun(String assayId)
    {
        log("Navigating to Haplotype assay run");
        goToAssayHome(ASSAY_NAME);
        clickAndWait(Locator.linkWithText(assayId));
        waitForText(ASSAY_NAME + " Results");
    }

    private void goToAssayImport(String assayName)
    {
        log("Navigating to Haplotype Assay Import");
        goToAssayHome(assayName);
        clickButton("Import Data");
        waitForText("Copy/Paste the rows, including the headers, into the text area below:");
        waitForText("Match the column headers from the tab-delimited data with the key fields:");
    }

    private void goToAssayHome(String assayName)
    {
        goToProjectHome();
        goToManageAssays();
        clickAndWait(Locator.linkWithText(assayName));
    }
}
