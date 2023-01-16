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
import org.hazelcast.eventsourcing.eventstore.EventStoreCompactionEvent;
import org.hazelcast.msfdemo.acctsvc.domain.Account;

import java.io.Serializable;
import java.math.BigDecimal;

public class AccountCompactionEvent extends AccountEvent
        implements EventStoreCompactionEvent<Account>, Serializable {

    private String accountNumber;
    private String accountName;
    private BigDecimal balance;

    // Used to pass empty item into materialize that will get fields initialized from the
    // materialization process.  Considered experimental for now ...
    public AccountCompactionEvent() {
        this.eventClass = AccountCompactionEvent.class.getCanonicalName();
        JSONObject jobj = new JSONObject();
        setPayload(new HazelcastJsonValue(jobj.toString()));
    }

    public AccountCompactionEvent(String acctNumber, String acctName, BigDecimal balance) {
        this.key = acctNumber;
        this.eventClass = AccountCompactionEvent.class.getCanonicalName();
        JSONObject jobj = new JSONObject();
        jobj.put("accountName", acctName);
        jobj.put("balance", balance);
        setPayload(new HazelcastJsonValue(jobj.toString()));
    }

    public AccountCompactionEvent(SqlRow row) {
        this.accountNumber = row.getObject("key");
        this.key = accountNumber;
        HazelcastJsonValue payload = row.getObject("payload");
        JSONObject jobj = new JSONObject(payload.getValue());
        this.accountName = jobj.getString("accountName");
        this.balance = jobj.getBigDecimal("balance");
        setPayload(payload);
        eventClass = AccountCompactionEvent.class.getCanonicalName();
        setTimestamp(row.getObject("timestamp"));
    }

    @Override
    public Account apply(Account account) {
        account.setAcctNumber(accountNumber);
        account.setName(accountName);
        account.setBalance(balance);
        return account;
    }

    @Override
    public void initFromDomainObject(Account domainObject) {
        this.accountNumber = domainObject.getAcctNumber();
        this.key = accountNumber; // Required and easy to miss - maybe require on constructor instead?
        this.accountName = domainObject.getName();
        this.balance = domainObject.getBalance();
        JSONObject jobj = new JSONObject();
        jobj.put("accountName", accountName);
        jobj.put("balance", balance);
        setPayload(new HazelcastJsonValue(jobj.toString()));
    }

    @Override
    public String toString() {
        return "AccountCompactionEvent " + key;
    }
}
