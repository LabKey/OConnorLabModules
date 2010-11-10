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
    public FastqWriter(FastqGenerator generator)
    {
        super(generator);
    }

    @Override
    protected void writeEntry(PrintWriter pw, FastqEntry entry)
    {
        pw.print("@");
        pw.println(entry.getHeader());
        pw.println(entry.getSequence());
        pw.print("+");
        pw.println(entry.getHeader());
        pw.println(entry.getQuality());
    }

    public interface FastqEntry extends FastaEntry
    {
        String getQuality();
    }
}
