package org.labkey.test.tests;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.CustomModules;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Category({CustomModules.class})
public class PacBioTest extends GenotypingBaseTest
{
    private String pipelineLoc = TestFileUtils.getSampledataPath() + "/genotyping/PacBio";
    private static final String PAC_BIO_LIST_ARCHIVE = "pacbio.lists.zip";
    private static final String CSV_NAME = "SampleSheet.csv";
    private static final String PAC_BIO_RUN = "2";

    private static final Map<Integer, List<String>> POOL1_DATA =
            Collections.unmodifiableMap(new HashMap<Integer, List<String>>()
            {{
                    put(0, Arrays.asList("lbc1--lbc1.fastq", "1858", "5", "1"));
                    put(1, Arrays.asList("lbc2--lbc2.fastq", "1859", "12", "1"));
                }});
    private static final Map<Integer, List<String>> POOL2_DATA =
            Collections.unmodifiableMap(new HashMap<Integer, List<String>>()
            {{
                    put(0, Arrays.asList("lbc14--lbc14.fastq", "1871", "7", "2"));
                    put(1, Arrays.asList("lbc15--lbc15.fastq", "1872", "6", "2"));
                    put(2, Arrays.asList("lbc16--lbc16.fastq", "1873", "0", "2"));
                }});

    @BeforeClass
    public static void setupProject()
    {
        PacBioTest init = (PacBioTest)getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        setUp2(PAC_BIO_LIST_ARCHIVE, false);
    }

    @Override
    protected String getPipelineLoc()
    {
        return this.pipelineLoc;
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void verifyAllImports()
    {
        int importCount = 0;
        verifyRun("pool1_barcoded_fastqs", POOL1_DATA, importCount++);
        verifyRun("pool2_barcoded_fastqs", POOL2_DATA, importCount++);

        goToProjectHome();
        assertTextPresent(importCount + " runs");
    }

    @Test
    public void verifyFailOnBadSample()
    {
        importPacBioRun("badsample_fastqs", CSV_NAME, true);
    }

    @LogMethod
    private void verifyRun(String poolName, Map<Integer, List<String>> poolMap, int importCount)
    {
        importPacBioRun(poolName, CSV_NAME, false);
        goToProjectHome();
        clickAndWait(Locator.linkWithText("View Runs"));
        DataRegionTable runs = new DataRegionTable("Runs", this);
        assertEquals("Imported PacBio run # did not match data csv value", PAC_BIO_RUN, runs.getDataAsText(importCount, "runs"));

        clickRunLink(importCount);
        DataRegionTable reads = new DataRegionTable("Reads", this);
        List<String> realRow = reads.getRowDataAsText(importCount);
        List<String> correctRow = new ArrayList<>();
        correctRow.add(realRow.get(0));
        correctRow.addAll(poolMap.get(importCount));
        correctRow.add(null); // not sure why, but there's a null cell at the end dataregion data
        assertEquals("Imported data doesn't match expected for (0-based count) import " + importCount, correctRow, realRow);
    }

    private void importPacBioRun(String runDir, String file, boolean expectError)
    {
        goToProjectHome();
        clickAndWait(Locator.linkContainingText("Import Run"));
        _fileBrowserHelper.importFile("pacbio8/" + runDir + "/" + file, "Import PacBio Reads");
        clickButton("Import Reads");
        waitForPipelineJobsToComplete(++pipelineJobCount, "Import Run", expectError);
        if (expectError)
        {
            checkExpectedErrors(1);
            deletePipelineJob("Process PacBio reads for run", false, true);
            pipelineJobCount--;
        }
    }
}
