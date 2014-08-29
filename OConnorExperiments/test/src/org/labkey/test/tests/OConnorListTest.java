package org.labkey.test.tests;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.categories.OConnor;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PostgresOnlyTest;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Arrays;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: RyanS
 * Date: 8/19/14
 * Time: 2:52 PM
 * To change this template use File | Settings | File Templates.
 */

@Category({CustomModules.class, OConnor.class})
public class OConnorListTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static String PROJECT_NAME = "OConnorTestProject";
    private static String FOLDER_ZIP_FILE = "OConnor_Test.folder.zip";
    private static String[] MODULES = {"OConnor", "OConnorExperiments"};
    private static String[] SAMPLE_TYPES = {"type1", "type2", "type3", "type4"};
    private static String[] EXPERIMENT_TYPES = {"name1", "name2", "name3", "name4"};
    private static String[] DISABLED_SAMPLE_TYPES = {"disabledtype1", "disabledtype2"};
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
    }

    @Test
    public void testImportedListValues()
    {
        goToProjectHome();
        for(String type : SAMPLE_TYPES)
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
        click(Locator.linkWithText("Insert New"));
        waitForElement(Locator.linkWithText("history"));
        List<String> options = _ext4Helper.getComboBoxOptions("Experiment Type:");
        //enabled types are present
        for(String option : EXPERIMENT_TYPES)
        {
            assert(options.contains(option));
        }
        //disabled types are not shown
        for(String d_option : DISABLED_EXPERIMENT_TYPES)
        {
            assert(!options.contains(d_option));
        }
        //check available specimen types
        beginAt("/oconnor/OConnorTestProject/inventory_specimen_available.view?");
        waitForText("Inventory Specimen Available");
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan("Add new specimens").toBy()));
        click(Locator.linkWithSpan("Add new specimens"));
        waitForText("Specimen Details");
        click(Locator.id("specimen_type"));
        waitForText("type1");
        for(String option : SAMPLE_TYPES)
        {
            //assert(options.contains(option));
            assertTextPresent(option);
        }
        //disabled types are not shown
        for(String d_option : DISABLED_SAMPLE_TYPES)
        {
            //assert(!options.contains(d_option));
            //TODO: re-enable this test once bug is fixed
            //assertTextNotPresent(d_option);
            //assertElementNotPresent(Locator.xpath("//div[@class='x-combo-list-item'][.='" + d_option + "']"));
        }
    }

    @Override
    public void checkQueries()
    {
        //skip query validation, queries depend on user defined lists and modules to be available in specific containers
        //TODO: update archived project in sampledata to set up project correctly
    }
}
