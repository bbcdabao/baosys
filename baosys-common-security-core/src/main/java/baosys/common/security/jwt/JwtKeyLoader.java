package baosys.common.security.jwt;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.util.StringUtils;

/**
 * RSA 密钥加载工具。
 *
 * <p>平台配置允许直接填写 PEM 文本，也允许填写去掉 PEM 头尾后的裸 Base64。
 * 公钥按 X.509 SubjectPublicKeyInfo 解析，私钥按 PKCS#8 解析。这里不做密钥生成，
 * 只负责把部署配置中的字符串转换为 Spring Security / Nimbus 可使用的 RSA key。</p>
 */
public final class JwtKeyLoader {

    private JwtKeyLoader() {}

    /**
     * 加载用于校验 JWT 签名的 RSA 公钥。
     *
     * <p>网关和资源服务只需要公钥。公钥为空或格式错误时直接失败，避免服务在
     * 未具备 token 校验能力的状态下启动。</p>
     */
    public static RSAPublicKey loadPublicKey(String publicKeyPem) {
        if (!StringUtils.hasText(publicKeyPem)) {
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
     *
     * <p>私钥只应配置在认证中心。当前实现要求 PKCS#8 格式，不支持传统
     * {@code -----BEGIN RSA PRIVATE KEY-----} 的 PKCS#1 私钥。</p>
     */
    public static RSAPrivateKey loadPrivateKey(String privateKeyPem) {
        if (!StringUtils.hasText(privateKeyPem)) {
            throw new IllegalArgumentException("JWT private key must not be blank");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPem(privateKeyPem, "PRIVATE KEY"));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception exception) {
            throw new IllegalStateException(
                "RSA private key parse failed, please use PKCS8 format",
                exception
            );
        }
    }

    /**
     * 去掉 PEM 头尾和所有空白字符，得到可直接 Base64 解码的密钥正文。
     *
     * <p>换行、空格和制表符都允许出现在配置中，便于环境变量、配置文件和密钥文件
     * 复用同一套解析逻辑。</p>
     */
    private static String stripPem(String pem, String type) {
        return pem
            .replace("-----BEGIN " + type + "-----", "")
            .replace("-----END " + type + "-----", "")
            .replaceAll("\\s", "");
    }
}
