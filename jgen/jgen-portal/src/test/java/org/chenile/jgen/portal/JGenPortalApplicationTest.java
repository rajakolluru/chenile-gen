package org.chenile.jgen.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JGenPortalApplicationTest {
    @Autowired ApplicationContext context;
    @Autowired PortalController controller;

    @Test
    void startsWithoutADatabaseAndReportsTheReleaseVersion() {
        assertFalse(context.containsBean("dataSource"));
        assertEquals("2.1.31", controller.generator("chenile-process-management").get("version"));
    }
}
