package br.com.archlab.simulation.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreIndependenceTest {

    @Test
    void springBootMustNotBeOnCoreClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.boot.SpringApplication"));
    }
}
