package org.hazelcast.msfdemo.acctsvc.service;

import com.hazelcast.core.HazelcastInstance;

public class ServiceInitRequest {

    public enum HazelcastInitType { EMBEDDED, CLIENT_SERVER, VIRIDIAN, NONE }
    public HazelcastInitType initHazelcast = HazelcastInitType.NONE;
    public boolean initEventSourcingController = false;
    public boolean initAPIEndpoint = false;
    public boolean initPipelines = false;
    public boolean useExistingHazelcastInstance = false;
    public byte[] clientConfig;
    private HazelcastInstance hazelcast;

    public ServiceInitRequest initHazelcast(HazelcastInitType mode) {
        initHazelcast = mode;
        return this;
    }

    public ServiceInitRequest setClientConfig(byte[] clientConfig) {
        this.clientConfig = clientConfig;
        return this;
    }

    public ServiceInitRequest initEventSourcingController() {
        initEventSourcingController = true;
        return this;
    }

    public ServiceInitRequest initAPIEndpoint() {
        initAPIEndpoint = true;
        return this;
    }

    public ServiceInitRequest initPipelines() {
        initPipelines = true;
        return this;
    }

    public ServiceInitRequest setHazelcastInstance(HazelcastInstance hz) {
        useExistingHazelcastInstance = true;
        hazelcast = hz;
        return this;
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }
}
