docker run --name mancenter \
            --network hazelcast-network \
            --rm \
            -p 8080:8080 \
            hazelcast/management-center:5.3.3