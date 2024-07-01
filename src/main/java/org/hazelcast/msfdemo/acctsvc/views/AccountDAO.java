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
package org.hazelcast.msfdemo.acctsvc.views;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.SqlRow;
import com.hazelcast.sql.SqlService;
import com.hazelcast.sql.SqlStatement;
import org.hazelcast.msfdemo.acctsvc.domain.Account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;

public class AccountDAO {

    private HazelcastInstance hazelcast;
    private IMap<String, GenericRecord> accountMap;

    private final String CREATE_MAPPING  =
        """
        CREATE MAPPING IF NOT EXISTS account_VIEW (
            accountName   VARCHAR,
            key           VARCHAR,
            balance       DECIMAL  
        )
        TYPE IMap
        Options (
            'keyFormat' = 'java',
            'keyJavaClass' = 'java.lang.String',
            'valueFormat' = 'compact',
            'valueCompactTypeName' = 'AccountService.account'
        )
        """;

    public AccountDAO(HazelcastInstance hz) {
        //super(controller, "account");
        this.hazelcast = hz;
        accountMap = hazelcast.getMap("account_VIEW");
        // Fails in Docker environment
        //hazelcast.getSql().execute(CREATE_MAPPING);
    }

    // Non-inheritable query methods
    public Account findByKey(String accountNumber) {
        GenericRecord data = accountMap.get(accountNumber);
        return new Account(data);
    }

    public Collection<Account> getAllAccounts() {
        //return accountMap.values();
        Collection<Account> accounts = new ArrayList<>();
        Collection<GenericRecord> grs = accountMap.values();
        for (GenericRecord gr : grs) {
            Account a = new Account(gr);
            accounts.add(a);
        }
        return accounts;
    }

    public BigDecimal getTotalAccountBalances() {
        // Old way:
        //BigDecimal v1 = accountMap.aggregate(Aggregators.bigDecimalSum("balance"));

        long start = System.currentTimeMillis();
        SqlService sql = hazelcast.getSql();
        SqlResult result = sql.execute(new SqlStatement("select sum(balance) from account_VIEW"));
        BigDecimal value = BigDecimal.ZERO;
        for (SqlRow row : result) {
            value = row.getObject(0);
        }
        System.out.println(System.currentTimeMillis() - start + "ms to aggregate account balances with SQL");
        return value;
    }
}
