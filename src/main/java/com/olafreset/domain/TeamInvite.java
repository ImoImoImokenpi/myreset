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
import java.time.LocalDateTime;

/** 「一緒にやろう」の招待。招待した人の習慣と、招待された人の組で1件。承認すると消える。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "invitee_id"}))
public class TeamInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 招待した人の習慣。参加するとこの習慣の設定がコピーされる。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_id")
    private Habit habit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id")
    private AppUser invitee;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected TeamInvite() {
    }

    public TeamInvite(Habit habit, AppUser invitee) {
        this.habit = habit;
        this.invitee = invitee;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Habit getHabit() {
        return habit;
    }

    public AppUser getInviter() {
        return habit.getOwner();
    }

    public AppUser getInvitee() {
        return invitee;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
