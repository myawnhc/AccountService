# Used in client/server mode to start HZ Platform Node
#ln -s /Users/myawn/Documents/GitHub/AccountService/target/AccountProto-1.0-SNAPSHOT.jar \
#      /opt/homebrew/Cellar/hazelcast/5.3.0/libexec/custom-lib/AccountProto-1.0-SNAPSHOT.jar
ln -s /Users/myawn/Documents/GitHub/AccountService/target/accountservice-1.0-SNAPSHOT.jar \
      /opt/homebrew/Cellar/hazelcast/5.3.0/libexec/bin/user-lib/accountservice-1.0-SNAPSHOT.jar
#ln -s /Users/myawn/Documents/GitHub/InventoryService/target/InventoryService-1.0-SNAPSHOT.jar-1.0-SNAPSHOT.jar \
#      /opt/homebrew/Cellar/hazelcast/5.3.0/libexec/bin/user-lib/InventoryService-1.0-SNAPSHOT.jar
#ln -s /Users/myawn/Documents/GitHub/OrderService/target/orderservice-1.0-SNAPSHOT.jar \
#      /opt/homebrew/Cellar/hazelcast/5.3.0/libexec/bin/user-lib/orderservice-1.0-SNAPSHOT.jar
ln -s /Users/myawn/Documents/GitHub/GrpcSourceAndSink/target/grpc-connectors-1.0-SNAPSHOT.jar \
      /opt/homebrew/Cellar/hazelcast/5.3.0/libexec/bin/user-lib/grpc-connectors-1.0-SNAPSHOT.jar
ln -s /Users/myawn/Documents/GitHub/EventSourcing/Event/target/eventsourcing-1.0-SNAPSHOT.jar \
      /opt/homebrew/Cellar/hazelcast/5.3.0/libexec/bin/user-lib/eventsourcing-1.0-SNAPSHOT.jar
ls -l /opt/homebrew/Cellar/hazelcast/5.3.0/libexec/bin/user-lib
export HZ_USERCODEDEPLOYMENT_ENABLED=true
/opt/homebrew/bin/hz start -c target/classes/hazelcast.yaml