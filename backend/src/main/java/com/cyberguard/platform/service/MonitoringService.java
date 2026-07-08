package com.cyberguard.platform.service;

import com.cyberguard.platform.entity.LoginAttempt;
import com.cyberguard.platform.entity.NetworkEvent;
import com.cyberguard.platform.entity.SystemLog;
import com.cyberguard.platform.repository.LoginAttemptRepository;
import com.cyberguard.platform.repository.NetworkEventRepository;
import com.cyberguard.platform.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final SystemLogRepository systemLogRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final NetworkEventRepository networkEventRepository;

    public Page<SystemLog> getSystemLogs(Pageable pageable) {
        return systemLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<LoginAttempt> getLoginAttempts(Pageable pageable) {
        return loginAttemptRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<NetworkEvent> getNetworkEvents(boolean flaggedOnly, Pageable pageable) {
        return flaggedOnly
                ? networkEventRepository.findByFlaggedTrueOrderByCreatedAtDesc(pageable)
                : networkEventRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public SystemLog recordLog(SystemLog log) {
        return systemLogRepository.save(log);
    }

    public NetworkEvent recordNetworkEvent(NetworkEvent event) {
        return networkEventRepository.save(event);
    }
}
