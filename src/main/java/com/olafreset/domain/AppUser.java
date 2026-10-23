package com.olafreset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** アプリの利用者。USER は SQL の予約語なので app_user テーブルにする。 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /** メインカラーの初期値。 */
    public static final String DEFAULT_THEME_COLOR = "#4f46e5";

    public enum Role {
        USER, ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** ユーザーが選んだメインカラー（#rrggbb）。未設定なら初期値を使う。 */
    @Column(length = 7)
    private String themeColor;

    /** 招待リンク用のコード。初めて使うときに作る。 */
    @Column(length = 16, unique = true)
    private String inviteCode;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    /** 画面の CSS にそのまま埋め込むので、#rrggbb 形式以外は初期値に置き換える。 */
    public String getThemeColor() {
        return themeColor != null && themeColor.matches("#[0-9a-fA-F]{6}") ? themeColor : DEFAULT_THEME_COLOR;
    }

    public void setThemeColor(String themeColor) {
        this.themeColor = themeColor;
    }
}
