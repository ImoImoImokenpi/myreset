package com.olafreset.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public class PasswordForm {

    @NotBlank(message = "現在のパスワードを入力してください")
    private String currentPassword = "";

    @Size(min = 8, max = 72, message = "パスワードは8〜72文字にしてください")
    private String newPassword = "";

    private String newPasswordConfirm = "";

    @AssertTrue(message = "確認用パスワードが一致しません")
    public boolean isNewPasswordConfirmed() {
        return Objects.equals(newPassword, newPasswordConfirm);
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPasswordConfirm() {
        return newPasswordConfirm;
    }

    public void setNewPasswordConfirm(String newPasswordConfirm) {
        this.newPasswordConfirm = newPasswordConfirm;
    }
}
