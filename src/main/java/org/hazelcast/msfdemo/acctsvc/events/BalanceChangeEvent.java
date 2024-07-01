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

public class BalanceChangeEvent extends AccountEvent {

    public static final String QUAL_EVENT_NAME = "AccountService.BalanceChangeEvent";
//    public static final String ACCT_NUM = "key";
//    public static final String BALANCE_CHANGE = "balanceChange";
    public static final String CUSTOM_EVENT_NAME = "customEvent";

    private BigDecimal balanceChange;
    private String customEventName;

    public BalanceChangeEvent(String acctNumber, String eventName, BigDecimal change) {
        setEventName(QUAL_EVENT_NAME);
        this.key = acctNumber;
        this.customEventName = eventName;
        this.balanceChange = change;
    }

    public BalanceChangeEvent(GenericRecord data) {
        setEventName(QUAL_EVENT_NAME);
        this.key = data.getString(Account.FIELD_ACCT_NUM);
        this.customEventName = data.getString(CUSTOM_EVENT_NAME);
        this.balanceChange = data.getDecimal(Account.FIELD_BALANCE);
    }

    public BalanceChangeEvent(SqlRow row) {
        setEventName(QUAL_EVENT_NAME);
        this.key = row.getObject(Account.FIELD_ACCT_NUM);
        this.customEventName = row.getObject(CUSTOM_EVENT_NAME);
        this.balanceChange = row.getObject(Account.FIELD_BALANCE);
        Long time = row.getObject(EVENT_TIME);
        if (time != null)
            setTimestamp(time);
    }

    @Override
    public GenericRecord apply(GenericRecord account) {
        BigDecimal newBalance = account.getDecimal(Account.FIELD_BALANCE).add(balanceChange);
        account = account.newBuilderWithClone().setDecimal(Account.FIELD_BALANCE, newBalance).build();
        return account;
    }

    public BigDecimal getBalanceChange() { return balanceChange; }
    public String getCustomEventName() { return customEventName; }

    @Override
    public String toString() {
        return customEventName + " " + key + " " + balanceChange;
    }

    @Override
    public GenericRecord toGenericRecord() {
        GenericRecord gr = GenericRecordBuilder.compact(getEventName())
                .setString(EVENT_NAME, QUAL_EVENT_NAME)
                .setString(Account.FIELD_ACCT_NUM, key)
                .setString(CUSTOM_EVENT_NAME, customEventName)
                .setDecimal(Account.FIELD_BALANCE, balanceChange)
                .build();
        return gr;
    }
}
