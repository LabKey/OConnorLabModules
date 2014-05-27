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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.ExecuteSqlCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.Locator;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.PasswordUtil;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({CustomModules.class})
public class IlluminaTest extends GenotypingBaseTest
{
    public static final String first454importNum = "207";
    public static final String second454importNum = "208";
    public static final String illuminaImportNum = "206";
    protected int pipelineJobCount = 0;

    String pipelineLoc =  getLabKeyRoot() + "/sampledata/genotyping";
    protected int runNum = 0; //this is globally unique, so we need to retrieve it every time.
    protected String checkboxId = ".select";
//    private String expectedAnalysisCount = "1 - 61 of 61";

    DataRegionTable drt = null;
    private String samples = "samples";
    private String TEMPLATE_NAME = "GenotypingTest Saved Template";

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
        verifyIlluminaExport();
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

    private void verifyIlluminaExport() throws Exception
    {
        log("Verifying FASTQ and ZIP export");

        String url = WebTestHelper.getBaseURL() + "/genotyping/" + getProjectName() + "/mergeFastqFiles.view";
        List<NameValuePair> args;
        HttpContext context = WebTestHelper.getBasicHttpContext();
        HttpPost method;
        HttpResponse response = null;
        SelectRowsResponse resp;

        try (CloseableHttpClient httpClient = (CloseableHttpClient)WebTestHelper.getHttpClient())
        {
            ExecuteSqlCommand cmd = new ExecuteSqlCommand("genotyping", "SELECT s.* from genotyping.SequenceFiles s LEFT JOIN (select max(rowid) as rowid from genotyping.Runs r WHERE platform = 'Illumina' group by rowid) r ON r.rowid = s.run");
            Connection cn = new Connection(getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());

            resp = cmd.execute(cn, getProjectName());
            assertTrue("Wrong number of files found.  Expected 30, found " + resp.getRows().size(), resp.getRows().size() == 30);

            //first try FASTQ merge
            method = new HttpPost(url);
            args = new ArrayList<>();
            for (Map<String, Object> row : resp.getRows())
            {
                args.add(new BasicNameValuePair("dataIds", row.get("DataId").toString()));
            }

            args.add(new BasicNameValuePair("zipFileName", "genotypingExport"));

            method.setEntity(new UrlEncodedFormEntity(args));
            response = httpClient.execute(method, context);
            int status = response.getStatusLine().getStatusCode();
            assertTrue("FASTQ was not Downloaded", status == HttpStatus.SC_OK);
            assertTrue("Response header incorrect", response.getHeaders("Content-Disposition")[0].getValue().startsWith("attachment;"));
            assertTrue("Response header incorrect", response.getHeaders("Content-Type")[0].getValue().startsWith("application/x-gzip"));


            try (
                    InputStream is = response.getEntity().getContent();
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
        finally
        {
            if (null != response)
                EntityUtils.consumeQuietly(response.getEntity());
        }

        try (CloseableHttpClient httpClient = (CloseableHttpClient)WebTestHelper.getHttpClient())
        {
            //then ZIP export
            url = WebTestHelper.getBaseURL() + "/experiment/" + getProjectName() + "/exportFiles.view";

            method = new HttpPost(url);
            args = new ArrayList<>();
            for (Map<String, Object> row : resp.getRows())
            {
                args.add(new BasicNameValuePair("dataIds", row.get("DataId").toString()));
            }

            args.add(new BasicNameValuePair("zipFileName", "genotypingZipExport"));
            method.setEntity(new UrlEncodedFormEntity(args));
            response = httpClient.execute(method, context);
            int status = response.getStatusLine().getStatusCode();
            assertEquals("Status code was incorrect", HttpStatus.SC_OK, status);
            assertEquals("Response header incorrect", "attachment; filename=\"genotypingZipExport\"", response.getHeaders("Content-Disposition")[0].getValue());
            assertEquals("Response header incorrect", "application/zip;charset=UTF-8", response.getHeaders("Content-Type")[0].getValue());
        }
        finally
        {
            if (null != response)
                EntityUtils.consumeQuietly(response.getEntity());
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

        xpath = xpath.replace("disabled", "labkey");
        click(Locator.name(checkboxId, 2));
        click(Locator.name(checkboxId, 3));
        click(Locator.name(checkboxId, 9));
        Locator exportButton = Locator.xpath(xpath);

        click(exportButton);
        waitForText("Export Files");
        assertTextPresent("ZIP Archive", "Merge");
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
        File dir = new File(pipelineLoc);
        FilenameFilter filter = new OutputFilter();
        File[] files = dir.listFiles(filter);

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
    public String getAssociatedModuleDirectory()
    {
        return "server/customModules/genotyping";
    }

}
