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
import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.map.IMap;
import io.grpc.stub.StreamObserver;
import org.example.grpc.APIBufferPair;
import org.example.grpc.Arity;
import org.hazelcast.eventsourcing.event.PartitionedSequenceKey;
import org.hazelcast.eventsourcing.sync.CompletionInfo;
import org.hazelcast.msfdemo.acctsvc.domain.Account;
import org.hazelcast.msfdemo.acctsvc.events.AccountEvent;
import org.hazelcast.msfdemo.acctsvc.events.AccountGrpc;
import org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass;
import org.hazelcast.msfdemo.acctsvc.views.AccountDAO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import static org.hazelcast.msfdemo.acctsvc.events.AccountOuterClass.*;

/** Server-side implementation of the AccountService API
 *  Takes requests and puts them to API-specific IMaps that trigger Jet pipelines
 *  Looks for result in corresponding result map to return to client
 */
public class AccountAPIImpl extends AccountGrpc.AccountImplBase {

    private static final Logger logger = Logger.getLogger(AccountAPIImpl.class.getName());
    private AccountDAO accountDAO;
    private final IMap<String, APIBufferPair> bufferPairsForAPI;
    APIBufferPair<OpenAccountRequest,OpenAccountResponse> openHandler;
    APIBufferPair<AdjustBalanceRequest,AdjustBalanceResponse> balanceAdjustHandler;

