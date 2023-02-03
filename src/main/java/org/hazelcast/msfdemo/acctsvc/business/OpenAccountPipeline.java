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
import com.hazelcast.flakeidgen.FlakeIdGenerator;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.ServiceFactories;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.map.IMap;
import org.example.grpc.GrpcConnector;

import org.example.grpc.MessageWithUUID;
import org.hazelcast.eventsourcing.EventSourcingController;
import org.hazelcast.eventsourcing.event.PartitionedSequenceKey;
import org.hazelcast.eventsourcing.sync.CompletionInfo;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.OpenAccountEvent;
import org.hazelcast.msfdemo.acctsvc.service.AccountService;

import static com.hazelcast.jet.datamodel.Tuple2.tuple2;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.OpenAccountRequest;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.OpenAccountResponse;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OpenAccountPipeline implements Runnable {

    private static AccountService service;
    private List<URL> dependencies;

    public OpenAccountPipeline(AccountService service, byte[] clientConfig, List<URL> dependentJars) {
        System.out.println("OPA.<init> with cc " + clientConfig);
        OpenAccountPipeline.service = service;
        if (service == null)
            throw new IllegalArgumentException("Service cannot be null");
        // When running in client/server mode, service won't be initialized yet
        if (service.getEventSourcingController() == null && clientConfig != null) {
            System.out.println("member/pipeline service init");
            service.initService(clientConfig);
        }
        this.dependencies = dependentJars;

    }

    @Override
    public void run() {
        try {
            System.out.println("OpenAccountPipeline.run() invoked, submitting job");
            HazelcastInstance hazelcast = service.getHazelcastInstance();
            JobConfig jobConfig = new JobConfig();
            jobConfig.setName("AccountService.OpenAccount");
            for (URL url : dependencies)
                jobConfig.addJar(url);
            hazelcast.getJet().newJob(createPipeline(), jobConfig);

        } catch (Exception e) { // Happens if our pipeline is not valid
            e.printStackTrace();
        }
    }

    private static Pipeline createPipeline() {
        Pipeline p = Pipeline.create();

        final String SERVICE_NAME = "account.Account";
        final String METHOD_NAME = "open";
        //AccountService service = OpenAccountPipeline.service;

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
                                System.out.println("**** ESC service factory needs to initialize service");
                                Map<String,Object> configMap = (Map<String, Object>) ctx.hazelcastInstance().getMap("ServiceConfig").get("AccountService");
                                byte[] clientConfig = (byte[]) configMap.get("clientConfig");
                                service = new AccountService();
                                service.initService(clientConfig);
                                System.out.println("service initialized from configmap");
                            }
                            return service.getEventSourcingController();
                        });


        events.mapUsingService(eventController, (controller, tuple) -> {
            controller.handleEvent(tuple.f1());
            // TODO: should wait for entry in completionsMap corresponding to eventKey
            //  to be updated by the handleEvent pipeline ...
            return tuple;
        })

        // Send response back via GrpcSink
        .map(tuple -> {
            UUID uuid = tuple.f0();
            OpenAccountEvent event = tuple.f1();
            String acctNumber = event.getKey();
            OpenAccountResponse response = OpenAccountResponse.newBuilder()
                    .setAccountNumber(acctNumber)
                    .build();
            MessageWithUUID<OpenAccountResponse> wrapped = new MessageWithUUID<>(uuid, response);
            return wrapped;
        })
        .writeTo(GrpcConnector.grpcUnarySink(SERVICE_NAME, METHOD_NAME))
                .setName("Write response to GrpcSink");

        return p;
    }
}
