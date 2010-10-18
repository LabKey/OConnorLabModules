package org.labkey.genotyping.sequences;

import org.labkey.api.writer.FastaEntry;
import org.labkey.api.writer.FastaWriter;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: Oct 15, 2010
 * Time: 11:57:10 PM
 */
public class FastqWriter extends FastaWriter<FastqWriter.FastqEntry>
{
    public FastqWriter(FastqGenerator generator)
    {
        super(generator);
    }

    @Override
    protected void writeEntry(PrintWriter pw, FastqEntry entry)
    {
        pw.print(">");
        pw.println(entry.getHeader());
        pw.println(entry.getSequence());
        pw.println(entry.getQuality());
    }

    public interface FastqEntry extends FastaEntry
    {
        String getQuality();
    }
}
