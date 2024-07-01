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

import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import com.hazelcast.sql.SqlRow;
import org.hazelcast.eventsourcing.eventstore.EventStoreCompactionEvent;
import org.hazelcast.msfdemo.acctsvc.domain.Account;

import java.io.Serializable;
import java.math.BigDecimal;

public class AccountCompactionEvent extends AccountEvent
        implements EventStoreCompactionEvent, Serializable {

    public static final String QUAL_EVENT_NAME = "AccountService.AccountCompactionEvent";
    public static final String ACCT_NUM = "key";
    public static final String ACCT_NAME = "accountName";
    public static final String BALANCE = "balance";

    //private String accountNumber;
    private String accountName;
    private BigDecimal balance;

    // Used to pass empty item into materialize that will get fields initialized from the
    // materialization process.  Considered experimental for now ...
    public AccountCompactionEvent() {
        setEventName(QUAL_EVENT_NAME);
    }

    public AccountCompactionEvent(String acctNumber, String acctName, BigDecimal balance) {
        setEventName(QUAL_EVENT_NAME);
        this.key = acctNumber;
        this.accountName = acctName;
        this.balance = balance;
    }

    public AccountCompactionEvent(GenericRecord data) {
        setEventName(QUAL_EVENT_NAME);
        this.key = data.getString(ACCT_NUM);
        this.accountName = data.getString(ACCT_NAME);
        this.balance = data.getDecimal(BALANCE);
    }

    public AccountCompactionEvent(SqlRow row) {
        setEventName(QUAL_EVENT_NAME);
        this.key = row.getObject("doKey");
        this.accountName = row.getObject(ACCT_NAME);
        this.balance = row.getObject(BALANCE);
    }

    @Override
    public GenericRecord apply(GenericRecord account) {
        BigDecimal previousBalance = account.getDecimal(Account.FIELD_BALANCE);
        if (previousBalance != null)
            this.balance = this.balance.add(previousBalance);
        return this.toGenericRecord();
    }

    @Override
    public void initFromGenericRecord(GenericRecord domainObject) {
        this.key = domainObject.getString(Account.FIELD_ACCT_NUM);
        this.accountName = domainObject.getString(Account.FIELD_ACCT_NAME);
        this.balance = domainObject.getDecimal(Account.FIELD_BALANCE);
    }

    @Override
    public String toString() {
        return "AccountCompactionEvent " + key;
    }

    @Override
    public GenericRecord toGenericRecord() {
        GenericRecord gr = GenericRecordBuilder.compact(getEventName())
                .setString(EVENT_NAME, QUAL_EVENT_NAME)
                .setString(ACCT_NUM, key)
                .setString(ACCT_NAME, accountName)
                .setDecimal(BALANCE, balance)
                .build();
        return gr;
    }
}
