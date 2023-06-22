/*
 * Copyright 2018-2022 Hazelcast, Inc
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
 */

package org.hazelcast.msfdemo.acctsvc.domain;

import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import org.hazelcast.eventsourcing.event.DomainObject;

import java.math.BigDecimal;

public class Account implements DomainObject<String> {

    private BigDecimal balance;
    private String accountNumber;
    private String accountName;

    public Account() {}

    public Account(GenericRecord fromGR) {
        this.accountNumber = fromGR.getString("key");
        this.accountName = fromGR.getString("accountName");
        this.balance = fromGR.getDecimal("balance");
    }

    @Override
    public String getKey() {
        return accountNumber;
    }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal value) { balance = value; }

    public void setAccountName(String name) { this.accountName = name; }
    public String getAccountName() { return accountName; }

    public void setAccountNumber(String acctNum) { this.accountNumber = acctNum; }
    public String getAccountNumber() { return this.accountNumber; }

    public GenericRecord toGenericRecord() {
        GenericRecord gr = GenericRecordBuilder.compact("AccountService.account")
                .setString("key", accountNumber)
                .setString("accountName", accountName)
                .setDecimal("balance", balance)
                .build();
        return gr;
    }

}
