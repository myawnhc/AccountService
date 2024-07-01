FROM msf/hz-esf

ARG HZ_HOME=/opt/hazelcast

ADD target/accountservice-1.0-SNAPSHOT-with-dependencies.jar $HZ_HOME/bin/user-lib/
# target directory is where we expect to find jars we upload as part of Jet JobConfig
ADD target/accountservice-1.0-SNAPSHOT.jar $HZ_HOME/target/
ADD target/dependentJars/eventsourcing-1.0-SNAPSHOT.jar $HZ_HOME/target/dependentJars/
ADD target/dependentJars/AccountProto-1.0-SNAPSHOT.jar $HZ_HOME/target/dependentJars/
ADD target/classes/service.yaml $HZ_HOME/config/service.yaml

ARG ACCT_JAR="$HZ_HOME/bin/user-lib/accountservice-1.0-SNAPSHOT-with-dependencies.jar"

ENV JAVA_ARGS "-cp $ACCT_JAR"
ENV JAVA_OPTS "--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

ENTRYPOINT exec java \
 $JAVA_ARGS $JAVA_OPTS \
 org.hazelcast.msfdemo.acctsvc.service.AccountService