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

import org.labkey.api.reader.FastaLoader;

import java.io.File;

/**
 * User: adam
 * Date: Aug 13, 2010
 * Time: 9:44:05 AM
 */
public class AlleleFastaLoader extends FastaLoader<Allele>
{
    protected AlleleFastaLoader(File fastaFile)
    {
        super(fastaFile, new FastaIteratorElementFactory<Allele>() {
            @Override
            public Allele createNext(String header, byte[] body)
            {
                return new Allele(header, body);
            }
        });
    }

    @Override
    public FastaIterator iterator()
    {
        return new AlleleIterator();
    }

    public class AlleleIterator extends FastaIterator
    {
        private AlleleIterator()
        {
        }
    }
}
