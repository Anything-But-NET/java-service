package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.UserAclUpsert;
import com.anything_but_dotnet.copilot.entity.User;
import com.anything_but_dotnet.copilot.repository.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

@Service
public class UsersService {
    private static final Logger log = LoggerFactory.getLogger(UsersService.class);

    private final UsersRepository usersRepository;
    private final Clock clock;

    public UsersService(UsersRepository usersRepository, Clock clock) {
        this.usersRepository = usersRepository;
        this.clock = clock;
    }

    @Transactional
    public void processEvent(UserAclUpsert event) {
        User user = findOrCreate(event.email());

        if (user.isStale(event.lastUpdated())) {
            log.warn(
                    "Skipping stale user acl upsert event for user={} (event last_updated={}, db last_updated={})",
                    event.email(), event.lastUpdated(), user.getLastUpdated()
            );
            return;
        }

        List<String> groups = event.acl() != null ? event.acl().normalizedGroups() : List.of();
        user.applyUpsert(groups, event.lastUpdated(), clock.instant());
        usersRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<String> getUserGroups(String email) {
        return usersRepository.findById(email)
                .map(user -> Stream.concat(user.getAllowedGroups().stream(), Stream.of(email))
                        .distinct()
                        .toList())
                .orElseGet(() -> List.of(email));
    }

    private User findOrCreate(String email) {
        return usersRepository.findById(email)
                .orElseGet(() -> new User(email));
    }
}
