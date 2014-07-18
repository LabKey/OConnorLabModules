/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.test.SortDirection;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({CustomModules.class})
public class IlluminaTest extends GenotypingBaseTest
{
    public static final String illuminaImportNum = "206";
    protected int pipelineJobCount = 0;

    File pipelineLoc = new File(TestFileUtils.getLabKeyRoot(), "/sampledata/genotyping");
    protected String checkboxId = ".select";
//    private String expectedAnalysisCount = "1 - 61 of 61";

    private static final String TEMPLATE_NAME = "GenotypingTest Saved Template";

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

        verifyIlluminaSampleSheet();
        goToProjectHome();
        importIlluminaRunTest();
        verifyFASTQExport();
        verifyZipExport();
        verifyCleanIlluminaSampleSheets();
    }

    //verify that with good data, there is no QC warning when creating an illumina sample sheet
    //https://docs.google.com/a/labkey.com/file/d/0B45Fm0-0-NLtdmpDR1hKaW5jSWc/edit
    private void verifyCleanIlluminaSampleSheets()
    {
        importFolderFromZip(new File(pipelineLoc, "/genoCleanSamples.folder.zip"), true, 2);
        goToProjectHome();
        click(Locator.linkWithText("Samples"));
        waitForText("SIVkcol2");
        DataRegionTable d = new DataRegionTable("query", this);
        d.checkAllOnPage();
        clickButton("Create Illumina Sample Sheet");
        waitForText("You have chosen to export 6 samples");
        assertElementNotPresent(Locator.tag("label").containing("Warning").notHidden());
    }

    private void importIlluminaRunTest()
    {
        log("import illumina run");
        startImportIlluminaRun("IlluminaSamples.csv", "Import Illumina Reads");
        waitForPipelineJobsToComplete(++pipelineJobCount, "Import Run", false);

        goToProjectHome();
        clickAndWait(Locator.linkWithText("View Runs"));
        clickRunLink(illuminaImportNum);

        verifyIlluminaSamples();
    }

    @LogMethod
    private void verifyZipExport() throws Exception
    {
        final File export = exportAllFiles(ExportType.ZIP, "genotypingExport.zip");

        final int expectedLength = 30;
        doesElementAppear(new Checker()
        {
            @Override
            public boolean check()
            {
                int fileCount;
                try
                {
                    fileCount = getFileCountInZip(export);
                }
                catch (Exception e)
                {
                    return false;
                }
                return expectedLength == fileCount;
            }
        }, WAIT_FOR_JAVASCRIPT);

        assertEquals("Wrong number of zipped files", expectedLength, getFileCountInZip(export));
    }

    private int getFileCountInZip(File file) throws Exception
    {
        int count = 0;
        try (
                InputStream is = new FileInputStream(file);
                ZipInputStream zip = new ZipInputStream(is))
        {
            while (zip.getNextEntry() != null)
            {
                count++;
            }
        }
        return count;
    }

    @LogMethod
    private void verifyFASTQExport() throws Exception
    {
        File export = exportAllFiles(ExportType.FASTQ, "genotypingExport");
        try (
                InputStream is = new FileInputStream(export);
                GZIPInputStream gz = new GZIPInputStream(is);
                BufferedReader br = new BufferedReader(new InputStreamReader(gz)))
        {
            int count = 0;
            while (br.readLine() != null)
            {
                count++;
            }

            int expectedLength = 1088;
            assertTrue("Length of file doesnt match expected value of " + expectedLength + ", was: " + count, count == expectedLength);
        }
    }

    private File exportAllFiles(ExportType fileType, String filePrefix)
    {
        DataRegionTable data = new DataRegionTable("Reads", this);
        data.checkAll();

        clickButton("Download Selected", 0);
        waitForElement(Ext4Helper.Locators.window("Export Files"));
        assertTextPresent("You have chosen to export 272 reads");

        _ext4Helper.selectRadioButton(fileType.getRadioLabel());
        setFormElement(Locator.name("filePrefix"), filePrefix);

        File exportFile = clickAndWaitForDownload(Ext4Helper.Locators.ext4Button("Submit"));
        assertEquals("Illumina export has wrong file name.", filePrefix + fileType.getFileSuffix(), exportFile.getName());

        return exportFile;
    }

    private enum ExportType
    {
        ZIP("ZIP Archive of Individual Files", ""), //TODO: ZIP export doesn't currently append the correct file extension
        FASTQ("Merge into Single FASTQ File", ".fastq.gz");

        private String _radioLabel;
        private String _fileSuffix;

        private ExportType(String radioLabel, String fileSuffix)
        {
            _radioLabel = radioLabel;
            _fileSuffix = fileSuffix;
        }

        private String getRadioLabel()
        {
            return _radioLabel;
        }

        public String getFileSuffix()
        {
            return _fileSuffix;
        }
    }

    private void verifyIlluminaSampleSheet()
    {
        goToProjectHome();
        clickAndWait(Locator.linkWithText("Samples"));
        DataRegionTable d = new DataRegionTable("query", this);
        String viewName = "Yellow Peas";
        createCusomizedView(viewName, new String[]{"Created"}, new String[] {"fivemid"});
        d.checkAllOnPage();
        clickButton("Create Illumina Sample Sheet");
        waitForText("Reagent Cassette Id");
        Ext4FieldRef.getForLabel(this, "Reagent Cassette Id").setValue("FlowCell");

        String[][] fieldPairs = {
            {"Investigator Name", "Investigator"},
            {"Experiment Number", "Experiment"},
            {"Project Name", "Project"},
            {"Description", "Description"}
        };

        for (String[] a : fieldPairs)
        {
            Ext4FieldRef.getForLabel(this, a[0]).setValue(a[1]);
        }

        _ext4Helper.clickTabContainingText("Preview Header");
        waitForText("Edit Sheet");
        for (String[] a : fieldPairs)
        {
            assertEquals(a[1], Ext4FieldRef.getForLabel(this, a[0]).getValue());
        }

        clickButton("Edit Sheet", 0);
        waitForText("Done Editing");
        for (String[] a : fieldPairs)
        {
            assertTextPresent(a[0] + "," + a[1]);
        }

        //add new values
        String prop_name = "NewProperty";
        String prop_value = "NewValue";
        Ext4FieldRef textarea = _ext4Helper.queryOne("textarea[itemId='sourceField']", Ext4FieldRef.class);
        String newValue = prop_name + "," + prop_value;
        String currentText = (String)textarea.getEval("getValue()");
        textarea.eval("setValue(arguments[0] + \"\\n\" + arguments[1])", currentText, newValue);
        clickButton("Done Editing", 0);

        waitForElement(Locator.tag("label").withText("Warning: Sample indexes do not support both color channels at each position. See Preview Samples tab for more information."));

        //verify template has changed
        _ext4Helper.clickTabContainingText("General Info");
        assertEquals("Custom", Ext4FieldRef.getForLabel(this, "Template").getValue());

        //set custom view
        _ext4Helper.selectComboBoxItem("Custom View:", viewName);

        //verify values persisted
        _ext4Helper.clickTabContainingText("Preview Header");
        waitForText("Edit Sheet");
        assertEquals(prop_value, Ext4FieldRef.getForLabel(this, prop_name).getValue());

        //save template
        clickButton("Save As Template", 0);
        waitForElement(Ext4Helper.Locators.window("Choose Name"));
        Ext4FieldRef textfield = _ext4Helper.queryOne("textfield", Ext4FieldRef.class);
        textfield.setValue(TEMPLATE_NAME);
        clickButton("OK", 0);
        _ext4Helper.clickTabContainingText("General Info");
        assertEquals(TEMPLATE_NAME, Ext4FieldRef.getForLabel(this, "Template").getValue());

        //if we navigate too quickly, before the insertRows has returned, the test can get a JS error
        //therefore we sleep
        sleep(200);

        //verify samples present
        _ext4Helper.clickTabContainingText("Preview Samples");
        waitForText("Sample Name");

        int expectRows =  966; //(16 * (49 +  1)) + 16;  //11 cols, 45 rows, plus header and validation row (which is only 8 cols)
        assertEquals(expectRows, getElementCount(Locator.xpath("//td[contains(@class, 'x4-table-layout-cell')]")));

        //make sure values persisted
        refresh();
        String url = getCurrentRelativeURL();
        url += "&exportAsWebPage=1";
        beginAt(url);

        waitForElement(Ext4Helper.Locators.formItemWithLabel("Template:"));
        for (String[] a : fieldPairs)
        {
            Ext4FieldRef.getForLabel(this, a[0]).setValue(a[1]);
        }
        Ext4FieldRef combo = Ext4FieldRef.getForLabel(this, "Template");
        combo.setValue(TEMPLATE_NAME);

        int count = ((Long)combo.getEval("store.getCount()")).intValue();
        assertEquals("Combo store does not have correct record number", 3, count);
        sleep(50);
        assertEquals("Field value not set correctly", TEMPLATE_NAME, Ext4FieldRef.getForLabel(this, "Template").getValue());
        _ext4Helper.clickTabContainingText("Preview Header");
        waitForText("Edit Sheet");
        assertEquals(prop_value, Ext4FieldRef.getForLabel(this, prop_name).getValue());

        clickButton("Download");

        for (String[] a : fieldPairs)
        {
            assertTextPresent(a[0] + "," + a[1]);
        }

        assertTextPresent(prop_name + "," + prop_value);
        goToHome();
        goToProjectHome();
    }

    private void createCusomizedView(String viewName, String[] columnsToAdd, String[] columnsToRemove )
    {
        _customizeViewsHelper.openCustomizeViewPanel();

        for(String column : columnsToAdd)
        {
            _customizeViewsHelper.addCustomizeViewColumn(column);
        }

        for(String column : columnsToRemove)
        {
            _customizeViewsHelper.removeCustomizeViewColumn(column);
        }

        _customizeViewsHelper.saveCustomView(viewName);
    }

    private void assertExportButtonPresent()
    {
        String xpath =  "//a[contains(@class, 'disabled-button')]/span[text()='Download Selected']";
        assertElementPresent(Locator.xpath(xpath));
        assertElementPresent(Locator.paginationText(30));

        DataRegionTable table = new DataRegionTable("Reads", this);
        table.setSort("DataId", SortDirection.ASC);
        xpath = xpath.replace("disabled", "labkey");
        click(Locator.name(checkboxId, 2));
        click(Locator.name(checkboxId, 3));
        click(Locator.name(checkboxId, 9));
        Locator exportButton = Locator.xpath(xpath);

        click(exportButton);
        waitForElement(Ext4Helper.Locators.window("Export Files"));
        assertTextPresent("You have chosen to export 27 reads", "ZIP Archive", "Merge");
        clickButtonContainingText("Cancel", 0);
        _extHelper.waitForExt3MaskToDisappear(WAIT_FOR_JAVASCRIPT);

    }

    private class OutputFilter implements FilenameFilter
    {
        public boolean accept(File dir, String name)
        {
            return name.startsWith("IlluminaSamples-");
        }
    }
    private void verifyIlluminaSamples()
    {
        assertExportButtonPresent();
        FilenameFilter filter = new OutputFilter();
        File[] files = pipelineLoc.listFiles(filter);

        assertEquals(30, files.length);
        DataRegionTable d = new DataRegionTable("Reads", this);
        assertEquals(d.getDataRowCount(), 30);
        assertTextPresent("Read Count");
        assertEquals("9", d.getDataAsText(d.getIndexWhereDataAppears("IlluminaSamples-R1-4947.fastq.gz", "Filename") + 1, "Read Count"));
    }

    private void startImportIlluminaRun(String file, String importAction)
    {
        clickAndWait(Locator.linkContainingText("Import Run"));
        _fileBrowserHelper.expandFileBrowserRootNode();
        _fileBrowserHelper.importFile(file, importAction);
        selectOptionByText(Locator.name("run"), illuminaImportNum);
        setFormElement(Locator.name("prefix"), "IlluminaSamples-");
        clickButton("Import Reads");

    }

    @Override
    public java.util.List<String> getAssociatedModules()
    {
        return Arrays.asList("genotyping");
    }

}
