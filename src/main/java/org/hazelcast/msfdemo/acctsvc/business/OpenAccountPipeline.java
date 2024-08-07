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
package org.hazelcast.msfdemo.acctsvc.business;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.ISemaphore;
import com.hazelcast.flakeidgen.FlakeIdGenerator;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.ServiceFactories;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.jet.pipeline.StreamStage;
import org.example.grpc.GrpcConnector;
import org.example.grpc.MessageWithUUID;
import org.hazelcast.eventsourcing.EventSourcingController;
import org.hazelcast.eventsourcing.sync.CompletionInfo;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.OpenAccountEvent;
import org.hazelcast.msfdemo.acctsvc.service.AccountService;
import org.hazelcast.msfdemo.acctsvc.service.ServiceInitRequest;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static com.hazelcast.jet.datamodel.Tuple2.tuple2;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.OpenAccountRequest;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.OpenAccountResponse;

public class OpenAccountPipeline implements Runnable {

    private static AccountService service;
    private List<URL> dependencies;
    private static final Logger logger = Logger.getLogger(OpenAccountPipeline.class.getName());

    public OpenAccountPipeline(AccountService service, byte[] clientConfig, List<URL> dependentJars) {
        OpenAccountPipeline.service = service;
        if (service == null)
            throw new IllegalArgumentException("Service cannot be null");
        this.dependencies = dependentJars;
    }

    @Override
    public void run() {
        try {
            HazelcastInstance hazelcast = service.getHazelcastInstance();
            JobConfig jobConfig = new JobConfig();
            jobConfig.setName("AccountService.OpenAccount");
            for (URL url : dependencies) {
                jobConfig.addJar(url);
                logger.info("OpenAccountPipeline job config adding " + url);
            }
            hazelcast.getJet().newJob(createPipeline(), jobConfig);
        } catch (Exception e) { // Happens if our pipeline is not valid
            e.printStackTrace();
        }
    }

    private static Pipeline createPipeline() {
        Pipeline p = Pipeline.create();

        final String SERVICE_NAME = "account.Account";
        final String METHOD_NAME = "open";

        StreamStage<MessageWithUUID<OpenAccountRequest>> requests =
                p.readFrom(GrpcConnector.<OpenAccountRequest>grpcUnarySource(SERVICE_NAME, METHOD_NAME))
                .withoutTimestamps()
                .setName("Read OpenAccount requests from GrpcSource");

        // Flake ID Generator is used to generated account numbers for newly opened accounts
        ServiceFactory<?, FlakeIdGenerator> sequenceGeneratorServiceFactory =
                ServiceFactories.sharedService(
                        (ctx) -> {
                            HazelcastInstance hz = ctx.hazelcastInstance();
                            return hz.getFlakeIdGenerator("accountNumber");
                        }
                );

        // Create AccountEvent object and assign generated account number
        StreamStage<Tuple2<UUID,OpenAccountEvent>> events =
                requests.mapUsingService(sequenceGeneratorServiceFactory, (seqGen, entry) -> {
                    UUID uniqueRequestID = entry.getIdentifier();
                    OpenAccountRequest request = entry.getMessage();
                    long acctNumber = seqGen.newId();
                    OpenAccountEvent event = new OpenAccountEvent(
                            ""+acctNumber,
                            request.getAccountName(),
                            BigDecimal.valueOf(request.getInitialBalance(), 2));
                    Tuple2<UUID,OpenAccountEvent> item = tuple2(uniqueRequestID, event);
                    return item;
                })
                .setName("Generate Account # and create AccountEvent.OPEN");

        // EventSourcingController will add event to event store, update the in-memory
        // materialized view, and publish the event to all subscribers
        ServiceFactory<?, EventSourcingController<Account,String, AccountEvent>> eventController =
                ServiceFactories.sharedService(
                        // In C/S config, service is uninitialized!
                        (ctx) -> {
                            if (service == null) {
                                logger.info("OpenAccountPipeline service factory needs to get or initialize service");
                                Map<String,Object> configMap = (Map<String, Object>) ctx.hazelcastInstance().getMap("ServiceConfig").get("AccountService");
                                //byte[] clientConfig = (byte[]) configMap.get("clientConfig");
                                ServiceInitRequest initRequest = new ServiceInitRequest()
                                        .setHazelcastInstance(ctx.hazelcastInstance())
                                        .initEventSourcingController();
                                service = AccountService.getInstance(initRequest);
                            }
                            return service.getEventSourcingController();
                        });

        events.mapUsingServiceAsync(eventController, (controller, tuple) -> {
            CompletableFuture<CompletionInfo> completion = controller.handleEvent(tuple.f1(), tuple.f0());
            //System.out.println("OpenAccountPipeline awaiting " + completion);
            return completion;
        }).setName("Invoke EventSourcingController.handleEvent")

        // Send response back via GrpcSink
        .map(completion -> {
            UUID uuid = completion.getUUID();
            //OpenAccountEvent event = (OpenAccountEvent) completion.getEvent();
            String acctNumber = (String) completion.getEventKey();
            OpenAccountResponse response = OpenAccountResponse.newBuilder()
                    .setAccountNumber(acctNumber)
                    .build();
            MessageWithUUID<OpenAccountResponse> wrapped = new MessageWithUUID<>(uuid, response);
            return wrapped;
        }).setName("Build OpenAccountResponse")
        .writeTo(GrpcConnector.grpcUnarySink(SERVICE_NAME, METHOD_NAME))
                .setName("Write response to GrpcSink");

        return p;
    }
}
