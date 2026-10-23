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
import java.time.LocalDate;
import org.hibernate.annotations.ColumnDefault;

/** 毎日取り組む習慣（クエスト）。 */
@Entity
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 持ち主。ユーザー管理を入れる前のデータを引き継げるよう、DB上は NULL を許可している。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    /** 1回達成したときにもらえる経験値。 */
    @Column(nullable = false)
    private int xp;

    @Column(nullable = false)
    private boolean active = true;

    /** フレンドに見せるか。既存のデータにも列を足せるよう DB に初期値を持たせる。 */
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean shared = true;

    @Column(nullable = false)
    private LocalDate createdAt;

    protected Habit() {
    }

    public Habit(AppUser owner, String name, Category category, int xp) {
        this.owner = owner;
        this.name = name;
        this.category = category;
        this.xp = xp;
        this.createdAt = LocalDate.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public int getXp() {
        return xp;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public void archive() {
        this.active = false;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }
}
