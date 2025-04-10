package org.example.backendai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.example.backendai.mapper")
public class BackendAiApplication {
	public static void main(String[] args) {
		SpringApplication.run(BackendAiApplication.class, args);
	}

}
