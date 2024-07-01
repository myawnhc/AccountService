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
import com.hazelcast.cp.ISemaphore;
import com.hazelcast.map.IMap;
import org.hazelcast.eventsourcing.EventSourcingController;
import org.hazelcast.msfdemo.acctsvc.business.AccountAPIImpl;
import org.hazelcast.msfdemo.acctsvc.business.OpenAccountPipeline;
import org.hazelcast.msfdemo.acctsvc.business.AdjustBalancePipeline;
import org.hazelcast.msfdemo.acctsvc.configuration.ServiceConfig;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.domain.AccountHydrationFactory;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;

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
import java.util.logging.Logger;

public class AccountService implements HazelcastInstanceAware {

    public static final String PIPELINE_SYNC_SEM = "AcccountService.PipelineInit.Semaphore";
    private HazelcastInstance hazelcast;
    private EventSourcingController<Account,String, AccountEvent> eventSourcingController;
    private boolean embedded;
    private byte[] clientConfig;

    private static final Logger logger = Logger.getLogger(AccountService.class.getName());

    private static AccountService theService;
    private static AccountAPIImpl theAPIEndpoint;

    private AccountService() {}

    /* Note that despite efforts to make AccountService a singleton, the Jet classloader
       architecture is causing us to get a service per pipeline, as each pipeline has its
       own classloader.  With refactored completion tracking this is no longer causing an
       issue but we might revisit - perhaps we can force service into root classloader?
     */
    public static AccountService getInstance(ServiceInitRequest request) {
        logger.info("getInstance entry by thread " + Thread.currentThread().getId());
        if (theService == null) {
            logger.info("AccountService.getInstance initializing new AccountService instance");
            if (request == null)
                throw new IllegalArgumentException("ServiceInitRequest cannot be null when instance being created");
            theService = new AccountService();
            if (request.initHazelcast == ServiceInitRequest.HazelcastInitType.CLIENT_SERVER) {
                byte[] clientConfig = request.clientConfig;
                theService.initHazelcast(false, clientConfig);
            } else if (request.initHazelcast == ServiceInitRequest.HazelcastInitType.EMBEDDED)
                theService.initHazelcast(true, null);
            else if (request.useExistingHazelcastInstance)
                theService.hazelcast = request.getHazelcastInstance();
            if (request.initEventSourcingController)
                theService.initEventSourcingController(theService.hazelcast);
            if (request.initAPIEndpoint) {
                theAPIEndpoint = new AccountAPIImpl(theService.hazelcast);
                logger.info("AccountService.getInstance has initialized theAPIEndpoint");
            }
            if (request.initPipelines)
                theService.initPipelines(theService.getHazelcastInstance());
        } else {
            logger.info("AccountService.getInstance returning existing AccountService instance");
        }

        return theService;
    }

    // When in client/server mode. pipelines need access to service object, and we need
    // to ensure there is only one cluster-wide.

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
            hazelcast = Hazelcast.newHazelcastInstance(config);
        } else {
            System.out.println("AccountService.initHazelcast for client/server");
            InputStream is = new ByteArrayInputStream(clientConfig);
            //System.out.println("ClientConfig as inputstream " + is);
            ClientConfig config = new YamlClientConfigBuilder(is).build();
            //System.out.println("ClientConfig as object " + config);

//            if (sslProperties != null) {
//                System.out.println("Setting SSL properties programmatically");
//                config.getNetworkConfig().getSSLConfig().setEnabled(true).setProperties(sslProperties);
//            }

            System.out.println("AccountService starting Hazelcast Platform client with config from classpath");
            hazelcast = HazelcastClient.newHazelcastClient(config);
            System.out.println(" Target cluster: " + hazelcast.getConfig().getClusterName());

            // Just checking ...
//            ConfirmKeyClassVisibility test = new ConfirmKeyClassVisibility();
//            IExecutorService ies = hazelcast.getExecutorService("test");
//            ies.submit(test);
//            System.out.println(" Sent class visibility checker to cluster");

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
//            File grpcJar = new File("target/dependentJars/grpc-connectors-1.0-SNAPSHOT.jar");
//            URL grpc = grpcJar.toURI().toURL();
//            File protoJar = new File("target/dependentJars/AccountProto-1.0-SNAPSHOT.jar");
//            URL proto = protoJar.toURI().toURL();
            File acctsvcJar = new File("target/accountservice-1.0-SNAPSHOT.jar");
            URL acctsvc = acctsvcJar.toURI().toURL();
            List<URL> dependencies = new ArrayList<>();
            //dependencies.add(es);
            //dependencies.add(grpc);
            //dependencies.add(proto);
            dependencies.add(acctsvc);

            eventSourcingController = EventSourcingController
                    .<Account,String, AccountEvent>newBuilder(hazelcast, "account")
                    .addDependencies(dependencies)
                    .hydrationFactory(new AccountHydrationFactory())
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
//            dependencies.add(es);
//            dependencies.add(grpc);
            dependencies.add(proto);
            dependencies.add(acctsvc);

            logger.info("Begin pipeline client-side init");
            ISemaphore semaphore = hazelcast.getCPSubsystem().getSemaphore(AccountService.PIPELINE_SYNC_SEM);
            semaphore.init(1);

            OpenAccountPipeline openPipeline = new OpenAccountPipeline(this, cc, dependencies);
            executor.submit(openPipeline);

            AdjustBalancePipeline adjPipeline = new AdjustBalancePipeline(this, cc, dependencies);
            executor.submit(adjPipeline);
            logger.info("End pipeline client-side init");

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public boolean isEmbedded() { return embedded; }
    public byte[] getClientConfig() { return clientConfig; }


    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    // previously called from pipeline; now unused
    public void initService(byte[] clientConfig) {
        System.out.println("initService " + clientConfig);
        initHazelcast(false, clientConfig);
        initEventSourcingController(hazelcast);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ServiceConfig.ServiceProperties props = ServiceConfig.get("account-service");

        ServiceInitRequest.HazelcastInitType mode = ServiceInitRequest.HazelcastInitType.CLIENT_SERVER;
        if (props.isEmbedded())
            mode = ServiceInitRequest.HazelcastInitType.EMBEDDED;

        ServiceInitRequest initRequest = new ServiceInitRequest()
                .setClientConfig(props.getClientConfig())
                .initHazelcast(mode)
                .initEventSourcingController()
                .initAPIEndpoint()
                .initPipelines();

        // getInstance initializes static theService & theAPIEndpoint members
        AccountService unused = getInstance(initRequest);
        logger.info("initialized AccountService " + unused);

        final GrpcServer server = new GrpcServer(theAPIEndpoint, props.getGrpcPort());
        server.blockUntilShutdown();
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
    }
}
