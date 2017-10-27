/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.categories.OConnor;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Arrays;
import java.util.List;

@Category({CustomModules.class, OConnor.class})
public class OConnorListTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static String PROJECT_NAME = "OConnorTestProject";
    private static String FOLDER_ZIP_FILE = "OConnor_Test.folder.zip";
    private static String[] MODULES = {"OConnor", "OConnorExperiments"};
    private static String[] SPECIMEN_TYPES = {"type1", "type2", "type3", "type4"};
    private static String[] EXPERIMENT_TYPES = {"name1", "name2", "name3", "name4"};
    private static String[] DISABLED_SPECIMEN_TYPES = {"disabledtype1", "disabledtype2"};
    private static String[] DISABLED_EXPERIMENT_TYPES = {"disabledname1", "disabledname2"};

    @Nullable
    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    public BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList(MODULES);
    }

    @BeforeClass
    @LogMethod
    public static void setup() throws Exception
    {
        OConnorListTest initTest = (OConnorListTest)getCurrentTest();
        initTest.setupOConnorProject();
    }

    private void setupOConnorProject()
    {
        _containerHelper.createProject(getProjectName(), "OConnor Purchasing System");
        _containerHelper.enableModules(Arrays.asList(MODULES));
        //TODO: turn query validation back on once query validation bugs are fixed
        importFolderFromZip(TestFileUtils.getSampleData(FOLDER_ZIP_FILE), false, 1);
        importExperimentTypes();
        importSpecimenTypes();
    }

    @LogMethod
    private void importExperimentTypes()
    {
        goToSchemaBrowser();
        selectQuery("OConnorExperiments", "ExperimentType");
        click(Locator.linkWithText("view data"));

        StringBuilder expTsv = new StringBuilder();
        expTsv.append("name\tenabled");
        for(String type : EXPERIMENT_TYPES)
        {
            expTsv.append("\n");
            expTsv.append(type);
            expTsv.append("\ttrue");
        }
        for(String type : DISABLED_EXPERIMENT_TYPES)
        {
            expTsv.append("\n");
            expTsv.append(type);
            expTsv.append("\tfalse");
        }
        _listHelper.uploadData(expTsv.toString());
    }

    @LogMethod
    private void importSpecimenTypes()
    {
        goToSchemaBrowser();
        selectQuery("oconnor", "specimen_type");
        click(Locator.linkWithText("view data"));
        DataRegionTable drt = new DataRegionTable("query", this);

        // NOTE: clear old values (as they are not container scoped)
        drt.setFilter("specimen_type", "Equals One Of (example usage: a;b;c)", StringUtils.join(ArrayUtils.addAll(SPECIMEN_TYPES, DISABLED_SPECIMEN_TYPES), ';'));
        if (drt.getDataRowCount() > 0)
        {
            drt.checkAll();
            doAndWaitForPageToLoad(() ->
            {
                clickButton("Delete", 0);
                assertAlert("Are you sure you want to delete the selected rows?");
            });
        }

        StringBuilder specimenTsv = new StringBuilder();
        specimenTsv.append("specimen_type\tenabled");
        for(String type : SPECIMEN_TYPES)
        {
            specimenTsv.append("\n");
            specimenTsv.append(type);
            specimenTsv.append("\ttrue");
        }
        for(String type : DISABLED_SPECIMEN_TYPES)
        {
            specimenTsv.append("\n");
            specimenTsv.append(type);
            specimenTsv.append("\tfalse");
        }
        _listHelper.uploadData(specimenTsv.toString());
    }

    @Test
    public void testImportedListValues()
    {
        goToProjectHome();
        for(String type : SPECIMEN_TYPES)
        {
            assertElementPresent(Locator.linkWithText(type));
        }
        //TODO: check that edit of imported experiment/specimen with disabled type still shows type
    }

    @Test
    public void testAvailableDDValues()
    {
        goToProjectHome();
        //check available experiment types
        DataRegionTable.findDataRegion(this).clickInsertNewRow();
        waitForElement(Locator.linkWithText("history"));
        click(Locator.xpath("//div[contains(@class, 'x4-trigger-index')]"));
//        List<String> options = new ArrayList<>();
//        List<WebElement> optionsEls = this.getDriver().findElements(By.className("list-item"));
//        for(WebElement optionEl : optionsEls){options.add(optionEl.getText());}
        //enabled types are present
        for(String option : EXPERIMENT_TYPES)
        {
            assertTextPresent(option);
        }
        //disabled types are not shown
        for(String d_option : DISABLED_EXPERIMENT_TYPES)
        {
            assertTextNotPresent(d_option);
        }
        //check available specimen types
        beginAt("/oconnor/OConnorTestProject/inventory_specimen_available.view?");
        waitForText("Inventory Specimen Available");
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan("Add new specimens")));
        click(Locator.linkWithSpan("Add new specimens"));
        waitForText("Specimen Details");
        click(Locator.id("specimen_type"));
        waitForText("type1");
        for(String option : SPECIMEN_TYPES)
        {
            //assert(options.contains(option));
            assertTextPresent(option);
        }
        //disabled types are not shown
        for(String d_option : DISABLED_SPECIMEN_TYPES)
        {
            //assert(!options.contains(d_option));
            //TODO: re-enable this test once bug is fixed
            assertTextNotPresent(d_option);
            //assertElementNotPresent(Locator.xpath("//div[@class='x-combo-list-item'][.='" + d_option + "']"));
        }
    }

    @Override
    public void checkQueries()
    {
        //skip query validation, queries depend on user defined lists and modules to be available in specific containers
        //TODO: update archived project in sampledata to set up project correctly
    }

    //assumes query webpart named "experimentType" is visible
    private void insertExperimentType(String type, Boolean enabled )
    {
        PortalHelper portalHelper = new PortalHelper(this);
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan(DataRegionTable.getInsertNewButtonText())));
        portalHelper.clickWebpartMenuItem("experimentType", DataRegionTable.getInsertNewButtonText());
        setFormElement(Locator.input("quf_Name"), type);
        if(!enabled){uncheckCheckbox(Locator.checkboxByName("quf_enabled"));}
        if(enabled){checkCheckbox(Locator.checkboxByName("quf_enabled"));}
        click(Locator.linkWithSpan("Submit"));
    }

    //assumes query webpart named "specimenType" is visible
    private void insertSpecimenType(String type, Boolean enabled)
    {
        PortalHelper portalHelper = new PortalHelper(this);
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan(DataRegionTable.getInsertNewButtonText())));
        portalHelper.clickWebpartMenuItem("specimenType", DataRegionTable.getInsertNewButtonText());
        setFormElement(Locator.input("quf_specimen_type"), type);
        if(!enabled){uncheckCheckbox(Locator.checkboxByName("quf_enabled"));}
        if(enabled){checkCheckbox(Locator.checkboxByName("quf_enabled"));}
        click(Locator.linkWithSpan("Submit"));
    }
}
