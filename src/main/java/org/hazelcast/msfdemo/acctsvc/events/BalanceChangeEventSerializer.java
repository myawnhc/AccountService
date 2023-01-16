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

public class BalanceChangeEventSerializer implements CompactSerializer<BalanceChangeEvent> {
    @Override
    public BalanceChangeEvent read(CompactReader compactReader) {
        String acctNumber = compactReader.readString("key");
        String eventClass = compactReader.readString("eventClass");
        long timestamp = compactReader.readInt64("timestamp");
        HazelcastJsonValue payload = compactReader.readCompact("payload");
        JSONObject jobj = new JSONObject(payload.getValue());
        BigDecimal balanceChange = jobj.getBigDecimal("balanceChange");
        String eventName = jobj.getString("eventName");
        BalanceChangeEvent event = new BalanceChangeEvent(acctNumber, eventName, balanceChange);
        event.setTimestamp(timestamp);
        event.setEventClass(eventClass);
        return event;
    }

    @Override
    public void write(CompactWriter compactWriter, BalanceChangeEvent balanceChangeEvent) {
        compactWriter.writeString("key", balanceChangeEvent.getKey());
        compactWriter.writeString("eventClass", balanceChangeEvent.getEventClass());
        compactWriter.writeInt64("timestamp", balanceChangeEvent.getTimestamp());
        compactWriter.writeCompact("payload", balanceChangeEvent.getPayload());
    }

    @Override
    public String getTypeName() {
        return BalanceChangeEvent.class.getCanonicalName();
    }

    @Override
    public Class<BalanceChangeEvent> getCompactClass() {
        return BalanceChangeEvent.class;
    }
}
