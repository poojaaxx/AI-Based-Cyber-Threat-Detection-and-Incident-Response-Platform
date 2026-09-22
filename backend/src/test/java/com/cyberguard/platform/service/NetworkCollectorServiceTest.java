package com.cyberguard.platform.service;
import com.cyberguard.platform.dto.request.CollectorRequests.*;
import com.cyberguard.platform.repository.*;
import com.cyberguard.platform.client.AiServiceClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.collector.enabled=true", "app.collector.demo-rule-enabled=true"})
@Import(NetworkCollectorService.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class NetworkCollectorServiceTest {
    @Autowired NetworkCollectorService service;
    @Autowired NetworkEventRepository networks;
    @Autowired SecurityEventRepository events;
    @Autowired CollectorStateRepository collectors;
    @Autowired ThreatRepository threats;
    @Autowired IncidentRepository incidents;
    @MockBean SseHubService sse;
    @MockBean AiServiceClient ai;
    String session;
    @BeforeEach void start() {
        session=UUID.randomUUID().toString(); service.start(new Start("windows-local",session));
    }
    Observation observation(long seq, String connection, EventType type, int port) {
        return new Observation("127.0.0.1",51001,"127.0.0.1",port,"TCP",TcpState.Established,
                1234L,"test-process",null,Instant.now(),seq,connection,type);
    }
    @Test void eventMetadataIsHonestAndDeliveryIsIdempotentWithoutThreatsOrAi() {
        long count=events.count(), threatCount=threats.count(), incidentCount=incidents.count();
        Batch batch=new Batch("windows-local",session,List.of(observation(1,UUID.randomUUID().toString(),EventType.CONNECTION_OBSERVED,8080)));
        assertEquals(1,service.ingest(batch).get("inserted"));
        assertEquals(0,service.ingest(batch).get("inserted"));
        assertEquals(count+1,events.count());
        var row=events.findAll().stream().filter(e -> e.getNetworkEvent()!=null && session.equals(e.getNetworkEvent().getSessionId())).findFirst().orElseThrow();
        assertEquals("EVENT",row.getDetectorType()); assertNull(row.getThreatId()); assertNull(row.getIncidentId());
        assertNull(row.getLoginAttemptId()); assertEquals(51001,row.getNetworkEvent().getLocalPort());
        assertNull(row.getNetworkEvent().getSourceIp()); assertNull(row.getNetworkEvent().getDestinationIp());
        assertNull(row.getNetworkEvent().getBytesTransferred()); assertNull(row.getNetworkEvent().getPacketCount());
        assertEquals(threatCount,threats.count()); assertEquals(incidentCount,incidents.count());
        verifyNoInteractions(ai); verify(sse,never()).broadcastDashboardUpdate(anyString());
    }
    @Test void controlledRuleMatchesOnceAndExcludesBaselineConnections() {
        long threatCount=threats.count(), incidentCount=incidents.count();
        String baseline=UUID.randomUUID().toString(), fresh=UUID.randomUUID().toString();
        service.ingest(new Batch("windows-local",session,List.of(
                observation(1,baseline,EventType.CONNECTION_PRESENT,19090),
                observation(2,baseline,EventType.CONNECTION_STATE_CHANGED,19090),
                observation(3,fresh,EventType.CONNECTION_OBSERVED,19090),
                observation(4,fresh,EventType.CONNECTION_STATE_CHANGED,19090))));
        var matches=events.findAll().stream().filter(e -> e.getNetworkEvent()!=null && session.equals(e.getNetworkEvent().getSessionId()) && "RULE".equals(e.getDetectorType())).toList();
        assertEquals(1,matches.size()); assertNull(matches.get(0).getThreatId()); assertNull(matches.get(0).getIncidentId());
        assertEquals("LOCAL_DEMO_ENDPOINT_V1",matches.get(0).getNetworkEvent().getRuleId());
        assertEquals(threatCount,threats.count()); assertEquals(incidentCount,incidents.count());
        verifyNoInteractions(ai);
    }
    @Test void healthTracksSamplingAndHeartbeatIndependentlyAndRestartRejectsOldSession() {
        assertEquals("STARTING",service.health().getStatus());
        service.heartbeat(new Heartbeat("windows-local",session,Instant.now(),null,null,0,0,0));
        assertEquals("AVAILABLE",service.health().getStatus());
        service.heartbeat(new Heartbeat("windows-local",session,Instant.now(),null,"INGESTION_FAILED",1,0,0));
        assertEquals("DEGRADED",service.health().getStatus());
        service.heartbeat(new Heartbeat("windows-local",session,Instant.now(),"SAMPLE_FAILED",null,0,2,1));
        assertEquals("DEGRADED",service.health().getStatus());
        var state=collectors.findById("windows-local").orElseThrow();
        state.setLastHeartbeatAt(Instant.now().minusSeconds(16)); collectors.saveAndFlush(state);
        service.checkHealth(); assertEquals("UNAVAILABLE",service.health().getStatus());
        service.start(new Start("windows-local",UUID.randomUUID().toString()));
        assertEquals("STARTING",service.health().getStatus());
        assertThrows(RuntimeException.class,()->service.ingest(new Batch("windows-local",session,List.of(observation(9,UUID.randomUUID().toString(),EventType.CONNECTION_PRESENT,8080)))));
    }
    @Test void invalidTimestampRollsBackEntireBatch() {
        long count=events.count();
        var bad=new Observation("127.0.0.1",51001,"127.0.0.1",8080,"TCP",TcpState.Established,
                null,null,null,Instant.now().plusSeconds(3600),2,UUID.randomUUID().toString(),EventType.CONNECTION_PRESENT);
        assertThrows(RuntimeException.class,()->service.ingest(new Batch("windows-local",session,List.of(
                observation(1,UUID.randomUUID().toString(),EventType.CONNECTION_PRESENT,8080),bad))));
        assertEquals(count,events.count());
    }
}
