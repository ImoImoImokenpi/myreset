package com.olafreset.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * DATABASE_URL を JDBC の接続情報に変換する。
 * Neon などが表示する「postgresql://ユーザー:パスワード@ホスト/DB名?sslmode=require」も、
 * 「jdbc:postgresql://ホスト/DB名?sslmode=require」もそのまま受け付ける。
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    public static DatabaseUrl parse(String url, String username, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("環境変数 DATABASE_URL が設定されていません");
        }
        String value = url.strip();
        if (value.startsWith("jdbc:")) {
            return new DatabaseUrl(value, username, password);
        }
        if (!value.startsWith("postgres://") && !value.startsWith("postgresql://")) {
            throw new IllegalStateException(
                    "DATABASE_URL は postgresql://... か jdbc:postgresql://... の形式にしてください");
        }

        URI uri = URI.create(value);
        String user = username;
        String pass = password;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            user = decode(parts[0]);
            pass = parts.length > 1 ? decode(parts[1]) : pass;
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String jdbc = "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query;
        return new DatabaseUrl(jdbc, user, pass);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
