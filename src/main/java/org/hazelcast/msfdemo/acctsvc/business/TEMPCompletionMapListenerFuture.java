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

package org.hazelcast.msfdemo.acctsvc.business;

import com.hazelcast.config.Config;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.ServiceFactories;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.test.TestSources;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryUpdatedListener;
import org.hazelcast.eventsourcing.event.PartitionedSequenceKey;
import org.hazelcast.eventsourcing.sync.CompletionInfo;
import org.hazelcast.eventsourcing.sync.CompletionMapListenerFuture;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Once this works will relocate class to EventSourcing module so it can be
// easily leveraged by any callers

/** Adapts a EntryUpdatedListener&lt;T&gl; to a CompleteableFuture&lt;T&gt; */
public class TEMPCompletionMapListenerFuture<K,V> extends CompletableFuture<V>
                                           implements EntryUpdatedListener<K,V>,
                                                      Serializable {

    //private V value;

    @Override
    public void entryUpdated(EntryEvent<K, V> entryEvent) {
        //this.value = entryEvent.getValue();
        System.out.println("entryUpdated invoked, completing future");
        super.complete(entryEvent.getValue());
    }

    private static HazelcastInstance hazelcast;

    // May create a junit testLocally just for this to verify we can serialize it
    // into a pipeline and that the future completes as expected ....
    public void initHazelcast() {
        Config config = new Config();
        config.getJetConfig().setEnabled(true);
        hazelcast = Hazelcast.newHazelcastInstance(config);
    }

    // Test first without a pipeline
    private void testLocally() throws ExecutionException, InterruptedException {
        IMap<PartitionedSequenceKey<String>, CompletionInfo> map = hazelcast.getMap("testLocally");
        map.addEntryListener(this, true);
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.submit(new MyTask());
        System.out.println("Getting from future");
        V value = this.get();
        System.out.println("Future completed with value " + value);
    }

    /* This standalone runnable works; now need to put the same logic into a pipeline
     * and see if we can make the listener+future serialize properly.
     */
    static class MyTask implements Runnable {
        @Override
        public void run() {
            System.out.println("MyTask running");
            IMap<PartitionedSequenceKey<String>, CompletionInfo> map = hazelcast.getMap("testLocally");
            PartitionedSequenceKey<String> psk = new PartitionedSequenceKey<>(1, "key");
            CompletionInfo completionInfo = new CompletionInfo();
            System.out.println("Adding to map");
            map.put(psk, completionInfo); // Should not trigger listener
            System.out.println("Updating map");
            completionInfo.markComplete();
            map.put(psk, completionInfo); // Should trigger listener & copmplete future
            System.out.println("Mytask complete, exits");
        }
    }

    public Pipeline createPipeline() {
        // Items for pipeline input
        PartitionedSequenceKey<String> psk = new PartitionedSequenceKey<>(1, "key");

        ServiceFactory<?, IMap<PartitionedSequenceKey<String>, CompletionInfo>> completionMap =
                ServiceFactories.iMapService("testLocally");

        Pipeline p = Pipeline.create();

        BatchStage<PartitionedSequenceKey> batch = p.readFrom(TestSources.items(psk));
        batch.mapUsingService(completionMap, (map, key) -> {
            System.out.println("Pipeline: Putting initial completion info");
            // not using, just confirming serializability ...
            CompletionMapListenerFuture cmlf = new CompletionMapListenerFuture();
            map.put(key, new CompletionInfo());
            return key;
        }).mapUsingService(completionMap, (map, key) -> {
            System.out.println("Pipeline: Updating completion info");
            CompletionInfo info = map.get(key);
            info.markComplete();
            map.put(key, info);
            return info;
        }).writeTo(Sinks.logger());

        return p;
    }

    private void testViaPipeline() throws ExecutionException, InterruptedException {
        IMap<PartitionedSequenceKey<String>, CompletionInfo> map = hazelcast.getMap("testLocally");
        map.addEntryListener(this, true);

        System.out.println("Submitting job");
        Job j = hazelcast.getJet().newJob(createPipeline());

        System.out.println("Getting from future");
        V value = this.get();
        System.out.println("Future completed with value " + value);

    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        TEMPCompletionMapListenerFuture main = new TEMPCompletionMapListenerFuture();
        main.initHazelcast();
        //main.testLocally();
        main.testViaPipeline();
    }
}
