package com.flavors.repository;

import com.flavors.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** "My notifications, newest first" (the index in V5 serves it). */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}
