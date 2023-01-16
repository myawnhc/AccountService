/*
 * Copyright 2022 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

package org.hazelcast.msfdemo.acctsvc.events;

import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import com.hazelcast.org.json.JSONObject;

import java.math.BigDecimal;

public class OpenAccountEventSerializer implements CompactSerializer<OpenAccountEvent> {
    @Override
    public OpenAccountEvent read(CompactReader compactReader) {
        String acctNumber = compactReader.readString("key");
        String eventClass = compactReader.readString("eventClass");
        long timestamp = compactReader.readInt64("timestamp");
        HazelcastJsonValue payload = compactReader.readCompact("payload");
        JSONObject jobj = new JSONObject(payload.getValue());
        String acctName = jobj.getString("accountName");
        BigDecimal initialBalance = jobj.getBigDecimal("initialBalance");
        OpenAccountEvent event = new OpenAccountEvent(acctNumber, acctName, initialBalance);
        event.setTimestamp(timestamp);
        event.setEventClass(eventClass);
        return event;
    }

    @Override
    public void write(CompactWriter compactWriter, OpenAccountEvent openAccountEvent) {
        compactWriter.writeString("key", openAccountEvent.getKey());
        compactWriter.writeString("eventClass", openAccountEvent.getEventClass());
        compactWriter.writeInt64("timestamp", openAccountEvent.getTimestamp());
        compactWriter.writeCompact("payload", openAccountEvent.getPayload());
    }

    @Override
    public String getTypeName() {
        return OpenAccountEvent.class.getCanonicalName();
    }

    @Override
    public Class<OpenAccountEvent> getCompactClass() {
        return OpenAccountEvent.class;
    }
}
