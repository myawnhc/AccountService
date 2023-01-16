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
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.flakeidgen.FlakeIdGenerator;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.ServiceFactories;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.org.json.JSONObject;
import org.example.grpc.GrpcConnector;
import org.example.grpc.MessageWithUUID;
import org.hazelcast.eventsourcing.EventSourcingController;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.BalanceChangeEvent;
import org.hazelcast.msfdemo.acctsvc.service.AccountService;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.UUID;

import static com.hazelcast.jet.datamodel.Tuple2.tuple2;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.AdjustBalanceRequest;
import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.AdjustBalanceResponse;

public class AdjustBalancePipeline implements Runnable {

    private static AccountService service;

    public AdjustBalancePipeline(AccountService service) {
        AdjustBalancePipeline.service = service;
        if (service == null)
            throw new IllegalArgumentException("Service cannot be null");
    }

    @Override
    public void run() {
        try {
            // Currently running embedded with all classes local -- so the upload of
            // jars is not being used; keeping code in place because eventually we'll deploy
            // this to the cloud and need to handle deployment of user classes.
            //MSFController controller = MSFController.getOrCreateInstance("AccountService", service.isEmbedded(), service.getClientConfig());
            File fw = new File("/ext/framework-1.0-SNAPSHOT.jar");
            URL framework = fw.toURI().toURL();
            File grpc = new File("/ext/account-proto-1.0-SNAPSHOT.jar");
            URL grpcdefs = grpc.toURI().toURL();
            File svc = new File("/application.jar");
            URL serviceJar = svc.toURI().toURL();
            //System.out.println(">>> Found files? " + fw.exists() + " " + grpc.exists() + " " + svc.exists());
            URL[] jobJars = new URL[] { framework, grpcdefs, serviceJar };
            Class[] jobClasses = new Class[] {}; // {AccountOuterClass.class };
            System.out.println("AdjustBalancePipeline.run() invoked, submitting job");
            HazelcastInstance hazelcast = service.getHazelcastInstance();
            JobConfig jobConfig = new JobConfig();
            jobConfig.setName("AccountService.AdjustBalance");
            // TODO: set jars
            hazelcast.getJet().newJob(createPipeline(), jobConfig);

        } catch (Exception e) { // Happens if our pipeline is not valid
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
                        (ctx) -> service.getEventSourcingController());

        events.mapUsingService(eventController, (controller, tuple) -> {
            controller.handleEvent(tuple.f1());
            return tuple;
        })

        // Send response back via GrpcSink
        .map(tuple -> {
            UUID uuid = tuple.f0();
            BalanceChangeEvent event = tuple.f1();
            //String acctNumber = event.getKey();
            HazelcastJsonValue payload = event.getPayload();
            JSONObject jobj = new JSONObject(payload.getValue());
            BigDecimal balanceChange = jobj.getBigDecimal("balanceChange");
            // Convert to cents as protobuf has no decimal type
            balanceChange = balanceChange.movePointRight(2);
            //String eventName = jobj.getString("eventName");
            AdjustBalanceResponse response = AdjustBalanceResponse.newBuilder()
                    .setNewBalance(balanceChange.intValue())
                    .build();
            MessageWithUUID<AdjustBalanceResponse> wrapped = new MessageWithUUID<>(uuid, response);
            return wrapped;
        })
        .writeTo(GrpcConnector.grpcUnarySink(SERVICE_NAME, METHOD_NAME))
                .setName("Write response to GrpcSink");

        return p;
    }
}
