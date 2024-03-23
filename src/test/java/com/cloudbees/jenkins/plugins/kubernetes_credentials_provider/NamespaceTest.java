package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import static org.junit.Assert.*;

import hudson.util.FormValidation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NamespaceTest {
    private Namespace.DescriptorImpl descriptor = new Namespace.DescriptorImpl();

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

    @Test
    public void doCheckNameValidNames() {
        assertDoCheckNameOk("hello");
        assertDoCheckNameOk("5hello");
        assertDoCheckNameOk("hello5");
        assertDoCheckNameOk("5hello5");
        assertDoCheckNameOk("5-hello-5");
    }

    private void assertDoCheckNameOk(String name) {
        assertDoCheckName(FormValidation.ok(), name);
    }

    @Test
    public void doCheckNameBlankName() {
        assertDoCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_MandatoryProperty()), "");
    }

    @Test
    public void doCheckNameInvalidCharacters() {
        assertDoCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters()),
                "._hello#$");

        assertDoCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters()), "Hello");

        assertDoCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters()), "hellO");
    }

    @Test
    public void doCheckNameStartsWithDash() {
        assertDoCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_StartsWithDash()), "-hello#$");
    }

    @Test
    public void doCheckNameEndsWithDash() {
        assertDoCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_EndsWithDash()), "hello5-");

        assertDoCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_EndsWithDash()), "hello#$-");
    }

    private void assertDoCheckName(FormValidation expected, String name) {
        assertEquals(
                "Namespace name: '" + name + "'",
                expected.toString(),
                descriptor.doCheckName(name).toString());
    }
}
