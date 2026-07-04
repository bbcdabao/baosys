package szs.baosys.managerweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import szs.baosys.common.security.annotation.SzsEnableAuthenticationCheck;

@SpringBootApplication
@SzsEnableAuthenticationCheck
public class ManagerWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagerWebApplication.class, args);
    }
}