    // Tracking for stuck operations.  Not currently in use but keeping around
    //  in case it becomes useful (again) in the future.
    private static final boolean TRACK_PENDING_OPS = false;
    private static final int THRESHOLD_WARN = 10_000; // ms
    private static final int THRESHOLD_FAIL = 60_000; // ms
    class PendingOperation {
        public String eventName;
        public long timestamp;
        public PendingOperation(String eventName) {
            this.eventName = eventName;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private Map<UUID, PendingOperation> pendingOperationMap = new HashMap<>();


    public AccountAPIImpl(HazelcastInstance hazelcast) {
        String serviceName = bindService().getServiceDescriptor().getName();
        logger.info("AccountAPIImpl initializing structures for " + serviceName);

        bufferPairsForAPI = hazelcast.getMap(serviceName+"_APIS");

        APIBufferPair<OpenAccountRequest,OpenAccountResponse> openHandler =
                new APIBufferPair(hazelcast,"open", Arity.UNARY, Arity.UNARY);
        bufferPairsForAPI.put("open", openHandler);
        this.openHandler = openHandler;

        // Avoid creating unnecessary local variable, redo above to match
        balanceAdjustHandler = new APIBufferPair(hazelcast, "adjustBalance", Arity.UNARY, Arity.UNARY);
        bufferPairsForAPI.put("adjustBalance", balanceAdjustHandler);

        accountDAO = new AccountDAO(hazelcast);

        //Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new CheckPendingOperations(hazelcast), 5, 5, TimeUnit.SECONDS);
    }

    // Schedule this to run at a fixed rate
    private class CheckPendingOperations implements Runnable {
        HazelcastInstance hazelcast;
        IMap<PartitionedSequenceKey, AccountEvent> pendingEventsMap;
        IMap<PartitionedSequenceKey, CompletionInfo> completionsMap;


        public CheckPendingOperations(HazelcastInstance hz) {
            this.hazelcast = hz;
            // EventSourcingController allows these names to be overridden
            // but since we aren't doing so we will hard-code the names here.
            pendingEventsMap = hazelcast.getMap("account_PENDING");
            completionsMap = hazelcast.getMap("account_COMPLETIONS");
        }
        @Override
        public void run() {
            int pendingCount = 0;
            for (UUID identifier : pendingOperationMap.keySet()) {
                pendingCount++;
                PendingOperation op = pendingOperationMap.get(identifier);
                long elapsed = System.currentTimeMillis() - op.timestamp;
                if (elapsed > THRESHOLD_FAIL) {
                    logger.warning(op.eventName + " item pending > " + THRESHOLD_FAIL + ", marking failed");
                    // TODO: add 'cancel' or 'abort' in APIBufferPair
                } else if (elapsed > THRESHOLD_WARN) {
                    logger.warning(op.eventName + " item pending > " + THRESHOLD_WARN);
                }

            }
            //if (pendingCount > 0)
                logger.info(pendingCount + " operations pending");
        }

        private void find(UUID identifier, APIBufferPair handler) {
            PartitionedSequenceKey stuckKey = null;
            // Should be in PendingEvents

            // Should be in Completions in an incomplete status
            // Is the Request still queued?
            // Is the Response object in the response map?
        }
    }


    @Override
    public void open(OpenAccountRequest request, StreamObserver<OpenAccountResponse> responseObserver) {
        UUID identifier = UUID.randomUUID();
        if (TRACK_PENDING_OPS)
            pendingOperationMap.put(identifier, new PendingOperation("open"));
        openHandler.putUnaryRequest(identifier, request);

        OpenAccountResponse response = openHandler.getUnaryResponse(identifier);
        if (TRACK_PENDING_OPS)
            pendingOperationMap.remove(identifier);
        if (response == null) {
            responseObserver.onError(new OperationTimeoutException("OPEN operation timed out"));
            return;
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void deposit(AccountOuterClass.AdjustBalanceRequest request, StreamObserver<AccountOuterClass.AdjustBalanceResponse> responseObserver) {
        //logger.info("deposit requested " + request.getAccountNumber() + " " + request.getAmount());
        UUID identifier = UUID.randomUUID();
        if (TRACK_PENDING_OPS)
            pendingOperationMap.put(identifier, new PendingOperation("deposit"));
        balanceAdjustHandler.putUnaryRequest(identifier, request);

        AdjustBalanceResponse response = balanceAdjustHandler.getUnaryResponse(identifier);
        if (TRACK_PENDING_OPS)
            pendingOperationMap.remove(identifier);
        if (response == null) {
            responseObserver.onError(new OperationTimeoutException("DEPOSIT operation timed out"));
            return;
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void withdraw(AdjustBalanceRequest request, StreamObserver<AdjustBalanceResponse> responseObserver) {
        //System.out.println("withdrawal requested " + request.getAccountNumber() + " " + request.getAmount());
        UUID identifier = UUID.randomUUID();
        if (TRACK_PENDING_OPS)
            pendingOperationMap.put(identifier, new PendingOperation("withdraw"));

        // Flip the sign of the amount to make it a debit
        AdjustBalanceRequest modRequest = AdjustBalanceRequest.newBuilder(request)
                .setAmount(-1 * request.getAmount())
                .build();
        balanceAdjustHandler.putUnaryRequest(identifier, modRequest);

        AdjustBalanceResponse response = balanceAdjustHandler.getUnaryResponse(identifier);
        if (TRACK_PENDING_OPS)
            pendingOperationMap.remove(identifier);
        if (response == null) {
            responseObserver.onError(new OperationTimeoutException("WITHDRAW operation timed out"));
            return;
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void payment(AccountOuterClass.AdjustBalanceRequest request, StreamObserver<AccountOuterClass.AdjustBalanceResponse> responseObserver) {
        // Just like withdrawals, we flip the sign and use the method to handle the transaction
        AccountOuterClass.AdjustBalanceRequest payment = AccountOuterClass.AdjustBalanceRequest.newBuilder(request)
                .setAmount(request.getAmount() * -1)
                .build();
        deposit(payment, responseObserver);
    }

    @Override
    public void checkBalance(AccountOuterClass.CheckBalanceRequest request, StreamObserver<AccountOuterClass.CheckBalanceResponse> responseObserver) {
        String acctNumber = request.getAccountNumber();
        Account account = accountDAO.findByKey(acctNumber);
        if (account == null) {
            Exception e = new IllegalArgumentException("Account Number does not exist: :" + acctNumber);
            responseObserver.onError(e);
        } else {
            logger.info("AccountAPIImpl.checkBalance: Balance is " + account.getBalance());
            int balanceInCents = account.getBalance().movePointRight(2).intValue();
            AccountOuterClass.CheckBalanceResponse response = AccountOuterClass.CheckBalanceResponse.newBuilder()
                    .setBalance(balanceInCents)
                    .build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void requestAuth(AccountOuterClass.AuthorizationRequest request, StreamObserver<AccountOuterClass.AuthorizationResponse> responseObserver) {
        String acctNumber = request.getAccountNumber();
        Account account = accountDAO.findByKey(acctNumber);
        boolean approved = false;
        if (account == null) {
            Exception e = new IllegalArgumentException("Account Number does not exist: :" + acctNumber);
            responseObserver.onError(e);
        } else {
            int balanceInCents = account.getBalance().movePointRight(2).intValue();
            if (balanceInCents >= request.getRequestedAmount())
                approved = true;
            AccountOuterClass.AuthorizationResponse response = AccountOuterClass.AuthorizationResponse.newBuilder()
                    .setApproved(approved)
                    .build();
            responseObserver.onNext(response);
        }
        logger.info("Requested auth for " + request.getRequestedAmount() + ", balance is " +
                account.getBalance() + ", approved=" + approved);
        responseObserver.onCompleted();
    }

    @Override
    public void transferMoney(AccountOuterClass.TransferMoneyRequest request, StreamObserver<AccountOuterClass.TransferMoneyResponse> responseObserver) {
        //logger.info("AccountAPIImpl.transferMoney");
        AdjustBalanceRequest withdrawal = AdjustBalanceRequest.newBuilder()
                .setAccountNumber(request.getFromAccountNumber())
                // Note that withdraw() will flip sign of the amount so don't do it here.
                .setAmount(request.getAmount())
                .build();

        AdjustBalanceRequest deposit = AdjustBalanceRequest.newBuilder()
                .setAccountNumber(request.getToAccountNumber())
                .setAmount(request.getAmount())
                .build();

        final CountDownLatch latch = new CountDownLatch(2);  // Simple Java, not HZ's distributed CPSubsystem one

        withdraw(withdrawal, new StreamObserver<>() {
                    @Override
                    public void onNext(AdjustBalanceResponse adjustBalanceResponse) {
                        //System.out.println("Withdrawal processed");
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // pass to our caller
                        responseObserver.onError(throwable);
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });

        deposit(deposit, new StreamObserver<>() {
            @Override
            public void onNext(AdjustBalanceResponse adjustBalanceResponse) {
                //System.out.println("Deposit processed");
            }

            @Override
            public void onError(Throwable throwable) {
                // pass to our caller
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        try {
            latch.await(); // TODO: maybe use a timer here in case of issues with pipeline
            //System.out.println("Both halves complete, sending response");
            TransferMoneyResponse response = TransferMoneyResponse.newBuilder()
                    .setSucceeded(true)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InterruptedException e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }

    }

    @Override
    public void allAccountNumbers(AccountOuterClass.AllAccountsRequest request, StreamObserver<AccountOuterClass.AllAccountsResponse> responseObserver) {
        Collection<Account> accounts = accountDAO.getAllAccounts();
        List<String> accountNumbers = new ArrayList<>();
        for (Account a : accounts) {
            accountNumbers.add(a.getAccountNumber());
        }
        AccountOuterClass.AllAccountsResponse.Builder responseBuilder = AccountOuterClass.AllAccountsResponse.newBuilder();
        responseBuilder.addAllAccountNumber(accountNumbers);
        AccountOuterClass.AllAccountsResponse response = responseBuilder.build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void totalAccountBalances(AccountOuterClass.TotalBalanceRequest request, StreamObserver<AccountOuterClass.TotalBalanceResponse> responseObserver) {
        BigDecimal total = accountDAO.getTotalAccountBalances();
        AccountOuterClass.TotalBalanceResponse response = AccountOuterClass.TotalBalanceResponse.newBuilder()
                .setTotalBalance(total.intValue())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}