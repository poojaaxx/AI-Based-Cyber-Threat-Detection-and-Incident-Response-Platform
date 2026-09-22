package com.cyberguard.platform.controller;
import com.cyberguard.platform.dto.request.CollectorRequests.*;
import com.cyberguard.platform.service.NetworkCollectorService;
import com.cyberguard.platform.exception.BadRequestException;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/v1/collector") @RequiredArgsConstructor
@PreAuthorize("hasAuthority('COLLECTOR_INGEST')")
public class CollectorIngestionController {
    private final NetworkCollectorService service;
    private final ObjectMapper mapper;
    private final Validator validator;
    private <T> T parse(byte[] body, Class<T> type) {
        try {
            T value = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(body);
            if (value == null || !validator.validate(value).isEmpty()) throw new BadRequestException("Invalid collector metadata");
            return value;
        } catch (java.io.IOException ex) { throw new BadRequestException("Invalid collector metadata"); }
    }
    @PostMapping(value="/start", consumes="application/json") public Object start(@RequestBody byte[] body) { return service.start(parse(body, Start.class)); }
    @PostMapping(value="/heartbeat", consumes="application/json") public Object heartbeat(@RequestBody byte[] body) { return service.heartbeat(parse(body, Heartbeat.class)); }
    @PostMapping(value="/observations", consumes="application/json") public Object observations(@RequestBody byte[] body) { return service.ingest(parse(body, Batch.class)); }
}
