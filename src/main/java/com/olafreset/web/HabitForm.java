package com.olafreset.web;

import com.olafreset.domain.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class HabitForm {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 50, message = "名前は50文字以内にしてください")
    private String name;

    @NotNull
    private Category category = Category.BODY;

    @Min(value = 1, message = "XPは1以上にしてください")
    @Max(value = 100, message = "XPは100以下にしてください")
    private int xp = 10;

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
}
