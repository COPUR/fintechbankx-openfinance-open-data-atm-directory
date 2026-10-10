package com.enterprise.openfinance.atmdirectory;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class ApplicationTest {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.main.web-application-type=none"};
        try (MockedStatic<SpringApplication> springApplication = Mockito.mockStatic(SpringApplication.class)) {
            Application.main(args);
            springApplication.verify(() -> SpringApplication.run(Application.class, args));
        }
    }
}
