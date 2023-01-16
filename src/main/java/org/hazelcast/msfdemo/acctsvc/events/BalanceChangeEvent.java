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
import com.hazelcast.org.json.JSONObject;
import com.hazelcast.sql.SqlRow;
import org.hazelcast.msfdemo.acctsvc.domain.Account;

import java.math.BigDecimal;

public class BalanceChangeEvent extends AccountEvent {

    public BalanceChangeEvent(String acctNumber, String eventName, BigDecimal change) {
        this.key = acctNumber;
        this.eventClass = BalanceChangeEvent.class.getCanonicalName();
        JSONObject jobj = new JSONObject();
        jobj.put("balanceChange", change);
        jobj.put("eventName", eventName);
        setPayload(new HazelcastJsonValue(jobj.toString()));
    }

    public BalanceChangeEvent(SqlRow row) {
        this.key = row.getObject("key");
        HazelcastJsonValue payload = row.getObject("payload");
        setPayload(payload);
        eventClass = BalanceChangeEvent.class.getCanonicalName();
        setTimestamp(row.getObject("timestamp"));
    }

    @Override
    public Account apply(Account account) {
        JSONObject jobj = new JSONObject(payload.getValue());
        account.setBalance(account.getBalance().add(jobj.getBigDecimal("balanceChange")));
        return account;
    }

    @Override
    public String toString() {
        JSONObject jobj = new JSONObject(getPayload().getValue());
        String eventName = jobj.getString("eventName");
        BigDecimal amount = jobj.getBigDecimal("balanceChange");
        return eventName + " " + key + " " + amount.toString();
    }
}
