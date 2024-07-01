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
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.ServiceFactories;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import org.example.grpc.GrpcConnector;
import org.example.grpc.MessageWithUUID;
import org.hazelcast.eventsourcing.EventSourcingController;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.BalanceChangeEvent;
import org.hazelcast.msfdemo.acctsvc.service.AccountService;
import org.hazelcast.msfdemo.acctsvc.service.ServiceInitRequest;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static com.hazelcast.jet.datamodel.Tuple2.tuple2;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.AdjustBalanceRequest;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.AdjustBalanceResponse;

public class AdjustBalancePipeline implements Runnable {

    private static AccountService service;
    private List<URL> dependencies;
    private static final Logger logger = Logger.getLogger(AdjustBalancePipeline.class.getName());

    public AdjustBalancePipeline(AccountService service, byte[] clientConfig, List<URL> dependentJars) {
        AdjustBalancePipeline.service = service;
        if (service == null)
            throw new IllegalArgumentException("Service cannot be null");
        // When running in client/server mode, service won't be initialized yet
//        if (service.getEventSourcingController() == null && clientConfig != null) {
//            logger.info("*** ABPipeline inits service in constructor");
//            service.initService(clientConfig);
//        }
        this.dependencies = dependentJars;
    }

    @Override
    public void run() {
        try {
            //System.out.println("AdjustBalancePipeline.run");
            HazelcastInstance hazelcast = service.getHazelcastInstance();
            JobConfig jobConfig = new JobConfig();
            jobConfig.setName("AccountService.AdjustBalance");
            for (URL url : dependencies) {
                jobConfig.addJar(url);
                logger.info("AdjustBalancePipeline job config adding " + url);
            }
            hazelcast.getJet().newJob(createPipeline(), jobConfig);

        } catch (Exception e) { // Happens if our pipeline is not valid
            logger.info("Error on AdjustBalance submission");
            e.printStackTrace();
        }
    }

    private static Pipeline createPipeline() {
        Pipeline p = Pipeline.create();

        final String SERVICE_NAME = "account.Account";
        final String METHOD_NAME = "adjustBalance";

        StreamStage<MessageWithUUID<AdjustBalanceRequest>> requests =
                p.readFrom(GrpcConnector.<AdjustBalanceRequest>grpcUnarySource(SERVICE_NAME, METHOD_NAME))
                .withoutTimestamps()
                .setName("Read AdjustBalance requests from GrpcSource");

        // Create BalanceChangeEvent object
        StreamStage<Tuple2<UUID,BalanceChangeEvent>> events =
                requests.map(entry -> {
                    UUID uniqueRequestID = entry.getIdentifier();
                    AdjustBalanceRequest request = entry.getMessage();
                    BalanceChangeEvent event = new BalanceChangeEvent(
                            request.getAccountNumber(),
                            request.getEventName(),
                            BigDecimal.valueOf(request.getAmount(), 2));
                    Tuple2<UUID,BalanceChangeEvent> tuple = tuple2(uniqueRequestID, event);
                    return tuple;
                })
                .setName("Create BalanceChangeEvent");

        // EventSourcingController will add event to event store, update the in-memory
        // materialized view using an EntryProcessor, and publish the event to all subscribers
        ServiceFactory<?, EventSourcingController<Account,String, AccountEvent>> eventController =
                ServiceFactories.sharedService(
                        (ctx) -> {
                            if (service == null) {
                                System.out.println("AdjustBalancePipeline service factory needs to get or initialize service");
                                ServiceInitRequest initRequest = new ServiceInitRequest()
                                        .setHazelcastInstance(ctx.hazelcastInstance())
                                        .initEventSourcingController();
                                service = AccountService.getInstance(initRequest); // no clientconfig
                            }
                            return service.getEventSourcingController();
                        });

        events.mapUsingServiceAsync(eventController, (controller, tuple) -> {
            // Returns CompletableFuture<CompletionInfo>
            //System.out.println("AdjustBalancePipeline calls handleEvent with " + tuple.f0() + "," + tuple.f1());
            return controller.handleEvent(tuple.f1(), tuple.f0());
        }).setName("Send event to EventSourcingFramework async")

        // Send response back via GrpcSink
        .map(completion -> {
            UUID uuid = completion.getUUID();
            //System.out.println("AdjustBalancePipeline received completion for " + uuid);
            GenericRecord eventGR = completion.getEvent();
            BalanceChangeEvent event = new BalanceChangeEvent(eventGR);
            //String acctNumber = event.getKey();
            BigDecimal balanceChange = event.getBalanceChange();
            // Convert amount to cents as protobuf has no decimal type
            balanceChange = balanceChange.movePointRight(2);
            //String eventName = event.getCustomEventName();
            AdjustBalanceResponse response = AdjustBalanceResponse.newBuilder()
                    .setNewBalance(balanceChange.intValue())
                    .build();
            MessageWithUUID<AdjustBalanceResponse> wrapped = new MessageWithUUID<>(uuid, response);
            return wrapped;
        }).setName("Build AdjustBalanceResponse after return from ESF received")
        .writeTo(GrpcConnector.grpcUnarySink(SERVICE_NAME, METHOD_NAME))
                .setName("Write response to GrpcSink");

        return p;
    }
}
