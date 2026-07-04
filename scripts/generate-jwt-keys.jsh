import java.security.KeyPairGenerator;
import java.util.Base64;

var generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
var pair = generator.generateKeyPair();
System.out.println("BAOSYS_JWT_PRIVATE_KEY=" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
System.out.println("BAOSYS_JWT_PUBLIC_KEY=" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
/exit
