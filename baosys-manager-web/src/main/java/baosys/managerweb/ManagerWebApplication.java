package baosys.managerweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import baosys.common.security.annotation.EnableAuthenticationCheck;

@SpringBootApplication
@EnableAuthenticationCheck
public class ManagerWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagerWebApplication.class, args);
    }
}
