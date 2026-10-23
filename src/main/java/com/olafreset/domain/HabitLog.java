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
import org.hibernate.annotations.ColumnDefault;

/** ある日に習慣を達成した記録。1習慣につき1日1件。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "log_date"}))
public class HabitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_id")
    private Habit habit;

    @Column(name = "log_date", nullable = false)
    private LocalDate date;

    /** 記録した時点の XP（あとで習慣の XP を変えても過去の獲得分は変わらない）。 */
    @Column(nullable = false)
    private int xp;

    /** その日に記録した量。「やった／やってない」の習慣では 1。 */
    @ColumnDefault("1")
    @Column(nullable = false)
    private int amount = 1;

    /** 目標量に届いたか。XP や連続日数は達成した記録だけで数える。 */
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean completed = true;

    protected HabitLog() {
    }

    public HabitLog(Habit habit, LocalDate date, int amount) {
        this.habit = habit;
        this.date = date;
        this.xp = habit.getXp();
        setAmount(amount);
    }

    public void setAmount(int amount) {
        this.amount = amount;
        this.completed = amount >= habit.getTarget();
    }

    public int getAmount() {
        return amount;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Long getId() {
        return id;
    }

    public Habit getHabit() {
        return habit;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getXp() {
        return xp;
    }
}
