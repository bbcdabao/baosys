package szs.baosys.manager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import szs.baosys.common.security.annotation.SzsEnableUltraViresCheck;

@MapperScan("szs.baosys.manager.infra.database.mapper")
@SpringBootApplication
@SzsEnableUltraViresCheck
public class ManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagerApplication.class, args);
    }
}
