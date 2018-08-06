/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.client.ZeebeClient;
import io.zeebe.broker.client.api.commands.Partition;
import io.zeebe.broker.client.api.commands.Topic;
import io.zeebe.broker.client.api.events.JobState;
import io.zeebe.broker.client.impl.job.CreateJobCommandImpl;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.test.util.AutoCloseableRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class SubscriptionClusteredTest {
  private static final int PARTITION_COUNT = 3;

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(30);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  private ZeebeClient client;

  @Before
  public void startUp() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldOpenSubscriptionGroupForDistributedTopic() {
    // given
    final Topic topic = clusteringRule.waitForTopic(PARTITION_COUNT);

    // when
    final Integer[] partitionIds =
        topic.getPartitions().stream().mapToInt(Partition::getId).boxed().toArray(Integer[]::new);

    createJobOnPartition(partitionIds[0]);
    createJobOnPartition(partitionIds[1]);
    createJobOnPartition(partitionIds[2]);

    // and
    final List<Integer> receivedPartitionIds = new ArrayList<>();
    client
        .topicClient()
        .newSubscription()
        .name("SubscriptionName")
        .jobEventHandler(
            e -> {
              if (e.getState() == JobState.CREATED) {
                receivedPartitionIds.add(e.getMetadata().getPartitionId());
              }
            })
        .startAtHeadOfTopic()
        .open();

    // then
    waitUntil(() -> receivedPartitionIds.size() == PARTITION_COUNT);

    assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
  }

  protected void createJobOnPartition(int partition) {
    final CreateJobCommandImpl command =
        (CreateJobCommandImpl) client.topicClient().jobClient().newCreateCommand().jobType("baz");

    command.getCommand().setPartitionId(partition);
    command.send().join();
  }
}
