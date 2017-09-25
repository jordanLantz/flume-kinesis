package com.amazonaws.services.kinesis.flume;

/*
 * Copyright 2012-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Modifications: Copyright 2015 Sharethrough, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.google.common.base.Preconditions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flume.Context;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.PollableSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.source.AbstractSource;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.kinesis.MyAwsCredentials;
import com.amazonaws.services.kinesis.RecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;

public class KinesisSource extends AbstractSource implements Configurable, PollableSource {

  private static final Log LOG = LogFactory.getLog(KinesisSource.class);
  private Worker worker;

  // Initial position in the stream when the application starts up for the first time.
  // Position can be one of LATEST (most recent data) or TRIM_HORIZON (oldest available data)
  private InitialPositionInStream DEFAULT_INITIAL_POSITION = InitialPositionInStream.TRIM_HORIZON;

  private KinesisClientLibConfiguration kinesisClientLibConfiguration;

  @Override
  public void configure(Context context) {
    String endpoint = context.getString("endpoint", ConfigurationConstants.DEFAULT_KINESIS_ENDPOINT);

    String region = context.getString("region", ConfigurationConstants.DEFAULT_REGION);

    String streamName = Preconditions.checkNotNull(
        context.getString("streamName"), "streamName is required");

    String applicationName = Preconditions.checkNotNull(
        context.getString("applicationName"), "applicationName is required");

    String initialPosition = context.getString("initialPosition", "TRIM_HORIZON");
    String workerId = null;

    if (initialPosition.equals("LATEST")) {
      DEFAULT_INITIAL_POSITION=InitialPositionInStream.LATEST;
    }

    String accessKeyId = context.getString("accessKeyId");

    String secretAccessKey = context.getString("secretAccessKey");

    AWSCredentialsProvider credentialsProvider;

    if (accessKeyId != null && secretAccessKey != null) {
      credentialsProvider = new MyAwsCredentials(accessKeyId, secretAccessKey);
    } else {
      credentialsProvider = new DefaultAWSCredentialsProviderChain();
    }

    try {
      workerId = InetAddress.getLocalHost().getCanonicalHostName() + ":" + UUID.randomUUID();
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }

    LOG.info("Using workerId: " + workerId);

    kinesisClientLibConfiguration = new KinesisClientLibConfiguration(applicationName, streamName,
        credentialsProvider, workerId).
        withKinesisEndpoint(endpoint).
        withRegionName(region).
        withInitialPositionInStream(DEFAULT_INITIAL_POSITION);

  }

  @Override
  public void start() {
    IRecordProcessorFactory recordProcessorFactory = new RecordProcessorFactory(getChannelProcessor());
    worker = new Worker(recordProcessorFactory, kinesisClientLibConfiguration);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        System.out.println("Shutting down Kinesis client thread...");
        worker.shutdown();
      }
    });

    worker.run();
  }

  @Override
  public void stop() {
  }

  @Override
  public Status process() throws EventDeliveryException {
    return null;
  }
}
