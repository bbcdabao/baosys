// Generates persistent JWK material once. Never overwrite an existing signing key.
// Usage: jshell -R-Dsigning.key.output=D:/baosys/keys/signing-jwks.json scripts/generate-signing-key.jsh
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.*;

class SigningKeyGenerator {
    static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return base64url(bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
    }

    static int generate() {
        try {
            String output = System.getProperty("signing.key.output");
            if (output == null || output.isEmpty()) {
                throw new IllegalArgumentException("Usage: jshell -R-Dsigning.key.output=<output.json> scripts/generate-signing-key.jsh");
            }
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var key = (RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
            var fields = new LinkedHashMap<String, String>();
            fields.put("kty", "RSA");
            fields.put("n", unsigned(key.getModulus()));
            fields.put("e", unsigned(key.getPublicExponent()));
            fields.put("d", unsigned(key.getPrivateExponent()));
            fields.put("p", unsigned(key.getPrimeP()));
            fields.put("q", unsigned(key.getPrimeQ()));
            fields.put("dp", unsigned(key.getPrimeExponentP()));
            fields.put("dq", unsigned(key.getPrimeExponentQ()));
            fields.put("qi", unsigned(key.getCrtCoefficient()));
            String thumbprint = "{\"e\":\"" + fields.get("e") + "\",\"kty\":\"RSA\",\"n\":\"" + fields.get("n") + "\"}";
            fields.put("kid", base64url(MessageDigest.getInstance("SHA-256")
                .digest(thumbprint.getBytes(StandardCharsets.UTF_8))));
            fields.put("use", "sig");
            fields.put("alg", "RS256");
            var json = new StringJoiner(",\n", "{\n  \"keys\": [\n    {\n", "\n    }\n  ]\n}");
            fields.forEach((name, value) -> json.add("      \"" + name + "\": \"" + value + "\""));
            Path path = Path.of(output).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            FileAttribute<?>[] attributes = Files.getFileStore(path.getParent())
                .supportsFileAttributeView(PosixFileAttributeView.class)
                ? new FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")) }
                : new FileAttribute<?>[0];
            try (var channel = Files.newByteChannel(path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), attributes)) {
                var bytes = ByteBuffer.wrap(json.toString().getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
            }
            System.out.println("Created " + path + "; restrict access to the authorization server account.");
            return 0;
        } catch (Exception exception) {
            System.err.println(exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return 1;
        }
    }
}

int status = SigningKeyGenerator.generate();
/exit status
