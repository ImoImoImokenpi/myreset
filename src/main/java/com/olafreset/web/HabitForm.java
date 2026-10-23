package com.olafreset.web;

import com.olafreset.domain.Category;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

public class HabitForm {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 50, message = "名前は50文字以内にしてください")
    private String name;

    @NotNull
    private Category category = Category.BODY;

    @Min(value = 1, message = "XPは1以上にしてください")
    @Max(value = 100, message = "XPは100以下にしてください")
    private int xp = 10;

    /** 実施する曜日。初期値は毎日。 */
    private Set<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);

    /** 回数・時間の目標を使うか。 */
    private boolean useTarget;

    @Min(value = 1, message = "目標は1以上にしてください")
    @Max(value = 10000, message = "目標は10000以下にしてください")
    private int target = 1;

    @Min(value = 1, message = "1回に増やす量は1以上にしてください")
    @Max(value = 1000, message = "1回に増やす量は1000以下にしてください")
    private int step = 1;

    @Size(max = 10, message = "単位は10文字以内にしてください")
    private String unit = "";

    @AssertTrue(message = "曜日を1つ以上選んでください")
    public boolean isAnyDay() {
        return days != null && !days.isEmpty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public Set<DayOfWeek> getDays() {
        return days;
    }

    public void setDays(Set<DayOfWeek> days) {
        this.days = days;
    }

    public boolean isUseTarget() {
        return useTarget;
    }

    public void setUseTarget(boolean useTarget) {
        this.useTarget = useTarget;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
