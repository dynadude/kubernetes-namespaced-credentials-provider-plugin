package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NamespaceTest {
    @Test
    public void getName() {
        String nsName = "hello";

        Namespace ns = new Namespace(nsName);

        assertEquals(nsName, ns.getName());
    }

    @Test
    public void setName() {
        String nsStartName = "hello";
        String nsEndName = "bye";

        Namespace ns = new Namespace(nsStartName);

        assertEquals(nsStartName, ns.getName());

        ns.setName(nsEndName);

        assertEquals(nsEndName, ns.getName());
    }

    @Test
    public void equalsEqualNamespaces() {
        String nsName = "hello";

        Namespace ns1 = new Namespace(nsName);
        Namespace ns2 = new Namespace(nsName);

        assertEquals(ns1, ns2);
    }

    @Test
    public void equalsDifferentNamespaces() {
        String nsName1 = "hello";
        String nsName2 = "bye";

        Namespace ns1 = new Namespace(nsName1);
        Namespace ns2 = new Namespace(nsName2);

        assertNotEquals(ns1, ns2);
    }

    @Test
    public void hashCodeTest() {
        String nsName = "hello";

        Namespace ns = new Namespace(nsName);

        assertEquals(ns.hashCode(), nsName.hashCode());
    }

    @Test
    public void toStringTest() {
        String nsName = "hello";

        Namespace ns = new Namespace(nsName);

        assertEquals(nsName, ns.toString());
    }
}
