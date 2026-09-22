package com.cyberguard.platform.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.cyberguard.platform.validation.ValidIpAddress;
import java.time.Instant;
import java.util.List;
public final class CollectorRequests {
    public static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public record Start(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,60}") String collectorId,
                        @NotNull @Pattern(regexp=UUID) String sessionId) {}
    public record Heartbeat(@NotBlank String collectorId, @NotNull @Pattern(regexp=UUID) String sessionId,
            @PastOrPresent Instant lastSuccessfulSampleAt,
            @Pattern(regexp="SAMPLE_FAILED|ACCESS_DENIED") String sampleError,
            @Pattern(regexp="INGESTION_FAILED") String deliveryError,
            @Min(0) @Max(1000) long queuedEvents, @Min(0) long droppedEvents, @Min(0) long gapCount) {}
    public enum EventType { CONNECTION_PRESENT, CONNECTION_OBSERVED, CONNECTION_STATE_CHANGED, CONNECTION_NO_LONGER_OBSERVED }
    public enum TcpState { Closed, Listen, SynSent, SynReceived, Established, FinWait1, FinWait2, CloseWait, Closing, LastAck, TimeWait, DeleteTcb, Bound }
    public record Observation(
            @NotBlank @ValidIpAddress String localIp, @NotNull @Min(0) @Max(65535) Integer localPort,
            @NotBlank @ValidIpAddress String remoteIp, @NotNull @Min(0) @Max(65535) Integer remotePort,
            @NotNull @Pattern(regexp="TCP") String protocol, @NotNull TcpState tcpState,
            @PositiveOrZero Long processId, @Size(max=120) @Pattern(regexp="[^\\\\/\r\n\t]*") String processName,
            Instant connectionCreatedAt, @NotNull Instant observedAt,
            @Positive long sequence, @NotNull @Pattern(regexp=UUID) String connectionId,
            @NotNull EventType eventType) {}
    public record Batch(@NotBlank String collectorId, @NotNull @Pattern(regexp=UUID) String sessionId,
                        @NotEmpty @Size(max=100) List<@NotNull @Valid Observation> observations) {}
}
