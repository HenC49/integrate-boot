package com.github.henc.test;

import com.github.henc.integrateboot.starter.IntegrateBoot;
import org.springframework.boot.SpringApplication;

/**
 * Sample application that exercises integrate-boot: it boots with {@link IntegrateBoot},
 * relies on the conventional layer scanning, and uses MyBatis-Flex over an in-memory H2
 * database so no external setup is required.
 */
@IntegrateBoot
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
