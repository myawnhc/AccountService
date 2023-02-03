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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.YamlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.IMap;
import org.example.grpc.GrpcServer;
import org.hazelcast.eventsourcing.EventSourcingController;
import org.hazelcast.eventsourcing.event.DomainObject;
import org.hazelcast.eventsourcing.event.PartitionedSequenceKey;
import org.hazelcast.eventsourcing.event.SourcedEvent;
import org.hazelcast.eventsourcing.sync.CompletionInfo;
import org.hazelcast.eventsourcing.sync.CompletionInfoCompactSerializer;
import org.hazelcast.msfdemo.acctsvc.business.AccountAPIImpl;
import org.hazelcast.msfdemo.acctsvc.business.OpenAccountPipeline;
import org.hazelcast.msfdemo.acctsvc.business.AdjustBalancePipeline;
import org.hazelcast.msfdemo.acctsvc.configuration.ServiceConfig;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.events.AccountCompactionEventSerializer;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.BalanceChangeEventSerializer;
import org.hazelcast.msfdemo.acctsvc.events.OpenAccountEventSerializer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountService implements HazelcastInstanceAware {

    private HazelcastInstance hazelcast;
    private EventSourcingController<Account,String, AccountEvent> eventSourcingController;
    private boolean embedded;
    private byte[] clientConfig;

    private void initHazelcast(boolean isEmbedded, byte[] clientConfig) {
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
            config.getJetConfig().setResourceUploadEnabled(true);
            config.getMapConfig("account_PENDING").getEventJournalConfig().setEnabled(true);
            config.getSerializationConfig().getCompactSerializationConfig()
                    .addSerializer(new OpenAccountEventSerializer())
                    .addSerializer(new BalanceChangeEventSerializer());
            // NOTE: we may need additional configuration here!
            config = EventSourcingController.addRequiredConfigItems(config);
            hazelcast = Hazelcast.newHazelcastInstance(config);
        } else {
            InputStream is = new ByteArrayInputStream(clientConfig);
            ClientConfig config = new YamlClientConfigBuilder(is).build();

//            if (sslProperties != null) {
//                System.out.println("Setting SSL properties programmatically");
//                config.getNetworkConfig().getSSLConfig().setEnabled(true).setProperties(sslProperties);
//            }

            // Doing programmatically for now since YAML marked invalid
            config.getSerializationConfig().getCompactSerializationConfig()
                    .addSerializer(new AccountCompactionEventSerializer())
                    .addSerializer(new BalanceChangeEventSerializer())
                    .addSerializer(new OpenAccountEventSerializer())
                    .addSerializer(new CompletionInfoCompactSerializer());

            System.out.println("Adding classes needed outside of pipelines via UserCodeDeployment");
            config.getUserCodeDeploymentConfig().setEnabled(true)
                    .addClass(PartitionedSequenceKey.class)
                    .addClass(Account.class)
                    .addClass(DomainObject.class)
                    .addClass(SourcedEvent.class)
                    .addClass(CompletionInfo.class)
                    .addClass(CompletionInfo.Status.class);

            System.out.println("AccountService starting Hazelcast Platform client with config from classpath");
            hazelcast = HazelcastClient.newHazelcastClient(config);
            System.out.println(" Target cluster: " + hazelcast.getConfig().getClusterName());


//            // HZCE doesn't have GUI support for enabling Map Journal
//            enableMapJournal(serviceName);
//            // just for confirmation in the client logs, as executor output goes to server logs
//            serviceName = serviceName.replace("Service", "Event_*");
//            System.out.println("Enabled map journal for " + serviceName);

            // For client/server configs, make config info available to pipelines
            // so they can initialize a member-side AccountService object.  Map of values
            // may be overkill as initially we only have a single item to pass, but
            // allowing for future expansion.
            IMap<String, Map<String,Object>> configMap = hazelcast.getMap("ServiceConfig");
            Map<String,Object> serviceConfig = new HashMap<>();
            serviceConfig.put("clientConfig", clientConfig);
            configMap.put("AccountService", serviceConfig);
            System.out.println("AccountService config added to cluster ServiceConfig map");
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
        try {
            File esJar = new File("target/dependentJars/eventsourcing-1.0-SNAPSHOT.jar");
            URL es = esJar.toURI().toURL();
            File grpcJar = new File("target/dependentJars/grpc-connectors-1.0-SNAPSHOT.jar");
            URL grpc = grpcJar.toURI().toURL();
            File protoJar = new File("target/dependentJars/AccountProto-1.0-SNAPSHOT.jar");
            URL proto = protoJar.toURI().toURL();
            File acctsvcJar = new File("target/accountservice-1.0-SNAPSHOT.jar");
            URL acctsvc = acctsvcJar.toURI().toURL();
            List<URL> dependencies = new ArrayList<>();
            dependencies.add(es);
            //dependencies.add(grpc);
            //dependencies.add(proto);
            dependencies.add(acctsvc);

            eventSourcingController = EventSourcingController
                    .<Account,String, AccountEvent>newBuilder(hazelcast, "account")
                    .addDependencies(dependencies)
                    .build();

        } catch (MalformedURLException m) {
            m.printStackTrace();
        }

    }

    public EventSourcingController<Account,String, AccountEvent> getEventSourcingController() {
        return eventSourcingController;
    }


    private void initPipelines(HazelcastInstance hazelcast) {
        // Start the various Jet transaction handler pipelines
        ExecutorService executor = Executors.newCachedThreadPool();
        byte[] cc = isEmbedded() ? null : getClientConfig();
        try {
            File esJar = new File("target/dependentJars/eventsourcing-1.0-SNAPSHOT.jar");
            URL es = esJar.toURI().toURL();
            File grpcJar = new File("target/dependentJars/grpc-connectors-1.0-SNAPSHOT.jar");
            URL grpc = grpcJar.toURI().toURL();
            File protoJar = new File("target/dependentJars/AccountProto-1.0-SNAPSHOT.jar");
            URL proto = protoJar.toURI().toURL();
            File acctsvcJar = new File("target/accountservice-1.0-SNAPSHOT.jar");
            URL acctsvc = acctsvcJar.toURI().toURL();
            List<URL> dependencies = new ArrayList<>();
            dependencies.add(es);
            dependencies.add(grpc);
            dependencies.add(proto);
            dependencies.add(acctsvc);

            OpenAccountPipeline openPipeline = new OpenAccountPipeline(this, cc, dependencies);
            executor.submit(openPipeline);

        } catch (Throwable t) {
            t.printStackTrace();
        }


//        AdjustBalancePipeline adjPipeline = new AdjustBalancePipeline(this);
//        try {
//            executor.submit(adjPipeline);
//        } catch (Throwable t) {
//            t.printStackTrace();
//        }
    }

    public boolean isEmbedded() { return embedded; }
    public byte[] getClientConfig() { return clientConfig; }


    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    // called from pipeline
    public void initService(byte[] clientConfig) {
        System.out.println("initService " + clientConfig);
        initHazelcast(false, clientConfig);
        initEventSourcingController(hazelcast);

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

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
    }
}
