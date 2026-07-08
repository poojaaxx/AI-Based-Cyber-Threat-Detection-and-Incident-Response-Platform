package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.request.SimulationRequest;
import com.cyberguard.platform.dto.response.SimulationResponse;
import com.cyberguard.platform.entity.SimulationRun;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.SimulationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Threat Simulation Lab", description = "Manual threat simulation using the existing AI prediction service, plus simulation history")
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping
    public ResponseEntity<SimulationResponse> runSimulation(@Valid @RequestBody SimulationRequest request,
                                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(simulationService.runSimulation(request, principal.getUser()));
    }

    @GetMapping
    public ResponseEntity<Page<SimulationRun>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String threatCategory,
            @RequestParam(required = false) String trafficType,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(simulationService.search(search, threatCategory, trafficType, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulationRun> getById(@PathVariable Long id) {
        return ResponseEntity.ok(simulationService.getOrThrow(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        simulationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
