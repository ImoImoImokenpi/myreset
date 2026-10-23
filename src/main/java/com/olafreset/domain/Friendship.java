package com.olafreset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** フレンド関係。申請した人(requester)と申請された人(addressee)の組で1件。承認されると ACCEPTED になる。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
public class Friendship {

    public enum Status {
        PENDING, ACCEPTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private AppUser requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id")
    private AppUser addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Friendship() {
    }

    public Friendship(AppUser requester, AppUser addressee, Status status) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getRequester() {
        return requester;
    }

    public AppUser getAddressee() {
        return addressee;
    }

    public Status getStatus() {
        return status;
    }

    public void accept() {
        this.status = Status.ACCEPTED;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 自分から見た相手。 */
    public AppUser other(AppUser me) {
        return requester.getId().equals(me.getId()) ? addressee : requester;
    }
}
