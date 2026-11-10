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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.annotations.ColumnDefault;

/** 決まった曜日に取り組む習慣（クエスト）。回数や時間の目標を持つこともできる。 */
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

    /** 実施する曜日（月曜=1ビット目 … 日曜=7ビット目）。127 は毎日。 */
    @ColumnDefault("127")
    @Column(nullable = false)
    private int days = ALL_DAYS;

    /** 1日の目標量。1 なら「やった／やってない」だけの習慣。 */
    @ColumnDefault("1")
    @Column(nullable = false)
    private int target = 1;

    /** 「＋」ボタン1回で増やす量。 */
    @ColumnDefault("1")
    @Column(nullable = false)
    private int step = 1;

    /** 量の単位（回・杯・分 など）。 */
    @Column(length = 10)
    private String unit;

    /** フレンドと一緒に取り組んでいるときのチーム。1人でやる習慣では null。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private HabitTeam team;

    protected Habit() {
    }

    /**
     * チームに参加した人の習慣を、招待した人の習慣と同じ設定で作る。
     * source は遅延読み込みのプロキシのことがあるので、フィールドではなくゲッターで読む。
     */
    public static Habit joinTeam(Habit source, AppUser owner) {
        Habit habit = new Habit(owner, source.getName(), source.getCategory(), source.getXp());
        habit.days = source.getDays();
        habit.target = source.getTarget();
        habit.step = source.getStep();
        habit.unit = source.getUnit();
        habit.team = source.getTeam();
        return habit;
    }

    public static final int ALL_DAYS = 0b1111111;

    public Habit(AppUser owner, String name, Category category, int xp) {
        this.owner = owner;
        this.name = name;
        this.category = category;
        this.xp = xp;
        this.createdAt = LocalDate.now();
    }

    /** 曜日と目標量を決める。 */
    public void configure(Set<DayOfWeek> days, int target, int step, String unit) {
        this.days = toMask(days);
        this.target = Math.max(1, target);
        this.step = Math.max(1, step);
        this.unit = unit == null || unit.isBlank() ? null : unit.strip();
    }

    public static int toMask(Set<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek d : days) {
            mask |= 1 << (d.getValue() - 1);
        }
        return mask == 0 ? ALL_DAYS : mask;
    }

    public boolean isScheduledOn(LocalDate date) {
        return (days >> (date.getDayOfWeek().getValue() - 1) & 1) == 1;
    }

    public Set<DayOfWeek> getDayOfWeeks() {
        return EnumSet.allOf(DayOfWeek.class).stream()
                .filter(d -> (days >> (d.getValue() - 1) & 1) == 1)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    /** 「毎日」「平日」「土日」「月・水・金」のような表示。 */
    public String getScheduleLabel() {
        if (days == ALL_DAYS) {
            return "毎日";
        }
        if (days == 0b0011111) {
            return "平日";
        }
        if (days == 0b1100000) {
            return "土日";
        }
        return getDayOfWeeks().stream().map(Habit::shortName).collect(Collectors.joining("・"));
    }

    public static String shortName(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "月";
            case TUESDAY -> "火";
            case WEDNESDAY -> "水";
            case THURSDAY -> "木";
            case FRIDAY -> "金";
            case SATURDAY -> "土";
            case SUNDAY -> "日";
        };
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

    public void rename(String name) {
        this.name = name;
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

    public int getDays() {
        return days;
    }

    public int getTarget() {
        return target;
    }

    public int getStep() {
        return step;
    }

    public String getUnit() {
        return unit;
    }

    public HabitTeam getTeam() {
        return team;
    }

    public void setTeam(HabitTeam team) {
        this.team = team;
    }

    /** フレンドと一緒に取り組んでいる習慣か。 */
    public boolean isTeamHabit() {
        return team != null;
    }

    /** 量を記録する習慣か（目標が 2 以上、または単位がある）。 */
    public boolean isQuantity() {
        return target > 1 || unit != null;
    }

    /** 「8杯」「30分」のような目標の表示。 */
    public String getTargetLabel() {
        return target + (unit == null ? "回" : unit);
    }
}
