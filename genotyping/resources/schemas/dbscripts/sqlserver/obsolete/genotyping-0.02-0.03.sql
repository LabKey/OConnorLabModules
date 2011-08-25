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

-- Create new clustered index to help with base sort on reads table
ALTER TABLE genotyping.ReadsJunction
    DROP CONSTRAINT FK_ReadsJunction_Reads
GO

ALTER TABLE genotyping.Reads
    DROP CONSTRAINT PK_Reads
GO

CREATE CLUSTERED INDEX IDX_ReadsRunRowId ON genotyping.Reads (Run, RowId)
GO

ALTER TABLE genotyping.Reads
    ADD CONSTRAINT PK_Reads PRIMARY KEY NONCLUSTERED (RowId)
GO

ALTER TABLE genotyping.ReadsJunction
    ADD CONSTRAINT FK_ReadsJunction_Reads FOREIGN KEY (ReadId) REFERENCES genotyping.Reads(RowId)
GO
