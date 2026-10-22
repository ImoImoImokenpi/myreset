package com.olafreset.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public class RegisterForm {

    @NotBlank(message = "ユーザー名を入力してください")
    @Pattern(regexp = "[A-Za-z0-9_]{3,30}", message = "ユーザー名は半角英数字と _ の3〜30文字にしてください")
    private String username;

    @Size(min = 8, max = 72, message = "パスワードは8〜72文字にしてください")
    private String password = "";

    private String passwordConfirm = "";

    @AssertTrue(message = "確認用パスワードが一致しません")
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, passwordConfirm);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }
}
