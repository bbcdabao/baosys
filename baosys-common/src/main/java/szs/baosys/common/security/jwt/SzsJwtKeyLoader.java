package szs.baosys.common.security.jwt;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 密钥加载工具。
 *
 * <p>平台配置允许直接填写 PEM 文本，也允许填写去掉 PEM 头尾后的裸 Base64。
 * 公钥按 X.509 SubjectPublicKeyInfo 解析，私钥按 PKCS#8 解析。</p>
 */
public final class SzsJwtKeyLoader {

    private SzsJwtKeyLoader() {
    }

    /**
     * 加载用于校验 JWT 签名的 RSA 公钥。
     */
    public static RSAPublicKey loadPublicKey(String publicKeyPem) {
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalArgumentException("JWT public key must not be blank");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPem(publicKeyPem, "PUBLIC KEY"));
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception exception) {
            throw new IllegalStateException("RSA public key parse failed", exception);
        }
    }

    /**
     * 加载用于签发 JWT 的 RSA 私钥。
     */
    public static RSAPrivateKey loadPrivateKey(String privateKeyPem) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalArgumentException("JWT private key must not be blank");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPem(privateKeyPem, "PRIVATE KEY"));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception exception) {
            throw new IllegalStateException("RSA private key parse failed, please use PKCS8 format", exception);
        }
    }

    /**
     * 去掉 PEM 头尾和所有空白字符，得到可直接 Base64 解码的密钥正文。
     */
    private static String stripPem(String pem, String type) {
        return pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }
}
