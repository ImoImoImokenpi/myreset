package com.olafreset.service;

import com.olafreset.domain.AppSetting;
import com.olafreset.repository.AppSettingRepository;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Web Push の送信（VAPID 方式）。
 * 通知の中身は送らず「通知があるよ」という合図だけを送り、
 * 受け取ったブラウザ（sw.js）が /api/reminder から内容を取得して表示する。
 * そのため本文の暗号化は不要で、署名（ES256）だけを Java 標準の機能で行う。
 */
@Component
public class WebPush {

    /** 送信結果。GONE は宛先が無効になったので削除してよい。 */
    public enum Result {
        SENT, GONE, FAILED
    }

    static {
        // プッシュサービス（Windows の WNS など）は本文が空でも Content-Length: 0 を要求するが、
        // Java の HttpClient は通常このヘッダーを付けさせないので、明示的に許可する
        String allowed = System.getProperty("jdk.httpclient.allowRestrictedHeaders", "");
        if (!allowed.toLowerCase().contains("content-length")) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders",
                    allowed.isBlank() ? "content-length" : allowed + ",content-length");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(WebPush.class);
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final String PUBLIC_KEY = "vapid.public";
    private static final String PRIVATE_KEY = "vapid.private";

    private final AppSettingRepository settings;
    private final TransactionTemplate newTransaction;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String publicKey;
    private volatile PrivateKey privateKey;

    public WebPush(AppSettingRepository settings, PlatformTransactionManager transactionManager) {
        this.settings = settings;
        // 読み取り専用の処理の中から呼ばれても鍵を保存できるよう、別のトランザクションで扱う
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** ブラウザに渡す公開鍵（base64url）。初めて使うときに鍵を作って保存する。 */
    public String publicKey() {
        ensureKeys();
        return publicKey;
    }

    public Result send(String endpoint) {
        ensureKeys();
        try {
            URI uri = URI.create(endpoint);
            String audience = uri.getScheme() + "://" + uri.getAuthority();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "43200")
                    .header("Urgency", "normal")
                    .header("Authorization", "vapid t=" + jwt(audience) + ", k=" + publicKey)
                    .header("Content-Length", "0")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            if (status >= 200 && status < 300) {
                return Result.SENT;
            }
            log.warn("プッシュ通知の送信に失敗しました: HTTP {}", status);
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("プッシュ通知の送信に失敗しました: {}", e.toString());
            return Result.FAILED;
        }
    }

    private String jwt(String audience) throws Exception {
        String header = b64(("{\"typ\":\"JWT\",\"alg\":\"ES256\"}").getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
        String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + exp
                + ",\"sub\":\"mailto:noreply@myreset.app\"}").getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(privateKey);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + b64(signer.sign());
    }

    private synchronized void ensureKeys() {
        if (privateKey != null) {
            return;
        }
        newTransaction.executeWithoutResult(status -> loadOrCreateKeys());
    }

    private void loadOrCreateKeys() {
        try {
            var pub = settings.findById(PUBLIC_KEY);
            var priv = settings.findById(PRIVATE_KEY);
            if (pub.isEmpty() || priv.isEmpty()) {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
                gen.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair pair = gen.generateKeyPair();
                String publicValue = b64(rawPublicKey((ECPublicKey) pair.getPublic()));
                String privateValue = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
                settings.save(new AppSetting(PUBLIC_KEY, publicValue));
                settings.save(new AppSetting(PRIVATE_KEY, privateValue));
                publicKey = publicValue;
                privateKey = pair.getPrivate();
                return;
            }
            publicKey = pub.get().getValue();
            privateKey = KeyFactory.getInstance("EC").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priv.get().getValue())));
        } catch (Exception e) {
            throw new IllegalStateException("通知用の鍵を用意できませんでした", e);
        }
    }

    /** 0x04 || X(32バイト) || Y(32バイト) の形の公開鍵。 */
    static byte[] rawPublicKey(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void copy32(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, out, offset + 32 - length, length);
    }

    private static String b64(byte[] bytes) {
        return B64URL.encodeToString(bytes);
    }
}
