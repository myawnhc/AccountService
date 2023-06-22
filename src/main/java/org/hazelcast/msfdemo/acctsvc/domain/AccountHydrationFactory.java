/*
 * Copyright 2023 Hazelcast, Inc
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

package org.hazelcast.msfdemo.acctsvc.domain;

import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.sql.SqlRow;
import org.hazelcast.eventsourcing.event.HydrationFactory;
import org.hazelcast.msfdemo.acctsvc.events.AccountCompactionEvent;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.BalanceChangeEvent;
import org.hazelcast.msfdemo.acctsvc.events.OpenAccountEvent;

import java.io.Serializable;

public class AccountHydrationFactory implements HydrationFactory<Account, String, AccountEvent>, Serializable {

    public static final String DO_NAME = "AccountService.account";
    private String mapping_template = "CREATE MAPPING IF NOT EXISTS \"?\" (\n" +
            // SourcedEvent fields
            // Key is PartitionedSequenceKey here -- will be cast as VARCHAR
            //  in query which may break if domain object key isn't a String
            //  External names are as declared in PartitionedSequenceKey.java
            "   doKey        OBJECT    EXTERNAL NAME \"__key.domainObjectKey\",\n" +
            "   sequence     BIGINT    EXTERNAL NAME \"__key.sequence\",\n" +
            "   eventName    VARCHAR,\n" +
            // AccountEvent fields
            "   customEvent  VARCHAR,\n" +
            "   eventTime    BIGINT,\n"    +
            "   accountName  VARCHAR,\n"    +
            "   balance      DECIMAL\n,"    +
            "   balanceChange DECIMAL\n"    +
            ")\n" +
            "TYPE IMap\n" +
            "OPTIONS (\n" +
            "  'keyFormat' = 'java',\n" +
            "  'keyJavaClass' = 'org.hazelcast.eventsourcing.event.PartitionedSequenceKey',\n" +
            "  'valueFormat' = 'compact',\n" +
            "  'valueCompactTypeName' = 'AccountService.AccountEvent'\n" +
            ")";

    public String getEventMapping(String eventStoreName) {
        // Default event store name is <doname>_ES, but it can be overridden so we
        // don't assume anything here.
        mapping_template = mapping_template.replaceAll("\\?", eventStoreName);
        return mapping_template;
    }

    @Override
    public Account hydrateDomainObject(GenericRecord data) {
        return new Account(data);
    }

    @Override
    public AccountEvent hydrateEvent(String eventName, SqlRow data) {
        switch (eventName) {
            case "AccountService.OpenAccountEvent":
                return new OpenAccountEvent(data);
            case "AccountService.BalanceChangeEvent":
                return new BalanceChangeEvent(data);
            case "AccountService.AccountCompactionEvent":
                return new AccountCompactionEvent(data);
            default:
                throw new IllegalArgumentException("bad eventName:" + eventName);

        }    }

    @Override
    public AccountEvent hydrateEvent(String eventName, GenericRecord data) {
        switch (eventName) {
            case "AccountService.OpenAccountEvent":
                return new OpenAccountEvent(data);
            case "AccountService.BalanceChangeEvent":
                return new BalanceChangeEvent(data);
            case "AccountService.AccountCompactionEvent":
                return new AccountCompactionEvent(data);
            default:
                throw new IllegalArgumentException("bad eventName:" + eventName);
        }
    }
}
