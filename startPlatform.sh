# Used in client/server mode to start HZ Platform Node
export HZ_USERCODEDEPLOYMENT_ENABLED=true

#export CLASSPATH="\
#~/Documents/GitHub/AccountService/target/accountservice-1.0-SNAPSHOT.jar:\
#~/Documents/GitHub/GrpcSourceAndSink/target/grpc-connectors-1.0-SNAPSHOT.jar:\
#~/Documents/GitHub/EventSourcing/Event/target/eventsourcing-1.0-SNAPSHOT.jar:\
#$CLASSPATH"

#$PWD/target/accountservice-1.0-SNAPSHOT.jar:\

export CLASSPATH="\
$PWD/target/dependentJars/grpc-connectors-1.0-SNAPSHOT.jar:\
$PWD/target/dependentJars/eventsourcing-1.0-SNAPSHOT.jar:\
$PWD/target/accountservice-1.0-SNAPSHOT.jar"

#echo $CLASSPATH
hz start -c target/classes/hazelcast.yaml
#/opt/homebrew/bin/hz start -c target/classes/hazelcast.yaml