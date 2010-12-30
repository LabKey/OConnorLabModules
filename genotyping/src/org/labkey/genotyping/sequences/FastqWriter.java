/*
 * Copyright (c) 2010 LabKey Corporation
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
    private final boolean _filterOutLowQualityBases;

    public FastqWriter(FastqGenerator generator, boolean filterOutLowQualityBases)
    {
        super(generator);
        _filterOutLowQualityBases = filterOutLowQualityBases;
    }

    @Override
    protected void writeEntry(PrintWriter pw, FastqEntry entry)
    {
        StringBuilder sequence = new StringBuilder(entry.getSequence());
        StringBuilder quality = new StringBuilder(entry.getQuality());
        String header = entry.getHeader();

        if (sequence.length() != quality.length())
            throw new IllegalArgumentException("Sequence length does not equal quality length for " + header);

        if (_filterOutLowQualityBases)
        {
            int length = sequence.length();

            for (int i = length - 1; i >= 0; i--)
            {
                if (Character.isLowerCase(sequence.charAt(i)))
                {
                    sequence.delete(i, i + 1);
                    quality.delete(i, i + 1);
                }
            }

            // They came in equal... not equal now would indicate a programming error
            if (sequence.length() != quality.length())
                throw new IllegalStateException("After filtering, sequence length does not equal quality length for " + header);
        }

        pw.print("@");
        pw.println(header);
        pw.println(sequence);
        pw.print("+");
        pw.println(header);
        pw.println(quality);
    }

    public interface FastqEntry extends FastaEntry
    {
        String getQuality();
    }
}
