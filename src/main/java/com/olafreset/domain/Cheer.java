package com.olafreset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** フレンドへの応援（👏）。同じ相手には1日1回。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"from_user_id", "to_user_id", "cheer_date"}))
public class Cheer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id")
    private AppUser fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id")
    private AppUser toUser;

    @Column(name = "cheer_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Cheer() {
    }

    public Cheer(AppUser fromUser, AppUser toUser, LocalDate date) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.date = date;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getFromUser() {
        return fromUser;
    }

    public AppUser getToUser() {
        return toUser;
    }

    public LocalDate getDate() {
        return date;
    }
}
