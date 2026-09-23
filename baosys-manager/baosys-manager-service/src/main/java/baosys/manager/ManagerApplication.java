package baosys.manager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import baosys.common.security.annotation.EnableUltraViresCheck;

@MapperScan("baosys.manager.infra.database.mapper")
@SpringBootApplication
@EnableUltraViresCheck
public class ManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagerApplication.class, args);
    }
}
