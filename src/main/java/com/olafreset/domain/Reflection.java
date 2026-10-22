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

/** 1日の振り返りメモ。1ユーザー1日1件。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "reflection_date"}))
public class Reflection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 持ち主。ユーザー管理を入れる前のデータを引き継げるよう、DB上は NULL を許可している。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @Column(name = "reflection_date", nullable = false)
    private LocalDate date;

    @Column(length = 2000)
    private String text;

    protected Reflection() {
    }

    public Reflection(AppUser owner, LocalDate date) {
        this.owner = owner;
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
