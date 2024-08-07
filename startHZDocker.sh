#
# Copyright 2024 Hazelcast, Inc
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
# Vestibulum commodo. Ut rhoncus gravida arcu.
#

docker network create hazelcast-network || true
docker run --name hz-acct-node \
           --rm \
           --network hazelcast-network \
           -e HZ_CLUSTERNAME=acctsvc \
           -e HZ_JET_RESOURCEUPLOADENABLED=true \
           -p5701:5701 \
           msf/hz-esf
