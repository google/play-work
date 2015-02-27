#!/bin/bash

mvn clean compile assembly:single && \
java -cp target/emm-notifications-1.0-SNAPSHOT-jar-with-dependencies.jar \
    com.google.android.work.emmnotifications.notpublic.FauxPublisher  \
        --topic_name=projects/enterprise-cloud-pub-sub/topics/hello-world-topic