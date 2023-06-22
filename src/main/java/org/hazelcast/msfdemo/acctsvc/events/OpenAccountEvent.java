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
import org.hazelcast.msfdemo.acctsvc.domain.Account;

import java.math.BigDecimal;

public class OpenAccountEvent extends AccountEvent {

    public static final String QUAL_EVENT_NAME = "AccountService.OpenAccountEvent";
    public static final String ACCT_NUM = "key";
    public static final String ACCT_NAME = "accountName";
    public static final String INITIAL_BALANCE = "balance";

    private String accountName;
    private BigDecimal initialBalance;

    public OpenAccountEvent(String acctNumber, String acctName, BigDecimal initialBalance) {
        setEventName(QUAL_EVENT_NAME);
        this.key = acctNumber;
        this.accountName = acctName;
        this.initialBalance = initialBalance;
    }

    // Used in pipelines when getting events from PendingEvents or updating the
    // materialized view
    public OpenAccountEvent(GenericRecord data) {
        setEventName(QUAL_EVENT_NAME);
        this.key = data.getString(ACCT_NUM);
        this.accountName = data.getString(ACCT_NAME);
        this.initialBalance = data.getDecimal(INITIAL_BALANCE);
    }

    // Reconstruct an event from its SQL stored format
    public OpenAccountEvent(SqlRow row) {
        this.key = row.getObject("doKey");
        this.eventName = QUAL_EVENT_NAME;
        this.accountName = row.getObject(ACCT_NAME);
        this.initialBalance = row.getObject(INITIAL_BALANCE);
        Long time = row.getObject(EVENT_TIME);
        if (time != null)
            setTimestamp(time);
    }

    @Override
    public Account apply(Account account) {
        // When called from pipeline we will be passed null as there is no
        // entry for the account found when doing initial lookup
        if (account == null)
            account = new Account();
        account.setAccountNumber(key);
        account.setAccountName(accountName);
        account.setBalance(initialBalance);
        return account;
    }

    @Override
    public String toString() {
        return "OpenAccountEvent " + key;
    }

    @Override
    public GenericRecord toGenericRecord() {
        GenericRecord gr = GenericRecordBuilder.compact(getEventName())
                .setString(EVENT_NAME, QUAL_EVENT_NAME)
                .setString(ACCT_NUM, key)
                .setString(ACCT_NAME, accountName)
                .setDecimal(INITIAL_BALANCE, initialBalance)
                .build();
        return gr;
    }
}
