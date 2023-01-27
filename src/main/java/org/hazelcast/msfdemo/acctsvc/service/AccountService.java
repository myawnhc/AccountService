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

package org.hazelcast.msfdemo.acctsvc.service;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.example.grpc.GrpcServer;
import org.hazelcast.eventsourcing.EventSourcingController;
import org.hazelcast.msfdemo.acctsvc.business.AccountAPIImpl;
import org.hazelcast.msfdemo.acctsvc.business.OpenAccountPipeline;
import org.hazelcast.msfdemo.acctsvc.business.AdjustBalancePipeline;
import org.hazelcast.msfdemo.acctsvc.configuration.ServiceConfig;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.BalanceChangeEventSerializer;
import org.hazelcast.msfdemo.acctsvc.events.OpenAccountEventSerializer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountService {

    private HazelcastInstance hazelcast;
    private EventSourcingController<Account,String, AccountEvent> eventSourcingController;
    private boolean embedded;
    private byte[] clientConfig;

    public void initHazelcast(boolean isEmbedded, byte[] clientConfig) {
        this.embedded = isEmbedded;
        this.clientConfig = clientConfig;
        if (!embedded && clientConfig == null) {
            throw new IllegalArgumentException("ClientConfig cannot be null for client-server deployment");
        }
        if (embedded) {
            Config config = new Config();
            config.setClusterName("acctsvc");
            config.getNetworkConfig().setPort(5701);
            config.getJetConfig().setEnabled(true);
            config.getMapConfig("account_PENDING").getEventJournalConfig().setEnabled(true);
            config.getSerializationConfig().getCompactSerializationConfig()
                    .addSerializer(new OpenAccountEventSerializer())
                    .addSerializer(new BalanceChangeEventSerializer());
            // NOTE: we may need additional configuration here!
            config = EventSourcingController.addRequiredConfigItems(config);
            hazelcast = Hazelcast.newHazelcastInstance(config);
        } else {
            // see MSF project for getting input stream of clientconfig byte[] and using it
            // to initialize the client config
            throw new IllegalArgumentException("Not set up to handle client-server yet!");
        }

        // Needed for cloud deployment - disabling for now
//        ClassLoader classLoader = AccountService.class.getClassLoader();
//        Properties props = null;
//        URL keystorePath = classLoader.getResource("client.keystore");
//        if (keystorePath != null) {
//            props = new Properties();
//            System.out.println(" KeyStore Resource path: " + keystorePath);
//            props.setProperty("javax.net.ssl.keyStore", "client.keystore");
//            System.out.println("WARNING: TODO: hardcoded keystore password, should read from service.yaml");
//            props.setProperty("javax.net.ssl.keyStorePassword", "2ec95573367");
//        } else System.out.println(" null keystorePath");
//        URL truststorePath = classLoader.getResource("client.truststore");
//        if (truststorePath != null) {
//            if (props == null) props = new Properties();
//            System.out.println(" Truststore Resource path: " + truststorePath);
//            props.setProperty("javax.net.ssl.trustStore", "client.truststore");
//            props.setProperty("javax.net.ssl.trustStorePassword", "2ec95573367");
//        } else System.out.println(" null truststorePath");

    }

    private void initEventSourcingController(HazelcastInstance hazelcast) {
        eventSourcingController = EventSourcingController.<Account,String, AccountEvent>newBuilder(hazelcast, "account")
                .build();
    }

    public EventSourcingController<Account,String, AccountEvent> getEventSourcingController() {
        return eventSourcingController;
    }

    private void initPipelines(HazelcastInstance hazelcast) {
        // Start the various Jet transaction handler pipelines
        ExecutorService executor = Executors.newCachedThreadPool();
        OpenAccountPipeline openPipeline = new OpenAccountPipeline(this);
        try {
            executor.submit(openPipeline);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        AdjustBalancePipeline adjPipeline = new AdjustBalancePipeline(this);
        try {
            executor.submit(adjPipeline);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public boolean isEmbedded() { return embedded; }
    public byte[] getClientConfig() { return clientConfig; }


    public void shutdown() {
        // notify Hazelcast controller, it can shut down if no other
        // services are still running.
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ServiceConfig.ServiceProperties props = ServiceConfig.get("account-service");
        AccountService acctService = new AccountService();
        acctService.initHazelcast(props.isEmbedded(), props.getClientConfig());

        // Need service initialized before pipelines (APIBufferPairs)
        AccountAPIImpl serviceImpl = new AccountAPIImpl(acctService.getHazelcastInstance());
        //acctService.initEventStore(acctService.getHazelcastInstance());
        acctService.initEventSourcingController(acctService.getHazelcastInstance());
        acctService.initPipelines(acctService.getHazelcastInstance());

        final GrpcServer server = new GrpcServer(serviceImpl, props.getGrpcPort());
        server.blockUntilShutdown();
    }
}
