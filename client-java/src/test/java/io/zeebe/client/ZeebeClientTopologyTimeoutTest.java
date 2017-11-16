package io.zeebe.client;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Properties;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.time.ClockUtil;

public class ZeebeClientTopologyTimeoutTest
{
    @Rule
    public StubBrokerRule broker = new StubBrokerRule();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();


    protected ZeebeClient buildClient()
    {
        final ZeebeClient client = ZeebeClient.create(new Properties());
        closeables.manage(client);
        return client;
    }

    @After
    public void tearDown()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldFailRequestIfTopologyCannotBeRefreshed()
    {
        // given
        broker.onTopologyRequest().doNotRespond();
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .doNotRespond();

        final TaskEventImpl baseEvent = Events.exampleTask();

        final ZeebeClient client = buildClient();

        // then
        exception.expect(ClientException.class);
        exception.expectMessage("Cannot determine leader for partition (timeout 5 seconds). " +
                "Request was: [ topic = default-topic, partition = 99, event type = TASK ]");

        // when
        client.tasks().complete(baseEvent).execute();
    }

    @Test
    public void shouldRetryTopologyRequestAfterTimeout()
    {
        // given
        final int topologyTimeoutSeconds = 5;

        ClockUtil.pinCurrentTime();
        broker.onTopologyRequest().doNotRespond();
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key(123)
            .event()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETED")
              .done()
            .register();
        final TaskEventImpl baseEvent = Events.exampleTask();

        final ZeebeClient client = buildClient();

        // wait for a hanging topology request
        waitUntil(() ->
            broker.getReceivedControlMessageRequests()
                .stream()
                .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
                .count() == 1);

        broker.stubTopologyRequest(); // make topology available
        ClockUtil.addTime(Duration.ofSeconds(topologyTimeoutSeconds + 1)); // let request time out

        // when making a new request
        final TaskEvent response = client.tasks().complete(baseEvent).execute();

        // then the topology has been refreshed and the request succeeded
        assertThat(response.getState()).isEqualTo("COMPLETED");
    }

}
