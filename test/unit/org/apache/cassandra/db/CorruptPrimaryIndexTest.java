/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.db;

import org.junit.Test;

import org.apache.cassandra.cql3.CQLTester;
import org.apache.cassandra.cql3.UntypedResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CorruptPrimaryIndexTest extends CQLTester.InMemory
{
    @Test
    public void bigPrimaryIndexDoesNotDetectDiskCorruption()
    {
        // Set listener early, before the file is opened; mmap access can not be listened to, so need to observe the open, which happens on flush
        fs.onPostRead(isCurrentTableIndexFile(keyspace()), (path, channel, position, dst, read) -> {
            // Reading the Primary index for the test!
            // format
            // 2 bytes: length of bytes for PK
            // 4 bytes: pk as an int32
            // variable bytes (see org.apache.cassandra.io.sstable.format.big.RowIndexEntry.IndexSerializer.deserialize(org.apache.cassandra.io.util.FileDataInput))
            assertThat(position).describedAs("Unexpected access, should start read from start of file").isEqualTo(0);

            // simulate bit rot by having 1 byte change... but make sure it's the pk!
            dst.put(2, Byte.MAX_VALUE);
        });

        createTable("CREATE TABLE %s (id int PRIMARY KEY, value int)");
        execute("INSERT INTO %s (id, value) VALUES (?, ?)", 0, 0);
        flush();

        UntypedResultSet rs = execute("SELECT * FROM %s WHERE id=?", 0);
        // this assert check is here to get the test to be green... if the format is fixed and this data loss is not
        // happening anymore, then this check should be updated
        assertThatThrownBy(() -> assertRows(rs, row(0, 0))).hasMessage("Got less rows than expected. Expected 1 but got 0");
    }
}
