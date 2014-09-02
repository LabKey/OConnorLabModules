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
import org.labkey.test.util.PortalHelper;
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
        PortalHelper portalHelper = new PortalHelper(this);
        _containerHelper.createProject(getProjectName(), "OConnor Purchasing System");
        _containerHelper.enableModules(Arrays.asList(MODULES));
        //TODO: turn query validation back on once query validation bugs are fixed
        importFolderFromZip(TestFileUtils.getSampleData(FOLDER_ZIP_FILE), false, 1);
        goToProjectHome();
//        portalHelper.addQueryWebPart("experimentType", "oconnor", "experiment_types", null);
//        portalHelper.addQueryWebPart("specimenType", "oconnor", "specimen_type", null);
//        for(String enabledSpecimenType : SPECIMEN_TYPES)
//        {insertSpecimenType(enabledSpecimenType, true);}
//        for(String disabledSpecimenType : DISABLED_SPECIMEN_TYPES)
//        {insertSpecimenType(disabledSpecimenType, false);}
//        for(String enabledExperimentType : EXPERIMENT_TYPES)
//        {insertExperimentType(enabledExperimentType, true);}
//        for(String disabledExperimentType : DISABLED_EXPERIMENT_TYPES)
//        {insertExperimentType(disabledExperimentType, false);}
        goToSchemaBrowser();
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.xpath("//span[.='OConnorExperiments']").toBy()));
        click(Locator.xpath("//span[.='OConnorExperiments']"));
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan("ExperimentType").toBy()));
        click(Locator.linkWithSpan("ExperimentType"));
        click(Locator.linkWithText("view data"));
        for(String type : EXPERIMENT_TYPES)
        {
            waitForElement(Locator.linkWithSpan("Insert New"));
            click(Locator.linkWithSpan("Insert New"));
            setFormElement(Locator.name("quf_Name"), type);
            click(Locator.linkWithSpan("Submit"));
        }
        for(String type : DISABLED_EXPERIMENT_TYPES)
        {
            waitForElement(Locator.linkWithSpan("Insert New"));
            click(Locator.linkWithSpan("Insert New"));
            setFormElement(Locator.name("quf_Name"), type);
            uncheckCheckbox(Locator.checkboxByName("quf_Enabled"));
            click(Locator.linkWithSpan("Submit"));
        }
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
        click(Locator.linkWithText("Insert New"));
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
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan("Add new specimens").toBy()));
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
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan("Insert New").toBy()));
        portalHelper.clickWebpartMenuItem("experimentType", "Insert New");
        setFormElement(Locator.input("quf_Name"), type);
        if(!enabled){uncheckCheckbox(Locator.checkboxByName("quf_enabled"));}
        if(enabled){checkCheckbox(Locator.checkboxByName("quf_enabled"));}
        click(Locator.linkWithSpan("Submit"));
    }

    //assumes query webpart named "specimenType" is visible
    private void insertSpecimenType(String type, Boolean enabled)
    {
        PortalHelper portalHelper = new PortalHelper(this);
        shortWait().until(ExpectedConditions.elementToBeClickable(Locator.linkWithSpan("Insert New").toBy()));
        portalHelper.clickWebpartMenuItem("specimenType", "Insert New");
        setFormElement(Locator.input("quf_specimen_type"), type);
        if(!enabled){uncheckCheckbox(Locator.checkboxByName("quf_enabled"));}
        if(enabled){checkCheckbox(Locator.checkboxByName("quf_enabled"));}
        click(Locator.linkWithSpan("Submit"));
    }
}
