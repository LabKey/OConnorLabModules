/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.DeleteRowsCommand;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.InsertRowsCommand;
import org.labkey.remoteapi.query.SaveRowsResponse;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.UpdateRowsCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PasswordUtil;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.join;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({CustomModules.class})
public class OConnorExperimentTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static final String PROJECT_NAME = "OConnor Experiment Project";
    private static final String MODULE_NAME = "OConnorExperiments";
    private static final String SCHEMA_NAME = MODULE_NAME;
    private static final String TABLE_NAME = "Experiments";
    private static final String EXPERIMENT_TYPE_TABLE_NAME = "ExperimentType";
    private ArrayList<String> pkeys = new ArrayList<>();

    @Nullable
    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @LogMethod(category = LogMethod.MethodType.SETUP)
    protected void doSetup()
    {
        _containerHelper.createProject(PROJECT_NAME, null);
        _containerHelper.enableModule(PROJECT_NAME, MODULE_NAME);

        // Add the experiment webpart to the portal page
        goToProjectHome();
        PortalHelper portalHelper = new PortalHelper(this);
        portalHelper.addWebPart("OConnorExperiments");

        log("setup complete");
    }

    @Test
    public void testSteps()
    {
        doSetup();

        createExperimentTypes();
        goToProjectHome();
        insertViaExperimentsWebpart("description1", "type1", null);
        insertViaExperimentsWebpart("description2", "type2", "1");
        insertViaExperimentsWebpart("description3", "type3", "1,2");

        verifyExperimentWebpart(2, "description1", "type1");
        verifyExperimentWebpart(1, "description2", "type2", 1);
        verifyExperimentWebpart(0, "description3", "type3", 1,2);

        // update a single row
        updateViaExperimentWebpart(0, "updated description 3", "type2", "1");
        verifyExperimentWebpart(0, "updated description 3", "type2", 1);
        updateViaExperimentWebpart(0, "updated description 3", "type3", "1,2");
        verifyExperimentWebpart(0, "updated description 3", "type3", 1,2);

        testBulkUpdate();

        // delete via the webpart
        DataRegionTable table = new DataRegionTable("query", this);
        table.uncheckAll();
        table.checkCheckbox(0);
        waitForElement(Locator.lkButton("Delete"));
        click(Locator.lkButton("Delete"));
        getAlert();

        assertTrue(table.getDataRowCount() == 2);

        // test via api's
        insertViaJavaApi();
        verifyExperimentWebpart(0, "API Description", null);
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void verifyExperimentWebpart(int row, String description, @Nullable String type, int... parentExperiments)
    {
        // Verify the Experiment is inserted, examine OConnorExperiment webpart
        DataRegionTable table = new DataRegionTable("query", this);
        assertEquals(description, table.getDataAsText(row, "Description"));
        if (type != null)
        {
            assertEquals(type, table.getDataAsText(row, "ExperimentType"));
        }

        // Make sure each component of the ParentExperiments column is rendered with a link to the begin page for that experiment
        Locator.XPathLocator l = table.xpath(row, table.getColumn("ParentExperiments"));
        for (int i : parentExperiments)
        {
            Locator.XPathLocator link = l.child("a[" + i + "]");
            String parentExpText = getText(link);
            String parentExpHref = getAttribute(link, "href");
            assertTrue("Expected link to go to project begin for " + parentExpText + ", got: " + parentExpHref,
                    parentExpHref.contains("/" + parentExpText + "/begin.view") || parentExpHref.contains("/" + parentExpText + "/project-begin.view"));
        }
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void createExperimentTypes()
    {
        log("creating the experiment type lookups");

        beginAt("/query/" + getProjectName() + "/insertQueryRow.view?schemaName=" + SCHEMA_NAME + "&query.queryName=" + EXPERIMENT_TYPE_TABLE_NAME);
        waitForElement(Locator.name("quf_Name"));
        setFormElement(Locator.name("quf_Name"), "type1");
        clickButton("Submit");
        beginAt("/query/" + getProjectName() + "/insertQueryRow.view?schemaName=" + SCHEMA_NAME + "&query.queryName=" + EXPERIMENT_TYPE_TABLE_NAME);
        waitForElement(Locator.name("quf_Name"));
        setFormElement(Locator.name("quf_Name"), "type2");
        clickButton("Submit");
        beginAt("/query/" + getProjectName() + "/insertQueryRow.view?schemaName=" + SCHEMA_NAME + "&query.queryName=" + EXPERIMENT_TYPE_TABLE_NAME);
        waitForElement(Locator.name("quf_Name"));
        setFormElement(Locator.name("quf_Name"), "type3");
        clickButton("Submit");
    }

    /**
     * Insert a new experiment using the OConnorExperiment webpart
     */
    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void insertViaExperimentsWebpart(String description, String type, @Nullable String parentExperiment)
    {
        waitForElement(Locator.lkButton("Insert New"));
        click(Locator.lkButton("Insert New"));

        editExperiment(description, type, parentExperiment);
        goToProjectHome();
    }

    protected void updateViaExperimentWebpart(int row, String description, String type, @Nullable String parentExperiment)
    {
        DataRegionTable table = new DataRegionTable("query", this);
        Locator.XPathLocator l = table.xpath(row, 0);
        waitForElement(l);
        clickAndWait(table.detailsLink(row));

        editExperiment(description, type, parentExperiment);
        goToProjectHome();
    }

    protected void editExperiment(String description, String type, @Nullable String parentExperiment)
    {
        // set the description field
        setEditInPlaceContent("Description:", description);

        // parent experiments field
        if (parentExperiment != null)
        {
            setEditInPlaceContent("Parent Experiments:", parentExperiment);
        }

        // the type combobox
        _ext4Helper.selectComboBoxItem("Experiment Type:", Ext4Helper.TextMatchTechnique.CONTAINS, type);
    }

    protected void setEditInPlaceContent(String label, String text)
    {
        Locator.XPathLocator cmp = Locator.tagWithClass("div", "ocexp-edit-in-place-text").withDescendant(Locator.tagContainingText("label", label));
        cmp = cmp.append(Locator.tagWithClass("div", "x4-form-display-field"));
        waitForElement(cmp);
        click(cmp);

        Locator.XPathLocator input = Locator.tagWithClass("div", "ocexp-edit-in-place-text").withDescendant(Locator.tagContainingText("label", label));
        cmp = input.append(Locator.xpath("//textarea"));
        if (!isElementPresent(cmp))
            cmp = input.append(Locator.xpath("//input"));

        waitForElement(cmp);
        setFormElement(cmp, text);

        WebElement el = cmp.findElement(getDriver());
        fireEvent(el, SeleniumEvent.blur);
        shortWait().until(ExpectedConditions.not(ExpectedConditions.visibilityOf(el)));
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void insertViaJavaApi()
    {
        log("** Inserting via api...");
        try
        {
            Map<String,Object> rowMap;
            rowMap = new HashMap<>();
            rowMap.put("Description", "API Description");
            rowMap.put("ExperimentType", "API Type");
            Connection cn = new Connection(getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
            InsertRowsCommand insertCmd = new InsertRowsCommand(SCHEMA_NAME, TABLE_NAME);
            insertCmd.addRow(rowMap);
            SaveRowsResponse resp = insertCmd.execute(cn, getProjectName());
            for (int i = 0; i < insertCmd.getRows().size(); i++)
            {
                Map<String, Object> row = resp.getRows().get(i);
                assertTrue(row.containsKey("container"));
                pkeys.add(row.get("container").toString());
            }
        }
        catch (CommandException e)
        {
            log("CommandException: " + e.getMessage());
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            log("IOException: " + e.getMessage());
            throw new RuntimeException(e);
        }
        goToProjectHome();
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void updateViaJavaApi()
    {
        log("** Updating via api...");
        try
        {
            UpdateRowsCommand cmd = new UpdateRowsCommand(SCHEMA_NAME, TABLE_NAME);
            Map<String,Object> rowMap;
            rowMap = new HashMap<>();
            rowMap.put("Description", "API Description Edited");
            rowMap.put("ExperimentType", "API Type Edited");
            Connection cn = new Connection(getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
            SaveRowsResponse resp = cmd.execute(cn, getProjectName());
            assertEquals("Expected to update " + rowMap.size() + " rows", rowMap.size(), resp.getRowsAffected().intValue());
            goToProjectHome();
        }
        catch (CommandException e)
        {
            log("CommandException: " + e.getMessage());
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            log("IOException: " + e.getMessage());
            throw new RuntimeException(e);
        }
        goToProjectHome();
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void deleteViaJavaApi()
    {
        log("** Deleting via api: pks=" + join(",", pkeys) + "...");
        try
        {
            DeleteRowsCommand cmd = new DeleteRowsCommand(SCHEMA_NAME, TABLE_NAME);
            Connection cn = new Connection(getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());
            for (String pk : pkeys)
                cmd.addRow(Collections.singletonMap("container", (Object) pk));

            SaveRowsResponse resp = cmd.execute(cn, getProjectName());
            assertEquals("Expected to delete " + pkeys.size() + " rows", pkeys.size(), resp.getRowsAffected().intValue());

            SelectRowsCommand selectCmd = new SelectRowsCommand(SCHEMA_NAME, TABLE_NAME);
            selectCmd.addFilter("RowId", join(";", pkeys), Filter.Operator.IN);
            SelectRowsResponse selectResp = selectCmd.execute(cn, getProjectName());
            assertEquals("Expected to select 0 rows", 0, selectResp.getRowCount().intValue());
        }
        catch (CommandException e)
        {
            log("CommandException: " + e.getMessage());
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            log("IOException: " + e.getMessage());
            throw new RuntimeException(e);
        }
        goToProjectHome();
    }

    @LogMethod(category = LogMethod.MethodType.VERIFICATION)
    protected void testBulkUpdate()
    {
        DataRegionTable table = new DataRegionTable("query", this);
        table.checkAll();
        waitForElement(Locator.lkButton("Bulk Edit"));
        click(Locator.lkButton("Bulk Edit"));

        editMultipleExperiments("bulk edit description", "type2", "1", null, null);
        verifyExperimentWebpart(0, "bulk edit description", "type2", 1);
        verifyExperimentWebpart(1, "bulk edit description", "type2", 1);
        verifyExperimentWebpart(2, "bulk edit description", "type2", 1);

        table = new DataRegionTable("query", this);
        table.uncheckAll();
        table.checkCheckbox(0);
        table.checkCheckbox(2);
        waitForElement(Locator.lkButton("Bulk Edit"));
        click(Locator.lkButton("Bulk Edit"));

        editMultipleExperiments("multiple edit description", "type3", "1", "bulk edit description", "type2");
        verifyExperimentWebpart(0, "multiple edit description", "type3", 1);
        verifyExperimentWebpart(1, "bulk edit description", "type2", 1);
        verifyExperimentWebpart(2, "multiple edit description", "type3", 1);
    }

    protected void editMultipleExperiments(String description, String type, String parentExperiment,
                                           @Nullable String oldDescription, @Nullable String oldType)
    {
        waitForElement(Locator.name("quf_Description"));
        if (oldDescription != null)
        {
            assertEquals(oldDescription, getText(Locator.name("quf_Description")));
        }
        setFormElement(Locator.name("quf_Description"), description);

        waitForElement(Locator.name("quf_ExperimentTypeId"));
        if (oldType != null)
        {
            assertEquals(oldType, getSelectedOptionText(Locator.name("quf_ExperimentTypeId")));
        }
        selectOptionByText(Locator.name("quf_ExperimentTypeId"), type);

        waitForElement(Locator.name("quf_ParentExperiments"));
        selectOptionByText(Locator.name("quf_ParentExperiments"), parentExperiment);

        clickButton("Submit");
        goToProjectHome();
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("OConnorExperiments");
    }

    @Override public BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}

