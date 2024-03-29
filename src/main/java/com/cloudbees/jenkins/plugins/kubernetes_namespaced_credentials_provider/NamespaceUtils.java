package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;

public class NamespaceUtils {
    public static final int NAMESPACE_NAME_MAX_LENGTH = 63;

    /**
     * Form validation for a namespace.
     *
     * @param name the name.
     * @return the validation results.
     */
    public static FormValidation checkName(String name) {
        if (StringUtils.isBlank(name)) {
            return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_MandatoryProperty());
        }

        if (name.length() > NAMESPACE_NAME_MAX_LENGTH) {
            return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_TooLong());
        }

        if (StringUtils.startsWith(name, "-")) {
            return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_StartsWithDash());
        }

        if (StringUtils.endsWith(name, "-")) {
            return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_EndsWithDash());
        }

        if (doesNameContainInvalidCharacters(name)) {
            return FormValidation.error(Messages.KubernetesNamespacedCredentialsProvider_InvalidCharacters());
        }

        return FormValidation.ok();
    }

    private static boolean doesNameContainInvalidCharacters(String string) {
        for (char character : string.toCharArray()) {
            if (isCharacterValid(character)) {
                continue;
            }

            return true;
        }

        return false;
    }

    private static boolean isCharacterValid(char character) {
        if (Character.isLowerCase(character)) {
            return true;
        }

        if (Character.isDigit(character)) {
            return true;
        }

        if (character == '-') {
            return true;
        }

        return false;
    }
}
