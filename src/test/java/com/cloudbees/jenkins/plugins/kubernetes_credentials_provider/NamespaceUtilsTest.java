package com.cloudbees.jenkins.plugins.kubernetes_credentials_provider;

import static org.junit.Assert.*;

import hudson.util.FormValidation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NamespaceUtilsTest {
    @Test
    public void checkNameValidNames() {
        assertCheckNameOk("hello");
        assertCheckNameOk("5hello");
        assertCheckNameOk("hello5");
        assertCheckNameOk("5hello5");
        assertCheckNameOk("5-hello-5");
    }

    private void assertCheckNameOk(String name) {
        assertCheckName(FormValidation.ok(), name);
    }

    @Test
    public void checkNameBlankName() {
        assertCheckName(FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_MandatoryProperty()), "");
    }

    @Test
    public void checkNameInvalidCharacters() {
        assertCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters()),
                "._hello#$");

        assertCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters()), "Hello");

        assertCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters()), "hellO");
    }

    @Test
    public void checkNameStartsWithDash() {
        assertCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_StartsWithDash()), "-hello#$");
    }

    @Test
    public void checkNameEndsWithDash() {
        assertCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_EndsWithDash()), "hello5-");

        assertCheckName(
                FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_EndsWithDash()), "hello#$-");
    }

    private void assertCheckName(FormValidation expected, String name) {
        assertEquals(
                "Namespace name: '" + name + "'",
                expected.toString(),
                NamespaceUtils.checkName(name).toString());
    }
}